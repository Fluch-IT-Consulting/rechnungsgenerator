#!/usr/bin/env python3
"""Listet die Abschlagsrechnungen eines Kunden, die noch abzusetzen sind.

Durchsucht das Ablageverzeichnis samt Jahresunterordnern nach YAML-Dateien.
Offen ist eine Abschlagsrechnung (art: abschlagsrechnung) des Kunden, die
nicht storniert ist und deren Nummer in keiner gültigen Schlussrechnung unter
abschlaege/rechnungsnummer steht. Ausgabe je Treffer eine Zeile: Nummer,
Datum, Datei – durch Tabulatoren getrennt, nach Nummer sortiert. Beträge gibt
das Skript bewusst nicht aus: Die rechnet der Generator
(rechnungsgenerator abschlaege), nicht dieses Skript.

Zwei Widersprüche der Ablage meldet das Skript dabei, weil sie nur beim Lesen
eines ganzen Verzeichnisses auffallen: ein Abschlag, den zwei Schlussrechnungen
absetzen (#67), und einer, der storniert ist und trotzdem abgesetzt wird.

Stornos zählen mit (#66), in beide Richtungen: Eine stornierte
Abschlagsrechnung ist nichts mehr, was abgesetzt werden könnte, und eine
stornierte Schlussrechnung setzt nichts mehr ab – ihre Abschläge sind wieder
offen. Nur das Skript kann das sehen: Der Generator liest beim Erzeugen eine
einzige Datei (ADR-0001). Was dabei auffällt, steht auf stderr; stdout trägt
nur die Liste.

Aufruf:  offene-abschlaege.py <Ablageverzeichnis> <Kundenname>
"""
import argparse
import pathlib
import sys

try:
    import yaml
except ImportError:
    print("Dieses Skript braucht PyYAML: pip3 install pyyaml", file=sys.stderr)
    sys.exit(1)


def as_text(number) -> str:
    # Exakt verglichen wie ABSCHLAG_DOPPELT im Generator (#53). str(), weil YAML
    # eine Nummer wie 123 als Zahl liest, der Generator aber als Text.
    return str(number)


def final_invoice_phrase(numbers, adjective: str = "") -> str:
    # Derselbe Abschlag kann in mehreren Schlussrechnungen stehen – dann
    # gehören alle in die Meldung, nicht bloß die erste. Dativ, damit sich der
    # Satzbau der Meldung nicht nach der Anzahl richten muss: "in der
    # stornierten Schlussrechnung X" / "in den stornierten Schlussrechnungen
    # X, Y".
    if len(numbers) == 1:
        return f"der {adjective}Schlussrechnung {numbers[0]}"
    return f"den {adjective}Schlussrechnungen " + ", ".join(numbers)


def main() -> int:
    p = argparse.ArgumentParser()
    # Die Namen der Oberfläche bleiben deutsch, der Skill ruft sie so auf;
    # metavar trennt sie vom Attribut.
    p.add_argument("directory", metavar="verzeichnis", type=pathlib.Path)
    p.add_argument(
        "customer", metavar="kunde", help="Kundenname genau so, wie er unter kunde/name steht"
    )
    args = p.parse_args()

    if not args.directory.is_dir():
        print(f"Verzeichnis nicht gefunden: {args.directory}", file=sys.stderr)
        return 1

    prepayment_invoices = []
    # Nummer der aufgehobenen Rechnung -> Nummer ihres Stornos.
    cancelled_by = {}
    # Nummer der Schlussrechnung -> Nummern der darin abgesetzten Abschläge.
    final_invoices = []
    # rglob wie bei naechste-nummer.py: Abschlag und Schlussrechnung liegen
    # oft in verschiedenen Jahrgängen.
    for file in sorted(args.directory.rglob("*.yaml")):
        try:
            invoice = yaml.safe_load(file.read_text(encoding="utf-8"))
        except (yaml.YAMLError, UnicodeDecodeError) as error:
            # Warnen statt still überspringen: Eine kaputte Datei könnte genau
            # die gesuchte Abschlagsrechnung sein. Eine Zeile genügt; die
            # ausführliche Meldung gibt der Generator, wenn man die Datei mit
            # ihm liest.
            mark = getattr(error, "problem_mark", None)
            location = f", Zeile {mark.line + 1}" if mark else ""
            reason = getattr(error, "problem", None) or str(error).splitlines()[0]
            print(f"Übersprungen, nicht lesbar: {file}{location}: {reason}", file=sys.stderr)
            continue
        if not isinstance(invoice, dict):
            continue

        invoice_type = invoice.get("art")
        if invoice_type == "abschlagsrechnung":
            prepayment_invoices.append((file, invoice))
        elif invoice_type == "schlussrechnung":
            deducted = set()
            for abschlag in invoice.get("abschlaege") or []:
                if isinstance(abschlag, dict) and "rechnungsnummer" in abschlag:
                    deducted.add(as_text(abschlag["rechnungsnummer"]))
            final_invoices.append((as_text(invoice.get("nummer") or file.name), deducted))
        elif invoice_type == "stornorechnung":
            cancelled = invoice.get("storniert")
            if isinstance(cancelled, dict) and "rechnungsnummer" in cancelled:
                # Ohne eigene Nummer wird der Dateiname genannt: Die Meldung
                # soll das Storno auffindbar machen, auch wenn die Datei noch
                # unfertig ist.
                cancelled_by[as_text(cancelled["rechnungsnummer"])] = \
                    as_text(invoice.get("nummer") or file.name)

    # Erst jetzt, wo alle Dateien gelesen sind: Ein Storno darf in der
    # Verzeichnisreihenfolge hinter seiner Schlussrechnung liegen.
    deducted_in = {}
    released_by = {}
    for number, deducted in final_invoices:
        target = released_by if number in cancelled_by else deducted_in
        for abschlag in deducted:
            target.setdefault(abschlag, []).append(number)

    def customer_of(invoice) -> str:
        customer = invoice.get("kunde")
        return customer.get("name", "") if isinstance(customer, dict) else ""

    customer_invoices = [(d, r) for d, r in prepayment_invoices if customer_of(r) == args.customer]
    if not customer_invoices:
        # Ein leicht abweichender Name fiele sonst als "nichts offen" durch.
        others = sorted({customer_of(r) for _, r in prepayment_invoices} - {""})
        if others:
            print(f"Keine Abschlagsrechnung an '{args.customer}'. Abschlagsrechnungen gibt es an: "
                  + "; ".join(others), file=sys.stderr)
        else:
            print(f"Keine Abschlagsrechnung im Verzeichnis {args.directory}.", file=sys.stderr)
        return 0

    still_open = []
    for file, invoice in sorted(customer_invoices, key=lambda t: as_text(t[1].get("nummer", ""))):
        number = as_text(invoice.get("nummer", ""))
        cancellation = cancelled_by.get(number)
        if cancellation:
            if number in deducted_in:
                # Weder offen noch in Ordnung: Eine gültige Schlussrechnung
                # setzt Geld ab, das der Kunde zurückbekommen hat.
                print(f"Achtung: {number} ist durch {cancellation} storniert, wird aber in "
                      f"{final_invoice_phrase(deducted_in[number])} abgesetzt.", file=sys.stderr)
            else:
                print(f"{number} ist durch {cancellation} storniert – gilt nicht als offen.",
                      file=sys.stderr)
            continue
        if number in deducted_in:
            if len(deducted_in[number]) > 1:
                # Der Generator lehnt dieselbe Nummer zweimal ab, aber nur
                # innerhalb einer Datei (ADR-0001). Über Dateigrenzen sieht
                # das nur, wer das Verzeichnis liest (#67).
                print(f"Achtung: {number} wird mehrfach abgesetzt – in "
                      f"{final_invoice_phrase(deducted_in[number])}.", file=sys.stderr)
            continue
        if number in released_by:
            print(f"{number} war in {final_invoice_phrase(released_by[number], 'stornierten ')} "
                  f"abgesetzt – gilt wieder als offen.", file=sys.stderr)
        still_open.append((file, invoice))
        print(f"{invoice.get('nummer')}\t{invoice.get('datum')}\t{file}")
    if not still_open:
        # "oder storniert" nur, wenn wirklich eines dabei war – sonst nennt die
        # Meldung einen Grund, den es nicht gibt.
        how = ("abgesetzt oder storniert"
               if any(as_text(r.get("nummer", "")) in cancelled_by for _, r in customer_invoices)
               else "abgesetzt")
        print(f"Alle {len(customer_invoices)} Abschlagsrechnungen an '{args.customer}' sind schon {how}.",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
