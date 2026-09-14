# Nur eine Rechnung ohne Abbruch lässt sich setzen

Renderer und `prepaymentOf` nehmen keine `CalculatedInvoice` mehr an, sondern
eine `AcceptedInvoice`. Die gibt es nur über `AcceptedInvoice.check`, und nur
dann, wenn die Rechnung keinen Befund mit der Schwere Abbruch trägt. Sonst
liefert die Prüfung `Checked.Rejected` mit allen Abbrüchen. Das ergänzt
ADR-0004 an einer Stelle: Die Zusage, dass nie gesetzt wird, was einen Abbruch
hat, hält jetzt der Compiler.

## Warum

ADR-0004 sagt: „Die Zahlen entstehen, gesetzt werden sie nie: Das Kommando
bricht vorher ab." Das stimmte, aber nur, weil beide Kommandos daran dachten.
`GenerateCommand` und `PrepaymentsCommand` filterten die Befunde je für sich,
und dahinter nahmen `InvoiceRenderer.render` und `prepaymentOf` jede Rechnung
an. Der Renderer verließ sich sogar darauf: Sein `checkNotNull` für die
Vereinnahmung eines Abschlags setzte voraus, dass eine Rechnung mit
`ABSCHLAG_OHNE_VEREINNAHMUNG` nie bei ihm ankommt. Garantiert hat das niemand.

Beim Zahlungsziel und beim Restbetrag hat das Projekt diese Sorte Lücke schon
geschlossen: Ein Ergebnis mit Zahlungsziel *und* Restbetrag null ist nicht mehr
baubar, nicht bloß ungebaut. Für die folgenreichste Zusage des Programms galt
das bisher nicht.

## Verworfen

- **Eine Laufzeitprüfung im Renderer.** Sie meldete den Fehler erst beim
  Setzen, und `prepaymentOf` bräuchte dieselbe Prüfung ein zweites Mal. Es
  bliebe eine Disziplin, nur an anderer Stelle.
- **`calculate` liefert gleich `Checked`.** ADR-0004 rechnet bewusst trotz
  Verstoß: Die Regeltests rechnen gerade Rechnungen mit Abbruch und lesen deren
  Befunde. Die Prüfung gehört hinter die Rechnung, nicht in sie.
- **Typ und Modell je Rechnungsart in einem Umbau** (zweiter Teil von #70).
  Der Typ ohne Abbruch erfüllt die Zusage für sich allein, mit einem kleinen
  Diff. Das Modell je Rechnungsart folgt getrennt, noch vor #64.

## Konsequenzen

- Die Kommandos werten `Checked` mit `when` aus. Die Hinweise einer
  angenommenen Rechnung liefert `AcceptedInvoice.hints`.
- `prepaymentsOf` übernimmt nur Abschläge angenommener Abschlagsrechnungen.
  Die Liste ist deshalb nur vollständig, wenn kein Abbruch gemeldet ist, und nur
  dann schreibt `abschlaege` den Block (ADR-0003: alles oder nichts). (Seit
  #118 hält das der Typ: `CollectedPrepayments` ist entweder vollständig oder
  abgelehnt, und die Liste gibt es nur im ersten Fall.)
- Das `checkNotNull` im Renderer bleibt vorerst. Seine Voraussetzung ist jetzt
  garantiert, aber der Compiler kennt sie nicht: `vereinnahmt-am` ist im Modell
  weiter optional. Entfallen kann es mit dem Modell je Rechnungsart. (Entfallen
  mit ADR-0008.)
- Tests, die setzen oder einen Abschlag übernehmen, holen sich die angenommene
  Rechnung über `accepted()`. Hat die Testrechnung einen Abbruch, scheitern sie
  dort mit den Kennungen.

Umgesetzt in #70 (erster Teil).
