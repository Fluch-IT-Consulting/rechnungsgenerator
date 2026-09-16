# Drei Beobachtungen ohne Anlass

Notiz vom 16.09.2026, festgehalten für später. **Hier wird nichts entschieden.**
Anlass war ein Architektur-Review der Kotlin-Anwendung. Von seinen Befunden
sind vier anderswo gelandet: #172 (erledigt: `CalculatedInvoice` rechnet im
Rumpf), #173 (abgelehnt, weil ADR-0009 den Punkt schon entschieden hatte;
daraus #177), #179 (der Pflichtsatz als Typ) und #180 (`InvoiceKind` zieht zu
seinem Erzeuger). Drei bleiben. Keiner davon ist ein Mangel, keiner hat heute
einen Anlass, und ein Issue verlangte eine Entscheidung. Die Notiz hält fest,
was gesehen wurde und woran sich erkennen ließe, dass der Zeitpunkt da ist.

## 1. Zwei Wege zum `CliktError` in `erzeuge`

Seit #119 übersetzt `execute()` die drei Fachfehler – `InputFileException`,
`ToolchainException`, `RejectedException` – einmal für alle Kommandos in einen
`CliktError`. `GenerateCommand` geht daneben zwei eigene Wege:

- Es wirft selbst einen `CliktError` für die vorhandene Zieldatei und fängt
  die `IOException` beim Schreiben, begründet mit "nur dieses Kommando
  schreibt".
- Es wirft eine `ToolchainException`, wenn veraPDF das PDF ablehnt. Das ist
  aber kein Werkzeugfehler – veraPDF hat funktioniert –, sondern ein
  Prüfergebnis, das als Werkzeugfehler reist, weil die Ausnahme gerade da war.

Beides tut heute, was es soll: Meldung ohne Stacktrace, Exitcode 1, Text auf
stderr. Der Unterschied ist nur sichtbar, wenn man `execute()` liest und dort
drei Ausnahmen findet, wo es vier Fehlerarten gibt.

**Woran sich der Zeitpunkt erkennen ließe:** am zweiten Kommando, das eine
Datei schreibt – etwa einem ZUGFeRD-Export. Dann stünde die Übersetzung von
"gibt es schon" und "nicht beschreibbar" ein zweites Mal in einem Kommando,
und das ist genau die Lage, aus der #52 entstand. Die Richtung wäre dann eine
eigene Ausnahme fürs Schreiben, übersetzt in `execute()`, und für das
PDF/A-Urteil entweder ein eigener Typ oder ein Ergebnis wie `Verdict`, über
das das Kommando mit `when` entscheidet, statt eine fremde Ausnahme zu leihen.

## 2. `model` liest selbst

`YamlLoader` samt kaml und `InputFileException` liegt im Paket `model`. #111
hat das so gewollt: "Alles, was mit kaml zu tun hat, an einer Stelle." Dazu
kommt, dass die Datenklassen und Werttypen mit `@Serializable` annotiert sind
und `Identifiers.kt` seine Prüffehler in eine `SerializationException`
verwandelt, damit `YamlLoader` sie fängt. Das Modell *ist* das Schema, und die
unterste Schicht weiß, dass es eines gibt.

Das ist bei kotlinx.serialization die übliche Form und bei einem Eingabeformat
die einfachste richtige. Die Annotationen sind deklarativ und ziehen keine
Abhängigkeit in die Logik; das Schichtbild `values → model` bleibt wahr.

**Woran sich der Zeitpunkt erkennen ließe:** an einem zweiten Leser. Nicht am
ZUGFeRD-*Export* – der schreibt und wäre ein Randpaket wie `generate` –,
sondern an einem Kommando, das etwas anderes als die eigene YAML liest, etwa
eine fremde E-Rechnung zurück ins Modell. Dann gehörte das Lesen in ein eigenes
Paket (`model.io` oder `input`), die Importtabelle bekäme eine Zeile, und
`InputFileException` zöge mit.

## 3. Die Kommentare tragen die Änderungsgeschichte

CLAUDE.md verlangt lernorientierte, aber knappe Kommentare. Beim Lesen fällt
auf, dass viele die Geschichte einer Stelle erzählen: was vor #70 dort stand,
was #128 geändert hat, warum #147 das rohe `\\` verbot. Gezählt am 16.09.2026:
91 Issue-Verweise im Hauptcode, 30 Zeilen, die mit "vorher" oder "bis #"
beginnen.

Das hat einen Wert – die Begründung steht neben dem Code, und wer die Stelle
zurückbauen will, liest, warum er das nicht sollte. Es hat auch einen Preis:
Jede Datei trägt eine zweite Historie neben Git, und die altert. Ein "vorher"
sagt nichts mehr, sobald niemand mehr weiß, was vorher war; ein Issue-Verweis
verlangt einen Klick, um zu verstehen, was der Satz meint.

Das ist keine Architekturfrage, und die Konvention ist bewusst so gewählt.
Zwei mögliche Linien, unbewertet:

- **So lassen.** Es ist ein Lernprojekt, und die Kommentare sind sein
  Lehrbuch. Der Preis ist Lesezeit, und die trägt der, der auch den Nutzen hat.
- **Das "vorher" gehört in Commit und ADR, der Kommentar behält nur das
  "warum so".** Dann sagt ein Kommentar, was die Stelle tut und welche
  Alternative sie ausschließt, aber nicht mehr, wann sie so wurde. Die
  Issue-Nummer bleibt als Fußnote für den, der die Geschichte will.

**Woran sich der Zeitpunkt erkennen ließe:** nicht an der Technik. Eher daran,
dass das Repo öffentlich wird und ein Leser ohne Zugang zur eigenen
Issue-Geschichte die Kommentare lesen soll – oder daran, dass der Autor selbst
beim Lesen häufiger den Kommentar überspringt als ihn braucht.

## Erwogen und verworfen

- **Für die drei Punkte Issues anlegen.** Ein Issue verlangt eine Entscheidung
  und bekommt eine Nummer, auf die sich Commits beziehen. Hier ist nichts zu
  entscheiden, solange der Anlass fehlt; das Projekt entscheidet am Anlass
  entlang, nicht auf Vorrat (Notiz vom 14.09.2026). Ein Issue ohne Anlass
  wiederholt nur das Gespräch – #173 hat gezeigt, wie das ausgeht.
- **Punkt 3 als Änderung an CLAUDE.md vorschlagen.** Das wäre eine
  Entscheidung über die Konvention, und die trifft der Autor, nicht ein Review.

## Nicht Teil dieser Frage

- Was ADR-0009 zur Rechnungsart in zwei Darstellungen sagt. Das ist
  entschieden; #173 hat es bestätigt.
- Die Umbauten aus der Notiz vom 14.09.2026 (Module, Komposition,
  Renderer-Schnitt). Dieselbe Sorte Notiz, anderer Gegenstand.
