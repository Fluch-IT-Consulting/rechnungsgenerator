# Das Storno nennt seine Ursprungsrechnung und rechnet mit negativen Mengen

Eine fünfte Rechnungsart: `art: stornorechnung`, Code 384. Sie hebt eine bereits
ausgestellte Rechnung vollständig auf und trägt dafür

- den **Bezug auf die Ursprungsrechnung** als Pflichtangabe – Nummer und Datum,
  in einem eigenen Block `storniert:` und nicht in einem Satz;
- die Positionen der Ursprungsrechnung mit **negativer Menge**, sodass der
  Gesamtbetrag negativ wird;
- **kein Zahlungsziel**: Ein Storno fordert nicht zur Zahlung auf.

Die Rechnungskorrektur (einzelne Angaben ändern) und die Entgeltminderung nach
§17 UStG (Rabatt, Rückgabe, Skonto) sind eigene Vorgänge und bleiben draußen.

## Warum

Bis hierher konnte der Generator eine falsche Rechnung nicht zurücknehmen. Der
Calculator sagte das an zwei Stellen selbst – "eine Gutschrift, und die gibt es
hier noch nicht" –, und ein negativer Gesamtbetrag war deshalb pauschal ein
Abbruch.

**Code 384 und nicht 381.** Die KoSIT hat das für XRechnung ausdrücklich
entschieden: 384 für Korrektur und Storno, mit Bezug auf die Ursprungsrechnung
(BG-3); 381 für die kaufmännische Gutschrift ohne solchen Bezug; 389 für die
Gutschrift im Sinne des §14 Abs. 2 Satz 2 UStG. Nachgelesen am 12.09.2026 in
https://projekte.kosit.org/xrechnung/xrechnung/-/issues/23 (abgeschlossen, im
Release umgesetzt). Viele Ratgeber schreiben 381 – das galt für uns lange als
gesetzt und war beim Nachmessen falsch.

**Negativ über die Menge und nicht über den Einzelpreis.** EN 16931 verbietet
den negativen Einzelpreis (BR-27), weil er in der Buchhaltung des Empfängers
offenließe, ob Gutschrift, Rabatt oder Erfassungsfehler gemeint ist. Genau so
war dieses Projekt schon gebaut: negative Menge erlaubt, negativer Einzelpreis
Abbruch. Die Frage stand seit #47 offen, mit dem Vermerk, sie hänge an Storno
und Gutschrift – sie hing daran, und die Antwort ändert nichts.

**Das Wort "Gutschrift" fällt nicht.** Umsatzsteuerlich ist eine Gutschrift eine
Rechnung, die der Leistungsempfänger ausstellt. Das BMF-Schreiben vom 25.10.2013
nimmt der falschen Bezeichnung die Folge des §14c, die Verwechslung bleibt. Im
Repo stand das Wort an acht Stellen in der falschen Bedeutung.

## Verworfen

- **381 mit positiven Beträgen**, wo die Richtung allein am Typ hängt. Das ist
  die verbreitete Praxis und wäre für den Calculator die billigere Lösung
  gewesen – keine Regel hätte sich geändert. Es widerspricht aber der
  Festlegung der KoSIT, und die zählt, sobald ZUGFeRD dazukommt. Nebenbei ist
  das Vorzeichen bei 381 dort selbst ungeklärt: Die Frage steht seit XRechnung
  1.1 im Validator-Tracker und wurde ohne dokumentierte Antwort geschlossen.
- **Den Bezug in den Pflichtsatz schreiben**, mit Platzhaltern in
  `InvoiceType.mandatoryNote`, aufgelöst beim Setzen. Drei Kosten: Kein Compiler
  prüft die Platzhalter – ein vertipptes `{rechnungsdatum}` stünde als
  geschweifte Klammer in einem Satz, der auf dem Dokument stehen muss, und
  genau dieses Muster hat schon einmal zu `TemplateMacro` geführt. Die
  Reihenfolge des Escapens würde zur Falle. Und die Liste der einsetzbaren
  Werte stünde im Renderer, der Wortlaut im Enum. Dagegen ist der Bezug in
  EN 16931 ohnehin **Struktur** (BT-25, BT-26) und keine Prosa: Wer ihn später
  fürs XML aus einem Satz herausklauben muss, rät. Er steht deshalb als eigene
  Zeile im Kopf, und der Pflichtsatz bleibt konstant. (Aufgehoben samt der
  Zeile im Kopf, siehe ADR-0010.)

  Der Unterschied zu ADR-0005, wo eine Platzhaltersprache aus demselben Grund
  scheiterte, ist trotzdem einer: Dort kommt der Text von außen, aus den
  Stammdaten, und ein Benutzer kann keinen Kotlin-Code schreiben. Hier steht er
  im eigenen Quelltext, und dort ist Kotlin die Vorlagensprache, die es schon
  gibt.
- **Den Leistungszeitraum aus der Ursprungsrechnung erben.** Der Generator
  sieht beim Erzeugen eine einzige Datei (ADR-0001); das Storno trägt seinen
  Zeitraum selbst, wie jede andere Art.
- **Die Rechnungskorrektur gleich mitbauen.** Sie hebt nicht alles auf, sondern
  ändert einzelne Positionen oder Beträge, und muss dafür sagen können, *was*
  sie ändert. Vor allem aber kippt bei ihr die Vorzeichenregel: Eine
  Berichtigung kann nach oben gehen – zu wenig berechnet, also Nachforderung –,
  und dann ist der Gesamtbetrag positiv. Ein eigenes Modell also, keine
  Variante des Stornos. Festgehalten in #64.

## Konsequenzen

- **`GESAMTBETRAG_NEGATIV` gilt nicht mehr überall.** Bei einer Stornorechnung
  ist der negative Betrag die Regel; umgekehrt kommt eine neue hinzu, die einen
  *positiven* Gesamtbetrag in einem Storno ablehnt. Ein Storno über einen
  positiven Betrag ist ein Widerspruch in sich.
- **Kein Zahlungsziel, also auch keine Fälligkeit.** `dueDate` liefert `null` –
  derselbe Zug wie beim Restbetrag null (ADR-0002): Das Blatt soll nicht zwei
  verschiedene Aussagen tragen.
- **"Rechnet ab und fordert zur Zahlung auf" ist eine eigene Tatsache**
  (`InvoiceType.chargesForService`), nicht viermal die Frage "ist es ein
  Storno?". Daran hängen Zahlungsziel, Bankverbindung, die Sechs-Monats-Frist
  des §14 Abs. 2 UStG und das Alter der Bestätigungsabfrage. Die beiden
  Hinweise meldeten sich zunächst auch auf einem Storno: Sie fragen nach dem
  Rechnungsdatum, und das liegt bei einem Storno naturgemäß Monate nach der
  Leistung – sie forderten also, ein richtiges Datum zu ändern und eine
  gegenstandslose Abfrage zu wiederholen. Das Vorzeichen des Gesamtbetrags
  fragt weiter nach der Rechnungsart selbst: Dort ist es wirklich das Storno,
  das gemeint ist, und keine abgeleitete Eigenschaft.
- **`RESTBETRAG_NEGATIV` bleibt ein Abbruch.** Eine Überzahlung der
  Schlussrechnung ist kein Storno; geändert wird nur der Wortlaut, der heute
  auf ein Dokument verweist, das es nicht gab.
- **Abschläge in einem Storno sind schon verboten**, über die bestehende Regel
  `ABSCHLAEGE_FALSCHE_ART` ("Abschläge nur in einer Schlussrechnung").
- **Die Einmaligkeit der Nummer gilt auch hier**: Ein Storno bekommt eine eigene
  Rechnungsnummer aus derselben Reihe, nicht die der Ursprungsrechnung.

Umgesetzt in #63.

## Nachtrag vom 29.09.2026: Der Restbetrag null steht nicht in ADR-0002

„Derselbe Zug wie beim Restbetrag null (ADR-0002)“ unter „Konsequenzen“
verweist ins Leere: ADR-0002 sagt nur, wo die Abschläge abgesetzt werden.
Begründet ist der Zug im Commit f834085 („Die Schlussrechnung zeigt, was schon
vereinnahmt ist“): Ein nacktes „0,00 €“ sähe aus wie ein Fehler, also steht ein
Satz statt einer Zahl, und ein Zahlungsziel daneben wäre eine zweite Aussage
auf demselben Blatt. Dass dann auch die Bankverbindung entfällt, kam mit #94.
