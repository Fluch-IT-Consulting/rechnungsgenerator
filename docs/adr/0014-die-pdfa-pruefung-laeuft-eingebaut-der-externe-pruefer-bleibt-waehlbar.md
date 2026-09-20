# Die PDF/A-Prüfung läuft eingebaut, der externe Prüfer bleibt wählbar

Ob das gesetzte Dokument ein gültiges PDF/A-1b ist, beantworten zwei Prüfer
hinter einer Naht (`PdfAValidator`):

- **eingebaut** (`BuiltInPdfAValidator`): `org.verapdf:validation-model` in
  derselben JVM. Die Vorgabe.
- **extern** (`ExternalPdfAValidator`): das installierte Programm `verapdf` als
  eigener Prozess. Über `erzeuge --externe-pdfa-pruefung`.

Der Generator läuft damit ohne installiertes veraPDF. `checkAvailable()`
verlangt immer LuaLaTeX; nach dem Prüfer fragt sein Adapter selbst, und nur der
externe verlangt etwas. Wer den Schalter setzt, ohne dass das Programm da ist,
bekommt einen Abbruch mit Installationshinweis.

## Warum

**Der Grund ist die Startzeit, nicht die Prüfung.** Das Programm `verapdf`
braucht für ein einzelnes Dokument 6,4 s – davon 6,2 s für den Start seines
CLI-Wrappers. Die Prüfung selbst dauert rund 50 ms. Gemessen gegen dieselbe
Fassung als Bibliothek, in einer JVM:

| Weg | 1 PDF | 6 PDFs | 29 PDFs |
|---|---|---|---|
| Programm je Aufruf | 6,44 s | ~39 s | ~191 s |
| Bibliothek | 1,28 s | 1,65 s | 2,09 s |

Das trifft beides: den Testlauf, der rund 29 PDFs setzt, und den Menschen am
Rechner, dessen `erzeuge`-Lauf von ~9 s auf ~4 s fällt.

**Der externe Prüfer bleibt, weil er unabhängig urteilt.** Er ist getrennt
installiert und getrennt versioniert – nicht dasselbe Stück Software, das der
Generator selbst mitbringt. Das ist eine Aussage, die eine eingebaute
Bibliothek über sich nicht machen kann, und sie ist der einzige Grund für den
zweiten Weg. Ein Ticket, das ihn als Doppelung wegräumen will, räumt genau das
weg.

Gegen die Doppelung steht die Erfahrung des Projekts: Zwei Wege zur selben
Aussage laufen auseinander (#52, #119). Hier tun sie es nicht unbemerkt – ein
Test vergleicht sie (siehe unten).

**Dieselbe Fassung auf beiden Wegen.** Maven Central führt 1.30.2, genau die
Fassung, die Homebrew als Programm installiert. Gemessen über zehn Rechnungs-PDFs
und zwei absichtlich nicht konforme urteilen beide gleich, bis auf die
Regelkennungen genau. Nur der Wortlaut der Meldung weicht ab: Das Programm setzt
die Werte des Dokuments ein („first four bytes = 0, 0, 0, 0"), die Bibliothek
liefert die Vorlage aus dem Profil („%1, %2, %3, %4"). Der Wortlaut ist
Darstellung, die Regelkennung ist die Aussage.

**Der Typesetter bekommt seinen Prüfer gereicht.** `LuaLatexTypesetter` ist
deshalb eine Klasse und kein `object` mehr, und das Kommando bekommt eine
`TypesetterFactory` statt eines fertigen Typesetters: Welcher Prüfer es ist,
steht erst fest, wenn der Schalter gelesen ist. Nebenbei wird die Wahl dadurch
prüfbar, ohne dass ein PDF entsteht – der Test sieht, welchen Prüfer das
Kommando übergibt.

## Verworfen

- **Nur die Bibliothek, der externe Prüfer ersatzlos.** Einfacher, und veraPDF
  fiele ganz aus den Voraussetzungen. Verworfen wegen der Unabhängigkeit oben:
  Ein Prüfer, der mit dem Geprüften ausgeliefert wird, ist ein schwächerer
  Zeuge.
- **Nur das Programm, die Bibliothek nur im Test.** Dann prüften die Tests nicht
  mehr den Weg, den der Benutzer geht – derselbe Fehler, den `ToolchainRun`
  bewusst vermeidet, indem es durch das Kommando geht.
- **Die Prüfung abwählbar machen und im Stapel nachholen.** Ein Aufruf mit 29
  Dateien kostet 7,5 s statt 191 s, und der Testlauf wäre damit fast so schnell.
  Aber: Das Urteil wanderte vom einzelnen Test ans Spec-Ende, das Kommando
  bekäme einen Schalter „gar nicht prüfen", und dem Menschen am Rechner hülfe es
  nicht. Der Vorschlag stand in #225 und ist dort geschlossen.
- **Die `-jakarta`-Artefakte.** Sie sollten die drei JVM-Warnzeilen auf JDK 26
  vermeiden; nachgemessen tun sie es nicht, dort wechselt nur der Verursacher.
- **Den Klassenpfad kürzen.** Saxon-HE und xmlresolver (6,66 MB) kommen über
  `org.verapdf:core` und werden für PDF/A-1b nicht gebraucht; über zehn PDFs
  liefert der gekürzte Pfad identische Urteile. Für ein lokales Werkzeug sind
  6,6 MB kein Preis, und ein von Hand gekürzter Klassenpfad ist eine Falle, die
  beim nächsten Versionssprung zuschnappt – womöglich als fehlende Regel und
  nicht als Absturz.

## Konsequenzen

- **veraPDF ist keine Voraussetzung mehr**, weder für den Generator noch für
  seinen Build. Die zwei Tests, die das Programm wirklich brauchen, überspringen
  sich, wenn es fehlt – übersprungen und nicht bestanden, wie beim
  Altersnachweis der EU-Liste (#79).
- **Ein Test vergleicht die beiden Prüfer** über ein gültiges und ein
  absichtlich nicht konformes PDF: gleiches Urteil, gleiche Regelliste. Er wird
  rot, sobald Homebrew und Maven Central auseinanderlaufen – das ist sein Zweck
  und kein Defekt. `nicht-konform.pdf` liegt als Datei im Testbestand statt zur
  Laufzeit erzeugt zu werden: 595 Bytes von Hand, fünf verletzte Regeln.
- **Die Auslieferung wächst von 4,1 MB auf 21 MB.** Für ein lokal aufgerufenes
  Kommando ist das folgenlos; für einen künftigen Versand über das Netz wäre es
  eine eigene Frage.
- **Lizenz: MPL-2.0.** veraPDF steht dual unter GPLv3+ oder MPLv2+ nach Wahl.
  GPLv3 schlüge auf das Ganze durch, wenn das Repo öffentlich wird; MPL-2.0 ist
  dateiweise. Die Wahl steht hier und in `CLAUDE.md`, weil eine Wahl, die
  nirgends steht, beim nächsten Mal neu getroffen wird.
- **Drei JVM-Warnzeilen ab JDK 24 werden unterdrückt.** veraPDF schreibt sein
  Validierungsprofil per JAXB in ein `final`-Feld. `-XX:+IgnoreUnrecognizedVMOptions
  --enable-final-field-mutation=ALL-UNNAMED` stellt das auf JDK 21 und 26 ab,
  in `applicationDefaultJvmArgs` und in `tasks.test`. Unterdrückt wird damit
  eine **Ankündigung**: Wird die Änderung scharf geschaltet, läuft veraPDF gar
  nicht mehr, und die Option hat die Vorwarnung genommen. Die Alternative wären
  drei Zeilen Lärm bei jedem Rechnungslauf – dieselbe Abwägung wie bei Mordant.
  (Das Programm `verapdf` schreibt dieselben drei Zeilen; heute unsichtbar, weil
  der Adapter alles vor dem XML-Prolog wegschneidet.)
- **Der Testlauf fällt von 247 s auf rund 130 s**, der ganze Build von 4:12 auf
  1:08. Was davon auf #223 entfällt, steht dort.

Umgesetzt in #226.
