# Rechnungsgenerator

Kotlin/Gradle-Lernprojekt: erzeugt aus einer YAML-Rechnungsdatei plus
YAML-Stammdaten-Konfiguration eine deutsche Rechnung als valides
PDF/A-1b-Dokument. Satz über LuaLaTeX (Schrift: TeX Gyre Heros, passend zum
CV/Web-Auftritt des Autors), Validierung über veraPDF.

## Befehle

- `./gradlew build` – kompiliert, testet, lintet
- `./gradlew test` – nur Tests
- `./gradlew ktlintCheck` / `ktlintFormat` – Linting prüfen / autokorrigieren
- `./gradlew installDist` – ausführbares Kommando nach
  `build/install/rechnungsgenerator/bin/` bauen

## Architektur-Entscheidungen

- **Eingabe**: YAML (kaml/kotlinx.serialization). Zwei Dateien: Stammdaten-Config
  (auf jeder Rechnung gleich) + rechnungsspezifische Datei.
- **Eingaben lesen**: `YamlLoader` übersetzt die Fehler des Lesens selbst und
  nennt dabei immer die Datei – unlesbar, kein gültiges YAML, ein Wert, der
  seine Prüfung nicht besteht. Der Dateiname gehört dorthin, wo gelesen wird:
  kaml nennt nur den falschen Schlüssel, und `erzeuge` liest zwei Dateien.
  Vorher übersetzte jedes Kommando für sich, und die Fassungen liefen
  auseinander: `erzeuge` meldete einen Lesefehler als "Die Datei konnte nicht
  geschrieben werden", weil sein IOException-Zweig dem Ziel galt und das Lesen
  mitfing; `abschlaege` fing ihn gar nicht, und weil `Main` nur `CliktError`
  fängt, endete der Aufruf im Stacktrace.
- **Geldwerte**: ausschließlich `BigDecimal`, niemals Float/Double. Zwei
  gleichrangige Value Objects, die die Grenze der EN 16931 abbilden:
  - `Price` – exakt, rundet nie. Für Einzelpreise (BT-146), wo die Norm beliebig
    viele Nachkommastellen erlaubt.
  - `Money` – auf zwei Nachkommastellen normalisiert. Für alle Betragsfelder,
    die die Regeln BR-DEC-* auf zwei Stellen festlegen (BT-131 und Folgende).

  Der einzige Übergang ist `Price.roundToAmount()`; der Compiler erzwingt ihn.
  Bewusst **keine** Vererbung zwischen beiden – eine Unterklasse würde die
  Symmetrie von `equals` brechen. Ebenso bewusst fehlen `Money × Menge`
  (Schleichweg an `Price` vorbei) und `Money × Money` (fachlich sinnlos).

  Berechnungen (Summen, USt., Fälligkeit) im Kotlin-Code, nie im Template.
- **Ausgabe**: PDF/A-1b via LuaLaTeX + `pdfx`-Paket. Externe Tools (lualatex,
  verapdf) werden im PATH erwartet; `LuaLatexTypesetter.checkAvailable()` prüft
  das beim Start und nennt die fehlenden mit Installationshinweis.
- **Naht zu den Werkzeugen**: `Typesetter` – "aus Quelltext wird ein geprüftes
  PDF". Dahinter im Betrieb LuaLaTeX und veraPDF, im Test ein vorbereitetes
  Ergebnis. Zwei Adapter, also eine echte Naht: Erst dadurch sind Kopieren,
  Ausgabereihenfolge, Exitcode und das Aufräumen des Arbeitsverzeichnisses
  prüfbar, ohne dass ein fremdes Programm läuft. Der Integrationstest mit den
  echten Werkzeugen bleibt daneben bestehen – ohne gültiges PDF/A kein grüner
  Build.
- **Satz nur in der Vorlage**: Abstände, Schriftgrad, Farbe und Linien stehen in
  `invoice.tex`. Der Renderer liefert Inhalt und Makroaufrufe und entscheidet
  weiter, *ob* etwas dasteht. Die Millimeter sind benannte Längen, die
  Zeilenwechsel mit Abstand Makros (`\positionstrenner`), ebenso das
  Zusammenhalten einer Angabe (`\zusammen`): Der Renderer schreibt weder einen
  Längennamen noch ein Makro von LaTeX selbst (#76). Auch kein `\\` (#147):
  Die Zeilen eines Blocks verbindet `\zeilenwechsel`, die Abschlusszeile eines
  Betragsblocks beendet die Zeile davor selbst, und die Person im Briefkopf
  setzt `\kopfperson`. Vorher schrieb der Renderer das rohe `\\`, und eine
  Zeile, die mit `[` begann, las LaTeX als Abstandsangabe und brach ab – das
  `{}`, das `Latex.macro` hinter ein Makro ohne Argument setzt, verhindert das.
  Beim Setzen prüft `checkLineBreaks`, dass kein Wert ein `\\` enthält.
  **Nicht** umgestellt wurden die Trenner ` · ` im Fuß und in den
  Zusatzzeilen: Sie sind Satzbild und werden mit dem zweiten Design fällig,
  wenn feststeht, welche dort anders sind. Die Tabellenzeilen sind
  Makros (`\positionszeile` mit
  fünf Argumenten) – damit steht die Spaltenzahl nur an einer Stelle, statt in
  Vorlage und Kotlin getrennt getroffen zu werden. Welche Makros der Renderer
  aufruft, zählt das Enum `TemplateMacro` auf; beim Setzen wird geprüft, dass die
  Vorlage jedes davon mit derselben Stelligkeit kennt. Vorher fiel ein vertippter
  Name erst im LuaLaTeX-Lauf auf, also im Integrationstest. **Nicht** eingeebnet
  wurde dabei der Unterschied im Einzug zwischen der gedämpften Zweitzeile einer
  Position und der eines Abschlags: Das ist Satzbild und gehört vor Augen, nicht
  in einen Umbau.
  Auch der Satzabstand ist Satzbild (#143): Im ganzen Dokument gilt
  `\frenchspacing`, weil TeX sonst jeden Punkt nach Kleinbuchstabe oder Ziffer
  für ein Satzende hält ("Dr. Erika") und sich in Eingabedaten nicht erkennen
  lässt, wo ein Satz wirklich endet. Den breiten Abstand nach `.` `?` `!` setzt
  allein `\hinweisabsatz`, der die eigenen Sätze des Programms trägt –
  Eingabedaten gehören deshalb nicht hinein. Ein Datum schreibt
  `GermanFormat.date` mit `~` hinter dem Tag, damit es auch dort keine Lücke
  bekommt; in den XMP-Metadaten wäre `~` aber ein Zeichen, dort gilt
  `dateInMetadata`. Erwogen und verworfen: ein zweiter Weg in `escapeLatex`,
  der in Daten jeden Punkt entschärft – 74 Aufrufe hätten ihre Bedeutung
  geändert, für eine Entscheidung, die in die Vorlage gehört.
- **Externe Prozesse**: Ausgabe wird in eine Datei umgeleitet, nicht in einen
  Stream gelesen – sonst blockiert das Lesen bei einem hängenden Prozess, und
  die Zeitgrenze käme nie zum Zug. LuaLaTeX läuft, solange die Log-Datei einen
  weiteren Lauf verlangt, höchstens dreimal – wer nach drei Läufen immer noch
  einen vierten fordert, konvergiert nicht. Bei den Rechnungen dieses Projekts
  ist es einer: Sie haben weder Querverweise noch Lesezeichen, und nachgemessen
  mit TeX Live 2026 sind die PDFs nach einem und nach zwei Läufen im Satzbild
  gleich. Dieselben Quellen ergeben dasselbe PDF, Byte für Byte: Das
  Erstellungsdatum ist das Rechnungsdatum (`SOURCE_DATE_EPOCH`, geschrieben in
  UTC), und die Trailer-ID bildet `LatexCompiler` aus der Prüfsumme von `.tex`
  und `.xmpdata`. Von sich aus nähme LuaTeX dafür Uhr und Pfad, und jeder Lauf
  setzt in einem anderen Temp-Verzeichnis.
- **CLI**: Clikt, und zwar `clikt-core` ohne Mordant – die Vollvariante zieht
  JNA nach und schreibt auf JDK 24+ bei jedem Start vier Warnzeilen auf stderr.
  `main()` von Clikt beendet den Prozess mit Code 0 auch im Fehlerfall, deshalb
  `parse()` mit eigenem `exitProcess(e.statusCode)`. Das Programm ist ein
  Elternkommando mit Unterkommandos (`erzeuge`), weil kein Aufruf je nach
  Schalter mal ein PDF und mal YAML ausgeben soll (ADR-0003). Fehlt das
  Unterkommando, wirft Clikt die Hilfe ebenfalls mit Code 0 – deshalb wirft
  `RechnungsgeneratorCommand` sie selbst, mit 1. Der Kanal folgt dem Exitcode:
  Was mit Code ungleich 0 endet, steht auf stderr, stdout trägt nur das
  Ergebnis. Weil `clikt-core` den Parameter `err` ignoriert und alles auf
  stdout schreibt, setzt `execute()` dafür einen eigenen Ausgeber.
  Die Fachfehler – `InputFileException`, `ToolchainException` und die
  `RejectedException` einer Ablehnung – übersetzt ebenfalls `execute()`, einmal
  für alle Kommandos; die Kommandos werfen sie nur durch (#119). Zu Text wird
  die Abbruchliste allein in `RejectedException`. Vorher tat jedes Kommando das
  selbst, und in #52 liefen die Fassungen auseinander.
  `erzeuge --oeffnen` öffnet das fertige PDF über `PdfViewer`, eine eigene
  Naht im Paket `generate` und bewusst nicht in `toolchain` (#144): Dort
  liegen die Werkzeuge, ohne die kein PDF entsteht, und `checkAvailable()`
  prüft sie beim Start. Fehlt der Viewer, entsteht die Rechnung trotzdem – ein
  Fehlschlag ist deshalb ein Hinweis mit Exitcode 0, kein Abbruch. Gestartet
  wird nicht über `ProcessRunner`, dessen Vertrag bis zum Ende wartet und hart
  abräumt; das risse unter Linux den Viewer mit.
- **Positionstexte**: drei Felder statt einem, entlang der Aufteilung von
  EN 16931 – `bezeichnung` (BT-153, Pflicht), `beschreibung` (BT-154) und
  `hinweis` (BT-127). Ein einziges Freitextfeld hätte fürs PDF gereicht, würde
  beim ZUGFeRD-Export aber zum Raten zwingen: Ein Programm, das
  "IT-Beratung, Arbeitspaket X" an einem Komma zerteilt, liegt falsch, sobald
  die Bezeichnung selbst ein Komma enthält.
- **Geprüfte Werte statt Freitext**: `einheit` ist ein Enum mit den Codes aus
  UN/ECE Rec. 20 (BT-130 verlangt einen Code, Freitext ist unzulässig – Regeln
  BR-23/BR-CL-23). IBAN, BIC, USt-IdNr. und Steuernummer sind Value Objects mit
  Syntaxprüfung; IBAN über die Prüfsumme mod 97, deutsche USt-IdNr. über die
  Prüfziffer des BZSt. Intern normalisiert (ohne Leerzeichen), fürs PDF über
  `formatted()` wieder gruppiert. Grund: Diese Werte tippt man einmal und
  schaut sie nie wieder an; ein Zahlendreher in der IBAN fällt sonst erst auf,
  wenn das Geld nicht ankommt.
- **Rechnungsnummer**: Geprüft wird nur ihre Form – welche gilt, steht als
  regulärer Ausdruck in den Stammdaten (`rechnungsnummer-muster`), und ohne
  Eintrag wird gar nicht geprüft (ADR-0005). `R26-009` ist ein Format unter
  vielen; UStAE 14.5 Abs. 10 erlaubt Ziffern und Buchstaben und verlangt keine
  lückenlose Abfolge. Die Einmaligkeit (§14 Abs. 4 Satz 1 Nr. 4 UStG) prüft der
  Generator nicht – beim Erzeugen sieht er eine einzige Datei (ADR-0001). Die
  Prüfung sitzt deshalb im `InvoiceCalculator` und nicht im Serialisierer: Ob
  eine Nummer gültig ist, hängt an zwei Dateien, und der sieht beim Einlesen
  immer nur eine. Leere Nummer und Leerraum am Rand brechen unabhängig vom
  Muster ab, weil aus der Nummer auch der Dateiname entsteht. Die Nummern der
  abgesetzten Abschläge bleiben ungeprüft: Sie stammen aus früheren Rechnungen,
  womöglich aus der Zeit vor einem Wechsel des Musters.
- **Steuerkategorie**: `TaxCategory` (BT-151, Codeliste UNCL 5305) pro Position,
  Vorgabe S. Der Satz allein genügt nicht – ohne Kategorie lässt sich "0 %"
  nicht von "steuerbefreit" und nicht von "Steuerschuldnerschaft des
  Leistungsempfängers" unterscheiden. Die Folgeregeln (Satz 0 bei steuerfreien
  Kategorien, Befreiungsgrund BT-120, USt-IdNr. des Kunden BT-48 bei AE und K)
  prüft der `InvoiceCalculator`, nicht erst ein späterer Export: Eine
  widersprüchliche Rechnung soll nicht als PDF hinausgehen. Gruppiert wird nach
  Kategorie **und** Satz, weil zwei Positionen mit 0 % steuerlich Verschiedenes
  bedeuten können.
- **Bestätigungsabfrage**: Vor einer Rechnung ohne Umsatzsteuer muss die
  USt-IdNr. des Kunden geprüft und die Prüfung dokumentiert sein
  (`kunde: ust-id-geprueft`). Der Calculator bricht sonst ab. Verschieden
  streng, weil die Rechtslage es ist (Stand August 2026): bei K immer (§6a
  UStG), bei AE nur grenzüberschreitend in die EU (seit 1/2024 keine
  Voraussetzung des §13b mehr, aber Vertrauensschutz), bei Drittländern und im
  Inland gar nicht – VIES deckt nur Mitgliedstaaten ab, und das BZSt bestätigt
  *ausländische* Nummern, die eines deutschen Kunden also gar nicht. Die Abfrage selbst macht das Programm bewusst nicht: Sie
  setzt eine BZSt-Registrierung voraus und muss ein aufbewahrbares Protokoll
  erzeugen. Die Liste der EU-Mitgliedstaaten trägt ihr Prüfdatum als Wert
  (`EuMemberStates.AS_OF`), nicht als Kommentar: Sie ist heute richtig und wird
  irgendwann still falsch. Zwei Netze, absichtlich versetzt:
  - Ein **Test** wird rot, sobald das Prüfdatum sechs Monate alt ist. Er
    scheitert also irgendwann ohne Codeänderung – das ist der Zweck, nicht ein
    Defekt. Die Fehlermeldung sagt, was zu tun ist. Der Preis: Jeder ältere
    Stand scheitert an ihm, bei `git bisect` ebenso wie beim Nachbauen eines
    alten PDF. Dafür `EU_LISTE_ALTERSPRUEFUNG=aus ./gradlew build` – der Test
    gilt dann als übersprungen, nicht als bestanden (#79). Stände vor #79
    kennen den Schalter noch nicht.
  - Ein **Laufzeit-Hinweis** meldet sich nach zwölf Monaten, aber nur bei
    Auslandsrechnungen. Er fängt den ab, der nur das gebaute Programm benutzt,
    ohne je `./gradlew build` zu laufen. Bei Inlandsrechnungen schweigt er,
    weil die Liste dort nicht befragt wird und Dauerhinweise dazu führen, dass
    auch die wichtigen überlesen werden.
- **Rechnungsart**: `InvoiceType` (BT-3, Codeliste UNTDID 1001) mit Vorgabe
  `rechnung`, geschlossene Menge wie `Unit` und `TaxCategory`. Sie ist keine
  Beschriftung: Wer einen Abschlag wie eine gewöhnliche Rechnung abrechnet und
  später noch einmal die Gesamtleistung, weist die Umsatzsteuer zweimal aus und
  schuldet sie nach §14c Abs. 1 UStG auch zweimal. Die YAML-Werte heißen wie die
  Dokumente in `CONTEXT.md` (`abschlagsrechnung`, nicht `anzahlung`), nicht wie
  die Codeliste. Die Schlussrechnung trägt denselben Code 380 wie die
  gewöhnliche Rechnung – sie *ist* die Rechnung über die Gesamtleistung,
  kenntlich an der Absetzung, nicht am Dokumenttyp. Aus der einen Tatsache
  `servicePeriodIsForecast` folgen beide Zeitraum-Beschriftungen und die
  Schreibweise des Datums (Kalendermonate, UStAE 14.8 Abs. 1 und 4): Auf einer
  Abschlagsrechnung ist der Zeitraum eine Prognose, und auf den Tag genau
  genannt behauptete er eine Genauigkeit, die er nicht hat.

  Diese Eigenschaft, `chargesForService` und `mandatoryNote` haben **keine**
  Vorgabewerte: Jeder Eintrag nennt sie ausdrücklich, damit eine neue Art
  nicht still die Antworten der gewöhnlichen Rechnung erbt – samt
  Zahlungsziel und Bankverbindung. Das hält die Zusage aus ADR-0008 an der
  Stelle, an der die Art entsteht. Das flache Modell hinter
  `AcceptedInvoice.calculated` bleibt dafür bewusst lesbar (ADR-0009).

  Dazu gehört ein Sicherungsnetz: `src/test/resources/rechnung-erwartet.tex` ist
  das erwartete LaTeX der Beispielrechnung, zeichengleich verglichen. Der Satz
  einer gewöhnlichen Rechnung soll sich nicht nebenbei ändern, während die
  anderen Arten dazukommen. Wird der Test rot, ist das eine Frage und keine
  Aufgabe: erst nachsehen, ob die Änderung gewollt ist, dann die Datei neu
  erzeugen – und das erzeugte PDF gegen das vorherige prüfen. Gleiche Prüfsumme
  des PDF heißt: nichts geändert. Weicht sie ab, entscheidet das Bild
  (`pdftoppm` und dessen Prüfsumme), denn eine geänderte Kommentar- oder
  Leerzeile im `.tex` ändert die Trailer-ID, aber nicht das Satzbild.
- **Storno**: Eine falsche Rechnung wird nicht überschrieben, sondern
  aufgehoben – `art: stornorechnung` mit eigener Nummer, dem Bezug auf die
  Ursprungsrechnung unter `storniert` (EN 16931 BG-3, Pflicht) und den
  Positionen der Ursprungsrechnung mit negativer Menge (ADR-0006). Code 384 und
  nicht 381: Die KoSIT führt 381 für die kaufmännische Gutschrift **ohne**
  Bezug, 384 für Korrektur und Storno mit Bezug, 389 für die Gutschrift im
  Sinne des §14 Abs. 2 Satz 2 UStG. Negativ wird die Menge und nie der
  Einzelpreis, den BR-27 verbietet – genau so war das Projekt schon gebaut, die
  seit #47 offene Frage nach der negativen Menge ist damit beantwortet. Das
  Vorzeichen des Gesamtbetrags gehört seither zur Rechnungsart: im Storno
  Pflicht, überall sonst verboten. Fälligkeit und Bankverbindung entfallen, weil
  ein Storno kein Geld fordert; dafür wurde der Bankblock zum Makro der Vorlage,
  sonst bliebe die Überschrift ohne Konto stehen. Die Ursprungsrechnung nennt
  der Pflichtsatz mit Nummer und Datum, über die Platzhalter
  `<<URSPRUNGSNUMMER>>` und `<<URSPRUNGSDATUM>>`, die `InvoiceVariableResolver`
  beim Setzen auflöst; eine eigene Zeile im Kopf gibt es nicht mehr (ADR-0010).
  Im Modell bleibt der Bezug Struktur. Das Wort **Gutschrift** fällt
  nicht: Umsatzsteuerlich ist das eine Rechnung des Leistungsempfängers. Nur
  ganz, nicht teilweise. Eine Teilberichtigung kennt das Projekt nicht: Falsche
  Angaben, falsche Beträge und zu viel ausgewiesene Steuer laufen über Storno
  und neue Rechnung. Offen wäre allein die nachträgliche Entgeltminderung
  (§17 UStG), ein eigener Vorgang. Erwogen und mangels Anlass geschlossen in
  #64. Über die eigene Datei hinaus wirkt ein Storno nur dort, wo jemand das
  Ablageverzeichnis liest, also im Skript `offene-abschlaege.py` (#66): Eine
  stornierte Abschlagsrechnung ist nicht offen, und die Abschläge einer
  stornierten Schlussrechnung sind es wieder. Beide Richtungen kosten Geld –
  die eine setzt Zurückgezahltes ab, die andere lässt zweimal zahlen –, deshalb
  bleibt keine still: Jede vermerkt das Skript auf stderr, auch den Fall, der
  weder offen noch in Ordnung ist (ein stornierter Abschlag, den eine gültige
  Schlussrechnung absetzt).
- **Absetzung**: Die Schlussrechnung trägt die vereinnahmten Abschläge unter
  `abschlaege` ausgeschrieben, mit `steueranteile` je Kombination aus Kategorie
  und Satz. Nachgeschlagen wird nichts (ADR-0001): Zwischen Abschlag und
  Schlussrechnung liegen Monate, in denen Dateien in Jahresordner wandern; eine
  Rechnung, die beim Erzeugen fremde Dateien liest, ergibt nicht zweimal
  dasselbe PDF. Das Abtippen übernimmt `rechnungsgenerator abschlaege`
  (ADR-0003): Es rechnet die Abschlagsrechnungen mit demselben Calculator wie
  beim PDF und gibt den Block auf stdout aus – alles oder nichts, ohne Datum
  und Pfad, ein fehlendes `vereinnahmt-am` als leeres Feld. Das "alles oder
  nichts" hält der Typ: `prepaymentsOf` liefert ein versiegeltes
  `CollectedPrepayments`, vollständig oder abgelehnt, und die Einträge tragen
  den Namen ihrer Datei selbst (#118). Vorher war es eine Liste neben den
  Befunden, "nur vollständig, wenn kein Abbruch" laut Kommentar, und das
  Kommando filterte und paarte per `zip` – dieselbe Sorte Disziplin, die
  ADR-0007 beim Renderer abgeschafft hat. Eingefügt wird von
  Hand, und danach kann der Block geändert oder ganz von Hand geschrieben sein.
  Deshalb bleiben die Abbruchregeln im `InvoiceCalculator` – darunter die
  Prüfsumme `netto × satz = ust` und die Forderung, dass Kategorie und Satz in
  der Schlussrechnung überhaupt vorkommen. Dieselbe Nummer zweimal fängt er
  aber nur *innerhalb* einer Datei; dass zwei Schlussrechnungen denselben
  Abschlag absetzen, sieht allein `offene-abschlaege.py` (#67), weil nur es das
  Verzeichnis liest. Abgesetzt wird nur, was
  **vereinnahmt** ist (§14 Abs. 5 Satz 2 UStG); ein unbezahlter Abschlag ist
  eine offene Forderung.

  Im PDF steht die Absetzung unter dem Gesamtbetrag und nie als Position
  (ADR-0002): Eine negative Position senkte die Bemessungsgrundlage und damit
  die ausgewiesene Umsatzsteuer, die aber in voller Höhe geschuldet wird.
  Restbetrag null bekommt einen Satz statt einer Zahl, und dann entfallen auch
  Zahlungsziel und Bankverbindung – sonst stünden zwei verschiedene Aussagen auf
  einem Blatt. Beide folgen aus `requestsPayment`, das neben der Rechnungsart
  den Restbetrag kennt: Eine voll abgesetzte Schlussrechnung rechnet ab, ohne
  Geld zu fordern (#94).
  `prepaymentTotal`, `remainingAmount` und das Zahlungsziel sind abgeleitet,
  nicht gespeichert: `calculate` liefert eine `CalculatedInvoice`, die Rechnung,
  Stammdaten und Ergebnis zusammen trägt – ein Paar aus einer Rechnung und den
  Summen einer anderen ist damit nicht baubar.
- **ZUGFeRD-Vorbereitung**: Datenmodell enthält von Anfang an alle
  EN-16931-Pflichtfelder (Leistungsdatum, Steuerkategorie, Kennungen), damit
  ZUGFeRD (PDF/A-3 + XML) später ein zusätzlicher Ausgabemodus wird, kein Umbau.
  Bis dahin bewusst nur PDF/A-1b (Archivierungsanforderungen der E-Rechnung
  noch offen).
- **Befunde statt Abbruch beim ersten Fehler**: Die Prüfung wirft nicht,
  sondern liefert eine Liste von Befunden. Ein Befund trägt eine
  Kennung, eine Schwere (Abbruch oder Hinweis), den Gegenstand (Rechnung,
  Position, Abschlag) und die Meldung; den Gegenstand nennt die Meldung nicht
  selbst, das tut die Ausgabe. Abgebrochen wird im Kommando – und dann stehen
  alle Gründe auf einmal da, statt dass fünf Fehler fünf Läufe brauchen.
  `calculate` rechnet nur. Geprüft wird in Regelgruppen im Paket `rules`, je
  eine Datei neben ihrem gleichnamigen Test, jede gegen das fertig gerechnete
  Ergebnis (#71). Vorher standen Regeln zwischen den Rechenschritten, und weil
  zwei die Summen brauchten, entstand das Ergebnis zweimal. Den Prüflauf führt
  `AcceptedInvoice.check`, nicht die `CalculatedInvoice`: Befunde sind ein
  Urteil über die Rechnung und kein abgeleiteter Wert wie `dueDate`. Als `lazy`
  auf ihr hatte die Prüfung keinen Besitzer – die Rechnungsart wurde dort
  gemerkt, und dass eine Ablehnung ihre Gründe nannte, hing an einem Eintrag in
  der Liste der Regelgruppen (#105). Setzen lässt sich nur eine
  `AcceptedInvoice`, und die gibt es nur ohne Abbruch: Renderer und das
  Zusammentragen der Abschläge (`prepaymentsOf`) verlangen sie, damit hält der
  Compiler die Zusage aus ADR-0004 und nicht mehr die Disziplin der Kommandos
  (ADR-0007, #70). Sie trägt die Rechnungsart als Typ (`InvoiceKind`): Nach
  Bezug, Vereinnahmung und Abschlägen fragen Renderer und `prepaymentsOf`
  dort, nicht im flachen Modell.
  Gebaut wird er in `InvoiceKindRules`, zusammen mit den sechs Regeln, ohne die
  er nicht baubar ist (ADR-0008).
  Die Regelgruppe einer Rechnungsart (`PrepaymentRules`, der Bezug in
  `CancellationRules`, `FinalInvoiceRules`) prüft nur auf dieser Art – als
  `when` über alle Arten ohne `else`, damit eine neue Art dort
  entscheiden muss (#121). Steht
  ihr Feld auf einer anderen, bleibt allein der Befund aus `InvoiceKindRules` –
  sonst stünden Sätze über "diese Schlussrechnung" unter einer gewöhnlichen
  Rechnung (#97). Der Preis: Wer nur `art` falsch gesetzt hat, sieht Fehler
  in den Angaben der gemeinten Art erst im zweiten Lauf.
  Ebenso fragt der Renderer die `InvoiceKind`: Vereinnahmung und Absetzung je
  in einem `when` ohne `else`, zwei Stellen für zwei Entscheidungen (#128).
  Nur `prepaymentOf` behält sein `else` – dort ist genau eine Art richtig.
  Den Bezug fragt seit ADR-0010 `InvoiceVariableResolver` mit `as?`, und das
  wirkt wie ein `else`: Eine neue Art mit eigenem Bezug wird dort nicht zur
  Entscheidung gezwungen. Ein Pflichtsatz, der eine Variable auf der falschen
  Art nennt, bricht aber beim Setzen ab, also in der Vergleichsdatei.
  Die Kennungen sind deutsch benannt wie die Regeln und **nicht** nummeriert:
  Die Nummern standen nur in Kommentaren und Testnamen und liefen dort
  auseinander. Tests prüfen die Kennung statt des Wortlauts und brauchen dafür
  keine sonst makellose Rechnung mehr; wo früher "wirft nicht" die stille
  Zusage war, steht jetzt `aborts().shouldBeEmpty()`. Der Umbau selbst hat
  prompt eine Regel entlarvt, die sich auf einer Rechnung ganz ohne Abschläge
  meldete ("mehr Abschläge abgesetzt (0.00)") – solange der erste Wurf gewann,
  sah das niemand.
- **Validierung ist Teil der Tests**: erzeugte PDFs werden per veraPDF geprüft;
  ohne valides PDF/A kein grüner Build.
- **Paketstruktur**: Zwei vertikale Pakete, eines je Kommando, mit allem, was
  nur dieses Kommando braucht: `generate` mit dem LaTeX-Satz
  (`generate.render`) und der Naht zu den Werkzeugen (`generate.toolchain`),
  `prepayments` mit dem Zusammentragen der Abschläge und dem YAML-Block.
  Darunter der gemeinsame Kern, in dem jede Kante nach unten zeigt: `rules`
  (Befunde, Regelgruppen, `AcceptedInvoice.check`) → `calculation`
  (`InvoiceCalculator`, `CalculatedInvoice`, `InvoiceKind`) → `model` (samt
  `YamlLoader`) → `values`. In der Wurzel steht nur `Main.kt`. Vorher war die
  Wurzel Kern und CLI zugleich, und daraus entstanden die beiden einzigen
  Zyklen des Projekts: Wurzel ↔ `rules` und Wurzel ↔ `render` (#107). Der
  Schnitt trennt den Rand, nicht die Mitte – was beide Kommandos brauchen, ist
  der größere Teil. Die Grenze zwischen `rules` und `calculation` ist die aus
  ADR-0004: `calculate` rechnet nur, `check` prüft und urteilt. Deshalb liegt
  die `AcceptedInvoice` bei den Regeln, und der Renderer importiert aus
  `rules`. Kotlin hält keine Paketgrenze (`internal` gilt pro Modul, nicht pro
  Paket); `PackageStructureTest` hält sie stattdessen (#112): eine Tabelle, wer
  woher importieren darf, gegen die Importe aller Quellen unter `src/main`,
  gelesen mit Konsist. Ein neues Paket muss dort eingetragen werden. Derselbe
  Test verbietet Importe aus dem eigenen Paket – ktlint lässt die absichtlich
  stehen, und beim Umzug in #111 blieben 99 davon liegen. Nicht Konsists
  `assertArchitecture`: Dessen Schichten schließen Unterpakete immer ein, und
  dann fällt ein Import von `generate.render` nach `generate` nicht auf.
  Die Testpakete spiegeln die Hauptpakete, geteilte Helfer liegen im Testpaket
  dessen, was sie bauen oder prüfen (`model/TestFixtures.kt`,
  `rules/CheckHelpers.kt`); ein Test darf aber jedes Paket sehen. Kein ADR:
  Die Alternative war "so lassen", der Grund sind die Zyklen. Kommt ZUGFeRD als
  weiterer Ausgabemodus, ist das der Moment für eines.

## Konventionen

- Bezeichner Englisch, Kommentare Deutsch. Kommentare sind lernorientiert
  (der Autor lernt Kotlin), aber knapp: Kotlin-Idiome kurz erklären, wo sie
  zum ersten Mal auftauchen – nicht jede Zeile kommentieren.

  **Ausnahme: die Fachbegriffe aus `CONTEXT.md`.** `abschlag`, `anteil`,
  `restbetrag`, `vereinnahmt`, `zeitraum` heißen auch in lokalen Variablen und
  Parametern so wie im Glossar. Eine Übersetzung (`downPayment`, `remainder`)
  wäre ein zweites Vokabular neben dem, das die Domäne schon hat, und zwänge
  beim Lesen zum Hin- und Herübersetzen – das Glossar gibt es genau deshalb.
  Die Ausnahme gilt eng: Alles ohne Fachbezug bleibt englisch, also `expected`
  statt `erwartet`, `rows` statt `zeilen`, `message` statt `meldung`.

  Ein Wort im Glossar genügt dafür nicht – es muss auch **ohne eingebürgerte
  englische Entsprechung** sein. `abschlag` hat keine: Wer `downPayment`
  schreibt, hat nicht übersetzt, sondern eine zweite Deutung gewählt.
  `Rechnungsnummer` dagegen heißt `invoiceNumber`, so wie EN 16931 sie führt
  (BT-1), und so steht sie im Code seit jeher. Das Glossar zieht die Grenze der
  Begriffe, nicht die der Bezeichner: Es sagt, dass es *Rechnungsnummer* heißt
  und nicht *Belegnummer* – nicht, dass der Bezeichner deutsch sein muss.
- Wo ein Kommentar, ein Testname oder ein Dokument eine Regel nennt, nennt es
  ihre Kennung aus `Rule`, etwa `ABSCHLAG_OHNE_VEREINNAHMUNG`. Die Kennung
  findet `grep`, und sie ändert sich nur mit der Regel selbst (ADR-0004).
- Keine echten Firmen-/Kundendaten im Repo (Projekt wird ggf. öffentlich).
  Testdaten: fiktive Firma, Test-IBAN (DE89 3704 0044 …), erfundene USt-ID.
  Das gilt auch für **Beispiele in Dokumentation und Skills** – dort rutschen
  echte Kunden- und Projektnamen am ehesten hinein, weil sie beim Schreiben
  gerade zur Hand sind. Die echten Stammdaten liegen außerhalb des Repos, der
  Pfad dorthin kommt aus `RECHNUNGEN_DIR` in `.claude/settings.local.json`.

  Ebenso in **GitHub-Issues** – Titel, Rumpf, Kommentare, angehängte YAML- oder
  Log-Ausschnitte. Ein Ticket zu Abschlagsrechnungen beschreibt den Fall mit
  "Kunde A" und erfundenen Beträgen, nicht mit dem Vorgang, der ihn ausgelöst
  hat. Issues wiegen hier schwerer als Code: Sie werden mit dem Repo öffentlich,
  aber ein `--amend` gibt es für sie nicht. Editieren löscht nur die sichtbare
  Fassung; die Versionshistorie eines Issues bleibt für jeden lesbar, der sie
  aufklappt, und die Benachrichtigungsmail mit dem Originaltext ist ohnehin
  schon draußen. Wirklich weg ist ein Text erst, wenn das Issue gelöscht wird.
- Linting (ktlint) muss vor jedem Commit grün sein. Ein Hook in
  `.claude/settings.json` ruft nach jeder Änderung an einer `.kt`/`.kts`-Datei
  `./gradlew ktlintFormat` auf, damit das nicht von Hand nachgezogen werden muss.
  Kosten: rund 1 s pro Bearbeitung bei warmem Gradle-Daemon.

## Umgebung

- macOS, TeX Live lokal (`lualatex` in /Library/TeX/texbin), veraPDF via
  Homebrew (`verapdf`), JDK 25 lokal; Build zielt per Toolchain auf JDK 21.
- `pdftotext` und `pdftoppm` aus poppler (Homebrew `poppler`). `pdftotext`
  braucht seit #81 auch der Build: `ToolchainIntegrationTest` liest damit
  zurück, ob Text aus den Eingabedaten unverändert im PDF steht.
- Die Skripte des `rechnung`-Skills laufen mit `python3`; PyYAML
  (`pip3 install pyyaml`) brauchen beide Skripte
  immer – `naechste-nummer.py` seit #75, weil es vergebene Nummern auch aus den
  YAML-Dateien liest und nicht nur aus den Dateinamen. Seit #61 hängt auch der Build daran:
  `NaechsteNummerSkriptTest` ruft das Skript wirklich auf, statt seine Regel
  nachzubauen.

## Agent skills

### Issue tracker

Issues und Specs liegen als GitHub-Issues in
`Fluch-IT-Consulting/rechnungsgenerator`, bedient über die `gh`-CLI.
Siehe `docs/agents/issue-tracker.md`.

### Triage labels

Die fünf Standardrollen, Label gleich Rollenname.
Siehe `docs/agents/triage-labels.md`.

### Domain docs

Single-Context: ein `CONTEXT.md` und `docs/adr/` in der Wurzel.
Siehe `docs/agents/domain.md`.

Daneben `docs/notizen/`: durchdachte, aber **nicht entschiedene** Fragen, je eine
Datei mit Datum im Namen. Ein ADR hält eine Entscheidung fest, eine Notiz das
Gespräch davor – wer eine der dort behandelten Fragen neu aufwirft, liest erst
nach, was schon einmal erwogen und verworfen wurde. Notizen binden aber nicht:
Sie sind kein Beschluss, und wer anders entscheidet, schuldet ihnen keine
Begründung.
