# Rechnungsgenerator

Kotlin/Gradle-Projekt: erzeugt aus einer Rechnungsdatei und den Stammdaten
(beide YAML) eine deutsche Rechnung als PDF/A-1b, mit `--zugferd` als
E-Rechnung (PDF/A-3u mit XML). Satz über LuaLaTeX, PDF/A-Prüfung über
veraPDF. Kommandos: `erzeuge`, `abschlaege`, `einrichten`, `xml`.

Diese Datei sagt, wie man hier arbeitet. Warum etwas so ist, steht im ADR,
im Issue oder am Code – nicht hier.

## Befehle

- `./gradlew build` – kompiliert, testet, lintet; das volle Netz samt
  LuaLaTeX und PDF/A-Prüfung
- `./gradlew build -PohneToolchain` – ohne die Tests mit Tag `Toolchain`, so
  läuft die CI; reicht für Änderungen abseits von Satz und E-Rechnung
- `./gradlew test`, `./gradlew ktlintCheck` / `ktlintFormat`
- `./gradlew installDist` – Kommando nach `build/install/rechnungsgenerator/bin/`
- `EU_LISTE_ALTERSPRUEFUNG=aus ./gradlew build` – einen älteren Stand bauen,
  etwa bei `git bisect`; der Altersnachweis der EU-Liste gilt dann als
  übersprungen. Stände vor #79 kennen den Schalter nicht.
- `scripts/deutsche-bezeichner.py` – misst deutsche Bezeichner, endet mit 1,
  solange es etwas findet

## Wo was steht

- `CONTEXT.md` – das Glossar. Es zieht die Grenze der Begriffe.
- `docs/adr/` – die Entscheidungen, der Dateiname sagt welche. Vor einer
  Änderung das ADR des Bereichs lesen, samt „Verworfen“ und Nachträgen.
  Entscheidungen ohne ADR stehen im Issue, auf das Code oder Test verweisen.
- `docs/notizen/` – durchdachte, aber **nicht entschiedene** Fragen, je eine
  Datei mit Datum. Wer eine davon neu aufwirft, liest erst nach; binden tun
  sie nicht. Eine Notiz steht auf heutigem Stand, ohne Nachträge. Ist ihre
  Frage entschieden, fällt sie weg, und das ADR nennt das Issue oder den Pull
  Request, in dem sie steht (#470).
- `src/main/resources/templates/` – je Design eine Vorlage (`helsinki.tex`,
  `prag.tex`), je Design und Sprache eine Sprachdatei (`prag-en-gb.tex`) und
  die gemeinsame `grundlage.sty`.
- `src/test/resources/` – Beispielrechnungen und Vergleichsdateien
  (`*-erwartet.tex`, `*-inhalt.tex`, `*-erwartet.xml`). Welches Beispiel zu
  welcher Rechnungsart gehört, steht in `exampleInvoices`
  (`core/model/TestFixtures.kt`).
- Das Pflichtangaben-Netz setzt die Beispiele zu echten PDFs und prüft sie:
  vier Klassen über `PflichtangabenRun` in `generate/toolchain` (Test).
- `.claude/skills/rechnung/` – der Skill zum Anlegen einer Rechnung, samt
  `naechste-nummer.py` und `offene-abschlaege.py`.

### Pakete

```
Main.kt           Einstieg; übersetzt die Fachfehler aller Kommandos
generate/         erzeuge
  render/         LaTeX aus der AcceptedInvoice
  einvoice/       XML der E-Rechnung (CiiWriter)
  toolchain/      Satz und Einbetten hinter der Naht Typesetter
prepayments/      abschlaege
setup/            einrichten – kennt aus dem Kern nur model
xml/              xml – kennt den Kern gar nicht
toolchain/        gemeinsamer Rand: PDF/A- und E-Rechnungs-Prüfer,
                  ProcessRunner, XmlParser; importiert nichts aus dem Projekt
core/             accepted → rules → calculation → model → localization → values
                  Blätter ohne Projektimporte: text, serialization
```

Ein Kommando importiert aus seinem Paket, dem Rand und dem Kern; was nur ein
Kommando braucht, bleibt in dessen Paket. Der Schnitt trennt den Rand, nicht
die Mitte: Was beide Kommandos brauchen, ist der größere Teil, deshalb bleibt
der Kern beisammen. Maßgeblich ist die Tabelle in `PackageStructureTest`; die
Gründe stehen in #107, #304, #310, #312, #431 und #432.

## Arbeitsregeln

### Rechnen und prüfen

- Geld nur als `BigDecimal`: Einzelpreise als `Price`, Beträge als `Money`,
  der einzige Übergang ist `Price.roundToAmount()`. Summen, Steuer und
  Fälligkeit rechnet Kotlin; die Vorlage setzt nur (ADR-0017, ADR-0021).
- Eine neue Prüfung ist ein Befund mit Kennung in `Rule`, in einer
  Regelgruppe unter `core/rules` neben ihrem gleichnamigen Test, geprüft am
  fertig gerechneten Ergebnis (ADR-0004). Kennungen sind deutsch und nicht
  nummeriert; Tests fragen die Kennung, nicht den Wortlaut.
- Wo ein Kommentar, ein Testname oder ein Dokument eine Regel nennt, nennt es
  ihre Kennung, etwa `ABSCHLAG_OHNE_VEREINNAHMUNG`.
- Ein `when` über `InvoiceKind`, `InvoiceType` oder `Language` hat kein
  `else`. Eine neue Rechnungsart entscheidet in jeder Regelgruppe, im
  Renderer, im `InvoiceVariableResolver` und im `CiiWriter`; nur
  `prepaymentOf` hat ein `else` (ADR-0008, ADR-0023). Ihr YAML-Wert heißt wie
  im Glossar, ihr Eintrag in `InvoiceType` nennt jede Antwort ausdrücklich
  (ADR-0009).
- Eingaben liest `YamlLoader`, und er übersetzt die Lesefehler samt
  Dateiname (#52).

### Satz

- Der Renderer liefert Werte und Makroaufrufe, das Satzbild steht in der
  Vorlage. Wer für ein Design ein Wort braucht, das `TemplateValue` und
  `TemplateMacro` nicht haben, liest zuerst ADR-0017.
- In `grundlage.sty` gehört nur, was in einem anderen Design *falsch* wäre,
  nicht was nur *anders* wäre (ADR-0022).
- Ziffern kommen als `\betrag{8337.50}` mit `toPlainString()`, die
  Schreibweise wählt die Sprachdatei (ADR-0021).
- Ein Logo ist ein TikZ-Fragment; eine Bilddatei per `\includegraphics`
  bricht PDF/A-1 (ADR-0012).
- Was die Bytes des PDF bestimmt, gehört in `PdfSources`. Der Jobname bleibt
  `fileBaseName`: pdfx bildet aus ihm die DocumentID (ADR-0005).
- **Neues Design** (ADR-0011): eine Vorlage, die Grundlage und
  `\input{sprache}` lädt und die Folgeseiten selbst setzt (ADR-0020); je
  Sprache eine Sprachdatei mit allen `\wort…`, jedem `\zeichen…` und
  `\eurobetrag` (ADR-0025 bis ADR-0027); ein Eintrag in `Design`; je Sprache
  ein Volltext als Vergleichsdatei; ein Eintrag in `PflichtangabenRun.designs`.
- **Neue Sprache** (ADR-0025): ein Wortlaut in jedem `when`. Kommt sie
  schrittweise, bringt sie die Regel mit, die ablehnt, was sie noch nicht
  kann. In englischen Sätzen steht ein Datum am Satzende oder vor einem Komma.
  Deutsch bleibt, was sich an den Rechnungssteller richtet: Befunde,
  Meldungen, YAML-Schlüssel und -Werte, Jobname, BT-33.
- **Neue Währung** (ADR-0026): ein Eintrag in `Currency` und ein `\zeichen…`
  in jeder Sprachdatei jedes Designs.

### E-Rechnung und Werkzeuge

- Das XML schreibt `CiiWriter`, jede Zahl aus der `CalculatedInvoice`. Kann
  er eine Angabe nicht schreiben, bricht `erzeuge --zugferd` mit einer
  Kennung für genau sie ab, statt sie wegzulassen (ADR-0023).
- XML liest das Programm nur über `XmlParser`.
- Bei einem Versionssprung von Mustang zuerst `EInvoiceEmbedderTest` lesen.
  Bringt er eine neue ZUGFeRD-Fassung mit, braucht `CiiWriterTest` den neuen
  Pfad zum Schematron (`/xslt/ZF_…`).
- Externe Programme laufen über `ProcessRunner`.
- veraPDF ist unter **MPL-2.0** eingebunden, nicht unter GPLv3+ (ADR-0014).

### Kommandos

- Ein Unterkommando je Art von Ergebnis, kein Schalter, der die Ausgabe
  wechselt (ADR-0003).
- stdout trägt nur das Ergebnis; was mit einem Code ungleich 0 endet, steht
  auf stderr. Fachfehler (`InputFileException`, `ToolchainException`,
  `RejectedException`) wirft das Kommando durch, übersetzt werden sie in
  `execute()` (#119).
- Kommt ein zweiter Weg zu einer Rechnungsdatei, ziehen Zieldatei,
  Überschreibschutz und PDF/A-Urteil aus `GenerateCommand.run` heraus, statt
  abgeschrieben zu werden (#359).

### Tests

- Ohne gültiges PDF/A kein grüner Build. Ein Test, dem ein optionales
  Programm fehlt, gilt als übersprungen, nie als bestanden.
- Wird eine Vergleichsdatei rot, ist das **eine Frage und keine Aufgabe**:
  erst klären, ob die Änderung gewollt ist, dann die Datei neu erzeugen und
  das PDF gegen das vorherige prüfen. Gleiche Prüfsumme heißt: nichts
  geändert; weicht sie ab, entscheidet das Bild (`pdftoppm`), denn schon eine
  Leerzeile der Vorlage ändert die Trailer-ID. Einen Schalter zum
  Neu-Erzeugen gibt es nicht.
- Eine neue schwere Testklasse in `generate.toolchain` kann die Verteilung
  auf die Forks verschieben; die Messung steht an `maxParallelForks` in
  `build.gradle.kts`.
- Die YAML-Beispiele und Skripte des `rechnung`-Skills stehen unter Test;
  nach einer Änderung am Skill bauen.

### Paketstruktur

- Ein neues Paket kommt in die Tabelle von `PackageStructureTest`.
- Importe aus dem eigenen Paket lässt ktlint stehen; nach einem Umzug von
  Hand löschen.
- Die Testpakete spiegeln die Hauptpakete. Geteilte Helfer liegen im
  Testpaket dessen, was sie bauen oder prüfen (`core/model/TestFixtures.kt`,
  `core/rules/CheckHelpers.kt`, `core/accepted/AcceptedHelpers.kt`).

## Konventionen

- Bezeichner Englisch, Kommentare Deutsch. Ein Kommentar **warnt**
  (ADR-0028): Er steht nur, wo man die Stelle ohne ihn falsch ändern würde,
  in ein bis zwei Zeilen. BT-Nummern, Paragraphen und Verweise auf ADR, Issue
  oder Regel sind immer erlaubt; Verworfenes steht im ADR, Geschichte im
  Issue.
- **Fachbegriffe aus `CONTEXT.md` ohne eingebürgerte englische Entsprechung**
  heißen auch im Code so, bis in lokale Variablen: `abschlag`, `anteil`,
  `restbetrag`, `vereinnahmt`, `zeitraum`. Eine Übersetzung wie
  `downPayment` wäre ein zweites Vokabular neben dem Glossar. Hat der Begriff
  eine eingebürgerte Entsprechung, gilt sie: `invoiceNumber` wie BT-1. Alles
  ohne Fachbezug bleibt englisch: `expected`, `rows`, `message`.
- **Keine echten Firmen- oder Kundendaten** – nicht im Code, nicht in
  Testdaten, Dokumentation und Skills, und erst recht nicht in GitHub-Issues:
  Die werden mit dem Repo öffentlich, und ihre Versionshistorie und die
  Benachrichtigungsmail behalten jeden Text. Beispiele mit fiktiver Firma,
  „Kunde A“, Test-IBAN (DE89 3704 0044 …) und erfundener USt-IdNr. Die echten
  Stammdaten liegen außerhalb des Repos unter `RECHNUNGEN_DIR` aus
  `.claude/settings.local.json`.
- ktlint muss vor jedem Commit grün sein. Ein Hook in `.claude/settings.json`
  ruft nach jeder Änderung an einer `.kt`/`.kts`-Datei `ktlintFormat` auf.
- Ein Kommentar im Versionskatalog, der ein Issue nennt, ist eine
  **Bedingung**: Bevor ein Dependabot-PR diese Zeile anhebt, das Issue lesen;
  trägt seine Begründung noch, geht der Bump durch, sonst nicht. Das ist eine
  Gewohnheit und kein Netz; warum kein `ignore` in `dependabot.yml`, steht in
  #253.

## Umgebung

- macOS, TeX Live lokal (`lualatex` in `/Library/TeX/texbin`), JDK 26 lokal;
  der Build zielt per Toolchain auf JDK 21.
- `pdftotext` und `pdftoppm` aus poppler (Homebrew `poppler`); `pdftotext`
  braucht auch der Build.
- veraPDF (Homebrew `verapdf`) ist optional: nur für
  `--externe-pdfa-pruefung`; ohne es überspringen sich zwei Tests.
- `python3` mit PyYAML (`pip3 install pyyaml`) für die Skripte des
  `rechnung`-Skills – auch der Build ruft sie auf.

## Agent skills

### Issue tracker

Issues und Specs liegen als GitHub-Issues in
`Fluch-IT-Consulting/rechnungsgenerator`, bedient über die `gh`-CLI.
Siehe `docs/agents/issue-tracker.md`.

### Triage labels

Die fünf Standardrollen, Label gleich Rollenname.
Siehe `docs/agents/triage-labels.md`.

### Domain docs

Single-Context: ein `CONTEXT.md` und `docs/adr/` in der Wurzel.
Siehe `docs/agents/domain.md`.
