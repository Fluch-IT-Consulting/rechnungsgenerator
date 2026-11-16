# Die Rechnungsnummer vergibt ein Skript in der Ablage

`naechste-nummer.py` im `rechnung`-Skill wird geteilt:

- Der **Rahmen** bleibt `scripts/naechste-nummer.py` im Paket. Er liest das
  Feld `nummer` jeder YAML-Datei der Ablage, ruft das Vergabeskript und prüft,
  was herauskommt.
- Das **Vergabeskript** liegt in der Wurzel der Ablage, als
  `.rechnungsgenerator/rechnungsnummer-vergabe`. Es bekommt die belegten
  Nummern und das Rechnungsdatum und nennt die nächste Nummer. Das Format der
  Nummern kennt nur es.

Zwischen beiden gilt:

    printf 'R26-011\nR26-012\n' | .rechnungsgenerator/rechnungsnummer-vergabe 2026-11-16
    R26-013

- **stdin:** die belegten Nummern, eine je Zeile, ungefiltert. Was nicht in
  sein Format passt, übergeht das Vergabeskript.
- **Argument:** das Rechnungsdatum als `JJJJ-MM-TT`, nicht das heutige Datum.
- **stdout:** genau eine Zeile, die Nummer. Sonst endet es mit einem Code
  ungleich 0 und einer Meldung auf stderr.
- **Ohne Nebenwirkung:** Derselbe Aufruf ergibt dieselbe Nummer.
- **Ohne belegte Nummern** nennt es die erste Nummer des Nummernkreises.

Die Datei ist ausführbar und hat keine Endung; die Sprache ist frei. Der Skill
bringt eine Vorlage in Python mit, gleichen Namens, neben `scripts/`. In sie
trägt man zwei Zeilen ein:

    NUMMERNKREIS = r"R%y-(\d{3})"
    FORMAT = "R%y-{:03d}"

`%y`, `%Y` und `%m` stehen für das Rechnungsdatum. `NUMMERNKREIS` trifft danach
nur die Nummern des Nummernkreises, seine Klammer ist der Zähler; `FORMAT`
macht aus dem höchsten Zähler plus eins die nächste Nummer.

**Der Rahmen erkennt den leeren Nummernkreis.** Er ruft das Vergabeskript
zweimal, mit den belegten Nummern und ohne. Kommt beide Male dieselbe Nummer
heraus, gibt es im Nummernkreis keine YAML-Rechnung, und die Nummer ist ein
Vorschlag:

| Im Nummernkreis des Rechnungsdatums | `naechste-nummer.py` |
|---|---|
| gibt es eine YAML-Rechnung | die nächste Nummer auf stdout, Code 0 |
| gibt es keine | den Vorschlag auf stderr, Code 3 |

Bestätigt wird mit einem zweiten Aufruf, der die Nummer mit
`--rechnungsnummer` nennt; eine andere Nummer geht denselben Weg. Jede Nummer,
die auf stdout geht, hat der Rahmen geprüft: Sie ist nicht schon belegt,
Zeichen für Zeichen verglichen (#53), und mit `--stammdaten` passt sie zu
`rechnungsnummer-muster` (ADR-0005).

Bricht das Vergabeskript ab, endet der Rahmen mit 1 und reicht seine Meldung
weiter, nicht seinen Code. Fehlt es, bricht der Rahmen ab und nennt die
Vorlage. Eine Vorgabe gibt es nicht.

## Warum

**Das Schema stand fest im Skript.** `R{JJ}-{NNN}` kannte nur
`naechste-nummer.py`, und wer anders nummeriert, konnte den Skill nicht
benutzen. Ändern ließ sich das Skript auch nicht mehr: Seit ADR-0030 kommt der
Skill im Paket, und das nächste `brew upgrade` nähme eine Änderung wieder weg.
`rechnungsnummer-muster` hilft dabei nicht. Ein regulärer Ausdruck prüft, er
zählt nicht.

**Das Format gehört in die Ablage, das Durchsuchen ins Paket.** Wie Nummern
aussehen, ist Sache des Rechnungsstellers und soll ein Upgrade nicht still
ändern; die Vorlage wird deshalb kopiert, nicht verlinkt. Was Fehler haben
kann, das Durchsuchen und das Lesen der YAML-Dateien, bleibt im Paket.

**Der Stand steckt allein in den YAML-Dateien.** Dateinamen liest der Rahmen
nicht mehr: Er kennt das Format nicht und kann eine Nummer in einem Namen
nicht erkennen. Die Annahme dahinter: Wer zum Generator wechselt, bleibt
dabei. Rechnungen ohne YAML-Datei gibt es nur aus der Zeit davor.

**Ohne YAML-Rechnung im Nummernkreis wird immer bestätigt.** Dort können alte
Rechnungen liegen, die die Vergabe nicht sieht. Die genannte Nummer ist danach
der Stand: Aus ihr entsteht die erste YAML-Rechnung des Nummernkreises.

**Den Vorschlag erkennt der Rahmen, nicht das Vergabeskript.** So kennt das
Vergabeskript nur zwei Ausgänge, eine Nummer oder den Abbruch, und das
einfachste ist richtig: die höchste Nummer plus eins, sonst die erste. Ein
Fehler geht nur in die harmlose Richtung, schlimmstenfalls wird einmal zu viel
bestätigt. Die Regel „immer bestätigen“ steht im Paket und nicht in jeder
Kopie.

**stdout trägt nur eine Nummer, die benutzt werden darf.** Ein
`$(naechste-nummer.py …)` liefert nie eine unbestätigte. Die Einmaligkeit
(§ 14 Abs. 4 Satz 1 Nr. 4 UStG) hängt damit nicht an der Sorgfalt des
einzelnen Vergabeskripts.

**Der Generator bleibt unberührt.** Er kennt die Vergabe nicht (ADR-0001), und
`einrichten` legt das Vergabeskript nicht an: Es hat kein Argument und bricht
in einer eingerichteten Wurzel ab, eine bestehende Ablage käme so nie zu ihrem
Skript. Das Vergabeskript legt der Skill im Gespräch an.

**libyaml, wo es da ist.** `yaml.safe_load` nimmt den Leser in Python, auch
wenn libyaml da ist. Nachgemessen am 16.11.2026 an 2000 erfundenen
Rechnungsdateien, frischer Prozess, Intel i9:

| Leser | Python 3.9 | Python 3.14 |
|---|---|---|
| `safe_load` | 13,6 s | 7,4 s |
| `CSafeLoader` (libyaml) | 1,6 s | 1,0 s |

Maßstab sind rund 2000 Rechnungsdateien, 15 im Monat über 10 Jahre. Eine
Sekunde je Aufruf ist dafür in Ordnung; kein Zwischenspeicher, keine
parallelen Prozesse. Der Grund in „Übersprungen, nicht lesbar“ lautet mit
libyaml etwas anders, Art des Fehlers und Fundstelle bleiben.

## Verworfen

- **Eine Vorgabe ohne Vergabeskript.** Wer anders nummeriert, bekäme still
  `R26-001`, ohne es gewählt zu haben.
- **Auf den Zählerstand 1 schließen, wenn ein früherer Nummernkreis eine
  YAML-Rechnung hat.** Die Nummer sagt, in welchen Nummernkreis eine Rechnung
  gehört, nicht wann sie geschrieben wurde. Wer nach dem Wechsel erst eine
  Rechnung nach 2024 rückdatiert und dann eine nach 2025, bekäme `R25-001`,
  obwohl es dort alte Rechnungen geben kann.
- **Dasselbe, beschränkt auf den heutigen Nummernkreis.** Das fängt den
  Wechsel im laufenden Nummernkreis nicht ab: Anfang Januar entsteht `R27-001`
  noch im alten System, die erste Rechnung mit dem Generator gehört ins alte
  Jahr, und die nächste vom Januar bekäme wieder `R27-001`. Bei Jahr und Monat
  ist das der gewöhnliche Monatswechsel.
- **Den Wechsel als Konstante im Vergabeskript festhalten**, bis zu welchem
  Nummernkreis es Rechnungen ohne YAML geben kann. Das ersparte die
  Bestätigung danach, kostet aber einen Zustand: bei Jahr und Zähler eine
  Bestätigung im Jahr, bei Jahr und Monat zwölf. Stört das, braucht die
  Konstante eine Erweiterung der Absprache, denn den leeren Nummernkreis
  erkennt der Rahmen.
- **Ein eigener Code des Vergabeskripts für „vorgeschlagen“.** Vergisst ein
  selbst geschriebenes Skript ihn, gilt der Vorschlag still als vergeben. Der
  gefährliche Fehler wäre der bequemste.
- **Eine gekennzeichnete Zeile**, `vergeben R26-013` oder
  `vorgeschlagen R27-001`. Vergessen ginge nicht mehr, aber ein Skript könnte
  weiter falsch „vergeben“ sagen, etwa das naheliegende „höchste plus eins,
  sonst 1“.
- **Die Dateinamen als zweite Quelle behalten** (#75), gekennzeichnet neben den
  Nummern. Die Bestätigung im leeren Nummernkreis deckt denselben Fall ab.
- **Ein Schema mit Platzhaltern in den Stammdaten** (`R{JJ}-{NNN}`). ADR-0005
  hat es fürs Prüfen verworfen; fürs Zählen wäre es eine eigene kleine Sprache
  und ein Schlüssel, den der Generator kennen müsste, ohne ihn zu brauchen.
- **Je Familie von Formaten eine Vorlage**, mit Konstanten für Präfix,
  Trennzeichen und Zählerbreite. Die Familien unterscheiden sich nur in den
  zwei Zeilen, und `0008/2026` hätte keine gekonnt.
- **Alle Zähler durchprobieren**, statt die belegten Nummern zu lesen: jede
  Nummer von 1 bis zu einer Obergrenze bilden und nachsehen, welche belegt
  ist. Das käme mit dem Format allein aus, braucht aber die Obergrenze als
  zweite Angabe. Passt sie nicht zur Stellenzahl, sieht die Vorlage belegte
  Nummern nicht.
- **Das Vergabeskript `naechste-nummer` nennen.** Es unterschiede sich vom
  Rahmen nur in der Endung, und in einem Satz oder einer Meldung liest man
  darüber hinweg.
- **Den Schalter `--nummer` nennen.** Das Glossar führt „Nummer (für sich
  allein)“ unter „Vermeiden“, und `--rechnungsnummer` steht neben
  `--rechnungsdatum`.
- **Die Zeile der Vorlage `MUSTER` nennen.** Dann hießen zwei Dinge Muster:
  der Ausdruck für einen Nummernkreis und `rechnungsnummer-muster`, das jede
  gültige Nummer trifft.

## Konsequenzen

- **Eine bestehende Ablage braucht nach dem Upgrade einmal ihr
  Vergabeskript**, auch eine im Schema `R{JJ}-{NNN}`. Der Abbruch des Rahmens
  kommt beim Skill an; er fragt, wie die Nummern aussehen, kopiert die Vorlage
  und trägt die zwei Zeilen ein. Ohne Claude Code geht dasselbe mit `cp` und
  einem Editor. Die Rechnungsdateien bleiben gültig.
- **Den Vorschlag bestätigt nie der Skill selbst.** Der Rahmen kann das nicht
  verhindern, nur der Text von `SKILL.md`: Bei Code 3 fragt er, ob es im
  Nummernkreis Rechnungen aus der Zeit vor dem Wechsel gibt.
- **`rechnungsnummer-muster` kommt mit dem Vergabeskript.** Legt der Skill das
  Vergabeskript an, schlägt er das passende Muster für die Stammdaten vor und
  trägt es erst nach einem Ja ein. Das Muster bleibt freiwillig.
- **Der Nummernkreis hängt an Jahr und Monat des Kalenders.** Ein abweichendes
  Geschäftsjahr oder ein Nummernkreis je Kunde geht nicht über die zwei
  Zeilen; dafür schreibt man ein eigenes Vergabeskript.
- **Ein erschöpfter Nummernkreis bricht ab.** `R26-1000` passt nicht mehr zu
  den drei Stellen in `NUMMERNKREIS`. Breiter wird die Nummer nicht.
- **Die Vorlage prüft ihre zwei Zeilen bei jedem Aufruf gegeneinander** und
  kommt unausgefüllt: Bis sie eingetragen sind, bricht sie ab.
- **Der Name der Rechnungsdatei ist nur noch eine Konvention.** Der Skill
  bildet den Teil für die Nummer nach der Regel von
  `CalculatedInvoice.fileBaseName`; aus `2026/0042` wird
  `Rechnung_2026-0042-kunde-a.yaml`.
- **Die Vorlage braucht im Archiv ihr Ausführungsbit** wie die Skripte;
  `release.yml` prüft es. `scripts/deutsche-bezeichner.py` liest sie als
  Python mit.
- `offene-abschlaege.py` liest dieselben Dateien noch mit `safe_load`; den
  Leser bekommt es in #565.

Umgesetzt in #564.
