---
name: code-health
description: >
  Führt einen Umbau nach Code Health von CodeScene und hält das Rezeptbuch
  dieser Codebasis. Lade ihn vor jedem Umbau, der einen Befund des
  CodeHealth-MCP beheben soll: eine Datei unter 9.5 anheben oder eine Datei,
  deren Wert eine Änderung gesenkt hat, wieder gesund machen.
---

# Umbau nach Code Health

Ziel ist **9.5 je Datei unter `src/main`** (ADR-0032). Testcode wird beim
Ändern abgesichert wie jede Datei (#576), aber nicht eigens angehoben.

Ohne verbundenen CodeHealth-MCP gibt es keinen Wert. Sag das und führe den
Umbau ohne diesen Skill.

## Ablauf

1. **Ausgangswert.** `code_health_score` und `code_health_review` der Datei.
   Notiere den Wert und jeden Befund mit Funktion und Kennzahl (`cc = 16`,
   `LoC = 149`). Die Datei liegt dafür im Repository, nie als Kopie.
2. **Rezeptbuch lesen**, [`rezepte.md`](rezepte.md), ganz. Ordne jeden Befund
   zu: Ein Rezept greift, er ist bleibend, er ist eine Messfalle, oder er ist
   neu. Einen bleibenden Befund gehst du nicht an; ein neuer, dessen Form ein
   ADR des Bereichs verlangt, ist eine Frage an Martin, kein Umbau.
3. **Netz nennen.** Welche Tests halten die Stelle, und tragen sie den Tag
   `Toolchain`? Dann zählt erst der volle `./gradlew build`. Verschiebt der
   Umbau Prüfungen, gilt das Rezept „Prüfungen zählen“: Zähle jetzt, vor dem
   ersten Schritt.
4. **Drei bis fünf kleine Schritte**, je einer für einen Befund, und jeder für
   sich grün. Ein Rumpf wird verschoben, nicht umgeschrieben; der Kommentar
   zieht mit seiner Zeile um.
5. **Nach jedem Schritt messen.** Review neu, Wert notieren, Tests der Stelle
   laufen lassen. Taucht ein Befund auf, den es vorher nicht gab, schlag ihn
   unter „Messfallen“ und „Fehlschläge“ nach, bevor du weiterbaust.
6. **Aufhören**, wenn jeder angegangene Befund weg ist oder das Rezeptbuch ihn
   als bleibend führt. Ein Befund, der trotz Schritt steht, ist ein Ergebnis:
   Er kommt in den Bericht, nicht in einen sechsten Schritt.
7. **Bericht** im Pull Request: Wert vorher und nachher, welche Befunde weg
   sind und welche stehen, warum; das Netz und wie es geprüft wurde.

## Das Rezeptbuch wächst im Review

Was der Umbau gelehrt hat, schlägt sein Pull Request als Eintrag in
`rezepte.md` vor: ein Rezept, das gegriffen hat, eine Messfalle, ein
Fehlschlag oder ein Befund, den ein ADR verlangt. Jeder Eintrag trägt einen
Beleg aus diesem Umbau – Issue, Datei, Wert vorher und nachher. Er gilt, wenn
Martin ihn im Review annimmt (ADR-0032).

Eine Datei unter 9.5, die das Rezeptbuch nicht als bleibend führt, ist ein
Kandidat für ein Issue. Das Issue entwirft der Agent, angelegt wird es nach
Martins Freigabe.
