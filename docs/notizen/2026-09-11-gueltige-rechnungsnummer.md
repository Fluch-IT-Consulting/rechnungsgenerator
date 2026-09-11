# Was eine gültige Rechnungsnummer ist

Notiz vom 11.09.2026, festgehalten für später. **Hier wird nichts entschieden.**
Fest steht nur die Richtung: Der Generator soll so viel wie möglich prüfen, aber
welche Rechnungsnummern er annimmt, legt der Rechnungssteller fest – nicht der
Code.

## Der Anlass

Ein Architektur-Review fand `trim().uppercase()` an drei Stellen: in Regel 7 der
Absetzung, in `prepaymentsOf` und in `offene-abschlaege.py`. Der erste Gedanke
war ein Wert-Typ, der diese Normalisierung an einer Stelle besitzt. Im Gespräch
stellte sich heraus, dass die Normalisierung selbst das Problem ist: Sie nimmt
an, dass `R26-009` und `r26-009` dasselbe Dokument sind. Das gilt für das
eigene Schema, aber nicht für jedes. #53 entfernt sie ersatzlos.

Übrig bleibt die Frage, welche Nummern der Generator überhaupt annehmen soll.

## Wo der Nummernkreis heute steckt

Der Generator kennt keinen. Für ihn ist `nummer` ein beliebiger Text; nur
`jobNameFor` macht daraus einen Dateinamen und bricht ab, wenn nichts übrig
bleibt. Das Wissen über den Nummernkreis liegt vollständig im Skill:

- **Schema und Vergabe**: `naechste-nummer.py`, mit `R{JJ}-{NNN}` als regulärem
  Ausdruck, dem Neubeginn zum Jahreswechsel und der Grenze bei 999.
- **Dateiname**: `SKILL.md`, Schritt 3. Er gehört dazu, weil das Skript die
  vergebenen Nummern über die Dateinamen findet – eine anders benannte Datei
  hält ihre Nummer nicht fest.
- **Eine Reihe für alle Rechnungsarten**: `SKILL.md:54-56`, als Satz.

## Was der Generator nicht prüfen kann

§14 Abs. 4 Satz 1 Nr. 4 UStG verlangt eine Nummer, die einmalig vergeben wird.
Einmalig ist eine Eigenschaft aller Rechnungen zusammen, und beim Erzeugen sieht
der Generator eine einzige Datei (ADR-0001). Die Vergabe gehört deshalb zur
Autorenzeit, wie die Notiz vom 10.09. es nennt: Die darf die Welt lesen, muss
ihr Ergebnis aber hinschreiben.

UStAE 14.5 Abs. 10 erlaubt Zahlen- und Buchstabenreihen, auch gemischt, und
hält fest: „Eine lückenlose Abfolge der ausgestellten Rechnungsnummern ist nicht
zwingend.“ Abs. 11 überlässt es dem Rechnungsaussteller, wie viele Nummernkreise
er bildet – etwa je Zeitraum oder Filiale –, verlangt aber, dass jede Rechnung
„leicht und eindeutig“ ihrem Nummernkreis zugeordnet werden kann, zum Beispiel
über eine Klassifizierung in der Nummer. Nachgelesen am 11.09.2026 in der
konsolidierten Fassung des UStAE (Stand 2. Juni 2026, S. 544), wortgleich mit
der Amtlichen Umsatzsteuer-Handausgabe 2023.

EN 16931 führt die Rechnungsnummer (BT-1) als Kennung, also als freien Text.

Prüfen kann der Generator also nur die Form einer Nummer, nie ihre
Einmaligkeit.

## Der Zielkonflikt

Auf der einen Seite: Eine widersprüchliche Rechnung soll nicht als PDF
hinausgehen (CLAUDE.md), und ein Tippfehler in einer Kennung fällt sonst erst
auf, wenn es zu spät ist – deshalb werden IBAN und USt-IdNr. schon heute
geprüft. Auf der anderen: Der Generator soll sich in verschiedene Abläufe
einbinden lassen und keinen vorschreiben (Notiz vom 10.09.). `R26-009` ist das
eigene Format; wer den Generator mit Nummern aus seiner Buchhaltung füttert, hat
ein anderes.

Konfigurierbarkeit löst beides auf: Geprüft wird, was der Rechnungssteller in
seinen Stammdaten als gültig festlegt.

## Mögliche Formen

**A. Ein regulärer Ausdruck in den Stammdaten.** Am flexibelsten und schnell
gebaut. Aber schwer zu lesen für den, der ihn einträgt, und die Meldung kann nur
sagen „passt nicht zu `^R\d{2}-\d{3}$`“.

**B. Ein Schema mit Platzhaltern**, etwa `R{JJ}-{NNN}`. Lesbarer, und es kann
mehr: das Jahr gegen das Rechnungsdatum prüfen, und `naechste-nummer.py` könnte
dasselbe Schema lesen, statt es fest einzubauen. Dafür braucht es eine kleine
eigene Sprache, und die wird gern größer als geplant.

**C. Ohne Eintrag keine Prüfung.** Wer nichts festlegt, bekommt das heutige
Verhalten. Das folgt aus „keinen Ablauf vorschreiben“ und lässt sich mit A oder
B verbinden.

Offen ist außerdem, ob ein Verstoß abbricht oder nur einen Hinweis erzeugt.
Legt der Rechnungssteller das Schema selbst fest, spricht viel für den Abbruch.

## Fragen, die daran hängen

- **Leerzeichen am Rand.** Nur ein gequoteter YAML-Wert kann sie tragen;
  ungequotet schneidet YAML sie selbst ab (nachgemessen am 11.09.2026). Eine
  Regel für jedes Schema, oder Sache des Schemas?
- **Leere Nummer.** `erzeuge` fängt sie über `jobNameFor` ab, mit einer Meldung
  über den Dateinamen statt über die Nummer. Im `abschlaege`-Block geht
  `rechnungsnummer: ""` durch. Ob leer überhaupt falsch ist, hängt am
  Rechnungssteller: Kleinunternehmer (§34a UStDV) und Kleinbetragsrechnungen
  (§33 UStDV) brauchen laut UStAE 14.5 Abs. 14 keine fortlaufende Nummer.
- **Die Nummern der Abschläge.** Sie stammen aus früheren Rechnungen, womöglich
  aus der Zeit vor einem Wechsel des Schemas. Gegen das heutige Schema geprüft,
  würde eine korrekte alte Nummer abgelehnt.
- **Ein Wechsel des Schemas überhaupt.** Stammdaten gelten für jede Rechnung;
  eine alte Rechnung neu zu erzeugen, scheiterte nach einem Wechsel. Für die
  Anschrift gilt Ähnliches schon heute, dort ändert sich aber nur das PDF.
- **Das Jahr in `naechste-nummer.py`.** Das Skript nimmt das Jahr von heute,
  nicht das des Rechnungsdatums. Eine Rechnung vom 31.12., die erst am 2.1.
  geschrieben wird, bekommt eine Nummer des neuen Jahres. Heute ist das
  kosmetisch, weil keine Dublette entsteht. Ein Schema, das `{JJ}` gegen das
  Rechnungsdatum prüft (Form B), würde genau diese Rechnung ablehnen.
- **Mehrere Nummernkreise bei anderen Rechnungsstellern.** UStAE 14.5 Abs. 11
  erlaubt sie, solange jede Nummer eindeutig einem Kreis zuzuordnen ist. Ein
  konfigurierbares Schema müsste dann mehr als ein Muster zulassen – oder ein
  Muster, das die Klassifizierung mit abdeckt.
- **Das Glossar.** „Rechnungsnummer“ und „Nummernkreis“ stehen nicht in
  `CONTEXT.md`. Bevor sie in den Code gehen, gehören sie dort hinein.

## Erwogen und verworfen

- **Ein Wert-Typ mit fest eingebautem Schema**, nach dem Vorbild von `Iban`.
  Er schriebe das eigene Format allen vor.
- **Ein austauschbarer Nummernkreis als Interface mit Factory.** Mit einem
  einzigen Schema wäre das eine vermutete Naht mit einem einzigen Adapter, also
  bloße Umleitung. Die Austauschbarkeit, um die es geht, liegt beim
  Rechnungssteller und gehört damit in die Stammdaten, nicht in eine
  Klassenhierarchie.

## Nicht Teil dieser Frage

- **Die Vergabe durch den Generator**, etwa als eigenes Unterkommando wie
  `abschlaege`. Das ist eine eigene Entscheidung; ein Schema in den Stammdaten
  wäre aber ihre Voraussetzung.
- **Mehrere Nummernkreise im eigenen Gebrauch.** `SKILL.md` schließt das aus.
