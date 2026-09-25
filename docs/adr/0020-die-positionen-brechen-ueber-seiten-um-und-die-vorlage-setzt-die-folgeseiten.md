# Die Positionen brechen über Seiten um, und die Vorlage setzt die Folgeseiten

Passen die Positionen nicht auf eine Seite, bricht die Tabelle um, und die
Vorlage des Designs entscheidet, wie eine Folgeseite aussieht. Der Renderer
liefert weiter dieselben Werte und Makroaufrufe wie für eine Seite; ob eine
Rechnung eine, zwei oder fünf Seiten hat, erfährt er nicht.

Was auf jeder Seite steht, gilt für jedes Design:

- **Folgeseiten tragen einen kurzen Kopf** mit Rechnungsnummer, Datum und
  „Seite 2 von 3“, keinen vollen Briefkopf. Der **Fuß des Briefbogens** –
  Anschrift, Registergericht, USt-IdNr., Bankverbindung – steht auf jeder
  Seite. Eine Seite 2, die jemand allein in der Hand hält, gehört damit
  erkennbar zu einer Rechnung und zu einem Rechnungssteller.
- **Der Spaltenkopf der Positionen wiederholt sich** auf jeder Seite.
- **Am Seitenende steht „Fortsetzung auf der nächsten Seite“**, kein Übertrag.
- **Der Summenblock hängt an den letzten Positionszeilen.** Pflichtsatz,
  Skonto und Bankverbindung dürfen auf die nächste Seite rutschen; ein
  Summenblock ohne eine Position darüber steht nie allein.
- **Schlusssatz, Gruß und Name bleiben beisammen.** Sie rutschen nur
  gemeinsam auf die nächste Seite. Ein Design ohne Gruß hängt den
  Schlusssatz an den Satz davor, damit er nicht allein auf der letzten Seite
  steht.
- **Kein fachlicher Satz unter den Summen bricht über zwei Seiten** –
  Pflichtsatz, Zahlungsfrist, Skonto, der Satz zum ausgeglichenen
  Restbetrag und jeder Hinweis auf eine Steuerbefreiung (Nachtrag unten).
- **Eine einseitige Rechnung sieht aus wie bisher**: keine Seitenzahl, kein
  Fortsetzungshinweis. Ob es Folgeseiten gibt, weiß nur die Vorlage, aus der
  Seitenzahl des Satzes.

Dazu ein Netz, das unabhängig vom Umbruch gilt: **Ein `Overfull \vbox` im
Log von LuaLaTeX bricht den Lauf ab.** Die Meldung heißt, dass Inhalt vom
Blatt gefallen ist, und ein PDF ohne diesen Inhalt darf nicht entstehen –
auch nicht als gültiges PDF/A. Ein `Overfull \hbox` bleibt eine Warnung: Eine
zu breite Zeile ragt in den Rand, sie fehlt nicht.

## Warum

**Der Befund aus #318.** Eine Rechnung mit 40 Positionen ergab ein gültiges
PDF/A-1b mit Exitcode 0 und drei Seiten: Seite 1 nur der Kopf, auf Seite 2
die Tabelle, die unten aus dem Blatt lief, auf Seite 3 allein der
Summenblock. Zwölf Positionen fehlten, auch im Textlayer, und der
Gesamtbetrag zählte sie trotzdem. Kein Prüfer sah etwas, weil das PDF
technisch einwandfrei war; LuaLaTeX schrieb eine Warnung ins Log, die niemand
las. `tabularx` bricht nicht um, sie wandert als Ganzes auf die nächste Seite
und läuft dort über.

**Der Umbruch ist Satz und gehört in die Vorlage (ADR-0017).** Kopf und Fuß
der Folgeseiten, der wiederholte Spaltenkopf, der Fortsetzungshinweis, der
Zusammenhalt von Positionen und Summen – nichts davon hängt an der
Rechnungsart oder an einer Rechnung, alles davon ist Ort, Beschriftung und
Abstand. Deshalb ändert sich das Vokabular aus `TemplateValue` und
`TemplateMacro` dafür nicht: Die Positionen kommen weiter als eine Folge von
`\positionszeile`, und wo die Seite bricht, weiß LaTeX.

**Kein Übertrag, weil beide Wege ihn ausschließen.** Ein Übertrag ist eine
Summe, also eine Rechnung, und Geld wird nur in Kotlin gerechnet, nie in der
Vorlage. Der Renderer könnte ihn rechnen – aber er weiß nicht, wo die Seite
bricht, und dürfte das nicht wissen, ohne dass der Satz zweimal liefe und
zurückmeldete. Was bleibt, ist ein Satz ohne Zahl am Seitenende, und der ist
Satzbild. Fachlich fehlt nichts: §14 UStG verlangt das Entgelt, nicht eine
Zwischensumme je Blatt.

**Der Summenblock braucht eine Position über sich.** Genau das Bild aus dem
Befund – Zahlen ohne das, was sie summieren – soll nicht wieder entstehen.
Mehr zusammenzuhalten (Summen samt Pflichtsatz, Gruß und Bank) machte den
Block so groß, dass er oft eine halbleere Seite davor erzwänge.

**Der Schluss bricht nicht auseinander.** Nachgemessen an einer Rechnung mit
36 Positionen: Ohne Zusammenhalt endete Seite 2 mit dem Dankessatz, und auf
Seite 3 stand "Mit freundlichen Grüßen" allein über dem Namen – ein Gruß ohne
den Satz, den er beschließt. Gehalten wird mit Umbruchstrafen und nicht mit
einer Box: Strafen nehmen keinen Raum ein, und die einseitige Rechnung
bleibt pixelgleich. In prag, das keinen Gruß setzt, stand an derselben
Stelle der Schlusssatz allein auf Seite 3; er hängt dort deshalb an der
Zahlungsfrist, beim Storno am Pflichtsatz. Dabei zeigte sich, dass die Seite
auch mitten im zweizeiligen Pflichtsatz brechen konnte und auf Seite 3 nur
"gezahlter Betrag wird erstattet." stand – ein Satz ohne Anfang. Seither
bricht keiner dieser Sätze um, in beiden Designs.

**Die einseitige Rechnung bleibt, wie sie ist.** „Seite 1 von 1“ sagt nichts,
und jedes Beispiel-PDF änderte sich. Die Vorlage entscheidet an der
Seitenzahl, die der Satz kennt, ohne einen Schalter aus Kotlin.

**Der Abbruch bei `Overfull \vbox` ist ein Netz, kein Behelf.** Er fängt den
Fehler aus #318 schon vor dem Umbau, und er bleibt danach, weil der Verlust
weiter möglich ist: Eine Tabellenzeile bricht nicht innerhalb einer Zelle, und
eine Position mit einer Beschreibung über eine halbe Seite fiele wieder still
vom Blatt. Das Netz aus #187 sieht nur die Beispieldateien, nie eine echte
Rechnung; das Log sieht jede.

## Verworfen

- **Ein Übertrag, in LaTeX gerechnet.** Die Vorlage rechnete Geld, mit
  Rundung in TeX-Arithmetik, und stünde damit gegen die Regel, dass Beträge
  ausschließlich `BigDecimal` sind.
- **Ein Übertrag aus Kotlin.** Der Renderer müsste die Seitenumbrüche kennen:
  ein erster Lauf, ein Rücklesen, ein zweiter Lauf mit den Umbrüchen als
  Eingabe. Für einen Wert, den keine Vorschrift verlangt.
- **Der volle Briefkopf auf jeder Seite.** Er nähme auf jeder Folgeseite ein
  Drittel des Blatts, und der Balken samt Anschrift wiederholte etwas, das
  nur einmal gemeint ist.
- **Nur die Seitenzahl auf Folgeseiten.** Am wenigsten Satzarbeit, aber eine
  lose Seite 2 gehörte zu keiner Rechnung.
- **Summen mit allem darunter als Einheit.** Siehe oben: ein zu großer
  Block.
- **Der Abbruch bei `Overfull \vbox` nur bis zum Umbau.** Danach passierte
  derselbe Verlust wieder still, bei einer anderen Eingabe.
- **Ein Platzhalter `SEITENZAHL` oder ein Schalter „mehrseitig“ aus Kotlin.**
  Der Renderer weiß nicht, wie viele Seiten der Satz ergibt, und die Frage
  ist eine der Vorlage.

## Konsequenzen

- **Beide Vorlagen setzen die Positionen als umbrechende Tabelle** statt als
  `tabularx`, mit Kopf und Fuß je Seite. Wie – `longtable`, `xltabular`,
  `fancyhdr`, `lastpage` – entscheidet die Vorlage; das Vokabular der Naht
  bleibt gleich.
- **Der Fuß wird ein Seitenfuß.** In `helsinki` steht er heute am Ende des
  Textes hinter einem `\vfill`, in `prag` ebenso. Als Seitenfuß kann er auf
  der einseitigen Rechnung um Millimeter wandern. Ziel ist das unveränderte
  Satzbild; ob es erreicht ist, entscheidet der Bildvergleich mit `pdftoppm`,
  nicht die Prüfsumme.
- **`LatexCompiler` liest das Log auf `Overfull \vbox`** und wirft dann wie
  bei einem gescheiterten Lauf, mit der Zeile aus dem Log in der Meldung. Das
  Log liest er schon, für „Rerun to get“.
- **Das Netz aus #187 bekommt eine Beispieldatei mit vielen Positionen.**
  Geprüft wird über `pdftotext`, dass jede Position im PDF steht, und über
  die Seitenzahl, dass es mehr als eine ist. Dieselbe Datei hält den
  Fortsetzungshinweis und den kurzen Kopf der Folgeseite fest. Eine zweite,
  `schluss-an-der-seitengrenze.yaml`, füllt die zweite Seite so weit, dass
  sie ohne Zusammenhalt bräche: in helsinki zwischen Schlusssatz und Gruß, in
  prag zwischen Zahlungsfrist und Schlusssatz. Die Grenze ist
  nachgemessen und wandert, wenn sich das Satzbild ändert; die Prüfung bleibt
  dann richtig, trifft den Fall aber nicht mehr.
- **Ein drittes Design** muss die Folgeseiten selbst setzen; die Liste oben
  ist dafür die Checkliste, und das Netz prüft sie an der Beispieldatei.

Umgesetzt in #318.

## Nachtrag vom 24.09.2026: auch die Hinweise auf eine Steuerbefreiung

Die Zusage oben galt „keinem fachlichen Satz“, gehalten war sie aber nur für
die Sätze in `\hinweisabsatz`. Die Hinweise auf eine Steuerbefreiung – „Auf
… entfällt keine Umsatzsteuer: <Befreiungsgrund>.“ (BT-120, §14 Abs. 4 Nr. 8
UStG) – standen ohne Strafe in der Vorlage, als ein Absatz, die Sätze
getrennt durch `\zeilenwechsel`. Nachgemessen an einer Rechnung mit einem
dreizeiligen Befreiungsgrund: In beiden Designs stand nur die erste Zeile auf
Seite 2, der Rest auf Seite 3 – derselbe Satz ohne Anfang wie beim
Pflichtsatz (#335).

In `\hinweisabsatz` gehören sie nicht: Das Makro setzt auch den breiten
Abstand nach einem Satzende, und den bekommen nur die eigenen Sätze des
Programms, der Befreiungsgrund aber kommt aus den Eingabedaten (#143).
Deshalb sind **Zusammenhalt und Satzabstand getrennt**. Den Zusammenhalt
trägt in jeder Vorlage ein Makro für sich, `\ungeteilt`; es benutzen
`\hinweisabsatz`, der Schlusssatz und der neue `\steuerhinweis`, der
Satzabstand bleibt bei `\hinweisabsatz`.

**Jeder Hinweis ist ein Makroaufruf** (`TemplateMacro.STEUERHINWEIS`), wie
ADR-0017 es für fachliche Sätze vorsieht, und keine Zeile in einem
gemeinsamen Absatz. So entscheidet die Vorlage je Satz, wie er zusammenhält:
Zwischen zwei Hinweisen darf die Seite brechen, in einem nicht. Das Satzbild
ist unverändert, bei 300 dpi pixelgleich an allen Beispielen und an einer
Rechnung mit zwei Hinweisen.

Das Netz bekommt eine dritte Datei, `befreiungsgrund-an-der-seitengrenze.yaml`,
gebaut wie `schluss-an-der-seitengrenze.yaml`: Ihre Positionen füllen die
zweite Seite so weit, dass sie ohne Zusammenhalt nach der ersten Zeile des
Hinweises endete. Geprüft wird an jeder mehrseitigen Rechnung, dass jeder
Befreiungsgrund ganz auf einer Seite steht und das Entgelt, mit dem sein
Satz beginnt, auf derselben.

## Nachtrag vom 25.09.2026: was das Netz davon prüft

Die Liste oben ist die Checkliste für ein Design, und das Netz prüft sie an
jeder mehrseitigen Rechnung (#337): auf jeder Seite den ganzen Fuß des
Briefbogens, auf jeder Seite mit Positionen den Spaltenkopf, auf der Seite
der letzten Position den Gesamtbetrag, und dass Pflichtsatz und Zahlungsfrist
ganz auf einer Seite stehen. Vom Register prüft es nur die Nummer: Das
Registergericht heißt meist wie ein Ort und stünde schon mit der Anschrift
da.

Die Seitenzahl jeder Beispieldatei steht im Netz je Design, nachgemessen und
nicht hergeleitet; jede Datei, die dort nicht steht, hat eine Seite. „Mehr
als eine“ übersah eine Seite mehr oder weniger, und eine gewöhnliche
Rechnung, die still auf zwei Seiten wüchse, sähe nicht mehr aus wie bisher.
Ändert sich das Satzbild, wird die Zahl neu gemessen, wie eine
Vergleichsdatei neu erzeugt wird: erst nachsehen, ob die Änderung gewollt
ist.

Skonto und der Satz zum ausgeglichenen Restbetrag stehen in eigenen
Beispieldateien, `skonto.yaml` und `schlussrechnung-ausgeglichen.yaml`
(#342). Beide sind einseitig, ebenso jede Datei mit einem Pflichtsatz: Dass
diese Sätze nicht über zwei Seiten brechen, prüft das Netz an jeder
mehrseitigen Rechnung, aber keine der Dateien liegt an einer Seitengrenze.
Eine eigene Datei an der Grenze bekommen sie bewusst nicht. Den
Zusammenhalt trägt in jeder Vorlage ein Makro, `\ungeteilt`, und das prüft
das Netz an zwei Grenzen, beim Schluss und beim Befreiungsgrund; jeder Satz
in `\hinweisabsatz` bekommt es von dort. Eine Grenzdatei je Satz prüfte
dasselbe Makro noch einmal und wanderte mit jeder Änderung am Satzbild.
