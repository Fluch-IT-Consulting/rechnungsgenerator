# Der rechnung-Skill kommt im Paket und wird als Link eingehängt

Das Archiv des Generators enthält den `rechnung`-Skill unter
`skill/rechnung/`, neben `bin/` und `lib/`. Bei Claude Code hängt ihn ein
Kommando ein:

    rechnungsgenerator skill installieren
    rechnungsgenerator skill status
    rechnungsgenerator skill entfernen

`installieren` legt `~/.claude/skills/rechnung` als symbolischen Link auf den
Skill im Paket an. Ist `CLAUDE_CONFIG_DIR` gesetzt, tritt dieser Ordner an die
Stelle von `~/.claude`. Fehlende Ordner auf dem Weg legt es an.

Wohin der Link zeigt, sagt `RECHNUNGSGENERATOR_SKILL`. Ohne die Variable ist es
`skill/rechnung` neben dem `lib/`, aus dem das Programm läuft. Der Pfad wird
absolut gemacht, aber nicht aufgelöst.

Unterschieden wird zwischen einem Link und allem anderen:

| An der Stelle liegt | `installieren` | `entfernen` | `status` |
|---|---|---|---|
| nichts | legt den Link an | 0, nichts zu tun | „Nicht eingehängt“ |
| der eigene Link | 0, nichts zu tun | löscht ihn | „Eingehängt“ samt Ziel |
| ein Link auf ein anderes Ziel | bricht ab, nennt Ziel und `entfernen` | löscht ihn | „Eingehängt“ samt Ziel |
| ein Ordner oder eine Datei | bricht ab | bricht ab | „Kein Link“ |

`status` endet immer mit 0 und gibt eine Zeile aus. Zeigt ein Link ins Leere,
steht das dabei. `installieren` bricht ab, wenn an der Quelle keine `SKILL.md`
liegt. Kein Kommando schreibt in `~/.claude/settings.json`.

`.claude/skills/rechnung/` bleibt die einzige Quelle, mit einem Text für
Repository und Paket. Der Text ruft `rechnungsgenerator` aus dem PATH, nennt
sein Skriptverzeichnis über `${CLAUDE_SKILL_DIR}` und sucht die Ablage über
`RECHNUNGEN_DIR`, dann über die Wurzel über dem Arbeitsverzeichnis
(ADR-0015).

## Warum

**Der Skill beschreibt genau eine Fassung des Programms.** Er nennt
YAML-Schlüssel, Unterkommandos und Meldungen. Nur im selben Paket kommen
beide zwingend gemeinsam, und nur ein Link hält das nach einem `brew upgrade`
ohne weiteren Schritt.

**Maßstab ist, wer nur `brew install` kennt.** Er sieht das Repository nie.
Deshalb ruft der Text kein `build/install/…` und kein `./gradlew`, und deshalb
wird benutzerweit eingehängt und nicht in der Wurzel der Ablage: Ein zweiter
Rechnungssteller mit eigener Wurzel bräuchte sonst eine zweite Installation.

**Den stabilen Pfad kennt allein die Formel.** Das Startskript löst Links auf
und sieht nur `…/Cellar/rechnungsgenerator/<fassung>/…`. Ein Link dorthin
risse beim nächsten Upgrade ab. Die Formel reicht den Pfad über `opt/` in
`RECHNUNGSGENERATOR_SKILL` herein, und das Programm löst ihn nicht auf. Der
Rückfall neben `lib/` stimmt für ein von Hand entpacktes Archiv und für
`installDist`.

**Ein echter Ordner wird nie angerührt.** Das ist jemandes eigener Skill oder
eine angepasste Kopie. Einen Link dagegen löscht `entfernen` immer, gleich
wohin er zeigt: Nach `brew uninstall` zeigt auch der eigene ins Leere, und
wer das Programm danach neu installiert, soll ihn wegnehmen können.

**`skill` hat Unterkommandos, obwohl ADR-0003 je Art von Ergebnis eines
vorsieht.** Die drei sind Schritte an derselben Sache und keine Arten von
Ergebnis des Generators. Auf oberster Ebene stünden sie neben `erzeuge`, als
gehörten sie zum Rechnungsschreiben.

## Nachgemessen

Am 14.11.2026 mit Claude Code 2.1.280, je Fall in einer frischen Sitzung
(`claude -p`), mit einem Probe-Skill, der seinen Ort und
`${CLAUDE_SKILL_DIR}` ausgibt.

- **Ein Link unter `~/.claude/skills` wird geladen.** Der Skill lag dort nur
  als symbolischer Link auf einen Ordner anderswo.
- **`${CLAUDE_SKILL_DIR}` wird in `SKILL.md` ersetzt**, und zwar durch den
  Pfad des Links (`~/.claude/skills/<name>`), nicht durch sein Ziel. Dass es
  in den übrigen Dateien eines Skills ersetzt wird, sagt die Dokumentation
  nicht; `schlussrechnung.md` benutzt die Variable deshalb nicht.
- **Der benutzerweite Skill verdrängt den gleichnamigen im Projekt.** Mit dem
  Skill nur im Projekt antwortete der des Projekts, mit beiden der
  benutzerweite. Die Dokumentation sagt dasselbe („personal over project“)
  und nennt keinen Schalter je Projekt.
- **Mit `CLAUDE_CONFIG_DIR` liegen die Skills unter
  `$CLAUDE_CONFIG_DIR/skills`.** Ein Skill, der nur dort lag, stand in der
  Liste der Sitzung, die Skills aus `~/.claude/skills` standen nicht darin.
  Die Dokumentation nennt die Variable als Ersatz für `~/.claude`, die Skills
  aber nicht ausdrücklich.

## Verworfen

- **Ein Plugin-Marketplace oder `npx skills add`.** Das öffentliche
  Repository gäbe beides her. Der Skill käme dann auf eigenem Weg und in
  eigener Fassung, neben einem Programm, das älter oder neuer ist.
- **Eine Kopie statt eines Links.** Eine Kopie, die niemand aktualisiert, ist
  die Drift, die ein Programm älter als seine Anleitung macht. Ein Kommando
  „aktualisieren“ gibt es deshalb nicht.
- **Ein Shell-Skript im Paket.** Das Kommando steht in
  `rechnungsgenerator --help`, seine Tests laufen im vorhandenen Netz, und es
  kommt keine dritte Sprache ins Repository.
- **Ein zweiter Name für den Skill im Paket**, damit er den im Repository
  nicht verdrängt. Das wäre ein zweiter Text, den `SkillExamplesTest` nicht
  prüft.
- **Eine Formel, die selbst einhängt.** Homebrew sperrt `install` und
  `post_install` in eine Sandbox, die nicht nach `~/.claude` schreibt, und
  `brew uninstall` kennt keinen Haken.
- **Vom Pfad in `Cellar/` auf `opt/` schließen.** Das wäre Homebrew-Wissen im
  Generator.
- **`--force` für `installieren`.** Was an der Stelle liegt, hat jemand
  anderes angelegt; den Link nimmt `entfernen` weg, den Ordner der Mensch.
- **Die Python-Skripte in den Generator ziehen.** Das hat #74 verworfen.

## Konsequenzen

- Das Archiv bekommt `skill/rechnung/` über `distributions`. Die Skripte
  bekommen ihr Ausführungsbit eigens, weil Gradle sonst jede Datei mit 644
  schreibt; das Archiv bleibt reproduzierbar (#266). `release.yml` prüft, dass
  `SKILL.md` und beide Skripte im Archiv liegen und die Skripte ausführbar
  sind.
- Ein neues Paket `skill`, das nichts aus dem Projekt importiert.
- Was nur im Repository gilt, steht in `CLAUDE.md`: dass mit
  `rechnungsgenerator` dort `build/install/…` gemeint ist, und dass ein
  benutzerweit eingehängter Skill den im Repository verdrängt. Wer am Skill
  arbeitet, nimmt den Link vorher weg. Das ist eine Gewohnheit und kein Netz.
- Die Formel setzt `RECHNUNGSGENERATOR_SKILL` in einem Startskript, bevor die
  erste Fassung mit dem Skill erscheint. Käme die Fassung zuerst, zeigte ein
  Link aus dieser Zwischenzeit in `Cellar/`.
- Nur Claude Code, also CLI und der Code-Tab der Desktop-App, und nur macOS
  und Linux: Der Link setzt ein Dateisystem voraus, das ihn kennt.
- Die Durchsicht des Skilltextes für Fremde steht in #552.
