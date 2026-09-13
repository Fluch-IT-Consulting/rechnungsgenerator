# Issue tracker: GitHub

Issues and specs for this repo live as GitHub issues. Use the `gh` CLI for all operations.

## Conventions

- **Create an issue**: `gh issue create --type Bug|Feature|Task --title "..." --body "..."`. Use a heredoc for multi-line bodies.
- **Read an issue**: `gh issue view <number> --comments`, filtering comments by `jq` and also fetching labels.
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` with appropriate `--label` and `--state` filters.
- **Comment on an issue**: `gh issue comment <number> --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --comment "..."`

Infer the repo from `git remote -v`; `gh` does this automatically when run inside a clone.

## Beziehungen zwischen Issues

GitHub kennt drei – zwei davon lassen sich automatisieren, die dritte nicht:

| Beziehung | Bedeutung | Wie |
|---|---|---|
| Parent / Sub-Issue | Teil-von, mit Fortschrittsanzeige am Elternteil | `gh issue edit <n> --parent <m>` |
| Blocked by / Blocking | echte Reihenfolge: erst das eine, dann das andere | `gh issue edit <n> --add-blocked-by <m>` |
| **Relates to** | gleichrangiger Bezug, ohne Hierarchie und ohne Zustandslogik | **nur von Hand** in der Oberfläche (`B R`) |

"Relates to" gibt es in der Web-Oberfläche unter *Relationships*, aber weder in
der REST-API (auch nicht unter neueren `X-GitHub-Api-Version`-Werten) noch in
GraphQL noch in `gh`. Nachgemessen am 09.09.2026: Die dokumentierten Endpunkte
unter `issues/{n}/dependencies` decken ausschließlich `blocked_by` und
`blocking` ab; `/related` und `/relationships` antworten mit 404. Wer etwas
anderes behauptet, hat den Endpunkt geraten – der echte Pfad trägt immer
`/blocked_by` am Ende.

Praktisch heißt das: Ein Agent legt das Issue an und schreibt den Bezug in den
Rumpf (die Erwähnung erzeugt beidseitig einen Querverweis in der Zeitleiste);
die Beziehung selbst setzt ein Mensch nach. Wird sie später über die API
erreichbar, gehört diese Tabelle nachgezogen.

Ein **Sub-Issue** ist die falsche Wahl, wenn das Elternteil vor dem Kind fertig
wird: Ein geschlossenes Elternteil mit offenem Kind behauptet eine
Vollständigkeit, die es nicht hat. Dann "relates to".

## Typ und Label sagen Verschiedenes

Jedes Issue bekommt einen **Typ**. Die Organisation führt drei, und sie
beschreiben, *was* das Issue ist:

| Typ | wofür |
|---|---|
| `Bug` | Verhalten, das so niemand wollte – auch wenn die Zahl rechnerisch stimmt |
| `Feature` | eine neue Fähigkeit für den Benutzer |
| `Task` | Umbau ohne neue Fähigkeit: Nähte, Testbarkeit, Doppelungen, Doku |

Gesetzt wird er beim Anlegen (`--type`) oder nachträglich
(`gh issue edit <n> --type Task`, `--remove-type`).

Der Typ bestimmt auch den **Zweignamen**: `bugfix/<nummer>` für einen `Bug`,
`feature/<nummer>` für ein `Feature`, `task/<nummer>` für einen `Task`. Wer die
Zweigliste liest, sieht damit ohne Nachschlagen, was gerade in Arbeit ist.

Die **Labels** tragen die Triage-Rolle – wer als Nächstes dran ist, siehe
`triage-labels.md`. Die GitHub-Vorgaben `bug` und `enhancement` werden
**nicht** benutzt: Sie sagten dasselbe wie der Typ und widersprachen ihm
prompt (ein Issue vom Typ `Bug` mit dem Label `enhancement`).

Die einzige Ausnahme sagt, **woher** ein Issue stammt: `architektur-review`
bekommt jedes Issue, das aus einem Architektur-Review hervorgeht – zusätzlich
zur Triage-Rolle, beim Anlegen:

```
gh issue create --type Task --label "needs-triage,architektur-review" ...
```

Der Rumpf endet trotzdem mit der Zeile `Gefunden im Architektur-Review vom
<Datum>.` Das Label macht die Issues filterbar
(`gh issue list --label architektur-review --state all`), das Datum ordnet sie
einem bestimmten Review zu – ein Label je Review wäre dafür zu viel. Weitere
Herkunftslabels kommen nicht dazu, solange niemand nach ihnen filtern will.

## Bevor die Arbeit an einem Issue beginnt

Erst nachsehen, ob schon jemand darauf sitzt:

```
gh issue view <nummer> --json assignees --jq '.assignees | map(.login)'
```

- **Niemand zugewiesen**: dem Menschen zuweisen, der die Arbeit anstößt.
  `gh issue edit <nummer> --add-assignee @me` trifft ihn, solange `gh` unter
  seinem Konto angemeldet ist – bei einem eigenen Bot-Konto stattdessen den
  Anmeldenamen ausschreiben.
- **Schon zugewiesen**: sagen, an wen, und nicht stillschweigend übernehmen.
  Wer dort steht, sitzt womöglich gerade daran.

Der Grund ist banal und trotzdem teuer: Ein Issue ohne Zuweisung sieht frei
aus, auch wenn längst jemand daran arbeitet. Die Zuweisung kostet einen Aufruf
und macht aus der Liste offener Issues eine Liste dessen, was wirklich offen
ist.

## Der Commit-Titel nennt Typ und Issue

```
typ(#nummer): Ein Satz, der sagt, was der Commit tut
```

Beispiel: `fix(#14): Gedruckte Zahlen runden nicht mehr stillschweigend`

Die Nummer gehört in die **Betreffzeile**, nicht nur in den Rumpf: Die
Commit-Liste auf GitHub zeigt allein den Betreff, alles andere steckt hinter
dem Aufklapp-Knopf. In dieser Schreibweise wird die Nummer verlinkt –
nachgemessen über die Markdown-Schnittstelle, `fix(#14):` liefert den `href`
aufs Issue.

Die Typwörter stammen aus Conventional Commits. Die Spezifikation schreibt nur
`feat` und `fix` vor, alles andere ist Empfehlung; hier gilt diese Auswahl:

| Typ | wofür |
|---|---|
| `feat` | eine neue Fähigkeit für den Benutzer |
| `fix` | Verhalten, das so niemand wollte |
| `refactor` | Umbau ohne Verhaltensänderung |
| `docs` | README, Skills, CLAUDE.md, diese Anleitungen |
| `test` | nur Tests, ohne Produktivcode |
| `chore` | Werkzeug- und Ablagekram ohne Bezug zum Programm |
| `build`, `ci` | Gradle-Aufbau, Pipelines – falls je gebraucht |

`style` fehlt bewusst: ktlint formatiert automatisch, ein reiner
Formatierungs-Commit entsteht hier nicht.

Zum **Issue-Typ**: `Bug` wird zu `fix`, `Feature` zu `feat`. Ein `Task`
zerfällt in das, was tatsächlich getan wurde – meist `refactor` oder `docs`.
Das ist Absicht: Stünde über jedem Task-Commit `chore`, wäre die Zeile beim
Überfliegen wertlos, und der Issue-Typ ist ohnehin einen Klick entfernt.

Gehört ein Commit zu keinem Issue, entfällt der Geltungsbereich – die leere
Klammer wäre Lärm:

```
docs: Der Zweigname sagt, welcher Art die Arbeit ist
```

Bricht eine Änderung bestehende **Rechnungsdateien**, trägt der Typ ein
Ausrufezeichen, dazu eine Fußzeile `BREAKING CHANGE: <was bricht>`. Ein
umbenannter YAML-Schlüssel wie `bezahlt-am` zu `vereinnahmt-am` ist genau so
ein Fall:

```
feat(#1)!: Die Schlussrechnung setzt ab, was vereinnahmt wurde
```

Der Rumpf behält trotzdem seine Schlusszeile (siehe unten): Betreffzeilen werden
abgeschnitten, in Merge-Commits umgeschrieben und in Listen gekürzt – die Zeile
im Rumpf hält die Verbindung, wenn der Titel sie verliert.

Bestehende Commits werden dafür **nicht** umgeschrieben. Sie liegen auf `main`
und in gemergten Pull Requests; die Historie neu zu schreiben wäre teurer als
der Gewinn.

## Commits und Issues verbinden

GitHub trägt einen Commit-Verweis erst in die Zeitleiste des Issues ein, wenn
der Commit den Standardzweig erreicht. Auf einem Feature-Zweig bleibt ein `#1`
in der Nachricht also unsichtbar, bis gemerged wird. Wer die Verbindung sofort
sehen will, nennt das Issue im Rumpf des Pull Requests – dieser Querverweis
erscheint beim Anlegen.

Schlusszeile je Commit, direkt vor `Co-Authored-By`:

```
Teil von #<nummer>
```

Automatisch geschlossen wird nur dort, wo das Issue wirklich erledigt ist – im
letzten Commit oder im Rumpf des PR, der ihn merged:

```
Closes #<nummer>
```

Die Schlüsselwörter (`closes`, `fixes`, `resolves`) versteht GitHub nur auf
Englisch; ein deutsches "schließt #1" bleibt ein bloßer Verweis. Ein Issue mit
mehreren Schritten – jeder Schritt ein eigener Commit – darf in den
Zwischenschritten deshalb kein Schlüsselwort tragen: Sonst schließt der erste
Merge das ganze Issue, während vier Schritte noch offen sind.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

When set to `yes`, PRs run through the same labels and states as issues, using the `gh pr` equivalents:

- **Read a PR**: `gh pr view <number> --comments` and `gh pr diff <number>` for the diff.
- **List external PRs for triage**: `gh pr list --state open --json number,title,body,labels,author,authorAssociation,comments` then keep only `authorAssociation` of `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR`, or `NONE` (drop `OWNER`/`MEMBER`/`COLLABORATOR`).
- **Comment / label / close**: `gh pr comment`, `gh pr edit --add-label`/`--remove-label`, `gh pr close`.

GitHub shares one number space across issues and PRs, so a bare `#42` may be either: resolve with `gh pr view 42` and fall back to `gh issue view 42`.

## No real customer data in issues

This repo may be made public, and its issues become public with it. Titles,
bodies, comments and pasted YAML or log excerpts must not name real customers,
projects or amounts — describe the case with "Kunde A" and invented figures
instead. See the convention in `CLAUDE.md`; it explains why an issue is harder
to clean up after the fact than a commit.

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --comments`.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. `gh issue create --label wayfinder:map`.
- **Child ticket**: an issue linked to the map as a GitHub sub-issue (`gh api` on the sub-issues endpoint). Where sub-issues aren't enabled, add the child to a task list in the map body and put `Part of #<map>` at the top of the child body. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving dev.
- **Blocking**: GitHub's **native issue dependencies**, the canonical, UI-visible representation. Add an edge with `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, where `<blocker-db-id>` is the blocker's numeric **database id** (`gh api repos/<owner>/<repo>/issues/<n> --jq .id`, _not_ the `#number` or `node_id`). GitHub reports `issue_dependencies_summary.blocked_by` (open blockers only, the live gate). Where dependencies aren't available, fall back to a `Blocked by: #<n>, #<n>` line at the top of the child body. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children (`gh issue list --state open`, scoped to the map's sub-issues / task list), drop any with an open blocker (`issue_dependencies_summary.blocked_by > 0`, or an open issue in the `Blocked by` line) or an assignee; first in map order wins.
- **Claim**: `gh issue edit <n> --add-assignee @me`, the session's first write.
- **Resolve**: `gh issue comment <n> --body "<answer>"`, then `gh issue close <n>`, then append a context pointer (gist + link) to the map's Decisions-so-far.
