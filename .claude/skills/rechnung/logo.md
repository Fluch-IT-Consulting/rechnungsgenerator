# Logo anlegen

> Alle Beispiele in dieser Datei sind erfunden, wie in `SKILL.md`.

Ein Logo ist keine Bilddatei, sondern ein Stück LaTeX: eine TikZ-Zeichnung,
als Makro definiert (ADR-0012). Bringt der Nutzer ein Bild seines Logos, eine
PNG-, PDF- oder SVG-Datei oder einen Screenshot, zeichnest du es in TikZ nach.
Ein Bild per `\includegraphics` einzubinden bricht PDF/A-1, und der Generator
lehnt das PDF ab.

Die Datei heißt `logo.tex` und liegt neben den Stammdaten in
`.rechnungsgenerator/`. Die Stammdaten nennen sie mit `logo: logo.tex`, ein
relativer Pfad gilt ab ihrem Ordner. Beide Designs setzen das Logo oben links
auf der ersten Seite.

## Was die Datei enthält

```latex
\newfontfamily{\musterschrift}{texgyreheros}[
  Extension   = .otf,
  UprightFont = *-regular,
  BoldFont    = *-bold,
]
\definecolor{musterdunkel}{HTML}{1B2A4A}
\definecolor{musterhell}{HTML}{4FB3F0}
\definecolor{mustermittel}{HTML}{2F7FD8}
\newcommand{\logoskalierung}{1.2}
\newcommand{\logozeichnung}{%
  \shade[top color=musterhell, bottom color=mustermittel, rounded corners=3pt]
    (0,0) -- (0,2.6) -- (0.8,2.6) -- (0.8,0) -- cycle;
  \fill[musterdunkel] (1.2,0) -- (1.2,2.6) -- (2.0,2.6) -- (2.0,0) -- cycle;
  \node[anchor=south west, inner sep=0, font=\musterschrift\fontsize{40}{40}\selectfont\bfseries]
    at (2.8,0.95) {\textcolor{musterdunkel}{MUSTER}\textcolor{musterhell}{IT}};
  \node[anchor=south west, inner sep=0, text=musterdunkel,
    font=\musterschrift\fontsize{13}{13}\selectfont\addfontfeature{LetterSpace=30}]
    at (2.85,0.05) {CONSULTING};
}
```

- **`\logozeichnung`** ist Pflicht: der Rumpf einer `tikzpicture`, also nur
  die Zeichenbefehle, ohne `\begin{tikzpicture}`. Die Vorlage setzt die Umgebung
  selbst darum.
- **`\logoskalierung`** ist freiwillig, ein Faktor auf die Höhe, die das
  Design einem Logo gibt: 12 mm. `1.2` heißt 20 % größer. Ein breites Logo,
  etwa eine Wortmarke, wird auf 12 mm Höhe schmal und braucht ihn. Bei gut 2
  stößt es an die Angaben daneben.
- **Farben und Schrift** bringt die Datei selbst mit, mit `\definecolor` und
  `\newfontfamily`. TikZ, fontspec und xcolor lädt die Vorlage. Eine Bibliothek
  von TikZ lädst du bei Bedarf mit `\usetikzlibrary` nach.
- **Eigene Namen** für Farben, Schriften und Hilfsmakros tragen ein eigenes
  Präfix, im Beispiel `muster`.

## Vom Bild zur Zeichnung

Lies am Bild ab, woraus das Logo besteht: Grundformen und ihre Proportionen,
die Farben als Hexwert, Text samt Schriftcharakter (mit oder ohne Serifen,
Strichstärke, Sperrung). Die Einheit wählst du frei: Die Vorlage bringt die
ganze Zeichnung auf ihre Höhe, Schrift und Formen gemeinsam. Wichtig ist nur,
dass die Proportionen untereinander stimmen.

TikZ zählt die y-Achse nach oben, ein Bild nach unten. Wer Koordinaten aus
einem Bild abliest, setzt die Zeichnung in `\begin{scope}[yscale=-1]`.

**Eine SVG-Datei** liefert die Pfade fertig. Ihr Attribut `d` übernimmst du
mit der Bibliothek `svg.path`, ebenfalls gespiegelt:

```
\usetikzlibrary{svg.path}
\newcommand{\logozeichnung}{%
  \begin{scope}[yscale=-1]
    \fill[mustermittel] svg "M 10 10 L 90 10 L 90 40 C 90 70 60 90 50 90 C 40 90 10 70 10 40 Z";
  \end{scope}
}
```

Verläufe übernimmst du als `\shade`. Transparenz, Masken und eingebettete
Bilder der SVG gehen nicht, siehe unten.

Eine Nachzeichnung bleibt eine Annäherung. Sag dem Nutzer, wo sie abweicht,
etwa bei einer Schrift, die es in TeX Live nicht gibt, und lass ihn das
Ergebnis ansehen.

## Fallen

- **Keine Bilddatei.** Kein `\includegraphics`, auch nicht für einen Teil des
  Logos.
- **Keine Transparenz.** `opacity`, `fill opacity`, `draw opacity`, Überblendungen
  und `path fading` verbietet PDF/A-1. veraPDF meldet dann einen `ca`- oder
  `CA`-Schlüssel „other than 1.0“. Eine halbtransparente Fläche mischst du
  stattdessen als volle Farbe vor.
- **Jede Fläche hat ihre Farbe.** Ein `\fill` ohne Farbe nimmt die Textfarbe
  der Vorlage, ein Text ohne `text=` oder `\textcolor` ebenso.
- **Keine Farbe der Vorlage überschreiben.** `akzent`, `akzentweb`,
  `akzentalt`, `haupttext`, `gedaempft` und `linie` gehören der Vorlage. Ein
  `\definecolor` mit einem dieser Namen ändert still die ganze Rechnung.
- **Kein Makro der Vorlage neu definieren.** Die Vorlage hat eigene Namen mit
  `\logo…`, etwa `\logohoehe`. Deshalb trägt kein eigener Name dieses Präfix,
  außer `\logozeichnung` und `\logoskalierung`. Ein doppelt definierter Name
  bricht den Satz ab.
- **Nur Schriften aus TeX Live.** Einbinden wie im Beispiel, über den
  Dateinamen. Ob es eine Schrift gibt, sagt `kpsewhich <name>-regular.otf`.
  Naheliegend sind:
  - `texgyreheros`, ähnlich Helvetica
  - `texgyreadventor`, ähnlich Avant Garde
  - `texgyretermes`, ähnlich Times
  - `texgyrepagella`, ähnlich Palatino

  Die Fira Sans heißt anders: `FiraSans` mit `UprightFont = *-Regular`.

## Ablauf

**1. Zeichnen.** Schreib die Datei zunächst in ein eigenes Verzeichnis, nicht
nach `.rechnungsgenerator/`. Fertig, wenn sie alle Formen, Farben und Texte
des Bilds enthält.

**2. Prüfen.** Setz eine vorhandene Rechnung mit einer Kopie der Stammdaten,
die auf die neue Datei zeigt. So bleiben die echten Stammdaten unberührt, und
das archivierte PDF auch:

```
PROBE=$(mktemp -d)
cp "$RECHNUNGEN_DIR/.rechnungsgenerator/stammdaten.yaml" "$PROBE/"
cp logo.tex "$PROBE/"
echo "logo: logo.tex" >> "$PROBE/stammdaten.yaml"
rechnungsgenerator erzeuge "$RECHNUNGEN_DIR/Rechnung_R26-011-beispiel.yaml" \
  -s "$PROBE/stammdaten.yaml" -o "$PROBE/probe.pdf"
```

Steht in den Stammdaten schon ein `logo:`, ersetzt du die Zeile in der Kopie,
statt eine zweite anzuhängen. Gibt es noch keine Rechnung, prüfst du mit der
ersten, die du anlegst.

Die Ausgabe muss mit „PDF/A-1b: gültig“ enden. Bricht der Satz ab oder ist das
PDF/A ungültig, liegt es an der Logodatei: Die Meldung nennt sie, und die
Fallen oben sind die üblichen Gründe. Dann sieh dir die erste Seite von
`probe.pdf` an, du kannst PDFs selbst lesen. Mit poppler geht auch
`pdftoppm -r 90 -png -singlefile -f 1 -l 1 probe.pdf seite`. Sitzt das Logo zu
klein, hilft `\logoskalierung`. Fertig, wenn das PDF gültig ist und der Nutzer
das Logo auf der Seite gesehen hat.

**3. Eintragen.** Erst wenn der Nutzer zustimmt, kommt `logo.tex` nach
`.rechnungsgenerator/` und `logo: logo.tex` in die Stammdaten. Ersetzt sie ein
vorhandenes Logo, frag vorher. Ab dann tragen alle Rechnungen das Logo, auch
alte, die neu gesetzt werden. Die gültige Fassung einer alten Rechnung bleibt
ihr archiviertes PDF; überschreib es nicht.
