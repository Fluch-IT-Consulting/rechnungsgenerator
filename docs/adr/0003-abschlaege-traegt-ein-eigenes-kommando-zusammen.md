# Die Abschläge trägt ein eigenes Kommando zusammen

Der Generator bekommt ein zweites Kommando, `rechnungsgenerator abschlaege`. Es
liest die Abschlagsrechnungen, die ihm übergeben werden, rechnet ihre
Steueraufschlüsselung mit demselben Code aus wie beim Erzeugen des PDF und gibt
daraus den `abschlaege`-Block einer Schlussrechnung auf stdout aus. Einfügen
muss ihn ein Mensch oder der `rechnung`-Skill. Das bisherige Erzeugen heißt
künftig `rechnungsgenerator erzeuge` und liest weiter genau eine Datei.

ADR-0001 bleibt damit richtig, man muss es nur genau lesen: Verworfen ist das
Nachschlagen *beim Erzeugen des PDF*. Wer die Werte vorher zusammenträgt, darf
fremde Dateien lesen. Das Ergebnis muss dann aber in der Rechnung stehen, wo
ein Mensch es sieht und die Abbruchregeln des Calculators es prüfen.

## Verworfene Alternativen

- **Der Skill überträgt die Werte.** So stand es in ADR-0001 und Issue #2.
  Beträge und Steuerbeträge sind aber genau die Stelle, an der man einem
  Sprachmodell nicht zusehen kann. Der Skill sucht weiter und fragt nach,
  welche Abschläge dazugehören, aber er schreibt keine Zahlen ab.
- **Ein Protokoll neben jedem PDF als Quelle der Werte** (Idee B der Notiz vom
  10.09.2026). Damit wäre die Drift weg, aber es braucht ein neues Format für
  einen Vorgang, der zum Zeitpunkt der Entscheidung noch nie stattgefunden
  hatte. Das Kommando braucht kein neues Format: Es liest die vorhandene
  Abschlagsrechnung und gibt den vorhandenen `abschlaege`-Block aus. Ein
  Protokoll kann später als andere Quelle für denselben Block dazukommen.
- **Der Generator schreibt den Block selbst in die Schlussrechnung** (Idee A
  der Notiz). Neu geschrieben verlöre die Datei ihre Kommentare. Die Ausgabe
  auf stdout lässt die Wahl, und zum Anhängen genügt `>>`.
- **Eine Option statt eines Unterkommandos.** Ein Kommando, das je nach
  Schalter ein PDF oder YAML ausgibt, wäre zweideutig. Dafür funktioniert der
  bisherige Aufruf nicht mehr. Zum Zeitpunkt der Entscheidung benutzte ihn nur
  der Skill.

## Konsequenzen

- **Die Drift ist benannt, nicht beseitigt.** Die Werte werden mit dem
  heutigen Code aus der alten Datei neu berechnet, also genau so, wie es
  ADR-0001 für das Erzeugen des PDF ausschließt. Der Kopfkommentar der Ausgabe
  sagt das, und den Block mit dem verschickten PDF zu vergleichen, bleibt
  Sache des Menschen.
- Über die alte Abschlagsrechnung laufen alle Regeln von heute, deshalb
  braucht das Kommando die Stammdaten. Lehnt eine inzwischen hinzugekommene
  Regel einen damals gültigen Abschlag ab, bricht der Lauf ab, und der Block
  muss von Hand geschrieben werden.
- Der Lauf liefert alles oder nichts. Ein fehlender Abschlag fiele sonst
  keiner Regel auf, nur dem Kunden, der ihn dann doppelt zahlt.
- `vereinnahmt-am` wird nur übernommen, wenn es in der Abschlagsrechnung
  steht. Dort hält es eine Vereinnahmung fest, die vor der Rechnungsstellung
  stattfand und die auch auf deren PDF steht. Sonst bleibt das Feld leer, und
  `ABSCHLAG_OHNE_VEREINNAHMUNG` fordert es beim Erzeugen der Schlussrechnung
  ein. Nachgetragen wird es in der Abschlagsrechnung nie (siehe Notiz vom
  10.09.2026).
