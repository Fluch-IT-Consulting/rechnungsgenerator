#!/usr/bin/env python3
"""Ermittelt die nächste freie Rechnungsnummer im Schema R{JJ}-{NNN}.

Durchsucht das Ablageverzeichnis nach Dateien der Form
"Rechnung_R26-011-kunde.pdf" (oder .yaml/.ods) und gibt die nächste Nummer
des laufenden Jahres aus. Beginnt ein neues Jahr, fängt die Zählung bei 001 an.

Aufruf:  naechste-nummer.py <Ablageverzeichnis> [--jahr JJ]
"""
import argparse
import datetime
import pathlib
import re
import sys

MUSTER = re.compile(r"Rechnung_R(\d{2})-(\d{3})\b")


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("verzeichnis", type=pathlib.Path)
    p.add_argument("--jahr", default=None, help="Zweistellige Jahreszahl; Vorgabe ist das aktuelle Jahr")
    args = p.parse_args()

    if not args.verzeichnis.is_dir():
        print(f"Verzeichnis nicht gefunden: {args.verzeichnis}", file=sys.stderr)
        return 1

    jahr = args.jahr or f"{datetime.date.today().year % 100:02d}"

    # rglob statt glob: Ältere Jahrgänge liegen in Unterordnern. Sie stören
    # nicht, weil nur Treffer des gesuchten Jahres zählen – aber wer eine
    # Rechnung versehentlich einsortiert hat, bekommt trotzdem keine
    # doppelte Nummer.
    belegt = {
        int(m.group(2))
        for datei in args.verzeichnis.rglob("Rechnung_R*")
        if (m := MUSTER.search(datei.name)) and m.group(1) == jahr
    }

    naechste = max(belegt) + 1 if belegt else 1
    if naechste > 999:
        print(f"Nummernkreis R{jahr} ist erschöpft (999 Rechnungen).", file=sys.stderr)
        return 1

    print(f"R{jahr}-{naechste:03d}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
