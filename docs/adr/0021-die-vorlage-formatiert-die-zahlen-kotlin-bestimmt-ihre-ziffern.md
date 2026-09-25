# Die Vorlage formatiert die Zahlen, Kotlin bestimmt ihre Ziffern

Beträge, Einzelpreise, Mengen und Prozentsätze kommen als Makroaufruf mit der
Zahl in kanonischer Form in die Vorlage: `\betrag{8337.50}`,
`\prozent{19}`, `\menge{86.5}`. Kanonisch heißt: Punkt als Dezimalzeichen,
keine Gruppierung, kein Exponent, `-` für negative Werte – das, was
`BigDecimal.toPlainString()` liefert. Die Vorlage definiert die drei Makros
mit siunitx und entscheidet damit über Dezimalzeichen, Tausendertrenner,
Minuszeichen, Währungs- und Prozentzeichen und den Abstand davor.

Die Grenze läuft zwischen den Ziffern und ihrer Schreibweise:

- **Welche Ziffern dastehen, entscheidet Kotlin.** `Money` mit genau zwei
  Nachkommastellen, `Price` mit mindestens zwei und allen, die der Wert trägt
  (#330), Menge und Satz ohne überflüssige Nullen. Die Vorlage zeigt jede
  Ziffer, die sie bekommt, und keine weitere.
- **Wie sie aussehen, entscheidet die Vorlage.** Jede setzt dafür ihr eigenes
  `\sisetup`.

Daten bleiben in `GermanFormat`: Ob ein Zeitraum „Juli 2026“ heißt oder zwei
Daten nennt, ist eine Entscheidung nach §31 Abs. 4 UStDV und kein Satzbild.

## Warum

**Die Schreibweise einer Zahl ist Satzbild.** ADR-0017 legt Satzbild in die
Vorlage, aber `GermanFormat` lieferte `8.337,50~€` und `19~\%` fertig: wo
das `€` steht, welcher Abstand davor, wie gruppiert wird. Ein Design mit
„EUR“, mit dem Zeichen vor der Zahl oder mit einem schmalen Tausendertrenner
hätte am Renderer bauen müssen.

**Die Ziffern sind es nicht.** Dass ein Einzelpreis von 0,00001 € nicht als
0,00 € auf dem Blatt steht, ist die Zusage, dass sich eine Rechnung
nachrechnen lässt (#14, #330). Sie hängt an `Money` und `Price`, und die
kennt nur Kotlin. Deshalb füllt Kotlin auch die Nachkommastellen auf: `0.50`
und nicht `0.5` mit `minimum-decimal-digits = 2` in der Vorlage. Beträge und
Einzelpreise teilen sich ein Makro, und die Vorlage könnte sie nicht
auseinanderhalten.

**siunitx rundet in der Vorgabe nicht** (`round-mode = none`). Dass eine
Vorlage es trotzdem könnte, unterscheidet sie nicht von einem selbst
geschriebenen Makro: Auch ein `\betrag`, das eine fertige Zeichenkette bekommt,
kann Unfug damit treiben. Schützen kann in beiden Fällen nur ein Netz am
fertigen PDF, nicht der Ort, an dem formatiert wird. Das Netz ist der
`PflichtangabenTest`: Er formatiert die erwarteten Werte selbst und liest sie
je Design mit `pdftotext` zurück.

**Die Vorgaben von siunitx passen nicht, und nicht jede fällt auf.** Nachgemessen
am 25.09.2026 mit siunitx 3.5.6, LuaLaTeX und TeX Gyre Heros:

| Einstellung | Vorgabe | ergibt | deshalb |
|---|---|---|---|
| `mode` | `math` | Ziffern aus Computer Modern | `text` |
| `group-digits` | `all` | `0,335.0` für `0.3350` | `integer` |
| `reset-text-series` | `true` | `\textbf{\betrag{…}}` nicht fett | `false`, ebenso `-shape` und `-family` |
| `group-minimum-digits` | `5` | `8337,50` ohne Punkt | `4` |

`0,335.0` liest ein deutscher Leser als dreihundertfünfunddreißig, und kein
Test hätte es bemerkt: Das Netz las Einzelpreise nicht zurück, und kein
Beispiel hatte einen mit mehr als zwei Nachkommastellen. Mit diesen
Einstellungen, `output-decimal-marker = {,}` und `group-separator = {.}` ist
die Ausgabe zeichengleich mit der von `GermanFormat`. Das Minus setzt siunitx
in `mode = text` als `\textminus`, und `pdftotext` liest U+2212 – das Zeichen
aus #320.

## Verworfen

- **Kotlin formatiert weiter, die Vorlage setzt nur das Zeichen dahinter**
  (`\betrag{8.337,50}`). Das war die kleinere Änderung und hielt die
  Schreibweise der Ziffern in Kotlin, aber auch Dezimalzeichen und
  Tausendertrenner sind Satzbild. Als sicherer galt sie nur, weil ein Makro
  mehr Mühe hätte, eine fertige Zeichenkette zu verderben – das ist ein
  Unterschied im Aufwand und kein Schutz.
- **Ein eigenes Makro statt siunitx.** Gruppieren in TeX von Hand ist ein
  Zahlenparser, den niemand sonst benutzt. siunitx gehört zu TeX Live
  (`collection-mathscience`) und steckt in `mactex-no-gui`, das die
  Homebrew-Formel nennt.
- **`mode = match`.** Gerät eine Zahl doch einmal in den Mathematikmodus,
  kämen ihre Ziffern dort aus der Mathematikschrift und nicht aus der des
  Designs. `mode = text` schließt das aus.
- **Das Euro-Zeichen als Einheit von siunitx** (`\qty{8337.50}{€}`). Das
  brächte eigene Vorgaben für den Abstand zwischen Zahl und Einheit und für
  die Schrift der Einheit mit, die ebenfalls festzulegen wären – für nichts,
  was `\num{#1}~€` nicht auch leistet.
- **Nackte Zahlen in die Makros der Tabelle**, `\nettozeile{8337.50}`, und
  die Vorlage weiß aus dem Makro, was sie bekommt. Die Rabattspalte trägt
  mal einen Satz, mal einen Betrag, und in den fachlichen Sätzen (Absetzung,
  Skonto, Steuerbefreiung) stehen Zahlen mitten im Text. Beides braucht den
  Makroaufruf aus Kotlin, und eine Regel für alle Zahlen ist einfacher als
  zwei.
- **Ein gemeinsames `\sisetup` für alle Designs**, etwa in einer eigenen
  Datei. Ein Design ist eine Vorlage (ADR-0011) und wählt damit auch sein
  Zahlformat. Dass keines dabei eine der stillen Vorgaben übernimmt, prüft
  das Netz je Design.

## Konsequenzen

- **Wer ein Design baut, übernimmt den ganzen `\sisetup`-Block**, nicht nur
  die Trennzeichen. Jede Zeile der Tabelle oben ist ein Fehler, den die
  Vorgabe macht, wenn die Einstellung fehlt.
- **Der `PflichtangabenTest` liest auch Einzelpreise zurück**, und ein
  Beispiel trägt einen Einzelpreis mit mehr als zwei Nachkommastellen.
- **Kotlin übergibt `toPlainString()`, nie `toString()`.** Nach
  `stripTrailingZeros` wird aus `1000` ein `1E+3`, und siunitx setzte daraus
  1 × 10³.
- **`GermanFormat` formatiert nur noch Daten.** Die Zahlen gehen als
  `\betrag`, `\prozent` und `\menge` durch `TemplateMacro`, auch innerhalb der
  fachlichen Sätze. `\%` schreibt allein die Vorlage.
- **Das Blatt bleibt gleich, die Vergleichsdateien nicht.** Jede Zahl im
  `.tex` steht jetzt als Makroaufruf da; beide Designs müssen bei 300 dpi
  pixelgleich zum Stand davor setzen.
- **Ausrichtung am Dezimalkomma wird möglich, aber nicht umsonst.** Eine
  `S`-Spalte von siunitx richtet nur Zahlen aus, die sie selbst liest; ein
  Makroaufruf in der Zelle ist für sie Text. Wer die Ausrichtung will, steht
  vor der Frage der nackten Zahlen noch einmal – dann mit einem Grund, den es
  heute nicht gibt.

Entschieden in #353.
