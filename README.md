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

## Verwendung

```
rechnungsgenerator erzeuge [OPTIONEN] <rechnung.yaml>

  -s, --stammdaten=<pfad>    Stammdaten des Rechnungsstellers
                             (Vorgabe: stammdaten.yaml)
  -o, --ausgabe=<pfad>       Zieldatei
                             (Vorgabe: aus der Rechnungsnummer gebildet)
  --arbeitsdateien-behalten  Das .tex und die LaTeX-Hilfsdateien nicht löschen
```

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
ebenso ein Aufruf ohne Unterkommando, der nur die Hilfe zeigt.

## Eingabedateien

Zwei YAML-Dateien: die Stammdaten (auf jeder Rechnung gleich) und die Rechnung
selbst. Beispiele liegen in [src/test/resources](src/test/resources).

### Rechnungsarten

`art` sagt, worüber abgerechnet wird. Ohne Angabe ist es die gewöhnliche
Rechnung, und bestehende Dateien bleiben unverändert gültig.

| `art` | Dokument | Besonderheit |
|---|---|---|
| `rechnung` | über eine erbrachte Leistung | Vorgabe |
| `abschlagsrechnung` | über einen Teil des Entgelts, bevor geleistet wurde | Zeitraum gilt als voraussichtlich, optional `vereinnahmt-am` |
| `schlussrechnung` | über die Gesamtleistung | setzt unter `abschlaege` die vereinnahmten Abschläge ab |
| `teilrechnung` | über eine abgeschlossene Teilleistung | wie eine Rechnung; eigener Code fürs spätere ZUGFeRD |

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

Im PDF stehen die Abschläge einzeln unter dem Gesamtbetrag, mit Netto und
Steuer getrennt, darunter der Restbetrag. Ist er null, steht dort ein Satz
statt einer Zahl.

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
- oder mehr abgesetzt würde, als die Rechnung ausmacht.

Jede Meldung nennt den betroffenen Abschlag und was zu tun ist.

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

Layout und Toolchain stehen, ebenso Reverse Charge (§13b UStG) und die
Abschlags- und Schlussrechnung. Offen sind Entgeltminderungen (Skonto),
Gutschrift und Storno – und damit der Fall, dass zu viel gezahlt wurde – sowie
später ZUGFeRD.
