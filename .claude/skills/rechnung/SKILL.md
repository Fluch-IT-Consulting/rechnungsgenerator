---
name: rechnung
description: >
  Legt eine neue Rechnung an: ermittelt die nächste freie Rechnungsnummer, fragt
  die fehlenden Angaben ab, schreibt die YAML-Datei und erzeugt daraus ein auf
  PDF/A-1b geprüftes PDF. Nutze diesen Skill, sobald es darum geht, eine Rechnung
  zu schreiben, zu erstellen oder eine Leistung abzurechnen – auch dann, wenn nur
  ein Kunde und ein Zeitraum genannt werden ("mach mal die August-Rechnung
  fertig") und das Wort "Rechnung" gar nicht fällt. Ebenso bei Fragen nach der
  nächsten freien Rechnungsnummer oder wenn eine bestehende Rechnung als Vorlage
  für eine neue dienen soll.
---

# Rechnung anlegen

> Alle Beispiele in dieser Datei sind erfunden. Echte Kunden-, Projekt- oder
> Firmennamen gehören nicht hierher – dieses Repository kann öffentlich werden,
> und ein Beispiel ist schnell mit echten Daten gefüllt, ohne dass es auffällt.

## Wo die Dateien liegen

Das Ablageverzeichnis steht in der Umgebungsvariablen `RECHNUNGEN_DIR`. Sie ist
bewusst nicht im Skill hinterlegt, weil dieses Repository öffentlich werden kann.
Ist sie nicht gesetzt, frage nach dem Verzeichnis und weise darauf hin, dass es
in `.claude/settings.local.json` unter `env` gehört.

```
$RECHNUNGEN_DIR/
  stammdaten.yaml                     ← Firmendaten, gelten für jede Rechnung
  Outgoing/
    Rechnung_R26-011-beispiel.yaml    ← Quelle
    Rechnung_R26-011-beispiel.pdf     ← Ergebnis
    2025/ 2024/ …                     ← abgeschlossene Jahrgänge
```

Quelle und Ergebnis tragen denselben Namen und liegen nebeneinander – so wie
früher die `.ods` neben ihrem PDF lag.

## Ablauf

**1. Nummer ermitteln.** Nicht selbst zählen, sondern das Skript benutzen:

```
scripts/naechste-nummer.py "$RECHNUNGEN_DIR/Outgoing"
```

Es durchsucht auch die Jahresunterordner, damit eine versehentlich einsortierte
Rechnung keine doppelte Nummer verursacht.

**2. Angaben sammeln.** Nötig sind Kunde mit Anschrift, Leistungszeitraum und
die Positionen. Erfinde nichts davon – eine falsche Zahl auf einer Rechnung ist
teurer als eine Rückfrage.

Was du dir selbst beschaffen kannst, solltest du auch selbst beschaffen:

- **Kundenanschrift**: Steht in einer früheren Rechnung an denselben Kunden.
  Suche zuerst nach einer `.yaml` im Ablageverzeichnis; gibt es für den Kunden
  nur alte PDFs, hole die Anschrift mit `pdftotext -layout` daraus.
- **Rechnungsdatum**: heute, sofern nichts anderes gesagt wird.
- **Leistungszeitraum**: Wird ein Monat genannt ("August"), nimm den ganzen
  Monat. Frage nach, wenn gar kein Zeitraum genannt wurde.
- **Zahlungsziel**: kommt aus den Stammdaten, nur bei Abweichung angeben.

Nutze für die Rückfragen ein strukturiertes Frageformat, wenn es mehrere offene
Punkte gibt – das ist angenehmer als eine Kette von Einzelfragen.

**3. YAML schreiben** nach `$RECHNUNGEN_DIR/Outgoing/Rechnung_{Nummer}-{kunde}.yaml`.
Der Kundenteil ist ein kurzer Kleinbuchstaben-Name ohne Rechtsform, wie er in den
vorhandenen Dateien schon benutzt wird – etwa `beispiel` für die
"Beispiel Software GmbH", nicht `beispiel-software-gmbh`.

**4. PDF erzeugen.**

```
build/install/rechnungsgenerator/bin/rechnungsgenerator \
  "$RECHNUNGEN_DIR/Outgoing/Rechnung_R26-012-beispiel.yaml" \
  -s "$RECHNUNGEN_DIR/stammdaten.yaml" \
  -o "$RECHNUNGEN_DIR/Outgoing/Rechnung_R26-012-beispiel.pdf"
```

Fehlt das Programm, baue es mit `./gradlew installDist` im Projektverzeichnis.

Das Kommando gibt `PDF/A-1b: gültig` aus und endet mit Exitcode 0. Bei jedem
anderen Ergebnis ist die Rechnung **nicht** fertig: Melde den Fehler, statt ihn
zu übergehen – ein ungültiges PDF/A fällt sonst erst beim Finanzamt auf.

**5. Vorlegen.** Zeige das PDF (`SendUserFile` oder als Bild über `pdftoppm`)
und nenne Nummer, Kunde, Zeitraum und Gesamtbetrag in einem Satz. Der Nutzer
soll die Zahlen prüfen können, ohne die Datei zu öffnen.

## Format der Rechnungsdatei

```yaml
nummer: R26-012
datum: 2026-09-02
leistungszeitraum:
  von: 2026-08-01
  bis: 2026-08-31
kunde:
  name: Beispiel Software GmbH
  anschrift:
    strasse: Industriestraße 1
    plz: "90402"                    # in Anführungszeichen, sonst fällt die 0 weg
    ort: Nürnberg
  referenz: BST-2026-0815           # optional, Bestellnummer des Kunden
positionen:
  - bezeichnung: IT-Beratungsleistungen      # Pflicht: die Art der Leistung
    beschreibung: Arbeitspaket Nord 26.3     # optional: ihr Umfang
    hinweis: Abschlag September 2026         # optional: Abrechnungskontext
    menge: 86.5
    einheit: Std.                   # oder "pauschal", "Tage", …
    einzelpreis: 95.00
    ust-satz: 19                    # optional, Vorgabe 19
    positions-leistungszeitraum:    # optional, nur bei Abweichung
      von: 2026-09-01
      bis: 2026-09-30
zahlungsziel-tage: 30               # optional, sonst aus den Stammdaten
bemerkung: …                        # optional, ersetzt den Schlusssatz
```

Der Parser ist streng: Ein unbekannter Schlüssel bricht mit einer Meldung ab,
die den Tippfehler nennt. Das ist Absicht – eine stillschweigend ignorierte
Zeile wäre schlimmer.

## Die drei Textfelder einer Position

Sie bilden ab, was EN 16931 unterscheidet – und zwar danach, *worüber* der Text
spricht. Das kostet beim Schreiben nichts (nur `bezeichnung` ist Pflicht),
erspart aber später das Raten beim ZUGFeRD-Export.

| Feld | EN 16931 | Frage, die es beantwortet |
|---|---|---|
| `bezeichnung` | BT-153 | Wie heißt die Leistung? |
| `beschreibung` | BT-154 | Was umfasst sie? |
| `hinweis` | BT-127 | Was ist zur Abrechnung anzumerken? |

Im PDF stehen `beschreibung` und `hinweis` als zweite Zeile unter der
Bezeichnung, klein und gedämpft.

§14 Abs. 4 Nr. 5 UStG verlangt „Umfang und Art der sonstigen Leistung" – die
**Art** gehört in `bezeichnung`, der **Umfang** in `beschreibung`. Ein reines
Projektkürzel als Bezeichnung erfüllt das nicht; es sagt einem Prüfer nicht, was
geleistet wurde. Nennt der Nutzer nur ein Kürzel, ergänze die Art der Leistung
von dir aus und sage, dass du es getan hast.

## Was dieser Weg noch nicht kann

Sprich es an, wenn einer dieser Fälle auftaucht, statt etwas Falsches zu bauen:

- **Reverse Charge** (§13b UStG, Kunden außerhalb Deutschlands): Datenmodell
  kennt weder die USt-IdNr. des Kunden noch steuerfreie Positionen.
- **Skonto und andere Entgeltminderungen**: kein Feld vorhanden.
- **Abschlags- und Schlussrechnungen**: Abschläge lassen sich in der
  Schlussrechnung nicht absetzen.
