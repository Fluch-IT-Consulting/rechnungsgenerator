# Der Rabatt mindert die Position, nicht den Einzelpreis

Eine Position kann einen Rabatt tragen: `rabatt: 30` heißt 30 %. Er mindert
den Positionsbetrag, der Einzelpreis bleibt so stehen, wie er vereinbart ist.
In der Sprache von EN 16931 ist das ein Abschlag auf Positionsebene (BG-27):

- Basis (BT-137) = Menge × Einzelpreis, auf Cent gerundet,
- Rabattbetrag (BT-136) = Basis × Satz (BT-138), auf Cent gerundet,
- Positionsbetrag (BT-131) = Basis − Rabattbetrag.

Zulässig ist ein Satz über 0 % bis einschließlich 100 %
(`RABATT_AUSSERHALB_BEREICH`), auf jeder Rechnungsart. Im PDF steht er in einer
eigenen Spalte zwischen Einzelpreis und Betrag, und die gibt es nur, wenn eine
Position einen Rabatt hat. Im Summenblock steht er nicht.

## Warum

**Abschlag auf die Position und nicht Preisnachlass.** EN 16931 kennt einen
zweiten Weg: Bruttopreis (BT-148) minus Preisnachlass (BT-147) ergibt den
Einzelpreis (BT-146), und mit dem wird gerechnet. Dann stünde in der Spalte
Einzelpreis der geminderte Preis, und der vereinbarte fände sich nirgends auf
dem Blatt. Beim Abschlag bleibt der Einzelpreis ehrlich, und man sieht, was
gemindert wurde. Auch 100 % lassen sich so ausdrücken: eine Leistung, die auf
der Rechnung steht, aber nichts kostet.

**Das Netto ist die Differenz.** Basis × (1 − Satz) gerundet ergäbe mit dem
gerundeten Rabattbetrag nicht immer die Basis: 50 % von 10,05 € sind 5,025 €,
beides gerundet 5,03 €, zusammen 10,06 €. `HALF_UP` rundet weg von der Null,
deshalb hebt ein Storno mit negativer Menge (ADR-0006) auch den Rabatt auf den
Cent genau auf.

**Die Spalte ist ein Schalter, die Vorlage setzt sie.** Der Renderer liefert
den Rabatt als siebtes Argument von `\positionszeile`, leer ohne Rabatt, und
den Platzhalter `RABATTSPALTE`, leer oder nicht. Ob und wie die Spalte
dasteht, entscheidet die Vorlage mit `\ifblank` – so, wie #188 es für jede
Angabe festgelegt hat, und so, wie es beim Logo (ADR-0012) schon geht.
`RABATTSPALTE` ist Pflicht für jede Vorlage: Ein Design ohne ihn zeigte einen
Betrag, der nicht Menge × Einzelpreis ist, ohne Grund.

## Verworfen

- **Preisnachlass auf den Einzelpreis (BT-147/148)**, siehe oben.
- **Ein fester Rabattbetrag** neben dem Prozentsatz. BG-27 erlaubt ihn, aber
  es gibt keinen Anlass; er kommt dazu, wenn es einen gibt.
- **Eine Zeile „Rabatt“ im Summenblock.** Sie sähe aus wie ein Abschlag auf die
  ganze Rechnung (BG-20), und das ist ein anderes Ding: Der mindert die
  Bemessungsgrundlage je Steuersatz und bräuchte eine eigene Aufteilung.
- **Ein Grund als Freitext** (BT-139). BR-42 verlangt Grund oder Code; der
  spätere ZUGFeRD-Export setzt fest den Code 95 („Discount“, UNTDID 5189).
- **Ein Rabatt von 0 %** als zulässiger Wert. Er schaltete die Spalte ein, ohne
  etwas zu mindern.

## Konsequenzen

- **`CalculatedLine` rechnet selbst.** Im Konstruktor steht nur die Position,
  Basis, Rabattbetrag und Netto rechnet der Rumpf – wie `CalculatedInvoice`
  seit #172. `discountAmount` ist ohne Rabatt `null`, nicht null Euro: Der
  Export schreibt einen Abschlag nur, wenn es einen gibt.
- **Jede Vorlage kennt `\positionszeile` mit sieben Argumenten** und setzt
  `<<RABATTSPALTE>>`. Ohne Rabatt ist das Satzbild dasselbe wie vorher,
  nachgemessen mit `pdftoppm` an allen Beispielen beider Designs.
- **Der Rabatt ist keine Entgeltminderung nach §17 UStG.** Er steht auf der
  Rechnung und ist im Entgelt schon berücksichtigt; ADR-0006 lässt nur die
  nachträgliche Minderung draußen. Auch §14 Abs. 4 Nr. 7 verlangt ihn nicht,
  das Netz (`PflichtangabenTest`) prüft die Spalte trotzdem.
- **Eine Rechnung kann 0 € ergeben**, wenn jede Position 100 % trägt. Was sie
  dann fordert, ist eine eigene Frage (#214).

Umgesetzt in #213.
