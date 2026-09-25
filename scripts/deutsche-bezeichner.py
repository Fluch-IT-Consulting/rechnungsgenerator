#!/usr/bin/env python3
"""Findet deutsche Bezeichner im Kotlin-Code (#338).

CLAUDE.md: Bezeichner Englisch, Kommentare Deutsch – mit einer engen
Ausnahme für Fachbegriffe aus CONTEXT.md ohne eingebürgerte englische
Entsprechung. Dieses Skript misst, wie weit der Code davon abweicht, damit
"fertig" in #338 eine Zahl ist und kein Eindruck.

Gemessen werden deklarierte Namen: Funktionen, val/var samt Destrukturierung,
Parameter, Lambda-Parameter und Typen. Strings und Kommentare zählen nicht –
Testnamen sind deutsche Sätze und sollen es bleiben. Jeder Name wird an den
Wechseln der Groß- und Kleinschreibung zerlegt, und ein Teil, der in keinem
englischen Wörterbuch steht, macht ihn verdächtig.

Das ist eine Schätzung und keine Grammatik: Ein Wörterbuch kennt nicht jedes
Fachwort der Technik, und ein deutsches Wort, das zufällig auch englisch ist,
rutscht durch. Was fälschlich gemeldet wird, gehört in TECHNICAL; was
durchrutscht, in GERMAN_ALSO_ENGLISH; was die Ausnahme erlaubt, in GLOSSARY –
nie eine davon aus Bequemlichkeit.

Aufruf:  deutsche-bezeichner.py [<verzeichnis> ...] [--vorkommen]
         ohne Verzeichnis: src/main/kotlin und src/test/kotlin
Endet mit 1, solange es etwas meldet.
"""
import argparse
import collections
import pathlib
import re
import sys

# macOS bringt beide mit; web2a ergänzt Wortverbindungen.
DICTIONARIES = ["/usr/share/dict/words", "/usr/share/dict/web2a"]

# Die Ausnahme aus CLAUDE.md, samt den Formen, in denen sie im Code stehen.
# Nicht dazu gehören Fachwörter, für die der Code längst ein englisches Wort
# führt: "rechnung" heißt dort Invoice, "stammdaten" SellerConfig.
GLOSSARY = set(
    """
    abschlag abschlaege anteil anteile restbetrag vereinnahmt
    zeitraum zeitraeume skonto stufe stufen pflichtangaben
    """.split()
)

# Englisch, aber in keinem der Wörterbücher: Abkürzungen, Werkzeuge,
# Fachwörter der Technik, Eigennamen der Designs.
TECHNICAL = set(
    """
    pdf pdfa pdfs pdfx yaml yml json toml xmp xml xmpdata tex latex lualatex
    verapdf pdftotext pdftoppm fancyhdr longtable xltabular tabularx hbox vbox
    iban bic vat ust ustid id ids uuid utf utc cli kt kts dir tmp temp rgb dpi
    mm pt pos eu de at ch fr us regex str num int bool eq ok src dst len idx
    calc util utils impl misc attr param params prop props arg args config env
    io nio api url uri jvm jdk gradle ktlint konsist kotest kaml svg png
    serializer serializers validator validators metadata checksum email
    timeout cancelled descriptor deserialize encoder decoder entries formatted
    padded arity neighbours website subcommand newcommand typesetter toolchain
    acc lua fixture fixtures stdout stderr stdin println runner prepayment
    prepayments setup builtin bytes handwritten newline enum whitespace
    rechnungsgenerator helsinki prag
    """.split()
)

# Deutsch, obwohl im englischen Wörterbuch: "alter" ist dort ein Verb,
# "fremd" ein Altwort, "listen" ein Verb. Gefunden, indem alle Wortteile
# durchgesehen wurden, die das Wörterbuch durchließ.
GERMAN_ALSO_ENGLISH = set(
    """
    alt alter aus datum extern firma fremd frist gedeckt grund hier inland
    land lang leer listen muster rot steller und wert wurzel
    """.split()
)

# Ganze Namen, die deutsch sein sollen: das Vokabular der Platzhalter in den
# Vorlagen (ADR-0017). Die Platzhalter selbst heißen wie in der Vorlage,
# <<ZAHLUNGSZIEL>>, und eine benannte Menge von ihnen gehört dazu.
TEMPLATE_VOCABULARY = {"ZAHLUNGSANGABE"}

DECLARATIONS = [
    re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)\s*\("),
    re.compile(r"\b(?:val|var)\s+(\w+)"),
    re.compile(r"\b(?:class|object|interface)\s+(\w+)"),
]
DESTRUCTURING = re.compile(r"\b(?:val|var)\s*\(([^)]*)\)")
LAMBDA_PARAMS = re.compile(r"\{\s*\(?([\w\s,()]+?)\)?\s*->")
FUN_PARAMS = re.compile(r"\bfun\s[^{=]*?\(([^)]*)\)")


def load_words() -> set[str]:
    words = set()
    for path in DICTIONARIES:
        try:
            text = pathlib.Path(path).read_text(encoding="latin-1")
        except OSError:
            sys.exit(f"Wörterbuch {path} fehlt – das Skript braucht die Wortlisten von macOS.")
        for line in text.splitlines():
            words.update(p for p in re.split(r"[\s-]+", line.lower()) if p)
    return words


def strip_strings_and_comments(source: str) -> str:
    source = re.sub(r'"""[\s\S]*?"""', '""', source)
    source = re.sub(r'"(?:\\.|[^"\\])*"', '""', source)
    source = re.sub(r"/\*[\s\S]*?\*/", "", source)
    return re.sub(r"//[^\n]*", "", source)


def declared_names(source: str) -> list[str]:
    names = []
    for pattern in DECLARATIONS:
        names += pattern.findall(source)
    for group in DESTRUCTURING.findall(source):
        names += re.findall(r"\b(\w+)\b", group)
    for group in LAMBDA_PARAMS.findall(source):
        names += re.findall(r"\b([a-z]\w*)\b", group)
    for signature in FUN_PARAMS.findall(source):
        names += re.findall(r"\b([a-z]\w*)\s*:", signature)
    return [n for n in names if n not in ("_", "it", "this")]


def word_parts(name: str) -> list[str]:
    spaced = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name)
    spaced = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1 \2", spaced)
    return [p.lower() for p in re.split(r"[\s_0-9]+", spaced) if p]


def is_english(part: str, words: set[str]) -> bool:
    if part in GERMAN_ALSO_ENGLISH:
        return False
    if part in words or part in TECHNICAL or part in GLOSSARY:
        return True
    # Das Wörterbuch führt Grundformen: "results" steht nur als "result"
    # drin, "categories" als "category", "earlier" als "early", "managing"
    # als "manage".
    if part.endswith(("ies", "ier")) and part[:-3] + "y" in words:
        return True
    if part.endswith("ing") and part[:-3] + "e" in words:
        return True
    return any(part.endswith(s) and part[: -len(s)] in words for s in ("s", "es", "ed", "ing", "er", "ers", "ly", "d"))


def main() -> int:
    parser = argparse.ArgumentParser(description="Findet deutsche Bezeichner im Kotlin-Code.")
    parser.add_argument("roots", nargs="*", default=["src/main/kotlin", "src/test/kotlin"])
    parser.add_argument("--vorkommen", action="store_true", help="auch jede Verwendung zählen, nicht nur Deklarationen")
    args = parser.parse_args()

    words = load_words()
    sources = {
        path: strip_strings_and_comments(path.read_text(encoding="utf-8"))
        for root in args.roots
        for path in sorted(pathlib.Path(root).rglob("*.kt"))
    }

    german = {
        name
        for source in sources.values()
        for name in declared_names(source)
        if name not in TEMPLATE_VOCABULARY
        and any(len(p) > 2 and not is_english(p, words) for p in word_parts(name))
    }

    # Je Datei die gefundenen Namen und wie oft sie dort stehen – deklariert
    # oder, mit --vorkommen, überhaupt.
    per_file: dict[pathlib.Path, collections.Counter] = {}
    for path, source in sources.items():
        tokens = re.findall(r"\b\w+\b", source) if args.vorkommen else declared_names(source)
        found = collections.Counter(t for t in tokens if t in german)
        if found:
            per_file[path] = found

    for path, found in sorted(per_file.items(), key=lambda item: -sum(item[1].values())):
        listed = ", ".join(f"{name} {count}" for name, count in found.most_common())
        print(f"{sum(found.values()):5d}  {path}: {listed}")

    total = sum(sum(found.values()) for found in per_file.values())
    unit = "Vorkommen" if args.vorkommen else "Deklarationen"
    print(f"\n{len(german)} deutsche Bezeichner, {total} {unit}, in {len(per_file)} von {len(sources)} Dateien.")
    return 1 if german else 0


if __name__ == "__main__":
    sys.exit(main())
