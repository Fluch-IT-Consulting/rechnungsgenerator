# Das Muster der Rechnungsnummer steht in den Stammdaten

Die Stammdaten dürfen einen regulären Ausdruck tragen, dem jede Rechnungsnummer
genügen muss:

```yaml
rechnungsnummer-muster: '^R\d{2}-\d{3}$'
```

Ohne diesen Eintrag wird die Form nicht geprüft. Passt eine Nummer nicht zum
eingetragenen Muster, bricht der Lauf ab – als Befund des `InvoiceCalculator`
mit der Schwere Abbruch, nicht als geworfener Fehler beim Einlesen.

Unabhängig vom Muster gilt für jede Rechnung: Die Nummer darf nicht leer sein
und keinen Leerraum am Rand tragen.

Geprüft wird nur die Nummer der Rechnung selbst, nie die der abgesetzten
Abschläge.

## Warum

Der Generator soll so viel wie möglich prüfen – ein Zahlendreher in einer
Kennung fällt sonst erst auf, wenn es zu spät ist; deshalb werden IBAN und
USt-IdNr. schon heute geprüft. Aber welche Nummern gültig sind, weiß nur der
Rechnungssteller: UStAE 14.5 Abs. 10 erlaubt Zahlen- und Buchstabenreihen, auch
gemischt, und verlangt keine lückenlose Abfolge; Abs. 11 überlässt ihm, wie
viele Nummernkreise er bildet. `R26-009` ist ein Format unter vielen. Ein fest
eingebautes Schema schriebe das eigene allen vor.

Die Einmaligkeit (§14 Abs. 4 Satz 1 Nr. 4 UStG) prüft der Generator nicht und
kann es nicht: Sie ist eine Eigenschaft aller Rechnungen zusammen, und beim
Erzeugen sieht er eine einzige Datei (ADR-0001). Prüfbar ist allein die Form.

Der Stand des Gesprächs vor dieser Entscheidung steht in
`docs/notizen/2026-09-11-gueltige-rechnungsnummer.md`.

## Verworfen

- **Ein Schema mit Platzhaltern** wie `R{JJ}-{NNN}`. Es liest sich besser, und
  es könnte mehr: das Jahr gegen das Rechnungsdatum prüfen. Genau diese
  Mehrleistung lehnte aber eine richtige Rechnung ab – die vom 31.12., die erst
  am 2.1. geschrieben wird. Übrig bliebe eine kleine eigene Sprache, die man
  nur aus diesem Projekt lernen kann, gegen eine Notation, die jeder
  nachschlagen kann. Wird `naechste-nummer.py` das Muster einmal wirklich
  lesen, statt es fest einzubauen, ist das der Anlass, die Frage neu zu
  stellen.
- **Ein Wert-Typ mit fest eingebautem Schema**, nach dem Vorbild von `Iban`.
  Er schriebe das eigene Format allen vor.
- **Ein austauschbarer Nummernkreis als Interface mit Factory.** Mit einem
  einzigen Schema wäre das eine vermutete Naht mit einem einzigen Adapter. Die
  Austauschbarkeit, um die es geht, liegt beim Rechnungssteller und gehört
  damit in die Stammdaten, nicht in eine Klassenhierarchie.
- **Eine Liste von Mustern** für mehrere Nummernkreise. Ein regulärer Ausdruck
  trägt seine Alternativen selbst: `^(R|G)\d{2}-\d{3}$`.
- **Nur ein Hinweis statt Abbruch.** Das Muster hat der Rechnungssteller selbst
  eingetragen; was ihm widerspricht, ist ein Tippfehler oder ein Schemawechsel.
  Beides will man sehen, und Dauerhinweise führen dazu, dass auch die wichtigen
  überlesen werden.

## Konsequenzen

- **Die Prüfung sitzt im Calculator, nicht im Serialisierer.** Ob eine Nummer
  gültig ist, hängt an zwei Dateien – der Rechnung und den Stammdaten. Der
  Serialisierer sieht beim Einlesen immer nur eine. Der Calculator sieht beide
  und hat den Mechanismus schon (ADR-0004). Geprüft wird beim Einlesen nur, ob
  der eingetragene Ausdruck überhaupt übersetzbar ist.
- **Die Meldung zitiert das Muster.** Mehr kann sie nicht: Ein regulärer
  Ausdruck sagt nicht, wie eine passende Nummer aussähe. Wer ein unverständlich
  langes Muster einträgt, bekommt eine unverständlich lange Meldung.
- **Leere Nummer und Leerraum am Rand brechen ab, auch ohne Muster.** Aus der
  Nummer entsteht der Dateiname; ein gequotetes `' R26-009'` ergäbe still eine
  andere Datei. Damit ist auch die Nummernlosigkeit ausgeschlossen, die UStAE
  14.5 Abs. 14 Kleinunternehmern und Kleinbetragsrechnungen zugesteht – dieser
  Generator benennt seine Ausgabe nach der Nummer und bräuchte sonst eine
  zweite Antwort auf die Frage, wie die Datei heißt.
- **Die Nummern der Abschläge bleiben ungeprüft.** Sie stammen aus früheren
  Rechnungen, womöglich aus der Zeit vor einem Wechsel des Musters. Gegen das
  heutige Muster geprüft, hielte eine korrekte alte Nummer die Schlussrechnung
  auf. Das Muster beschreibt, was der Rechnungssteller heute ausstellt.
- **Ein Wechsel des Musters wirkt rückwärts.** Stammdaten gelten für jede
  Rechnung; eine alte Rechnung neu zu erzeugen, scheitert nach einem Wechsel.
  Für die Anschrift gilt das schon heute, dort ändert sich aber nur das PDF.
- **`naechste-nummer.py` kennt sein Schema weiter selbst.** Zwei Stellen, die
  dasselbe wissen. Nachgetragen mit #61: Auflösen lässt sich das nicht – ein
  regulärer Ausdruck prüft, er zählt nicht, und benannte Gruppen als Ausweg
  scheiden aus, weil Java und Python sie unvereinbar schreiben (`(?<jahr>…)`
  gegen `(?P<jahr>…)`, nachgemessen am 12.09.2026). Das Skript liest das Muster
  seither trotzdem und prüft damit die Nummer, die es vergeben will. Die
  Doppelung bleibt, kann aber nicht mehr still auseinanderlaufen.

Umgesetzt in #54.
