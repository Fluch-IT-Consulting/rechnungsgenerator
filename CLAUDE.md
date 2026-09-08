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
  verapdf) werden im PATH erwartet; `InvoiceGenerator.checkTools()` prüft das
  beim Start und nennt die fehlenden mit Installationshinweis.
- **Externe Prozesse**: Ausgabe wird in eine Datei umgeleitet, nicht in einen
  Stream gelesen – sonst blockiert das Lesen bei einem hängenden Prozess, und
  die Zeitgrenze käme nie zum Zug. LuaLaTeX läuft zweimal (hyperref schreibt
  die `.out`-Datei erst im ersten Lauf).
- **CLI**: Clikt, und zwar `clikt-core` ohne Mordant – die Vollvariante zieht
  JNA nach und schreibt auf JDK 24+ bei jedem Start vier Warnzeilen auf stderr.
  `main()` von Clikt beendet den Prozess mit Code 0 auch im Fehlerfall, deshalb
  `parse()` mit eigenem `exitProcess(e.statusCode)`.
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
  erzeugen.
- **ZUGFeRD-Vorbereitung**: Datenmodell enthält von Anfang an alle
  EN-16931-Pflichtfelder (Leistungsdatum, Steuerkategorie, Kennungen), damit
  ZUGFeRD (PDF/A-3 + XML) später ein zusätzlicher Ausgabemodus wird, kein Umbau.
  Bis dahin bewusst nur PDF/A-1b (Archivierungsanforderungen der E-Rechnung
  noch offen).
- **Validierung ist Teil der Tests**: erzeugte PDFs werden per veraPDF geprüft;
  ohne valides PDF/A kein grüner Build.

## Konventionen

- Bezeichner Englisch, Kommentare Deutsch. Kommentare sind lernorientiert
  (der Autor lernt Kotlin), aber knapp: Kotlin-Idiome kurz erklären, wo sie
  zum ersten Mal auftauchen – nicht jede Zeile kommentieren.
- Keine echten Firmen-/Kundendaten im Repo (Projekt wird ggf. öffentlich).
  Testdaten: fiktive Firma, Test-IBAN (DE89 3704 0044 …), erfundene USt-ID.
  Das gilt auch für **Beispiele in Dokumentation und Skills** – dort rutschen
  echte Kunden- und Projektnamen am ehesten hinein, weil sie beim Schreiben
  gerade zur Hand sind. Die echten Stammdaten liegen außerhalb des Repos, der
  Pfad dorthin kommt aus `RECHNUNGEN_DIR` in `.claude/settings.local.json`.
- Linting (ktlint) muss vor jedem Commit grün sein. Ein Hook in
  `.claude/settings.json` ruft nach jeder Änderung an einer `.kt`/`.kts`-Datei
  `./gradlew ktlintFormat` auf, damit das nicht von Hand nachgezogen werden muss.
  Kosten: rund 1 s pro Bearbeitung bei warmem Gradle-Daemon.

## Umgebung

- macOS, TeX Live lokal (`lualatex` in /Library/TeX/texbin), veraPDF via
  Homebrew (`verapdf`), JDK 25 lokal; Build zielt per Toolchain auf JDK 21.
