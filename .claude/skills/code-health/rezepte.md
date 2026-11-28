# Rezeptbuch

Was ein Umbau nach Code Health in dieser Codebasis gelehrt hat. Ein Eintrag
nennt:

- **Befund** in der Benennung von CodeScene, wie ihn `code_health_review`
  meldet
- **Bedingungen**: woran man erkennt, dass er greift
- **Grenzen**: wo er nicht greift oder was er nicht schafft
- **Mechanismus**: wie der Umbau geht
- **Beleg**: Issue oder Pull Request, Datei, Wert vorher und nachher

Neue Einträge kommen mit dem Pull Request des Umbaus, der sie gefunden hat
(ADR-0032).

## Rezepte

### Abschnitte als Funktionen

- **Befund:** Complex Method, Large Method, Bumpy Road
- **Bedingungen:** Eine Funktion tut mehrere Dinge nacheinander, und die
  Abschnitte stehen schon da – als Kommentar, als Leerzeile, als Vorschrift
  über einer Gruppe von Prüfungen. Kein ADR legt den Rumpf fest.
- **Grenzen:** Lesen mehrere Abschnitte dieselben Werte von weiter oben, gilt
  zusätzlich „Geteilte Werte als kleine Klasse“. Was nach dem Herausziehen
  bleibt, etwa eine Schleife mit Auswertung, kann die Komplexität auf genau 9
  halten; siehe „Komplexität 9 ist noch ein Befund“.
- **Mechanismus:** Jeder Abschnitt wird eine private Funktion, deren Name
  sagt, was sie tut oder prüft. Die Rümpfe werden verschoben, nicht
  umgeschrieben; ein Kommentar zieht mit seiner Zeile um. Die alte Funktion
  liest sich danach als Folge von Aufrufen. Ein lokaler Wert, der in einer
  Zeile aus dem Parameter folgt (`val customer = invoice.customer`), zieht
  in jede Funktion mit, die ihn liest; der Pfad statt seiner änderte die
  Meldungszeile und machte sie zu lang.
- **Beleg:**
  - #578, `generate/typesetting/LatexCompiler.kt`: `compile` prüft, schreibt
    und setzt; die ersten beiden wurden `requirePlainFileNames` und
    `writeSources`. 8.97 → 9.09, `compile` von `cc = 16` auf `cc = 9`.
  - #580, `generate/typesetting/Pflichtangaben.kt` (Test): `Sheet.verify` mit
    191 Zeilen und `cc = 33` ruft je Vorschrift eine Funktion, von
    `verifyParties` bis `verifyFollowingPages`. 8.17 → 9.68; Complex Method,
    Large Method, Bumpy Road und Complex Conditional sind weg.
  - #584, `core/rules/TaxCategoryRules.kt`: `check` mit `cc = 16` und
    `LoC = 81` setzt drei Funktionen zusammen; die Prüfungen je Position
    teilen sich auf `vatRate` und `exemptionReason` auf. 9.25 → 10.0, mit der
    benannten Bedingung von `BEFREIUNGSGRUND_FEHLT`.
  - #585, `core/rules/VatIdCheckRules.kt`: `hints` mit `cc = 10` setzt
    `euListAge`, `noVies` und `existingCheck` zusammen; die letzte nimmt die
    vorhandene Abfrage. 9.38 → 10.0, mit der benannten Bedingung
    `outsideEu`. `customer` steht in zwei der Funktionen je einmal.

### Geteilte Werte als kleine Klasse

- **Befund:** vermeidet Excess Number of Function Arguments, das sonst nach
  „Abschnitte als Funktionen“ auftaucht
- **Bedingungen:** Mehrere herausgezogene Funktionen brauchen dieselben Werte,
  die vorher einmal oben in der Funktion entstanden.
- **Grenzen:** Geöffnet wird die Klasse in einem Blockrumpf, sonst greift die
  Messfalle „Ausdrucksrumpf mit Lambda“.
- **Mechanismus:** Eine private Klasse bündelt die Werte und entsteht einmal;
  jede Funktion nimmt nur sie und öffnet sie mit
  `fun f(facts: Facts) { with(facts) { … } }`.
- **Beleg:** #580, `Pflichtangaben.kt` (Test): `Facts` trägt `words`,
  `spelling` und die Werte, die die Folgeseiten von weiter oben brauchen.
  Die Parameterliste stattdessen steht unter „Fehlschläge“.

### Prüfungen zählen

- **Befund:** jeder; das Rezept sichert den Umbau, nicht den Wert
- **Bedingungen:** Der Umbau verschiebt Prüfungen – im Netz selbst, also im
  Testcode, oder im Hauptcode, wo nicht jede Prüfung einen eigenen Test hat.
  Ein Netz, das beim Verschieben eine Prüfung verliert, bleibt grün.
- **Grenzen:** Gezählt wird der Quelltext. Eine Prüfung, die zwar aufgerufen
  wird, aber nie greift, findet nur der eingebaute Fehler.
- **Mechanismus:**
  1. Vor dem ersten Schritt die Aufrufe jeder Prüffunktion in der Datei
     zählen (`grep -c`), danach noch einmal: Die Zahlen sind gleich.
  2. Die Prüfzeilen von alt und neu nebeneinanderlegen (`diff`).
  3. Je einen Fehler absichtlich einbauen, vorn und hinten in dem, was
     verschoben wurde: Das Netz wird rot, mit der erwarteten Meldung.
  4. Dieselbe Zahl von Tests vorher und nachher.
- **Beleg:** #580, `Pflichtangaben.kt` (Test): `shows(` 46, `lacks(` 12,
  `showsOnPage(` 19, `lacksOnPage(` 2, `withClue(` 12, vorher wie nachher.
  Rot wurde das Netz mit unsichtbarem Leistungszeitraum vorn und ohne
  Rechnungsnummer im Kopf der Folgeseiten; 1079 Tests mit altem und neuem
  Stand (PR #581).

## Messfallen

### Ausdrucksrumpf mit Lambda

- **Befund:** Global Conditionals, obwohl die Bedingungen in einer Funktion
  stehen
- **Bedingungen:** Eine Funktion hat einen Ausdrucksrumpf mit Lambda,
  `private fun Sheet.verifyX(facts: Facts) = with(facts) { … }`. Der
  Kotlin-Parser von CodeScene sieht darin keinen Funktionsrumpf; Komplexität
  je Funktion misst er dort nicht, und der Wert der Datei stimmt nicht.
- **Grenzen:** Nicht jedes Lambda: `fun check(…) = buildList { … }` misst er
  als Funktion. Belegt ist die Falle nur für `= with(…)`.
- **Mechanismus:** Blockrumpf, `fun f(facts: Facts) { with(facts) { … } }`.
  Taucht nach einem Umbau Global Conditionals auf, zuerst nach `= with(` und
  ähnlichen Ausdrucksrümpfen suchen.
- **Beleg:** #580, `Pflichtangaben.kt` (Test): mit Ausdrucksrumpf 9.09, der
  fertige Stand mit Blockrumpf 9.68 (PR #581). Die Grenze: #584,
  `TaxCategoryRules.kt`, `check` als `= buildList { … }` mit `cc = 16`
  gemessen; danach nannte der Befund die herausgezogene
  `exemptionReason`, ebenfalls ein `= buildList { … }`.

### Komplexität 9 ist noch ein Befund

- **Befund:** Complex Method mit `cc = 9`
- **Bedingungen:** CodeScene verlangt für Kotlin eine Komplexität *unter* 9.
  Ein Umbau, der auf genau 9 kommt, lässt den Befund stehen.
- **Mechanismus:** Den Schritt nicht für fertig halten, weil die Zahl die
  Grenze erreicht hat. Weg ist der Befund erst bei 8.
- **Beleg:** #578, `LatexCompiler.kt`: `compile` von `cc = 16` auf `cc = 9`,
  der Befund blieb, 9.09 (PR #579). Ebenso `InvoiceRenderer.value` unter
  „Bleibende Befunde“.

### Dateien ohne Wert

- **Befund:** keiner – `Could not determine Code Health score.`
- **Bedingungen:** Fast immer hat die Datei keine benannte Funktion:
  Kotest-Spezifikationen aus Lambdas, reine Daten-, Enum- oder
  Sealed-Deklarationen. Ausnahmen ohne bekannten Grund sind
  `toolchain/process/ProcessRunner.kt` und `generate/FakePdfViewer.kt` (Test).
- **Grenzen:** Für diese Dateien sieht auch das Absichern nach #576 nichts.
  Kein Wert ist kein 10.0.
- **Beleg:** Messung vom 28.11.2026 (#582): 49 von 196 Kotlin-Dateien ohne
  Wert, 14 im Hauptcode und 35 im Test.

### Gemessen wird nur im Repository

- **Befund:** keiner – das Werkzeug lehnt ab
- **Bedingungen:** Die Datei liegt außerhalb eines Git-Repositorys, etwa als
  Kopie im Scratchpad, um eine Fassung zu vergleichen. Die Antwort nennt
  dann, dass die CodeScene CLI ein Git-Repository braucht.
- **Mechanismus:** Eine Fassung zum Vergleich entsteht in der Datei selbst,
  im Arbeitsbaum, und wird danach zurückgesetzt.
- **Beleg:** am 28.11.2026 mit einer Kopie von `InvoiceRenderer.kt` im
  Scratchpad (#582).

## Fehlschläge

### Parameterliste statt Klasse

- **Befund:** Excess Number of Function Arguments
- **Bedingungen:** Nach „Abschnitte als Funktionen“ gehen die geteilten Werte
  einzeln als Argumente in die neuen Funktionen.
- **Was geschah:** Acht Argumente für die Folgeseiten meldete CodeScene als
  Excess Number of Function Arguments; der Umbau tauschte einen Befund gegen
  einen anderen. Das Rezept ist „Geteilte Werte als kleine Klasse“.
- **Beleg:** #580, Triage, `Pflichtangaben.kt` (Test).

## Bleibende Befunde

Befunde, deren Form ein ADR verlangt. Eine Datei mit einem solchen Befund
ist unter 9.5 kein Kandidat für ein Issue.

### `InvoiceRenderer.value`

- **Befund:** Complex Method (`cc = 9`), Large Method (`LoC = 149`)
- **Datei:** `generate/render/InvoiceRenderer.kt`, 9.18
- **Warum er bleibt:** Das `when` ohne `else` über `TemplateValue` zwingt
  jeden neuen Platzhalter zur Entscheidung. Eine eigene Funktion je Angabe
  mit Formatierschritt ist dort verworfen; eine bekommt nur, was entscheidet
  (ADR-0017).
- **Beleg:** Messung vom 28.11.2026 (#582).
