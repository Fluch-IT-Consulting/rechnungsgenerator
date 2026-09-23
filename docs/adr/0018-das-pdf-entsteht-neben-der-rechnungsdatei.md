# Das PDF entsteht neben der Rechnungsdatei

Ohne `-o` legt `erzeuge` das PDF in das Verzeichnis der **Rechnungsdatei** und
benennt es nach ihr, mit `.pdf` statt ihrer Endung:

    Rechnungen/
      .rechnungsgenerator/
        stammdaten.yaml
      2026/
        R26-009.yaml
        R26-009.pdf      ← entsteht hier, egal wo man steht

Aus `R.yml` wird `R.pdf`, eine Datei ohne Endung bekommt `.pdf` angehängt.
stdout nennt das Ziel so, wie die Rechnungsdatei angegeben wurde: Aus
`erzeuge ablage/2026/R.yaml` wird `ablage/2026/R.pdf`, relativ bleibt relativ.
`-o` gilt unverändert relativ zum Arbeitsverzeichnis. Das Ziel ist nie die
Rechnungsdatei selbst, auch nicht mit `--ueberschreiben`.

## Warum

**Dieselbe Frage wie in ADR-0015, dieselbe Antwort.** Die Stammdaten findet der
Generator ab der Rechnungsdatei, damit derselbe Aufruf auf dieselbe Datei von
jedem Standort aus dasselbe ergibt. Das PDF schrieb er bis hierher aber ins
Arbeitsverzeichnis, und damit hing der Ablageort doch wieder am Standort:
Derselbe Aufruf legte das PDF je nach Standort woanders ab.

**Der Überschreibschutz greift nur am richtigen Ort.** Eine vorhandene
Zieldatei ersetzt `erzeuge` nur mit `--ueberschreiben` (#73), weil sie die
verschickte Rechnung sein kann. Stand das PDF im Arbeitsverzeichnis, sah der
Schutz sie nur, wenn man dort stand, wo sie lag; von woanders entstand still
ein zweites PDF derselben Rechnungsnummer. Neben der Rechnungsdatei gibt es
genau einen Ort, und dort sieht er nach.

**Der Name kommt aus der Rechnungsdatei und nicht aus der Nummer.** Wer die
Datei benannt hat, hat den Namen schon entschieden, und das PDF ist die
Darstellung genau dieser Datei. Der Skill `rechnung` zeigt, dass ein eigenes
Schema die Regel ist und nicht die Ausnahme: Er übergeht die Vorgabe mit `-o`,
um `Rechnung_R26-012-beispiel.pdf` neben `Rechnung_R26-012-beispiel.yaml` zu
legen statt `Rechnung-R26-012.pdf`.

**Ein getippter Pfad meint, was die Shell darunter versteht.** `-o` ist wie
`-s` eine ausdrückliche Angabe und gilt deshalb relativ zum
Arbeitsverzeichnis. Abgeleitet wird nur, was nicht dasteht.

**Das Ziel ist nie die Eingabe.** Zeigt `-o` auf die Rechnungsdatei oder heißt
sie selbst `.pdf`, bricht das Kommando ab, mit eigener Meldung und auch mit
`--ueberschreiben`: Der Schalter ist fürs Nachbessern eines Entwurfs gedacht,
und die Eingabe zu zerstören ist kein Nachbessern. Verglichen wird, ob es
dieselbe Datei ist, nicht ob die Pfade gleich lauten. So fallen auch
`./R.yaml`, ein symbolischer Link und auf macOS `r.YAML` darunter.

## Verworfen

- **Das Arbeitsverzeichnis**, wie es die meisten Kommandozeilenprogramme
  halten. Die übliche Konvention, aber vom Standort abhängig, und genau das hat
  ADR-0015 für die Stammdaten ausgeschlossen.
- **`-o` als Pflicht.** Das wäre die harte Lesart von ADR-0015: keine Vorgabe,
  die am Standort hängen kann, also gar keine. Aber ADR-0015 hat die Vorgabe
  durch eine Ableitung ersetzt und nicht durch eine Pflichtangabe, und hier
  gibt es eine Ableitung, die trägt.
- **Neben der Rechnungsdatei, aber weiter nach der Nummer benannt.** Dann
  hätte eine Rechnung zwei Namen, den ihrer Datei und den ihres PDF, und wer
  seine Dateien anders benennt, übergeht die Vorgabe jedes Mal mit `-o`.

## Konsequenzen

- **Aus der Rechnungsnummer entsteht nur noch der Jobname für LuaLaTeX**
  (`fileBaseName`), und der gilt in einem Temp-Verzeichnis, das nach dem Lauf
  gelöscht wird. ADR-0005 begründet drei Abbrüche mit dem Dateinamen aus der
  Nummer – `RECHNUNGSNUMMER_FEHLT`, `RECHNUNGSNUMMER_MIT_LEERRAUM` und
  `RECHNUNGSNUMMER_OHNE_DATEINAMEN`. Ob sie eine andere Begründung tragen oder
  entfallen, klärt #297.
- **Der Skill `rechnung` behält sein `-o`.** Ist `build/install/…` nicht neu
  gebaut, legte eine ältere Fassung das PDF ins Arbeitsverzeichnis, beim Skill
  also ins Projektverzeichnis, und dort ist `*.pdf` nicht ignoriert. Ein
  Skript nennt sein Ziel ausdrücklich und hängt damit an keiner Fassung.
- **Kein `!` im Commit.** Bestehende Rechnungsdateien bleiben gültig, geändert
  ist das Verhalten der Kommandozeile. Die Release-Notizen entstehen aus den
  Titeln der Pull Requests, deshalb sagt der Titel, dass sich die Vorgabe für
  die Zieldatei ändert.
- **`abschlaege` und `einrichten` bleiben, wie sie sind.** Das eine schreibt
  auf stdout, das andere legt die Wurzel ausdrücklich im Arbeitsverzeichnis an
  (#276). Beide erzeugen kein Dokument aus einer Eingabedatei.

Umgesetzt in #296.
