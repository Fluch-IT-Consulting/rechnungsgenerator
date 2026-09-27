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
_Englisch_: Invoice – _Vermeiden_: Bill

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
_Englisch_: Partial invoice – _Vermeiden_: Interim invoice, Progress invoice
(beide klingen nach einem Abschlag auf noch nicht Erbrachtes), Instalment
invoice (klingt nach Ratenzahlung)

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

**E-Rechnung**:
Eine Rechnung, die neben dem Blatt als XML nach ZUGFeRD vorliegt, beides in
einem PDF/A-3; maßgeblich ist das XML. Keine Rechnungsart, sondern eine Form –
jede Art kann E-Rechnung sein. Gültig ist sie, wenn das PDF/A die
PDF/A-Prüfung besteht und das XML die XML-Prüfung.
_Vermeiden_: ZUGFeRD-Rechnung, Hybridrechnung, PDF-Rechnung, XRechnung (das
ist XML ohne Blatt)

Das Gesetz sagt *elektronische Rechnung* (§14 Abs. 1 UStG) und meint mehr:
jede Rechnung in einem strukturierten Format, auch reines XML wie die
XRechnung. Dieses Projekt kennt davon nur die Form mit Blatt. Enger als ZUGFeRD
ist das Gesetz bei zwei Profilen: MINIMUM und BASIC-WL tragen nicht alle
Pflichtangaben und sind keine E-Rechnung (BMF-Schreiben vom 15.10.2024). Und
ein PDF ohne XML ist keine, auch per E-Mail nicht – umsatzsteuerlich ist es
eine *sonstige Rechnung*.

**Stammdaten**:
Die Angaben des Rechnungsstellers, die auf jeder Rechnung gleich sind.
_Vermeiden_: Konfiguration, Absenderdaten, Profil

## Ablage

**Wurzel**:
Das Verzeichnis, das den Ordner `.rechnungsgenerator/` mit den Stammdaten
enthält. Alles darunter wird mit diesen Stammdaten gesetzt; ein zweiter
Rechnungssteller ist eine zweite Wurzel (ADR-0015). Im Code heißt sie `root`.
_Vermeiden_: Basisverzeichnis, Projektverzeichnis, Arbeitsverzeichnis

Mehr als die Wurzel kennt der Generator von der Ablage nicht: Er liest eine
Rechnungsdatei und was die Stammdaten nennen, nie das Verzeichnis. Welche
Nummern vergeben und welche Abschläge offen sind, sieht er deshalb nicht
(ADR-0001, ADR-0005) – das tun die Skripte des `rechnung`-Skills. Im Ordner der
Wurzel liegt darum nur Handgeschriebenes, kein Zähler und kein Cache.

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

**Rabatt**:
Eine Minderung des Positionsbetrags, sofort und ohne Bedingung – vereinbart als
Satz oder als Betrag. Der Einzelpreis bleibt, wie er vereinbart ist; gemindert
wird, was die Position kostet. Anders als das Skonto steht auf der Rechnung schon der
geminderte Betrag.
_Vermeiden_: Nachlass, Preisnachlass (für einen geminderten Einzelpreis),
Abzug

**Skonto**:
Eine im Voraus vereinbarte Minderung des Entgelts für den Fall, dass der Kunde
innerhalb einer Frist zahlt. Auf der Rechnung steht die Bedingung, nicht der
geminderte Betrag; ob der Kunde sie nutzt, zeigt erst seine Zahlung. Eine
Rechnung kann mehrere Skontostufen nennen.
_Vermeiden_: Rabatt (mindert sofort und ohne Bedingung), Nachlass, Abzug

**Skontostufe**:
Eine Bedingung des Skontos: ein Skontosatz und die Skontofrist, bis zu der er
gilt.
_Vermeiden_: Skontostaffel (für eine einzelne Stufe), Skontozeile

**Skontosatz**:
Der Prozentsatz, um den sich das Entgelt bei Zahlung innerhalb der Skontofrist
mindert.
_Vermeiden_: Skontoprozent, Skontorate

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
_Englisch_: Service period – _Vermeiden_: Period of supply, Tax point (beides
im britischen VAT eigene Begriffe)

**Voraussichtlicher Leistungszeitraum**:
Der erwartete Zeitraum auf einer Abschlagsrechnung. Eine Prognose, keine
Tatsache; der Kalendermonat genügt.
_Vermeiden_: geplanter Zeitraum, Lieferdatum

**Zahlungsziel**:
Die Frist ab Rechnungsdatum, bis zu der ohne Abzug zu zahlen ist. Ein Dokument,
das nicht zur Zahlung auffordert, hat keines.
_Vermeiden_: Zahlungsfrist, Zahlungsbedingung (das ist Zahlungsziel und Skonto
zusammen)

**Skontofrist**:
Die Frist ab Rechnungsdatum, innerhalb derer eine Zahlung den Skontosatz einer
Skontostufe abziehen darf. Sie endet vor dem Zahlungsziel.
_Vermeiden_: Skontotage, Skontoziel

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

## Texte

**Sprache**:
Die Sprache, in der eine Rechnung steht – genau eine je Rechnung, mit ihrer
Variante: britisches und nicht bloß Englisch. Sie gilt für das Blatt und die
Freitexte der E-Rechnung, nicht für das, was sich an den Rechnungssteller
richtet.
_Vermeiden_: Locale, Übersetzung

**Einleitung**:
Der Satz über den Positionen, der sagt, worum es geht: welcher Auftrag, welcher
Anlass, welcher Ort. Er steht nur in der Rechnungsdatei und nie in den
Stammdaten, und er ist keine Pflichtangabe.
_Vermeiden_: Betreff, Anschreiben, Vorspann

## Prüfung

**Pflichtangaben**:
Die Angaben, die §14 Abs. 4 UStG auf jeder Rechnung verlangt – Name und
Anschrift beider Seiten, Steuernummer oder USt-IdNr. des Ausstellers,
Rechnungsdatum, Rechnungsnummer, Menge und Art der Leistung, Leistungszeitraum,
Entgelt je Steuersatz und Steuerbetrag. Fehlt eine, ist das Dokument keine
ordnungsgemäße Rechnung. Ob sie auf dem Blatt stehen, zeigt sich erst am
fertigen PDF und nicht am Modell.
_Vermeiden_: Pflichtfelder, Mussfelder, Mindestangaben

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
_Vermeiden_: gültig (das ist das Urteil über ein Dokument, nicht über die
Rechnung), korrekt, freigegeben

**PDF/A-Prüfung**:
Die Frage, ob ein Dokument dem Archivformat PDF/A genügt – eine gesetzte
Rechnung dem PDF/A-1b, eine E-Rechnung dem PDF/A-3. Ihr Ergebnis ist *gültig*
oder nicht, und sie betrifft das Dokument und nicht die Rechnung. Beim
Erzeugen kommt sie nach dem Satz, während die *Prüfung* der Rechnung ihr
vorausgeht und über *angenommen* oder *abgelehnt* entscheidet. Ohne gültiges
PDF/A entsteht keine Datei am Ziel.
_Vermeiden_: Prüfung (ohne Zusatz – das ist die der Rechnung), Validierung,
Konformitätsprüfung

**XML-Prüfung**:
Die Frage, ob das XML einer E-Rechnung dem Schema und den Geschäftsregeln
seines Profils genügt. Wie die PDF/A-Prüfung betrifft sie das Dokument, und ihr
Ergebnis ist *gültig* oder nicht. Ihre Regeln sind die der Norm (EN 16931 und
ZUGFeRD), nicht die der Prüfung, und was sie bemängelt, sind keine Befunde.
Beim Erzeugen geht sie dem Satz voraus; scheitert sie dort, liegt der Fehler
beim Generator, der das XML geschrieben hat, und nicht an der Rechnung.
_Vermeiden_: Prüfung (ohne Zusatz), Validierung, ZUGFeRD-Prüfung

**Abgelehnt**:
Das Urteil über eine Rechnung mit mindestens einem Abbruch – oder über das
Zusammentragen der Abschläge, wenn eine Abschlagsrechnung abgelehnt ist oder sie
nicht zusammenpassen. Es nennt alle Befunde auf einmal.
_Vermeiden_: ungültig (das Urteil über ein Dokument), fehlerhaft
