# Das PDF signieren

Notiz vom 14.09.2026, Stand 29.09.2026. **Hier wird nichts entschieden.**
Die Frage war, ob sich das erzeugte PDF signieren lässt und ob das etwas
brächte. Kurz: Technisch geht es, fachlich gibt es keinen Anlass, und es
verträgt sich schlecht mit der Reproduzierbarkeit.

Die Rechtslage unten stammt aus dem Gespräch und ist **nicht** gegen die
Quellen nachgelesen. Wer die Frage neu aufwirft, prüft §14 UStG, die GoBD und
die Übergangsregeln zur E-Rechnung zuerst.

## Was technisch geht

PAdES hängt die Signatur als inkrementelles Update an das fertige PDF; das
ist mit PDF/A vereinbar, veraPDF prüft das Ergebnis weiter. Drei Stufen, nach
dem Zertifikat:

- **Selbstsigniert** – kostenlos, der Empfänger sieht „Identität unbekannt“.
  Belegt nur, dass sich seit dem Signieren nichts geändert hat.
- **Fortgeschritten** – etwa mit einem Zertifikat aus Adobes Vertrauensliste
  (AATL), dann erscheint der grüne Haken.
- **Qualifiziert (QES)** – Zertifikat eines Vertrauensdiensteanbieters,
  jährliche Kosten, der eigenhändigen Unterschrift gleichgestellt.

Dazu optional ein Zeitstempel nach RFC 3161. In Kotlin kämen PDFBox, das
über Mustang ohnehin im Klassenpfad liegt, oder die DSS-Bibliothek der EU in
Frage.

## Warum kein Anlass

- **Nicht vorgeschrieben.** §14 Abs. 3 UStG verlangt seit 2011 keine
  Signatur. Echtheit und Unversehrtheit weist der Empfänger über ein
  innerbetriebliches Kontrollverfahren nach.
- **Auch die E-Rechnung braucht keine** (ADR-0023). Innerhalb Deutschlands
  ist das eingebettete XML die Rechnung (BR-FX-DE-03); eine Signatur über
  das PDF machte daraus nichts anderes.
- **Schwacher Schutz gegen Rechnungsbetrug.** Der realistische Angriff ist
  die ausgetauschte IBAN in einer abgefangenen Mail. Ein gefälschtes PDF wäre
  schlicht unsigniert, und das bemerkt kaum ein Empfänger. Wirksamer ist, die
  Bankverbindung einmal auf anderem Weg zu bestätigen.
- **Die eigene Ablage.** Unveränderbare Aufbewahrung (GoBD) leistet das
  Archiv; die Unversehrtheit der eigenen Kopie belegt auch eine Prüfsumme.

## Der Konflikt mit der Reproduzierbarkeit

Dieselben Quellen ergeben dasselbe PDF, Byte für Byte, auch mit
eingebettetem XML (ADR-0005, ADR-0023). Darauf baut der Abgleich über die
Prüfsumme, wenn eine Vergleichsdatei rot wird. Eine Signatur trägt den
Zeitpunkt des Signierens, ein Zeitstempel ohnehin. Wenn überhaupt, dann als
eigener Schritt nach dem Generator: Das unsignierte PDF bleibt sein Ergebnis.

## Erwogen und verworfen

- **Signieren im `Typesetter`.** Dort entsteht und prüft sich das PDF, aber
  die Naht verspricht „geprüftes PDF“, nicht „unterschriebenes“, und die
  Reproduzierbarkeit wäre weg.
- **Selbstsigniert als Zwischenlösung.** Sagt dem Empfänger nichts, und
  „Identität unbekannt“ wirkt eher verdächtiger als gar keine Signatur.

## Wann die Frage wiederkommt

- Wenn ein Kunde eine signierte Rechnung ausdrücklich verlangt.
- Als Übung mit PDFBox, PAdES und Zertifikatsketten – dann außerhalb des
  Generators.
