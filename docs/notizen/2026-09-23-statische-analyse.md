# Statische Analyse: SonarQube Cloud oder detekt

Notiz vom 23.09.2026, festgehalten für später. **Hier wird nichts entschieden.**
Anlass war die Frage, ob sich eine Anbindung an SonarQube Cloud lohnt – das
Projekt liegt weit unter der Grenze von 50.000 Zeilen, bis zu der der Dienst
auch private Repositories kostenlos analysiert. Statt das zu erörtern, wurde
nachgemessen, was ein Analysewerkzeug hier über ktlint hinaus findet: mit
detekt, lokal, ohne Änderung am Build.

## Was schon hält

Eine allgemeine Analyse träfe auf ein Projekt, das seine Netze schon hat:
ktlint für die Form, `PackageStructureTest` für die Paketgrenzen, die
Vergleichsdateien und das Pflichtangaben-Netz für das Blatt, den Compiler für
die Zusagen aus ADR-0004 und ADR-0007. Jedes davon hält eine Regel, die hier
begründet steht. Die Frage war also nicht, ob ein Werkzeug Befunde liefert,
sondern ob darunter einer ist, den keines dieser Netze fängt.

## Gemessen

detekt in zwei Fassungen, je mit der Vorgabe-Konfiguration:

- **1.23.8**, die letzte stabile (Februar 2025), ohne Typauflösung.
- **2.0.0-alpha.6** (August 2026) mit Typauflösung, also mit dem
  Klassenpfad aus Gradle. Nur so laufen die Regeln, die Typen kennen müssen,
  etwa die über `!!` oder `check` gegen `null`.

| | `src/main` | `src/test` |
|---|---:|---:|
| 1.23.8, ohne Typauflösung | 79 | 68 |
| 2.0.0-alpha.6, mit Typauflösung | 69 | 65 |

Nach Regel, `src/main`, 1.23.8:

| Regel | Anzahl | Befund |
|---|---:|---|
| `MagicNumber` | 31 | Konstanten von Verfahren (IBAN mod 97, Prüfziffer des BZSt), das Prüfdatum `EuMemberStates.AS_OF`, die Stelligkeit der Makros in `TemplateMacro`, zwei verschiedene Fristen von zwölf Monaten in `VatIdCheckRules`. Keiner. |
| `MaxLineLength` | 22 | detekt misst gegen 120 Zeichen, ktlint gegen die 140 aus `.editorconfig`. Eine zweite Stelle für dieselbe Regel, keiner. |
| `SwallowedException` | 8 | Jedes Mal die gewollte Übersetzung einer Ausnahme in eine Meldung (#52, #119), die Meldung der Ursache reist mit. Keiner. |
| Größe und Verzweigung | 15 | `LongMethod`, `CyclomaticComplexMethod`, `ReturnCount`, `ThrowsCount`, `TooManyFunctions`, `LongParameterList`. Der größte Ausschlag, `InvoiceRenderer.value` mit 50, ist das `when` ohne `else` über `TemplateValue`, das ADR-0017 so will; die Regelgruppen sind lange Listen von Regeln. Einzig `GenerateCommand.run` (75 Zeilen, Komplexität 22) zeigt auf etwas, das schon gesehen wurde: Beobachtung 1 der Notiz vom 16.09.2026. |
| `MatchingDeclarationName` | 1 | `EnumSerializer.kt` enthält die Klasse `CodeLookup`. Geschmack. |

In `src/test` sind es fast nur überlange Zeilen (59) und die Parameterlisten
der Testfabriken in `model/TestFixtures.kt`. Die drei `EqualsNullCall`
prüfen absichtlich, dass `equals(null)` falsch liefert.

Was **nur mit Typauflösung** dazukommt, sind die einzigen Befunde, die eine
Änderung wert wären – beide klein:

- `generate/render/ItemBlocks.kt`, `taxNotes`: erst `filter { … != null }`,
  dann `exemptionReason!!`. Mit `mapNotNull` bewiese der Compiler, was heute
  der Filter darüber zusagt (`UnsafeCallOnNullableType`).
- `generate/render/Template.kt`: `check(stelligkeit != null)` statt
  `checkNotNull` (`UseCheckNotNull`) – das Idiom, das das Projekt beim Lesen
  der Ressourcen sonst benutzt.

Im Test dazu ein `"%02d".format(…)` ohne `Locale` in
`NaechsteNummerSkriptTest` (`ImplicitDefaultLocale`). Im Produktivcode findet
dieselbe Regel nichts.

**Ergebnis:** Rund 150 Befunde, zwei davon eine Änderung wert, beide
kosmetisch und beide nur mit einer Typauflösung, die es stabil nicht gibt
(nächster Abschnitt). Der Rest ist entweder eine Regel, die hier schon
anders gehalten wird, oder eine Schwelle ohne Begründung. Konfigurieren ließe
sich das, aber dann bliebe von der Vorgabe wenig übrig, und für jede
Abschaltung schuldete man eine Zeile Begründung.

## Die Werkzeuge passen noch nicht zu Kotlin 2.4

Das stabile detekt ist gegen Kotlin 2.0.21 gebaut, das Projekt steht auf
2.4.20 (#158). Probiert an einem Klon, mit dem Gradle-Plugin 1.23.8:

- Unter dem lokalen JDK 26 bricht `detektMain` ab, weil der eingebaute
  Compiler die Versionsnummer `26.0.2.1` nicht lesen kann.
- Unter JDK 21, dem der CI, läuft es, liefert aber 13 Befunde
  `UnreachableCode` in `YamlLoader`, `GenerateCommand` und `PdfAValidator`,
  alle falsch – Code nach einem `try`, der sehr wohl erreicht wird. Den `!!`
  in `ItemBlocks` findet es dafür nicht.

detekt 2.0 passt zu Kotlin 2.4, ist aber seit Oktober 2025 im Alpha-Stadium.
Auch dort meldet der Lauf zwei Compilerfehler: `serializer()` in `YamlLoader`
bleibt unaufgelöst, weil der CLI das Compiler-Plugin von kotlinx.serialization
fehlt. Über das Gradle-Plugin von 2.0 wurde nicht gemessen.

Eine feste Einbindung hieße heute also: eine Alpha im Versionskatalog oder
eine stabile Fassung, deren Typauflösung falsch meldet. Beides ist ein Netz,
das anders aussieht, als es hält.

## Und SonarQube Cloud

Gemessen wurde detekt, nicht SonarQube. Übertragen lässt sich nur die Lage:
Die Kotlin-Regeln von SonarQube zielen auf dieselben Arten von Befunden –
Komplexität, Code-Smells, Ausnahmebehandlung –, und die liefern hier fast
nur Lärm. Dazu kommen Punkte, die detekt nicht hat:

- **Abdeckung, schief gemessen.** Ein Quality Gate verlangt üblicherweise
  eine Abdeckung auf neuem Code. Die CI läuft mit `-PohneToolchain`, ohne
  `ToolchainIntegrationTest` (#136); die Werkzeugseite stünde schlechter da,
  als sie ist.
- **Ein fremder Dienst.** Der Quelltext eines privaten Repositories läge bei
  SonarSource, unter dessen Nutzungsbedingungen. Aus demselben Grund steht der
  Gradle-Cache der CI auf `basic` und nicht auf `enhanced`. Echte Kundendaten
  liegen nicht im Repo, ein Ausschlussgrund ist das also nicht, aber eine
  Entscheidung.
- **Nicht nachgeprüft**, bevor es darauf ankommt: welche Zweige und Pull
  Requests der kostenlose Plan bei privaten Repositories analysiert und wie
  lange die Bedingungen so bleiben.

CodeQL, die naheliegende Alternative für die Sicherheitsseite, setzt bei
privaten Repositories ein kostenpflichtiges Produkt von GitHub voraus.

## Woran sich der Zeitpunkt erkennen ließe

- **An einem stabilen detekt für Kotlin 2.4.** Dann ist die Messung zu
  wiederholen, diesmal über das Gradle-Plugin mit dem Compiler-Plugin für
  kotlinx.serialization. Die Richtung wäre detekt in `./gradlew build` –
  lokal, ohne fremden Dienst, unter "ohne gültiges X kein grüner Build" – mit
  einer Auswahl von Regeln statt der Vorgabe: `MaxLineLength` gehört ktlint,
  `MagicNumber` und die Schwellen trügen hier nichts. Keine Baseline: Eine
  Liste stillgelegter Befunde sieht aus wie ein Netz und hält keines.
- **An einer zweiten Person im Code.** Heute hält das Review die Konventionen,
  und die meisten sind zu fachlich für ein Werkzeug. Schreibt jemand mit, der
  sie nicht kennt, lohnen sich auch die groben Regeln.
- **Am Wunsch, SonarQube kennenzulernen.** Das ist in einem Lernprojekt ein
  eigener Grund – dann aber als Versuch mit einem einzigen Lauf und
  ausdrücklich ohne Quality Gate im Weg zum Merge, bis sich zeigt, was er
  findet.

## So wurde gemessen

Die JARs von den Releases unter `github.com/detekt/detekt`, außerhalb des
Repos abgelegt. Der Klassenpfad für die Typauflösung kam aus einem
Init-Skript, das `sourceSets.main.compileClasspath` ausgibt – ohne Eintrag im
Build.

```bash
java -jar detekt-cli-1.23.8-all.jar --input src/main/kotlin --report txt:main.txt
java -jar detekt-cli-2.0.0-alpha.6-all.jar --analysis-mode full --jvm-target 21 \
  --classpath "$KLASSENPFAD" --input src/main/kotlin --report checkstyle:main.xml
```

Das Gradle-Plugin lief in einem Klon mit
`id("io.gitlab.arturbosch.detekt") version "1.23.8"` und
`detekt { ignoreFailures = true }`.
