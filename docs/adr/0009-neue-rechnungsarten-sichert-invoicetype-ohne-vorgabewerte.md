# Neue Rechnungsarten sichert InvoiceType ohne Vorgabewerte, nicht ein verborgenes Modell

`InvoiceType` hat für `servicePeriodIsForecast`, `chargesForService` und
`mandatoryNote` keine Vorgabewerte. Jeder Eintrag nennt alle drei ausdrücklich,
und ein neuer übersetzt erst, wenn sie dastehen.

Hinter der Annahme bleibt das flache Modell dagegen lesbar:
`AcceptedInvoice.calculated.invoice` gibt es weiter, und Renderer und
Kommandos lesen dort Nummer, Kunde, Positionen – und `type`. ADR-0008 hatte das
Verbergen als eigenes Issue offengelassen; es wird nicht gebaut.

Das Gespräch dazu steht in #92.

## Warum

ADR-0008 verspricht, dass eine neue Rechnungsart (#64) nicht vergessen werden
kann. Das Architektur-Review vom 13.09.2026 fand, dass der Renderer die Art an
3 Stellen über `kind` fragt und an 8 über `invoice.type`, und schloss daraus,
die 8 Stellen seien die Lücke.

Beim Nachlesen lag sie woanders. Die 8 Stellen lesen Eigenschaften, die jeder
Eintrag von `InvoiceType` selbst trägt – `documentName`, die Beschriftung des
Zeitraums, den Pflichtsatz, ob Geld gefordert wird. Durchrutschen konnte eine
neue Art dort nur, weil diese Eigenschaften Vorgabewerte hatten
(`false`, `true`, `null`): Ein neuer Eintrag mit bloß Label und Code hätte
still die Antworten der gewöhnlichen Rechnung geerbt, samt Zahlungsziel und
Bankverbindung. Den Renderer auf `kind` umzustellen, änderte daran nichts.
Ohne Vorgabewerte fällt die Entscheidung dort, wo die Art entsteht, und der
Compiler verlangt sie.

Die artgebundenen Felder, um die es ADR-0008 ging – `cancelled`,
`receivedOn`, `prepayments` –, liest hinter der Annahme schon heute niemand
mehr aus dem flachen Modell.

## Verworfen

- **`AcceptedInvoice` als alleinige Interface.** Sie gäbe `calculated` nicht
  mehr heraus, sondern selbst, was dahinter gebraucht wird: Nummer, Datum,
  Kunde, Zeitraum, Anmerkung, Positionen, Summen, Steuergruppen, Restbetrag,
  Zahlungsziel, Stammdaten, Dokumentname – rund zwölf Angaben, die sie fast
  alle nur durchreichte. Damit wäre sie shallow: Ihr Löschen verschöbe die
  Komplexität, statt sie zu verteilen. Geschützt würde vor einem Leck, das es
  derzeit nicht gibt.
- **Das Modell teilen**, sodass hinter der Annahme nur ein Teil von `Invoice`
  ohne die artgebundenen Felder sichtbar ist. Widerspricht ADR-0008 („die
  YAML-Datei, `Invoice`, der Calculator und die übrigen Regelgruppen bleiben,
  wie sie sind") und wäre der größte Umbau.
- **Ein Test je Rechnungsart**, der die drei Angaben festschreibt, statt die
  Vorgabewerte zu streichen. Er schriebe die Enum-Einträge ein zweites Mal ab
  und fiele erst zur Laufzeit auf.

## Konsequenzen

- **Die fünf Einträge von `InvoiceType` sind länger**, mit benannten
  Argumenten statt `false, true, null`.
- **Der Rest an Disziplin aus ADR-0008 bleibt, jetzt begründet.** Wer hinter
  der Annahme `calculated.invoice.cancelled` liest statt `kind`, hat wieder ein
  optionales Feld in der Hand. Kommt ein solcher Zugriff auf, ist das der
  Anlass, das Verbergen neu zu erwägen – nicht die bloße Möglichkeit.
- **Das Satzbild ändert sich nicht.** Die Vergleichsdateien `*-erwartet.tex`
  bleiben zeichengleich.

Umgesetzt in #92.
