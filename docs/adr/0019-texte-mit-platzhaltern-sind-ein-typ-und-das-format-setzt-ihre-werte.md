# Texte mit Platzhaltern sind ein Typ, und das Format setzt ihre Werte

`InvoiceType.mandatoryNote` ist ein `TextTemplate<InvoiceVariable>` und kein
String mehr. Die Arbeit teilen sich vier Stellen, jede kennt nur ihren Teil:

- **`TextTemplate<V>`** (`values`): ein Wortlaut, beim Bau zerlegt in festen
  Text und Variablen vom Typ `V`. Es kennt kein Ausgabeformat und kein
  Vokabular. Die Schreibweise `<<NAME>>` bleibt die aus ADR-0010.
- **`InvoiceVariable`** (`values`): das Vokabular der Rechnung, vorerst
  `URSPRUNGSNUMMER` und `URSPRUNGSDATUM`.
- **`VariableResolver<V>`** (`values`) liefert zu jeder Variablen einen
  `TextValue`, einen Wert ohne Format: `Text` oder `Date`. Die Werte einer
  angenommenen Rechnung liefert `InvoiceVariableResolver`. Er kennt kein
  Format und liegt deshalb bei der `AcceptedInvoice` und nicht mehr in
  `generate.render`.
- **Das Format** entscheidet, wie fester Text und Werte aussehen. Für LaTeX ist
  das `TextTemplate.toLatex(resolver)` in `generate.render`: Es maskiert den
  festen Text und setzt jeden Wert in `\zusammen`.

## Warum

**Drei Stellen kannten die Namen als Zeichenkette.** `InvoiceType` schrieb
sie, `InvoiceVariableResolver` beantwortete sie mit einem `when` über Strings,
und `PflichtangabenTest` ersetzte sie ein zweites Mal selbst mit `replace`. Es
war die eine Stelle im Projekt, an der eine Zusage über drei Schichten nicht
der Typ hielt, sondern eine Konvention (#283). Jetzt zwingt eine neue Variable
jeden Resolver zur Antwort, und ein neuer Werttyp jedes Format – beides als
`when` ohne `else`.

**Der zweite Verbraucher ist schon da.** ADR-0010 sagte über ein späteres XML:
„Für das XML genügte ein zweiter Resolver.“ Der Test ist so ein Verbraucher: Er
will denselben Satz als Klartext, wie ihn `pdftotext` liest, nicht als LaTeX.
Mit einem Resolver, der fertiges LaTeX liefert, konnte er ihn nicht benutzen
und baute die Ersetzung selbst nach.

**Holen und Formatieren hängen an Verschiedenem.** Woher ein Wert kommt, hängt
an der Rechnung und ihrer Art. Wie er aussieht, hängt am Format. Wie der feste
Text aussieht, hängt *nur* am Format. Getrennt steht jedes davon einmal da: das
Maskieren einmal je Format, das Nachschlagen einmal je Vokabular.

**Ein vertippter Name fällt früher auf.** Zerlegt wird beim Bau, und das
geschieht, wenn `InvoiceType` geladen wird. Dann wird jeder Test rot, der die
Rechnungsart anfasst, und nicht erst der Vergleich einer Stornorechnung.

## Verworfen

- **`NoteVariable` und `NoteTemplate`**, die Namen aus #283. Sie machen den
  ersten Verbraucher zum Namen der Sache.
- **`TemplateVariable`.** `Template` ist in `generate.render` die Vorlage des
  Designs, neben `TemplateValue` und `TemplateMacro`. Ein `TemplateVariable`
  läse sich als drittes Geschwister davon, also gerade als die Welt von
  `<<NAME>>`, die getrennt bleiben soll.
- **`MessageTemplate`.** „Message“ ist im Projekt die Meldung eines Befunds.
  Der Pflichtsatz ist Inhalt der Rechnung.
- **Die Variablen im `TextTemplate` verschachtelt**, als
  `TextTemplate.Variable`. Dann wüsste der allgemeine Typ, welche Werte es
  gibt, und das gehört zur Rechnung.
- **Den Wortlaut aus Teilen bauen**, `TextTemplate.of("… ", URSPRUNGSNUMMER, " vom ", …)`.
  Ein Tippfehler wäre ein Übersetzungsfehler statt eines Laufzeitfehlers, und
  `values` bräuchte gar keine Grammatik. Aber der Satz läse sich nicht mehr am
  Stück, und der Laufzeitfehler kommt ohnehin in jedem Unit-Test.
- **Ein Resolver, der weiter `Latex` liefert** (`LatexVariableResolver`). Ein
  zweites Format hätte das Nachschlagen samt der Frage nach der Rechnungsart
  wiederholen müssen.
- **Ein Interface, das Text und Variable bündelt**, etwa
  `TextTemplateFormatter<V, R>`. Das Maskieren hängt nur am Format, der Wert
  auch am Vokabular und an der Rechnung. Gebündelt müsste jedes LaTeX-Format
  das Maskieren wiederholen.
- **Der echte `InvoiceVariableResolver` im `PflichtangabenTest`.** Holte der
  das falsche Datum, sähe der Test dasselbe falsche Datum und bliebe grün. Der
  Test holt den Bezug deshalb weiter selbst, jetzt als eigener
  `VariableResolver<InvoiceVariable>`.

## Konsequenzen

- **ADR-0010 ist in zwei Punkten aufgehoben**: in der Aussage, der Resolver
  liefere fertiges `Latex` und `String.escapeLatex(resolver)` zerlege den Satz,
  und im Preis „Die Namen prüft nicht der Compiler, aber der Build“. Der übrige
  ADR gilt: die Schreibweise, der eine Durchgang, der Wortlaut im Quelltext und
  nicht in den Stammdaten.
- **Zwei Mengen von `<<NAME>>`, zwei Grammatiken.** Die Platzhalter der
  Design-Vorlage (`TemplateValue`) sucht `Template` mit seinem eigenen
  `PLACEHOLDER`, die Variablen eines Textes zerlegt `TextTemplate`. Die
  Schreibweise ist dieselbe, die Mengen sind es nicht: Ein Pflichtsatz kann
  kein `<<FIRMA>>` nennen.
- **Der Fehler steht in der Initialisierung eines Enums.** Der erste Zugriff
  auf `InvoiceType` wirft einen `ExceptionInInitializerError` mit der Meldung
  als Ursache, jeder weitere nur `NoClassDefFoundError`. Laut ist das, aber
  nicht freundlich zu lesen. Die lesbare Meldung prüft `TextTemplateTest` mit
  einem eigenen Vokabular.
- **Eine weitere Variable** ist ein Eintrag in `InvoiceVariable` und ein Zweig
  in jedem Resolver, heute `InvoiceVariableResolver` und der im
  `PflichtangabenTest`. Beide hält der Compiler an.
- **Ein weiterer Werttyp**, etwa ein Betrag, ist ein Eintrag in `TextValue` und
  ein Zweig in jedem Format, heute `toLatex` und die Klartext-Fassung im Test.
- **Ein weiteres Format**, etwa eine Notiz im ZUGFeRD-XML, ist eine Funktion
  neben `toLatex`. Den `InvoiceVariableResolver` findet sie bei der
  `AcceptedInvoice`, die sie ohnehin braucht.
- **Das Satzbild ändert sich nicht.** Alle Vergleichsdateien bleiben
  zeichengleich.

Umgesetzt in #283.

## Nachtrag vom 29.09.2026: Wo die Typen heute liegen

Die Pakete oben sind die des Stands von #283. Seit #310 und #312 liegen
`TextTemplate`, `VariableResolver` und `TextValue` in `core.text`, einem Blatt
ohne Projektimporte, und `InvoiceVariable` als Vokabular der Rechnung in
`core.model`; in `values` stehen nur noch Werte. An der Aufteilung auf die vier
Stellen ändert das nichts.
