# Die Rechnungsdatei wählt die Währung, eine Fremdwährung nur ohne Umsatzsteuer

Eine Rechnung steht ganz in einer Währung. Welche, sagt die Rechnungsdatei
neben Art und Sprache: `waehrung: USD`, ohne Eintrag `EUR`. Die Währung ist
ein Code nach ISO 4217, `Currency` in `core.values`, eine geschlossene Menge:
`EUR`, `USD`, `GBP`. Jeder andere Wert bricht beim Einlesen ab.

- **Nur ohne Umsatzsteuer.** Trägt eine Rechnung in `USD` oder `GBP` eine
  Position der Kategorie S, bricht `WAEHRUNG_MIT_UMSATZSTEUER` ab. Jede andere
  Kategorie ist zulässig, auch Z und G.
- **Das Blatt.** Der Renderer liefert den Code als Platzhalter
  `<<WAEHRUNG>>`, die Vorlage reicht ihn als `\waehrung` an die Sprachdatei.
  Die kennt ein Zeichen je Code – `\zeichenEUR`, `\zeichenUSD`,
  `\zeichenGBP`, also €, $ und £ – und setzt es in `\betrag` an die Stelle
  ihrer Sprache: Deutsch `4.400,00 $`, Englisch `£8,217.50` und `−£200.00`.
- **Die E-Rechnung.** `InvoiceCurrencyCode` (BT-5) und die `currencyID` der
  Steuersumme tragen den Code. BT-6, die Währung der Umsatzsteuer, bleibt weg.
- **Die Schlussrechnung.** `rechnungsgenerator abschlaege` bricht ab, wenn die
  Abschlagsrechnungen in verschiedenen Währungen stehen
  (`ABSCHLAGSRECHNUNGEN_VERSCHIEDENE_WAEHRUNGEN`). Der Block nennt die
  Währung nicht; steht er nicht in Euro, erinnert ein Hinweis auf stderr an
  `waehrung:` in der Schlussrechnung.
- **Die Ablage.** `offene-abschlaege.py` meldet mit „Achtung“ eine
  Schlussrechnung, die einen Abschlag anderer Währung absetzt, und ein Storno
  in anderer Währung als die Rechnung, die es aufhebt.

## Warum

**Die Währung gehört zur Rechnung, nicht zum Betrag.** Jeder Betrag einer
Rechnung ist in ihrer Währung, und es gibt keine Währung je Position und
keine je Abschlag. Deshalb tragen `Money` und `Price` keine: Sie bekämen eine
Angabe, die auf jeder Rechnung überall gleich wäre, und jede Rechnung mit
ihnen müsste sie vergleichen.

**In der Rechnungsdatei, ohne Vorgabe in den Stammdaten.** Die Währung
vereinbart der Rechnungssteller mit dem Kunden, wie das Skonto. Eine Vorgabe
in den Stammdaten landete still auf jeder Rechnung. In der Rechnungsdatei
wandert sie mit, weil der `rechnung`-Skill die letzte Rechnung desselben
Kunden als Vorlage nimmt.

**Unabhängig von der Sprache.** Die Sprache bestimmt die Schreibweise, die
Währung das Zeichen: `de-DE` in `USD` und `en-GB` in `EUR` sind beide
gültig. Eine an die Sprache gebundene Währung könnte die Rechnung an eine
US-Tochter eines deutschen Kunden nicht schreiben.

**Nur ohne Umsatzsteuer, weil der Kurs fehlt.** Art. 230 MwStSystRL erlaubt
jede Währung, die geschuldete Steuer muss aber in Euro dastehen,
umgerechnet nach §16 Abs. 6 UStG mit dem Monatsdurchschnittskurs des BMF.
Maßgeblich ist der Monat der Leistung, bei einer Anzahlung und für jeden,
der nach vereinnahmten Entgelten versteuert (§20), der Monat der
Vereinnahmung – und der liegt oft nach dem Tag, an dem die Rechnung
entsteht. Ohne Steuer ist nichts umzurechnen, und die Rechnung nach UK oder
in die USA trägt meist O, AE oder G. Die Fundstellen stehen im Kommentar
vom 27.09.2026 in #409.

**Gefragt wird nach S und nicht nach dem Steuerbetrag.** S ist die einzige
Kategorie mit einem Satz über null (`REGELSATZ_MIT_NULL`). Eine Position über
0,00 machte die Rechnung sonst nur zufällig zulässig. Ein Befund für die
Rechnung und nicht je Position: Der Grund ist die Währung.

**Zeichen und Stellung in der Sprachdatei, die Währung als Platzhalter.**
Kotlin bestimmt die Währung, die Vorlage ihre Schreibweise – derselbe
Schnitt wie bei den Ziffern (ADR-0021). Die Währung ist eine einzelne Angabe
und kommt deshalb als Platzhalter (ADR-0017), einmal im Vorspann wie
`<<AKZENTFARBE>>`. Jeder Aufruf `\betrag{…}` bleibt, wie er war.

**$ und £ ohne Zusatz.** `$` steht auch für andere Dollar, aber der
Empfänger einer Rechnung in US-Dollar erwartet das Zeichen, das er kennt.
Welcher Dollar gemeint ist, sagt das XML in BT-5.

**`Template.of` verlangt ein Zeichen für jede Währung.** Die Sprachdatei
schlägt es mit `\csname` nach, und ein Name ohne Definition ist dort
`\relax`: Der Betrag stünde still ohne Zeichen da.

**Die Schlussrechnung prüft nichts, `abschlaege` schon.** Die
Schlussrechnung liest ihre Abschlagsrechnungen nicht (ADR-0001), also kann
sie nicht wissen, in welcher Währung sie standen. Das Kommando liest sie und
ist die letzte Stelle, die es weiß – daher Abbruch und Hinweis dort.
Dasselbe gilt für das Storno und seine Ursprungsrechnung; das sieht nur, wer
die ganze Ablage liest.

## Verworfen

- **Die Umsatzsteuer in Euro umrechnen**, mit Kurs und Quelle in der
  Rechnungsdatei. Der Kurs hängt an der Art der Rechnung und an der
  Versteuerung des Rechnungsstellers und steht beim Erzeugen oft noch nicht
  fest. Das ist ein eigenes Vorhaben, kein Teil dieses: #418.
- **`US$` oder der ISO-Code auf dem Blatt**, `1.000,00 USD`. Eindeutig, aber
  nicht das Zeichen, das der Empfänger erwartet, und in der Betragsspalte
  breiter.
- **Die Währung als Argument von `\betrag`**, `\betrag{USD}{8337.50}`. Der
  Code stünde an jedem Betrag, jede Vergleichsdatei änderte sich in jeder
  Zeile mit einem Betrag, und eine einzelne Angabe wäre plötzlich
  Wiederholtes.
- **Eine Währung in `Money` und `Price`**, siehe oben.
- **Die Währung im Block von `abschlaege`.** Die Schlussrechnung trägt sie
  selbst; ein zweiter Eintrag bräuchte eine Regel, die beide vergleicht.
- **Eine Bankverbindung je Währung.** Eine Zahlung in USD oder GBP auf ein
  Euro-Konto funktioniert, kostet aber Umtausch. Ob die Stammdaten mehrere
  Konten tragen, ist eine eigene Frage. Heute steht auf jeder Rechnung die
  Bankverbindung der Stammdaten, gleich in welcher Währung.

## Konsequenzen

- **Eine Rechnung ohne `waehrung` sieht aus wie zuvor**, bei 300 dpi
  pixelgleich über alle Beispieldateien, beide Designs und jede Sprache, aber
  nicht byte-gleich: Vorlage und Sprachdatei haben sich geändert, und mit
  ihnen die Trailer-ID. Die Abnahme in #409 verlangte Byte-Gleichheit; sie
  ist bewusst auf Pixelgleichheit gelockert.
- **Die englische Beispielrechnung steht in Pfund, die amerikanische in
  US-Dollar**, dazu `dollar-auf-deutsch.yaml`. Das Pflichtangaben-Netz prüft
  Zeichen und Stellung an allen dreien, je Design; Englisch in Euro zeigen
  `reverse-charge.yaml` und die Beispiele je Art auf Englisch.
- **Eine neue Währung** ist ein Eintrag in `Currency` und in jeder
  Sprachdatei jedes Designs ein `\zeichen…`, das `Template.of` verlangt, und
  im Pflichtangaben-Netz ein Zeichen in `Spelling`. Ein neues Design bringt
  je Sprachdatei alle Zeichen mit.
- **Die Meldung bei einem unbekannten Code** nennt Datei, Wert und die
  zulässigen Codes, aber nicht den Schlüssel, so wie bei `sprache`.
- **Eine Rechnung mit Umsatzsteuer in Fremdwährung** geht heute nicht. Wer
  sie braucht, stellt sie in Euro, bis die Umrechnung kommt (#418).

Umgesetzt in #409.

## Nachtrag vom 28.09.2026: Umsatzsteuer in Fremdwährung mit Umrechnung

Was hier unter „Verworfen“ als eigenes Vorhaben steht, ist mit ADR-0027
entschieden: Eine Rechnung in Fremdwährung weist Umsatzsteuer aus, wenn die
Rechnungsdatei Kurs und Monat unter `umrechnung` nennt, und die Steuer steht
dann auch in Euro da – auf dem Blatt und im XML (BT-6, BT-111). Damit gilt
dreierlei nicht mehr, wie es oben steht:

- **„Nur ohne Umsatzsteuer“** gilt nur noch auf Abschlags- und
  Schlussrechnung (`WAEHRUNG_MIT_UMSATZSTEUER_AUF_DIESER_ART_NICHT_MOEGLICH`).
  `WAEHRUNG_MIT_UMSATZSTEUER` bricht nur noch ab, wenn die Umrechnung fehlt.
- **„BT-6 bleibt weg“** gilt nur ohne Umrechnung.
- **„Eine Rechnung mit Umsatzsteuer in Fremdwährung geht heute nicht“**
  gilt nicht mehr für Rechnung, Teilrechnung und Storno.

Die Gründe dieses ADR bleiben, wie sie sind; der Titel auch, damit Verweise
auf ihn nicht brechen.
