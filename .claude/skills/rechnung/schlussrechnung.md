# Schlussrechnung anlegen

> Alle Beispiele in dieser Datei sind erfunden, wie in `SKILL.md`.

Die Schlussrechnung rechnet die vollständig erbrachte Leistung ab und setzt die
vereinnahmten Abschläge ab. Nummer, Angaben, PDF und Vorlegen laufen wie in
`SKILL.md`; diese Datei regelt, wie die Abschläge in die Datei kommen.

Die Arbeitsteilung steht in ADR-0003: Du suchst und fragst, der Generator
rechnet und schreibt. Jeder Betrag im `abschlaege`-Block kommt aus
`rechnungsgenerator abschlaege` – das Abschreiben, die Fehlerquelle, bleibt
ganz beim Programm.

## Ablauf

**1. Offene Abschlagsrechnungen finden.**

```
scripts/offene-abschlaege.py "$RECHNUNGEN_DIR/Outgoing" "Beispiel Software GmbH"
```

Den Kundennamen genau so übergeben, wie er in den Rechnungen unter `kunde:` →
`name:` steht. Das Skript listet je offener Abschlagsrechnung Nummer, Datum und
Datei. Was es auf stderr meldet, gibst du weiter: Eine Liste anderer Kunden
heißt meist, dass der Name abweicht; eine übersprungene Datei kann genau die
gesuchte sein. Fertig, wenn die Liste steht – oder feststeht, dass nichts offen
ist. Dann ist es keine Schlussrechnung, sondern eine Rechnung (`art: rechnung`).

Nach Stornos musst du nicht selbst suchen, das Skript wertet sie aus: Eine
stornierte Abschlagsrechnung steht nicht in der Liste, und die Abschläge einer
stornierten Schlussrechnung stehen wieder darin. Beides vermerkt es auf stderr,
und beides gibst du weiter – ein Abschlag, der fehlt oder unerwartet auftaucht,
gehört erklärt. Eine Zeile, die mit **Achtung** beginnt, hältst du an: Da setzt
eine gültige Schlussrechnung einen stornierten Abschlag ab, und das ist ein
Fall für den Nutzer, nicht für die neue Rechnung.

**2. Vorlegen und fragen, welche abgesetzt werden.** Die Beträge liefert der
Generator: Lass ihn über alle gefundenen Dateien laufen (Aufruf wie in
Schritt 3, ohne `>>`) und lege je Abschlag Nummer, Datum, Netto und
Umsatzsteuer vor, so wie sie in seiner Ausgabe stehen. Frage, welche zu diesem
Vorgang gehören – ob zwei Vorgänge desselben Kunden zusammengehören, weiß nur
der Nutzer. Fertig, wenn er die Nummern genannt hat.

**3. Block anhängen.** Die Schlussrechnung zuerst ohne `abschlaege` schreiben
(Schritt 3 in `SKILL.md`). Ihr Rechnungsdatum liegt nicht vor dem Ende des
Leistungszeitraums, denn sie rechnet die erbrachte Leistung ab; sonst bricht
der Generator ab. Widersprechen sich die beiden, frag nach, was gilt, statt eins
davon anzupassen. Dann den Block des Generators anhängen, die ausgewählten
Dateien in der Reihenfolge ihrer Nummern:

```
build/install/rechnungsgenerator/bin/rechnungsgenerator abschlaege \
  "$RECHNUNGEN_DIR/Outgoing/Rechnung_R26-009-beispiel.yaml" \
  "$RECHNUNGEN_DIR/Outgoing/2025/Rechnung_R26-011-beispiel.yaml" \
  -s "$RECHNUNGEN_DIR/stammdaten.yaml" \
  >> "$RECHNUNGEN_DIR/Outgoing/Rechnung_R26-014-beispiel-schluss.yaml"
```

Den Block übernimmst du, wie er kommt. Bei einem Fehler endet das Kommando mit
einem Exitcode ungleich 0 und schreibt nichts in die Datei; gib die Meldung
weiter. Fertig, wenn der Block in der Datei steht.

**4. Die leeren `vereinnahmt-am` füllen.** Wo die Abschlagsrechnung keinen
Zeitpunkt der Vereinnahmung trug, steht im Block

```
    vereinnahmt-am:                 # Datum der Vereinnahmung eintragen
```

Frage je Abschlag, wann er vereinnahmt wurde, und ersetze die ganze Zeile durch
`vereinnahmt-am: JJJJ-MM-TT` – jeweils die Zeile unter seiner
`rechnungsnummer`, denn die leeren Zeilen gleichen sich aufs Zeichen.
Abgesetzt wird nur, was vereinnahmt ist (§14 Abs. 5 Satz 2 UStG):

- **Nicht vereinnahmt**: Den Abschlag aus dem Block streichen – er wird gemahnt
  oder storniert. Sag das, statt ein Datum anzunehmen.
- **Nur teilweise vereinnahmt**: Der Block gilt nur für den vollen Betrag.
  Halte an und besprich den Fall; der Generator deckt ihn noch nicht ab.

Fertig, wenn kein leeres `vereinnahmt-am` mehr im Block steht.

**5. PDF erzeugen und vorlegen** wie in `SKILL.md`. Beim Vorlegen nennst du
neben dem Gesamtbetrag die abgesetzten Abschläge und den **Restbetrag** – die
Zahl, die der Kunde überweist. Alles liest du aus dem PDF (`pdftotext -layout`)
und nennst es so, wie es dort steht: die Abschläge einzeln oder als „drei
Abschläge zu je 4.760,00 €“. Eine Summenzeile der Abschläge führt das PDF
nicht. Deckt die Absetzung den Gesamtbetrag ganz, steht dort statt eines
Restbetrags ein Satz, und das Zahlungsziel entfällt.

**Ein Abschlag, der nur als PDF vorliegt**: Nennt der Nutzer einen Abschlag, den
das Skript nicht findet – etwa aus dem Altverfahren –, kann der Generator ihn
nicht vorbereiten. Den Eintrag schreibt dann der Nutzer von Hand nach dem
Format unten; die Prüfsumme `netto × ust-satz = ust` fängt Zahlendreher.

## Format

```yaml
nummer: R26-014
art: schlussrechnung
datum: 2026-11-05
leistungszeitraum:
  von: 2026-06-01
  bis: 2026-10-31
kunde: …
positionen:
  - bezeichnung: Projekt Nord, Gesamtleistung
    menge: 1
    einheit: pauschal
    einzelpreis: 20000.00
abschlaege:                         # von rechnungsgenerator abschlaege
  - rechnungsnummer: R26-009
    rechnungsdatum: 2026-06-15
    vereinnahmt-am: 2026-06-10
    steueranteile:                  # Liste: ein Abschlag darf gemischt sein
      - steuerkategorie: S
        ust-satz: 19
        netto: 4000.00
        ust: 760.00
```

Die Werte stehen ausgeschrieben in der Datei; beim Erzeugen des PDF schlägt der
Generator nichts im Verzeichnis nach, damit dieselbe Datei in einem Jahr
dasselbe PDF ergibt (ADR-0001).

Der Generator bricht ab, wenn – und nennt dabei alle zutreffenden Gründe auf
einmal, nicht nur den ersten:

| Fall | Warum |
|---|---|
| `abschlaege` ohne `art: schlussrechnung` | Sobald abgesetzt wird, *ist* es eine Schlussrechnung |
| `art: schlussrechnung` ohne `abschlaege` | Dann fehlt die Absetzung – oder es ist keine Schlussrechnung |
| `vereinnahmt-am` fehlt | Abgesetzt wird, was vereinnahmt wurde (§14 Abs. 5 Satz 2 UStG) |
| `vereinnahmt-am` nach dem Rechnungsdatum | Für diese Rechnung ist der Abschlag nicht vereinnahmt |
| `ust` passt nicht zu `netto × ust-satz` | Prüfsumme gegen den Zahlendreher beim Abschreiben |
| Kategorie und Satz kommen in der Rechnung nicht vor | Entweder war der Abschlag falsch oder die Schlussrechnung ist es |
| Dieselbe `rechnungsnummer` zweimal | Kopierfehler |
| Restbetrag negativ | Eine Überzahlung gehört nicht in eine Rechnung – sie wird zurückgezahlt oder der zu hohe Abschlag storniert |
| Leere `steueranteile` | Ein Abschlag ohne Netto und Steuer lässt sich nicht absetzen |
| `rechnungsdatum` eines Abschlags nach dem Datum der Schlussrechnung | Ein Abschlag geht der Schlussrechnung voraus |
