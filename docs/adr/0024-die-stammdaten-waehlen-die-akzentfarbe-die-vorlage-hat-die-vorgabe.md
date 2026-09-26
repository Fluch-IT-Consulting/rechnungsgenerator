# Die Stammdaten wählen die Akzentfarbe, die Vorlage hat die Vorgabe

Die Stammdaten können eine Akzentfarbe nennen: `akzentfarbe: 31AE34`, sechs
Hexadezimalziffern ohne `#`. Ohne Eintrag gibt es keine. Der Weg ins PDF:

- `RgbColor` in `core.values` prüft die Form und normalisiert auf
  Großbuchstaben. Ein `#` davor bricht ab, mit dem Hinweis, es wegzulassen.
- `SellerConfig.accentColor` liest den Schlüssel mit `AccentColorSerializer`:
  Fehlt er, gibt es keine Farbe; steht er ohne Wert da, bricht das Einlesen ab.
- Der Renderer liefert den Platzhalter `AKZENTFARBE`, die Farbe oder leer.
- Die Vorlage entscheidet mit `\ifblank`. `helsinki` färbt damit den
  Titelbalken und nimmt ohne Eintrag sein eigenes Rot, `8D313A`. `prag` hat
  keine Akzentfarbe und übergeht den Wert.

## Warum

**Die Farbe gehört zum Rechnungssteller, nicht zum Design.** Ein Logo in
Grün neben einem roten Balken passt nicht zusammen, und welches Grün passt,
weiß nur, wer das Logo kennt. Das ist dieselbe Frage wie bei der Anschrift:
eine Angabe der Stammdaten, die das Design setzt.

**Die Vorgabe steht in der Vorlage und nicht in Kotlin.** Farbe ist Satzbild
(ADR-0017). Stünde `8D313A` in `SellerConfig`, entschiede Kotlin das Aussehen
von `helsinki`, und `prag`, das keine Akzentfarbe hat, bekäme trotzdem eine.
So bleibt es bei der Richtung aus ADR-0011: Der Renderer liefert eine
einzelne Angabe, leer erlaubt, und die Vorlage entscheidet, was sie damit tut.

**Ohne `#`.** In YAML beginnt ein `#` nach einem Leerzeichen einen Kommentar:
`akzentfarbe: #31AE34` ist ein Schlüssel ohne Wert. Die Schreibweise mit `#`
ginge deshalb nur in Anführungszeichen, und dieselbe Farbe wäre einmal
richtig und einmal still verschwunden. Also gilt sie gar nicht, auch nicht in
Anführungszeichen.

**Ein Schlüssel ohne Wert bricht ab.** Bei `telefon` oder `webseite` heißt
ein leerer Wert still „keiner“. Bei `akzentfarbe` wäre das genau die Falle
von oben: Wer die Farbe gewohnt mit `#` schreibt, bekäme ohne Meldung das Rot
der Vorlage und sähe es erst am Blatt. Nur bei diesem Schlüssel beginnt ein
Wert üblicherweise mit `#`, deshalb ist nur er strenger. Das Gerüst dafür,
`valueWhenPresent` in `core.serialization`, muss an der Eigenschaft stehen:
Am Typ reicht kotlinx.serialization das `null` am Serializer vorbei.

## Verworfen

- **Die Farbe in der Logodatei**, als optionales `\definecolor{akzent}` wie
  `\logoskalierung` (ADR-0012). Kein Kotlin, und die Farbe stünde bei der
  Marke. Aber ohne Logo gäbe es keine eigene Farbe, der Name `akzent` wäre ein
  stiller Vertrag zwischen Fragment und jeder Vorlage, und ein Tippfehler fiele
  erst LuaLaTeX auf, nicht beim Einlesen.
- **Die Vorgabe in Kotlin** mit einem nicht nullbaren Feld. Ein leerer Wert
  fiele damit von selbst auf, aber das Aussehen von `helsinki` stünde in
  `SellerConfig`, siehe oben.
- **Ein leerer Wert als „keine Farbe“**, wie bei den anderen freiwilligen
  Schlüsseln. Einheitlicher, aber die Falle mit `#` bliebe offen.
- **Mehrere Farben** (Text, Gedämpftes, Linien). Die übrigen Farben von
  `helsinki` sind Grautöne und passen zu jedem Logo. Wer sie braucht, schreibt
  einen weiteren Schlüssel mit derselben Bauart.
- **Eine Prüfung des Kontrasts.** Weiß auf dem Grün eines Logos liegt leicht
  unter 3:1 und ist trotzdem lesbar. Das Programm urteilte über Geschmack, und
  man sieht es am Blatt.

## Konsequenzen

- **Die Vorlage von `einrichten` nennt die Vorgabe nur als Text.** Ihr Netz
  verlangt, dass eine eingetragene Vorgabe dieselben Stammdaten ergibt wie
  keine; `8D313A` ergäbe aber eine Farbe statt keiner.
- **Ohne Eintrag ist `helsinki` pixelgleich wie zuvor**, aber nicht
  byte-gleich: Die `.tex` enthält jetzt das `\ifblank`, und mit ihr ändert
  sich die Trailer-ID.
- **Dass die Farbe auf dem Blatt ankommt**, prüft `ToolchainIntegrationTest`
  am gerasterten Bild, mit und ohne Eintrag.
- **Ein neues Design** entscheidet selbst, ob es eine Akzentfarbe hat. Hat es
  eine, setzt es sie aus `<<AKZENTFARBE>>` und bringt seine eigene Vorgabe mit.
- **Die Meldung bei einer ungültigen Farbe** nennt Datei und Wert, aber nicht
  den Schlüssel, so wie bei der IBAN.

Umgesetzt in #371.
