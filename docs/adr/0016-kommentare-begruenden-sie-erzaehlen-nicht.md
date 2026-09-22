# Kommentare begründen, sie erzählen nicht

Ein Kommentar sagt, **warum** eine Stelle so ist und welche Alternative sie
ausschließt. Er sagt nicht, was vorher dort stand, wann sie so wurde und in
welchem Issue – das liegt in Commit, Issue und ADR. Er erklärt auch keine
Kotlin-Idiome mehr (`takeIf gibt den Wert zurück, wenn …`); damit fällt die
bisherige Konvention „Kotlin-Idiome kurz erklären“.

Eine **Issue-Nummer in Klammern bleibt**. Sie ist keine Geschichte, sondern ein
Verweis – dasselbe, was ein ADR-Verweis tut.

## Warum

Die Kommentare tragen eine zweite Historie neben Git, und die altert. Ein
„Vorher stand hier X“ sagt nichts mehr, sobald niemand mehr weiß, was X war;
gelesen wird es trotzdem jedes Mal.

Der eigentliche Grund für dieses ADR ist aber das **Ergebnis der Messung**, und
es ist ein anderes als erwartet. Gezählt am 22.09.2026 im Hauptcode: 3.051
Kommentarzeilen auf 6.443 Codezeilen, 47 %. Die Vermutung war, dass darin die
Änderungsgeschichte steckt. Sie steckt nicht darin:

| | Zeilen |
|---|---|
| Geschichte (`vorher`, `bis #`, `seit #`, `früher`) | rund 60 |
| Lehrsätze zu Kotlin-Idiomen | rund 30 |
| Issue-Verweise gesamt (158), davon tragend | 7 |
| ADR-Verweise | 56 |
| **alles übrige: Begründungen und Normverweise** | **rund 2.900** |

Die 47 % bestehen aus „warum so“, aus BT-Nummern der EN 16931 und aus
Paragraphen des UStG. In `calculation`, der dichtesten Stelle mit 65 %,
überlebt unter dieser Regel praktisch jede Zeile: `roundToAmount()` als einzige
Rundung, das Vorzeichen des Rabattbetrags im Storno, „die Differenz und nicht
Basis × (1 − Satz)“.

Deshalb steht hier eine Regel und keine Zahl. Nach dem Schnitt liegt die Dichte
bei rund 44 %, und das ist richtig so: Wer sie auf ein landläufiges Maß bringen
wollte, müsste an die Begründungen, und die stehen mit Absicht dort.

## Verworfen

- **Auf 15–20 % kürzen.** Das nähme die Konvention zurück, dass die Begründung
  beim Aufrufer steht und der Wertetyp seine Aufrufer nicht kennt. Wer
  `Money`, `Price` und `roundToAmount` ohne die Sätze am Aufrufer liest, sieht
  die Grenze der EN 16931 nicht mehr, die sie abbilden. Das wäre kein
  Aufräumen, sondern eine Kehrtwende – und sie müsste als solche entschieden
  werden, nicht als Nebenwirkung einer Prozentzahl.
- **Die 130 entbehrlichen Issue-Nummern streichen.** Wörtlich genommen
  verlangt die Regel das: Wo der Kommentar die Begründung selbst trägt, ist die
  Nummer Fußnote. Sie kostet aber vier Zeichen am Zeilenende und ist der
  einzige Weg von hier zu der Diskussion, die den Satz erzeugt hat. Eine Nummer
  altert nicht – sie zeigt auf ein Dokument, das bleibt. Je weniger der
  Kommentar daneben erzählt, desto wertvoller wird sie.
- **Ein Test auf die Geschichtswörter.** Er wäre billig (`PackageStructureTest`
  liest ohnehin alle Quellen) und sein Prädikat fast scharf. Fast: `vorher`
  heißt an drei Stellen „zeitlich davor“ und nicht „früher einmal“
  (`Template.kt:34`, `ProcessRunner.kt:38`, `PdfAValidator.kt:66`), und `seit #`
  beschreibt meist die Gegenwart. Es bleibt bei einer **Gewohnheit und keinem
  Netz** – dieselbe Wahl wie bei den Kommentaren im Versionskatalog (#253),
  mitsamt demselben nächsten Schritt, falls sie steckenbleibt.
- **Der Agent schreibt die fehlenden ADRs.** Wo eine Begründung nirgends sonst
  steht, bleibt der Kommentar stehen und die Stelle kommt auf eine Liste. Ein
  Agent, der beim Aufräumen Architekturentscheidungen dokumentiert, trifft sie.

## Konsequenzen

- **`CLAUDE.md` ändert seine Konvention.** „Kotlin-Idiome kurz erklären“
  entfällt, die Regel oben tritt an ihre Stelle.
- **`CLAUDE.md` selbst ist davon noch nicht berührt.** Es trägt 81 Zeilen mit
  Geschichtsmarkern oder Issue-Nummern auf 7.225 Wörter – das Dokument, auf das
  die Kommentare künftig zeigen, hat dieselbe Krankheit in stärkerer Form. Das
  ist ein eigener Vorgang: Welcher Kommentar trägt, und welcher Abschnitt eines
  Architekturdokuments trägt, sind verschiedene Fragen mit verschiedenen Lesern.
- **Die heimatlosen Begründungen werden sichtbar.** Was beim Streichen auffällt
  und nirgends sonst steht, sammelt der Pull Request in einer Liste. Jede davon
  wird ein ADR, eine Notiz oder gar nichts – von Hand entschieden. #283 ist ein
  solcher Fall, von Hand gefunden, bevor diese Regel galt.
- **Das Pflichtangaben-Netz bekommt seinen Namen.** An fünf Stellen hieß es
  „das Netz aus #187“; dort war die Nummer tatsächlich tragend, weil der Name
  nie in den Code kam. Der Test heißt `PflichtangabenTest`, gemeint ist „das
  Pflichtangaben-Netz“, und damit ist auch die Nummer entbehrlich. Über Code
  und Dokumente hinweg stand derselbe Begriff bis dahin dreifach da: „das Netz“
  (12×), „Pflichtangaben-Netz“ (5×), „Netz aus #187“ (4×).
- **Die Vorlagen sind fast nicht betroffen.** Von 315 Kommentarzeilen in
  `balken.tex` und `raster.tex` sind 11 Geschichte. Der Rest ist Satzbild-
  Begründung, Zuständigkeitsregel oder LaTeX-Fallstrick – und das Nachgemessene
  („nachgemessen braucht die längste Zeile 69,8 mm“) ist dort der einzige Ort,
  an dem das Ergebnis überhaupt stehen kann, weil kein Test Millimeter prüft.
  Ein LaTeX-Fallstrick ist außerdem kein Lehrsatz: `takeIf` kann man
  nachschlagen, dass `minipage` über `\@parboxrestore` den Flattersatz
  zurücksetzt, hat jemand einmal teuer bezahlt.

Entschieden am 22.09.2026, umgesetzt in #280.
