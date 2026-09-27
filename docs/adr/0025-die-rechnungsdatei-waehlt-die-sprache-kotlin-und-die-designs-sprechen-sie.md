# Die Rechnungsdatei wählt die Sprache, Kotlin und die Designs sprechen sie

Eine Rechnung steht ganz in einer Sprache. Welche, sagt die Rechnungsdatei
neben der Art: `sprache: en-GB`, ohne Eintrag `de-DE`. Die Sprache ist ein Tag
nach BCP 47 mit Variante, `Language` in `core.values`, eine geschlossene Menge
samt `Locale`. Was an ihr hängt, steht an drei Orten:

- **Die Sätze mit fachlicher Aussage in Kotlin**, wo sie schon standen:
  Pflichtsatz, Name des Dokuments und Beschriftung des Zeitraums in
  `InvoiceType`, der Vorgabegrund in `TaxCategory`, die Einheiten in `Unit`,
  Zahlungsfrist und Steuerhinweis in `ItemBlocks`, der Schlusssatz in
  `SellerConfig`. Jeder wird zu einem `when` über die Sprache ohne `else`.
  Die Daten schreibt `DateFormat`, eine Klasse für alle Sprachen.
- **Die Wörter eines Designs in seiner Sprachdatei**, eine je Design und
  Sprache: `helsinki-en-gb.tex`. Sie definiert die Beschriftungen als
  `\wort…` und die Schreibweise der Zahlen, soweit sie an der Sprache hängt.
  Der Renderer legt sie als `sprache.tex` neben die Vorlage, wie die
  Grundlage, und die Vorlage lädt sie mit `\input{sprache}`.
- **Der Schlusssatz in den Stammdaten**, als Tabelle je Sprache:
  `schlusssatz: {de-DE: …, en-GB: …}`. Ihre Schlüssel sind Sprachbereiche,
  gesucht wird erst der genaue Code, dann die Hauptsprache, dann die Vorgabe
  aus Kotlin.

Eine Sprache kommt schrittweise. Englisch kann zuerst nur die gewöhnliche
Rechnung, mit S, Z, E und O und ohne Skonto (#377). Alles andere lehnt
`SPRACHE_NOCH_NICHT_MOEGLICH` ab, und die englischen Zweige der fehlenden
Sätze werfen über `Language.notYet`.

## Warum

**Die Sprache gehört zum Kunden, das Design zum Rechnungssteller.** Stünde
sie in den Stammdaten, spräche jede Rechnung eines Rechnungsstellers dieselbe
Sprache – das Gegenteil des Anlasses. In der Rechnungsdatei wandert sie mit,
denn der `rechnung`-Skill nimmt die letzte Rechnung desselben Kunden als
Vorlage. Sie steht neben `art` und nicht beim Kunden, weil sie auch Sätze
bestimmt, die mit ihm nichts zu tun haben.

**Mit Variante, auch im Deutschen.** An der Variante hängt das Datum:
`de-AT` schriebe „Jänner“, `en-US` den Monat vor den Tag. Kommt eine zweite
Variante dazu, bleibt jede vorhandene Datei gültig und bedeutet dasselbe wie
zuvor. So gilt eine Regel ohne Ausnahme: Jeder Wert nennt seine Variante.

**Sätze in Kotlin, Wörter im Design** – die Grenze aus ADR-0017. Ob
„Rechnungsnr.“ oder „Rechnungsnummer“ dasteht, entscheidet das Design, und
jedes wählt seine Wörter selbst: helsinki schreibt „Quantity“, prag in seiner
schmalen Spalte „Qty“. Ein Satz, der etwas Rechtliches sagt, hat dagegen
genau einen Wortlaut, und der gehört nicht in jede Vorlage einzeln. Die Sätze
bleiben, wo sie standen, weil die Fachkataloge ihren Wortlaut tragen (#312)
und die Notiz der E-Rechnung dieselben Sätze braucht wie das Blatt.

**Ein `when` ohne `else` statt einer Tabelle zum Nachschlagen.** Eine neue
Sprache übersetzt erst, wenn jeder Satz einen Wortlaut hat oder ausdrücklich
wirft – derselbe Zug wie `InvoiceType` ohne Vorgabewerte (ADR-0009). Für die
Sprachdateien gibt es keinen Compiler; dort hält ein Test, dass alle
Sprachdateien eines Designs dieselben Makros definieren, und `Template.of`
prüft Vorlage und Sprachdatei zusammen auf jedes Makro des Renderers.

**Schrittweise, wie ZUGFeRD (ADR-0023).** Die heiklen Sätze – Storno,
Abschlag, Absetzung, Reverse Charge, die Befreiungsgründe mit gesetzlichem
Wortlaut – bekommen je einen eigenen Schritt, statt nebenbei übersetzt zu
werden. O ist im ersten Schritt dabei: Die Leistung an einen Unternehmer im
Drittland ist der häufigste Fall einer englischen Rechnung, und den Grund
schreibt der Rechnungssteller selbst.

**Die werfenden Zweige samt ihrem Netz.** Welche Sätze es noch nicht gibt,
steht zweimal: in `LanguageRules`, die ablehnt, und in den Zweigen, die
werfen. Ableiten lässt sich die Regel aus den Zweigen nicht – die Sätze
stehen zum Teil im Rand (`generate.render`), und den kennt der Kern nicht.
Deshalb hält ein Test beide Hälften zusammen: Er setzt jedes Beispiel auf
Englisch und verlangt entweder allein diesen Abbruch oder ein geprüftes PDF
samt E-Rechnung. Lässt die Regel etwas durch, dessen Satz noch wirft, wird er
rot. Ein leerer Satz statt des Wurfs fehlte still auf dem Blatt.

**Die Schlüssel des Schlusssatzes sind Sprachbereiche.** `sprache:` sagt, in
welcher Sprache eine Rechnung *ist*; ein Schlüssel der Tabelle sagt, für
welche Rechnungen ein Satz *gilt*. Ein `en:` gilt so ohne Änderung auch für
eine spätere Variante. Seitwärts wird nicht gesucht, `en-US` fällt nie auf
`en-GB`. Erlaubt sind nur unterstützte Sprachen und ihre Hauptsprache – ein
Satz, den keine Rechnung je verwendet, wäre derselbe stille Fehler wie ein
vertippter Schlüssel. Fehlt ein Satz, gilt die Vorgabe der Sprache: Ein
allgemeiner Dank schadet nicht, und in der falschen Sprache steht er so nie
da.

**Was sich an den Rechnungssteller richtet, bleibt deutsch**: Befunde,
Meldungen, die Schlüssel und Werte der YAML-Dateien, `einheit: Std.` auch
auf einer englischen Rechnung. Ebenso der Jobname: Er bildet die DocumentID
(ADR-0005) und soll nicht an der Sprache hängen, deshalb bleibt
`InvoiceType.documentName` ohne Sprache deutsch.

**BT-33 bleibt deutsch**, auch im XML einer englischen E-Rechnung:
„Registergericht …; Geschäftsführung: …“ beschreibt einen deutschen
Rechnungssteller nach deutschem Registerrecht. Eine englische Fassung wäre
ein eigener Schritt. Das XML sonst folgt der Sprache nur in seinen
Freitexten: Einleitung, Bemerkung, Pflichtsatz und Befreiungsgrund.

## Verworfen

Die Notiz `docs/notizen/2026-09-26-rechnungen-in-anderen-sprachen.md` hält
die Abwägung fest. Kurz:

- **Ein zweisprachiges Blatt**, „Rechnung / Invoice“. Die Positionen schreibt
  der Rechnungssteller trotzdem in einer Sprache, und jede Beschriftung würde
  doppelt so lang.
- **Die Sprache in den Stammdaten** oder **als Design** (`helsinki-en`). Das
  eine machte jede Rechnung eines Rechnungsstellers gleichsprachig, das
  andere verdoppelte das Satzbild und vermischte zwei Achsen.
- **Die Beschriftungen aus Kotlin**, als Platzhalter. Der Renderer entschiede
  über den Wortlaut eines Designs (ADR-0017).
- **Eine Bedingung in der Vorlage**, `\ifstrequal{<<SPRACHE>>}{en-GB}{…}{…}`.
  Eine Bedingung über `\ifblank` hinaus, die ADR-0017 ausschließt.
- **`ResourceBundle` für die Sätze.** Ein fehlender Schlüssel fiele erst zur
  Laufzeit auf, und die Sätze entstehen aus LaTeX-Fragmenten in je Sprache
  anderer Reihenfolge.
- **`Locale.lookupTag` für die Kette des Schlusssatzes.** Sie ist zwei
  Glieder lang, und welche Schlüssel es gibt, soll die geschlossene Menge
  sagen und nicht die Nachsicht des JDK.
- **Eine Sprachdatei für alle Designs.** Dann wählte nicht mehr das Design
  seine Wörter.

## Konsequenzen

- **Eine deutsche Rechnung sieht aus wie zuvor**, bei 300 dpi pixelgleich in
  beiden Designs und allen Beispielen. Byte-gleich ist sie nicht mehr: Die
  Sprachdatei geht in die Trailer-ID ein, und die Volltexte zeigen `\wort…`
  statt der Wörter.
- **Ein neues Design** braucht je Sprache eine Sprachdatei mit denselben
  Makros wie die übrigen, und das Pflichtangaben-Netz je Sprache seine Wörter.
- **Eine neue Sprache** braucht einen Eintrag in `Language`, einen Wortlaut in
  jedem `when` – oder einen werfenden Zweig samt Ausschluss in
  `LanguageRules` –, je Design eine Sprachdatei, eine Vorgabe des
  Schlusssatzes und ihre Schreibweisen im Pflichtangaben-Netz.
- **Alte Stammdaten mit `schlusssatz:` als Text** brechen beim Einlesen ab.
  Hingenommen: Der Generator war noch nicht im Einsatz.
- **Der Name des Landes folgt dem JDK** in der Sprache der Rechnung, wie schon
  auf Deutsch (#374): „UNITED KINGDOM“, nicht „VEREINIGTES KÖNIGREICH“.

Entschieden in #375 und #377.

## Nachtrag vom 27.09.2026: jede Rechnungsart auf Englisch

Teilrechnung, Abschlagsrechnung, Schlussrechnung und Stornorechnung gibt es
jetzt auch auf Englisch (#405), je in einem eigenen Schritt. Ausgeschlossen
bleiben die Kategorien AE, K und G und das Skonto. `LanguageRules` fragt
deshalb die Art nicht mehr; dass eine neue Art ihren englischen Namen und
Pflichtsatz nennt, erzwingen das `when` in `InvoiceType.documentName` und der
Parameter ohne Vorgabe.

**Die Begriffe sind Entscheidungen, keine Übersetzungen**, und stehen mit
„Vermeiden“ im Glossar:

- *Partial invoice*, wie Code 326 heißt – nicht *Interim* oder *Progress
  invoice*, die nach einem Abschlag klingen.
- *Advance payment invoice* und *advance payment* für den Abschlag, wie
  `SpecifiedAdvancePayment` im XML – nicht *Prepayment invoice*, obwohl Code
  386 so heißt: *Prepayment* ist die Vorauszahlung, die auch das Deutsche
  meidet.
- *Final invoice*, wie der Pflichtsatz der Abschlagsrechnung sie ankündigt.
- *Cancellation invoice* – nicht *Credit note*, so heißt Code 381 (ADR-0006),
  und nicht *Corrected invoice*, obwohl Code 384 so heißt: Auf dem Blatt
  behauptete das eine Rechnungskorrektur. *Self-billing* bleibt, wie im
  Deutschen die *Gutschrift*, der Rechnung des Leistungsempfängers
  vorbehalten.
- In der Absetzung *Less …* für „abzüglich“, *received* für „vereinnahmt“,
  wie die Sprachdateien schon beschriften, und *Balance due* für den
  Restbetrag – nicht *Amount due*, das wäre der ganze Rechnungsbetrag.
- *Expected service period* für den voraussichtlichen Leistungszeitraum.
  *Provided* und nicht *supplied* im Pflichtsatz: Die *supply* ist ein
  Begriff des britischen VAT, wie schon beim *Service period*.

**Die Pflichtsätze sagen Glied für Glied dasselbe wie die deutschen.** Sie
stehen deshalb je Art als eigener Parameter `englishMandatoryNote` neben
`germanMandatoryNote`, ohne Vorgabe (ADR-0009): Wer den einen ändert, hat den
anderen vor Augen, und zerlegt werden beide beim Laden der Klasse.

**Eine englische Schlussrechnung darf Abschläge absetzen, die deutsch
gestellt wurden.** Dieselbe Sprache wird nicht verlangt: Die Nummern hängen
an keiner Sprache, die Daten stehen im Block `abschlaege` als Werte und
erscheinen in der Sprache der Schlussrechnung, und `abschlaege` liest aus den
Abschlagsrechnungen nur Werte, keine Sätze.

**Das Netz** hält je Art die Werte einer englischen Rechnung fest,
`<art>-en-gb-inhalt.tex`, aus dem Beispiel der Art mit vorangestelltem
`sprache: en-GB`; die Freitexte darin bleiben deutsch. Eine Art, die eine
Sprache noch ablehnt, hat keine solche Datei – der Test führt so keine eigene
Liste. `englische-rechnung-erwartet.xml` hält das XML der englischen Rechnung
fest, und das Pflichtangaben-Netz kennt den Satz zum ausgeglichenen
Restbetrag je Sprache.

## Nachtrag vom 27.09.2026: Englisch kann alles, der Ausschluss fällt weg

Reverse Charge, innergemeinschaftliche Lieferung, Ausfuhr und Skonto gibt es
jetzt auch auf Englisch (#406). Damit hat jeder Satz des Programms einen
englischen Wortlaut.

**AE: „Reverse charge“, der Begriff der Verwaltung.** §14a Abs. 1 und 5 UStG
verlangen den Hinweis „Steuerschuldnerschaft des Leistungsempfängers“. Statt
seiner lässt die Verwaltung den Begriff zu, den Art. 226 Nr. 11a MwStSystRL
in der jeweiligen Sprachfassung verwendet (Abschnitt 14a.1 Abs. 6 und
Anlage 8 UStAE, eingefügt mit BMF vom 17.09.2025 – III C 2 - S 7290/00003/
003/013). Für Englisch nennt Anlage 8 „Reverse charge“, und genau das steht
da, ohne deutschen Zusatz und ohne Erklärung. Eine eigene Übersetzung wäre
ein Wortlaut, den keine Liste der Verwaltung kennt. Er gilt deshalb auch im
XML (BT-120), der maßgeblichen Fassung. Der Steuerhinweis bleibt „No VAT is
charged on …: Reverse charge.“: Der Leistende berechnet keine Steuer, der
Kunde führt sie ab, und der Satz ist gleich gebaut wie der deutsche.

**K und G: die Wörter der Richtlinie.** Anlage 8 nennt sie nicht, und einen
Wortlaut schreibt auch sonst niemand vor: Der Grund darf umgangssprachlich
stehen (Abschnitt 14.5 Abs. 20 UStAE). Bindend ist nur, dass er fachlich
dasselbe sagt wie der deutsche. K heißt „Exempt intra-Community supply of
goods“, G „Exempt export of goods“ – „exempt“ aus Art. 226 Nr. 11,
„intra-Community supply of goods“ aus Art. 138. „of goods“, weil beide eine
Lieferung sind; eine Dienstleistung ins Drittland ist O. Nicht
„zero-rated“, wie das britische VAT beide nennt: Das ist hier die Kategorie
Z, von der sich K und G gerade unterscheiden.

**Skonto: kein vorgeschriebener Wortlaut.** UStAE 14.5 Abs. 19 lässt eine
Angabe wie „2 % Skonto bei Zahlung bis …“ genügen, und Anlage 8 kennt Skonto
nicht. Der englische Satz ist gleich gebaut wie der deutsche: „For payment
by 21 August 2026, an early payment discount of 2% may be deducted; for
payment by 24 August 2026, 1.5%.“ – „of 2%“, weil der Artikel vor der Zahl an
ihrer Aussprache hinge (a 2%, an 8%). Die Zahlungsfrist sagt mit Skonto „in
full“, wie das „Due in full by“ im Kopf von helsinki. Die Begriffe stehen mit
„Vermeiden“ im Glossar; *Prompt payment discount* gehört nicht dazu, weil
HMRC damit eigene Regeln verbindet. Das XML trägt das Skonto ohne Text und
bleibt, wie es war.

**`SPRACHE_NOCH_NICHT_MOEGLICH` und `Language.notYet` fallen weg**, mit
`LanguageRules`. Die Regel schlüge auf keiner Sprache mehr an, und eine
Regel, die nie anschlägt, sowie eine Funktion ohne Aufrufer wären Pflege ohne
Nutzen. Verworfen: sie leer stehen lassen, als Gerüst für die nächste
Sprache. Kommt eine Sprache wieder schrittweise, bringt sie den Mechanismus
mit – Regel, werfende Zweige und den Test, der beide zusammenhält –, und die
Konsequenz oben zu einer neuen Sprache gilt dann wieder mit diesem Zweig.
Bis dahin braucht eine neue Sprache einen Wortlaut in jedem `when`.

Was die Regel nebenbei hielt, hält jetzt ein Test: Jede Sprache hat einen
Vorgabegrund genau für die Kategorien, die einen mitbringen. Darauf verlässt
sich `BEFREIUNGSGRUND_FEHLT`, das `TaxCategory.hasDefaultReason` ohne Sprache
fragt – fehlte einer Sprache ein Grund, stünde sonst still keiner auf dem
Blatt.

**Das Netz**: `reverse-charge.yaml` ist eine englische Rechnung mit AE an
einen Unternehmer in Österreich, und `skonto.yaml` läuft je Design auch auf
Englisch. Jedes Beispiel je Art ergibt auf Englisch ein gültiges PDF, ohne
den früheren Ausweg des Abbruchs. `verify` prüft an jeder Rechnung die
USt-IdNr. des Kunden, sobald die Datei sie nennt.
