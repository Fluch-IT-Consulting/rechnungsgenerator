#!/usr/bin/env python3
"""Ermittelt die nächste freie Rechnungsnummer (ADR-0031).

Durchsucht das Ablageverzeichnis nach belegten Nummern, also dem Feld `nummer`
jeder YAML-Datei, und lässt das Vergabeskript der Ablage die nächste nennen:
.rechnungsgenerator/rechnungsnummer-vergabe. Das Format der Nummern kennt nur
das Vergabeskript; seine Vorlage liegt im Skill neben diesem Ordner.

Gibt es im Nummernkreis des Rechnungsdatums noch keine YAML-Rechnung, ist die
Nummer nur ein Vorschlag: Er steht auf stderr, und das Skript endet mit 3. Dort
können Rechnungen aus der Zeit vor dem Generator liegen, die es nicht sieht.
Bestätigt wird mit einem zweiten Aufruf, der die Nummer mit --rechnungsnummer
nennt.

Jede Nummer, die auf stdout geht, ist geprüft: Sie ist nicht schon belegt
(§14 Abs. 4 Satz 1 Nr. 4 UStG), und mit --stammdaten passt sie zu
'rechnungsnummer-muster' (ADR-0005).

Aufruf:  naechste-nummer.py <Ablageverzeichnis> [--rechnungsdatum JJJJ-MM-TT]
                            [--rechnungsnummer NUMMER] [--stammdaten <pfad>]
Endet mit 0 und der Nummer, mit 3 bei einem Vorschlag, sonst mit 1.
"""
import argparse
import datetime
import os
import pathlib
import re
import shlex
import subprocess
import sys
import sysconfig
from typing import Optional

ROOT_FOLDER = ".rechnungsgenerator"
ALLOCATOR = "rechnungsnummer-vergabe"
# Neben scripts/. Nicht aufgelöst: Im eingehängten Skill ist das der Link, und
# der Pfad bleibt über ein Upgrade gültig (ADR-0030).
TEMPLATE = pathlib.Path(__file__).parent.parent / ALLOCATOR
PROPOSAL = 3


class Refusal(Exception):
    pass


def pyyaml_hint() -> str:
    """Was fehlt, bei welchem Interpreter, und der Befehl, der bei ihm geht (#553)."""
    python = shlex.quote(sys.executable or "python3")
    # An dieser Datei liest auch pip ab, ob es ablehnt (PEP 668), etwa bei
    # Homebrews Python. In einer venv gilt sie nicht.
    marker = os.path.join(sysconfig.get_path("stdlib"), "EXTERNALLY-MANAGED")
    managed = os.path.exists(marker) and sys.prefix == sys.base_prefix
    flags = " --user --break-system-packages" if managed else ""
    return (
        f"Dieses Skript braucht PyYAML, und {python} hat es nicht. "
        f"Installieren mit: {python} -m pip install{flags} pyyaml"
    )


def yaml_module():
    """PyYAML, oder ein Abbruch mit Installationshinweis."""
    # Der Import steht in einer Funktion und nicht oben, damit ein fehlendes
    # PyYAML eine Meldung ergibt statt eines Stacktraces.
    try:
        import yaml
    except ImportError:
        raise Refusal(pyyaml_hint())
    return yaml


def pattern_from_seller_config(path: pathlib.Path) -> Optional[str]:
    """Der Prüfausdruck aus den Stammdaten, oder None, wenn keiner eingetragen ist."""
    yaml = yaml_module()
    try:
        seller_config = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, yaml.YAMLError) as error:
        raise Refusal(f"Stammdaten nicht lesbar ({path}): {error}")

    # Kein Eintrag heißt: nicht prüfen. Dieselbe Regel wie im Generator.
    return (seller_config or {}).get("rechnungsnummer-muster")


def taken_numbers(directory: pathlib.Path) -> dict[str, pathlib.Path]:
    """Die Nummern aus dem Feld `nummer` aller YAML-Dateien, mit der Datei, in der sie stehen."""
    yaml = yaml_module()
    # safe_load nähme den Leser in Python, auch wenn libyaml da ist: bei 2000
    # Dateien rund 13 statt 1,6 Sekunden (#564).
    loader = getattr(yaml, "CSafeLoader", yaml.SafeLoader)
    taken = {}
    # rglob: Ältere Jahrgänge liegen in Unterordnern, und wer eine Rechnung
    # einsortiert hat, bekommt trotzdem keine doppelte Nummer.
    for file in sorted(directory.rglob("*.yaml")):
        try:
            content = yaml.load(file.read_text(encoding="utf-8"), Loader=loader)
        except (yaml.YAMLError, UnicodeDecodeError) as error:
            # Warnen wie offene-abschlaege.py, nicht still überspringen: Eine
            # kaputte Datei kann eine Rechnung sein, deren Nummer vergeben ist.
            # Abbrechen dagegen hielte jede Rechnung an, weil irgendwo im
            # Verzeichnis eine unfertige Datei liegt.
            reason = getattr(error, "problem", None) or str(error).splitlines()[0]
            print(f"Übersprungen, nicht lesbar: {file}: {reason}", file=sys.stderr)
            continue
        # Stammdaten, vereinnahmungen.yaml und Ähnliches haben keine Nummer.
        if isinstance(content, dict) and "nummer" in content:
            # str(): Eine ungequotete Nummer kann YAML als Zahl lesen.
            taken.setdefault(str(content["nummer"]), file)
    return taken


def allocate(allocator: pathlib.Path, taken: list[str], invoice_date: datetime.date) -> str:
    """Die Nummer, die das Vergabeskript zu diesen belegten Nummern nennt."""
    try:
        run = subprocess.run(
            [str(allocator), invoice_date.isoformat()],
            input="".join(f"{number}\n" for number in taken),
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
    except OSError as error:
        raise Refusal(f"Das Vergabeskript {allocator} lässt sich nicht aufrufen, ist es ausführbar? {error}")
    # Nur die Meldung, nicht den Code: Ein Vergabeskript, das mit 3 endet,
    # sähe sonst wie ein Vorschlag aus.
    if run.returncode != 0:
        raise Refusal(run.stderr.rstrip("\n") or f"Das Vergabeskript {allocator} endet mit {run.returncode}.")
    lines = run.stdout.splitlines()
    if len(lines) != 1 or not lines[0].strip():
        raise Refusal(
            f"Das Vergabeskript {allocator} muss genau eine Zeile ausgeben, die Nummer. Es gab aus: {run.stdout!r}"
        )
    return lines[0]


def check(number: str, taken: dict[str, pathlib.Path], seller_config: Optional[pathlib.Path], origin: str) -> None:
    """Bricht ab, wenn die Nummer belegt ist oder nicht zum Muster der Stammdaten passt."""
    # Zeichen für Zeichen, wie der Generator Nummern vergleicht (#53).
    if number in taken:
        raise Refusal(f"Die Nummer {number} ist schon belegt, in {taken[number]}.")
    if seller_config is None:
        return
    pattern = pattern_from_seller_config(seller_config)
    # fullmatch, nicht search: Der Generator prüft mit Regex.matches, also
    # ebenfalls gegen die ganze Nummer.
    if pattern is not None and not re.fullmatch(pattern, number):
        raise Refusal(f"{origin} {number} passt nicht zum Muster '{pattern}' aus {seller_config}.")


def next_number(args) -> int:
    if not args.directory.is_dir():
        raise Refusal(f"Verzeichnis nicht gefunden: {args.directory}")

    allocator = args.directory / ROOT_FOLDER / ALLOCATOR
    if not allocator.is_file():
        raise Refusal(
            f"In der Ablage fehlt das Vergabeskript {allocator}. Die Vorlage liegt unter {TEMPLATE}: "
            f"kopieren und die zwei Zeilen NUMMERNKREIS und FORMAT eintragen."
        )

    taken = taken_numbers(args.directory)

    if args.invoice_number is not None:
        check(args.invoice_number, taken, args.seller_config, "Die genannte Nummer")
        print(args.invoice_number)
        return 0

    number = allocate(allocator, list(taken), args.invoice_date)
    # Dieselbe Nummer ohne belegte Nummern heißt: Im Nummernkreis gibt es keine
    # YAML-Rechnung. Ein Fehler des Vergabeskripts führt so höchstens zu einer
    # Rückfrage zu viel, nie zu einer zu wenig.
    proposal = allocate(allocator, [], args.invoice_date) == number
    if proposal:
        check(number, taken, args.seller_config, "Der Vorschlag")
        print(f"Im Nummernkreis dieses Datums gibt es noch keine Rechnung. Vorschlag: {number}", file=sys.stderr)
        return PROPOSAL

    check(number, taken, args.seller_config, "Die nächste Nummer")
    print(number)
    return 0


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
        "--rechnungsnummer",
        dest="invoice_number",
        metavar="RECHNUNGSNUMMER",
        default=None,
        help="Bestätigt einen Vorschlag oder nennt eine andere Nummer; sie wird nur geprüft",
    )
    p.add_argument(
        "--stammdaten",
        dest="seller_config",
        metavar="STAMMDATEN",
        type=pathlib.Path,
        default=None,
        help="Stammdaten, gegen deren rechnungsnummer-muster die Nummer geprüft wird",
    )
    try:
        return next_number(p.parse_args())
    except Refusal as refusal:
        print(refusal, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
