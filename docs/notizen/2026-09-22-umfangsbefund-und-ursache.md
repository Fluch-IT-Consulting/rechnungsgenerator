# Ein Umfangsbefund nennt die Ursache daneben

Notiz vom 22.09.2026, festgehalten für später. **Hier wird nichts
entschieden.** Anlass sind #280 und #284, zwei Issues aus dem
Architektur-Review vom 22.09.2026. Beide haben einen Umfang gezählt, daraus
eine Ursache geschlossen – und beide Male war die Ursache eine andere.

## Was zweimal passiert ist

**#280** zählte 3.051 Kommentarzeilen im Hauptcode, 47 %, und nannte als Grund
die Änderungsgeschichte: „Jede Datei erzählt heute neben dem Code, was vor #70
dort stand." Nachgemessen waren es rund 60 Zeilen Geschichte und rund 30
Lehrsätze zu Kotlin-Idiomen. Die übrigen rund 2.900 Zeilen sind Begründungen,
BT-Nummern der EN 16931 und Paragraphen des UStG. Der Umbau hat netto 68
Zeilen gestrichen, 1,5 %.

**#284** zählte 7.264 Wörter in `CLAUDE.md` und nannte denselben Grund: „liest
sich wie ein Changelog". Nachgemessen waren 28 Zeilen Geschichte, 3,6 %. Die
Länge kam aus etwas anderem: zehn Abschnitte, die ein bestehendes ADR
nacherzählen, zusammen 2.833 Wörter oder 39 % der Datei.

Beide Male hat die Messung vor dem Schnitt die Diagnose umgeworfen, und beide
Male ist daraus ein ADR entstanden, das sagt, was gehen darf und was nicht
(ADR-0016, ADR-0017).

## Warum das kein Zufall sein muss

Umfang ist billig zu zählen, Zusammensetzung nicht. Wer eine Datei oder ein
Verzeichnis vermisst, hat die Zahl in einer Minute; wer wissen will, woraus
die Zahl besteht, muss den Text lesen. Ein Review, das viele Befunde an einem
Tag erzeugt, zählt also und schließt – und greift dann zu der Ursache, die bei
diesem Projekt naheliegt, weil es sie überall gibt: die vielen Issue-Nummern,
die „Vorher"-Sätze, der erzählende Ton.

Das ist eine plausible Vermutung und trotzdem zweimal falsch gewesen. Die
Geschichte ist sichtbar, weil sie auffällt – nicht, weil sie viel ist.

## Was dagegen spricht, daraus eine Regel zu machen

Die falsche Diagnose war beide Male **produktiv**. Sie hat das Issue
überhaupt erst entstehen lassen, und der Widerspruch zwischen Vermutung und
Messung ist der Grund, warum ADR-0016 lesenswert ist: Es erklärt nicht nur,
was ein Kommentar sagen soll, sondern auch, warum 44 % das richtige Ergebnis
sind. Ein Issue, das nur „`CLAUDE.md` ist lang, Ursache offen" gesagt hätte,
wäre schwächer gewesen – womöglich hätte es niemand angefasst.

Dazu kommt: Der Preis war beide Male klein. Eine Messung vor dem Schnitt
kostet eine halbe Stunde. Wenn sie ohnehin stattfindet, richtet eine falsche
Vermutung im Rumpf keinen Schaden an.

## Drei Richtungen, keine entschieden

- **Nichts tun.** Zwei Fälle sind zwei Fälle. Die Triage hat beide Male
  gemessen, bevor sie einen Agent Brief geschrieben hat, und genau dort gehört
  die Messung hin.
- **Der Umfangsbefund trennt Messung und Vermutung.** Der Rumpf nennt die Zahl
  als Befund und die Ursache ausdrücklich als Vermutung, die die Triage prüft.
  Kostet einen Satz und nimmt dem Brief die Arbeit ab, die Vermutung erst
  widerlegen zu müssen.
- **Die Triage misst die Zusammensetzung, bevor sie einen Brief schreibt.** Als
  Zeile in `docs/agents/`, nicht als Netz. Dieselbe Bauart wie die Gewohnheit
  beim Versionskatalog (#253) – mit demselben bekannten Nachteil: Nichts hält
  sie auf, wenn sie vergessen wird.

## Woran sich der Zeitpunkt erkennen ließe

An einem **dritten** Umfangsbefund mit derselben Vermutung – dann ist es ein
Muster und keine Doppelung. Oder, teurer und deshalb das eigentliche Signal:
an einem Issue, dessen falsche Ursache durch die Triage kommt und unter dem
gearbeitet wird. Dann fällt es erst im Review des Pull Requests auf, oder gar
nicht, und am Ende steht ein Umbau, der das Falsche gestrichen hat.

Bis dahin ist die Gegenmaßnahme die, die zweimal funktioniert hat: erst messen,
woraus der Umfang besteht, dann entscheiden, was gehen darf.
