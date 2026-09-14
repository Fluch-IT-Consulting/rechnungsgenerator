# Das PDF signieren

Notiz vom 14.09.2026, festgehalten für später. **Hier wird nichts entschieden.**
Anlass war die Frage, ob sich das erzeugte PDF nach der Erstellung signieren
lässt und ob das einen Mehrwert hätte. Kurz: Technisch geht es gut, fachlich
gibt es heute keinen Anlass, und es verträgt sich schlecht mit der
Reproduzierbarkeit des Generators.

Die Rechtslage unten stammt aus dem Gespräch und ist **nicht** gegen die
Quellen nachgelesen. Wer die Frage neu aufwirft, prüft §14 UStG, die GoBD und
die Übergangsregeln zur E-Rechnung zuerst.

## Was technisch geht

Für PDFs gibt es PAdES (PDF Advanced Electronic Signatures). Die Signatur wird
als inkrementelles Update an das fertige PDF gehängt; das ist mit PDF/A-1b
vereinbar, veraPDF prüft das Ergebnis weiter. Drei Stufen, nach dem
Zertifikat:

- **Selbstsigniert** – kostenlos, aber der Empfänger sieht „Identität
  unbekannt“. Belegt nur, dass sich seit dem Signieren nichts geändert hat.
- **Fortgeschritten** – etwa mit einem Zertifikat aus Adobes Vertrauensliste
  (AATL), dann erscheint der grüne Haken.
- **Qualifiziert (QES)** – Zertifikat eines Vertrauensdiensteanbieters,
  jährliche Kosten, der eigenhändigen Unterschrift gleichgestellt.

Dazu optional ein Zeitstempel eines Zeitstempeldienstes (RFC 3161). In Kotlin
kämen Apache PDFBox oder die DSS-Bibliothek der EU in Frage.

## Warum kein Anlass

- **Nicht vorgeschrieben.** §14 Abs. 3 UStG verlangt seit 2011 keine Signatur
  mehr. Echtheit und Unversehrtheit weist der Empfänger über ein
  innerbetriebliches Kontrollverfahren nach; die Signatur ist eine mögliche
  Form, keine Pflicht.
- **Keine E-Rechnung.** Eine Signatur macht aus dem PDF keine strukturierte
  Rechnung. Im inländischen B2B-Verkehr ist das reine PDF ab 2027 nur noch
  unterhalb einer Umsatzgrenze zulässig, ab 2028 gar nicht mehr. Dann trägt
  ZUGFeRD beziehungsweise XRechnung die Rechnung, und auch die brauchen keine
  Signatur. Die Arbeit ist beim ZUGFeRD-Export besser angelegt.
- **Schwacher Schutz gegen Rechnungsbetrug.** Der realistische Angriff ist die
  ausgetauschte IBAN in einer abgefangenen Mail. Eine Signatur hilft nur, wenn
  der Empfänger sie prüft – ein gefälschtes PDF wäre schlicht unsigniert, und
  das bemerkt kaum jemand. Wirksamer: die Bankverbindung einmal auf anderem Weg
  bestätigen.
- **Die eigene Ablage.** Unveränderbare Aufbewahrung (GoBD) leistet das Archiv,
  nicht die Signatur. Die Unversehrtheit der eigenen Kopie belegt auch eine
  Prüfsumme.

## Der Konflikt mit der Reproduzierbarkeit

Dieselben Quellen ergeben heute dasselbe PDF, Byte für Byte
(`SOURCE_DATE_EPOCH`, Trailer-ID aus der Prüfsumme von `.tex` und
`.xmpdata`). Eine Signatur trägt den Zeitpunkt des Signierens, ein Zeitstempel
ohnehin – signiert der Generator selbst, ist die Zusage weg, und mit ihr der
Abgleich über die Prüfsumme des PDF, den `rechnung-erwartet.tex` vorsieht.

Wenn überhaupt, dann als eigener, nachgelagerter Schritt: Das unsignierte,
reproduzierbare PDF bleibt das Ergebnis des Generators, die Signatur kommt
danach dazu.

## Erwogen und verworfen

- **Signieren im `Typesetter`.** Liegt nahe, weil dort das PDF entsteht und
  geprüft wird. Bricht aber genau die Reproduzierbarkeit, und die Naht
  verspricht „geprüftes PDF“, nicht „unterschriebenes“.
- **Selbstsigniertes Zertifikat als Zwischenlösung.** Kostet nichts, sagt dem
  Empfänger aber nichts – und eine Warnung „Identität unbekannt“ wirkt eher
  verdächtiger als gar keine Signatur.

## Wann die Frage wiederkommt

- Beim ZUGFeRD-Export (PDF/A-3 mit XML): Ob dort signiert werden soll, gehört
  in dieselbe Entscheidung.
- Wenn ein Kunde eine signierte Rechnung ausdrücklich verlangt.
- Als Lernübung (PDFBox, PAdES, Zertifikatsketten) – dann aber außerhalb des
  Generators.
