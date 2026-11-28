# Ein Skill führt den Umbau nach Code Health und sammelt seine Rezepte

Ein Umbau nach Code Health von CodeScene folgt dem Projekt-Skill
`.claude/skills/code-health/`. Seine `SKILL.md` beschreibt den Ablauf:
Ausgangswert, Rezeptbuch, drei bis fünf kleine Schritte, nach jedem neu
messen, das Netz der Stelle nennen und prüfen, aufhören, wenn die angegangenen
Befunde weg sind oder als bleibend geführt werden.

Daneben liegt `rezepte.md`, das **Rezeptbuch**. Ein Eintrag nennt den Befund
in der Benennung von CodeScene, Bedingungen, Grenzen, Mechanismus und einen
Beleg mit Issue, Datei und Wert vorher und nachher. Eigene Abschnitte haben
Messfallen des Werkzeugs, Fehlschläge und Befunde, die bleiben, weil ein ADR
die Form verlangt.

- **Ziel ist 9.5 je Datei unter `src/main`.** Eine Datei darunter ist ein
  Kandidat für ein Issue, außer das Rezeptbuch führt ihren Befund als
  bleibend, mit Verweis auf das ADR.
- **Testcode wird abgesichert, nicht angehoben.** Für ihn gilt #576 wie für
  jede geänderte Datei, ein Ziel hat er nicht.
- **Neue Einträge kommen im Pull Request des Umbaus**, der sie gefunden hat,
  und gelten, wenn sie das Review annimmt.

Der Skill setzt den CodeHealth-MCP voraus und ist wie dieser optional. Ohne
ihn ändert sich für niemanden etwas.

## Warum

**Absichern und Anheben sind zwei Abläufe.** Adam Tornhill trennt sie in
seinem Vortrag „AI-Friendly Code: The Missing Link for Speed with Quality“
(GOTO Copenhagen 2026): Absichern prüft jede Änderung, bis die Datei gesund
ist – das tut hier #576. Anheben bringt den Bestand gezielt auf ein Ziel, und
dabei entsteht ein Rezeptbuch. Wertvoll sind die Rezepte für genau diese
Codebasis, nicht die allgemeinen.

**Das Wissen der Umbauten lag verstreut.** Nach #578 und #580 stand jede
Erkenntnis an einem anderen Ort, und der nächste Umbau hätte sie neu
gefunden: dass ein Ausdrucksrumpf mit `with` den Wert verfälscht, nur in
einem lokalen Agenten-Gedächtnis; dass eine Parameterliste einen Befund gegen
einen anderen tauscht, in der Triage von #580; dass eine Komplexität von
genau 9 ein Befund bleibt, im PR #579; wie man prüft, dass ein Netz beim
Verschieben keine Prüfung verliert, in #580.

**9.5, weil 9 auf Menschen kalibriert ist.** Nach Tornhill bricht eine KI die
Tests bei einem Wert von 9 schon fast 60 % häufiger als bei 10.0. Gemessen
am 28.11.2026 lagen fünf von 88 bewerteten Dateien des Hauptcodes darunter,
keine unter 9.

**Testcode ohne Ziel**, weil das Absichern dort genügt: Die drei Testdateien
unter 9.5 melden nur Argumente und Strings, und 35 der 94 Testdateien
bekommen gar keinen Wert.

**Neue Einträge gehen durch das Review.** Was ADR-0028 für Begründungen
verwirft, gilt hier entsprechend: Ein Rezept wie „kleine Klasse statt
Parameterliste“ ist selbst eine Entscheidung, und ein Agent, der es beim
Aufräumen allein niederschreibt, trifft sie.

## Verworfen

- **`docs/notizen/`.** Eine Notiz steht auf heutigem Stand, ohne Nachträge,
  und fällt weg, wenn ihre Frage entschieden ist. Ein Rezeptbuch wächst.
- **`CLAUDE.md`.** Dort stehen Arbeitsregeln, kein Wissen über einzelne
  Umbauten; jeder Agent läse es bei jeder Aufgabe mit.
- **Ein Agenten-Gedächtnis.** Es liegt lokal und nicht im Repository; so lag
  die Messfalle des Ausdrucksrumpfs nach #580.
- **Eine `code-health-rules.json` mit abgeschalteten Regeln.** Eine Liste
  stillgelegter Befunde sieht aus wie ein Netz und hält keines, wie die
  Baseline von detekt in `docs/notizen/2026-09-23-statische-analyse.md`. Ein
  bleibender Befund steht im Rezeptbuch, mit seinem ADR, und bleibt sichtbar.
- **10.0 als Ziel.** Manche Form verlangt ein ADR, etwa das `when` ohne
  `else` über `TemplateValue` in `InvoiceRenderer.value` (ADR-0017).
- **Ein deutscher Name wie `umbau`.** Code Health ist der Name der Metrik und
  des Werkzeugs, und `CONTEXT.md` hat keinen Begriff dafür; `rechnung` ist
  deutsch, weil es ein Fachbegriff ist.

## Konsequenzen

- **Der Skill kommt nicht ins Paket.** Das Archiv nimmt nur
  `.claude/skills/rechnung`, und `skill installieren` kennt nur diesen
  (ADR-0030).
- **Die Testtask liest nur `.claude/skills/rechnung` als Eingabe** (#235).
  Kein Test liest im Rezeptbuch; mit dem ganzen Baum liefen nach jeder
  Änderung an ihm alle Tests neu.
- **`CLAUDE.md` nennt den Skill** und dass ein Agent ihn vor einem Umbau
  nach Code Health lädt. Das ist eine Gewohnheit und kein Netz.
- **Die Kandidaten der Messung** bekommen eigene Issues: `TaxCategoryRules`
  und `VatIdCheckRules`; `GenerateCommand` erst nach einem Blick in #359, das
  das Herausziehen aus `run` an einen zweiten Weg zur Rechnungsdatei knüpft.
  Auch `LatexCompiler` mit 9.09 bleibt Kandidat: Die Schleife der Läufe, die
  `compile` auf `cc = 9` hält, hatte #578 ausgenommen.

Entschieden am 28.11.2026, umgesetzt in #582.
