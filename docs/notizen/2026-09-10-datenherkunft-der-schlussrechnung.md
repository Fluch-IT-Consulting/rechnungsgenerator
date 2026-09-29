# Woher die Zahlen einer Schlussrechnung kommen

Notiz vom 10.09.2026, Stand 29.09.2026. **Hier wird nichts entschieden.**

Entschieden ist der erste Schritt (ADR-0003): `abschlaege` liest die
Abschlagsrechnungen, rechnet ihre Steueranteile mit dem heutigen Code neu aus
und gibt den `abschlaege`-Block auf stdout aus. Offen ist, woher die Zahlen
genauer kämen und was der Block nicht kann.

## Was der Block nicht kann

- **Drift.** Die Werte entstehen heute neu, nicht in dem Lauf, der die
  Abschlagsrechnung verschickt hat. Eine geänderte Rundungsregel oder eine
  nachträglich geänderte Rechnungsdatei fiele niemandem auf; der
  Kopfkommentar der Ausgabe sagt das nur.
- **Teilvereinnahmung.** Abgesetzt wird das vereinnahmte Entgelt (§14 Abs. 5
  Satz 2 UStG). Kommen auf 4.760 € nur 3.000 €, ist die Absetzung der Eingang,
  und die Steuer muss aus ihm herausgerechnet werden. Der Kopfkommentar warnt,
  erkennen kann das Kommando den Fall nicht.
- **Gegenprüfen.** Ob der Block einer bestehenden Schlussrechnung noch zu den
  Abschlagsrechnungen passt, prüft niemand. Die Ausgabe ist zeichengleich
  reproduzierbar; ein `diff` zeigt im Normalfall nur die ergänzten
  `vereinnahmt-am`-Zeilen.
- **Sortierung.** Der Block folgt der Reihenfolge der Argumente. Ein Glob wie
  `R26-0*.yaml` sortiert bei fortlaufenden Nummern richtig; eine Option kommt
  erst, wenn das nicht reicht.

## Die Falle: `vereinnahmt-am` nachtragen

In der Abschlagsrechnung ist `vereinnahmt-am` BT-7 und steht auf ihrem PDF und
in ihrem XML. Nach dem Geldeingang nachgetragen, sagte die Datei etwas anderes
als das verschickte PDF, und ein zweiter Lauf ergäbe ein anderes Dokument.
Deshalb bleibt das Feld im Block leer, bis ein Mensch es füllt, und
`ABSCHLAG_OHNE_VEREINNAHMUNG` fordert es ein. Eine eigene Quelle dafür wäre
Idee C.

## Idee B: Die Werte aus dem Lauf, der die Abschlagsrechnung erzeugt hat

Gedacht war ein Protokoll neben jedem PDF mit allen berechneten Werten,
geschrieben beim Erzeugen. Es beseitigte die Drift, machte eine nachträglich
geänderte Rechnungsdatei sichtbar und das Zusammentragen trivial: kopieren
statt rechnen, ohne Stammdaten und ohne dass eine neue Regel einen alten
Abschlag ablehnt.

**Seit ADR-0023 gibt es diese Quelle womöglich schon.** Eine mit
`erzeuge --zugferd` gesetzte Abschlagsrechnung trägt ihr XML im PDF: Code
386, die Steueraufschlüsselung je Kategorie mit Netto und Steuer, BT-7, alles
aus der `CalculatedInvoice` jenes Laufs. Es wandert mit dem PDF, statt neben
ihm zu liegen, `xml` holt es heraus, und innerhalb Deutschlands ist das XML
die Rechnung (BR-FX-DE-03). Ein eigenes Format bräuchte es dann nicht.

Offen daran:

- Nur eine Abschlagsrechnung mit `--zugferd` trägt das XML; ohne bliebe der
  heutige Weg.
- `abschlaege` läse XML ins Modell zurück. Das wäre der zweite Leser, auf den
  „`model` liest selbst“ wartet (Notiz vom 14.09.2026), und genau das, was
  `xml` bewusst nicht tut (ADR-0023).
- Ein Prüfmodus, der alte Werte gegen den heutigen Code hält, kann nach einer
  geänderten Rundungsregel rot werden – und dann ist die alte, verschickte
  Zahl die richtige. Er dürfte melden, nie korrigieren.

## Idee C: Die Vereinnahmungen

Eine Datei `vereinnahmungen.yaml`, die je Rechnungsnummer festhält, wann und
über welchen Betrag vereinnahmt wurde. Sie ist das Gegenstück zu Idee B,
nicht ein Teil davon:

|           | Werte des Laufs       | Vereinnahmungen     |
|-----------|-----------------------|---------------------|
| Natur     | unveränderlich        | wächst              |
| schreibt  | der Generator         | der Mensch          |
| Zeitpunkt | Rechnungsstellung     | Geldeingang         |
| sagt      | was abgerechnet wurde | wann bezahlt wurde  |
| eins pro  | Rechnung              | alle zusammen       |

YAML und nicht CSV, weil es einen Eingabeweg gibt (`YamlLoader`). Der Name
folgt dem Glossar, „Zahlungseingang“ steht dort unter „Vermeiden“; die Datei
käme als Begriff ins Glossar, bevor sie in den Code geht. Mit ihr ließe sich
die Teilvereinnahmung erkennen, statt nur vor ihr zu warnen.

## Warum davon noch nichts gebaut ist

Der Generator soll sich in Abläufe einfügen und keinen vorschreiben; eine
Schlussrechnung von Hand zu schreiben bleibt der Maßstab. Am 29.09.2026 liegt
in der Ablage keine Abschlagsrechnung, der Vorgang hat also noch nie
stattgefunden. Wer ihn vorher automatisiert, rät – und baut meist zu groß.

Käme es dazu, dann in dieser Reihenfolge: erst die Werte aus dem XML lesen, wo
es eins gibt; dann die Vereinnahmungen. Das Gegenprüfen fiele dabei fast
nebenbei ab.
