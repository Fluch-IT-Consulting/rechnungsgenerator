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

FILE_NAME_PATTERN = re.compile(r"Rechnung_R(\d{2})-(\d{3})\b")
NUMBER_PATTERN = re.compile(r"R(\d{2})-(\d{3})")


def yaml_module():
    """PyYAML, oder ein Abbruch mit Installationshinweis."""
    # Der Import steht in einer Funktion und nicht oben, damit ein fehlendes
    # PyYAML eine Meldung ergibt statt eines Stacktraces.
    try:
        import yaml
    except ImportError:
        print("Dieses Skript braucht PyYAML: pip3 install pyyaml", file=sys.stderr)
        raise SystemExit(1)
    return yaml


def pattern_from_seller_config(path: pathlib.Path) -> str | None:
    """Der Prüfausdruck aus den Stammdaten, oder None, wenn keiner eingetragen ist."""
    yaml = yaml_module()
    try:
        seller_config = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, yaml.YAMLError) as error:
        print(f"Stammdaten nicht lesbar ({path}): {error}", file=sys.stderr)
        raise SystemExit(1)

    # Kein Eintrag heißt: nicht prüfen. Dieselbe Regel wie im Generator.
    return (seller_config or {}).get("rechnungsnummer-muster")


def numbers_from_yaml(directory: pathlib.Path):
    """Die Nummern aus dem Feld `nummer` aller YAML-Dateien, als (Jahr, Zähler)."""
    yaml = yaml_module()
    for file in sorted(directory.rglob("*.yaml")):
        try:
            content = yaml.safe_load(file.read_text(encoding="utf-8"))
        except (yaml.YAMLError, UnicodeDecodeError) as error:
            # Warnen wie offene-abschlaege.py, nicht still überspringen: Eine
            # kaputte Datei kann eine Rechnung sein, deren Nummer vergeben ist.
            # Abbrechen dagegen hielte jede Rechnung an, weil irgendwo im
            # Verzeichnis eine unfertige Datei liegt.
            reason = getattr(error, "problem", None) or str(error).splitlines()[0]
            print(f"Übersprungen, nicht lesbar: {file}: {reason}", file=sys.stderr)
            continue
        # Stammdaten, vereinnahmungen.yaml und Ähnliches haben keine Nummer.
        if not isinstance(content, dict) or "nummer" not in content:
            continue
        # str(): Eine ungequotete Nummer kann YAML als Zahl lesen. Nummern in
        # einem anderen Schema passen nicht und können mit diesem nicht
        # zusammenstoßen.
        if m := NUMBER_PATTERN.fullmatch(str(content["nummer"])):
            yield m.group(1), int(m.group(2))


def main() -> int:
    p = argparse.ArgumentParser()
    # Die Namen der Oberfläche bleiben deutsch, der Skill ruft sie so auf;
    # dest und metavar trennen sie vom Attribut.
    p.add_argument("directory", metavar="verzeichnis", type=pathlib.Path)
    p.add_argument(
        "--rechnungsdatum",
        dest="invoice_date",
        metavar="RECHNUNGSDATUM",
        type=datetime.date.fromisoformat,
        default=datetime.date.today(),
        help="Datum der Rechnung, die geschrieben wird (JJJJ-MM-TT); Vorgabe ist heute",
    )
    p.add_argument(
        "--stammdaten",
        dest="seller_config",
        metavar="STAMMDATEN",
        type=pathlib.Path,
        default=None,
        help="Stammdaten, gegen deren rechnungsnummer-muster die Nummer geprüft wird",
    )
    args = p.parse_args()

    if not args.directory.is_dir():
        print(f"Verzeichnis nicht gefunden: {args.directory}", file=sys.stderr)
        return 1

    # Das Jahr der Rechnung, nicht das von heute: Eine Rechnung vom 31.12., die
    # erst am 2.1. geschrieben wird, gehört in den Nummernkreis des alten Jahres.
    year = f"{args.invoice_date.year % 100:02d}"

    # rglob statt glob: Ältere Jahrgänge liegen in Unterordnern. Sie stören
    # nicht, weil nur Treffer des gesuchten Jahres zählen – aber wer eine
    # Rechnung versehentlich einsortiert hat, bekommt trotzdem keine
    # doppelte Nummer.
    taken = {
        int(m.group(2))
        for file in args.directory.rglob("Rechnung_R*")
        if (m := FILE_NAME_PATTERN.search(file.name)) and m.group(1) == year
    }
    taken |= {counter for j, counter in numbers_from_yaml(args.directory) if j == year}

    next_counter = max(taken) + 1 if taken else 1
    if next_counter > 999:
        print(f"Nummernkreis R{year} ist erschöpft (999 Rechnungen).", file=sys.stderr)
        return 1

    number = f"R{year}-{next_counter:03d}"

    if args.seller_config is not None:
        pattern = pattern_from_seller_config(args.seller_config)
        # fullmatch, nicht search: Der Generator prüft mit Regex.matches, also
        # ebenfalls gegen die ganze Nummer.
        if pattern is not None and not re.fullmatch(pattern, number):
            print(
                f"Die nächste Nummer wäre {number}, aber sie passt nicht zum Muster '{pattern}' "
                f"aus {args.seller_config}. Dieses Skript vergibt im Schema R{{JJ}}-{{NNN}} – wer das "
                f"Muster wechselt, muss es hier mitwechseln.",
                file=sys.stderr,
            )
            return 1

    print(number)
    return 0


if __name__ == "__main__":
    sys.exit(main())
