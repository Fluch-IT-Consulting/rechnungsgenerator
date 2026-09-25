# Die Absprachen des Satzes stehen in einer Grundlage, das Satzbild in der Vorlage

Jede Vorlage lädt direkt nach `\documentclass` die Grundlage,
`templates/grundlage.sty`. Darin steht, was ein Design nicht anders setzen
darf, und nichts sonst:

| In der Grundlage | weil ein Design sonst |
|---|---|
| pdfx mit `a-1b`, ohne Lesezeichen | kein PDF/A-1b ergibt oder einen Lauf zu viel verlangt (#321) |
| `Ligatures=TeXOff` für die Schriftfamilien | Text aus den Eingabedaten verändert, `--` wird `–` (#81) |
| aus `\sisetup`: `mode`, `group-digits`, `round-mode`, `reset-text-*` | eine Zahl falsch setzt, siehe ADR-0021 |
| `\frenchspacing` und `\let\normalsfcodes\frenchspacing` | nach „Dr.“ und „USt-IdNr.“ ein Satzende setzt (#143, #353) |
| `\ungeteilt` | einen fachlichen Satz über zwei Seiten bricht (ADR-0020) |
| `\programmsatz`, der breite Abstand nach einem Satzende | ihn an Eingabedaten vergibt oder den eigenen Sätzen vorenthält (#143) |
| `\wennweitereseite`, die Bedingung des Fortsetzungshinweises | ihn auf der letzten Seite setzt oder auf einer vorletzten nicht (ADR-0020) |
| `LTchunksize` | auf jeder Seite andere Spaltenbreiten hätte (ADR-0020) |

Alles andere bleibt in der Vorlage, auch wo es heute in beiden gleich steht:
die Schreibweise der Zahlen (`output-decimal-marker`, `group-separator`,
`group-minimum-digits`, `\betrag`, `\menge`, `\prozent`), Silbentrennung,
Abstände wie `\LTpre` und der über `\hinweisabsatz`, Ort, Grad und Wortlaut
des Fortsetzungshinweises, `\zeilenwechsel`, `\summenzeile`.

Die Frage für jede Zeile ist: **Wäre ein Design mit einer anderen Zeile
falsch oder nur anders?** Nur was falsch wäre, gehört in die Grundlage. Dass
eine Zeile in allen Vorlagen gleich steht, genügt nicht.

Zwei Regeln halten die Grundlage klein:

1. **Kein Platzhalter.** Sie ist für jede Rechnung dieselbe Datei. Was an der
   Rechnung hängt (`<<RABATTSPALTE>>`, `<<LOGO>>`), fragt die Vorlage.
2. **Kein Makro aus `TemplateMacro`.** Die Grundlage liefert Bausteine, und
   jedes Makro, das der Renderer aufruft, definiert die Vorlage selbst –
   `\hinweisabsatz` etwa aus ihrem Abstand, `\ungeteilt` und `\programmsatz`.
   So bleibt `checkMacros`, wie es ist, und der Volltext einer Vorlage enthält
   weiter jede Definition, die der Renderer aufruft (Nachtrag zu ADR-0011).

`Template.of` prüft, dass die Vorlage die Grundlage als Erstes lädt und dass
die Grundlage beide Regeln einhält. Geliefert wird sie wie das Logo über
`PdfSources.files`, bei jeder Rechnung.

## Warum

**Die Absprachen waren abgeschrieben.** Von den Zeilen ohne Kommentar standen
72 wortgleich in `helsinki.tex` und `prag.tex`, und `prag.tex` verwies ein
Dutzend Mal mit „siehe helsinki.tex“ auf die Begründung. Warum `prag` so gesetzt
ist, stand also in einem anderen Design, und ein drittes wäre von `helsinki`
abgeschrieben worden – samt Satzbild, das es nicht gewählt hat.

**Das Netz sieht Zeichen, keine Schriften.** ADR-0021 verwarf ein
gemeinsames `\sisetup`, weil das Netz je Design prüfe, dass keines eine
stille Vorgabe übernimmt. Das stimmt nur für einen Teil. Nachgemessen am
25.09.2026: Mit `mode = math` und `reset-text-series = true` liest
`pdftotext` zeichengleich dasselbe wie mit den richtigen Einstellungen, nur
kommen die Ziffern dann aus Computer Modern (`CMR10` in `pdffonts`), und der
Gesamtbetrag steht nicht fett. Kein Test las das zurück, und einer, der
Schriften je Zeichen vergleicht, prüfte eine Absprache, die sich auch einfach
einmal hinschreiben lässt.

**Nach dem Komma wird nicht getrennt**, weder durch einen Punkt noch durch
Leerraum. Gruppiert wird nur vor dem Komma (`group-digits = integer`), und
womit, wählt das Design. Mit Punkt läse ein deutscher Leser `0,033.5` als
eine andere Zahl; mit Leerraum, wie ISO 80000 es erlaubte, stünde `0,033 5`
da, und ein Einzelpreis sähe aus wie zwei Angaben. Die Ziffern hinter dem
Komma sind die, die `Price` trägt (ADR-0021), und stehen als eine Folge da.

**Nicht jede Absprache lässt sich so hinschreiben, wie man denkt.** Die
naheliegende Form für die Ligaturen, `\defaultfontfeatures{Ligatures=TeXOff}`,
wirkt nicht: `fontspec.cfg` legt `Ligatures=TeX` eigens für `\rmfamily` und
`\sffamily` fest, und die Vorgabe der Familie schlägt die allgemeine. Erst
`\defaultfontfeatures[\rmfamily,\sffamily]{Ligatures=TeXOff}` schaltet sie ab,
und übersetzt wird beides klaglos. Ein solcher Fallstrick gehört an eine
Stelle, nicht in jedes Design.

**Die Spannung zu ADR-0009.** Dort und bei `ZAHLUNGSANGABE` gilt: Der Nächste
entscheidet, statt zu erben. Die Grundlage ist eine Vererbung. Sie ist hier
richtig, weil sie nur weitergibt, was kein Design anders entscheiden darf –
es gibt dort nichts zu entscheiden, nur etwas zu vergessen. Das verschiebt die
Beweislast: Wer eine Zeile in die Grundlage verschieben will, begründet,
warum ein Design sie nicht anders setzen darf.

## Verworfen

- **Alles Gleiche in die Grundlage.** Das dritte Design erbte dann das
  Satzbild der ersten beiden, ohne es gewählt zu haben – dieselbe Vererbung,
  vor der ADR-0009 warnt, nur ohne Grund.
- **Keine Grundlage, nur das Netz schließen.** Ligaturen und Satzabstand
  prüft das Netz inzwischen je Design, aber Schrift und Fettdruck der Ziffern
  sieht es nicht, und die Begründungen blieben in `helsinki.tex`.
- **Die Grundlage per `Template.of` in den Quelltext einfügen**, statt sie
  als Datei danebenzulegen. Das erzeugte `.tex` bliebe eine einzige Datei,
  aber jede Volltext-Vergleichsdatei enthielte die Grundlage wieder, und jede
  Änderung an ihr träfe alle Volltexte zugleich (#204). Kotlin wäre dann ein
  Präprozessor für TeX.
- **Ein Design lädt die Präambel des anderen.** Das ist dieselbe Kopplung wie
  das „siehe helsinki.tex“, nur mit Folgen beim Übersetzen.

## Konsequenzen

- **ADR-0021 gilt weiter, bis auf eine Konsequenz.** Die Schreibweise der
  Zahlen wählt jedes Design selbst. Den ganzen `\sisetup`-Block übernimmt ein
  neues Design aber nicht mehr, sondern nur die Zeilen der Schreibweise.
- **Was die Grundlage lädt, lädt die Vorlage nicht noch einmal:** pdfx samt
  hyperref und xcolor, fontspec, siunitx, longtable, lastpage und refcount.
  Die Grundlage nennt sie in ihrem Kopf.
- **Die Grundlage behält ihre Kommentare.** Sie steht in keiner
  Vergleichsdatei, und `Template.of` streicht nur in der Vorlage.
- **Die Trailer-ID jeder Rechnung ändert sich einmal**, weil eine Datei
  hinzukommt; `LatexCompiler.trailerId` liest `files` schon mit ein. Das
  Blatt bleibt gleich: Jede Beispielrechnung setzt in jedem Design vorher und
  nachher bei 300 dpi pixelgleich.
- **Eine Grundlage für alle Designs**, keine je Familie. Braucht ein Design
  eine Absprache nicht, ist sie keine.

Entschieden in #355.
