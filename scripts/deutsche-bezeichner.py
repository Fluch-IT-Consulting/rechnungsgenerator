#!/usr/bin/env python3
"""Findet deutsche Bezeichner im Kotlin- und Python-Code (#338, #351).

CLAUDE.md: Bezeichner Englisch, Kommentare Deutsch – mit einer engen
Ausnahme für Fachbegriffe aus CONTEXT.md ohne eingebürgerte englische
Entsprechung. Dieses Skript misst, wie weit der Code davon abweicht, damit
"fertig" in #338 eine Zahl ist und kein Eindruck.

Gemessen werden deklarierte Namen: Funktionen, val/var samt Destrukturierung,
Parameter, Lambda-Parameter und Typen. Strings und Kommentare zählen nicht –
Testnamen sind deutsche Sätze und sollen es bleiben. Python liest das Skript
mit ast, also genau; für Kotlin gibt es keinen Parser in der Standardbibliothek,
dort liest es mit regulären Ausdrücken und Klammerzählung. Jeder Name wird an den
Wechseln der Groß- und Kleinschreibung zerlegt, und ein Teil, der in keinem
englischen Wörterbuch steht, macht ihn verdächtig.

Das ist eine Schätzung und keine Grammatik: Ein Wörterbuch kennt nicht jedes
Fachwort der Technik, und ein deutsches Wort, das zufällig auch englisch ist,
rutscht durch. Was fälschlich gemeldet wird, gehört in TECHNICAL; was
durchrutscht, in GERMAN_ALSO_ENGLISH; was die Ausnahme erlaubt, in GLOSSARY –
nie eine davon aus Bequemlichkeit.

Aufruf:  deutsche-bezeichner.py [<verzeichnis oder datei> ...] [--vorkommen]
         ohne Verzeichnis: der Kotlin-Code, scripts/ und die Skripte des
         rechnung-Skills
Endet mit 1, solange es etwas meldet.
"""
import argparse
import ast
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
    timeout cancelled descriptor deserialize deserializer encoder namespace decoder entries formatted
    padded arity neighbours website subcommand newcommand typesetter toolchain
    acc lua fixture fixtures stdout stderr stdin println runner prepayment
    prepayments setup builtin bytes handwritten newline enum whitespace
    unbuildable info kotlin destructuring
    rechnungsgenerator helsinki prag
    """.split()
)

# Deutsch, obwohl im englischen Wörterbuch: "alter" ist dort ein Verb,
# "fremd" ein Altwort, "listen" ein Verb. Gefunden, indem alle Wortteile
# durchgesehen wurden, die das Wörterbuch durchließ.
GERMAN_ALSO_ENGLISH = set(
    """
    alt alter aus datum extern firma fremd frist gedeckt grund hier inland
    land lang leer listen muster rot steller und wert wurzel als art
    """.split()
)

# Ganze Namen, die deutsch sein sollen: das Vokabular der Platzhalter in den
# Vorlagen (ADR-0017). Die Platzhalter selbst heißen wie in der Vorlage,
# <<ZAHLUNGSZIEL>>, und eine benannte Menge von ihnen gehört dazu.
TEMPLATE_VOCABULARY = {"ZAHLUNGSANGABE"}

# Tests, die nach der Datei heißen, die sie prüfen – so wie LatexCompilerTest
# nach LatexCompiler. Die Skripte des rechnung-Skills heißen deutsch, und ein
# übersetzter Klassenname verlöre den Weg zu seiner Datei.
FILE_NAMED = {"OffeneAbschlaegeSkriptTest", "NaechsteNummerSkriptTest"}

DECLARATIONS = [
    re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)\s*\("),
    re.compile(r"\b(?:val|var)\s+(\w+)"),
    re.compile(r"\b(?:class|object|interface)\s+(\w+)"),
]
DESTRUCTURING = re.compile(r"\b(?:val|var)\s*\(([^)]*)\)")
# Schleifenvariablen: for (x in …) und for ((a, b) in …).
FOR_VARIABLES = re.compile(r"\bfor\s*\(\s*(?:\(([^)]*)\)|(\w+))\s+in\b")
LAMBDA_PARAMS = re.compile(r"\{\s*\(?([\w\s,()]+?)\)?\s*->")
FUN_START = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?[\w.<>?]+\s*\(")


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
    """Der Code ohne Kommentare und ohne den Text in Strings.

    Was in einem String als `${...}` steht, ist Code und bleibt; ein
    regulärer Ausdruck verlöre sich in `"${if (x) "a" else "b"}"` an den
    inneren Anführungszeichen und hielte "a" für einen Bezeichner.
    """
    code, _ = _scan_code(source, 0, inside_template=False)
    return code


def _scan_code(source: str, i: int, inside_template: bool) -> tuple[str, int]:
    out, depth = [], 0
    while i < len(source):
        c = source[i]
        if inside_template and c == "}" and depth == 0:
            return "".join(out), i
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        if source.startswith("//", i):
            end = source.find("\n", i)
            i = len(source) if end < 0 else end
        elif source.startswith("/*", i):
            i = source.find("*/", i) + 2
        elif source.startswith('"""', i):
            text, i = _scan_string(source, i + 3, '"""')
            out.append('""' + text)
        elif c == '"':
            text, i = _scan_string(source, i + 1, '"')
            out.append('""' + text)
        elif c == "'":
            match = re.match(r"'(\\.|[^'\\])*'", source[i:])
            out.append("''")
            i += len(match.group(0))
        else:
            out.append(c)
            i += 1
    return "".join(out), i


def _scan_string(source: str, i: int, end: str) -> tuple[str, int]:
    """Liest einen String bis zu seinem Ende und liefert nur seine Vorlagen."""
    templates = []
    while i < len(source) and not source.startswith(end, i):
        if end == '"' and source[i] == "\\":
            i += 2
        elif source.startswith("${", i):
            code, i = _scan_code(source, i + 2, inside_template=True)
            templates.append(" " + code + " ")
            i += 1
        elif source[i] == "$" and re.match(r"[A-Za-z_]", source[i + 1 : i + 2]):
            match = re.match(r"\w+", source[i + 1 :])
            templates.append(" " + match.group(0) + " ")
            i += 1 + len(match.group(0))
        else:
            i += 1
    # Ein Raw-String endet an den letzten drei einer Folge von
    # Anführungszeichen: """a: "1"""" enthält a: "1".
    if end == '"""':
        while source.startswith('""""', i):
            i += 1
    return "".join(templates), i + len(end)


def fun_parameter_lists(source: str) -> list[str]:
    """Die Parameterlisten aller Funktionen, je auf der obersten Ebene.

    Mit Klammerzählung und nicht mit einem regulären Ausdruck: Ein
    Vorgabewert wie `viewer: PdfViewer = FakePdfViewer()` schlösse die Liste
    sonst an seiner Klammer, und jeder Parameter danach fehlte. Was tiefer
    liegt – Argumente eines Vorgabewerts, Typparameter –, fällt weg, damit
    `Path.of("x")` keinen Namen beisteuert.
    """
    lists = []
    for match in FUN_START.finditer(source):
        depth, top, i = 1, [], match.end()
        while i < len(source) and depth:
            c = source[i]
            if c in "([{<":
                depth += 1
            elif c in ")]}>":
                depth -= 1
            if depth == 1 or (depth == 0 and c != ")"):
                top.append(c if c not in "([{<" or depth == 1 else "")
            i += 1
        lists.append("".join(top))
    return lists


def declared_names(source: str) -> list[str]:
    names = []
    for pattern in DECLARATIONS:
        names += pattern.findall(source)
    for group in DESTRUCTURING.findall(source):
        names += re.findall(r"\b(\w+)\b", group)
    for group, single in FOR_VARIABLES.findall(source):
        names += re.findall(r"\b(\w+)\b", group) if group else [single]
    for group in LAMBDA_PARAMS.findall(source):
        names += re.findall(r"\b([a-z]\w*)\b", group)
    for signature in fun_parameter_lists(source):
        names += re.findall(r"(?:^|,)\s*(?:vararg\s+|val\s+|var\s+)?([a-z]\w*)\s*:", signature)
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


def python_names(source: str) -> tuple[list[str], list[str]]:
    """Deklarierte und überhaupt verwendete Namen eines Python-Skripts."""
    tree = ast.parse(source)
    declared, used = [], []
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            declared.append(node.name)
            used.append(node.name)
        elif isinstance(node, ast.arg):
            declared.append(node.arg)
            used.append(node.arg)
        elif isinstance(node, ast.Name):
            used.append(node.id)
            if isinstance(node.ctx, ast.Store):
                declared.append(node.id)
        elif isinstance(node, ast.Attribute):
            # args.verzeichnis: Das Attribut kommt aus argparse und heißt,
            # wie dest es nennt – ein deutsches fiele sonst durch.
            used.append(node.attr)
    return declared, used


def kotlin_names(source: str) -> tuple[list[str], list[str]]:
    code = strip_strings_and_comments(source)
    return declared_names(code), re.findall(r"\b\w+\b", code)


DEFAULT_ROOTS = ["src/main/kotlin", "src/test/kotlin", "scripts", ".claude/skills/rechnung/scripts"]


def main() -> int:
    parser = argparse.ArgumentParser(description="Findet deutsche Bezeichner im Kotlin- und Python-Code.")
    parser.add_argument("roots", nargs="*", default=DEFAULT_ROOTS)
    parser.add_argument("--vorkommen", action="store_true", help="auch jede Verwendung zählen, nicht nur Deklarationen")
    args = parser.parse_args()

    words = load_words()
    # Eine einzelne Datei ergäbe mit rglob "0 von 0 Dateien" – das sähe aus
    # wie ein sauberes Ergebnis und wäre keines.
    paths = []
    for root in map(pathlib.Path, args.roots):
        if not root.exists():
            sys.exit(f"{root} gibt es nicht.")
        paths += [root] if root.is_file() else sorted([*root.rglob("*.kt"), *root.rglob("*.py")])
    names = {
        path: (python_names if path.suffix == ".py" else kotlin_names)(path.read_text(encoding="utf-8"))
        for path in paths
    }

    german = {
        name
        for declared, _ in names.values()
        for name in declared
        if name not in TEMPLATE_VOCABULARY | FILE_NAMED
        and any(len(p) > 2 and not is_english(p, words) for p in word_parts(name))
    }

    # Je Datei die gefundenen Namen und wie oft sie dort stehen – deklariert
    # oder, mit --vorkommen, überhaupt.
    per_file: dict[pathlib.Path, collections.Counter] = {}
    for path, (declared, used) in names.items():
        tokens = used if args.vorkommen else declared
        found = collections.Counter(t for t in tokens if t in german)
        if found:
            per_file[path] = found

    for path, found in sorted(per_file.items(), key=lambda item: -sum(item[1].values())):
        listed = ", ".join(f"{name} {count}" for name, count in found.most_common())
        print(f"{sum(found.values()):5d}  {path}: {listed}")

    total = sum(sum(found.values()) for found in per_file.values())
    unit = "Vorkommen" if args.vorkommen else "Deklarationen"
    print(f"\n{len(german)} deutsche Bezeichner, {total} {unit}, in {len(per_file)} von {len(names)} Dateien.")
    return 1 if german else 0


if __name__ == "__main__":
    sys.exit(main())
