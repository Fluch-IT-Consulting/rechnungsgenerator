# Wo das Modell je Rechnungsart entsteht

Notiz vom 13.09.2026, festgehalten für später. **Hier wird nichts entschieden.**
Es ist der Stand eines Gesprächs über den zweiten Teil von #70. Eine Empfehlung
gibt es, eine Entscheidung noch nicht.

## Der Anlass

#70 hat zwei Teile. Der erste ist gebaut (ADR-0007): Renderer und
`prepaymentOf` nehmen nur eine `AcceptedInvoice`, und die gibt es nur ohne
Abbruch. Der zweite Teil soll die drei Felder einsammeln, die nur zu einer
Rechnungsart gehören – `storniert`, `vereinnahmt-am`, `abschlaege`. Heute sind
sie in `Invoice` optional, und alles dahinter muss mit Kombinationen umgehen,
die es nach der Prüfung gar nicht mehr gibt:

- `InvoiceRenderer` liest `invoice.cancelled?.let` und `invoice.receivedOn?.let`.
- `describePrepayment` braucht ein `checkNotNull` für die Vereinnahmung eines
  Abschlags. ADR-0007 garantiert dessen Voraussetzung, aber der Compiler weiß
  davon nichts.
- Mit #64 käme eine weitere Art mit eigenen Feldern, mit ZUGFeRD eine zweite
  Ausgabe, die dieselben Unterscheidungen noch einmal träfe.

Das Issue nimmt an, dass fünf Regeln wegfallen: `STORNO_OHNE_BEZUG`,
`BEZUG_OHNE_STORNO`, `VEREINNAHMUNG_FALSCHE_ART`, `ABSCHLAEGE_FALSCHE_ART`,
`ABSCHLAEGE_FEHLEN`.

## Warum die fünf Regeln nicht wegfallen

Die YAML-Datei bleibt flach – so steht es im Issue selbst. Dann kann dort
weiterhin `art: schlussrechnung` ohne `abschlaege` stehen, und jemand muss das
melden, in der Sprache der Rechnungsart. Die Regeln prüfen **Eingaben**, und die
Eingaben ändern sich nicht.

Wegfallen kann etwas anderes: dass die Stellen *hinter* der Prüfung mit
falschen Kombinationen rechnen müssen. Das Issue sollte an dieser Stelle
richtiggestellt werden, gleich wie entschieden wird.

## Die sechste Regel

Beim Durchdenken fiel eine Regel auf, die das Issue nicht nennt:
`ABSCHLAG_OHNE_VEREINNAHMUNG`. Solange `vereinnahmt-am` eines Abschlags fehlen
darf, lässt sich kein Abschlag bauen, dessen Datum feststeht – und genau daran
hängt das `checkNotNull` im Renderer. Sie gehört also zur selben Frage wie die
fünf.

## Drei Möglichkeiten

### A: Das Modell entsteht beim Einlesen

Schon `YamlLoader` liefert je Rechnungsart einen eigenen Typ; eine
Schlussrechnung ohne Abschläge lässt sich gar nicht erst einlesen. Das ist die
Lehrbuchantwort – prüfen, indem man den richtigen Typ baut, statt hinterher zu
kontrollieren.

Sie kollidiert mit drei Entscheidungen des Projekts:

- **Alle Gründe auf einmal (ADR-0004).** Scheitert das Einlesen an der
  Rechnungsart, gibt es keine Rechnung und damit keine übrigen Befunde. Ein
  Storno ohne Bezug mit negativem Einzelpreis bräuchte wieder zwei Läufe.
- **Meldungen in Fachsprache.** Je Wert von `art` ein Typ heißt Polymorphie in
  kotlinx.serialization und kaml. Die Fehler kämen als „Feld `storniert`
  fehlt“ vom Serialisierer. `Invoice.kt` begründet ausdrücklich, warum das
  nicht gewollt ist.
- **Die Regeltests brauchen falsche Kombinationen.** Sie bauen genau die
  Rechnungen, die es nicht geben darf, und lesen deren Befunde. Mit Modell A
  ließen sie sich nicht mehr bauen.

### B: Ein Typ neben der `AcceptedInvoice`, die Regeln bleiben, wo sie sind

`Invoice` und `CalculatedInvoice` bleiben flach, die Regelgruppen unverändert.
`AcceptedInvoice.check` baut zusätzlich eine versiegelte Hierarchie, etwa:

```kotlin
sealed interface InvoiceKind {
    data class Ordinary(val type: InvoiceType) : InvoiceKind      // rechnung, teilrechnung
    data class Prepayment(val receivedOn: LocalDate?) : InvoiceKind
    data class Final(val deductions: List<Deduction>) : InvoiceKind // Deduction.receivedOn: LocalDate
    data class Cancellation(val cancelled: CancelledInvoice) : InvoiceKind
}
```

Klein, und Renderer und `prepaymentOf` lesen nur noch `kind`.

Der Schwachpunkt: Dasselbe Wissen stünde zweimal da. Die Regel sagt, eine
Schlussrechnung brauche Abschläge; der Bau des Typs muss dieselbe Frage noch
einmal stellen und für den Fall, den die Regel angeblich ausschließt, ein
`error("kann nicht sein")` tragen. Das ist dieselbe Sorte Zusage, die ADR-0007
gerade abgeschafft hat – sie hält, solange zwei Stellen übereinstimmen.

### C: Der Typ und seine Regeln an einer Stelle

Wie B, aber die Regeln, deren Verletzung den Typ unbaubar macht, stehen dort,
wo der Typ gebaut wird – in einer Regelgruppe im Paket `rules`, Arbeitstitel
`InvoiceKindRules`. Sie liefert entweder die Rechnungsart oder die Befunde, die
erklären, warum keine entsteht. `AcceptedInvoice.check` nimmt nur an, wenn es
keinen Abbruch gibt **und** die Rechnungsart gebaut ist. Einen Zweig „kann
nicht sein“ gibt es nicht.

```
YAML (flach) → Invoice → calculate → CalculatedInvoice ─┬→ übrige Regelgruppen → Befunde
                                                          └→ InvoiceKindRules → Befunde + InvoiceKind?
AcceptedInvoice.check: keine Abbrüche UND InvoiceKind gebaut → Accepted(calculated, kind)
```

Das Kriterium, welche Regel dorthin gehört:

> **Eine Regel gehört zum Bau des Typs, wenn der Typ ohne sie nicht baubar ist.
> Alle anderen prüfen den gebauten Wert.**

Das trifft die fünf Regeln aus #70 und `ABSCHLAG_OHNE_VEREINNAHMUNG` – nicht
aber etwa `ABSCHLAG_NACH_RECHNUNGSDATUM`: Ein Abschlag mit spätem Datum ist
baubar, nur falsch.

## Was C bringt

- **Das `checkNotNull` entfällt**, ebenso `cancelled?.let` und
  `receivedOn?.let` im Renderer. Er liest `InvoiceKind`, und dort sind die
  Felder nicht mehr optional, wo sie es fachlich nicht sind.
- **#64 kann nicht vergessen werden.** Ein neuer Eintrag in `InvoiceType`
  macht das `when` beim Bau unvollständig; eine neue Variante von `InvoiceKind`
  erzwingt Zweige im Renderer und später im ZUGFeRD-Export.
- **ADR-0004 bleibt unberührt.** Die Befunde entstehen weiterhin vollständig,
  mit denselben Kennungen und Meldungen.
- **Das Einlesen bleibt, wie es ist.** YAML, `Invoice`, Calculator und die
  übrigen Regelgruppen ändern sich nicht.

## Was C kostet

- **Eine Regelgruppe liefert mehr als Befunde.** Das ist die einzige Ausnahme
  vom Muster aus #71 – begründet, weil gerade diese Regeln die Voraussetzungen
  des Typs sind.
- **Regeln und Tests ziehen um.** Die fünf Regeln kommen aus
  `CancellationRules`, `PrepaymentRules` und `FinalInvoiceRules`,
  `ABSCHLAG_OHNE_VEREINNAHMUNG` ebenfalls aus `FinalInvoiceRules`. Ihre Tests
  gehören dann in einen `InvoiceKindRulesTest`; die Rümpfe können bleiben.
- **Ein Rest an Disziplin bleibt.** Über `acceptedInvoice.calculated.invoice`
  ist das flache Feld weiter erreichbar. Wer dort `cancelled` liest statt
  `kind`, hat wieder ein optionales Feld in der Hand.

## Vergleich

| | A: beim Einlesen | B: Typ neben den Regeln | C: Typ und Regeln an einer Stelle |
|---|---|---|---|
| `checkNotNull` entfällt | ja | ja | ja |
| Alle Befunde auf einmal (ADR-0004) | nein | ja | ja |
| Meldungen in Fachsprache | nein | ja | ja |
| Wissen über die Art an einer Stelle | ja | nein, zweimal | ja |
| Umfang | groß | klein | mittel |

## Empfehlung

C. Es ist die einzige Möglichkeit, die das `checkNotNull` beseitigt, ohne
ADR-0004 aufzugeben oder eine neue Zusage zwischen zwei Stellen einzuführen.

## Fragen, die daran hängen

- **ADR-0007 ergänzen oder ADR-0008?** Die Entscheidung für C ändert, wo die
  fünf Regeln wohnen und warum; das ist mehr als ein Nachtrag zu „nur ohne
  Abbruch“.
- **Die Teilrechnung.** Bekommt sie eine eigene Variante oder teilt sie
  `Ordinary` mit der gewöhnlichen Rechnung? Sie unterscheidet sich heute nur im
  Code (326), nicht in den Feldern.
- **Die Abschlagsrechnung** trägt `vereinnahmt-am` fachlich optional – als
  `LocalDate?` in ihrer Variante bleibt es das, und das ist richtig so.
- **Das flache Feld hinter `calculated`.** Soll `AcceptedInvoice` die ganze
  `CalculatedInvoice` weiter herausgeben, oder nur, was Renderer und
  `prepaymentOf` brauchen? Das schlösse den Rest an Disziplin, wäre aber ein
  eigener Umbau.
- **Die Namen.** `InvoiceKind` neben `InvoiceType` liest sich wie zweimal
  dasselbe. Vielleicht trägt die Variante den `InvoiceType` gleich mit, oder
  eines der beiden heißt anders.

## Erwogen und verworfen

- **Die fünf Regeln einfach streichen.** Geht nur mit A, und A kostet ADR-0004.
- **Das `checkNotNull` durch einen stillen Ausweichzweig ersetzen** (Zeile ohne
  Datum). Eine Schlussrechnung, die einen Abschlag ohne Vereinnahmung absetzt,
  verstößt gegen §14 Abs. 5 Satz 2 UStG; sie leise zu drucken, ist schlimmer als
  der Absturz.

## Nicht Teil dieser Frage

- Wie die Rechnungskorrektur selbst aussieht (#64). Hier geht es nur darum,
  dass sie im Modell einen Platz bekommt, den der Compiler einfordert.
- Ob `offene-abschlaege.py` die Rechnungsarten kennt (#74). Mit C gäbe es in
  Kotlin eine Stelle, an der die Arten gebaut werden – ein Kommando nach #74
  könnte sie benutzen.
