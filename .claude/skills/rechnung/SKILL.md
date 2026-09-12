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
  für eine neue dienen soll. Ebenso für Abschlagsrechnungen ("Abschlag auf
  Projekt X") und Schlussrechnungen, in denen bereits vereinnahmte Abschläge
  abgesetzt werden.
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

Bei einer **Schlussrechnung** lies vor Schritt 2
[`schlussrechnung.md`](schlussrechnung.md): Dort steht, wie die Abschläge in
die Datei kommen. Die Schritte hier gelten trotzdem.

**1. Nummer ermitteln.** Nicht selbst zählen, sondern das Skript benutzen:

```
scripts/naechste-nummer.py "$RECHNUNGEN_DIR/Outgoing" \
  --stammdaten "$RECHNUNGEN_DIR/stammdaten.yaml"
```

Es durchsucht auch die Jahresunterordner, damit eine versehentlich einsortierte
Rechnung keine doppelte Nummer verursacht. Abschlags- und Schlussrechnungen
zählen in derselben Reihe wie alle anderen – ein zweiter Nummernkreis
verdoppelte nur die Gelegenheiten für Lücken und Dubletten.

Steht das Rechnungsdatum schon fest und ist es nicht heute – etwa weil die
Rechnung noch ins alte Jahr gehört –, gib es mit: `--rechnungsdatum 2025-12-31`.
Die Nummer gehört in den Nummernkreis des Jahres, in dem die Rechnung datiert
ist, und ohne Angabe rechnet das Skript mit heute.

Das Schema `R{JJ}-{NNN}` steckt weiter im Skript, und das lässt sich nicht
auflösen: Ein regulärer Ausdruck prüft, er zählt nicht – er sagt nicht, welcher
Teil einer Nummer der Zähler ist. `--stammdaten` prüft aber die Nummer, die das
Skript ausgeben will, gegen `rechnungsnummer-muster` (ADR-0005). Wer das Muster
wechselt und das Skript vergisst, bekommt hier einen Abbruch – statt später eine
Rechnung, die der Generator ablehnt, wenn die Nummer schon in Dateinamen steht.

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
"Beispiel Software GmbH", nicht `beispiel-software-gmbh`. Bei Abschlags- und
Schlussrechnungen darf ein Suffix folgen (`Rechnung_R26-014-beispiel-schluss.yaml`),
reine Ablage-Kosmetik.

**4. PDF erzeugen.**

```
build/install/rechnungsgenerator/bin/rechnungsgenerator erzeuge \
  "$RECHNUNGEN_DIR/Outgoing/Rechnung_R26-012-beispiel.yaml" \
  -s "$RECHNUNGEN_DIR/stammdaten.yaml" \
  -o "$RECHNUNGEN_DIR/Outgoing/Rechnung_R26-012-beispiel.pdf"
```

Fehlt das Programm, baue es mit `./gradlew installDist` im Projektverzeichnis.
Dasselbe, wenn es `got unexpected extra argument` meldet und dazu, eine Datei
`erzeuge` gebe es nicht: Dann ist das installierte Programm älter als diese
Anleitung und kennt das Unterkommando noch nicht.

Das Kommando gibt `PDF/A-1b: gültig` aus und endet mit Exitcode 0. Bei jedem
anderen Ergebnis ist die Rechnung **nicht** fertig: Melde den Fehler, statt ihn
zu übergehen – ein ungültiges PDF/A fällt sonst erst beim Finanzamt auf.

**5. Vorlegen.** Zeige das PDF (`SendUserFile` oder als Bild über `pdftoppm`)
und nenne Nummer, Kunde, Zeitraum und Gesamtbetrag in einem Satz. Der Nutzer
soll die Zahlen prüfen können, ohne die Datei zu öffnen.

## Format der Rechnungsdatei

```yaml
nummer: R26-012
art: rechnung                       # optional, Vorgabe: rechnung
datum: 2026-09-02
leistungszeitraum:
  von: 2026-08-01
  bis: 2026-08-31
kunde:
  name: Beispiel Software GmbH
  anschrift:
    strasse: Industriestraße 1
    plz: "90402"                    # Text, keine Zahl – führende Nullen bleiben
    ort: Nürnberg
  referenz: BST-2026-0815           # optional, Bestellnummer des Kunden
positionen:
  - bezeichnung: IT-Beratungsleistungen      # Pflicht: die Art der Leistung
    beschreibung: Arbeitspaket Nord 26.3     # optional: ihr Umfang
    hinweis: Nachtrag zu Bestellung 4711     # optional: Abrechnungskontext
    menge: 86.5
    einheit: Std.                   # geschlossene Liste, siehe unten
    einzelpreis: 95.00
    ust-satz: 19                    # optional, Vorgabe 19
    steuerkategorie: S              # optional, Vorgabe S (Regelsatz)
    positions-leistungszeitraum:    # optional, nur bei Abweichung
      von: 2026-07-01
      bis: 2026-07-31
zahlungsziel-tage: 30               # optional, sonst aus den Stammdaten
bemerkung: …                        # optional, ersetzt den Schlusssatz
```

Der Parser ist streng: Ein unbekannter Schlüssel bricht mit einer Meldung ab,
die den Tippfehler nennt. Das ist Absicht – eine stillschweigend ignorierte
Zeile wäre schlimmer. Dasselbe gilt für die geprüften Felder unten.

**Zulässige Einheiten** – EN 16931 verlangt für BT-130 einen Code aus UN/ECE
Rec. 20, deshalb ist die Liste geschlossen. Eine unbekannte Einheit bricht ab
und nennt die zulässigen:

`Std.` · `Tage` · `Wochen` · `Monate` · `Stück` · `pauschal` · `km`

**Geprüfte Kennungen in den Stammdaten**: IBAN (Prüfsumme mod 97), BIC (ISO
9362), USt-IdNr. (Länderpräfix, bei deutschen Nummern auch die Prüfziffer) und
Steuernummer (Struktur). Schreibweise mit oder ohne Leerzeichen ist egal – auf
der Rechnung erscheint immer die gruppierte Form.

## Abschlagsrechnung und Schlussrechnung

`art` sagt, worüber abgerechnet wird: `rechnung` (Vorgabe), `abschlagsrechnung`,
`schlussrechnung` oder `teilrechnung`. Der Unterschied ist keine Formsache. Wer
einen Abschlag wie eine gewöhnliche Rechnung abrechnet und später noch einmal
die Gesamtleistung, weist die Umsatzsteuer zweimal aus und schuldet sie nach
§14c Abs. 1 UStG auch zweimal.

**Abschlagsrechnung** – die Leistung ist noch nicht erbracht:

```yaml
nummer: R26-009
art: abschlagsrechnung
datum: 2026-06-15
leistungszeitraum:                  # der voraussichtliche; Monate genügen
  von: 2026-06-01
  bis: 2026-10-31
vereinnahmt-am: 2026-06-10          # nur wenn das Geld schon da ist
kunde: …
positionen:
  - bezeichnung: Abschlag auf Projekt Nord
    beschreibung: 1. von 3 Abschlägen (30 % des Auftragswerts)
    menge: 1
    einheit: pauschal
    einzelpreis: 4000.00
```

Frage den Leistungszeitraum als *voraussichtlichen* ab; der Kalendermonat
genügt. Beim Vorlegen sagst du dazu, dass zu diesem Vorgang später eine
Schlussrechnung gehört, die den Abschlag absetzt, sobald er vereinnahmt ist.

Das PDF beschriftet den Zeitraum als voraussichtlichen und trägt den Pflichtsatz,
dass die Leistung noch aussteht und eine Schlussrechnung folgt. Prozentsätze
kennt das Format nicht: "30 % von 40.000 €" rechnest du beim Anlegen einmal aus
und schreibst den Betrag hin; woher er stammt, gehört in `beschreibung`.

`vereinnahmt-am` nur angeben, wenn der Zeitpunkt feststeht – und nur auf einer
Abschlagsrechnung. Steht er nicht fest, weglassen; **nicht schätzen**. Auf einer
anderen Rechnungsart bricht der Generator ab (dort entsteht die Steuer mit der
Leistung, nicht mit dem Geldeingang), ebenso bei einem Datum nach dem
Rechnungsdatum – dann ist der Betrag für diese Rechnung nicht vereinnahmt.

**Schlussrechnung** – die Gesamtleistung, abzüglich der vereinnahmten
Abschläge: Ablauf, Format und Abbruchregeln stehen in
[`schlussrechnung.md`](schlussrechnung.md).

## Stornorechnung

Eine falsche Rechnung wird nicht überschrieben, sondern aufgehoben: `art:
stornorechnung` mit einer **eigenen** Nummer aus derselben Reihe, dem Bezug auf
die Ursprungsrechnung und denselben Positionen mit **negativer Menge**.

```yaml
nummer: R26-043
art: stornorechnung
datum: 2026-08-21
storniert:                          # Pflicht: welche Rechnung aufgehoben wird
  rechnungsnummer: R26-042
  rechnungsdatum: 2026-08-14
leistungszeitraum:                  # derselbe wie auf der Ursprungsrechnung
  von: 2026-07-01
  bis: 2026-07-31
kunde: …
positionen:
  - bezeichnung: IT-Beratungsleistungen
    beschreibung: Rahmenvertrag vom 01.02.2026
    menge: -86.5                    # negativ wird die Menge, nie der Einzelpreis
    einheit: Std.
    einzelpreis: 95.00
```

Übernimm die Positionen der Ursprungsrechnung unverändert und setze nur vor die
Menge ein Minus. Der Einzelpreis bleibt positiv: EN 16931 verbietet den
negativen Einzelpreis (BR-27), weil er offenließe, ob Gutschrift, Rabatt oder
Erfassungsfehler gemeint ist. Ein Storno über einen positiven Gesamtbetrag
bricht ab.

Das PDF nennt die Ursprungsrechnung oben rechts, trägt den Pflichtsatz, dass sie
vollständig aufgehoben ist, und lässt Fälligkeit und Bankverbindung weg – ein
Storno fordert kein Geld, es kündigt eine Erstattung an.

**Nur ganz, nicht teilweise.** Soll eine Rechnung bloß in einem Punkt berichtigt
werden, ist das eine Rechnungskorrektur, und die gibt es hier nicht. Der übliche
Weg ist: ganz stornieren und richtig neu stellen. Sprich es an, wenn das zu grob
erscheint.

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

## Steuerkategorien

Jede Position trägt eine Kategorie (EN 16931 BT-151); ohne Angabe `S`. Der
ermäßigte Satz von 7 % ist ebenfalls `S` – die Kategorie sagt nicht, wie hoch
besteuert wird, sondern ob.

| Code | Bedeutung | verlangt |
|---|---|---|
| `S` | Regelsatz | Satz > 0 |
| `Z` | Nullsatz | Satz 0 |
| `E` | steuerbefreit | Satz 0 **und** `steuerbefreiungsgrund` |
| `AE` | Steuerschuldnerschaft des Leistungsempfängers | Satz 0, USt-IdNr. des Kunden |
| `K` | innergemeinschaftliche Lieferung | Satz 0, USt-IdNr. des Kunden |
| `G` | Ausfuhrlieferung | Satz 0 |
| `O` | nicht steuerbar | Satz 0 **und** `steuerbefreiungsgrund` |

Bei `AE`, `K` und `G` setzt der Generator den vorgeschriebenen Wortlaut selbst
ein; bei `E` und `O` hängt er vom Befreiungstatbestand ab und muss angegeben
werden. Die Regeln werden beim Rechnen geprüft – eine widersprüchliche Rechnung
entsteht gar nicht erst als PDF.

## Bestätigungsabfrage vor einer Rechnung ohne Umsatzsteuer

Bevor ohne Umsatzsteuer abgerechnet wird, muss die USt-IdNr. des Kunden geprüft
und die Prüfung dokumentiert sein. Der Generator bricht sonst ab – bei
Kategorie `K` immer, bei `AE` dann, wenn der Kunde in einem *anderen*
EU-Mitgliedstaat sitzt:

```yaml
kunde:
  name: Beispiel Software GmbH
  anschrift: {strasse: Ringstraße 1, plz: "1010", ort: Wien, land: AT}
  umsatzsteuer-id: ATU12345678
  ust-id-geprueft:
    am: 2026-08-28
    verfahren: qualifiziert          # oder: einfach
    nachweis: BZSt-Protokoll 2026-08-28-4711
```

Wenn der Nachweis fehlt, **erfinde ihn nicht**. Sag dem Nutzer, dass die Abfrage
beim BZSt oder über VIES ansteht, und warte auf Datum und Protokollnummer. Ein
ausgedachter Nachweis wäre schlimmer als gar keine Rechnung.

Bei einer inländischen Steuerschuldumkehr (§13b Abs. 2, etwa Bauleistungen)
verlangt der Generator nichts: Das BZSt bestätigt ausländische Nummern, für die
eines deutschen Kunden gibt es diese Abfrage nicht.

Bei Kunden außerhalb der EU (Schweiz, UK, …) verlangt der Generator ebenfalls nichts –
VIES deckt nur Mitgliedstaaten ab. Er gibt dann einen Hinweis aus, dass für die
Akte ein anderer Nachweis der Unternehmereigenschaft nötig ist. Gib diesen
Hinweis weiter.

## Was dieser Weg noch nicht kann

Sprich es an, wenn einer dieser Fälle auftaucht, statt etwas Falsches zu bauen:

- **Skonto und andere Entgeltminderungen**: kein Feld vorhanden.
- **Rechnungskorrektur**: Eine Rechnung teilweise berichtigen, statt sie ganz
  zu stornieren, geht nicht. Das Storno gibt es (siehe oben), die Korrektur
  nicht.
- **Überzahlung einer Schlussrechnung**: Ein negativer Restbetrag bricht ab,
  statt eine Erstattung auszuweisen. Zurückgezahlt wird außerhalb des
  Generators, oder der zu hohe Abschlag wird storniert.
- **Teilvereinnahmung**: Kam auf einen Abschlag nur ein Teil des Geldes, stimmen
  die Steueranteile im Block des Generators nicht; er gilt nur für den vollen
  Betrag.
- **Gegenprüfen**: Ob der Block einer bestehenden Schlussrechnung noch zu den
  Abschlagsrechnungen passt, prüft bisher niemand. Beide Fälle sind erwogen,
  aber vertagt, siehe `docs/notizen/2026-09-10-datenherkunft-der-schlussrechnung.md`.
