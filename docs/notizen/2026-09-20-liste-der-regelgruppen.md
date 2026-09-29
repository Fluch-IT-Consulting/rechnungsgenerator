# Was die Liste der Regelgruppen zusammenhält

Notiz vom 20.09.2026, Stand 29.09.2026. **Hier wird nichts entschieden.**
Eine Architektur-Durchsicht fand: `InvoiceRules.check` ist eine
handgeschriebene Liste der Regelgruppen. Wer eine Gruppe anlegt und ihre
Zeile vergisst, prüft deren Regeln nie, und weder Compiler noch Test sagen
etwas. Vorgeschlagen war, die Gruppe zu einem Typ zu machen, der seine
Kennungen selbst nennt. Der Befund trägt nicht.

## Warum der Befund nicht trägt

**Das Netz liegt in `CheckHelpers`.** Jeder Regeltest geht über
`AcceptedInvoice.check` und damit über die Liste. Direkt ruft nur
`InvoiceKindRulesTest` seine Gruppe auf, und die steht nicht in der Liste,
sondern ist ein Parameter von `InvoiceRules.check` (#105). Fällt eine Gruppe
aus der Liste, liefert sie ihrem eigenen Test keine Befunde mehr, und
`messageOf` scheitert mit „Kein Befund zu X“. Wer die Regeltests einmal
direkt an den Gruppen ansetzt, nimmt dieses Netz weg und braucht ein anderes.

**Der Fall ist nie eingetreten.** Jede Gruppe kam mit ihrer Zeile im selben
Commit: die zehn aus #71, `InvoiceKindRules` (#70), `SkontoRules` (#183),
`CustomerRules`, heute `FreeTextRules` (#196), `LanguageRules` (#377) und
`CurrencyRules` (#409). `LanguageRules` ging mit #406 samt Zeile wieder.

## Gemessen am 29.09.2026

- **70 Kennungen, jede genau einmal vergeben**: 66 in den Gruppen, 4 in
  `prepayments/Prepayments.kt`, die nie durch `InvoiceRules` laufen. Die
  Zuordnung Regel → Gruppe ist also eine Funktion; sie steht nur nirgends, wo
  ein Compiler sie sähe.
- **Die Zwischenüberschriften in `Rule` ordnen nach Gegenstand**, nicht nach
  Gruppe. Sie waren nie eine Karte der Gruppen.

## Wenn die Frage wiederkommt

In dem Vorschlag stecken drei Zusagen: (a) keine Gruppe fällt aus dem Lauf,
(b) jede Kennung gehört zu genau einer Gruppe, (c) die Befunde kommen in der
Reihenfolge der Gruppen. (b) schließt (a) ein, umgekehrt nicht. Die
Reihenfolge bliebe am besten eine geordnete Liste, von der nur die
Vollständigkeit geprüft wird; ein Rang je Gruppe verteilte eine Angabe, die
niemand begründen kann, über vierzehn Dateien.

Für (b) gibt es drei Formen:

- **`Rule` trägt die Gruppe.** Der Enum-Konstruktor erzwingt sie, das ist
  eine Übersetzungsbedingung. Die Zuordnung steht dann aber nicht neben dem
  Code, der den Befund vergibt.
- **Die Gruppe nennt ihre Kennungen.** Näher am Code, aber eine vergessene
  Kennung fängt nur ein Test.
- **Nichts deklarieren**, die Zuordnung mit Konsist aus den Quellen ableiten
  wie `PackageStructureTest` die Importe. Nichts kann driften, dafür liest ein
  Test Quelltext, um eine Menge für einen anderen Test zu bilden.

Zwei Sonderfälle entscheiden mit: `InvoiceKindRules` liefert ein `Result` und
keine Befundliste (ADR-0008) und passt in keine Schnittstelle über `check`,
wohl aber in eine Angabe im Enum. Und die vier Kennungen aus `prepayments`
verlangen, dass eine Gruppe auch sagt, wo sie läuft – sonst braucht die
Vollständigkeitsprüfung eine Ausnahmeliste.

## `SKONTO_RULES` bleibt

Die Menge in `SkontoRulesTest` sieht aus wie eine zweite Liste neben
`SkontoRules`. Sie trägt aber die Zusage „und nur dieser Befund“: Die
Testrechnungen tragen absichtlich fremde Befunde – eine Skontostufe auf der
falschen Art hat zusätzlich eine zu späte Frist und soll trotzdem nur
`SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH` melden (#97). Der Filter nimmt die
fremden Befunde weg, damit `shouldBe listOf(…)` „nichts weiter“ behalten
kann. Die Liste ist der Preis dafür, dass eine Testrechnung fremde Befunde
tragen darf (ADR-0004).

**Anlass:** eine zweite Testdatei, die dieselbe Sorte Menge braucht; dann ist
die Zuordnung Regel → Gruppe neu zu fragen. Oder eine neue Skonto-Regel, die
in `SKONTO_RULES` fehlt: Der Filter wäre zu eng, und die Zusagen würden still
schwächer.

## Die Meldungen neben ihren Regeln

Nach ADR-0004 schreibt die Schicht `rules` die Sätze der Ausgabe selbst. #282
hat an allen Meldungen gemessen, ob sich der Preis zeigt: kein zweiter Kanal,
die eingesetzten Werte formatiert einheitlich ihr `toString()`, auseinander
liefen nur getippte Zahlen im Satz. Dagegen hält `CheckHelpers` jetzt ein
Netz, das jede Meldung eines Regeltests trifft – die vier aus `prepayments`
nicht.

**Anlass, neu zu fragen:** ein Leser der Befunde, der keinen Satz will, etwa
ein Export mit Kennung und Werten. Dann ist der Schritt dran, den #282
verwirft: Kennung plus Parameter, der Wortlaut an einer Stelle.

## Erwogen und verworfen

- **Den Umbau trotzdem bauen.** Er kaufte eine Zusage, die ein Netz schon
  hält, für 70 geänderte Enum-Zeilen.
- **`SKONTO_RULES` über das Namensschema bilden.** Der Name einer Kennung ist
  kein Ordnungsmerkmal; `SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH` gehört nur
  zufällig zur Vorsilbe.
- **Ein Strukturtest**, der die Liste gegen die `*Rules.kt`-Dateien
  vergleicht. Er prüfte dieselbe Zusage ein zweites und schwächeres Mal, weil
  er nur Dateinamen sieht.

## Nicht Teil dieser Frage

- Die Reihenfolge der Befunde als fachliche Sortierung, nach Schwere und
  Gegenstand. Sie änderte die Ausgabe jeder abgelehnten Rechnung und braucht
  einen eigenen Anlass.
