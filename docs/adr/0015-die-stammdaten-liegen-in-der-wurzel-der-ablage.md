# Die Stammdaten liegen in der Wurzel der Ablage

Eine **Wurzel** ist ein Verzeichnis, das den Ordner `.rechnungsgenerator/`
enthält. Darin liegt `stammdaten.yaml`, und alles unterhalb der Wurzel wird mit
diesen Stammdaten gesetzt:

    Rechnungen/
      .rechnungsgenerator/
        stammdaten.yaml
        logo.tex
      2026/
        R26-009.yaml

`rechnungsgenerator erzeuge Rechnungen/2026/R26-009.yaml` findet die Stammdaten
von jedem Arbeitsverzeichnis aus, ohne `-s`. Gesucht wird ab dem Verzeichnis
der **Rechnungsdatei**, den angegebenen Pfad hinauf, absolut und normalisiert,
ohne symbolische Links aufzulösen, bis zur Wurzel des Dateisystems. Mit `-s`
wird nicht gesucht; ohne `-s` gibt es nur die Suche.

## Warum

**Die Vorgabe trug nur, solange man am richtigen Ort stand.** Bis hierher suchte
der Generator ohne `-s` eine Datei `stammdaten.yaml` im Arbeitsverzeichnis. Wer
seine Rechnungen in Unterordnern führt – etwa je Jahr – und aus einem davon
aufruft, gab den Pfad jedes Mal an; ein Aufruf von woanders fand je nach
Standort andere Stammdaten oder keine. Git löst dieselbe Frage mit einem
Verzeichnis, das sich an `.git` zu erkennen gibt, und hier geht es genauso.

**Gesucht wird ab der Rechnungsdatei und nicht ab dem Arbeitsverzeichnis.**
Derselbe Aufruf auf dieselbe Datei soll dieselben Stammdaten finden, egal wo man
steht – sonst hinge der Aussteller auf dem Blatt an etwas, das gar nicht in der
Eingabe steht. Git hat diese Wahl nicht, weil es dort keine „eine Datei" gibt.

**Markierung ist allein der Ordner.** Eine lose `stammdaten.yaml` zählt nicht:
Sie kann als Kopie oder Sicherung zufällig irgendwo liegen, ein Ordner mit dem
Namen des Programms nicht. Zwei Markierungen mit einer Rangfolge wären der
Anfang von „warum hat er diese genommen?".

**Eine Wurzel, ein Satz Stammdaten.** Ein zweiter Rechnungssteller ist eine
zweite Wurzel, daneben oder verschachtelt – die nächste gewinnt. Für alles
andere bleibt `-s`.

**Eine halb eingerichtete Wurzel bricht ab.** Trifft die Suche auf
`.rechnungsgenerator/` ohne `stammdaten.yaml` – oder auf eine Datei dieses
Namens –, nennt sie den Ort und sieht nicht weiter oben nach. Sonst nähme die
innere Wurzel still die Stammdaten der äußeren, und auf der Rechnung stünde der
falsche Aussteller.

**Das Logo bleibt, was ADR-0012 sagt**: relativ zum Verzeichnis der Stammdaten.
Es liegt damit im Ordner, ohne eine Zeile Code.

## Verworfen

- **Ein Rückfall aufs Arbeitsverzeichnis**, wenn die Suche nichts findet.
  Bequem beim Umzug, aber genau der Fall, der still das Falsche nimmt: Wer aus
  dem Ordner eines anderen Rechnungsstellers aufruft, bekäme dessen Stammdaten
  auf sein Blatt. Die Vorgabe entfällt deshalb ersatzlos.
- **Die Suche ab dem Arbeitsverzeichnis**, wie Git sie führt. Dann hinge das
  Ergebnis am Standort des Aufrufs, und derselbe Befehl ergäbe zweimal
  Verschiedenes.
- **`~/.config/rechnungsgenerator/`**. Das gälte je Benutzer statt je Wurzel,
  und eine kopierte Ablage nähme ihre Stammdaten nicht mit.
- **Benannte Varianten im Ordner** (`stammdaten-zweitfirma.yaml`) und ein
  **Verweis aus der Rechnungsdatei** auf ihre Stammdaten. Beides koppelte die
  zwei Eingabedateien, die bisher unabhängig sind; der zweite Rechnungssteller
  ist eine zweite Wurzel.
- **Ein Sonderfall fürs Home-Verzeichnis**, der die Suche dort enden ließe.
  Eine Ablage auf einem anderen Laufwerk liegt gar nicht darunter.
- **Zustand im Ordner** – ein Zähler für die nächste Nummer, ein Cache. Dort
  liegt nur Handgeschriebenes. Der Generator liest weiterhin eine Rechnungsdatei
  und was die Stammdaten nennen, nie das Verzeichnis (ADR-0001, ADR-0005). Wer
  das ändern will, schreibt ein eigenes ADR.
- **Eine Migrationshilfe.** Der Umzug sind zwei Befehle und steht in den
  Release-Notizen.

## Konsequenzen

- **Wer sich auf `stammdaten.yaml` im Arbeitsverzeichnis verlässt, bekommt eine
  Meldung**, die das Verzeichnis nennt, ab dem gesucht wurde, den erwarteten
  Ordner und `-s` als Ausweg. Der Umzug:

      mkdir .rechnungsgenerator
      mv stammdaten.yaml .rechnungsgenerator/

  Eine Logo-Datei zieht mit um.
- **Die Suche steht in `model` neben dem `YamlLoader`**, als `Root`: Der
  Dateiname gehört dorthin, wo gelesen wird, und beide Kommandos brauchen sie.
  Sie wirft `InputFileException`, übersetzt wird wie bisher in `execute()`.
  Keine neue Naht – sie rechnet Pfade und fragt `exists`, geprüft mit
  temporären Verzeichnissen.
- **Die Option `-s` hat keine Vorgabe mehr** und ist nullbar.
- **`abschlaege` sucht je Datei.** Liegen die Dateien in verschiedenen Wurzeln,
  bricht das Kommando ab und nennt beide – Abschläge zweier Rechnungssteller in
  einem Block sind fachlich sinnlos, und „die erste gewinnt" verschleierte das.
- **Im Erfolgsfall nennt das Programm den Fund nicht.** Eine Zeile bei jedem
  Lauf wäre ein Dauerhinweis, und wer gegriffen hat, steht auf dem PDF.
- **Der Skill `rechnung` ruft ohne `-s` auf**, damit er denselben Weg geht wie
  ein Aufruf von Hand. `naechste-nummer.py` behält `--stammdaten` mit
  ausgeschriebenem Pfad: Die Suche dort nachzubauen wäre eine zweite Fassung
  derselben Regel. Dass beide Skripte die Datei im versteckten Ordner übergehen
  – das eine, weil sie kein `nummer` trägt, das andere, weil sie keine `art`
  trägt –, hält seit hier je ein Test fest; `rglob` steigt sehr wohl in
  versteckte Ordner.

Umgesetzt in #275. Das Kommando `einrichten`, das Ordner und Vorlage anlegt,
folgt in #276.
