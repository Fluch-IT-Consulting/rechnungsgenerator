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

Dass Mustangs Validator ein XML ohne PDF annimmt, legt seine Schnittstelle
nahe (`validate(byte[], String)`), geprüft ist es aber noch nicht. Trägt es
nicht, prüft Mustang das fertige PDF samt XML, und das Urteil über das PDF/A
bleibt trotzdem bei der Naht.

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
- **Die Auslieferung wächst** von 28 auf 49 JARs und von 20 auf 31 MB.
  `THIRD-PARTY.md` bekommt 21 Einträge. Jackson bleibt ungeladen, wie #250 es
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
