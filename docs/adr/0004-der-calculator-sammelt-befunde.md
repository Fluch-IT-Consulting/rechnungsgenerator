# Der Calculator sammelt Befunde, statt beim ersten Fehler zu werfen

`InvoiceCalculator.calculate` wirft nicht mehr. Es liefert die abgeleiteten
Werte und eine Liste von **Befunden**; jeder trägt eine Kennung, eine Schwere
(Abbruch oder Hinweis), den Gegenstand (Rechnung, Position, Abschlag) und die
Meldung. Abgebrochen wird im Kommando, sobald ein Befund die Schwere Abbruch
hat – und dann stehen alle Gründe auf einmal da.

## Warum

Vorher gehörten zur Schnittstelle des Calculators zwei Dinge, die dort nichts zu
suchen haben: die **Reihenfolge** seiner Regeln und die **vollständige Menge**
seiner Hinweise. Wer eine Regel prüfen wollte, musste eine Rechnung bauen, die
alle anderen besteht und keinen fremden Hinweis auslöst. In den Tests stand
19-mal `.single()` auf der Hinweisliste; ein neuer Hinweis machte fremde Tests
rot, und einer verschob seinen Leistungszeitraum nur, damit der Hinweis aus #47
schwieg.

Teurer war das Zweite: Was hinter dem ersten Wurf lag, sah niemand. Beim Umbau
kam heraus, dass die Absetzungsregel „Restbetrag negativ" auf jeder Rechnung mit
negativem Gesamtbetrag mitsprach – auch ohne einen einzigen Abschlag, mit der
Meldung „Es sind mehr Abschläge abgesetzt (0.00)". Genau diese Meldung war der
Anlass für #47. Sie war nie behoben, nur verdeckt.

## Verworfen

- **Weiter werfen, aber alle Gründe in eine Meldung packen.** Für die Kommandos
  dasselbe Ergebnis, für die Tests nichts gewonnen: Sie prüften weiter Prosa.
- **Je Regel eine öffentliche Funktion.** Das gäbe flache Module und risse Regel
  und Meldung auseinander; die Meldung steht bewusst neben ihrer Bedingung.
- **Nummerierte Kennungen** (`REGEL_7`). Die zehn Nummern der Absetzung liefen
  schon auseinander: `CLAUDE.md` zählte zehn, ein Testname neun.
- **Den Gegenstand in der Meldung lassen.** Dann stünde er zweimal da, und wer
  die Meldung weiterreicht – `abschlaege` liest mehrere Dateien – klebte
  weiterhin Text davor.

## Konsequenzen

- **Regeln, die früher unerreichbar waren, laufen jetzt.** Folgefehler werden je
  Gegenstand unterdrückt, ausgedrückt durch frühes Weiterspringen statt durch
  einen Mechanismus: Fehlt `vereinnahmt-am`, entfallen die Regeln, die es
  brauchen – für diesen Abschlag, nicht für die anderen. Und „Restbetrag
  negativ" prüft nur noch, wo Abschläge sind.
- **Gerechnet wird trotz Verstoß.** Die Zahlen entstehen, gesetzt werden sie
  nie: Das Kommando bricht vorher ab. Eine Steuergruppe mit zwei verschiedenen
  Befreiungsgründen trägt keinen – einen davon zu wählen hieße raten.
- **Die Kennungen sind deutsch benannt.** Das weicht von „Bezeichner Englisch"
  ab. Jede Regel ist ein Satz über die deutsche Rechnungsstellung, die Meldung
  daneben ist deutsch, und die Hälfte der Wörter steht im Glossar; ein
  englisches Vokabular daneben zwänge beim Lesen zum Übersetzen.
- **`InvoiceValidationException` gibt es nicht mehr.** Wo ein Kommando abbricht,
  wirft es `CliktError`.
- **Für Tests ist die Kennung die Zusage**, der Wortlaut nur dort, wo er selbst
  die Zusage ist – etwa dass eine Meldung die Datei nennt oder dass in der
  Meldung zum negativen Gesamtbetrag das Wort „Abschläge" **nicht** vorkommt.
- **`abschlaege` bleibt alles oder nichts** (ADR-0003). Es nennt nur alle
  Gründe, statt beim ersten aufzuhören; die Hinweise der alten
  Abschlagsrechnungen fallen dort weiter weg.

Umgesetzt in #49.

Ergänzt durch ADR-0007: Dass nie gesetzt wird, was einen Abbruch hat, hält
seither der Compiler und nicht mehr die Disziplin der Kommandos.
