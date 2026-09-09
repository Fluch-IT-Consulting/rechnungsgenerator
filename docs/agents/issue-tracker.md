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

Die **Labels** tragen ausschließlich die Triage-Rolle – wer als Nächstes dran
ist, siehe `triage-labels.md`. Die GitHub-Vorgaben `bug` und `enhancement`
werden **nicht** benutzt: Sie sagten dasselbe wie der Typ und widersprachen ihm
prompt (ein Issue vom Typ `Bug` mit dem Label `enhancement`).

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
