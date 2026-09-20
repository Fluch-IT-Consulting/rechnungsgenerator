# Was die Liste der Regelgruppen zusammenhält

Notiz vom 20.09.2026, festgehalten für später. **Hier wird nichts entschieden.**
Anlass war eine Architektur-Durchsicht der Kotlin-Anwendung. Ihr erster Befund
war dieser: `InvoiceRules.check` ist eine handgeschriebene `listOf(...)` von
dreizehn Regelgruppen; wer eine Gruppe anlegt und ihre Zeile vergisst, bekommt
eine Rechnung, die diese Regeln nicht prüft – und weder Compiler noch Test
sagen etwas. Vorgeschlagen war, die Regelgruppe zu einem Typ zu machen, der
seine `Rule`-Kennungen selbst nennt.

Der Befund trägt nicht. Die Notiz hält fest, warum – und was der Umbau gewesen
wäre, damit die nächste Durchsicht nicht bei null anfängt.

## Warum der Befund nicht trägt

**Das Netz liegt in `CheckHelpers`, nicht in einem Strukturtest.** Alle
Regeltests kommen über `CalculatedInvoice.findings()` und damit über
`AcceptedInvoice.check` → `InvoiceRules.check`. Keine Testdatei einer
gelisteten Gruppe ruft deren `check` unmittelbar auf – die einzige Ausnahme
ist `InvoiceKindRulesTest`, und das ist gerade die Gruppe, die nicht in der
Liste steht. Fällt eine Gruppe aus der Liste, produziert
sie für ihren eigenen Test keine Befunde mehr: `shouldBe`-Vergleiche werden
rot, und `messageOf` wirft mit Klartext ("Kein Befund zu X. Gefunden: …"). Das
gilt für alle zwölf Einträge. `InvoiceKindRules` ist ohnehin nicht droppbar –
es ist ein Parameter von `InvoiceRules.check` und kein Listeneintrag, genau
dafür wurde es in #105 herausgezogen.

Das ist die Kehrseite einer Beobachtung derselben Durchsicht: dass keine
Regelgruppe über ihre eigene Schnittstelle geprüft wird, sah nach Reibung aus.
Es ist der Grund, dass die Liste hält. Wer die Regeltests eines Tages an den
Gruppen ansetzen lässt, nimmt dieses Netz weg und braucht dann ein anderes.

**Der Fall ist nie eingetreten.** Seit der Extraktion aus dem
`InvoiceCalculator` (#71) sind es vier Commits, die Gruppendateien anlegen –
`cf24bc1` (zehn Gruppen und die Liste in einem Zug), `c7541ab`
(`InvoiceKindRules`), `a603d22` (`SkontoRules`), `23919af` (`CustomerRules`,
in `77862eb` zu `FreeTextRules` umbenannt). Jede Registerzeile steht im selben
Commit wie ihre Datei. Keine nachgereichte Zeile, keine nie aufgerufene Gruppe.
Und in neun Commits an `InvoiceRules.kt` wurde die Reihenfolge kein einziges
Mal geändert, nur an gewählter Stelle eingefügt.

## Was dabei gemessen wurde

- **65 Kennungen im Enum, 65 in den Gruppen benutzt, jede genau einmal, keine
  unbenutzt.** Die Zuordnung Regel → Gruppe ist heute schon eine Funktion. Sie
  steht nur nirgends, wo ein Compiler sie sähe.
- **Die Kommentarüberschriften in `Rule` sind keine Gruppenkarte.** Sie
  beschreiben den Gegenstand ("Zeiten", "Beträge und Fristen der Rechnung"),
  nicht die Gruppe: "Positionen und Steuerkategorie" deckt zwei Gruppen,
  `GESAMTBETRAG_NEGATIV` steht unter "Beträge und Fristen" und wird in
  `CancellationRules` geprüft, und das letzte Bündel sortiert nach Schwere.
  Sie driften also nicht von den Gruppen weg – sie sind nie eine Abbildung
  davon gewesen.
- **Drei Kennungen gehören keiner Gruppe**:
  `ABSCHLAGSRECHNUNGEN_VERSCHIEDENE_KUNDEN`, `ABSCHLAGSRECHNUNG_DOPPELT` und
  `KEINE_ABSCHLAGSRECHNUNG` stehen im Enum, vergeben werden sie in
  `prepayments/Prepayments.kt`, und durch `InvoiceRules` laufen sie nie.

## Der verworfene Umbau, mit seinen Achsen

Falls die Frage wiederkommt: Sie hat mehr Achsen, als der erste Blick zeigt.

**Der Zuschnitt.** In dem Vorschlag stecken drei trennbare Zusagen: (a) keine
Gruppe fällt aus dem Prüflauf, (b) jede Kennung gehört zu genau einer Gruppe,
(c) die Befunde erscheinen in der Reihenfolge der Gruppen. (b) nimmt (a) mit:
Wenn eine Stelle verlangt, dass alle Kennungen von registrierten Gruppen
abgedeckt sind, ist eine vergessene Gruppe eine unabgedeckte Kennungsmenge.
Umgekehrt gilt das nicht.

**Die Richtung.** Drei Formen, mit verschiedenen Zusagen:

- `Rule` trägt die Gruppe als zweite Eigenschaft. Der Enum-Konstruktor
  erzwingt sie, "jede Kennung hat eine Gruppe" wird damit zur
  Übersetzungsbedingung und nicht zur Testzusage. Der Preis: Die Zuordnung
  steht nicht neben dem Code, der den Befund vergibt.
- Die Gruppe nennt ihre Kennungen. Näher am Code, aber eine vergessene Kennung
  fängt nur ein Test – und die Form bricht an den beiden Sonderfällen unten.
- Gar nichts deklarieren und die Zuordnung mit Konsist aus den Quellen
  ableiten, wie `PackageStructureTest` es mit den Importen tut. Dann kann
  nichts driften, weil die Quelle die Wahrheit ist; dafür liest ein Test
  Quelltext, um eine Menge für einen anderen Test zu bilden.

**Die beiden Sonderfälle.** `InvoiceKindRules` liefert `Result` und nicht
`List<Finding>` (ADR-0008, begründete Ausnahme) und passt deshalb in keine
Schnittstelle, die über `check` geschnitten ist – über eine Deklaration im
Enum dagegen schon. Und die drei Kennungen aus `prepayments` verlangen, dass
eine Gruppe auch sagt, **wo** sie läuft (beim Prüfen einer Rechnung oder beim
Zusammentragen der Abschläge); sonst braucht die Vollständigkeitsprüfung doch
wieder eine Ausnahmeliste, also genau das, was der Umbau abschaffen soll.

**Die Reihenfolge** bliebe am besten, was sie ist: eine geordnete Liste, von
der nur die Vollständigkeit geprüft wird. Mitgliedschaft und Reihenfolge sind
zwei Dinge. Ein Rang je Gruppe verteilte eine Angabe, die niemand begründen
kann – warum `FreeTextRules` vor `ItemRules` kommt, lässt sich nur festhalten –
über dreizehn Dateien.

## `SKONTO_RULES` bleibt

`SkontoRulesTest` hält eine Menge der sieben Skonto-Kennungen und filtert die
Befunde damit. Das sieht aus wie eine zweite, handgeschriebene Liste neben
`SkontoRules` – und dafür sollte sie fallen. Sie fällt nicht, weil sie etwas
anderes tut, als sie aussieht: Sie trägt die Zusage **"und nur dieser eine
Befund"**.

Sichtbar an den Rechnungen, die der Test baut. `val falsch = listOf(stufe("2",
30))` ist absichtlich doppelt falsch – falsche Rechnungsart *und* eine Frist
jenseits des Zahlungsziels –, und behauptet wird, dass trotzdem nur
`SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH` kommt und nicht zusätzlich
`SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL` (#97). Bei
`SKONTO_OHNE_ZAHLUNGSAUFFORDERUNG` trägt die Rechnung Menge 0, also absichtlich
einen fremden `MENGE_NULL`-Hinweis. Der Filter nimmt die fremden Befunde weg,
damit `shouldBe listOf(…)` die Aussage "und nichts weiter" behalten kann.

Die Liste ist damit der Preis dafür, dass eine Testrechnung fremde Befunde
tragen *darf* – was ADR-0004 gerade als Gewinn eingeführt hat. Sauber ableiten
ließe sie sich nur über den Umbau oben.

**Woran sich der Zeitpunkt erkennen ließe:** an einer zweiten Testdatei, die
dieselbe Sorte Menge braucht. Einmal ist es eine Eigenheit, zweimal ist es ein
Muster – und dann ist die Frage nach der Zuordnung Regel → Gruppe neu zu
stellen, mit einem Nutznießer mehr als heute. Oder daran, dass eine neue
Skonto-Regel dazukommt und jemand sie in `SKONTO_RULES` vergisst: Der Filter
wäre dann zu eng, und die "nur dieser Befund"-Zusagen würden still schwächer,
ohne dass ein Test rot wird.

## Erwogen und verworfen

- **Den Umbau trotzdem bauen**, für die nächste Rechnungsart (#64) oder
  ZUGFeRD. Er kaufte eine Zusage, die ein bestehendes Netz schon hält, für 65
  geänderte Enum-Zeilen. Das Projekt entscheidet am Anlass entlang und nicht
  auf Vorrat (Notiz vom 14.09.2026); der Anlass fehlt hier nicht nur, er ist
  in der Historie nachweislich nie aufgetreten.
- **Ein Issue anlegen.** Ein Issue verlangt eine Entscheidung und bekommt eine
  Nummer, auf die sich Commits beziehen. Hier ist nichts zu entscheiden.
- **`SKONTO_RULES` über das Namensschema bilden**
  (`Rule.entries.filter { it.name.startsWith("SKONTO") }`). Passt heute auf
  alle sieben und ist trotzdem falsch: Der Name einer Kennung ist kein
  Ordnungsmerkmal, und `SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH` gehört nur
  zufällig zur selben Vorsilbe.
- **Einen Strukturtest bauen**, der die Liste gegen die `*Rules.kt`-Dateien im
  Paket vergleicht. Kleiner als der Umbau, aber er prüfte dieselbe Zusage ein
  zweites Mal – und das schwächere Mal, weil er nur Dateinamen sieht.

## Nicht Teil dieser Frage

- Ob `CheckHelpers` die richtige Testführung ist. Dass die Regeltests durch
  die ganze Prüfung gehen, ist hier das Netz; ob es auch die beste Form ist,
  eine einzelne Gruppe zu prüfen, ist eine eigene Frage.
- Die zweite Pipeline in `prepayments` – das Laden, Rechnen, Prüfen und
  Ablehnen ein zweites Mal, das `if (type != …)` statt eines `when` ohne
  `else`, der von Hand geschriebene YAML-Block. Eigener Gegenstand aus
  derselben Durchsicht.
- Die Reihenfolge der Befunde als fachliche Sortierung (nach Schwere, dann
  Gegenstand). Das änderte die Ausgabe jeder abgelehnten Rechnung und braucht
  einen eigenen Anlass.
