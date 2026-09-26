# ZUGFeRD bettet ein eigenes XML in das gesetzte PDF ein

`erzeuge --zugferd` erzeugt statt eines PDF/A-1b ein PDF/A-3u mit
eingebettetem CII-XML im Profil EXTENDED. Ohne den Schalter bleibt alles, wie
es ist. Mit ihm läuft `erzeuge` so:

1. Der Renderer liefert wie bisher die LaTeX-Quellen. Aus derselben
   `AcceptedInvoice` schreibt ein eigener Writer im Paket `generate.einvoice`
   das XML.
2. Der Validator von Mustang prüft das XML: Schema und Schematron des Profils.
3. LuaLaTeX setzt wie bisher ein PDF/A-1b.
4. Mustang (`ZUGFeRDExporterFromA1`) macht daraus ein PDF/A-3u mit dem XML als
   `factur-x.xml`. Vor dem Speichern werden XMP, Info-Dictionary und die
   Datumsangaben am Anhang aus dem PDF/A-1b übernommen.
5. `PdfAValidator` prüft das Ergebnis als PDF/A-3u, eingebaut oder extern wie
   in ADR-0014.

Ans Ziel kommt nur, was beide Prüfungen besteht. Zieldatei, Name und
Überschreibschutz bleiben, wie ADR-0018 sie festlegt.

## Warum

**Profil EXTENDED, für alle Arten gleich.** Das hat ADR-0002 schon
entschieden: Nur dort bildet `SpecifiedAdvancePayment` die Abschläge der
Schlussrechnung mit ihrer Steueraufteilung ab. Ein Profil für alle Arten heißt
ein Writer und kein Zweig je Art.

**Der Generator schreibt das XML selbst.** Mustang brächte ein eigenes Modell
mit, das XML erzeugt. Zwei Gründe sprechen dagegen, beide im Prototyp
nachgemessen (#363):

- Das Modell kann `SpecifiedAdvancePayment` nicht schreiben. Das Element fehlt
  samt `PaidAmount` und `IncludedTradeTax`; nur das Prüfschema kennt es.
- Mustang rechnet die Positionen selbst, und zwar anders als ADR-0013. Bei
  10.000 zufälligen Positionen mit Rabatt in realistischer Größe wich das
  Netto bei 404 um einen Cent ab. Das XML ist die maßgebliche Fassung der
  E-Rechnung und darf keine andere Zahl tragen als das Blatt daneben. Mit
  eigenem Writer kommt jede Zahl aus der `CalculatedInvoice`, wie im PDF auch.

Mustangs Validator nimmt die Zahlen nach ADR-0013 an. Für den Writer genügt
`javax.xml` aus dem JDK. Den Pflichtsatz setzt er über eine Funktion neben
`toLatex`, wie ADR-0019 es für ein weiteres Format vorsieht.

**Das XML kommt nachträglich ins PDF.** Das PDF/A-3 braucht mehr als einen
Anhang: `/AFRelationship /Alternative`, den Eintrag unter `/AF` und ein
XMP-Schema `fx:`, das im XMP selbst erklärt ist. Mustang baut das alles. Im
Satz hätten wir es von Hand bauen müssen, siehe unter „Verworfen“.

**Reproduzierbar wie bisher.** Von sich aus setzt Mustang das aktuelle Datum
ins Info-Dictionary, ins XMP und an den Anhang. Außerdem schreibt es das XMP
neu, und dabei gehen Titel, Beschreibung und Sprache verloren. `setXML`
bereitet das Dokument aber schon vollständig vor. Danach lässt es sich mit
PDFBox korrigieren, bevor `export` ein einziges Mal speichert. Übernommen wird
nur, was LuaLaTeX ins PDF/A-1b geschrieben hat: das XMP (mit `pdfaid` auf 3/U
und Mustangs `fx:`-Beschreibung samt Schema-Erklärung), das Info-Dictionary und
als Datum am Anhang das Rechnungsdatum. Damit gilt weiter „dieselben Quellen,
dasselbe PDF“ (ADR-0005, #202). Die Trailer-ID aus `LatexCompiler` lässt
Mustang ohnehin stehen.

**PDF/A-3u, nicht 3b, und kein Schalter dafür.** Mustang erklärt das Ergebnis
als 3u. Das hält an allen 13 Beispielrechnungen in drei Varianten (helsinki,
helsinki mit Logo, prag mit Logo), und zwar im eingebauten wie im externen
veraPDF. 3u ist eine Obermenge von 3b: Wer 3b annimmt, nimmt auch 3u an.

**Jeder Prüfer hat eine Aufgabe.** Das PDF/A urteilt weiter die Naht
`PdfAValidator`, jetzt mit dem Profil als Angabe (1b oder 3u). ADR-0014 bleibt
damit ganz: der eingebaute Prüfer als Vorgabe, der externe als unabhängiger
Zeuge (`verapdf -f 3u`), der Test, der beide vergleicht. Mustang urteilt nur
über das XML, und zwar bevor es eingebettet wird. Ein ungültiges XML bricht
also ab, bevor ein PDF entsteht. Nötig ist diese Prüfung bei jedem Lauf: Der
Exporter prüft nichts. Er bettete im Prototyp auch ein XML ohne
Länderkennungen und mit falschem Gesamtbetrag klaglos ein.

Mustangs Validator nimmt ein XML ohne PDF an (`validate(byte[], String)`),
auch wenn seine Dokumentation das nicht sagt. Er läuft dann Schema und
Schematron des Profils durch und lässt die PDF-Prüfung weg.
`EInvoiceValidatorTest` hält das fest, samt einer Gegenprobe, die BR-09
verletzt.

**Der Schnitt folgt den bestehenden Nähten.** Der Writer liest die
`AcceptedInvoice` wie der LaTeX-Renderer und liegt deshalb neben ihm in
`generate.einvoice`. Einbetten und XML-Prüfung sind Werkzeuge wie LuaLaTeX und
veraPDF und liegen hinter der Naht `Typesetter` in `generate.toolchain`.
`PdfSources` trägt das XML dafür als optionalen Anhang. `toolchain` sieht nur
Bytes und bleibt frei vom Projekt. Der Schalter ändert nur, was in `PdfSources`
steht und nach welchem Profil geprüft wird. `run` in `erzeuge` bekommt keinen
zweiten Zweig.

## Verworfen

- **Das XML im Satz einbetten**, mit `pdfx` in der Option `a-3b` und einem
  Anhang aus LaTeX. Es bliebe bei einem Schreiber des PDF und ohne neue
  Abhängigkeit. Aber `pdfx` kennt ZUGFeRD nicht: Das XMP-Schema `fx:` samt
  Erklärung und die Einträge `/AF` und `/AFRelationship` wären Handarbeit in
  der Grundlage (ADR-0022), und zwar in TeX. Das CTAN-Paket `zugferd` hilft
  nicht. Es verlangt `\DocumentMetadata` statt `pdfx`, schreibt das XML
  selbst in TeX und kennt kein EXTENDED.
- **Das XML aus Mustangs Modell**, siehe oben: Ihm fehlt die Absetzung, und es
  rechnet anders.
- **Das Profil EN16931, EXTENDED nur für die Schlussrechnung.** Zwei Profile
  hießen zwei Zweige im Writer. ADR-0002 hat EXTENDED als Ziel festgelegt.
- **Ein eigenes Kommando oder `--format pdfa|zugferd`.** Ein eigenes Kommando
  wäre der zweite Weg zu einer Rechnungsdatei, und dann wäre #359 sofort fällig.
  Eine Auswahl hielte Platz für Formate frei, die niemand angefragt hat.
- **Ein Schalter `--pdfa-3b`.** Das einzige bekannte Risiko für 3u ist ein Logo
  (ADR-0012), dessen Schrift keine Unicode-Zuordnung trägt. Das fängt die
  Prüfung bei jedem Lauf als Abbruch ab. Der Schalter kommt, wenn der Fall
  eintritt.
- **Mustang prüft PDF und XML zusammen.** Dann urteilten im ZUGFeRD-Modus zwei
  Prüfer über das PDF/A, und der externe Prüfer aus ADR-0014 fiele dort weg.
- **Alles mitliefern, was Mustang nachzieht.** Siehe unter „Konsequenzen“ und
  den Nachtrag zu ADR-0014.

## Was ZUGFeRD nicht auslöst

Mehrere Notizen verschieben einen Umbau ausdrücklich auf diesen Schritt. In
diesem Zuschnitt ist er für keinen der Anlass:

- **Die Regeln zum Ziel aus `run` herauslösen** (#359,
  `docs/notizen/2026-09-16-drei-beobachtungen-ohne-anlass.md`). Es entsteht
  kein zweiter Weg zu einer Rechnungsdatei, nur andere Quellen hinter
  derselben Naht.
- **Gradle-Module und Komposition am Rand**
  (`docs/notizen/2026-09-14-umbauten-vor-dem-wachsen.md`). Der Kern hat
  weiterhin einen Verbraucher, und `einvoice` ist ein Randpaket wie `render`.
  Für den Schalter reicht die `TypesetterFactory` aus ADR-0014. Die Zeile in
  `CLAUDE.md`, nach der ZUGFeRD der Moment für ein ADR zur Paketstruktur sei,
  ist damit beantwortet: Der Schnitt ändert sich nicht.
- **Signieren** (`docs/notizen/2026-09-14-pdf-signieren.md`). Auch eine
  E-Rechnung braucht keine Signatur, und die übrigen Gründe der Notiz tragen
  weiter.

## Konsequenzen

- **Neue Abhängigkeit:** `org.mustangproject:library` und
  `org.mustangproject:validator` 2.26.0, beide unter Apache-2.0.
- **veraPDF wechselt auf die Familie `-jakarta`.** Mustang zieht sie nach, und
  neben unserer bisherigen `validation-model` lägen rund 800 Klassen doppelt im
  Klassenpfad. Welche gälte, entschiede die Reihenfolge. Die Pakete heißen
  gleich, und unsere Prüfer-Tests laufen damit unverändert grün. Die JVM-Schalter
  gegen die JAXB-Warnzeilen decken auch diese Familie ab (Nachtrag zu
  ADR-0014).
- **Zwei Gruppen werden ausgeschlossen:** `org.apache.xmlgraphics` (FOP und
  Batik, Mustangs Sichtfassung einer E-Rechnung) und
  `com.helger:en16931-cii2ubl` (die Umwandlung nach UBL samt `ph-ubl21` bis
  `ph-ubl24`). ZUGFeRD ist immer CII, und das Blatt setzt LuaLaTeX. Kommt je
  XRechnung in UBL dazu, kehrt `en16931-cii2ubl` zurück.
- **Die Auslieferung wächst** von 28 auf 50 JARs und von 20,9 auf 31,9 MB.
  `THIRD-PARTY.md` bekommt 22 Einträge. Jackson bleibt ungeladen, wie #250 es
  gemessen hat.
- **`erzeuge` ohne Schalter bleibt so schnell wie bisher.** Mit `--zugferd`
  kommen rund 3,5 bis 4,5 s dazu, fast alles für das Schematron.
- **Der Code hängt an zwei Eigenheiten von Mustang.** Das Dokument ist nur
  über das `protected`-Feld `doc` erreichbar, und `setXML` ruft `prepare()`
  selbst auf. Ein zweiter Aufruf hängt das XML zweimal an, und veraPDF bemerkt
  das nicht. Zugesagt ist beides nirgends. Deshalb halten drei Tests den
  Stand: Dieselben Quellen ergeben dieselben Bytes, das PDF trägt genau einen
  Anhang, und die Metadaten stimmen mit denen des PDF/A-1b überein. Ein
  Versionssprung von Mustang wird zuerst an ihnen gelesen.
- **`PackageStructureTest` bekommt eine Zeile für `generate.einvoice`.**
- **Umgesetzt wird in Schritten.** Zuerst kommt die gewöhnliche Rechnung mit
  Kategorie S, dann weitere Steuerkategorien, Rabatt und Skonto, Abschlags-,
  Teil- und Stornorechnung und zuletzt die Schlussrechnung mit
  `SpecifiedAdvancePayment`. Bis eine Art umgesetzt ist, bricht `--zugferd`
  bei ihr mit eigener Kennung ab, statt ein unvollständiges XML auszugeben.

Entschieden in #363.

## Nachtrag vom 26.09.2026: Die Steuerkategorien außer S

Seit #366 schreibt der Writer alle Kategorien aus `TaxCategory`. Dabei sind
drei Fragen entschieden worden.

**O neben anderen Kategorien bleibt erlaubt, ebenso die USt-IdNr. des
Verkäufers bei O.** Der Kern von EN 16931 verbietet beides (BR-O-02,
BR-O-11 bis BR-O-14), und Mustang prüft diese Regeln in BASIC und EN16931.
Im Profil EXTENDED fehlen sie ausdrücklich, und eine Rechnung mit O neben S
ist dort gültig. Eine Regel in `rules` lehnte deshalb eine Rechnung ab, die
das Blatt und das Profil unserer E-Rechnung annehmen. Wechselt das Profil
einmal, etwa zu XRechnung, ist die Frage neu zu stellen.

**Das Lieferland der innergemeinschaftlichen Lieferung ist das Land des
Kunden.** BR-IC-12 verlangt es (BT-80). Ein eigenes Feld für eine abweichende
Lieferanschrift, etwa beim Reihengeschäft, kommt erst mit einem Fall: Bei
einer Beratungsrechnung kommt K praktisch nicht vor. Nur dann trägt
`ApplicableHeaderTradeDelivery` etwas, und die Warnung
`PEPPOL-EN16931-R008` entfällt.

**Der Befreiungsgrund steht zweimal**: in der Aufschlüsselung und an jeder
Position, beide Male der Grund der Gruppe. EXTENDED verlangt beide gleich
(`BR-FXEXT-E-08rev` und die Geschwister je Kategorie). Welche Gruppe zu einer
Position gehört, sagt `CalculatedInvoice.vatGroupOf`, damit der Writer den
Gruppenschlüssel nicht nachbaut. Bei O steht an der Position kein Satz
(BR-O-05), in der Aufschlüsselung die 0.

## Nachtrag vom 26.09.2026: Rabatt und Skonto

Seit #367 schreibt der Writer beides.

**Der Rabatt** steht als Abschlag auf die Position (BG-27) mit dem Code 95 und
ohne Text, wie ADR-0013 es vorsieht. Ein Satz kommt mit seiner Basis, ein
Betrag allein. Mustangs Validator rechnet dabei nur die Summen nach, nicht
Satz × Basis; ein falscher Satz neben dem richtigen Betrag ginge durch.
Deshalb hält `CiiWriterTest` die Abschläge wörtlich fest.

**Das Skonto** steht strukturiert: je Stufe eigene Zahlungsbedingungen mit
`ApplicableTradePaymentDiscountTerms`, Frist in Tagen ab dem Rechnungsdatum
und Satz, ohne Betrag wie auf dem Blatt. Die Zahlungsbedingung mit dem
Zahlungsziel steht davor. Verworfen ist der Freitext der XRechnung
(`#SKONTO#TAGE=…#PROZENT=…#`): Er ist die Behelfslösung für Profile ohne
strukturierte Skontobedingungen, und EXTENDED hat sie. Beides zu schreiben
hieße, dieselbe Angabe zweimal zu führen, und ein Empfänger, der beide liest,
müsste entscheiden, welche gilt.

**Die Prüfung im Test zählt jede Warnung**, auch eine ohne `[ID …]`. So meldet
Mustang eine Summe, die er anders nachrechnet; bisher sah der Test nur
Warnungen mit Kennung und hätte gerade diese übersehen.

## Nachtrag vom 26.09.2026: Abschlags- und Stornorechnung

Seit #368 schreibt der Writer beide Arten, mit den Codes 386 und 384 aus
`InvoiceType`.

**Der Pflichtsatz wird eine Notiz (BT-22)**, nach Einleitung und Bemerkung.
Er macht erst das Dokument zu dem, was es ist; ohne ihn stünde der Abschlag
im XML wie eine Rechnung über die Leistung da. Gesetzt wird er mit
`toPlainText` in `generate.einvoice`, dem Format neben `toLatex`
(ADR-0019), und dafür darf `einvoice` jetzt `core.text` importieren. Ein
Datum steht ausgeschrieben wie auf dem Blatt, damit Notiz und PDF denselben
Satz lesen lassen.

**Der Tag der Vereinnahmung eines Abschlags ist der Steuerzeitpunkt
(BT-7)**: Die Steuer auf eine Anzahlung entsteht mit ihrem Eingang. Steht er
noch nicht fest, fehlt er, wie auf dem Blatt. Dass der Leistungszeitraum
eines Abschlags ein voraussichtlicher ist, sagt allein der Pflichtsatz;
EXTENDED hat dafür kein eigenes Feld.

**Das Storno nennt die Ursprungsrechnung zweimal**: im Pflichtsatz und als
Bezug (BG-3) mit Nummer und Datum. Die negativen Mengen stehen, wie sie
gerechnet sind, samt negativem Rabattbetrag; Bankverbindung und
Zahlungsbedingungen entfallen wie auf dem Blatt (`requestsPayment`). Für das
Datum des Bezugs erklärt das Wurzelelement jetzt auch den Namensraum `qdt`,
bei jeder Art – das ist die einzige Änderung an `rechnung-erwartet.xml`.

## Nachtrag vom 26.09.2026: Die Schlussrechnung, und der Abbruch fällt weg

Seit #369 schreibt der Writer die Schlussrechnung, und damit jede Art. Das
war der Grund für das Profil EXTENDED (ADR-0002).

**Je abgesetzter Abschlag ein `SpecifiedAdvancePayment`**, Feld für Feld wie
der Block `abschlaege` (ADR-0001): der gezahlte Betrag, der Tag der
Vereinnahmung, je Steueranteil Kategorie, Satz, Netto und Steuer, und die
Abschlagsrechnung mit Nummer, Code 386 und Datum. Die Summe steht als
`TotalPrepaidAmount`, der Restbetrag als `DuePayableAmount`. Die
Steueraufschlüsselung bleibt die der Gesamtleistung; die Abschläge mindern
den Zahlbetrag und nicht die Bemessungsgrundlage, wie auf dem Blatt. Mustang
rechnet dabei nur die Summe gegen den Restbetrag nach (BR-FXEXT-CO-16), nicht
den einzelnen Abschlag; den hält `CiiWriterTest` wörtlich fest.

**`CiiResult.NotYetPossible` und `ZUGFERD_NOCH_NICHT_MOEGLICH` entfallen.**
Der Abbruch war das Gerüst der Schritte aus "Konsequenzen": Solange eine Art
fehlte, sollte sie nicht still unvollständig ins XML gehen. Ohne fehlende
Art trüge der Rückgabetyp einen Zweig, den nichts mehr erreicht, und `Rule`
eine Kennung, die nie vergeben wird. `CiiWriter.write` liefert das XML. Die
Zusage, dass nichts still fehlt, hält jetzt das `when` ohne `else` über die
`InvoiceKind`: Eine neue Art muss dort entscheiden, was sie trägt. Kommt
einmal eine Angabe, die der Writer nicht schreiben kann, kehrt der Abbruch
zurück, mit einer Kennung für genau diese Angabe.

## Nachtrag vom 26.09.2026: Das XML lässt sich wieder auslesen

Seit #380 prüft `rechnungsgenerator xml rechnung.pdf`, ob ein PDF eine gültige
E-Rechnung nach ZUGFeRD ist, und gibt dann ihr XML auf stdout aus. Angenommen
wird jede E-Rechnung nach ZUGFeRD oder Factur-X, nicht nur die eigene. Das
Kommando heißt nach dem, was auf stdout steht, wie `abschlaege`; ein Schalter
an `erzeuge` wäre ein Aufruf, der mal ein PDF und mal etwas anderes ausgibt
(ADR-0003).

**Jeder Prüfer behält seine Aufgabe.** veraPDF urteilt über das PDF/A, und
zwar gegen das Profil, als das sich die Datei in ihrem XMP ausgibt
(`pdfaid:part`, `pdfaid:conformance`). Verlangt ist ein PDF/A-3, ob a, b oder
u, und `PdfAProfile` kennt dafür auch 3a und 3b. Ein PDF/A-1 oder ein PDF ohne
diese Angabe bricht ab. Mustang urteilt über das XML wie beim Erzeugen und
erkennt das Profil am XML selbst. Scheitert eine Prüfung, bleibt stdout leer,
und auf stderr stehen die Gründe beider Prüfer auf einmal. Eine Warnung ist
ein Hinweis auf stderr, und das XML kommt trotzdem.

**Die Prüfer ziehen in einen gemeinsamen Rand.** Sie lagen in
`generate.toolchain`, und ein Kommando importiert nicht aus dem Paket eines
anderen. Im Wurzelpaket `toolchain` liegen jetzt `PdfAValidator` samt
Adaptern, `EInvoiceValidator` und der Prozessläufer, auf dem der externe
Prüfer aufbaut. Satz und Einbetten braucht nur `erzeuge`: `Typesetter`,
`LatexCompiler`, `PdfSources` und `EInvoiceEmbedder` bleiben in
`generate.toolchain`, das jetzt aus `toolchain` importiert. Frei vom Projekt
ist damit der Rand, nicht mehr `generate.toolchain`. `xml` braucht nichts aus
dem Kern, weil es keine Rechnungsdatei liest. Der Kern bekommt also keinen
Verbraucher dazu, und für den Modulschnitt gilt weiter, was unter „Was
ZUGFeRD nicht auslöst“ steht.

**`EInvoiceValidator` liefert ein Ergebnis, statt zu werfen.** Dass ein
Verstoß eine Lücke im Generator ist, stimmt nur beim Erzeugen; bei `xml` ist
das XML die Eingabe. Der Prüfer liefert deshalb Fehler und Warnungen, wie
`PdfAValidator` ein `ValidationResult` liefert, und jedes Kommando
entscheidet selbst: `erzeuge` wirft wie bisher, jetzt in `LuaLatexTypesetter`,
und `xml` nennt den Grund zur Datei.

**Ausgepackt wird mit PDFBox.** Gesucht wird im Namensbaum der Anhänge samt
Kindknoten, nach den Namen der Fassungen: `factur-x.xml`,
`zugferd-invoice.xml`, `xrechnung.xml` und `ZUGFeRD-invoice.xml` aus
ZUGFeRD 1. Verglichen wird genau, denn 2.0 und 1 unterscheiden sich nur in
der Schreibweise. Keiner oder mehr als einer ist ein Grund zum Abbruch. Das
XML geht als Bytes auf den Ausgabestrom und nicht über `echo`, das Text
schreibt: Ein XML in einer anderen Kodierung als UTF-8 käme sonst verändert
heraus.

Verworfen:

- **Das ganze Paket `generate.toolchain` als Rand.** Es wäre ein rein
  mechanischer Umzug gewesen. Dann lägen aber Satz und Einbetten, die nur
  `erzeuge` braucht, dort, wo jedes Kommando sie sieht.
- **Mustangs `ZUGFeRDInvoiceImporter`.** Er kennt die Namen der Fassungen.
  Einen Fehler beim Lesen schreibt er aber nur ins Protokoll, das `slf4j-nop`
  stumm schaltet, von mehreren Anhängen nimmt er still den letzten, und eine
  Datei, die kein PDF ist, liest er als XML. Die Bytes holt er mit demselben
  Aufruf wie PDFBox.
- **Ein Schalter, der ohne Prüfung auspackt.** Er hätte einen Grund:
  nachzusehen, warum eine empfangene Rechnung scheitert. Er kommt, wenn der
  Fall eintritt; bis dahin tut es `pdfdetach`.
- **`--externe-pdfa-pruefung` für `xml`.** Die Naht erlaubt ihn jederzeit,
  gefragt hat bisher niemand.
- **Eine Kennung in `Rule`.** Gültig oder nicht sagen veraPDF und Mustang mit
  eigenen Kennungen, der Klausel der Norm und `BR-…`. Befunde betreffen eine
  Rechnungsdatei (ADR-0004), und eine solche liest `xml` nicht.
- **Das XML einlesen oder zeigen.** Aus ihm entsteht weder eine
  Rechnungsdatei noch ein Modell noch eine Sichtfassung; das Kommando reicht
  die Bytes durch.
