# Woher die Zahlen einer Schlussrechnung kommen

Notiz vom 10.09.2026, festgehalten für später. **Hier wird nichts entschieden.**
Es ist der Stand eines Gesprächs, kein Plan und keine Zusage. Wer das hier
liest, weil er dasselbe Problem noch einmal durchdenkt, soll nicht bei null
anfangen müssen – und soll sehen, warum am Ende trotzdem nichts davon gebaut
wurde.

## Der Anlass

Issue #2 legt die Datenübernahme in die Schlussrechnung in den `rechnung`-Skill:
Die KI sucht die Abschlagsrechnungen im Ablageverzeichnis, liest die Werte und
trägt sie ein. Das Unbehagen daran ist begründet – Beträge und Steuerbeträge
sind genau die Stelle, an der man einem Sprachmodell nicht zusehen kann. Die
Frage war deshalb: Lässt sich die Datenaggregation in den Generator holen, ohne
ADR-0001 aufzugeben?

## Was ADR-0001 verbietet – und was nicht

ADR-0001 verwirft eine bestimmte Sache: *beim Erzeugen des PDF* Rechnungsnummern
auflösen und fremde Dateien nachschlagen. Die Begründung ist Reproduzierbarkeit.
Nicht verworfen ist, dass überhaupt je ein Programm fremde Dateien liest.

Das Prinzip, das sich im Gespräch herausschälte und das alle folgenden Ideen
tragen würde:

> **Renderzeit liest eine Datei. Autorenzeit darf die Welt lesen – muss ihr
> Ergebnis aber hinschreiben.**

Ein Lauf, der Werte zusammenträgt, sie in die Schlussrechnungs-Datei schreibt
und einem Menschen zur Durchsicht vorlegt, verletzt ADR-0001 nicht. Der
Renderlauf bleibt, was er ist: eine Datei rein, ein PDF raus. Das ADR bräuchte
in dem Fall keine Revision, nur einen Zusatz in diesem Sinn.

## Idee A: Ein zweiter Lauf, der die Abschläge einträgt

Der Generator bekommt eine Schlussrechnung, in der `abschlaege` noch fehlt, dazu
die Pfade der abzusetzenden Abschlagsrechnungen. Er schreibt eine neue Datei mit
gefülltem `abschlaege`-Block. Die wird gelesen, ergänzt, und ein zweiter,
gewöhnlicher Lauf macht daraus das PDF.

Drei Beobachtungen dazu:

- **Eine eigene Rechnungsart für die „Hülle" braucht es nicht.** `art:
  schlussrechnung` mit leerem `abschlaege` bricht heute schon ab
  (`InvoiceCalculator.kt`, Regel „Bitte 'abschlaege' füllen"). Die Hülle
  existiert also bereits: gleiches Format, nicht renderbar, mit einer
  Fehlermeldung, die genau das Richtige sagt. Ein fünfter `InvoiceType` wäre
  teuer erkauft – `art` ist BT-3 mit einem UNTDID-Code am Wert, und eine Hülle
  hat keinen Code. Sie beschreibt auch keinen Dokumenttyp, sondern einen
  Arbeitsstand; Zustände gehören nicht in ein Feld, das eine Tatsache über das
  Dokument nennt.
- **Der Unterschied gehört in den Aufruf**, nicht in die Daten – anderes Verb,
  andere Ausgabe (YAML statt PDF). Also eher ein Unterkommando als eine Option.
  Ein Befehl, der je nach Flag mal ein PDF und mal ein YAML ausgibt, wäre die
  Art Zweideutigkeit, die dieses Projekt sonst meidet. Kosten: `rechnung
  datei.yaml` müsste zu `rechnung erzeuge datei.yaml` werden.
- **Die Zieldatei entstünde besser durch Anhängen als durch Neuserialisieren.**
  Das `Invoice`-Objekt durch kaml zurückzuschreiben, kostet alle Kommentare und
  die Feldreihenfolge der handgeschriebenen Datei. Den `abschlaege:`-Block als
  Text anzuhängen (top-level Mapping, Reihenfolge egal) erhält beides, und der
  erzeugte Block kann selbst Kommentare tragen. Der Block enthält nur Daten,
  Zahlen, eine Nummer und Enum-Codes – YAML dafür von Hand zu bauen ist
  vertretbar eng.

## Die Sackgasse: den Zeitpunkt der Vereinnahmung nachtragen

`Prepayment` hat vier Felder. Drei davon stehen in der Abschlagsrechnung:
Nummer, Datum, Steueranteile. Das vierte, `vereinnahmt-am`, steht **nirgends** –
es ist das einzige, das eine Tatsache über die Welt behauptet und nicht über ein
Dokument.

Naheliegend wäre, es nach dem Geldeingang in der Abschlagsrechnungs-YAML
nachzutragen. Das ist eine Falle: `vereinnahmt-am` ist dort BT-7 und **steht auf
dem PDF** (Issue #1 hat das Feld genau dafür eingeführt – für den Fall, dass das
Geld schon vor Rechnungsstellung da war). Nachgetragen sagt die Datei etwas
anderes als das PDF beim Kunden, und ein zweiter Lauf über dieselbe Datei ergäbe
ein anderes Dokument. Das ist dieselbe Fehlerklasse, vor der Issue #2 Schritt 5
warnt, nur selbstverschuldet.

Bleiben zwei ehrliche Wege: das Feld beim Erzeugen leer lassen, damit die
bestehende Abbruchregel es einfordert – oder eine eigene Quelle dafür schaffen,
siehe Idee C.

## Idee B: Das Rechnungsprotokoll

Der Gedanke kam aus einer anderen Richtung: Wären die PDFs schon ZUGFeRD, ließen
sich alle Werte aus dem verschickten Dokument selbst lesen – auch die
berechneten. Also: Der Generator schreibt beim Erzeugen neben das PDF eine Datei
mit allem, was in der Rechnung steht, einschließlich der berechneten Werte. Ein
Protokoll dessen, was tatsächlich abgerechnet wurde, geschrieben von dem Lauf,
der es abgerechnet hat.

Das löst drei Dinge auf einmal:

- **Die Drift.** Die Steueranteile eines Abschlags würden sonst Jahre später mit
  heutigem Code neu ausgerechnet – genau das, was ADR-0001 fürchtet. Mit
  Protokoll werden sie damals einmal geschrieben und danach nur gelesen.
- **Nachträglich geänderte Rechnungen.** YAML und Protokoll laufen auseinander,
  und das sieht man. Issue #2 Schritt 5 wird damit prüfbar statt nur ermahnt.
- **Die Aggregation wird trivial.** Kein Calculator, keine Stammdaten, keine
  Neuvalidierung alter Rechnungen – nur Felder kopieren.

Zur Gestalt: **kein neues Vokabular nötig.** `TaxShare` ist bereits Feld für
Feld die serialisierte Form von `VatGroup` (`steuerkategorie`, `ust-satz`,
`netto`, `ust`). Die Steueraufschlüsselung einer Rechnung ist also schon heute
in genau der Gestalt beschreibbar, in der die Schlussrechnung sie braucht – der
`steueranteile`-Block wäre eine wörtliche Kopie. Das Protokoll wäre die
vorhandenen deutschen Schlüssel plus ein `berechnet:`-Abschnitt.

**Warum nicht gleich echtes CII-XML:** Namespaces, vorgeschriebene
Elementreihenfolge, und ohne Validator (KoSIT oder Mustang – ein drittes
externes Werkzeug in der Toolchain) beweist das erzeugte XML nichts. Der Wert
von ZUGFeRD liegt in der Interoperabilität mit *fremden* Systemen; hier liest
nur das eigene Programm zurück. Verloren wäre nichts: Wenn ZUGFeRD kommt, liest
der XML-Writer dasselbe Modell im Speicher. Sinnvoll wäre nur, die Feldauswahl
an EN 16931 zu orientieren und die BT-Nummern in die Kommentare zu schreiben.

Zwei Grenzen, die man gleich festhalten sollte:

- **Neben das PDF, nicht in einen Unterordner.** Dateien wandern in Jahresordner;
  was nebeneinander liegt, wandert zusammen. Genau das Szenario aus ADR-0001.
- **Das Protokoll ist kein Eingabeformat.** Es wird nie gerendert – sonst gibt es
  zwei Wege zu einem PDF, und der zweite ist der ungeprüfte.

## Idee C: Die Vereinnahmungen

Eine Datei, die je Rechnungsnummer festhält, wann (und über welchen Betrag)
vereinnahmt wurde. Sie ist das Gegenstück zum Protokoll, nicht dessen Erweiterung
– `vereinnahmt-am` dort hineinzuschreiben, zerstörte genau die Eigenschaft, die
das Protokoll wertvoll macht:

|            | Protokoll        | Vereinnahmungen   |
|------------|------------------|-------------------|
| Natur      | unveränderlich   | wächst            |
| schreibt   | der Generator    | der Mensch        |
| Zeitpunkt  | Rechnungsstellung| Geldeingang       |
| sagt       | was abgerechnet wurde | wann bezahlt wurde |
| eins pro   | Rechnung         | alle zusammen     |

Eher YAML als CSV: Das Projekt hat genau einen Eingabeweg (kaml und die Value
Objects mit ihren Prüfungen); ein CSV bräuchte einen eigenen Parser und eigene
Fehlermeldungen für ein krummes Datum.

Der Dateiname folgt aus dem Glossar: **`vereinnahmungen.yaml`**, nicht
`zahlungseingaenge.yaml` – „Zahlungseingang" steht in `CONTEXT.md` unter
Vermeiden. Beide Begriffe (Protokoll, Vereinnahmungen als Datei) müssten dort
aufgenommen werden, bevor sie in den Code gehen.

**Offen geblieben: die Teilvereinnahmung.** Abgesetzt wird nach §14 Abs. 5 das
*vereinnahmte* Entgelt. Kommen auf eine Abschlagsrechnung über 4.760 € nur
3.000 € an, ist die Absetzung der Eingang und nicht die Rechnung, und die Steuer
muss daraus herausgerechnet werden. Der Fall wurde benannt, nicht gelöst; ein
erster Bau würde ihn per Abbruch ausschließen.

## Warum davon (vorerst) nichts gebaut wird

Der Generator soll ein flexibles Werkzeug sein, das sich in verschiedene Prozesse
einbinden lässt – nicht ein Werkzeug, das einen Prozess vorschreibt. Eine
Schlussrechnung von Hand zu schreiben bleibt immer möglich und ist der Maßstab,
an dem sich jede Automatisierung messen lassen muss.

Alle drei Ideen sind Antworten auf einen Prozess, den es noch nicht gibt: Zum
Zeitpunkt dieser Notiz liegt im Ablageverzeichnis **keine einzige
Abschlagsrechnung** (1 Ausgangsrechnung als YAML, 100 PDFs aus dem Altverfahren).
Ein Protokollformat und eine Vereinnahmungsdatei für einen Vorgang zu entwerfen,
der noch nie stattgefunden hat, hieße raten – und was man rät, baut man meistens
zu groß.

Deshalb: erst Issue #2 wie geplant umsetzen, den Skill benutzen, und sehen, wo
es tatsächlich klemmt. Diese Notiz ist dann die Liste der Antworten, die schon
einmal durchdacht wurden.

## Wenn es doch gebaut wird, dann in dieser Reihenfolge

1. **Protokoll beim Erzeugen mitschreiben.** Klein, unabhängig nützlich
   (Jahresübersicht, USt-Voranmeldung, Nachweis gegen geänderte YAMLs), und ohne
   Altbestand auch ohne Übergangspfad.
2. **`vereinnahmungen.yaml` und der Aggregationslauf.** Nimmt Protokolle und
   Journal, schreibt den `abschlaege`-Block. Ein Prüfmodus („stimmt der Block
   noch mit den Protokollen überein?") fällt fast nebenbei ab.
3. **Issue #2 schrumpft** auf das, was einen Menschen braucht: suchen, vorlegen,
   fragen, welche Abschläge zum Vorgang gehören. Kein Übertragen, kein Rechnen.

Die Naht bliebe dieselbe wie heute gedacht: Der Skill sucht und fragt, der
Generator rechnet und schreibt.

## Was offen blieb

- Unterkommando (`rechnung erzeuge …`) oder Option? Damit die Frage, ob der
  bestehende Aufruf brechen darf.
- Teilvereinnahmung.
- Ein Prüfmodus, der Protokolle Jahre später gegen den heutigen Code hält, kann
  nach einer geänderten Rundungsregel rot werden – und dann ist die *alte*,
  verschickte Zahl die richtige. Er dürfte melden, aber nie „korrigieren". Ob
  das reicht, wurde nicht zu Ende gedacht.
- Ob das Protokoll irgendwann einfach durch echtes ZUGFeRD ersetzt wird und
  dieser ganze Zwischenschritt entfällt.
