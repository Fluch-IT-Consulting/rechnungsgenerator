# Drei Umbauten, deren Zeitpunkt das Wachsen ist

Notiz vom 14.09.2026, festgehalten für später. **Hier wird nichts entschieden.**
Anlass war eine Architektur-Durchsicht der Kotlin-Anwendung: Drei Stellen sind
heute bewusst einfacher gebaut, als sie es bei einem größeren Projekt wären.
Keine davon ist ein Mangel, jede hat einen natürlichen Zeitpunkt – und der ist
nicht jetzt. Die Notiz hält fest, welcher es ist, damit er nicht verpasst wird.

Der gemeinsame Auslöser ist der zweite Ausgabemodus (ZUGFeRD) beziehungsweise
die nächste Rechnungsart (#64): der Moment, in dem der Kern zum ersten Mal
zwei Ränder bedient.

> **Nachtrag vom selben Tag:** Zu Punkt 3 ist eine Achse dazugekommen, die die
> offen gelassene Schnittlinie entscheidet: Es werden verschiedene
> **Rechnungsdesigns** kommen, und nicht jedes wird sich sinnvoll mit demselben
> Renderer erzeugen lassen. Vervielfacht sich etwas, dann die Designs – die
> Rechnungsarten bleiben die geschlossene Menge, die *jeder* Renderer
> vollständig beantwortet. Ein Schnitt je Art stünde quer dazu (D Designs ×
> K Arten Bausteine), deshalb wandern die artabhängigen Blöcke **nicht** zur
> `InvoiceKind`: Die `when` ohne `else` je Block bleiben die Checkliste, die
> auch jeder weitere Renderer ausfüllen muss. Für ein neues Design gilt eine
> Treppe: erst versuchen, ob eine zweite Vorlage mit demselben Makro-Vokabular
> reicht (`checkMacros` prüft jede Vorlage gegen `TemplateMacro`); erst wenn
> das Vokabular nicht passt, ein zweiter Renderer hinter der Naht
> `AcceptedInvoice → tex + xmp` – das Interface dafür entsteht erst mit dem
> zweiten Adapter, wie bei `Typesetter`. Zum Gespräch vor dem zweiten Design
> gehört dann ein Pflichtangaben-Netz je Design (die `pdftotext`-Rücklese aus
> #81, verallgemeinert), denn `rechnung-erwartet.tex` schützt nur ein
> Satzbild. Der Text der Notiz bleibt, wie er war.

> **Nachtrag vom 15.09.2026:** Mit dem zweiten Design wird auch der Zuschnitt
> der Vergleichsdateien fällig. Heute enthält jede `*-erwartet.tex` die ganze
> Vorlage samt Werten; mit D Designs wären es D × K Dateien, und jede änderte
> sich mit ihrer Vorlage. Die Richtung, erwogen in #149: `InvoiceRenderer.render`
> liefert den Inhalt je Platzhalter, das Einsetzen in die Vorlage wird ein
> eigener Schritt, und die Vergleichsdateien halten nur den Inhalt, je Art und
> unabhängig vom Design. Offen blieb, was dann die Vorlage selbst schützt – der
> Bildvergleich mit `pdftoppm` ist heute ein Handgriff, kein Test – und ob
> Reihenfolge-Tests wie "der Pflichtsatz steht unter den Summen" dann zur
> Vorlage gehören statt zum Renderer. Entschieden wurde in #149 nur der kleine
> Teil, der schon heute wehtat: Die Vorlage kommt ohne Kommentarzeilen im
> `.tex` an.

## 1. Gradle-Module statt `PackageStructureTest`

Die Schichtung `values → model → calculation → rules` und die beiden
vertikalen Rand-Pakete hält heute `PackageStructureTest`: eine Importtabelle,
gelesen mit Konsist (#112). Das ist eine Konvention mit Sicherheitsnetz – ein
verbotener Import fällt im Test auf, nicht beim Übersetzen.

Als eigene Gradle-Module (etwa `:core` und `:cli`, oder feiner) würde
`internal` wirklich greifen: `internal` gilt in Kotlin pro Modul, und genau
deshalb hält es heute keine Paketgrenze. Ein verbotener Import wäre dann ein
Übersetzungsfehler, und die Tabelle im Test entfiele oder schrumpfte auf das,
was Module nicht ausdrücken (etwa das Verbot gleichpaketiger Importe).

**Warum nicht jetzt:** Bei einem Modul und einem Verbraucher kostet der Schnitt
Build-Komplexität (mehrere `build.gradle.kts`, Versionsabgleich, ktlint- und
Toolchain-Konfiguration je Modul), ohne dass ein zweiter Nutznießer da wäre.
`CLAUDE.md` nennt für das ADR zur Paketstruktur denselben Moment: wenn ZUGFeRD
kommt. Der Modulschnitt gehört in dieselbe Entscheidung – Schnittlinie und
Modulgrenze sollten zusammen festgelegt werden, nicht zweimal.

## 2. Komposition am Rand statt `object`-Singletons

`InvoiceCalculator`, `InvoiceRenderer`, `YamlLoader` sind `object`s, und
`LuaLatexTypesetter` verdrahtet `ExternalTool` fest. Testbar ist trotzdem
alles, weil die eine Naht (`Typesetter`) reicht und die Teile dahinter
(`LatexCompiler`, `PdfAValidator`) den Prozessläufer gereicht bekommen.

Das trägt, solange es genau einen Weg durch das Programm gibt. Ein zweiter
Ausgabemodus bricht die Annahme: Wer aus derselben `AcceptedInvoice` einmal
LaTeX und einmal XML erzeugt, braucht zwei Renderer nebeneinander – und
Singletons laden dazu ein, dass der neue Rand sich seine Abhängigkeiten selbst
nimmt, statt sie gereicht zu bekommen. Die Alternative: `Main` (oder das
jeweilige Kommando) setzt die Verdrahtung an einer Stelle zusammen, darunter
gewöhnliche Klassen mit Konstruktor-Parametern. `GenerateCommand` bekommt den
`Typesetter` heute schon so gereicht – das Muster ist da, es gilt nur noch
nicht überall.

**Warum nicht jetzt:** Zustandslose `object`s sind bei einem einzigen
Verbraucher die einfachste richtige Form. Der Umbau vorab wäre Zeremonie ohne
zweiten Aufrufer; er gehört in den ZUGFeRD-Schritt, wenn der zweite Renderer
entsteht und die Frage "wer baut wen?" zum ersten Mal zwei Antworten hat.

## 3. Den Renderer teilen, bevor er wächst

`InvoiceRenderer` ist mit gut 600 Zeilen die größte Datei des Projekts. Die
Gliederung – eine private Funktion je Vorlagenblock, drei `when` ohne `else`
über die `InvoiceKind` (#128) – trägt noch. Aber jede neue Rechnungsart fügt
in mehreren dieser Funktionen einen Zweig hinzu: Die Entscheidungen "steht das
da?" aller Arten sammeln sich in einer Datei.

Mögliche Schnittlinien, unbewegt und unbewertet:

- **Je Block eine Datei** (Absetzung, Summen, Bank, Kopf …) – folgt der
  heutigen Funktionsgliederung, ändert an den `when` nichts.
- **Die artabhängigen Blöcke zur `InvoiceKind` ziehen** – die Zweige der drei
  `when` würden zu Methoden der Varianten. Das kollidiert allerdings mit der
  Schichtung: `calculation` kennt heute kein LaTeX, und das soll so bleiben.
  Eher: je Variante ein Renderer-Baustein im Paket `generate.render`, der über
  ein `when` an einer Stelle gewählt wird.

**Warum nicht jetzt:** Der richtige Zeitpunkt ist, wenn #64 konkret wird – der
Schnitt sollte an der dann tatsächlich hinzukommenden Art Maß nehmen, nicht an
einer vermuteten. Vorher geschnitten, säße die Linie womöglich falsch, und
`rechnung-erwartet.tex` müsste den Umbau zweimal begleiten.

## Erwogen und verworfen

- **Alles drei jetzt bauen, weil es "sauberer" wäre.** Jeder der drei Umbauten
  hat heute genau einen Nutznießer: das Gefühl. Das Projekt entscheidet sonst
  am Anlass entlang (die Paketstruktur kam wegen der Zyklen, nicht auf
  Vorrat); dabei soll es bleiben.
- **Die Punkte als Issues anlegen.** Ein Issue verlangt eine Entscheidung und
  bekommt eine Nummer, auf die sich Commits beziehen. Hier ist nichts
  entschieden – erst der ZUGFeRD-Schritt oder #64 macht daraus Aufgaben, und
  dann mit dem Zuschnitt, den der Anlass vorgibt.

## Nicht Teil dieser Frage

- Wie der ZUGFeRD-Export selbst aussieht (Datenmodell, XML-Bibliothek,
  PDF/A-3). Hier geht es nur um die drei Stellen, die sein Kommen berührt.
- Ob die drei Umbauten *zusammen* kommen müssen. Modulschnitt und Komposition
  hängen aneinander (die Verdrahtung wandert dorthin, wo die Module
  zusammenlaufen); der Renderer-Schnitt hängt an #64 und kann früher fällig
  sein.
