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

**Stornorechnung**:
Das Dokument, das eine bereits ausgestellte Rechnung vollständig aufhebt. Es
nennt die Ursprungsrechnung, wiederholt deren Positionen mit negativer Menge und
fordert nicht zur Zahlung auf.
_Vermeiden_: Gutschrift, Rechnungskorrektur, Stornobeleg, Negativrechnung

„Gutschrift" ist hier besonders heikel: Umsatzsteuerlich ist das eine Rechnung,
die der **Leistungsempfänger** ausstellt (§14 Abs. 2 Satz 2 UStG), also etwas
ganz anderes. „Rechnungskorrektur" wiederum ist das Dokument, das einzelne
Angaben ändert, statt alles aufzuheben – dieses Projekt kennt es nicht.

**Ursprungsrechnung**:
Die Rechnung, auf die sich eine Stornorechnung bezieht. Genannt wird sie mit
Nummer und Datum (EN 16931 BG-3, BT-25 und BT-26).
_Vermeiden_: Originalrechnung, vorausgegangene Rechnung, Ausgangsrechnung

**Stammdaten**:
Die Angaben des Rechnungsstellers, die auf jeder Rechnung gleich sind.
_Vermeiden_: Konfiguration, Absenderdaten, Profil

## Kennungen

**Rechnungsnummer**:
Die Kennung, unter der eine Rechnung geführt wird (§14 Abs. 4 Satz 1 Nr. 4 UStG,
BT-1). Sie muss einmalig vergeben sein, aber nicht lückenlos aufeinander folgen;
Ziffern und Buchstaben sind beide erlaubt.
_Vermeiden_: Belegnummer, Rechnungs-ID, Nummer (für sich allein)

**Nummernkreis**:
Die Reihe, aus der Rechnungsnummern vergeben werden. Wie viele es gibt, bestimmt
der Rechnungssteller – etwa je Jahr oder je Geschäftszweig (UStAE 14.5 Abs. 11);
verlangt ist nur, dass jede Rechnung leicht und eindeutig ihrem Kreis zuzuordnen
ist.
_Vermeiden_: Nummernbereich, Zählerkreis, Sequenz

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

## Prüfung

**Befund**:
Was die Prüfung an einer Rechnung feststellt – bezogen auf die ganze Rechnung,
eine Position oder einen Abschlag. Jeder Befund ist entweder ein Abbruch oder
ein Hinweis; alle Befunde einer Rechnung erscheinen auf einmal.
_Vermeiden_: Fehler, Validierungsfehler, Meldung (das ist der Text eines Befunds)

**Abbruch**:
Ein Befund, mit dem aus der Rechnung kein Dokument entsteht. Die Rechnung
widerspricht dem Recht oder sich selbst.
_Vermeiden_: Fehler, Verstoß

**Hinweis**:
Ein Befund, der das Dokument nicht verhindert: Die Angabe ist zulässig, aber
selten gemeint, oder in die eigene Akte gehört noch etwas.
_Vermeiden_: Warnung

**Urteil**:
Was die Prüfung über eine Rechnung als Ganzes entscheidet: angenommen oder
abgelehnt. Es folgt allein aus den Befunden.
_Vermeiden_: Prüfergebnis, Status, Validierung

**Angenommen**:
Das Urteil über eine Rechnung ohne Abbruch. Nur aus ihr entsteht ein Dokument;
Hinweise kann sie tragen.
_Vermeiden_: gültig (das sagt veraPDF über das PDF), korrekt, freigegeben

**Abgelehnt**:
Das Urteil über eine Rechnung mit mindestens einem Abbruch – oder über das
Zusammentragen der Abschläge, wenn eine Abschlagsrechnung abgelehnt ist oder sie
nicht zusammenpassen. Es nennt alle Befunde auf einmal.
_Vermeiden_: ungültig, fehlerhaft
