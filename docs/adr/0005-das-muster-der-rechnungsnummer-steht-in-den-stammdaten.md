# Das Muster der Rechnungsnummer steht in den Stammdaten

Die Stammdaten dürfen einen regulären Ausdruck tragen, dem jede Rechnungsnummer
genügen muss:

```yaml
rechnungsnummer-muster: '^R\d{2}-\d{3}$'
```

Ohne diesen Eintrag wird die Form nicht geprüft. Passt eine Nummer nicht zum
eingetragenen Muster, bricht der Lauf ab – als Befund des `InvoiceCalculator`
mit der Schwere Abbruch, nicht als geworfener Fehler beim Einlesen. (Seit #172
gibt es den `InvoiceCalculator` nicht mehr; die Regel steht in
`InvoiceNumberRules` unter `core/rules`.)

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

Der Stand des Gesprächs vor dieser Entscheidung steht in #54.

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

- **Die Prüfung sitzt in den Regeln, nicht im Serialisierer.** Ob eine
  Nummer gültig ist, hängt an zwei Dateien – der Rechnung und den Stammdaten.
  Der Serialisierer sieht beim Einlesen immer nur eine. Die Regeln sehen beide
  und haben den Mechanismus schon (ADR-0004). Geprüft wird beim Einlesen nur,
  ob der eingetragene Ausdruck überhaupt übersetzbar ist.
- **Die Meldung zitiert das Muster.** Mehr kann sie nicht: Ein regulärer
  Ausdruck sagt nicht, wie eine passende Nummer aussähe. Wer ein unverständlich
  langes Muster einträgt, bekommt eine unverständlich lange Meldung.
- **Leere Nummer und Leerraum am Rand brechen ab, auch ohne Muster.** Aus der
  Nummer entsteht der Dateiname; ein gequotetes `' R26-009'` ergäbe still eine
  andere Datei. Damit ist auch die Nummernlosigkeit ausgeschlossen, die UStAE
  14.5 Abs. 14 Kleinunternehmern und Kleinbetragsrechnungen zugesteht – dieser
  Generator benennt seine Ausgabe nach der Nummer und bräuchte sonst eine
  zweite Antwort auf die Frage, wie die Datei heißt.
  Nachgetragen mit #150: Ebenso bricht eine Nummer ab, von der kein Dateiname
  übrig bleibt, etwa `"///"`. Vorher fiel sie erst in `erzeuge` auf, nachdem
  die Rechnung angenommen war, und damit neben anderen Abbrüchen erst im
  zweiten Lauf. Den Namen leitet jetzt `CalculatedInvoice.fileBaseName` ab,
  und nur eine `AcceptedInvoice` trägt ihn sicher.
  (Seit #514 bricht eine leere Nummer schon beim Einlesen ab, wie jeder leere
  Wert seit #504, und hat keine eigene Kennung mehr.)
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

## Nachtrag vom 23.09.2026: aus der Nummer entsteht kein Dateiname mehr

Die Begründung unter „Konsequenzen“ – „Aus der Nummer entsteht der Dateiname“
– ist aufgehoben. Seit ADR-0018 heißt das PDF wie die Rechnungsdatei. Aus der
Nummer entsteht nur noch der Jobname für LuaLaTeX, `fileBaseName`, und der
gilt in einem Arbeitsverzeichnis, das nach dem Lauf gelöscht wird.

**Der Jobname ist trotzdem nicht beliebig.** pdfx bildet aus ihm die
`xmpMM:DocumentID` im XMP, als MD5-Summe über `\jobname.pdf`. Nachgemessen am
23.09.2026 mit TeX Live 2026: Alle fünf Beispielrechnungen, in beiden Designs
und mit Logo, unter drei Jobnamen gesetzt, unterscheiden sich in genau diesen
36 Bytes und sonst in keinem. Ein Test in `ToolchainIntegrationTest` hält das
fest. Der Jobname bleibt deshalb Rechnungsart und Nummer: So ist die
DocumentID je Rechnung verschieden, und kein schon erzeugtes PDF ändert sich.

Die drei Abbrüche tragen damit andere Begründungen:

- **`RECHNUNGSNUMMER_FEHLT` und `RECHNUNGSNUMMER_MIT_LEERRAUM`**: Andere
  Dateien nennen die Nummer später – die Schlussrechnung unter `abschlaege`,
  das Storno unter `storniert` –, und verglichen wird Zeichen für Zeichen
  (#53). Ein gequotetes `' R26-009'` ließe den Verweis still ins Leere gehen.
  Die alte Begründung des Leerraums trug übrigens nie: `fileBaseName`
  schneidet den Rand ab, `' R26-009'` ergab denselben Namen wie `'R26-009'`.
  (`RECHNUNGSNUMMER_FEHLT` gibt es seit #514 nicht mehr: Die leere Nummer
  bricht beim Einlesen ab.)
- **`RECHNUNGSNUMMER_OHNE_DATEINAMEN`**: Der Jobname ist der Name der `.tex`
  im Arbeitsverzeichnis und die Quelle der DocumentID. Eine Nummer, von der
  kein Name übrig bleibt, ergibt keinen. Die Kennung bleibt, wie sie heißt –
  ein Dateiname ist er ja weiterhin.

**Die Rechnung ohne Nummer bleibt ausgeschlossen, aber nicht mehr mit dem
Dateinamen.** Ob sie möglich werden soll, ist eine fachliche Frage (#54).
Stellt sie sich, ist der Jobname kein Hindernis mehr, sondern nur noch die
Frage, woraus dann die DocumentID entsteht.

Verworfen:

- **Ein fester Jobname** wie `rechnung`. Alle Rechnungen trügen dieselbe
  DocumentID, und jedes schon erzeugte PDF sähe beim nächsten Lauf anders aus.
  Gespart wären `fileBaseName`, sein Feld auf der `AcceptedInvoice` und
  `RECHNUNGSNUMMER_OHNE_DATEINAMEN`.
- **Ein fester Jobname und die DocumentID selbst gesetzt**, aus Art und
  Nummer. Das ginge nur über `\xmp@docid`, ein internes Makro von pdfx ohne
  zugesicherte Schnittstelle – eine Vereinfachung in Kotlin gegen einen
  Eingriff in ein fremdes Paket.

Umgesetzt in #297.
