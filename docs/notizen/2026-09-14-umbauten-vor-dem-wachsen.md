# Drei Umbauten, deren Zeitpunkt das Wachsen ist

Notiz vom 14.09.2026, Stand 30.09.2026; Punkt 3 stammt aus einer Durchsicht
vom 16.09.2026. **Hier wird nichts entschieden.**

Drei Stellen sind bewusst einfacher gebaut, als sie es in einem größeren
Projekt wären. Keine ist ein Mangel, und keine hat heute einen Anlass. Die
Notiz hält fest, woran sich der Anlass erkennen ließe. ZUGFeRD, auf das alle
drei gewartet hatten, war es für keine (ADR-0023, „Was ZUGFeRD nicht
auslöst“).

## 1. Gradle-Module statt `PackageStructureTest`

Die Paketgrenzen hält `PackageStructureTest`, eine Importtabelle, gelesen mit
Konsist (#112). Ein verbotener Import fällt im Test auf, nicht beim
Übersetzen, und `internal` gilt je Modul, hält also keine Paketgrenze. Als
eigenes Modul griffe `internal`, ein verbotener Import wäre ein
Übersetzungsfehler, und die Tabelle schrumpfte auf das, was Module nicht
ausdrücken. Die Schnittlinie steht schon: `core` grenzt ab, was ein Modul
`:core` wäre (#310).

**Anlass:** ein zweiter Verbraucher des Kerns neben diesem Programm. Bis
dahin kostet der Schnitt Build-Aufwand – mehrere `build.gradle.kts`, ktlint
und Toolchain je Modul – ohne Nutznießer.

## 2. Komposition am Rand statt `object`

`InvoiceRenderer`, `CiiWriter`, `YamlLoader` und die Regelgruppen sind
`object`s. Gereicht wird, was ein Test ersetzen muss: `execute` bekommt die
`TypesetterFactory` (ADR-0014), den PDF-Betrachter und die Ausgabeströme.
`LuaLatexTypesetter` baut sich `LatexCompiler(LuaLatex(ExternalTool))`
dagegen selbst. Erwartet war, dass ein zweiter Ausgabemodus die Singletons
bricht. Seit ZUGFeRD stehen `InvoiceRenderer` und
`CiiWriter` nebeneinander, und es trägt: Wer zustandslos ist und nichts
braucht, dem muss niemand etwas reichen.

**Anlass:** ein `object` oder eine Klasse mit einer Abhängigkeit, die ein Test
ersetzen will – Uhr, Dateisystem, ein Prozess. Der eine bekannte Fall ist
`LuaLatexTypesetter.typeset` mit dem fest verdrahteten `ExternalTool`; ihn
prüft nur der Integrationstest, auf einer Maschine mit den Werkzeugen (#239).

## 3. Das Lesen in ein eigenes Paket

`YamlLoader` samt kaml und `InputFileException` liegt in `core.model`, Modell
und Werttypen tragen `@Serializable`, und die Werttypen melden Prüffehler als
`SerializationException`, damit `YamlLoader` sie mit Dateinamen übersetzt
(#52). Das Modell ist das Schema. Bei kotlinx.serialization ist das die
übliche Form, und die Annotationen ziehen keine Logik nach unten (#111).

**Anlass:** ein zweiter Leser ins Modell, der etwas anderes als die eigene
YAML liest. `xml` ist keiner, es reicht Bytes durch (ADR-0023). Einer wäre
`abschlaege`, wenn es die Werte aus dem XML einer Abschlagsrechnung läse
(Notiz vom 10.09.2026). Dann zöge das Lesen samt `InputFileException` in ein
eigenes Paket, und die Importtabelle bekäme eine Zeile.

## Erwogen und verworfen

- **Alle drei auf Vorrat bauen**, weil es sauberer wäre. Das Projekt
  entscheidet am Anlass entlang: Die Paketstruktur kam wegen der Zyklen
  (#107), nicht vorab.
- **Die Punkte als Issues anlegen.** Ein Issue verlangt eine Entscheidung,
  und ohne Anlass gibt es keine zu treffen.
