# Rechnungsgenerator

Erzeugt aus einer YAML-Beschreibung eine deutsche Rechnung als valides
PDF/A-1b-Dokument – gesetzt mit LuaLaTeX, geprüft mit veraPDF. Auf Wunsch
entsteht eine E-Rechnung nach ZUGFeRD: ein PDF/A-3u, das die Rechnung
zusätzlich als XML trägt. Aus einer E-Rechnung, auch einer fremden, liest das
Programm das XML wieder aus, sobald sie ihre Prüfung besteht.

Abgerechnet wird als Rechnung, Teilrechnung, Abschlags- und Schlussrechnung oder
Storno, in sieben Steuerkategorien samt Reverse Charge, mit Rabatt und Skonto,
auf Deutsch oder Englisch und in neun Währungen. Zwei Designs stehen zur Wahl,
mit eigenem Logo.

Geschrieben in Kotlin, gebaut mit Gradle. Die ausführliche Anleitung steht im
**[Wiki](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki)**.

## Installation

Über Homebrew, aus dem Tap
[Fluch-IT-Consulting/homebrew-tap](https://github.com/Fluch-IT-Consulting/homebrew-tap):

```bash
brew tap fluch-it-consulting/tap
brew install fluch-it-consulting/tap/rechnungsgenerator
```

Eine neue Fassung kommt mit `brew upgrade`.

Daneben braucht das Programm im PATH:

- ein JDK 21 oder neuer, etwa `brew install openjdk`. Fertig führt Homebrew
  das nur für Apple Silicon ab macOS 15; auf Intel-Macs und auf macOS 14 baut
  es openjdk aus dem Quelltext, und dort ist ein fertiges JDK, etwa von
  [Temurin](https://adoptium.net/), der schnellere Weg.
- TeX Live mit `lualatex`, etwa `brew install --cask mactex-no-gui`. Das
  Programm prüft das beim Start und sagt es, wenn es fehlt.
- nur für `--externe-pdfa-pruefung`: [veraPDF](https://verapdf.org/) –
  `brew install verapdf`. Ohne den Schalter prüft eine eingebaute Fassung
  derselben Bibliothek (ADR-0014).

## Die erste Rechnung

Die Stammdaten des Rechnungsstellers – Firma, Anschrift, USt-IdNr.,
Bankverbindung – liegen in einer **Wurzel**, einem Verzeichnis mit dem Ordner
`.rechnungsgenerator/`. Angelegt wird sie mit einer Vorlage zum Ausfüllen:

```
cd ~/Rechnungen
rechnungsgenerator einrichten
```

Dann je Rechnung eine YAML-Datei irgendwo unterhalb der Wurzel, etwa
`~/Rechnungen/2026/R26-042.yaml`:

```yaml
nummer: R26-042
datum: 2026-08-14
leistungszeitraum:
  von: 2026-07-01
  bis: 2026-07-31
kunde:
  name: Beispiel Software GmbH
  anschrift:
    strasse: Industriestraße 1
    plz: "90402"
    ort: Nürnberg
positionen:
  - bezeichnung: IT-Beratungsleistungen
    menge: 86.5
    einheit: Std.
    einzelpreis: 95.00
```

```
rechnungsgenerator erzeuge ~/Rechnungen/2026/R26-042.yaml
```

```
/Users/erika/Rechnungen/2026/R26-042.pdf
PDF/A-1b: gültig
```

Das PDF entsteht neben der Rechnungsdatei. Summen, Umsatzsteuer und
Fälligkeit rechnet das Programm; Widersprüche in der Datei melden sich alle auf
einmal, und dann entsteht keine Rechnung. Alle Beispiele hier und im Wiki sind
erfunden.

## Kommandos

| Kommando | Was es tut | Im Wiki |
|---|---|---|
| `einrichten` | legt die Wurzel mit einer Vorlage der Stammdaten an | [Einrichten](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki/Einrichten) |
| `erzeuge` | setzt eine Rechnungsdatei als PDF/A-1b, mit `--zugferd` als E-Rechnung | [Die Rechnungsdatei](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki/Die-Rechnungsdatei), [E-Rechnung](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki/E-Rechnung) |
| `abschlaege` | schreibt aus Abschlagsrechnungen den Block für die Schlussrechnung | [Rechnungsarten](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki/Rechnungsarten#schlussrechnung) |
| `xml` | prüft eine E-Rechnung und gibt ihr XML aus | [E-Rechnung](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki/E-Rechnung#das-xml-auslesen) |

stdout trägt nur das Ergebnis, Meldungen stehen auf stderr; der Exitcode ist 0
nur bei Erfolg. `rechnungsgenerator <kommando> --help` nennt die Optionen,
`rechnungsgenerator --version` die Fassung.

Weiter im Wiki: [Steuern](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki/Steuern),
[Sprachen und Währungen](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki/Sprachen-und-W%C3%A4hrungen),
[Designs](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki/Designs),
[Meldungen](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki/Meldungen)
und [Mit Claude Code](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki/Mit-Claude-Code),
der Skill `rechnung`, der Rechnungen im Gespräch anlegt.

## Stand

Die Fassung steht noch auf 0.x: Die Schlüssel der YAML-Dateien sind die
Schnittstelle des Programms, und sie sind nicht fertig. Offen ist ein Nachlass
auf die ganze Rechnung. Die Rechnungskorrektur – das Berichtigen einzelner
Positionen, statt ganz zu stornieren – ist erwogen und mangels Anlass
zurückgestellt (#64).

## Entwicklung

```
./gradlew build                   # kompiliert, testet, lintet
./gradlew build -PohneToolchain   # ohne die Tests mit LuaLaTeX; so baut die CI
./gradlew test                    # nur Tests
./gradlew ktlintFormat            # Formatierung korrigieren
./gradlew installDist             # Programm nach build/install/rechnungsgenerator/bin/
```

Zum Bauen genügt ein JDK 17+; Gradle lädt das Build-JDK bei Bedarf selbst. Der
volle Build setzt echte PDFs und braucht neben TeX Live `pdftotext` aus poppler
(`brew install poppler`). Python 3 mit PyYAML (`pip3 install pyyaml`) braucht
jeder Build, auch der mit `-PohneToolchain`: Tests rufen die Skripte des
`rechnung`-Skills auf. Ohne gültiges PDF/A ist der Build rot.

Wie der Code aufgebaut ist, steht im Wiki unter
[Architektur](https://github.com/Fluch-IT-Consulting/rechnungsgenerator/wiki/Architektur),
warum er so ist in den [ADRs](docs/adr), und was ein Begriff bedeutet im
Glossar [CONTEXT.md](CONTEXT.md).

## Lizenz

Apache-2.0, siehe [LICENSE](LICENSE) und [NOTICE](NOTICE).

Der Generator ist eine Anwendung der Norm EN 16931-1 und der Technischen
Spezifikation CEN/TS 16931-2. Er gibt Informationen aus diesen
Veröffentlichungen wieder, darunter ihre Kennungen und ihr Datenmodell, mit
Erlaubnis von CEN und DIN als Inhabern des Copyrights; siehe die
[Nutzungsrechte für den ZUGFeRD-Standard](https://www.ferd-net.de/ueber-uns/ressourcen-1/veroeffentlichungen/haftungsausschluss-und-nutzungsrechte)
und [NOTICE](NOTICE). Den Text der ZUGFeRD-Spezifikation gibt das Repository
nicht wieder; ADRs und Issues verweisen auf Kapitel und Regelkennungen.

Die mitgelieferten Bibliotheken stehen unter eigenen Lizenzen – Apache-2.0,
MPL-2.0 (Saxon-HE, Rhino), BSD-3-Clause und CDDL mit Classpath-Exception; für
veraPDF ist von den beiden angebotenen Fassungen MPL-2.0 gewählt, weil GPLv3
auf das Ganze durchschlüge. Keine davon verlangt, den eigenen Quelltext
offenzulegen: Sie alle binden je Datei, und keine dieser Dateien ist hier
geändert.
