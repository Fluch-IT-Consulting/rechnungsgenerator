# Rechnungsgenerator

Kotlin/Gradle-Lernprojekt: erzeugt aus einer YAML-Rechnungsdatei plus
YAML-Stammdaten-Konfiguration eine deutsche Rechnung als valides
PDF/A-1b-Dokument, mit `--zugferd` als E-Rechnung (PDF/A-3u mit XML). Satz über
LuaLaTeX (Schrift: TeX Gyre Heros, passend zum CV/Web-Auftritt des Autors),
PDF/A-Prüfung über veraPDF.

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
  Übersetzte jedes Kommando für sich, liefen die Fassungen auseinander (#52).
- **Wurzel der Ablage** (ADR-0015): Ohne `-s` findet der Generator die
  Stammdaten selbst. Ein Verzeichnis mit dem Ordner `.rechnungsgenerator/` ist
  eine **Wurzel**, `stammdaten.yaml` liegt darin, und gesucht wird ab dem
  Verzeichnis der **Rechnungsdatei** den Pfad hinauf – nicht ab dem
  Arbeitsverzeichnis. Einen Rückfall auf `stammdaten.yaml` im
  Arbeitsverzeichnis gibt es nicht, und eine halb eingerichtete Wurzel bricht
  ab, statt weiter oben zu suchen. Die Suche ist `Root` in `model` neben dem
  `YamlLoader`; `abschlaege` sucht je Datei und bricht bei verschiedenen
  Wurzeln ab. Im Ordner liegt nur Handgeschriebenes: keinen Zähler und keinen
  Cache legt das Programm dort ab (ADR-0001). Der `rechnung`-Skill ruft deshalb
  ohne `-s` auf; `naechste-nummer.py` behält `--stammdaten` mit
  ausgeschriebenem Pfad.
- **Zieldatei** (ADR-0018): Ohne `-o` entsteht das PDF neben der
  Rechnungsdatei und heißt wie sie, nicht nach der Nummer.

- **`einrichten`** (#276) legt `.rechnungsgenerator/stammdaten.yaml` im
  Arbeitsverzeichnis an – ein Gerüst und kein `git init`: eine Datei, die
  danach der Benutzerin gehört, und das Programm schreibt nie wieder in den
  Ordner. Ohne Argument (ein `cd` kostet nichts, ein Argument ließe sich
  ergänzen, aber nicht zurücknehmen); Vorhandenes wird nie überschrieben, und
  es gibt kein `--force`. Auf stdout allein der Pfad, damit
  `$(rechnungsgenerator einrichten)` ihn liefert; Abbruch und Hinweis stehen
  auf stderr. Eine Wurzel oberhalb ist ein **Hinweis** und kein Abbruch –
  Verschachteln ist der vorgesehene Weg für einen zweiten Rechnungssteller,
  dieselbe Unterscheidung wie bei den Befunden. Das Arbeitsverzeichnis ist dem
  Kommando gereicht wie `GenerateCommand` der Viewer: Sonst schriebe der
  Testlauf in sein eigenes Projektverzeichnis. Gefragt wird `Root.above`, das
  – anders als `Root.of` – nicht wirft und einen halb eingerichteten Ordner
  übergeht: `of` liefert die Stammdaten, die aufs Blatt kommen, `above` einen
  Satz auf stderr.

  Die **Vorlage** ist eine Ressource, gelesen mit `checkNotNull` wie die
  Vorlagen der Designs. Pflichtfelder stehen leer da, Freiwilliges mit `#`
  direkt vor dem Schlüssel (`#telefon:`), erklärender Text mit `# `. Nicht
  alles mit Beispielwerten vorbelegt: Eine vergessene Zeile ergäbe sonst ein
  gültiges PDF mit der Bankverbindung einer erfundenen Firma; leer scheitert
  sie laut, mit Datei und Schlüssel. Weil die Vorlage eine zweite Abschrift des
  Schemas ist und sonst still veraltet, hält sie ein Netz: Die Schlüssel der
  **einkommentierten** Vorlage sind die `@SerialName` aus den Deskriptoren von
  `SellerConfig`, `Address` und `BankDetails`, in beide Richtungen; jede
  genannte Vorgabe (`land`, `zahlungsziel-tage`, `design`, `schlusssatz`) ist
  die echte, geprüft über zwei Läufe mit und ohne sie; und die unausgefüllte
  Vorlage wird abgelehnt. Ohne dieses Netz wird das Kommando nicht gebaut.
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

  Den **Betrag ohne Vorzeichen** trägt `Money` selbst (`abs()`), den
  **Vorzeichenübertrag** nicht (#247): "ohne Vorzeichen" ist ein Begriff jeder
  Zahl, "nimm das Vorzeichen von jenem" einer der Rechnung. Die eine solche
  Rechnung steht in `CalculatedLine.discountAmount`, mit ihrer Begründung aus
  ADR-0013 und gehalten von einem Test über Rechnung und Storno.

  Berechnungen (Summen, USt., Fälligkeit) im Kotlin-Code, nie im Template.
- **Ausgabe**: PDF/A-1b via LuaLaTeX + `pdfx`-Paket, mit `--zugferd` danach
  PDF/A-3u samt XML über Mustang (siehe ZUGFeRD). `lualatex` wird im PATH
  erwartet; `LuaLatexTypesetter.checkAvailable()` prüft das beim Start und
  nennt Fehlendes mit Installationshinweis.
- **PDF/A-Prüfung** (ADR-0014): zwei Prüfer hinter der Naht `PdfAValidator` –
  eingebaut (`org.verapdf:validation-model-jakarta` in derselben JVM, die Vorgabe) und
  extern (`erzeuge --externe-pdfa-pruefung` ruft das installierte Programm
  `verapdf`). Der eingebaute ist die Vorgabe wegen der Startzeit, der externe
  bleibt, weil er unabhängig urteilt; dass die beiden nicht unbemerkt
  auseinanderlaufen, hält ein Test über ein gültiges und ein nicht konformes
  PDF fest. veraPDF ist damit **keine Voraussetzung**: `checkAvailable()`
  verlangt LuaLaTeX immer, nach dem Prüfer fragt sein Adapter selbst – und zwar
  über dieselbe Naht, über die er ihn aufruft (`ProcessRunner.canRun`, #245).
  Deshalb ist `LuaLatexTypesetter` eine Klasse und das Kommando bekommt eine
  `TypesetterFactory`: Welcher Prüfer es ist, steht erst mit dem Schalter fest.
  Lizenz von veraPDF: gewählt ist **MPL-2.0** und nicht GPLv3+, die auf das
  Ganze durchschlüge. Das Profil – PDF/A-1b, mit E-Rechnung PDF/A-3u – folgt
  allein aus `PdfSources.pdfaProfile`, und der Vergleich der beiden Prüfer
  deckt beide.

- **Naht zu den Werkzeugen**: `Typesetter` – "aus Quelltext wird ein geprüftes
  PDF". Dahinter im Betrieb LuaLaTeX und veraPDF, im Test ein vorbereitetes
  Ergebnis. Zwei Adapter, also eine echte Naht: Erst dadurch sind Kopieren,
  Ausgabereihenfolge, Exitcode und das Aufräumen des Arbeitsverzeichnisses
  prüfbar, ohne dass ein fremdes Programm läuft. Der Integrationstest mit den
  echten Werkzeugen bleibt daneben bestehen – ohne gültiges PDF/A kein grüner
  Build.

  Durch die Naht geht ein Wert und keine Parameterliste: `PdfSources` mit
  Quelltext, Metadaten, eingebundenen Dateien und Erstellungsdatum (#202).
  "Dieselben Quellen ergeben dasselbe PDF" gilt damit wörtlich für einen Wert –
  was darin steht, bestimmt die Bytes, was nicht darin steht, nicht. Deshalb
  gehört das Datum dazu (`SOURCE_DATE_EPOCH`) und das Arbeitsverzeichnis nicht:
  Es sagt, *wohin* gesetzt wird, und bleibt mit dem Jobnamen beim Kommando.
  Von der Zusage gibt es eine Ausnahme, eben den Jobnamen: pdfx bildet aus ihm
  die DocumentID im XMP (ADR-0005, #297). Gleich ist das PDF also bei
  denselben Quellen unter demselben Jobnamen, und das Kommando nimmt immer
  `fileBaseName`.
  Gebaut wird der Wert im Renderer und nicht im Kommando, das sonst sechs
  Argumente aus vier Quellen zusammensetzte. Der Preis ist die Kante `generate.render →
  generate.toolchain` in `PackageStructureTest`: Der Renderer kennt den
  Eingabetyp der Werkzeuge. Sie zeigt nach unten auf ein Blatt, `toolchain`
  bleibt frei vom Projekt. Verworfen: zwei Typen mit einer Abbildung im
  Kommando – das verlegt das Durchreichen nur.
- **Satz nur in der Vorlage** (ADR-0017): Abstände, Schriftgrad, Farbe, Linien,
  Beschriftungen, Trenner, Ort und Reihenfolge stehen in der Vorlage des
  Designs (`templates/helsinki.tex`, `templates/prag.tex`). Der Renderer
  liefert Werte und Makroaufrufe und entscheidet, *ob* etwas dasteht; er
  schreibt kein LaTeX von sich aus – keinen Längennamen, kein `\\`, kein
  `\mbox`. Die Grenze läuft zwischen zwei Arten von Angaben: Eine **einzelne
  Angabe** kommt als eigener Platzhalter und darf leer sein, und die Vorlage
  entscheidet mit `\ifblank` über Ort, Beschriftung und Trenner;
  **Wiederholtes und fachliche Sätze** (Positionen, Summen, Absetzung,
  Pflichtsatz, Skonto, Bankblock) kommen als Makroaufruf oder gar nicht, und
  dort liegt das "ob" beim Renderer, weil es an der Rechnungsart hängt.
  Aufgezählt ist beides: `TemplateValue` die Platzhalter, `TemplateMacro` die
  Makros samt Stelligkeit, geprüft beim Bau der Vorlage; `Template` hat einen
  privaten Konstruktor. Wer für ein neues Design ein Wort braucht, das diese
  beiden Aufzählungen nicht haben, liest ADR-0017 und baut nicht am Renderer
  vorbei.

- **Grundlage** (ADR-0022): Was ein Design nicht anders setzen darf, steht
  einmal, in `templates/grundlage.sty` – pdfx, Ligaturen, die Einstellungen
  von siunitx gegen falsche Zahlen, Satzabstand, `LTchunksize` und die
  Bausteine `\ungeteilt`, `\programmsatz`, `\wennweitereseite`. Jede Vorlage
  lädt sie direkt nach `\documentclass`, der Renderer legt sie über
  `PdfSources.files` daneben, und was sie lädt, lädt die Vorlage nicht noch
  einmal. Die Frage für jede Zeile: Wäre ein Design mit einer anderen Zeile
  *falsch* oder nur *anders*? Hinein gehört nur, was falsch wäre; eine Zeile,
  die in allen Vorlagen gleich steht, bleibt Satzbild der Vorlage. Zwei
  Regeln prüft `Foundation.of`: kein Platzhalter, und kein Makro aus
  `TemplateMacro` – die definiert die Vorlage aus den Bausteinen, damit ihr
  Volltext jede Definition enthält, die der Renderer aufruft.

- **Zahlen** (ADR-0021): Kotlin bestimmt die Ziffern, die Vorlage ihre
  Schreibweise. `Numbers` reicht Beträge, Einzelpreise, Mengen und Sätze als
  `\betrag{8337.50}`, `\menge{86.5}`, `\prozent{19}` durch – Punkt, keine
  Gruppierung, `toPlainString()` und nie `toString()`, sonst läse siunitx
  `1E+3` als Exponenten. Die Vorlage setzt sie mit siunitx und wählt in
  ihrem `\sisetup` nur die Schreibweise: Dezimalzeichen, Tausendertrenner,
  ab wie vielen Stellen gruppiert wird. Was siunitx sonst still falsch
  setzte, etwa `0,033.5` statt `0,0335`, steht in der Grundlage. Nach dem
  Komma wird nie getrennt, weder durch Punkt noch durch Leerraum.
  `GermanFormat` formatiert nur noch Daten. Den Vertrag „jede Ziffer, keine
  weitere“ hält der `PflichtangabenTest` je Design am fertigen PDF, samt
  `schreibweise-der-zahlen.yaml`.

- **Texte mit Platzhaltern** (ADR-0010, ADR-0019): Der Pflichtsatz ist ein
  `TextTemplate<InvoiceVariable>`, beim Bau zerlegt – ein vertippter Name wirft,
  sobald `InvoiceType` geladen wird. Das `TextTemplate` kennt weder Format noch
  Vokabular. Den Wert holt ein `VariableResolver` als `TextValue` ohne Format
  (`InvoiceVariableResolver`, bei der `AcceptedInvoice`), wie er aussieht,
  entscheidet das Format (`toLatex` in `generate.render`, `toPlainText` für
  die Notiz der E-Rechnung in `generate.einvoice`). `<<NAME>>` gibt es
  damit zweimal:
  die Platzhalter der Design-Vorlage (`TemplateValue`) und die Variablen eines
  Textes (`InvoiceVariable`) – dieselbe Schreibweise, verschiedene Mengen.

- **Designs** (ADR-0011): Die Stammdaten wählen das Design, `design: helsinki`
  oder `design: prag`, ohne Eintrag `helsinki`. Jedes ist eine eingebaute
  Vorlage, die derselbe Renderer füllt – kein zweiter Renderer, kein Schalter,
  keine mitgebrachte Vorlage. Feste Texte gehören zum Design, ein Wortlaut mit
  fachlicher Aussage kommt aus Kotlin (ADR-0017). Ein neues Design ist eine
  Vorlage, die die Grundlage lädt, ein Eintrag in `Design`, eine
  Vergleichsdatei und eine Zeile im Pflichtangaben-Netz.

  Die **Akzentfarbe** (ADR-0024) nennen die Stammdaten, `akzentfarbe: 31AE34`,
  sechs Ziffern ohne `#`; die Vorgabe ohne Eintrag hat die Vorlage, nicht
  Kotlin. `helsinki` färbt damit den Balken, `prag` hat keine. Steht der
  Schlüssel ohne Wert da, bricht das Einlesen ab (`valueWhenPresent`): Ein
  `#` ohne Anführungszeichen ist in YAML ein Kommentar, und die Farbe
  verschwände sonst still.

- **Mehrere Seiten** (ADR-0020): Die Positionen brechen zwischen zwei
  Positionen um, und die Vorlage setzt die Folgeseiten – ein kurzer Kopf mit
  Nummer, Datum und „Seite n von N“, der Fuß des Briefbogens auf jeder Seite,
  der wiederholte Spaltenkopf und ein Fortsetzungshinweis im Seitenfuß. Kein
  Übertrag: Die Vorlage rechnet nicht, und der Renderer weiß nicht, wo die
  Seite bricht. Die Summen sind die letzte Zeile der Positionstabelle und
  stehen so nie ohne eine Position darüber; Schlusssatz, Gruß und Name
  bleiben beisammen, und kein fachlicher Satz bricht über zwei Seiten – auch
  kein Hinweis auf eine Steuerbefreiung, deshalb trennt die Grundlage den
  Zusammenhalt (`\ungeteilt`) vom Satzabstand (`\programmsatz`). Eine
  einseitige Rechnung sieht aus wie zuvor, bei 300 dpi pixelgleich; ein
  neues Design muss die Folgeseiten selbst setzen, und das Netz prüft sie an
  `viele-positionen.yaml`, `schluss-an-der-seitengrenze.yaml` und
  `befreiungsgrund-an-der-seitengrenze.yaml`.
  Unabhängig davon bricht `LatexCompiler` ab, wenn
  das Protokoll `Overfull \vbox` meldet: Dann ist Inhalt vom Blatt gefallen,
  und ein gültiges PDF/A ohne ihn darf nicht entstehen.

- **Logo** (ADR-0012): Die Stammdaten nennen es als Datei, `logo: logo.tex`,
  relativ zu ihrem Verzeichnis; ohne Eintrag kein Logo. Die Datei definiert
  `\logozeichnung` (Pflicht, der Rumpf einer `tikzpicture`) und
  `\logoskalierung` (optional) und bringt Farben und Schrift selbst mit.
  Gelesen wird sie in `YamlLoader.loadSellerConfig`. Der Renderer liefert
  `LOGO` als Schalter und die Datei in `RenderedInvoice.files`, aber nur, wenn
  die Vorlage `<<LOGO>>` enthält, und sagt mit `RenderedInvoice.includesLogo`
  ausdrücklich, ob das Blatt es zeigt. **Keine Bilddatei**: `luatex.def` gibt
  jedem per `\includegraphics` eingebundenen PDF eine Transparenzgruppe mit,
  und die verbietet PDF/A-1 (#197).

- **Externe Prozesse**: Ausgabe wird in Dateien umgeleitet, nicht in Streams
  gelesen – sonst blockiert das Lesen bei einem hängenden Prozess, und
  die Zeitgrenze käme nie zum Zug. Zwei Dateien, denn stdout und stderr
  bedeuten nicht dasselbe: `verapdf` schreibt seinen Bericht auf stdout und
  sein Protokoll auf stderr, und zusammengeführt landeten Log-Zeilen mitten im
  XML – der Parser brach ab, und für die Benutzerin sah es aus wie ein Absturz
  des Generators (#227). `ToolResult.output` hängt beide Kanäle aneinander,
  für Meldungen, die alles wollen; die Reihenfolge *zwischen* ihnen geht dabei
  verloren, innerhalb eines Kanals nicht. An der Nahtstelle steht ein
  Zeilenumbruch, sonst klebte die letzte Zeile des einen an der ersten des
  anderen und sähe in der Meldung wie eine Zeile aus einer Quelle aus (#246).
  LuaLaTeX läuft, solange die Log-Datei einen
  weiteren Lauf verlangt, höchstens dreimal – wer nach drei Läufen immer noch
  einen vierten fordert, konvergiert nicht. Bei den Rechnungen dieses Projekts
  sind es zwei, und beide sind nötig (#321): longtable kennt die
  Spaltenbreiten der Positionen erst aus dem ersten Lauf, lastpage die Zahl
  der Seiten. Nicht jedes Paket verlangt den Lauf mit denselben Worten –
  longtable schreibt "Rerun LaTeX", nicht "Rerun to get" –, und
  `needsAnotherPass` muss jede Wendung kennen: Sonst löst ein anderes Paket
  den Lauf nur zufällig mit aus, und fällt es weg, entsteht das PDF aus dem
  ersten. Lesezeichen schreiben die
  Vorlagen keine (`bookmarks=false`); die leere `.out` von hyperref
  verlangte sonst ebenfalls einen zweiten Lauf, ohne einen Grund zu haben.
  Dieselben Quellen ergeben dasselbe PDF, Byte für Byte: Das
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
  die Abbruchliste allein in `RejectedException`; täte es jedes Kommando
  selbst, liefen die Fassungen auseinander (#52).
  Zieldatei, Überschreibschutz und das Urteil über das PDF/A entscheidet
  `erzeuge` selbst in `run`, samt eigenem `CliktError` und einer geliehenen
  `ToolchainException`: Ein Ablauf ohne Clikt hätte heute keinen zweiten
  Aufrufer. Heraus müssen die Regeln mit dem zweiten Weg zu einer
  Rechnungsdatei, als Kommando oder als Zweig in `erzeuge` – nicht
  abgeschrieben (#359).
  `erzeuge --oeffnen` öffnet das fertige PDF über `PdfViewer`, eine eigene
  Naht im Paket `generate` und bewusst nicht in `toolchain` (#144): Dort
  liegen die Werkzeuge, ohne die kein PDF entsteht, und `checkAvailable()`
  prüft sie beim Start. Fehlt der Viewer, entsteht die Rechnung trotzdem – ein
  Fehlschlag ist deshalb ein Hinweis mit Exitcode 0, kein Abbruch. Gestartet
  wird nicht über `ProcessRunner`, dessen Vertrag bis zum Ende wartet und hart
  abräumt; das risse unter Linux den Viewer mit.
  `--version` nennt die Fassung und endet mit 0. Woher sie zur Laufzeit kommt,
  ist eine Entscheidung und keine Formalie (#264): aus einer Ressource, die
  `generateVersionResource` beim Bauen aus `version` in `build.gradle.kts`
  schreibt – gelesen mit `checkNotNull`, wie `Template.of` die Vorlagen der
  Designs liest. **Nicht** aus dem Manifest-Attribut `Implementation-Version`:
  Das liefert `null`, sobald nicht aus dem JAR heraus gestartet wird, also in
  jedem Test – geprüft wäre dann nur der Zweig, der im Betrieb nie läuft. Dass
  die genannte Zahl die aus dem Build ist, hält ein Test fest, dem Gradle
  `version` als Systemeigenschaft reicht; ohne Gradle überspringt er sich.
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
- **Rechnungsnummer** (ADR-0005): Geprüft wird nur ihre Form – welche gilt,
  steht als regulärer Ausdruck in den Stammdaten (`rechnungsnummer-muster`),
  und ohne Eintrag wird gar nicht geprüft. Die Einmaligkeit (§14 Abs. 4 Satz 1
  Nr. 4 UStG) prüft der Generator nicht: Beim Erzeugen sieht er eine einzige
  Datei (ADR-0001). Die Prüfung sitzt deshalb in `InvoiceNumberRules` und nicht
  im Serialisierer – ob eine Nummer gültig ist, hängt an zwei Dateien. Leere
  Nummer, Leerraum am Rand und eine Nummer, von der kein Dateiname übrig
  bleibt, brechen unabhängig vom Muster ab (`RECHNUNGSNUMMER_FEHLT`,
  `RECHNUNGSNUMMER_MIT_LEERRAUM`, `RECHNUNGSNUMMER_OHNE_DATEINAMEN`):
  Schlussrechnung und Storno verweisen später auf die Nummer und vergleichen
  Zeichen für Zeichen (#53), und aus ihr entsteht der Jobname, aus dem pdfx die
  DocumentID bildet (Nachtrag zu ADR-0005). Abgeleitet wird er in
  `CalculatedInvoice.fileBaseName`. Die Nummern der abgesetzten Abschläge
  bleiben ungeprüft.

- **Steuerkategorie**: `TaxCategory` (BT-151, Codeliste UNCL 5305) pro Position,
  Vorgabe S. Der Satz allein genügt nicht – ohne Kategorie lässt sich "0 %"
  nicht von "steuerbefreit" und nicht von "Steuerschuldnerschaft des
  Leistungsempfängers" unterscheiden. Die Folgeregeln (Satz 0 bei steuerfreien
  Kategorien `SATZ_MUSS_NULL_SEIN`, Befreiungsgrund BT-120
  `BEFREIUNGSGRUND_FEHLT`, USt-IdNr. des Kunden BT-48 bei AE und K
  `KUNDEN_UST_ID_FEHLT`) prüft `TaxCategoryRules`, nicht erst ein späterer
  Export: Eine
  widersprüchliche Rechnung soll nicht als PDF hinausgehen. Gruppiert wird nach
  Kategorie **und** Satz, weil zwei Positionen mit 0 % steuerlich Verschiedenes
  bedeuten können.

  Auf dem Blatt trennt der **Befreiungsgrund** die Gruppen, nicht ihr
  Steuerbetrag (#218): Wer einen Grund trägt (E, AE, K, O), steht im Hinweis
  unter den Summen, wer keinen trägt (S, Z), als Steuerzeile im Summenblock –
  der Nullsatz also mit "0 % … 0,00 €", denn er ist keine Befreiung, sondern
  eine Besteuerung mit 0 %. Entschiede das der Steuerbetrag, fiele eine
  Z-Gruppe ganz vom Blatt. Die Steuerzeile nennt außerdem das Entgelt
  ihrer Gruppe (§14 Abs. 4 Nr. 7 UStG), sobald es mehr als eine gibt; bei
  einer wäre es die Zwischensumme und stünde doppelt da. Geprüft wird das an
  `gemischte-steuersaetze.yaml` im Netz, mit zwei Positionen je Satz – bei
  einer stünde das Entgelt zufällig schon als Positionsbetrag da.
- **Bestätigungsabfrage**: Vor einer Rechnung ohne Umsatzsteuer muss die
  USt-IdNr. des Kunden geprüft und die Prüfung dokumentiert sein
  (`kunde: ust-id-geprueft`). Sonst bricht der Lauf ab
  (`BESTAETIGUNGSABFRAGE_FEHLT` in `VatIdCheckRules`). Verschieden streng, weil die Rechtslage es ist (Stand August 2026): bei K immer (§6a
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
  später noch einmal die Gesamtleistung, schuldet die Umsatzsteuer nach §14c
  Abs. 1 UStG zweimal. Die YAML-Werte heißen wie die Dokumente in `CONTEXT.md`
  (`abschlagsrechnung`, nicht `anzahlung`), nicht wie die Codeliste; die
  Schlussrechnung trägt denselben Code 380 wie die gewöhnliche Rechnung, weil
  sie *ist* die Rechnung über die Gesamtleistung. `servicePeriodIsForecast`,
  `chargesForService` und `mandatoryNote` haben **keine** Vorgabewerte – jeder
  Eintrag nennt sie ausdrücklich, damit eine neue Art nicht still die Antworten
  der gewöhnlichen Rechnung erbt (ADR-0009, ADR-0008).

  Dazu ein Sicherungsnetz: `src/test/resources/rechnung-erwartet.tex` ist das
  erwartete LaTeX der Beispielrechnung, zeichengleich verglichen, daneben je
  Art eine Inhalts-Datei `<art>-inhalt.tex` und je Design ein Volltext der
  gewöhnlichen Rechnung. Wird einer rot, ist das **eine Frage und keine
  Aufgabe**: erst nachsehen, ob die Änderung gewollt ist, dann die Datei neu
  erzeugen – und das erzeugte PDF gegen das vorherige prüfen. Gleiche Prüfsumme
  heißt: nichts geändert; weicht sie ab, entscheidet das Bild (`pdftoppm`),
  denn eine geänderte Leerzeile der Vorlage ändert die Trailer-ID, aber nicht
  das Satzbild. Einen Schalter zum Neu-Erzeugen gibt es nicht. Welche
  Beispieldatei zu welcher Art gehört, steht in `exampleInvoices`
  (`core/model/TestFixtures.kt`).

- **Storno** (ADR-0006): Eine falsche Rechnung wird nicht überschrieben,
  sondern aufgehoben – `art: stornorechnung` mit eigener Nummer, dem Bezug auf
  die Ursprungsrechnung unter `storniert` (BG-3, Pflicht) und den Positionen
  der Ursprungsrechnung mit **negativer Menge**, nie negativem Einzelpreis
  (BR-27 verbietet ihn). Code 384 und nicht 381: Die KoSIT führt 381 für die
  kaufmännische Gutschrift ohne Bezug. Zahlungsziel und Bankverbindung
  entfallen, weil ein Storno kein Geld fordert; die Ursprungsrechnung nennt der
  Pflichtsatz über `<<URSPRUNGSNUMMER>>` und `<<URSPRUNGSDATUM>>` (ADR-0010, ADR-0019),
  eine eigene Zeile im Kopf gibt es nicht. Das Wort **Gutschrift** fällt nicht:
  Umsatzsteuerlich ist das eine Rechnung des Leistungsempfängers. Nur ganz,
  nicht teilweise – eine Teilberichtigung kennt das Projekt nicht, erwogen und
  mangels Anlass geschlossen in #64. Über die eigene Datei hinaus wirkt ein
  Storno nur dort, wo jemand das Ablageverzeichnis liest, also in
  `offene-abschlaege.py` (#66).

- **Absetzung** (ADR-0001, ADR-0002): Die Schlussrechnung trägt die
  vereinnahmten Abschläge unter `abschlaege` ausgeschrieben, mit
  `steueranteile` je Kombination aus Kategorie und Satz. Nachgeschlagen wird
  nichts: Eine Rechnung, die beim Erzeugen fremde Dateien liest, ergibt nicht
  zweimal dasselbe PDF. Das Abtippen übernimmt `rechnungsgenerator abschlaege`
  (ADR-0003) – alles oder nichts, gehalten vom Typ `CollectedPrepayments`.
  Eingefügt wird von Hand, deshalb bleiben die Abbruchregeln bei der
  Schlussrechnung, in `FinalInvoiceRules`: darunter die Prüfsumme
  `netto × satz = ust` (`ABSCHLAG_PRUEFSUMME`) und die Forderung, dass
  Kategorie und Satz in der Schlussrechnung überhaupt vorkommen
  (`ABSCHLAG_KOMBINATION_FEHLT`). `ABSCHLAG_DOPPELT` fängt dieselbe Nummer nur
  *innerhalb* einer Datei; dass zwei Schlussrechnungen denselben Abschlag
  absetzen, sieht allein `offene-abschlaege.py`. Abgesetzt wird nur, was
  **vereinnahmt** ist (§14 Abs. 5 Satz 2 UStG).

  Im PDF steht die Absetzung unter dem Gesamtbetrag und nie als Position: Eine
  negative Position senkte die Bemessungsgrundlage und damit die ausgewiesene
  Umsatzsteuer, die aber in voller Höhe geschuldet wird. Restbetrag null
  bekommt einen Satz statt einer Zahl, und dann entfallen auch Zahlungsziel und
  Bankverbindung – beides folgt aus `requestsPayment`, das neben der
  Rechnungsart den Restbetrag kennt. Alle Summen sind abgeleitet und nicht
  gespeichert: Im Konstruktor der `CalculatedInvoice` stehen nur Rechnung und
  Stammdaten, der Rumpf rechnet den Rest aus (#172).

- **Skonto**: Ein im Voraus vereinbartes Skonto (§14 Abs. 4 Satz 1 Nr. 7 UStG)
  steht als Liste von Skontostufen unter `skonto`, je `satz` und `tage` ab
  Rechnungsdatum (#183). Auf dem Blatt nennt es ein Satz unter den Summen, nach
  der Absetzung; der Kopf behält "Zahlbar ohne Abzug bis …". Kein Datum steht
  doppelt, weil jede Skontofrist vor dem Zahlungsziel endet
  (`SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL`). Geprüft wird in `SkontoRules` und
  nicht in `InvoiceKindRules`: Skonto ist keine Angabe einer Rechnungsart
  (ADR-0008). Bewusst **nicht**:
  - kein Skontobetrag und kein Zahlbetrag. UStAE 14.5 Abs. 19 lässt die
    Bedingung genügen; ein Betrag müsste bei mehreren Steuersätzen aufgeteilt
    werden und sähe neben dem Gesamtbetrag wie eine zweite Forderung aus.
  - keine Vorgabe in den Stammdaten: Vereinbart wird mit dem Kunden, eine
    Vorgabe landete still auf jeder Rechnung.
  - nur Rechnung und Teilrechnung (`SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH`). Auf
    der Abschlagsrechnung änderte ein Abzug, was vereinnahmt wird, und damit
    den Block `abschlaege` der Schlussrechnung; auf der Schlussrechnung wäre
    die Grundlage der Restbetrag, eine eigene Frage; das Storno fordert kein
    Geld.
- **Rabatt** (ADR-0013): Eine Position trägt ihn als Satz, `rabatt: 30`, oder
  als Betrag, `rabatt-betrag: 200.00`, und er mindert den Positionsbetrag,
  nicht den Einzelpreis (BG-27 und nicht BT-147). Basis und Rabattbetrag sind
  je auf Cent gerundet, das Netto ist ihre Differenz. Zulässig ist **genau
  eines** von beidem (`RABATT_DOPPELT`), ein Satz über 0 bis 100 %
  (`RABATT_AUSSERHALB_BEREICH`) oder ein Betrag über 0,00 bis zur Basis
  (`RABATT_BETRAG_AUSSERHALB_BEREICH`), auf jeder Art. Der Betrag steht positiv
  in der Datei und bekommt das Vorzeichen der Basis, damit ein Storno bleibt,
  was ADR-0006 sagt. Im Modell ist der Rabatt ein Typ: Die zwei YAML-Schlüssel
  sind private Eigenschaften von `LineItem`, nach draußen geht
  `discount: Discount?`, und wer ihn auswertet, entscheidet in einem `when`
  ohne `else`. In der Spalte steht, was vereinbart wurde – Satz oder Betrag,
  nicht stets der Rabattbetrag.

- **ZUGFeRD** (ADR-0023): `erzeuge --zugferd` erzeugt eine E-Rechnung im
  Profil EXTENDED, für alle Arten dasselbe (ADR-0002). Das XML schreibt
  `CiiWriter` in `generate.einvoice`, aus derselben `AcceptedInvoice` wie der
  Renderer und wie er ohne eigene Rechnung – jede Zahl kommt aus der
  `CalculatedInvoice`, denn Mustangs Modell rundet anders als ADR-0013 und
  kennt `SpecifiedAdvancePayment` nicht. `<art>-erwartet.xml` ist je Art, die
  der Writer schreibt, die Vergleichsdatei, eine Frage und keine Aufgabe wie
  die `.tex`-Dateien; daneben prüft Mustang das XML, und erlaubt ist allein
  die Warnung `PEPPOL-EN16931-R008` zum leeren
  `ApplicableHeaderTradeDelivery`. Leer ist es nicht bei K: Dort steht das Land
  des Kunden als Lieferland (BR-IC-12). Auch eine Warnung ohne Kennung zählt:
  So meldet Mustang eine Summe, die er anders nachrechnet. Satz und Basis
  eines Rabatts rechnet er nicht nach, die hält der Test wörtlich fest. O
  neben anderen Kategorien lehnt keine Regel ab, weil EXTENDED BR-O-11 bis
  BR-O-14 nicht prüft (Nachtrag zu ADR-0023).

  Der Writer schreibt jede Rechnungsart; was eine Art zusätzlich trägt –
  Vereinnahmung, Ursprungsrechnung, abgesetzte Abschläge als
  `SpecifiedAdvancePayment` –, fragt er die `InvoiceKind` in einem `when` ohne
  `else`. Das XML ist die maßgebliche Fassung, eine Angabe fehlt dort nie
  still: Eine neue Art muss in diesem `when` entscheiden, was sie trägt, und
  kommt eine Angabe, die der Writer nicht schreiben kann, bricht `erzeuge
  --zugferd` mit einer Kennung für genau sie ab, statt sie wegzulassen
  (Nachtrag zu ADR-0023).

  Mustang wirkt nur hinter der Naht `Typesetter`: `EInvoiceValidator` prüft
  das XML vor dem Satz – ein Verstoß ist eine Lücke im Generator, deshalb
  `ToolchainException` und kein PDF –, `EInvoiceEmbedder` bettet es danach
  ein. Mustang setzt dabei die Uhr ein und schreibt das XMP neu; der Embedder
  übernimmt vor dem Speichern XMP, Info-Dictionary und Datum aus dem
  PDF/A-1b. Das hängt an zwei Eigenheiten, die Mustang nirgends zusagt: Das
  Dokument ist nur über das `protected`-Feld `doc` erreichbar, und `setXML`
  ruft selbst `prepare()` – ein zweiter Aufruf hinge das XML zweimal an.
  `EInvoiceEmbedderTest` hält Bytegleichheit, genau einen Anhang und die
  Metadaten fest; bei einem Versionssprung von Mustang zuerst dort lesen.

  Im Klassenpfad: veraPDF als `-jakarta`-Familie, weil Mustang sie nachzieht
  (Nachtrag zu ADR-0014); FOP und `en16931-cii2ubl` ausgeschlossen, an
  `implementation`, damit die Tests mit dem ausgelieferten Klassenpfad laufen;
  `slf4j-nop`, sonst schriebe SLF4J bei jedem ZUGFeRD-Lauf auf stderr.
- **Befunde statt Abbruch beim ersten Fehler** (ADR-0004): Die Prüfung wirft
  nicht, sondern liefert eine Liste von Befunden – Kennung, Schwere (Abbruch
  oder Hinweis), Gegenstand und Meldung; den Gegenstand nennt die Ausgabe und
  nicht die Meldung. Abgebrochen wird im Kommando, und dann stehen alle Gründe
  auf einmal da. `CalculatedInvoice` rechnet nur; geprüft wird in Regelgruppen
  im Paket `rules`, je eine Datei neben ihrem gleichnamigen Test, jede gegen
  das fertig gerechnete Ergebnis. Den Prüflauf führt `AcceptedInvoice.check`,
  nicht die `CalculatedInvoice`: Befunde sind ein Urteil über die Rechnung und
  kein abgeleiteter Wert wie `dueDate`.

  Setzen lässt sich nur eine `AcceptedInvoice`, und die gibt es nur ohne
  Abbruch: Renderer und `prepaymentsOf` verlangen sie, damit hält der Compiler
  die Zusage aus ADR-0004 und nicht die Disziplin der Kommandos (ADR-0007). Sie
  trägt die Rechnungsart als Typ (`InvoiceKind`), gebaut in `InvoiceKindRules`
  zusammen mit den Regeln, ohne die er nicht baubar ist (ADR-0008). Die
  Regelgruppe einer Rechnungsart prüft nur auf dieser Art – als `when` über
  alle Arten ohne `else`, damit eine neue Art dort entscheiden muss; ebenso
  fragen Renderer und `InvoiceVariableResolver` die `InvoiceKind`. Nur
  `prepaymentOf` behält sein `else`, dort ist genau eine Art richtig.

  Die Kennungen sind deutsch benannt wie die Regeln und **nicht** nummeriert.
  Tests prüfen die Kennung statt des Wortlauts (`aborts().shouldBeEmpty()`).
  Alle stehen in `Rule` im Kern, auch die drei, die allein `abschlaege`
  vergibt – die bewusste Ausnahme vom Schnitt der Paketstruktur (#314).
  Was das Programm ablehnt, steht so an einer Stelle, und `abschlaege` meldet
  die Abbrüche der gelesenen Abschlagsrechnungen in derselben Liste wie seine
  eigenen. `PackageStructureTest` sieht die Ausnahme nicht: Kein Import verrät
  sie, der Kern kennt hier Wissen des Rands und keinen Typ.

- **Die PDF/A-Prüfung ist Teil der Tests**: erzeugte PDFs werden geprüft; ohne
  gültiges PDF/A kein grüner Build. Das gilt seit ADR-0014 mit dem eingebauten
  Prüfer, und deshalb auch ohne installiertes veraPDF. Die zwei Tests, die das
  *Programm* brauchen – der Gegentest zum aufgezeichneten Bericht und der
  Vergleich der beiden Prüfer –, überspringen sich, wenn es fehlt. Übersprungen
  und nicht bestanden, wie beim Altersnachweis der EU-Liste: Ein Netz, das
  fehlt, soll nicht aussehen wie eines, das hält.
- **Paketstruktur**: Ein vertikales Paket je Kommando, mit allem, was
  nur dieses Kommando braucht: `generate` mit dem LaTeX-Satz
  (`generate.render`), dem XML der E-Rechnung (`generate.einvoice`) und der
  Naht zu den Werkzeugen (`generate.toolchain`),
  `prepayments` mit dem Zusammentragen der Abschläge und dem YAML-Block,
  `setup` mit der Vorlage der Stammdaten (#276) – es kennt aus dem Kern nur
  `model`, weil `einrichten` den Namen des Wurzelordners braucht und die Frage,
  ob oberhalb schon eine Wurzel liegt.
  Darunter der gemeinsame Kern im Paket `core`, in dem jede Kante nach unten
  zeigt:
  `accepted` (`AcceptedInvoice` samt `check`, `Verdict`,
  `InvoiceVariableResolver`) → `rules` (Befunde, Regelgruppen, `InvoiceKind`,
  `Deduction`) → `calculation` (`CalculatedInvoice`) → `model` (samt
  `YamlLoader` und den Fachkatalogen `InvoiceType` und `TaxCategory`) →
  `values`. In `values` liegen nur Werte (#312): Ein Fachkatalog ist technisch
  ein Enum, aber jeder Eintrag trägt Rechtsfolgen und Wortlaut und weiß, was
  aus ihm für die Rechnung folgt – das gehört neben `CheckMethod` nach
  `model`. Zwei Blätter importieren nichts aus dem Projekt: `text`
  (`TextTemplate`, `VariableResolver`, `TextValue`) liegt neben `values`, weil
  es keinen Wert kennt, und `serialization` darunter, das Gerüst, über das
  jeder Werttyp seinen Serializer baut. Die `@Serializable`-Annotationen
  bleiben an den Typen. In der Wurzel steht nur `Main.kt`: Wäre sie Kern
  und CLI zugleich, entstünden dort Zyklen (#107). Der Kern trägt ein eigenes
  Paket, damit schon der Quellbaum ein Kommando von einer Schicht unterscheidet
  (#310) – `core` und nicht `domain`, weil `model` mit `YamlLoader` und `Root`
  das Dateisystem liest. Ein späteres Gradle-Modul `:core` folgt dieser Linie
  und zieht keine neue. Der Schnitt trennt den Rand, nicht die Mitte – was
  beide Kommandos brauchen, ist der größere Teil. Die Grenze zwischen `rules`
  und `calculation` ist die aus
  ADR-0004: `CalculatedInvoice` rechnet nur, `check` prüft und urteilt.
  `accepted` liegt **über** den Regeln, weil `check` sie aufruft, und enthält,
  was eine angenommene Rechnung ist und wie man sie liest – nichts davon ist
  Regel oder Befund, und die Rand-Pakete brauchen fast nur das (#304).
  `InvoiceKind` und `Deduction` bleiben trotzdem in `rules`: Ein Typ wohnt,
  wo er gebaut wird, und gebaut werden sie in `InvoiceKindRules` (#180); eine
  Etage höher zeigte `rules` nach oben. Kotlin hält keine Paketgrenze (`internal` gilt pro Modul, nicht pro
  Paket); `PackageStructureTest` hält sie stattdessen (#112): eine Tabelle, wer
  woher importieren darf, gegen die Importe aller Quellen unter `src/main`,
  gelesen mit Konsist. Ein neues Paket muss dort eingetragen werden. Derselbe
  Test verbietet Importe aus dem eigenen Paket – ktlint lässt die absichtlich
  stehen, und beim Umzug in #111 blieben 99 davon liegen. Nicht Konsists
  `assertArchitecture`: Dessen Schichten schließen Unterpakete immer ein, und
  dann fällt ein Import von `generate.render` nach `generate` nicht auf.
  Die Testpakete spiegeln die Hauptpakete, geteilte Helfer liegen im Testpaket
  dessen, was sie bauen oder prüfen (`core/model/TestFixtures.kt`,
  `core/rules/CheckHelpers.kt`, `core/accepted/AcceptedHelpers.kt`); ein Test
  darf aber jedes Paket sehen. Kein ADR:
  Die Alternative war "so lassen", der Grund sind die Zyklen. ZUGFeRD hat den
  Schnitt nicht geändert, `einvoice` ist ein Randpaket neben `render`
  (ADR-0023).

## Konventionen

- Bezeichner Englisch, Kommentare Deutsch. Ein Kommentar **begründet** und
  erzählt nicht (ADR-0016): Er sagt, warum eine Stelle so ist und welche
  Alternative sie ausschließt – nicht, was vorher dort stand, wann sie so wurde
  und in welchem Issue. Eine Issue-Nummer in Klammern bleibt trotzdem, sie ist
  ein Verweis wie ein ADR-Verweis. Kotlin-Idiome werden nicht erklärt, die kann
  man nachschlagen; ein LaTeX-Fallstrick in einer Vorlage ist kein Idiom.

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

  Wie weit der Code davon abweicht, misst `scripts/deutsche-bezeichner.py`; es
  endet mit 1, solange es etwas findet.
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
- Ein Kommentar im Versionskatalog, der ein Issue nennt, ist eine
  **Bedingung** und keine Fußnote. `kotlin` steht auf 2.4.20 wegen
  CVE-2026-53914 (#158), `ktlintCli` auf 1.8.0 wegen #159, `jackson` auf der
  Reihe 2.22, weil #250 nachgemessen hat, dass unser Weg Jackson nie lädt.
  Bevor ein Dependabot-PR eine solche Zeile anhebt, wird das genannte Issue
  gelesen: Trägt seine Begründung noch, geht der Bump durch, sonst nicht.

  Das ist ausdrücklich eine **Gewohnheit und kein Netz** (#253) – nichts
  hält sie auf, wenn sie vergessen wird, und genau das ist in #250 passiert:
  Ein Bump hob Jackson über die Reihe, auf die #230 sich festgelegt hatte,
  und auffallen tat es erst ein Review später. Trotzdem so entschieden. Der
  mechanische Weg wäre ein `ignore` in `.github/dependabot.yml`, und der ist
  hier der schlechtere: `ignore` ist in den GitHub-Docs für „Version
  updates" **und** „Security updates" gekennzeichnet (nachgemessen am
  21.09.2026), unterdrückt also auch den Pull Request der
  Sicherheitsaktualisierung. Die Meldung bliebe stehen, die Abhilfe fiele
  weg – bei einer Zeile, die überhaupt nur wegen Sicherheitsmeldungen
  festgeschrieben wurde, die falsche Richtung. Bleibt die Gewohnheit
  stecken, ist der nächste Schritt kein `ignore`, sondern ein Test, der
  `gradle/libs.versions.toml` gegen die Bedingung liest – die Bauart des
  Altersnachweises der EU-Liste.

## Umgebung

- macOS, TeX Live lokal (`lualatex` in /Library/TeX/texbin), JDK 26 lokal;
  Build zielt per Toolchain auf JDK 21.
- veraPDF via Homebrew (`verapdf`) ist seit ADR-0014 **optional**: Es braucht
  nur, wer `--externe-pdfa-pruefung` benutzt, und ohne es überspringen sich die
  zwei Tests, die das Programm aufrufen.
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
