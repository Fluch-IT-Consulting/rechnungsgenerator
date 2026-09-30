# Rechnungsgenerator

Erzeugt aus einer YAML-Beschreibung eine deutsche Rechnung als valides
PDF/A-1b-Dokument – gesetzt mit LuaLaTeX, geprüft mit veraPDF. Auf Wunsch
entsteht eine E-Rechnung nach ZUGFeRD: ein PDF/A-3u, das die Rechnung
zusätzlich als XML trägt. Aus einer E-Rechnung, auch einer fremden, liest das
Programm das XML wieder aus, sobald sie ihre Prüfung besteht.

Geschrieben in Kotlin, gebaut mit Gradle.

## Installation

Über Homebrew, aus dem Tap
[Fluch-IT-Consulting/homebrew-tap](https://github.com/Fluch-IT-Consulting/homebrew-tap).
Weil dieses Repository privat ist, braucht Homebrew Lesezugriff darauf und
einen GitHub-Token. Einen eigenen legt man nicht an: Die GitHub-CLI hat ihn
schon.

```bash
gh auth login   # falls noch nicht angemeldet
brew tap fluch-it-consulting/tap
HOMEBREW_GITHUB_API_TOKEN=$(gh auth token) brew install fluch-it-consulting/tap/rechnungsgenerator
```

Auch das Anheben lädt ein neues Archiv und braucht den Token:

```bash
HOMEBREW_GITHUB_API_TOKEN=$(gh auth token) brew upgrade fluch-it-consulting/tap/rechnungsgenerator
```

Ein `brew upgrade` über alles hinweg, ohne Token, scheitert an dieser Formel
mit `401`. Mit `403` oder `404` sieht das Konto das Repository nicht – dann
fehlt der Lesezugriff, oder die Organisation hat die GitHub-CLI nicht
freigegeben.

Daneben braucht das Programm im PATH:

- ein JDK 21 oder neuer, etwa `brew install openjdk`. Auf macOS 14 und älter
  baut Homebrew das aus dem Quelltext; dort ist ein fertiges JDK, etwa von
  [Temurin](https://adoptium.net/), der schnellere Weg.
- TeX Live mit `lualatex`, etwa `brew install --cask mactex-no-gui`. Das
  Programm prüft das beim Start und sagt es, wenn es fehlt.
- nur für `--externe-pdfa-pruefung`: [veraPDF](https://verapdf.org/) –
  `brew install verapdf`. Ohne den Schalter prüft eine eingebaute Fassung
  derselben Bibliothek (ADR-0014).

Zum Bauen aus dem Quelltext genügt ein JDK 17+; Gradle lädt das Build-JDK bei
Bedarf selbst.

Nur für den `rechnung`-Skill in `.claude/skills/`: Python 3 mit
[PyYAML](https://pyyaml.org/) – `pip3 install pyyaml`. Das Skript
`offene-abschlaege.py` liest damit die Rechnungsdateien, um die noch offenen
Abschlagsrechnungen eines Kunden zu finden. Fehlt PyYAML, sagt das Skript, wie
man es installiert. Der Generator selbst braucht kein Python.

## Verwendung

`rechnungsgenerator --version` nennt die Fassung des Programms und endet mit
Code 0.

Der Weg von vorn: **einrichten**, die Stammdaten **ausfüllen**, Rechnungen
**erzeugen**.

```
rechnungsgenerator einrichten
```

Legt `.rechnungsgenerator/stammdaten.yaml` im Arbeitsverzeichnis an – eine
Vorlage mit leeren Pflichtfeldern und Kommentaren – und macht das Verzeichnis
damit zur Wurzel (siehe [Wo die Stammdaten liegen](#wo-die-stammdaten-liegen)).
Auf stdout steht allein der Pfad der angelegten Datei, `$(rechnungsgenerator
einrichten)` liefert also genau ihn.

Vorhandenes wird nie überschrieben: Gibt es die Datei schon, bricht das
Kommando ab, und es gibt keinen Schalter dagegen. Gibt es den Ordner ohne die
Datei, entsteht nur die Datei. Liegt oberhalb schon eine Wurzel, entsteht die
neue trotzdem, mit einem Hinweis auf stderr – verschachteln ist der vorgesehene
Weg für einen zweiten Rechnungssteller, aus Versehen aber eine Falle.

Ausgefüllt wird von Hand; die Kommentare in der Datei sagen, was freiwillig
ist und welche Vorgabe ohne Eintrag gilt. Die Datei gehört danach dem, der sie
ausfüllt: Das Programm schreibt nie wieder in den Ordner.

Aus einer ausgefüllten Rechnungsdatei entsteht dann das PDF:

```
rechnungsgenerator erzeuge [OPTIONEN] <rechnung.yaml>

  -s, --stammdaten=<pfad>    Stammdaten des Rechnungsstellers
                             (ohne Angabe aus der Wurzel über der Rechnung)
  -o, --ausgabe=<pfad>       Zieldatei
                             (ohne Angabe neben der Rechnung, mit .pdf)
  --ueberschreiben           Eine vorhandene Zieldatei ersetzen
  --arbeitsdateien-behalten  Das .tex und die LaTeX-Hilfsdateien nicht löschen
  --oeffnen                  Die fertige Rechnung im PDF-Viewer des Systems öffnen
  --zugferd                  Eine E-Rechnung erzeugen (siehe unten)
  --externe-pdfa-pruefung    Das installierte Programm verapdf prüfen lassen
```

Ohne `-o` entsteht das PDF neben der Rechnungsdatei und heißt wie sie, mit
`.pdf` statt ihrer Endung – aus `2026/R26-009.yaml` wird `2026/R26-009.pdf`,
egal aus welchem Verzeichnis man aufruft (ADR-0018). Ein Pfad hinter `-o` gilt
dagegen wie jeder getippte Pfad relativ zum Arbeitsverzeichnis. Die
Rechnungsdatei selbst ist nie das Ziel, auch nicht mit `--ueberschreiben`.

Eine vorhandene Zieldatei bleibt ohne `--ueberschreiben` unangetastet: Sie
kann die Rechnung sein, die schon beim Kunden liegt. Der Schalter ist fürs
Nachbessern eines Entwurfs gedacht. Ein PDF, das die PDF/A-Prüfung nicht
besteht, kommt in keinem Fall ans Ziel.

`--oeffnen` öffnet die Rechnung nach der Ausgabe mit dem Programm, das das
System für PDFs eingerichtet hat – `open` auf macOS, `xdg-open` auf Linux.
Geöffnet wird nur, was gültig am Ziel liegt. Lässt sich das PDF nicht öffnen,
steht ein Hinweis unter der Ausgabe; der Exitcode bleibt 0, denn die Rechnung
ist entstanden.

Beispiel:

```
rechnungsgenerator erzeuge Rechnungen/2026/R26-042.yaml
```

Ausgabe – der Pfad so, wie die Rechnungsdatei angegeben wurde:

```
Rechnungen/2026/R26-042.pdf
PDF/A-1b: gültig
```

Der Exitcode ist 0 nur bei Erfolg. Ein Tippfehler in der YAML, ein LaTeX-Fehler
oder ein PDF, das die PDF/A-Prüfung nicht besteht, führen zu Exitcode 1 –
ebenso ein Aufruf ohne Unterkommando, der nur die Hilfe zeigt. Die
Fehlermeldung steht dann auf stderr; stdout trägt nur das Ergebnis und lässt
sich gefahrlos umleiten.

Steht etwas über den Rand seiner Zeile oder Spalte – in einer Tabelle
womöglich über der Spalte daneben –, folgt dem Ergebnis ein Hinweis mit der
Fundstelle aus dem Protokoll von LuaLaTeX. Das PDF ist gültig und vollständig,
der Exitcode bleibt 0; vor dem Versand gehört es trotzdem angesehen. Fehlt
dagegen Inhalt, weil eine Seite mehr trägt, als das Blatt fasst, oder weil die
Schrift des Designs ein Zeichen nicht hat, bricht der Satz mit Exitcode 1 ab.

### E-Rechnung (ZUGFeRD)

Mit `--zugferd` entsteht statt des PDF/A-1b eine E-Rechnung nach ZUGFeRD im
Profil EXTENDED: ein PDF/A-3u, das die Rechnung zusätzlich als XML
(`factur-x.xml`) trägt. Das Blatt sieht aus wie ohne den Schalter; Zieldatei
und Überschreibschutz sind dieselben, und die Ausgabe nennt das Profil:

```
Rechnungen/2026/R26-042.pdf
PDF/A-3u: gültig
```

Im XML stehen dieselben Zahlen wie auf dem Blatt, denn beide kommen aus
derselben Rechnung und keines rechnet selbst. Geprüft wird beides: das XML
gegen Schema und Regeln des Profils, bevor es eingebettet wird, das Ergebnis
gegen PDF/A-3u. Ein ungültiges XML ist ein Fehler des Generators und nicht der
Rechnung; es entsteht dann kein PDF. Ein Lauf dauert mit dem Schalter einige
Sekunden länger, fast alles für die Prüfung des XML.

Das geht mit jeder Rechnungsart, in jeder Steuerkategorie und samt Rabatt
und Skonto. Die Schlussrechnung setzt ihre Abschläge im XML strukturiert ab,
je Abschlag mit Betrag, Tag der Vereinnahmung und Steueranteilen. Warum
ZUGFeRD und nicht XRechnung und warum EXTENDED, steht in ADR-0002 und
ADR-0023.

### Das XML einer E-Rechnung auslesen

```
rechnungsgenerator xml <rechnung.pdf>
```

Prüft, ob das PDF eine gültige E-Rechnung nach ZUGFeRD ist, und gibt dann ihr
XML auf stdout aus, Byte für Byte, wie es eingebettet ist:

```
rechnungsgenerator xml Rechnungen/2026/R26-042.pdf > R26-042.xml
```

Das geht mit jeder E-Rechnung nach ZUGFeRD oder Factur-X, nicht nur mit den
eigenen: in jedem Profil, das der EN 16931 genügt – BASIC, EN16931, EXTENDED
und XRECHNUNG –, und mit dem Anhang unter jedem Namen, den die Fassungen
vorsehen (`factur-x.xml`, `zugferd-invoice.xml`, `xrechnung.xml`,
`ZUGFeRD-invoice.xml`). Geprüft wird das PDF gegen das PDF/A-3, als das es
sich ausgibt (a, b oder u), und das XML gegen Schema und Regeln seines
Profils. MINIMUM, BASIC-WL und ZUGFeRD 1 werden abgelehnt, auch wenn beide
Prüfungen bestehen: Sie tragen nicht alle Pflichtangaben und sind nach dem
BMF-Schreiben vom 15.10.2024 keine E-Rechnung.

Besteht die Datei eine Prüfung nicht, bleibt stdout leer, der Exitcode ist 1,
und auf stderr stehen alle Gründe auf einmal:

```
R26-042.pdf ist keine gültige E-Rechnung nach ZUGFeRD.
Es gibt sich als PDF/A-1b aus. Eine E-Rechnung nach ZUGFeRD ist ein PDF/A-3.
Es trägt kein XML einer E-Rechnung, gesucht unter factur-x.xml, zugferd-invoice.xml, xrechnung.xml, ZUGFeRD-invoice.xml.
```

Eine Warnung macht das XML nicht ungültig: Sie steht als Hinweis auf stderr,
und das XML kommt trotzdem. Ebenso ein Fehler im PDF/A, wenn Verkäufer und
Käufer beide in Deutschland sitzen und die Datei sich als PDF/A-3 ausgibt:
Dann gilt nach der Geschäftsregel BR-FX-DE-03 das XML, und der Empfänger kann
eine berichtigte Fassung verlangen. Stammdaten braucht das Kommando nicht, LuaLaTeX
auch nicht.

### Abschläge für die Schlussrechnung vorbereiten

```
rechnungsgenerator abschlaege [OPTIONEN] <abschlagsrechnung.yaml>...

  -s, --stammdaten=<pfad>    Stammdaten des Rechnungsstellers
                             (ohne Angabe aus der gemeinsamen Wurzel)
```

Rechnet die übergebenen Abschlagsrechnungen und gibt daraus den
`abschlaege`-Block einer Schlussrechnung auf stdout aus. Wo `vereinnahmt-am` in
keiner Datei steht, bleibt das Feld leer und trägt einen Hinweis – dort gehört
das Datum der Vereinnahmung hin. An eine Schlussrechnung, die noch keinen
`abschlaege`-Block hat, lässt sich die Ausgabe direkt anhängen:

```
rechnungsgenerator abschlaege R26-009.yaml R26-011.yaml >> R26-014.yaml
```

Bei einem Fehler steht nichts auf stdout, ein halber Block landet also nie in
der Schlussrechnung.

Gesucht wird je Datei. Liegen die Abschlagsrechnungen in verschiedenen Wurzeln,
bricht das Kommando ab und nennt beide: Abschläge zweier Rechnungssteller
gehören nicht in einen Block.

## Eingabedateien

Zwei YAML-Dateien: die Stammdaten (auf jeder Rechnung gleich) und die Rechnung
selbst. Beispiele liegen in [src/test/resources](src/test/resources).

### Wo die Stammdaten liegen

Ein Verzeichnis, das den Ordner `.rechnungsgenerator/` enthält, ist eine
**Wurzel** (ADR-0015). Darin liegt `stammdaten.yaml`, und alles unterhalb der
Wurzel wird damit gesetzt:

```
Rechnungen/
  .rechnungsgenerator/
    stammdaten.yaml
    logo.tex
  2026/
    R26-009.yaml
```

```
rechnungsgenerator erzeuge Rechnungen/2026/R26-009.yaml
```

Das findet die Stammdaten von jedem Arbeitsverzeichnis aus. Gesucht wird ab dem
Verzeichnis der **Rechnungsdatei** den Pfad hinauf, bis zur Wurzel des
Dateisystems – derselbe Aufruf auf dieselbe Datei findet damit immer dieselben
Stammdaten, egal wo man steht. Ein zweiter Rechnungssteller ist eine zweite
Wurzel, daneben oder verschachtelt; die nächste gewinnt. Für alles andere gibt
es `-s`, und dann wird nicht gesucht.

Angelegt wird der Ordner samt Vorlage von `rechnungsgenerator einrichten`.
Findet die Suche nichts, nennt die Meldung das Verzeichnis, ab dem gesucht
wurde, den erwarteten Ordner und beide Auswege – `einrichten` und `-s`. Trifft sie auf einen Ordner
`.rechnungsgenerator/` ohne `stammdaten.yaml`, bricht sie dort ab, statt weiter
oben nachzusehen: Eine halb eingerichtete Wurzel soll nicht still die
Stammdaten der äußeren nehmen und den falschen Aussteller aufs Blatt setzen.

Wer bisher `stammdaten.yaml` im Arbeitsverzeichnis liegen hatte, zieht sie um –
eine Logo-Datei kommt mit:

```
mkdir .rechnungsgenerator
mv stammdaten.yaml .rechnungsgenerator/
```

Widersprüchliche Angaben brechen ab, statt als PDF hinauszugehen. Der Generator
verlangt mindestens eine Position und lehnt ab, wenn

- ein Leistungszeitraum endet, bevor er beginnt,
- das Rechnungsdatum vor dem Ende der Leistung liegt – außer bei der
  Abschlagsrechnung, deren Zeitraum eine Prognose ist,
- ein Einzelpreis, das Zahlungsziel oder der Gesamtbetrag negativ ist.

Zulässig, aber verdächtig sind eine Position mit Menge 0 und eine Rechnung, die
mehr als sechs Monate nach dem Ende der Leistung kommt (§14 Abs. 2 UStG). Dafür
gibt es einen Hinweis nach dem Ergebnis; die Rechnung entsteht trotzdem.

### Die Rechnungsnummer

Sie darf nicht leer sein und keinen Leerraum am Rand tragen: Schlussrechnung
und Storno verweisen später auf sie, und verglichen wird Zeichen für Zeichen –
ein unsichtbares Leerzeichen ließe den Verweis ins Leere gehen. Welche Nummern
darüber hinaus gültig sind, weiß nur der Rechnungssteller: UStAE 14.5 Abs. 10
erlaubt Ziffern und Buchstaben und verlangt keine lückenlose Abfolge. Die
Stammdaten dürfen deshalb ein Muster tragen, und ohne Eintrag wird die Form
nicht geprüft:

```yaml
rechnungsnummer-muster: 'R\d{2}-\d{3}'    # optional
```

Der Ausdruck muss die **ganze** Nummer überdecken; Anker (`^` und `$`) sind
erlaubt, aber überflüssig. Mehrere Nummernkreise (UStAE 14.5 Abs. 11) stehen als
Alternative darin: `(R|G)\d{2}-\d{3}`. Passt eine Nummer nicht, bricht der Lauf
ab – das Muster hat der Rechnungssteller selbst eingetragen.

Die Nummern der in einer Schlussrechnung abgesetzten Abschläge bleiben
ungeprüft: Sie stammen aus früheren Rechnungen, womöglich aus der Zeit vor einem
Wechsel des Musters. Die Einmaligkeit einer Nummer (§14 Abs. 4 Satz 1 Nr. 4
UStG) prüft der Generator nicht – beim Erzeugen sieht er eine einzige Datei.

### Rechnungsarten

`art` sagt, worüber abgerechnet wird. Ohne Angabe ist es die gewöhnliche
Rechnung, und bestehende Dateien bleiben unverändert gültig.

| `art` | Dokument | Besonderheit |
|---|---|---|
| `rechnung` | über eine erbrachte Leistung | Vorgabe |
| `abschlagsrechnung` | über einen Teil des Entgelts, bevor geleistet wurde | Zeitraum gilt als voraussichtlich, optional `vereinnahmt-am` |
| `schlussrechnung` | über die Gesamtleistung | setzt unter `abschlaege` die vereinnahmten Abschläge ab |
| `teilrechnung` | über eine abgeschlossene Teilleistung | wie eine Rechnung; in der E-Rechnung ein eigener Code (326) |
| `stornorechnung` | hebt eine ausgestellte Rechnung auf | nennt sie unter `storniert`, Positionen mit negativer Menge |

Der Unterschied ist nicht kosmetisch: Wer einen Abschlag wie eine gewöhnliche
Rechnung abrechnet und später noch einmal die Gesamtleistung, weist die
Umsatzsteuer zweimal aus und schuldet sie nach §14c Abs. 1 UStG auch zweimal.

Die Schlussrechnung trägt die Abschläge ausgeschrieben – der Generator schlägt
nichts im Verzeichnis nach, damit dieselbe Datei in einem Jahr dasselbe PDF
ergibt:

```yaml
art: schlussrechnung
positionen:
  - bezeichnung: Projekt Nord, Gesamtleistung
    menge: 1
    einheit: pauschal
    einzelpreis: 20000.00
abschlaege:
  - rechnungsnummer: R26-009
    rechnungsdatum: 2026-06-15
    vereinnahmt-am: 2026-06-10      # was nicht vereinnahmt ist, wird nicht abgesetzt
    steueranteile:                  # Liste, denn ein Abschlag darf gemischt sein
      - steuerkategorie: S
        ust-satz: 19
        netto: 4000.00
        ust: 760.00                 # Prüfsumme: muss zu netto × satz passen
```

Abtippen muss man den Block nicht: `rechnungsgenerator abschlaege` (siehe
Verwendung) bereitet ihn aus den Abschlagsrechnungen vor.

Im PDF stehen die Abschläge einzeln unter dem Gesamtbetrag, mit Netto und
Steuer getrennt, darunter der Restbetrag. Ist er null, steht dort ein Satz
statt einer Zahl, und Zahlungsziel und Bankverbindung entfallen – wer nichts zu
zahlen hat, bekommt kein Konto genannt. Die IBAN in der Fußzeile bleibt.

Abgeschriebene Zahlen übernimmt der Generator nicht stillschweigend. Er bricht
ab, wenn

- `abschlaege` ohne `art: schlussrechnung` steht – oder eine Schlussrechnung
  keine trägt,
- `vereinnahmt-am` fehlt oder nach dem Rechnungsdatum liegt,
- `steueranteile` leer sind,
- `ust` nicht zu `netto × ust-satz` passt,
- eine Kombination aus Steuerkategorie und Satz in der Rechnung gar nicht
  vorkommt,
- dieselbe Rechnungsnummer zweimal abgesetzt wird,
- ein Abschlag später datiert ist als die Schlussrechnung,
- oder mehr abgesetzt würde, als die Rechnung ausmacht.

Jede Meldung nennt den betroffenen Abschlag und was zu tun ist.

### Das Storno

Eine falsche Rechnung wird nicht überschrieben, sondern aufgehoben – mit einer
eigenen Nummer, dem Bezug auf die Ursprungsrechnung und denselben Positionen mit
negativer Menge:

```yaml
art: stornorechnung
nummer: R26-043
storniert:
  rechnungsnummer: R26-042
  rechnungsdatum: 2026-08-14
positionen:
  - bezeichnung: IT-Beratungsleistungen
    menge: -86.5                    # negativ wird die Menge, nie der Einzelpreis
    einheit: Std.
    einzelpreis: 95.00
```

Negativ wird die Menge, weil EN 16931 den negativen Einzelpreis verbietet
(BR-27): Er ließe offen, ob Gutschrift, Rabatt oder Erfassungsfehler gemeint
ist. Der Code lautet 384 und nicht 381 – die KoSIT führt 381 für die
kaufmännische Gutschrift ohne Bezug, 384 für Korrektur und Storno mit Bezug auf
die Ursprungsrechnung (ADR-0006).

Das PDF nennt die Ursprungsrechnung oben rechts und lässt Fälligkeit und
Bankverbindung weg: Ein Storno fordert kein Geld, es kündigt eine Erstattung an.
Eine Rechnung nur *teilweise* zu berichtigen, geht nicht – der Weg ist, ganz zu
stornieren und richtig neu zu stellen.

Ein Storno wirkt über seine eigene Datei hinaus, aber nur dort, wo jemand das
Ablageverzeichnis liest: `offene-abschlaege.py` nimmt eine stornierte
Abschlagsrechnung aus der Liste der offenen und gibt die Abschläge einer
stornierten Schlussrechnung wieder frei. Der Generator kann das nicht sehen –
er liest beim Erzeugen eine einzige Datei.

## Entwicklung

```
./gradlew build          # kompiliert, testet, lintet
./gradlew test           # nur Tests
./gradlew ktlintFormat   # Formatierung korrigieren
./gradlew installDist    # Programm nach build/install/rechnungsgenerator/bin/
```

Der Build ruft LuaLaTeX und veraPDF wirklich auf: Ein Integrationstest erzeugt
aus den Beispieldaten ein PDF und prüft es gegen PDF/A-1b, als E-Rechnung
gegen PDF/A-3u. Ohne gültiges PDF/A ist der Build rot.

## Stand

Layout und Toolchain stehen, in zwei Designs zur Wahl (`design: helsinki` oder
`prag` in den Stammdaten), mit Logo und bei `helsinki` einer eigenen
Akzentfarbe. Abgerechnet wird als Rechnung, Teilrechnung, Abschlags- und
Schlussrechnung oder Storno, in sieben Steuerkategorien samt Reverse Charge
(§13b UStG), mit Skonto und mit einem Rabatt auf eine Position – als Satz oder
als Betrag. Eine Rechnung, die über mehrere Seiten geht, setzt die Folgeseiten
mit eigenem Kopf.

Jede Rechnung steht auf Deutsch oder Englisch, britisch oder amerikanisch
(`sprache: en-GB`), und in Euro, US-Dollar oder Pfund, ebenso in PLN, CZK,
RON, SEK, NOK oder CHF (`waehrung: USD`). Wird in einer anderen Währung als
Euro Umsatzsteuer berechnet, nennt die Rechnung sie zusätzlich in Euro, zum
Kurs aus der Rechnungsdatei.

Jede Rechnungsart geht auch als E-Rechnung nach ZUGFeRD, und das XML einer
E-Rechnung lässt sich wieder auslesen, auch aus einer fremden.

Offen ist ein Nachlass auf die ganze Rechnung. Die Rechnungskorrektur – das
Berichtigen einzelner Positionen, statt ganz zu stornieren – ist erwogen und
mangels Anlass zurückgestellt (#64).

## Lizenz

Apache-2.0, siehe [LICENSE](LICENSE) und [NOTICE](NOTICE).

Die mitgelieferten Bibliotheken stehen unter eigenen Lizenzen – Apache-2.0,
MPL-2.0 (Saxon-HE, Rhino), BSD-3-Clause und CDDL mit Classpath-Exception; für
veraPDF ist von den beiden angebotenen Fassungen MPL-2.0 gewählt, weil GPLv3
auf das Ganze durchschlüge. Keine davon verlangt, den eigenen Quelltext
offenzulegen: Sie alle binden je Datei, und keine dieser Dateien ist hier
geändert.
