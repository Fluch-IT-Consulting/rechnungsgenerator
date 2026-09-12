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


def als_text(nummer) -> str:
    # Exakt verglichen wie Regel 7 der Schlussrechnung (#53). str(), weil YAML
    # eine Nummer wie 123 als Zahl liest, der Generator aber als Text.
    return str(nummer)


def genannt(nummern, eigenschaft: str = "") -> str:
    # Derselbe Abschlag kann in mehreren Schlussrechnungen stehen – dann
    # gehören alle in die Meldung, nicht bloß die erste. Dativ, damit sich der
    # Satzbau der Meldung nicht nach der Anzahl richten muss: "in der
    # stornierten Schlussrechnung X" / "in den stornierten Schlussrechnungen
    # X, Y".
    if len(nummern) == 1:
        return f"der {eigenschaft}Schlussrechnung {nummern[0]}"
    return f"den {eigenschaft}Schlussrechnungen " + ", ".join(nummern)


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("verzeichnis", type=pathlib.Path)
    p.add_argument("kunde", help="Kundenname genau so, wie er unter kunde/name steht")
    args = p.parse_args()

    if not args.verzeichnis.is_dir():
        print(f"Verzeichnis nicht gefunden: {args.verzeichnis}", file=sys.stderr)
        return 1

    abschlagsrechnungen = []
    # Nummer der aufgehobenen Rechnung -> Nummer ihres Stornos.
    storniert_durch = {}
    # Nummer der Schlussrechnung -> Nummern der darin abgesetzten Abschläge.
    schlussrechnungen = []
    # rglob wie bei naechste-nummer.py: Abschlag und Schlussrechnung liegen
    # oft in verschiedenen Jahrgängen.
    for datei in sorted(args.verzeichnis.rglob("*.yaml")):
        try:
            rechnung = yaml.safe_load(datei.read_text(encoding="utf-8"))
        except (yaml.YAMLError, UnicodeDecodeError) as fehler:
            # Warnen statt still überspringen: Eine kaputte Datei könnte genau
            # die gesuchte Abschlagsrechnung sein. Eine Zeile genügt; die
            # ausführliche Meldung gibt der Generator, wenn man die Datei mit
            # ihm liest.
            stelle = getattr(fehler, "problem_mark", None)
            wo = f", Zeile {stelle.line + 1}" if stelle else ""
            grund = getattr(fehler, "problem", None) or str(fehler).splitlines()[0]
            print(f"Übersprungen, nicht lesbar: {datei}{wo}: {grund}", file=sys.stderr)
            continue
        if not isinstance(rechnung, dict):
            continue

        art = rechnung.get("art")
        if art == "abschlagsrechnung":
            abschlagsrechnungen.append((datei, rechnung))
        elif art == "schlussrechnung":
            abgesetzte = set()
            for abschlag in rechnung.get("abschlaege") or []:
                if isinstance(abschlag, dict) and "rechnungsnummer" in abschlag:
                    abgesetzte.add(als_text(abschlag["rechnungsnummer"]))
            schlussrechnungen.append((als_text(rechnung.get("nummer") or datei.name), abgesetzte))
        elif art == "stornorechnung":
            aufgehoben = rechnung.get("storniert")
            if isinstance(aufgehoben, dict) and "rechnungsnummer" in aufgehoben:
                # Ohne eigene Nummer wird der Dateiname genannt: Die Meldung
                # soll das Storno auffindbar machen, auch wenn die Datei noch
                # unfertig ist.
                storniert_durch[als_text(aufgehoben["rechnungsnummer"])] = \
                    als_text(rechnung.get("nummer") or datei.name)

    # Erst jetzt, wo alle Dateien gelesen sind: Ein Storno darf in der
    # Verzeichnisreihenfolge hinter seiner Schlussrechnung liegen.
    abgesetzt_in = {}
    freigegeben_von = {}
    for nummer, abgesetzte in schlussrechnungen:
        ziel = freigegeben_von if nummer in storniert_durch else abgesetzt_in
        for abschlag in abgesetzte:
            ziel.setdefault(abschlag, []).append(nummer)

    def kunde_von(rechnung) -> str:
        kunde = rechnung.get("kunde")
        return kunde.get("name", "") if isinstance(kunde, dict) else ""

    des_kunden = [(d, r) for d, r in abschlagsrechnungen if kunde_von(r) == args.kunde]
    if not des_kunden:
        # Ein leicht abweichender Name fiele sonst als "nichts offen" durch.
        andere = sorted({kunde_von(r) for _, r in abschlagsrechnungen} - {""})
        if andere:
            print(f"Keine Abschlagsrechnung an '{args.kunde}'. Abschlagsrechnungen gibt es an: "
                  + "; ".join(andere), file=sys.stderr)
        else:
            print(f"Keine Abschlagsrechnung im Verzeichnis {args.verzeichnis}.", file=sys.stderr)
        return 0

    offen = []
    for datei, rechnung in sorted(des_kunden, key=lambda t: als_text(t[1].get("nummer", ""))):
        nummer = als_text(rechnung.get("nummer", ""))
        storno = storniert_durch.get(nummer)
        if storno:
            if nummer in abgesetzt_in:
                # Weder offen noch in Ordnung: Eine gültige Schlussrechnung
                # setzt Geld ab, das der Kunde zurückbekommen hat.
                print(f"Achtung: {nummer} ist durch {storno} storniert, wird aber in "
                      f"{genannt(abgesetzt_in[nummer])} abgesetzt.", file=sys.stderr)
            else:
                print(f"{nummer} ist durch {storno} storniert – gilt nicht als offen.",
                      file=sys.stderr)
            continue
        if nummer in abgesetzt_in:
            if len(abgesetzt_in[nummer]) > 1:
                # Der Generator lehnt dieselbe Nummer zweimal ab, aber nur
                # innerhalb einer Datei (ADR-0001). Über Dateigrenzen sieht
                # das nur, wer das Verzeichnis liest (#67).
                print(f"Achtung: {nummer} wird mehrfach abgesetzt – in "
                      f"{genannt(abgesetzt_in[nummer])}.", file=sys.stderr)
            continue
        if nummer in freigegeben_von:
            print(f"{nummer} war in {genannt(freigegeben_von[nummer], 'stornierten ')} "
                  f"abgesetzt – gilt wieder als offen.", file=sys.stderr)
        offen.append((datei, rechnung))
        print(f"{rechnung.get('nummer')}\t{rechnung.get('datum')}\t{datei}")
    if not offen:
        # "oder storniert" nur, wenn wirklich eines dabei war – sonst nennt die
        # Meldung einen Grund, den es nicht gibt.
        wie = ("abgesetzt oder storniert"
               if any(als_text(r.get("nummer", "")) in storniert_durch for _, r in des_kunden)
               else "abgesetzt")
        print(f"Alle {len(des_kunden)} Abschlagsrechnungen an '{args.kunde}' sind schon {wie}.",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
