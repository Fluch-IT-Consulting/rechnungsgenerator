#!/usr/bin/env python3
"""Ermittelt die nächste freie Rechnungsnummer im Schema R{JJ}-{NNN}.

Durchsucht das Ablageverzeichnis nach vergebenen Nummern und gibt die nächste
des Jahres aus, in dem die Rechnung datiert ist. Beginnt ein neues Jahr,
fängt die Zählung bei 001 an.

Belegt ist eine Nummer, wenn sie an einer von zwei Stellen steht:
- im Dateinamen, in der Form "Rechnung_R26-011-kunde.pdf" (oder .yaml/.ods) –
  so heißen die Dateien, die der Skill anlegt, und die alten .ods-Rechnungen;
- im Feld `nummer` einer YAML-Datei, gleich wie sie heißt (#75). Der Generator
  nennt ein PDF ohne -o anders als der Skill, und wer eine Datei von Hand
  anlegt, hält sich womöglich an keine der beiden Regeln. Ihre Nummer war
  trotzdem vergeben, und die Einmaligkeit (§14 Abs. 4 Satz 1 Nr. 4 UStG)
  hängt an diesem Skript.

Das Schema steckt hier fest: Ein regulärer Ausdruck prüft, er zählt nicht –
er sagt nicht, welcher Teil einer Nummer der Zähler ist. Mit --stammdaten
wird die vergebene Nummer aber gegen 'rechnungsnummer-muster' geprüft
(ADR-0005), damit die beiden Stellen nicht still auseinanderlaufen: Wer das
Muster wechselt und dieses Skript vergisst, bekommt hier einen Abbruch statt
später eine abgelehnte Rechnung.

Aufruf:  naechste-nummer.py <Ablageverzeichnis> [--rechnungsdatum JJJJ-MM-TT]
                            [--stammdaten <pfad>]
"""
import argparse
import datetime
import pathlib
import re
import sys

MUSTER = re.compile(r"Rechnung_R(\d{2})-(\d{3})\b")
NUMMER = re.compile(r"R(\d{2})-(\d{3})")


def yaml_modul():
    """PyYAML, oder ein Abbruch mit Installationshinweis."""
    # Der Import steht in einer Funktion und nicht oben, damit ein fehlendes
    # PyYAML eine Meldung ergibt statt eines Stacktraces.
    try:
        import yaml
    except ImportError:
        print("Dieses Skript braucht PyYAML: pip3 install pyyaml", file=sys.stderr)
        raise SystemExit(1)
    return yaml


def muster_aus_stammdaten(pfad: pathlib.Path) -> str | None:
    """Der Prüfausdruck aus den Stammdaten, oder None, wenn keiner eingetragen ist."""
    yaml = yaml_modul()
    try:
        stammdaten = yaml.safe_load(pfad.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, yaml.YAMLError) as fehler:
        print(f"Stammdaten nicht lesbar ({pfad}): {fehler}", file=sys.stderr)
        raise SystemExit(1)

    # Kein Eintrag heißt: nicht prüfen. Dieselbe Regel wie im Generator.
    return (stammdaten or {}).get("rechnungsnummer-muster")


def nummern_aus_yaml(verzeichnis: pathlib.Path):
    """Die Nummern aus dem Feld `nummer` aller YAML-Dateien, als (Jahr, Zähler)."""
    yaml = yaml_modul()
    for datei in sorted(verzeichnis.rglob("*.yaml")):
        try:
            inhalt = yaml.safe_load(datei.read_text(encoding="utf-8"))
        except (yaml.YAMLError, UnicodeDecodeError) as fehler:
            # Warnen wie offene-abschlaege.py, nicht still überspringen: Eine
            # kaputte Datei kann eine Rechnung sein, deren Nummer vergeben ist.
            # Abbrechen dagegen hielte jede Rechnung an, weil irgendwo im
            # Verzeichnis eine unfertige Datei liegt.
            grund = getattr(fehler, "problem", None) or str(fehler).splitlines()[0]
            print(f"Übersprungen, nicht lesbar: {datei}: {grund}", file=sys.stderr)
            continue
        # Stammdaten, vereinnahmungen.yaml und Ähnliches haben keine Nummer.
        if not isinstance(inhalt, dict) or "nummer" not in inhalt:
            continue
        # str(): Eine ungequotete Nummer kann YAML als Zahl lesen. Nummern in
        # einem anderen Schema passen nicht und können mit diesem nicht
        # zusammenstoßen.
        if m := NUMMER.fullmatch(str(inhalt["nummer"])):
            yield m.group(1), int(m.group(2))


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("verzeichnis", type=pathlib.Path)
    p.add_argument(
        "--rechnungsdatum",
        type=datetime.date.fromisoformat,
        default=datetime.date.today(),
        help="Datum der Rechnung, die geschrieben wird (JJJJ-MM-TT); Vorgabe ist heute",
    )
    p.add_argument(
        "--stammdaten",
        type=pathlib.Path,
        default=None,
        help="Stammdaten, gegen deren rechnungsnummer-muster die Nummer geprüft wird",
    )
    args = p.parse_args()

    if not args.verzeichnis.is_dir():
        print(f"Verzeichnis nicht gefunden: {args.verzeichnis}", file=sys.stderr)
        return 1

    # Das Jahr der Rechnung, nicht das von heute: Eine Rechnung vom 31.12., die
    # erst am 2.1. geschrieben wird, gehört in den Nummernkreis des alten Jahres.
    jahr = f"{args.rechnungsdatum.year % 100:02d}"

    # rglob statt glob: Ältere Jahrgänge liegen in Unterordnern. Sie stören
    # nicht, weil nur Treffer des gesuchten Jahres zählen – aber wer eine
    # Rechnung versehentlich einsortiert hat, bekommt trotzdem keine
    # doppelte Nummer.
    belegt = {
        int(m.group(2))
        for datei in args.verzeichnis.rglob("Rechnung_R*")
        if (m := MUSTER.search(datei.name)) and m.group(1) == jahr
    }
    belegt |= {zaehler for j, zaehler in nummern_aus_yaml(args.verzeichnis) if j == jahr}

    naechste = max(belegt) + 1 if belegt else 1
    if naechste > 999:
        print(f"Nummernkreis R{jahr} ist erschöpft (999 Rechnungen).", file=sys.stderr)
        return 1

    nummer = f"R{jahr}-{naechste:03d}"

    if args.stammdaten is not None:
        muster = muster_aus_stammdaten(args.stammdaten)
        # fullmatch, nicht search: Der Generator prüft mit Regex.matches, also
        # ebenfalls gegen die ganze Nummer.
        if muster is not None and not re.fullmatch(muster, nummer):
            print(
                f"Die nächste Nummer wäre {nummer}, aber sie passt nicht zum Muster '{muster}' "
                f"aus {args.stammdaten}. Dieses Skript vergibt im Schema R{{JJ}}-{{NNN}} – wer das "
                f"Muster wechselt, muss es hier mitwechseln.",
                file=sys.stderr,
            )
            return 1

    print(nummer)
    return 0


if __name__ == "__main__":
    sys.exit(main())
