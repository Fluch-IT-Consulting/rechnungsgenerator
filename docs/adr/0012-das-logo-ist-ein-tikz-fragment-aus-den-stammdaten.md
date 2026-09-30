# Das Logo ist ein TikZ-Fragment aus den Stammdaten

Die Stammdaten nennen das Logo als Datei: `logo: logo.tex`, ein relativer Pfad
gilt ab dem Verzeichnis der Stammdaten. Ohne Eintrag gibt es kein Logo. Die
Datei ist ein LaTeX-Fragment, das die Zeichnung als Makro definiert.

Der Weg ins PDF:

- `YamlLoader.loadSellerConfig` liest die Datei beim Einlesen. `SellerConfig`
  trägt sie als `Logo` mit Pfad und Bytes. Fehlt sie oder hat sie eine andere
  Endung als `.tex`, bricht das Einlesen ab und nennt beide Dateien.
- Der Renderer liefert den Platzhalter `LOGO` als Schalter, leer oder `logo`,
  und die Datei als `logo.tex` in `RenderedInvoice.files` – aber nur, wenn die
  Vorlage `<<LOGO>>` enthält.
- `LatexCompiler` legt die Datei neben die `.tex`, und sie geht mit Namen und
  Inhalt in die Trailer-ID ein.
- Die Vorlage liest sie mit `\input` in der Präambel, setzt die Zeichnung in
  eine `tikzpicture` und bestimmt Ort und Höhe. `prag` setzt das Logo,
  `helsinki` übergeht es.

Der Vertrag des Fragments (seit #199):

```latex
\newfontfamily{\markenschrift}{…}[…]     % Schrift und Farben selbst
\definecolor{markenblau}{HTML}{…}
\newcommand{\logoskalierung}{1.2}        % optional, Vorgabe 1
\newcommand{\logozeichnung}{…}           % Pflicht: der Rumpf der tikzpicture
```

- `\logozeichnung` zeichnet in TikZ.
- `\logoskalierung` ist ein Faktor auf die Höhe, die das Design einem Logo
  gibt: `1.2` heißt 20 % größer. Er ist relativ und damit vom Design
  unabhängig. Ohne ihn gilt 1 (`\providecommand` in der Vorlage).
- Farben (`\definecolor`) und Schrift (`\newfontfamily`) bringt das Fragment
  selbst mit. Die Vorlage sichert nur zu, dass TikZ, fontspec und xcolor
  geladen sind.
- Eigene Hilfsmakros benennt es so, dass sie nicht mit der Vorlage
  kollidieren. Ein doppelt definierter Name bricht laut ab, nicht still.

## Warum

**Ein Fragment ist kein mitgebrachtes Design.** ADR-0011 hat mitgebrachte
Vorlagen verworfen, weil niemand sieht, ob sie jede Rechnungsart vollständig
setzen. Ein Logo bekommt keine Werte, ruft keine Makros des Renderers und hängt
an keiner Rechnungsart. Es kann eine Rechnung nicht unvollständig machen, nur
hässlich oder ungültig – und ungültig fängt veraPDF.

**TikZ und keine Bilddatei.** Der Treiber `luatex.def` von graphicx gibt jedem
über `\includegraphics` eingebundenen PDF eine Transparenzgruppe mit
(`\Gread@transgrouptrue`), und die verbietet PDF/A-1 (ISO 19005-1, 6.4).
Nachgemessen mit veraPDF 1.30 in #197: Ein sauberes Vektor-PDF ist nach dem
Einbinden nicht mehr konform, ohne die Gruppe ist es das wieder. Ein TikZ-Fragment
erzeugt keine. PDF und PNG bleiben möglich, die Endung bestimmt dann die Art;
sie brauchen eine Umdefinition von `\Gread@pdf` und die Prüfung auf
Alphakanal und CMYK.

**`\input` einer Datei statt eines Werts.** Das Fragment geht nicht durch die
Werte-Map, also weder durch die Maskierung noch durch `checkLineBreaks` oder
`fillIn`. Ein Fehler darin nennt im Protokoll `logo.tex`, nicht eine Zeile des
erzeugten `.tex`. Und der Weg – eine Datei neben die Quelle legen und in die
Prüfsumme nehmen – ist der, den ein Bild später auch braucht.

**Die Vorlage entscheidet, der Renderer befolgt sie.** Ob ein Design das Logo
setzt, weiß nur seine Vorlage. Der Renderer fragt ihren Platzhalter, nicht das
Design; dieselbe Richtung wie bei `fillIn` seit #188: Jeder Platzhalter der
Vorlage bekommt einen Wert, aber keine Vorlage muss jeden Wert setzen. Eine
Vorlage ohne `<<LOGO>>` bekommt die Datei gar nicht erst. So ergibt `helsinki`
mit und ohne Logo-Eintrag Byte für Byte dasselbe PDF.

**Der Faktor steht in der Logodatei (#199).** Die Vorlage bringt jedes Logo
auf dieselbe Höhe, und ein breites wird dabei klein: Eine Wortmarke, dreimal so
breit wie hoch, behält in `prag` gut 46 mm, und ihre Unterzeile ist kaum
lesbar. Wie breit ein Logo ist, kann die Vorlage nicht wissen – das gehört zum
Logo und nicht zum Design, also auch der Faktor. Er braucht so keinen Schlüssel
in den Stammdaten, keinen Platzhalter und keine Zahl mit Punkt statt deutschem
Komma aus dem Renderer. Und die Definitionen stehen in der Präambel, wo
`\newfontfamily` hingehört. Bis #199 war die ganze Datei der Rumpf der
`tikzpicture` und lief darin.

**`\directlua` läuft auch ohne `--shell-escape`.** Wer das Fragment schreibt,
kann damit Code ausführen. Das ist hinnehmbar: Stammdaten und Fragment gehören
demselben Menschen, der das Programm aufruft, und er könnte dasselbe auch ohne
den Umweg.

## Verworfen

- **Das Beispiellogo als Vorgabe ohne Eintrag.** Auf der Rechnung eines anderen
  Rechnungsstellers stünde eine erfundene Firma.
- **Ein Hinweis, wenn das Design kein Logo setzt.** Der Renderer müsste die
  Designs kennen, oder dieselbe Tatsache stünde in Kotlin und in der Vorlage.
- **Das Fragment als roher Wert durch die Werte-Map.** Es bräuchte einen eigenen
  Typ für unmaskiertes LaTeX, eine Ausnahme bei `checkLineBreaks` und ein Verbot
  von `<<`, damit `fillIn` nichts darin ersetzt.
- **Ein absoluter Pfad in `\input`.** `escapeLatex` macht aus `_` ein `\_`, und
  das kennt `\input` nicht; dazu `%`, `#` und Leerzeichen in Verzeichnisnamen.
  Eine zweite Maskierung nur für Pfade, statt die Datei unter festem Namen
  danebenzulegen.
- **Base64 in den Stammdaten.** Unlesbar, und das Nachbauen einer alten
  Rechnung hängt ohnehin schon an den Stammdaten.
- **Nur die Dateien in die Trailer-ID, die LuaLaTeX tatsächlich gelesen hat**
  (`-recorder`). Die ID muss vor dem Lauf feststehen, die Liste der gelesenen
  Dateien gibt es erst danach.
- **`erzeuge` sucht im `.tex` nach `\input{logo}`.** Das Kommando wüsste dann,
  wie Vorlagen ein Logo einbinden.
- **Ein Schlüssel `logo-skalierung` in den Stammdaten** (#199). `YamlLoader`
  hätte den Bereich prüfen können, aber der Faktor gehörte dann nicht zum
  Logo, und der Renderer bräuchte einen Platzhalter mit englischem
  Dezimalpunkt.
- **Der Faktor in der Rechnungsdatei.** Alle Rechnungen eines
  Rechnungsstellers sehen gleich aus (ADR-0011).
- **Eine Begrenzung des Faktors in der Vorlage** mit `\ifdim`: Rechnen in TeX
  für einen Fehler, den man sofort am Blatt sieht. Die Grenze nennt der
  Kommentar der Vorlage – in `prag` gut 2, dann reicht das Logo bis zur
  letzten Kopfangabe.
- **Eine Probe des Fragments in einem eigenen LuaLaTeX-Lauf.** Ein zweiter
  Satzweg bei jedem Aufruf. Stattdessen nennt die Meldung eines gescheiterten
  Satzes oder eines ungültigen PDF/A das Logo samt Pfad.

## Konsequenzen

- **Wer das Logo austauscht und eine alte Rechnung nachbaut, bekommt das neue
  Logo** – so wie heute eine geänderte Anschrift. Die gültige Rechnung ist das
  archivierte PDF.
- **Stammdaten mit einem Verweis ins Leere sind kaputt**, auch für
  `abschlaege`, das das Logo nicht braucht.
- **`parseSellerConfig` lehnt einen Logo-Eintrag ab**: Ohne Datei gibt es kein
  Verzeichnis, gegen das der Pfad gälte. Tests setzen das Logo über `copy()`.
- **`SellerConfig` trägt den Pfad zweimal**: `logoFile`, wie er in der YAML
  steht, und `logo`, gelesen. Eine eigene Klasse nur für die YAML hätte das
  vermieden, um den Preis eines Umbaus aller Stammdaten-Felder für eines.
- **Die Vergleichsdateien zeigen das Logo nicht**, nur `\input{logo}` und
  `\logozeichnung`. Ob es richtig gesetzt wird, prüfen `PflichtangabenTest`
  (prag mit Logo, jede Art) und `ToolchainIntegrationTest`, der auch misst,
  dass der Faktor ankommt. (Seit #434 steht das Pflichtangaben-Netz in vier
  Klassen, gemeinsam in `Pflichtangaben.kt`.)

Umgesetzt in #198, vorbereitet in #197; der Vertrag mit Makro und Faktor
seit #199.

## Nachtrag vom 26.09.2026: Auch helsinki setzt das Logo

Seit #370 setzt auch `helsinki` das Logo, oben links auf der Höhe der
Firmierung, nur auf der ersten Seite. Zwei Sätze oben gelten damit nicht mehr:
dass `helsinki` es übergeht, und dass `helsinki` mit und ohne Logo-Eintrag
dasselbe PDF ergibt.

Der Vertrag des Fragments bleibt, wie er ist, samt `\logoskalierung` und
derselben Grundhöhe von 12 mm wie in `prag`. Ein Logo, dessen Faktor für
`prag` gewählt ist, passt so auch in `helsinki`; ein Faktor je Design hätte
den Vertrag zweigeteilt, für eine Frage, die das Blatt beantwortet.

Die Richtung bleibt ebenfalls: Der Renderer fragt den Platzhalter und nicht
das Design. Nur übergeht heute keines der Designs das Logo. Dass eine Vorlage
ohne `<<LOGO>>` keine Datei bekommt, prüft deshalb kein Test mehr am Renderer,
nur noch `TemplateTest` an `uses`.

Ohne Logo ist `helsinki` bei 300 dpi pixelgleich mit dem Stand davor, aber
nicht byte-gleich: Die `.tex` ist eine andere, und mit ihr die Trailer-ID. Das
Pflichtangaben-Netz setzt jetzt beide Designs mit Logo, bei jeder
Rechnungsart. Den Fall ohne Logo trägt dort der Lauf für das
Einzelunternehmen.
