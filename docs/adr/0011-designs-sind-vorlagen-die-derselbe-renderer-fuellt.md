# Designs sind Vorlagen, die derselbe Renderer füllt

Die Stammdaten wählen das Design der Rechnung: `design: helsinki` oder
`design: prag`, ohne Eintrag `helsinki`. Jedes Design ist eine Vorlage im
Programm, `templates/helsinki.tex` und `templates/prag.tex`. Beide füllt
derselbe Renderer mit denselben Werten und Makroaufrufen.

Wer was entscheidet:

- **Eine einzelne Angabe** – eine Angabe der Stammdaten, eine Kopfangabe wie
  Zahlungsziel oder Kundennummer – liefert der Renderer als Platzhalter, leer
  erlaubt. Ob sie dasteht, entscheidet er über leer oder nicht leer.
- **Ort, Reihenfolge, Beschriftung und Trenner** entscheidet die Vorlage. Als
  einzige Bedingung kennt sie `\ifblank`.
- **Wiederholtes und fachliche Sätze** – Positionen, Summen, Absetzung,
  Pflichtsatz, Skonto, der Aufruf des Bankblocks – bleiben Makroaufrufe des
  Renderers oder fehlen ganz.
- **Welche Platzhalter keine Vorlage weglassen darf**, hält `checkPlaceholders`
  (#188): die, deren Inhalt an Rechnungsart oder Rechnung hängt.

## Warum

Der Generator soll auch für andere Rechnungssteller taugen, und nicht jeder
will aussehen wie der Lebenslauf des Autors. Die Rechnungsarten bleiben dabei
die geschlossene Menge, die jedes Design vollständig beantwortet (Notiz vom
14.09.2026): Was an der Art hängt, entscheidet einmal Kotlin, in den `when`
ohne `else`, und nicht jedes Design für sich ohne Compiler.

Die Treppe aus der Notiz hat auf der ersten Stufe gehalten. `prag` kommt mit
dem Vokabular aus, das #188 für `helsinki` geschnitten hat – kein neuer
Eintrag in `TemplateMacro` oder `TemplateValue`, kein zweiter Renderer.

**Feste Texte gehören zum Design.** Ob es "Rechnungsnr." heißt oder
"Rechnungsnummer", "Zahlbar ohne Abzug bis" oder "Zahlbar bis", entscheidet die
Vorlage. Ein Wortlaut mit fachlicher Aussage kommt dagegen aus Kotlin, auch
wenn er als Beschriftung dasteht: `ZEITRAUM_BESCHRIFTUNG` sagt auf einer
Abschlagsrechnung "Voraussichtlicher Leistungszeitraum" (UStAE 14.8), und eine
Vorlage, die fest "Leistungszeitraum" schriebe, wäre dort falsch. Dasselbe gilt
für die Wörter im Summenblock und in der Absetzung: Sie stehen in jedem Design
gleich da.

**Die Bankverbindung in `prag`** steht nur im Fuß, auf jedem Dokument, als
Angabe des Briefbogens und nicht als Zahlungsaufforderung. So hält es
`helsinki` schon mit der IBAN in seiner Fußzeile. `\bankblock` ist in `prag` definiert
und leer, der Renderer ruft ihn weiter nur bei `requestsPayment` auf. Ob ein
Dokument zur Zahlung auffordert, zeigt dort allein das Zahlungsziel. Entschieden
am fertigen Satzbild.

## Verworfen

- **Mitgebrachte Designs**, eine Vorlage als Datei neben den Stammdaten. Sie
  bestünde `checkMacros` und `checkPlaceholders`, aber niemand hätte gesehen, ob
  sie jede Rechnungsart vollständig setzt – das prüft nur das
  Pflichtangaben-Netz, und das läuft im Build dieses Projekts, nicht beim
  Benutzer.
- **Die Wahl per Schalter oder je Rechnung.** Alle Rechnungen eines
  Rechnungsstellers sehen gleich aus; ein Schalter am Kommando müsste bei jedem
  Aufruf richtig gesetzt werden, und zwei Rechnungen derselben Reihe sähen
  verschieden aus, nur weil einer ihn vergaß.
- **Ein zweiter Renderer je Design.** Er wiederholte jede Entscheidung über die
  Rechnungsarten, und die `when` ohne `else` hielten nur die Rechnungsarten
  zusammen, nicht die Designs untereinander.
- Aus #188, am selben Maßstab:
  - **Werte als `\newcommand` statt Platzhalter.** Namensregeln, Kollisionen
    und Expansionsfallen; die Prüfungen aus `fillIn` wären neu zu bauen.
  - **Fertige Fußblöcke** (Anschrift, Steuer, Bank). `helsinki` verteilt
    dieselben Angaben anders auf zwei Zeilen als `prag` auf drei Spalten; der
    Renderer müsste das Design kennen.
  - **Kopfangaben als Folge aus dem Renderer.** Reihenfolge und Beschriftung
    lägen in Kotlin.
  - **Bedingungen in der Vorlage über `\ifblank` hinaus.** Was an der
    Rechnungsart hängt, würde je Design neu entschieden, ohne Compiler.
- **Die Bank in `prag` als dritte Fußspalte nur bei `requestsPayment`.** Auf
  dem Storno hätte der Fuß ein Loch. Ebenso verworfen: ein Bankblock im
  Textfluss zusätzlich zum Fuß – dann stünde die Bank zweimal auf dem Blatt.

## Konsequenzen

- **Die Vergleichsdateien sind asymmetrisch.** `helsinki` sichern vier
  `*-erwartet.tex`, eine je Rechnungsart mit eigenem Markup. `prag` sichert
  nur `prag-rechnung-erwartet.tex`. Die übrigen Arten in `prag` schützt
  bewusst allein das Pflichtangaben-Netz: Ihre fachlichen Sätze sind dieselben
  wie in `helsinki` und dort schon gesichert, und eine Datei je Design und Art
  änderte sich mit jeder Vorlage. Ein Fehler im Satzbild einer Art, den das
  Netz nicht sieht, fällt in `prag` erst am PDF auf.
- **Das Pflichtangaben-Netz kennt jedes Design.** Ein Test verlangt, dass jedes
  `Design` darin steht. Zum Design gehören dort die Wörter, deren Abwesenheit
  geprüft wird; `prag` hat keine Bank-Überschrift.
- **Ein drittes Design** ist eine Vorlage, ein Eintrag in `Design` und eine Zeile
  im Netz. Braucht es ein Wort, das das Vokabular nicht hat, ist das die Frage
  aus #188 – nicht ein Makro am Renderer vorbei.
- **Die Build-Zeit wächst** mit jedem Design um fünf LuaLaTeX-Läufe im Netz.

Umgesetzt in #189, vorbereitet in #187 und #188.

## Nachtrag vom 19.09.2026: je Art der Inhalt, je Design das Satzbild

Die Asymmetrie der Vergleichsdateien hatte einen Preis, den diese Entscheidung
nicht ansah: Jede `*-erwartet.tex` enthielt die ganze Vorlage, und eine
Änderung am Satzbild von `helsinki` änderte vier Dateien wortgleich. Von den
150 Commits davor änderten 14 drei oder vier davon zugleich (#204).

Seither gilt:

- **Je Rechnungsart eine Inhalts-Datei**, `<art>-inhalt.tex`, unabhängig vom
  Design: die nicht leeren Werte aus `InvoiceRenderer.values()`, je Wert
  `<<NAME>>` und darunter der Wert. Es sind fünf, die Teilrechnung hatte
  vorher gar keine. Die Werte hängen nicht am Design, deshalb gilt eine Datei
  für jedes; entstanden sind sie mit den Stammdaten ohne Logo.
- **Je Design ein Volltext**, an der gewöhnlichen Rechnung:
  `rechnung-erwartet.tex` für `helsinki`, `prag-rechnung-erwartet.tex` für
  `prag` (mit Logo). Die übrigen drei Volltext-Dateien von `helsinki` sind
  entfallen.

Das kippt die Entscheidung nicht, es ergänzt die Konsequenz. Die Asymmetrie
ist aufgehoben: Beide Designs sichern dasselbe, und ein weiteres Design
bringt genau eine Vergleichsdatei mit. Die übrigen Arten verlieren dafür in
`helsinki` den zeichengleichen Volltext. Ihr Markup – Absetzungstabelle,
Pflichtsatz, Bezug – steht als Makroaufrufe in den Werten, und wie ein Design
diese Makros definiert, steht wortgleich in dessen Volltext. Dass sie auf dem
Blatt richtig erscheinen, prüft weiter allein das Pflichtangaben-Netz.

## Nachtrag vom 19.09.2026: Die Vorlage beschriftet die Summen

`prag` sollte den Summenblock anders benennen als `helsinki`: "Zwischensumme
(netto)" und "Umsatzsteuer 19 %" statt "Nettobetrag" und "zzgl. 19 % USt.".
Das Vokabular aus #188 hatte dafür kein Wort, die Beschriftungen kamen fertig
aus Kotlin. Also stellte sich die Frage aus #188 neu (#217).

Die Antwort: Die Wörter sind Satzbild. Beide Fassungen sagen fachlich
dasselbe, und die Regel "feste Texte gehören zum Design" gilt. Die Vorlage
definiert drei Makros, `\nettozeile{betrag}`, `\steuerzeile{satz}{betrag}`
und `\gesamtzeile{betrag}`, gebaut aus ihren eigenen `\summenzeile` und
`\abschlusszeile`. Makros und keine Platzhalter für die Beschriftung, weil
der Satz je Design an anderer Stelle im Wortlaut steht.

Nicht mitgewandert ist die Absetzung der Schlussrechnung: "Restbetrag" ist ein
Begriff aus dem Glossar, und die Zeile je Abschlag nennt Nummer, Datum und
Vereinnahmung – ein Satz mit fachlicher Aussage. Ebenso hängt keine
Beschriftung an der Rechnungsart; das bliebe eine Entscheidung je Design.

## Nachtrag vom 21.09.2026: Jede Vorlage sagt etwas über die Zahlung

`prag` nennt das Zahlungsziel seit #211 nicht mehr im Kopf, sondern als
Frist unter den Summen. Damit gibt es für dieselbe Angabe zwei Platzhalter,
und `ZAHLUNGSZIEL` konnte nicht bleiben, was die Pflichtliste aus #188 aus ihm
gemacht hatte: `TemplateValue.required` verlangt den Platzhalter von *jeder*
Vorlage, und `prag` setzt ihn bewusst nicht.

Beide schlicht optional zu machen, wie #211 es formuliert hatte, ließe aber
eine Vorlage durch, die über die Zahlung gar nichts sagt – auffallen würde das
erst im Pflichtangaben-Netz, am fertigen PDF und im Lauf mit den echten
Werkzeugen.

Die Antwort ist eine **benannte Regel** neben der Pflichtliste,
`TemplateValue.ZAHLUNGSANGABE`: die beiden Platzhalter als Menge, und der Bau
der Vorlage verlangt einen davon. Welchen, entscheidet das Design.

Bewusst keine allgemeine Liste von Gruppen (`requiredOneOf`, so war es zuerst
gebaut, #249): Es gibt genau diesen einen Fall. Eine allgemeine Liste nähme
dem nächsten Paar die Frage ab – man hängt eine Zeile an und ist fertig –,
während die Freiheit eines Designs genau die Frage ist, die dieses ADR und
#188 jedes Mal neu gestellt haben wollen. Wer ein zweites Paar braucht,
schreibt es hin und entscheidet damit sichtbar. Derselbe Zug wie `InvoiceType`
ohne Vorgabewerte (ADR-0009) und die `when` ohne `else` in den Regelgruppen:
Der Nächste entscheidet, statt zu erben.

## Nachtrag vom 24.09.2026: Die Designs heißen helsinki und prag

Die beiden Designs sind umbenannt (#324): `balken` heißt `helsinki`, `raster`
heißt `prag`, in den Stammdaten wie in den Vorlagen (`templates/helsinki.tex`,
`templates/prag.tex`). Am Satzbild ändert sich nichts, und die Vorgabe ohne
Eintrag bleibt dasselbe Design.

Die alten Namen nimmt das Programm nicht mehr an, auch nicht mit Warnung: Wer
seit v0.2.0 `design: balken` oder `design: raster` stehen hat, bekommt
„Unbekanntes Design“ wie bei jedem anderen Wert. Eine Zuordnung der alten
Namen müsste der Code sonst dauerhaft mitführen, für eine Zeile, die sich in
den Stammdaten einmal ändern lässt. Die ADRs nennen seither die neuen Namen;
die Notizen unter `docs/notizen/` behalten die alten, weil sie ein Gespräch mit
seinem damaligen Stand festhalten.
