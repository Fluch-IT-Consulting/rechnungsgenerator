# Ein Kommentar warnt, eine Begründung hat eine Heimat

Löst ADR-0016 ab.

Ein Kommentar steht nur dort, wo jemand, der Kotlin und die Domäne kennt, die
Stelle ohne ihn falsch ändern würde. Dass er etwas begründet, genügt nicht.

- **Form:** ein bis zwei Zeilen, das KDoc einer Klasse höchstens ein kurzer
  Absatz. Länger nur für Fallstricke fremder Werkzeuge, die sich nicht
  nachschlagen lassen (LaTeX, Mustang), und für Nachgemessenes, das kein Test
  hält.
- **Immer erlaubt:** BT-Nummern, Paragraphen und Verweise auf ADR, Issue,
  Glossar oder Regelkennung.
- **Nie:** was Compiler, Typ oder Test schon erzwingen, Geschichte der Stelle,
  Kotlin-Idiome.

Jede Begründung steht an genau einem Ort:

| Was | Wohin |
|---|---|
| Falle an einer Stelle | Kommentar |
| Entscheidung samt verworfener Alternativen | ADR, der Code verweist |
| Bedeutung eines Fachbegriffs | `CONTEXT.md` |
| Diskussion, die zur Stelle geführt hat | Issue |
| Arbeitsregel für Agenten | `CLAUDE.md`, als Regel mit Verweis |

## Warum

Das Repository wird öffentlich, und 44 % Kommentare im Hauptcode sind für ein
reguläres Projekt zu viel – so der wiederholte Befund aus Reviews. ADR-0016
konnte daran nichts ändern: Es fragte, ob ein Kommentar begründet, und fast
jeder hier begründet. Drei Durchgänge unter dieser Regel (#280, #329, #360)
kürzten von 47 % auf 44 %.

Dieselbe Begründung stand dazu oft dreimal da – am Code, in `CLAUDE.md` und im
ADR – und die drei Fassungen altern getrennt. Und lange Kommentare verdecken
die wenigen, die warnen, aus demselben Grund, aus dem der Laufzeit-Hinweis zur
EU-Liste bei Inlandsrechnungen schweigt: Wer ständig liest, überliest auch das
Wichtige.

Kalibriert ist das Maß an `CalculatedInvoice.kt`, der dichtesten Datei: 203
Kommentarzeilen wurden 37, die Quote fiel von 67 % auf 27 %, und jede
gestrichene Begründung stand schon in einem ADR, einem Issue oder im Glossar.

## Verworfen

- **ADR-0016 nachschärfen.** Das Kriterium „begründet“ bleibt dann dasselbe,
  und mit ihm das Ergebnis.
- **Eine Quote als Ziel.** Sie lädt dazu ein, bis zur Zahl zu streichen statt
  nach der Frage. Die Quote ist ein Befund, kein Ziel, und es gibt keinen Test
  auf sie.
- **Heimatlose Begründungen selbst zu ADRs machen.** Wie in ADR-0016: Ein
  Agent, der beim Aufräumen Entscheidungen dokumentiert, trifft sie.

## Konsequenzen

- **Aus ADR-0016 bleibt:** keine Geschichte, keine Kotlin-Idiome,
  Issue-Nummern als Verweis.
- **Die Begründung am Aufrufer schrumpft auf einen Verweis.** Ein Wertetyp
  kennt seine Aufrufer weiterhin nicht; der Aufrufer trägt eine Zeile und
  zeigt auf das ADR, nicht die ganze Herleitung.
- **Umgeschrieben wird je Datei von null**, nicht gekürzt: Kürzen erhält die
  Gliederung und damit die Länge. Paketweise, die dichtesten zuerst.
- **Heimatlose Begründungen** sammelt der Pull Request in einer Liste. Jede
  wird ein ADR, eine Notiz oder gar nichts – von Hand entschieden.
- **`CLAUDE.md` wird eine Arbeitsanleitung** und kein Architekturdokument. Das
  ist ein eigener Schritt und wird die meisten heimatlosen Begründungen
  hervorbringen, etwa die Grenze zwischen `Money` und `Price`.
- **Tests und Vorlagen** folgen derselben Frage. In den Vorlagen bleiben
  LaTeX-Fallstricke und nachgemessene Maße.
- **Gegen das Nachwachsen gibt es eine Gewohnheit und kein Netz**, wie bei den
  Kommentaren im Versionskatalog (#253).

Entschieden am 29.09.2026, umgesetzt in #462.
