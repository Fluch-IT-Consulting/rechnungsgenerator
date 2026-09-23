# Der Pflichtsatz nennt Werte der Rechnung über Platzhalter

`InvoiceType.mandatoryNote` darf Platzhalter `<<NAME>>` tragen – dieselbe
Schreibweise wie in `invoice.tex`. Beim Setzen löst sie ein `VariableResolver`
auf: `String.escapeLatex(resolver)` zerlegt den Satz an den Platzhaltern,
maskiert nur den festen Text und setzt die Werte als fertiges `Latex` ein.
Die Werte einer angenommenen Rechnung liefert `InvoiceVariableResolver`,
vorerst `URSPRUNGSNUMMER` und `URSPRUNGSDATUM`. (Aufgehoben, was Zerlegen und
Resolver angeht: Der Satz ist ein `TextTemplate`, der Resolver liefert Werte
ohne Format, siehe ADR-0019.)

Die Stornorechnung nennt ihre Ursprungsrechnung damit im Pflichtsatz – "hebt
die Rechnung R26-042 vom 14. August 2026 vollständig auf" –, und die Zeile
"Ursprungsrechnung" im Kopf entfällt.

## Warum

Der Satz verwies bis hierher auf "die genannte Ursprungsrechnung", also auf
eine Zeile zehn Zentimeter weiter oben. Er soll für sich lesbar sein.

ADR-0006 hatte genau das verworfen, mit drei Kosten. Beim Nachmessen bleibt
eine davon:

- **Die Reihenfolge des Escapens ist keine Falle mehr.** Zerlegt wird vor dem
  Maskieren; die Werte kommen als `Latex` und werden nicht noch einmal
  angefasst. Ersetzt wird in einem Durchgang über den Satz, sodass ein Wert,
  der selbst `<<…>>` enthält, Inhalt bleibt – derselbe Zug wie in
  `InvoiceRenderer.fillIn`.
- **Die Namen prüft nicht der Compiler, aber der Build.** Ein vertippter Name
  wirft beim Setzen, und jede Rechnungsart mit Pflichtsatz hat eine
  Vergleichsdatei `*-erwartet.tex`. Das ist dieselbe Stufe, auf der
  `TemplateMacro` gegen die Vorlage geprüft wird – nicht erst im PDF.
  (Aufgehoben: Die Namen sind ein Enum, siehe ADR-0019.)
- **Wortlaut und Werteliste stehen getrennt**, der Satz in `InvoiceType`, die
  Namen in `InvoiceVariableResolver`. Das bleibt. (Die Namen stehen seit
  ADR-0019 in `InvoiceVariable`.)

Das Argument, der Bezug sei in EN 16931 Struktur (BG-3, BT-25, BT-26) und keine
Prosa, trifft das Modell und nicht das PDF. Dort bleibt er Struktur, in
`InvoiceKind.Cancellation`; ein XML-Export liest ihn da und muss nichts aus
einem Satz herausklauben.

Auch der Unterschied zu ADR-0005 hält: Die Texte mit Platzhaltern stehen im
Quelltext, nicht in den Stammdaten. Kein Benutzer schreibt sie.

## Verworfen

- **Den Satz in den Renderer holen, als `when (kind)`.** Kotlin wäre die
  Vorlagensprache, der Compiler prüfte jeden Namen, und der Bezug käme ohne
  `null` an. Der Wortlaut verließe aber `InvoiceType`, und käme ZUGFeRD dazu,
  stünde er ein zweites Mal als reiner Text daneben. Die Vorlage mit
  Platzhaltern kennt kein Ausgabeformat: Für das XML genügte ein zweiter
  Resolver.
- **`resolve` liefert `Latex?`, und bei `null` wirft `escapeLatex`.** Dann
  stünde die Meldung an einer Stelle und nennte die Vorlage. Nur der Resolver
  weiß aber, *warum* ein Name nicht auflösbar ist – vertippt, oder auf dieser
  Rechnungsart ohne Wert –, und bei `null` sähen beide Fälle gleich aus.
- **`${name}` als Schreibweise.** Kotlin setzt in einem String-Literal an
  dieser Stelle selbst ein. Im Enum sind `label` und `code` sichtbar, also
  übersetzte ein vergessener Backslash vor `${code}` klaglos zu "384".
- **Die Kopfzeile behalten.** Dann stünde der Bezug zweimal auf dem Blatt. Ohne
  sie ist der Block rechts oben ruhiger – die Angabe war dort die einzige, die
  umbrach.

## Konsequenzen

- **ADR-0006 ist in zwei Punkten aufgehoben**: dem verworfenen "Den Bezug in
  den Pflichtsatz schreiben" und der Aussage, der Bezug stehe als eigene Zeile
  im Kopf. Der übrige ADR gilt.
- **Das `when` ohne `else` für den Bezug entfällt im Renderer (#128).**
  `InvoiceVariableResolver` fragt die Rechnungsart mit `as?`, und das wirkt wie
  ein `else`: Eine neue Art mit eigenem Bezug wird an dieser Stelle nicht zur
  Entscheidung gezwungen. Das ist der Preis. Er ist tragbar, weil eine solche
  Art ohnehin eigene Variablen bräuchte, und ein Pflichtsatz mit einem Namen,
  den seine Art nicht kennt, beim Setzen abbricht – in der Vergleichsdatei,
  nicht beim Kunden.
- **Eine weitere Variable** ist ein Zweig in `InvoiceVariableResolver`. Der
  Resolver ist nicht an den Pflichtsatz gebunden; jeder Text, der zu einer
  angenommenen Rechnung gehört, kann ihn benutzen.
- **Das Satzbild der Stornorechnung ändert sich**, gewollt:
  `stornorechnung-erwartet.tex` in genau zwei Zeilen, das PDF bleibt gültiges
  PDF/A-1b. Die übrigen Vergleichsdateien bleiben zeichengleich.

Umgesetzt in #141.
