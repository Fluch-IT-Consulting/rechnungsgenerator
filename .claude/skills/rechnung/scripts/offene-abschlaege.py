#!/usr/bin/env python3
"""Listet die Abschlagsrechnungen eines Kunden, die noch in keiner
Schlussrechnung abgesetzt sind.

Durchsucht das Ablageverzeichnis samt Jahresunterordnern nach YAML-Dateien.
Offen ist eine Abschlagsrechnung (art: abschlagsrechnung) des Kunden, deren
Nummer in keiner Schlussrechnung unter abschlaege/rechnungsnummer steht.
Ausgabe je Treffer eine Zeile: Nummer, Datum, Datei – durch Tabulatoren
getrennt, nach Nummer sortiert. Beträge gibt das Skript bewusst nicht aus:
Die rechnet der Generator (rechnungsgenerator abschlaege), nicht dieses
Skript.

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


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("verzeichnis", type=pathlib.Path)
    p.add_argument("kunde", help="Kundenname genau so, wie er unter kunde/name steht")
    args = p.parse_args()

    if not args.verzeichnis.is_dir():
        print(f"Verzeichnis nicht gefunden: {args.verzeichnis}", file=sys.stderr)
        return 1

    abschlagsrechnungen = []
    abgesetzt = set()
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
            for abschlag in rechnung.get("abschlaege") or []:
                if isinstance(abschlag, dict) and "rechnungsnummer" in abschlag:
                    abgesetzt.add(als_text(abschlag["rechnungsnummer"]))

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

    offen = [(d, r) for d, r in des_kunden if als_text(r.get("nummer", "")) not in abgesetzt]
    for datei, rechnung in sorted(offen, key=lambda t: als_text(t[1].get("nummer", ""))):
        print(f"{rechnung.get('nummer')}\t{rechnung.get('datum')}\t{datei}")
    if not offen:
        print(f"Alle {len(des_kunden)} Abschlagsrechnungen an '{args.kunde}' sind schon abgesetzt.",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
