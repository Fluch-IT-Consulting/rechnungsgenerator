# Die Rechnungsart wird ein Typ, gebaut von den Regeln, ohne die er nicht baubar ist

Hinter der Prüfung gibt es die Rechnungsart als Typ: `InvoiceKind`, eine
versiegelte Hierarchie mit `Ordinary` (Rechnung, Teilrechnung),
`PrepaymentInvoice`, `FinalInvoice` und `Cancellation`. Jede Variante trägt die
Angaben, die nur zu ihr gehören, und zwar nicht mehr optional, wo sie es
fachlich nicht sind: die Stornorechnung ihren Bezug, die Schlussrechnung ihre
Abschläge, jeden mit feststehender Vereinnahmung. `AcceptedInvoice` trägt sie
als `kind`, und Renderer und `prepaymentOf` fragen dort.

Gebaut wird der Typ in der Regelgruppe `InvoiceKindRules`, und zwar zusammen mit
den Regeln, ohne die er nicht baubar ist. Das Kriterium:

> Eine Regel gehört zum Bau des Typs, wenn der Typ ohne sie nicht baubar ist.
> Alle anderen prüfen den gebauten Wert.

Das sind sechs: `STORNO_OHNE_BEZUG`, `BEZUG_OHNE_STORNO`,
`VEREINNAHMUNG_FALSCHE_ART`, `ABSCHLAEGE_FALSCHE_ART`, `ABSCHLAEGE_FEHLEN` und
`ABSCHLAG_OHNE_VEREINNAHMUNG`. Die YAML-Datei, `Invoice`, der Calculator und die
übrigen Regelgruppen bleiben, wie sie sind.

Das Gespräch davor steht in `docs/notizen/2026-09-13-modell-je-rechnungsart.md`.

## Warum

ADR-0007 hat dafür gesorgt, dass eine Rechnung mit Abbruch nicht gesetzt werden
kann. Die Stellen dahinter mussten aber weiter mit Kombinationen umgehen, die die
Prüfung längst ausgeschlossen hatte: Der Renderer las `invoice.cancelled?.let`
und `invoice.receivedOn?.let` und brauchte für die Vereinnahmung eines Abschlags
ein `checkNotNull`. Dessen Voraussetzung war garantiert, aber nur die Prüfung
wusste das, nicht der Compiler.

Mit #64 käme eine weitere Rechnungsart mit eigenen Feldern, mit ZUGFeRD eine
zweite Ausgabe, die dieselben Unterscheidungen noch einmal träfe. Beide
bekommen jetzt einen Ort, den der Compiler einfordert: Ein neuer Eintrag in
`InvoiceType` macht das `when` in `InvoiceKindRules` unvollständig, eine neue
Variante von `InvoiceKind` jedes `when` darüber. (Hielt bis #128 nicht: Der
Renderer fragte mit `is` und `!is`, und eine neue Variante wäre dort still
durchgegangen. Ausgenommen bleibt `prepaymentOf`, wo genau eine Art richtig ist.)

**Regel und Bau an einer Stelle.** Stünden die sechs Regeln weiter in ihren
Gruppen und der Bau des Typs daneben, stünde das Wissen über die Rechnungsart
zweimal da: Die Regel sagt, eine Schlussrechnung brauche Abschläge, und der Bau
fragt dasselbe noch einmal, mit einem Zweig „kann nicht sein" für den Fall, den
die Regel ausschließt. In `InvoiceKindRules` liefert jeder Weg, auf dem kein Typ
entsteht, den Befund, der erklärt, warum. `AcceptedInvoice.check` nimmt nur an,
wenn es keinen Abbruch gibt **und** der Typ gebaut ist.

## Verworfen

- **Das Modell schon beim Einlesen.** Die Lehrbuchantwort, aber sie kostet drei
  Entscheidungen: alle Befunde auf einmal (ADR-0004), denn ohne eingelesene
  Rechnung gibt es keine weiteren; Meldungen in der Sprache der Rechnungsart
  statt „Feld `storniert` fehlt" vom Serialisierer; und die Regeltests, die
  gerade die falschen Kombinationen bauen.
- **Der Typ neben unveränderten Regeln.** Kleiner, aber mit dem Wissen über die
  Rechnungsart an zwei Stellen – dieselbe Sorte Zusage, die ADR-0007 gerade
  abgeschafft hat.
- **Die fünf Regeln streichen**, wie #70 ursprünglich annahm. Die YAML-Datei
  bleibt flach, also muss jemand eine Schlussrechnung ohne Abschläge melden.
- **Das flache Modell hinter `AcceptedInvoice.calculated` gleich mit
  verbergen.** Sinnvoll, aber ein eigener Umbau, weil der Renderer überall
  `calculatedInvoice.invoice` liest.

## Konsequenzen

- **`InvoiceKindRules` ist die einzige Regelgruppe, die mehr liefert als
  Befunde.** Das Muster aus #71 hat damit eine begründete Ausnahme.
- **Die sechs Regeln und ihre Tests sind umgezogen**, aus `CancellationRules`,
  `PrepaymentRules` und `FinalInvoiceRules` in `InvoiceKindRules` und
  `InvoiceKindRulesTest`. Kennungen und Meldungen sind gleich.
- **`ABSCHLAG_OHNE_VEREINNAHMUNG` meldet sich nur noch in einer
  Schlussrechnung.** Stehen Abschläge auf einem anderen Dokument, sagt
  `ABSCHLAEGE_FALSCHE_ART`, dass sie dort nicht hingehören; ob einer davon
  vereinnahmt ist, spielt dann keine Rolle mehr.
- **Das `checkNotNull` im Renderer entfällt**, ebenso `cancelled?.let` und
  `receivedOn?.let`.
- **Ein Rest an Disziplin bleibt.** Über `acceptedInvoice.calculated.invoice`
  sind die flachen, optionalen Felder weiter erreichbar. Wer dort statt in
  `kind` fragt, hat wieder ein optionales Feld in der Hand. Das zu schließen,
  ist ein eigenes Issue. (Nicht geschlossen, siehe ADR-0009.)
- **#70 ist damit erledigt**, auch wenn anders als dort angenommen keine Regel
  wegfällt.

Umgesetzt in #70 (zweiter Teil).
