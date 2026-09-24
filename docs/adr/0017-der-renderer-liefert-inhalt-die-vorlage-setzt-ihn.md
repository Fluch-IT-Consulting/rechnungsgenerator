# Der Renderer liefert Inhalt, die Vorlage setzt ihn

Abstände, Schriftgrad, Farbe, Linien, Beschriftungen, Trenner, Ort und
Reihenfolge stehen in der Vorlage des Designs (`templates/helsinki.tex`,
`templates/prag.tex`). Der Renderer liefert Werte und Makroaufrufe und
entscheidet, **ob** etwas dasteht – nicht, wie es aussieht. Er schreibt kein
LaTeX von sich aus: keinen Längennamen, kein `\\`, kein `\mbox`.

Die Grenze läuft nicht zwischen Kotlin und LaTeX, sondern zwischen zwei Arten
von Angaben:

- Eine **einzelne Angabe** – jede Angabe der Stammdaten, jede Kopfangabe wie
  Zahlungsziel oder Kundenreferenz – kommt als eigener Platzhalter und darf
  leer sein. Ob sie dasteht, entscheidet der Renderer über leer oder nicht
  leer; wo sie steht, wie sie heißt und was sie trennt (` · `, "Tel.",
  "Zahlbar ohne Abzug bis"), entscheidet die Vorlage. Sie fragt dafür nur
  `\ifblank` aus etoolbox, und ihr ist gleich, *warum* ein Wert leer ist.
- **Wiederholtes und fachliche Sätze** – Positionen, Summen, Absetzung,
  Pflichtsatz, Skonto, der Bankblock – kommen als Makroaufruf oder gar nicht.
  Hier liegt das "ob" beim Renderer, weil es an der Rechnungsart hängt.

Zwei Ausnahmen, beide begründet: Ein **Wortlaut mit fachlicher Aussage** kommt
auch als Beschriftung aus Kotlin (`ZEITRAUM_BESCHRIFTUNG`, "Voraussichtlicher
Leistungszeitraum") – das ist kein Satzbild. Und `EMPFAENGER` bleibt ein Block,
weil die Landzeile an `isDomestic` hängt und damit an einer Entscheidung.

## Warum

**Satzbild gehört vor Augen.** Wer einen Abstand ändern will, soll die Datei
öffnen, in der er ihn sieht, und nicht zwei Dateien treffen müssen. Stünden die
Millimeter in Kotlin, entschiede ein Design sein Aussehen an einer Stelle, die
kein Design kennt.

**Was in der Vorlage steht, prüft kein Compiler – also muss die Naht schmal
sein.** Deshalb ist sie aufgezählt statt beschrieben: `TemplateValue` nennt
jeden Platzhalter, `TemplateMacro` jedes Makro samt Stelligkeit, und beim Bau
einer Vorlage wird beides gegen sie gehalten. Ein vertippter Makroname fällt
damit beim Setzen auf, mit einer Meldung, die den Namen nennt, statt erst im
LuaLaTeX-Lauf. Die Werte-Map hat `TemplateValue` als Schlüssel und entsteht in
einem `when` ohne `else`: Ein neuer Platzhalter zwingt zur Entscheidung.

**Rohes LaTeX im Renderer war eine Fehlerquelle mit Satzbild-Folgen.** Vorher
schrieb er `\\[\positionsabstand]` und `\mbox` selbst; ein vertippter
Längenname fiel erst LuaLaTeX auf, und eine Zeile, die nach einem `\\` mit `[`
begann, las LaTeX als Abstandsangabe und brach ab. Heute verbinden
`\zeilenwechsel` und die Trenner die Zeilen, die Abschlusszeile eines
Betragsblocks beendet die Zeile davor selbst, die Person im Briefkopf setzt
`\kopfperson` – und das `{}`, das `Latex.macro` hinter ein Makro ohne Argument
setzt, schließt den Abstandsfall aus. `Template.fillIn` prüft beim Füllen, dass
kein Wert ein `\\` enthält.

**Die Vorlage ist ein Typ, kein String.** `Template` hat einen privaten
Konstruktor – derselbe Zug wie bei der `AcceptedInvoice`: Was ungeprüft ist,
lässt sich nicht bauen. `Template.of` streicht die Kommentarzeilen und prüft
dann Makros und Pflicht-Platzhalter, `fillIn` füllt und prüft die Werte, `uses`
beantwortet, ob die Vorlage einen Platzhalter setzt. Vorher waren das fünf
`internal`-Funktionen, die `template: String` herumreichten; die Reihenfolge
"erst streichen, dann prüfen" kannte nur `render`, und ein Test, der sie nicht
einhielt, prüfte die Vorlage mitsamt ihren Kommentaren.

**Pflicht ist, was an Rechnungsart oder Rechnung hängt.** `fillIn` verlangt nur
eine Richtung: Jeder Platzhalter der Vorlage bekommt einen Wert. Die
Gegenrichtung hätte verboten, dass ein Design einen Wert nicht setzt. Stattdessen
verlangt der Bau der Vorlage die Platzhalter mit `TemplateValue.required` von
jeder Vorlage – ließe ein Design sie weg, fehlte auf einem Teil der Rechnungen
eine Angabe, und kein Beispiel zeigte es. Für die Stammdaten gibt es keine
solche Pflicht: "Steuernummer **oder** USt-IdNr." lässt sich als Kennzeichen
nicht ausdrücken, das prüft das Pflichtangaben-Netz am fertigen PDF, je Design
und Rechnungsart. Ebenso wenig für einen **Schalter** (`LOGO`, `RABATTSPALTE`),
der keinen Inhalt hat, sondern nur sagt, ob die Vorlage ein Mittel einsetzt, und
für die **Einleitung**, die ein Design weglassen darf. Freiwillig heißt aber
nicht frei: Kundennummer, Auftragsnummer, Kundenreferenz und der Schlusssatz aus
`bemerkung` bleiben Pflicht – das sind Angaben und keine Begleitsätze, jemand
hat sie für diese eine Rechnung hingeschrieben.

**Eine benannte Regel neben der Pflichtliste, und nur eine.**
`TemplateValue.ZAHLUNGSANGABE`: Jede Vorlage sagt etwas über die Zahlung –
`helsinki` das Zahlungsziel als Datum im Kopf, `prag` die Frist als Satz unter
den Summen. Pflicht ist deshalb keiner der beiden Platzhalter, aber einer von
beiden, und geprüft wird das beim Bau der Vorlage statt am fertigen PDF.

## Verworfen

- **Bedingungen in der Vorlage über `\ifblank` hinaus.** Was an der
  Rechnungsart hängt, würde sonst je Design neu entschieden – ohne Compiler,
  ohne `when` ohne `else`, und mit der Aussicht, dass zwei Designs dieselbe
  Frage verschieden beantworten.
- **Eine allgemeine Liste von Platzhaltergruppen** statt der einen benannten
  Regel `ZAHLUNGSANGABE`. Es gibt genau diesen einen Fall. Ein zweiter soll
  nicht als Listeneintrag hereinkommen, sondern hingeschrieben werden – wer ihn
  hinschreibt, steht vor der Frage, welche Art von Angabe er vor sich hat.
  Derselbe Zug wie `InvoiceType` ohne Vorgabewerte (ADR-0009): Der Nächste
  entscheidet, statt zu erben.
- **Eine eigene Funktion für jede Angabe mit Formatierschritt.** Eine einzelne
  Angabe steht mit ihrer Zeile direkt im `when` von `InvoiceRenderer.values`,
  auch wenn sie `formatted()` oder `GermanFormat.date` braucht – so stehen dort
  seit jeher `USTID`, `IBAN` und `RECHNUNGSDATUM`. Eine eigene Funktion bekommt
  nur, was *entscheidet*: `MetadataBlocks` behält die Anschrift als Block und
  die Vereinnahmung mit ihrem `when` über die Rechnungsart. Wer beim Lesen von
  `values()` in eine andere Datei springt, soll dort eine Entscheidung finden
  und nicht einen Pfad.
- **Den Unterschied im Einzug einebnen** zwischen der gedämpften Zweitzeile
  einer Position und der eines Abschlags. Das ist Satzbild und gehört vor
  Augen, nicht in einen Umbau, der zwei Makros zu einem macht.
- **Ein zweiter Weg in `escapeLatex`, der in Daten jeden Punkt entschärft.**
  Der Satzabstand ist ebenfalls Satzbild: Im ganzen Dokument gilt
  `\frenchspacing`, weil TeX sonst jeden Punkt nach Kleinbuchstabe oder Ziffer
  für ein Satzende hält ("Dr. Erika") und sich in Eingabedaten nicht erkennen
  lässt, wo ein Satz wirklich endet. Den breiten Abstand setzt allein
  `\hinweisabsatz`, der die eigenen Sätze des Programms trägt. Ein zweiter Weg
  in `escapeLatex` hätte die Bedeutung von 74 Aufrufen geändert, für eine
  Entscheidung, die in die Vorlage gehört.

## Konsequenzen

- **Ein neues Design ist eine Vorlage und kein Code** (ADR-0011). Es kommt mit
  demselben Vokabular aus; wer ein Wort braucht, das `TemplateValue` und
  `TemplateMacro` nicht haben, stellt die Frage nach der Art der Angabe neu und
  baut nicht am Renderer vorbei.
- **Der Summenblock ist beschriftet von der Vorlage** (`\nettozeile`,
  `\steuerzeile`, `\gesamtzeile`), Kotlin liefert Satz und Beträge. Die
  Absetzung behält dagegen ihre Wörter in Kotlin: "Restbetrag" steht im
  Glossar, ist also ein Fachbegriff und kein Satzbild – dieselbe Grenze wie bei
  `ZEITRAUM_BESCHRIFTUNG`, nur an einem Block statt an einer Angabe.
- **Die Spaltenzahl steht an einer Stelle.** Eine Tabellenzeile ist ein Makro
  (`\positionszeile` mit sieben Argumenten, das erste die laufende Nummer, das
  sechste der Rabatt), statt in Vorlage und Kotlin getrennt getroffen zu werden.
- **Die Vorlage darf mehr Makros haben, als der Renderer aufruft**
  (`\kopfperson`, `\blocktrenner`, `\ustidtrenner`). Geprüft wird nur, dass
  sie jedes kennt, das er aufruft.
- **Ein Datum trägt `~` hinter dem Tag**, damit es auch unter `\frenchspacing`
  keine Lücke bekommt. In den XMP-Metadaten wäre `~` ein Zeichen, dort gilt
  `GermanFormat.dateInMetadata`.
- **Die Tests mit kaputten Vorlagen gehen durch `Template.of`**, also durch
  dieselbe Tür wie der Betrieb. Der Bau aus Quelltext gehört zum Interface und
  ist kein Testfenster.

Nachträglich festgehalten am 22.09.2026 aus #284. Entschieden wurde es in #76,
#143, #147, #187, #188, #201, #209, #244, #248 und #249 – bis dahin stand die
Begründung allein in `CLAUDE.md`.
