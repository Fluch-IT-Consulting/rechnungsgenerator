# Statische Analyse: SonarQube Cloud oder detekt

Notiz vom 23.09.2026, Stand 29.09.2026. **Hier wird nichts entschieden.**
Anlass war die Frage, ob sich SonarQube Cloud lohnt; das Projekt liegt weit
unter den 50.000 Zeilen, bis zu denen der Dienst auch private Repositories
kostenlos analysiert. Statt das zu erörtern, wurde gemessen, was ein
Analysewerkzeug hier über ktlint hinaus findet: mit detekt, lokal, ohne
Änderung am Build.

Die Frage war nicht, ob es Befunde liefert, sondern ob einer darunter ist,
den keines der vorhandenen Netze fängt: ktlint, `PackageStructureTest`, die
Vergleichsdateien, das Pflichtangaben-Netz, der Compiler.

## Gemessen am 23.09.2026

detekt mit der Vorgabe-Konfiguration, in zwei Fassungen:

| | `src/main` | `src/test` |
|---|---:|---:|
| 1.23.8, die letzte stabile, ohne Typauflösung | 79 | 68 |
| 2.0.0-alpha.6 mit Typauflösung | 69 | 65 |

In `src/main`, nach Regel:

- **`MagicNumber` (31)**: Konstanten von Verfahren (IBAN mod 97, Prüfziffer
  des BZSt), `EuMemberStates.AS_OF`, Stelligkeiten der Makros, zwei
  verschiedene Fristen von zwölf Monaten. Keiner.
- **`MaxLineLength` (22)**: detekt misst gegen 120 Zeichen, ktlint gegen die
  140 aus `.editorconfig`. Eine zweite Stelle für dieselbe Regel.
- **`SwallowedException` (8)**: jedes Mal die gewollte Übersetzung einer
  Ausnahme in eine Meldung (#52, #119).
- **Größe und Verzweigung (15)**: Der größte Ausschlag ist das `when` ohne
  `else` über `TemplateValue`, das ADR-0017 so will; die Regelgruppen sind
  lange Listen von Regeln. Einzig `GenerateCommand.run` zeigte auf etwas, das
  schon gesehen war (#359).
- **`MatchingDeclarationName` (1)**: `EnumSerializer.kt` enthält `CodeLookup`.
  Geschmack.

In `src/test` sind es fast nur überlange Zeilen (59) und die
Parameterlisten der Testfabriken in `core/model/TestFixtures.kt`.

Eine Änderung wert waren zwei Befunde, beide nur mit Typauflösung und beide
kosmetisch: `!!` nach einem Filter auf `null` und `check(… != null)` statt
`checkNotNull`. #307 hat sie behoben. Offen ist im Test ein `"%02d".format(…)`
ohne `Locale` in `NaechsteNummerSkriptTest`.

**Ergebnis:** rund 150 Befunde, davon zwei eine Änderung wert. Der Rest ist
eine Regel, die hier schon anders gehalten wird, oder eine Schwelle ohne
Begründung. Konfigurieren ließe sich das, aber dann bliebe von der Vorgabe
wenig übrig, und jede Abschaltung schuldete eine Zeile Begründung.

## Die Werkzeuge passen noch nicht zu Kotlin 2.4

Das stabile detekt ist gegen Kotlin 2.0.21 gebaut, das Projekt steht auf
2.4.20 (#158). Mit dem Gradle-Plugin 1.23.8, probiert an einem Klon:

- Unter dem lokalen JDK 26 bricht `detektMain` ab; der eingebaute Compiler
  liest die Versionsnummer `26.0.2.1` nicht.
- Unter JDK 21, dem der CI, meldet es 13-mal `UnreachableCode` für Code nach
  einem `try`, der sehr wohl erreicht wird, und findet das `!!` nicht.

detekt 2.0 passt zu Kotlin 2.4, ist aber am 29.09.2026 weiter im
Alpha-Stadium (alpha.6 vom August 2026). Auch dort bleibt `serializer()`
unaufgelöst, weil der CLI das Compiler-Plugin von kotlinx.serialization
fehlt; über das Gradle-Plugin von 2.0 wurde nicht gemessen. Eine feste
Einbindung hieße also: eine Alpha im Versionskatalog oder eine stabile
Fassung, die falsch meldet.

## SonarQube Cloud

Gemessen wurde nur detekt. Die Kotlin-Regeln von SonarQube zielen auf
dieselben Arten von Befunden und brächten hier vermutlich denselben Lärm.
Dazu kommt:

- **Abdeckung, schief gemessen.** Ein Quality Gate verlangt üblicherweise
  Abdeckung auf neuem Code; die CI läuft ohne die Tests mit Tag `Toolchain`
  (#136).
- **Ein fremder Dienst.** Der Quelltext läge bei SonarSource, unter dessen
  Bedingungen. Echte Kundendaten liegen nicht im Repo; ein Ausschlussgrund ist
  das nicht, aber eine Entscheidung.
- **Nicht nachgeprüft**: welche Zweige und Pull Requests der kostenlose Plan
  bei privaten Repositories analysiert.

CodeQL setzt bei privaten Repositories ein kostenpflichtiges Produkt von
GitHub voraus.

## Woran sich der Zeitpunkt erkennen ließe

- **An einem stabilen detekt für Kotlin 2.4.** Dann die Messung wiederholen,
  über das Gradle-Plugin mit dem Compiler-Plugin für kotlinx.serialization.
  Die Richtung wäre detekt in `./gradlew build`, mit einer Auswahl von Regeln
  statt der Vorgabe – `MaxLineLength` gehört ktlint, `MagicNumber` und die
  Schwellen trügen nichts – und ohne Baseline: Eine Liste stillgelegter
  Befunde sieht aus wie ein Netz und hält keines.
- **An einer zweiten Person im Code.** Heute hält das Review die
  Konventionen, und die meisten sind zu fachlich für ein Werkzeug.
- **Am Wunsch, SonarQube kennenzulernen** – dann als einzelner Lauf, ohne
  Quality Gate vor dem Merge.

## So wurde gemessen

Die JARs von den Releases unter `github.com/detekt/detekt`, außerhalb des
Repos. Den Klassenpfad für die Typauflösung gab ein Init-Skript aus
(`sourceSets.main.compileClasspath`), ohne Eintrag im Build.

```bash
java -jar detekt-cli-1.23.8-all.jar --input src/main/kotlin --report txt:main.txt
java -jar detekt-cli-2.0.0-alpha.6-all.jar --analysis-mode full --jvm-target 21 \
  --classpath "$KLASSENPFAD" --input src/main/kotlin --report checkstyle:main.xml
```

Das Gradle-Plugin lief in einem Klon mit
`id("io.gitlab.arturbosch.detekt") version "1.23.8"` und
`detekt { ignoreFailures = true }`.
