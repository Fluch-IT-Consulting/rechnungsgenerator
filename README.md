# Rechnungsgenerator

Erzeugt aus einer YAML-Beschreibung eine deutsche Rechnung als valides
PDF/A-1b-Dokument – gesetzt mit LuaLaTeX, geprüft mit veraPDF.

Ein Kotlin/Gradle-Lernprojekt.

## Voraussetzungen

Beide Werkzeuge müssen im PATH liegen; das Programm prüft das beim Start und
sagt, welches fehlt.

- TeX Live mit `lualatex` – <https://tug.org/texlive/>
- [veraPDF](https://verapdf.org/) – `brew install verapdf`
- Zum Bauen ein JDK 17+; Gradle lädt das Build-JDK bei Bedarf selbst.

Nur für den `rechnung`-Skill in `.claude/skills/`: Python 3 mit
[PyYAML](https://pyyaml.org/) – `pip3 install pyyaml`. Das Skript
`offene-abschlaege.py` liest damit die Rechnungsdateien, um die noch offenen
Abschlagsrechnungen eines Kunden zu finden. Fehlt PyYAML, sagt das Skript, wie
man es installiert. Der Generator selbst braucht kein Python.

## Verwendung

```
rechnungsgenerator erzeuge [OPTIONEN] <rechnung.yaml>

  -s, --stammdaten=<pfad>    Stammdaten des Rechnungsstellers
                             (Vorgabe: stammdaten.yaml)
  -o, --ausgabe=<pfad>       Zieldatei
                             (Vorgabe: aus der Rechnungsnummer gebildet)
  --ueberschreiben           Eine vorhandene Zieldatei ersetzen
  --arbeitsdateien-behalten  Das .tex und die LaTeX-Hilfsdateien nicht löschen
  --oeffnen                  Die fertige Rechnung im PDF-Viewer des Systems öffnen
```

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
./gradlew installDist
./build/install/rechnungsgenerator/bin/rechnungsgenerator erzeuge rechnung.yaml
```

Ausgabe:

```
Rechnung-R26-042.pdf
PDF/A-1b: gültig
```

Der Exitcode ist 0 nur bei Erfolg. Ein Tippfehler in der YAML, ein LaTeX-Fehler
oder ein PDF, das die PDF/A-Prüfung nicht besteht, führen zu Exitcode 1 –
ebenso ein Aufruf ohne Unterkommando, der nur die Hilfe zeigt. Die
Fehlermeldung steht dann auf stderr; stdout trägt nur das Ergebnis und lässt
sich gefahrlos umleiten.

### Abschläge für die Schlussrechnung vorbereiten

```
rechnungsgenerator abschlaege [OPTIONEN] <abschlagsrechnung.yaml>...

  -s, --stammdaten=<pfad>    Stammdaten des Rechnungsstellers
                             (Vorgabe: stammdaten.yaml)
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

## Eingabedateien

Zwei YAML-Dateien: die Stammdaten (auf jeder Rechnung gleich) und die Rechnung
selbst. Beispiele liegen in [src/test/resources](src/test/resources).

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

Sie darf nicht leer sein und keinen Leerraum am Rand tragen – aus ihr entsteht
auch der Dateiname des PDF. Welche Nummern darüber hinaus gültig sind, weiß nur
der Rechnungssteller: UStAE 14.5 Abs. 10 erlaubt Ziffern und Buchstaben und
verlangt keine lückenlose Abfolge. Die Stammdaten dürfen deshalb ein Muster
tragen, und ohne Eintrag wird die Form nicht geprüft:

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
| `teilrechnung` | über eine abgeschlossene Teilleistung | wie eine Rechnung; eigener Code fürs spätere ZUGFeRD |
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
```

Der Build ruft LuaLaTeX und veraPDF wirklich auf: Ein Integrationstest erzeugt
aus den Beispieldaten ein PDF und prüft es gegen PDF/A-1b. Ohne gültiges PDF/A
ist der Build rot.

## Stand

Layout und Toolchain stehen, ebenso Reverse Charge (§13b UStG), die Abschlags-
und Schlussrechnung, das Storno, das Skonto und der Rabatt auf eine Position –
als Satz oder als Betrag. Offen sind ein Nachlass auf die ganze Rechnung, die
Rechnungskorrektur – also das Berichtigen einzelner Positionen, statt ganz zu
stornieren – und später ZUGFeRD.
