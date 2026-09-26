# Rechnungen in anderen Sprachen

Notiz vom 26.09.2026. Anlass war die Frage, wie sich Rechnungen an Kunden
schreiben lassen, die kein Deutsch lesen – zuerst auf Englisch. Was im
Gespräch festgelegt wurde, steht zuerst, der Rest ist ein Vorschlag. Ein ADR
entsteht mit dem ersten Schritt, der ihn umsetzt.

Die Rechtslage unten stammt aus dem Gespräch und ist **nicht** gegen die
Quellen nachgelesen. Was vor dem Umsetzen zu prüfen ist, steht in einem
eigenen Abschnitt.

## Festgelegt

- **Einsprachig.** Jede Rechnung steht ganz in einer Sprache. Das Programm
  kann mehrere, jedes PDF spricht eine. Warum nicht zweisprachig, steht unter
  „Erwogen und verworfen“.
- **Die Sprache nennt die Rechnungsdatei**, ohne Eintrag `de-DE`. Nicht die
  Stammdaten: Das Design gehört zum Rechnungssteller (ADR-0011), die Sprache
  zum Kunden. Der `rechnung`-Skill nimmt die letzte Rechnung desselben Kunden
  als Vorlage, die Angabe wandert also von selbst mit.
- **Sie steht oben neben `art`**: Sie ist eine Eigenschaft des ganzen
  Dokuments, nicht nur des Kunden, und bestimmt auch Sätze, die mit ihm nichts
  zu tun haben.
- **Zuerst britisches Englisch, geschrieben `sprache: en-GB`** – ein Code nach
  BCP 47, so wie `land: AT` einer nach ISO 3166 ist, und so geht er auch in
  die Metadaten. Mit der Variante im Code ist von Anfang an klar, welche
  Schreibweise gilt, vor allem beim Datum. Kommt `en-US` dazu, bleibt jede
  vorhandene Datei gültig und bedeutet dasselbe wie zuvor; ein `en` allein
  müsste dann erst eine der beiden werden. Viel trennt die beiden Varianten auf
  einer Rechnung sonst nicht: Zahlen und Beträge schreiben sie gleich, es
  bleiben das Datum und einzelne Wörter wie „cancelled“.
- **Auch Deutsch nennt seine Variante, `de-DE`.** Derselbe Grund: Auch im
  Deutschen hängt das Datum an der Variante, `de-AT` schreibt „14. Jänner
  2026“ (nachgemessen mit dem JDK). So gilt eine Regel ohne Ausnahme – jeder
  Wert nennt seine Variante –, auch wenn eine zweite deutsche nicht absehbar
  ist.
- **Der Schlusssatz steht je Sprache in den Stammdaten**, als Tabelle:
  `schlusssatz: {de-DE: …, en-GB: …}`. Er ist eine Höflichkeit des
  Rechnungsstellers und auf jeder seiner Rechnungen derselbe, nur eben je
  Sprache. Ohne das landete der deutsche Satz still auf einer englischen
  Rechnung. Dass die Tabelle vorhandene Stammdaten ungültig macht, ist
  hingenommen: Der Generator ist noch nicht im Einsatz. Die Vorlage von
  `einrichten` und ihr Netz ziehen mit.
- **Fehlt der Schlusssatz für eine Sprache, gilt ihre Vorgabe aus Kotlin**,
  wie heute die deutsche – je Sprache eine, über ein `when` ohne `else`. Kein
  Abbruch: „Leer scheitert laut“ gilt in den Stammdaten für Angaben, die
  falsch schaden, etwa die Bankverbindung. Ein allgemeiner Dank schadet nicht,
  und in der falschen Sprache steht er so nie da.
- **Das kurze Datum ist im Englischen ISO 8601**, `2026-07-01`. Britisch wäre
  `01/07/2026`, und das liest ein amerikanischer Leser als 7. Januar. Das
  lange Datum bleibt britisch, `14 August 2026`: Mit ausgeschriebenem Monat
  versteht es jeder.
- **Das € steht vor dem Betrag**, `€8,217.50`, wie es im Britischen üblich
  ist. Die übrigen Schreibweisen des ersten Schritts – `19%`, `−€200.00`, die
  Einheiten `hrs`, `days`, `pcs`, `flat rate` – stehen in #377.

## Was die Sprache berührt

- **Die Beschriftungen der Vorlagen**: „Menge“, „Einzelpreis“, „Seite n von
  N“, „Fortsetzung auf der nächsten Seite“, „Bankverbindung“, „Mit
  freundlichen Grüßen“ und die übrigen, in `helsinki.tex` und `prag.tex`.
- **Die Schreibweise der Zahlen**: `\sisetup` mit Komma und Punkt, `\betrag`
  mit nachgestelltem `~€`. Heute wählt sie das Design (ADR-0021), das
  Dezimalzeichen hängt aber an der Sprache.
- **Die fachlichen Sätze aus Kotlin** (ADR-0017): die Pflichtsätze in
  `InvoiceType`, `servicePeriodLabel` und `itemPeriodLabel`, Steuerhinweis,
  Zahlungsfrist und Skonto in `ItemBlocks`, Absetzung und „Restbetrag“ in
  `DeductionBlocks`, `TaxCategory.defaultReason`.
- **Die Daten**: `GermanFormat` mit `Locale.GERMANY`.
- **Die Metadaten**: `Language` in der `.xmpdata` (`de-DE`), dazu Titel und
  Betreff mit „vom“.
- **Werte, die Eingabe und Blatt zugleich sind.** `Unit.label` ist
  YAML-Schlüssel und Text in der Positionstabelle („Std.“), `InvoiceType.label`
  liefert `documentName` für den Titel. Die Eingabe bleibt deutsch, das Blatt
  nicht – beides muss sich trennen. `documentName` steckt außerdem im Jobnamen
  (`CalculatedInvoice.fileBaseName`) und damit in der DocumentID (ADR-0005).
  Entsteht der Jobname aus dem Schlüssel der Datei, braucht er keine Sprache.
- **Die Landzeile der Anschrift** (#374): Der ausgeschriebene Name des Landes
  steht in der Sprache der Rechnung. #374 kam vorher und schreibt ihn auf
  Deutsch aus, über `Locale.getDisplayCountry(Locale.GERMAN)` in
  `MetadataBlocks`. Dort wird aus `Locale.GERMAN` die Sprache der Rechnung.

## Was sie nicht berührt

- **Alles, was sich an den Rechnungssteller richtet**: Befunde, Meldungen,
  Hilfe, die Schlüssel und Werte der YAML-Dateien. `einheit: Std.` bleibt die
  Schreibweise in der Datei, auch auf einer englischen Rechnung.
- **Das XML der E-Rechnung**, bis auf die Freitexte: Einleitung und Bemerkung
  (BT-22) schreibt der Rechnungssteller in der Sprache der Rechnung, und den
  Befreiungsgrund (BT-120) liefert ohne Eintrag `defaultReason`.
- **Die Silbentrennung**: Beide Designs schalten sie ab, deshalb braucht es
  weder babel noch polyglossia. `\frenchspacing` in der Grundlage ist eine
  Frage der Richtigkeit bei Abkürzungen, nicht der Sprache.

## Vorschlag

- **Beschriftungen je Design und Sprache in einer Sprachdatei**, etwa
  `helsinki-de-de.tex` und `helsinki-en-gb.tex`. Sie definiert die Beschriftungen
  als Makros, der Renderer legt die passende über `PdfSources.files` neben die
  Vorlage, wie heute die Grundlage. Ein Test prüft, dass alle Sprachdateien
  eines Designs dieselben Makros definieren. Nicht aus Kotlin: Ob
  „Rechnungsnr.“ oder „Rechnungsnummer“ dasteht, entscheidet das Design.
- **Die Zahlenschreibweise, soweit sie an der Sprache hängt**, zieht in die
  Sprachdatei: Dezimalzeichen, Tausendertrenner, Stellung des €. Ab wie vielen
  Stellen gruppiert wird, kann beim Design bleiben. Das wäre ein Nachtrag zu
  ADR-0021.
- **Die fachlichen Sätze bleiben in Kotlin**, je Satz ein `when` über eine
  `enum class Language` ohne `else`. Eine weitere Sprache übersetzt erst, wenn
  jeder Satz einen Wortlaut hat – derselbe Zug wie `InvoiceType` ohne
  Vorgabewerte (ADR-0009).
- **`GermanFormat` wird ein Format je Sprache.**
- **Erster Schritt wie bei ZUGFeRD (ADR-0023)** (#377): Englisch zuerst nur
  für `rechnung`, und ohne die Kategorien AE, K und G und ohne Skonto. Alles
  andere lehnt ein Befund ab, etwa `SPRACHE_NOCH_NICHT_MOEGLICH`, bis sein
  englischer Wortlaut entschieden ist. So bekommen die heiklen Sätze – Storno,
  Abschlag, Absetzung, Reverse Charge – je einen eigenen Schritt, statt
  nebenbei übersetzt zu werden. Nicht allein Kategorie S: Eine englische
  Rechnung geht fast immer ins Ausland, und mit S allein deckte der Schritt
  kaum eine ab. O, die Leistung an einen Unternehmer im Drittland, kostet nur
  einen Satz, denn den Grund schreibt der Rechnungssteller selbst. AE, K und G
  bleiben draußen, weil ihr Befreiungsgrund eine Vorgabe mit gesetzlichem
  Wortlaut hat.

## Offen

- **Englische Begriffe fürs Glossar**, mit „Vermeiden“ wie im Deutschen.
  Das sind Entscheidungen, keine Übersetzungen:
  - Das Storno darf nicht „Credit note“ heißen: So heißt Code 381, den das
    Projekt bewusst nicht verwendet (ADR-0006).
  - „Self-billing“ ist im Englischen reserviert, so wie „Gutschrift“ im
    Deutschen.
  - Für die Abschlagsrechnung kommen „Prepayment invoice“ (so heißt Code 386)
    und „Advance payment invoice“ in Frage.
  - Dazu die Wörter des Absetzungsblocks: Abschlag, vereinnahmt, Restbetrag.

## Rechtslage, vor dem Umsetzen nachzulesen

- §14 UStG schreibt keine Sprache vor. Das Finanzamt kann nach §87 Abs. 2 AO
  eine Übersetzung verlangen.
- §14a Abs. 5 UStG verlangt den Hinweis „Steuerschuldnerschaft des
  Leistungsempfängers“. Die englische Fassung der Richtlinie sagt „Reverse
  charge“ (Art. 226 Nr. 11a MwStSystRL). Nachzulesen ist, ob und wo die
  Verwaltung den fremdsprachigen Begriff ausdrücklich zulässt.
- Ebenso die Befreiungsgründe mit Vorgabe (`K`, `G`) und die Pflichtsätze von
  Abschlagsrechnung (UStAE 14.8) und Storno: Der englische Wortlaut darf
  fachlich nichts anderes sagen als der deutsche.

## Erwogen und verworfen

- **Ein zweisprachiges Blatt**, „Rechnung / Invoice“. Deutsch stünde immer
  da, und eine Übersetzung nach §87 Abs. 2 AO entfiele – aber nur für die
  Teile, die vom Programm kommen. Die Positionen, also Art und Umfang der
  Leistung (§14 Abs. 4 Nr. 5 UStG), schreibt der Rechnungssteller in einer
  Sprache. Dafür wird jede Beschriftung doppelt so lang, und jedes Design
  müsste das eigens unterbringen. Als späterer eigener Fall bleibt es möglich.
- **Die Sprache in den Stammdaten.** Dann spräche jede Rechnung eines
  Rechnungsstellers dieselbe Sprache – das Gegenteil des Anlasses.
- **Die Sprache als Design**, `helsinki-en` neben `helsinki`. Das verdoppelte
  das Satzbild je Sprache, und es vermischte zwei Achsen: Das Design wählt der
  Rechnungssteller, die Sprache folgt dem Kunden.
- **Die Beschriftungen aus Kotlin**, als Platzhalter. Das widerspricht
  ADR-0017, und der Renderer entschiede über den Wortlaut eines Designs.
- **Eine Bedingung in der Vorlage**, `\ifstrequal{<<SPRACHE>>}{en-GB}{…}{…}`.
  Das ist eine Bedingung über `\ifblank` hinaus, die ADR-0017 ausschließt.
- **`ResourceBundle` für die Sätze.** Ein fehlender Schlüssel fiele erst zur
  Laufzeit auf, und die Sätze entstehen aus Latex-Fragmenten, deren
  Reihenfolge je Sprache verschieden ist – eine Datei mit Platzhaltern
  verlegte die Zusammensetzung nur.
