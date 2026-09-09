# Rechnungen

Das Vokabular der deutschen Rechnungsstellung, soweit dieses Projekt es abbildet.
Die Begriffe stammen aus dem Umsatzsteuerrecht (§14 UStG, UStAE 14.8) und aus
EN 16931; wo beide verschiedene Wörter für dieselbe Sache haben, gilt das
deutsche.

Diese Datei ist ein Glossar, keine Spezifikation. Wie etwas gerechnet oder
gesetzt wird, steht im Code; warum eine Entscheidung so gefallen ist, in
`docs/adr/`.

## Dokumente

**Rechnung**:
Das Dokument, das eine erbrachte Leistung abrechnet und zur Zahlung auffordert.
Ohne Zusatz immer die gewöhnliche Rechnung über eine bereits erbrachte Leistung.
_Vermeiden_: Faktura, Beleg, Ausgangsrechnung

**Abschlagsrechnung**:
Rechnung über einen Teil des Entgelts für eine noch **nicht** erbrachte
Leistung. Sie wird später durch eine Schlussrechnung abgelöst.
_Vermeiden_: Anzahlungsrechnung, Vorausrechnung, Akontorechnung, Teilrechnung

**Schlussrechnung**:
Rechnung über die vollständig erbrachte Leistung, in der die vereinnahmten
Abschläge abgesetzt werden.
_Vermeiden_: Endrechnung, Restrechnung, Abschlussrechnung

**Teilrechnung**:
Endgültige Rechnung über eine abgeschlossene Teilleistung. Sie wird nie
abgesetzt und braucht keine Schlussrechnung – der Unterschied zur
Abschlagsrechnung ist nicht die Höhe, sondern ob die Leistung erbracht ist.
_Vermeiden_: Zwischenrechnung, Abschlagsrechnung (für diesen Fall)

**Stammdaten**:
Die Angaben des Rechnungsstellers, die auf jeder Rechnung gleich sind.
_Vermeiden_: Konfiguration, Absenderdaten, Profil

## Beträge und Vorgänge

**Abschlag**:
Der Betrag, über den eine Abschlagsrechnung lautet.
_Vermeiden_: Anzahlung, Vorauszahlung, Akonto, Teilzahlung

**Vereinnahmung**:
Der Eingang eines Abschlags beim Rechnungssteller. Erst sie lässt die
Umsatzsteuer entstehen und erlaubt die Absetzung – ein berechneter, aber nicht
bezahlter Abschlag ist eine offene Forderung, kein Abschlag im Sinne der
Schlussrechnung.
_Vermeiden_: Zahlungseingang, Bezahlung

**Absetzung**:
Das Abziehen der vereinnahmten Abschläge in der Schlussrechnung, mit Nettobetrag
und Steuerbetrag getrennt und unterhalb der Steueraufschlüsselung.
_Vermeiden_: Verrechnung, Abzug, Gutschrift, Anrechnung

**Restbetrag**:
Was nach der Absetzung vom Bruttobetrag der Schlussrechnung noch zu zahlen ist.
Null ist zulässig, negativ nicht.
_Vermeiden_: Zahlbetrag, offener Betrag, Saldo, Differenz

**Steueraufschlüsselung**:
Die Aufstellung der Bemessungsgrundlagen und Steuerbeträge, gruppiert nach
Steuerkategorie und Steuersatz.
_Vermeiden_: Steuertabelle, USt-Übersicht

**Teilleistung**:
Ein wirtschaftlich abgrenzbarer, gesondert abgenommener Teil einer Leistung.
Nur eine solche rechtfertigt eine Teilrechnung.
_Vermeiden_: Meilenstein, Arbeitspaket, Bauabschnitt

## Zeiten

**Leistungszeitraum**:
Der Zeitraum, in dem die Leistung tatsächlich erbracht wurde. Gibt es nur, wenn
sie erbracht ist.
_Vermeiden_: Abrechnungszeitraum, Leistungsdatum

**Voraussichtlicher Leistungszeitraum**:
Der erwartete Zeitraum auf einer Abschlagsrechnung. Eine Prognose, keine
Tatsache; der Kalendermonat genügt.
_Vermeiden_: geplanter Zeitraum, Lieferdatum

## Orte

**Inland**:
Das Land des Rechnungsstellers. Eine Rechnung ist eine Inlandsrechnung, wenn der
Kunde im selben Land sitzt – nicht, wenn dieses Land Deutschland ist. Der
Unterschied wird erst sichtbar, wenn der Rechnungssteller umzieht, und bis dahin
sind beide Lesarten ununterscheidbar; genau daran ist die Anschrift im PDF schon
einmal falsch geworden.
_Vermeiden_: Deutschland (als Synonym), Heimatland

**Ausland**:
Jedes andere Land als das des Rechnungsstellers. Ob dort Umsatzsteuer anfällt und
ob die USt-IdNr. überhaupt geprüft werden kann, hängt zusätzlich daran, ob es ein
EU-Mitgliedstaat ist – Ausland und EU sind zwei Fragen, nicht eine.
_Vermeiden_: nicht-Deutschland, Drittland (das ist enger)

**Drittland**:
Ein Land außerhalb der EU. Für eine USt-IdNr. von dort gibt es weder eine
VIES-Abfrage noch eine Bestätigung durch das BZSt; die Unternehmereigenschaft
muss anders nachgewiesen werden, etwa durch eine Unternehmerbescheinigung.
_Vermeiden_: Nicht-EU-Land, Ausland (für diesen Fall)
