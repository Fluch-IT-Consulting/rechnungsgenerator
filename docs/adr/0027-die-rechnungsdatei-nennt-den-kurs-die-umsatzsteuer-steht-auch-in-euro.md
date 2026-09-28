# Die Rechnungsdatei nennt den Kurs, die Umsatzsteuer steht auch in Euro

Eine Rechnung in Fremdwährung darf Umsatzsteuer ausweisen, wenn die
Rechnungsdatei den Umrechnungskurs nennt:

```yaml
waehrung: USD
umrechnung:
  kurs: 1.1685      # 1 € = 1,1685 $
  monat: 2026-07
```

- **Der Kurs** ist ein Wert, `ExchangeRate` in `core.values`: Einheiten der
  Fremdwährung je Euro, positiv, exakt und ohne Rundung. **Der Monat** ist
  ein `YearMonth`. Beides zusammen ist `CurrencyConversion` in `model`.
- **Die Steuer in Euro** rechnet `CalculatedInvoice.vatTotalInEuro`: die
  Summe der Umsatzsteuer geteilt durch den Kurs, mit genau einer Rundung auf
  Cent, kaufmännisch.
- **Die Regeln** stehen in `CurrencyRules`. `WAEHRUNG_MIT_UMSATZSTEUER` bricht
  ab, wenn Kategorie S in Fremdwährung ohne `umrechnung` steht. Auf einer
  Abschlags- oder Schlussrechnung geht es gar nicht
  (`WAEHRUNG_MIT_UMSATZSTEUER_AUF_DIESER_ART_NICHT_MOEGLICH`), dort als
  einziger Befund. Eine Umrechnung, die nichts umrechnet, bricht ebenfalls
  ab: `UMRECHNUNG_BEI_EURO` und `UMRECHNUNG_OHNE_UMSATZSTEUER`.
- **Das Blatt** nennt unter den Summen einen Satz aus Kotlin, über den
  Platzhalter `<<UMRECHNUNG>>`: „Umgerechnet in Euro beträgt die
  Umsatzsteuer 1.463,41 € (1 € = 1,1685 $, Monatsdurchschnittskurs des BMF
  für Juli 2026).“ Den Euro-Betrag setzt `\eurobetrag`, das jede Sprachdatei
  als `\betrag` mit `\waehrung` auf EUR definiert.
- **Die E-Rechnung** trägt BT-6 (`TaxCurrencyCode`, EUR) vor BT-5 und unter
  der Steuersumme eine zweite in Euro (BT-111), wie BR-53 es verlangt.

## Warum

**Der Kurs steht in der Rechnungsdatei.** Nachgeschlagen wird nichts
(ADR-0001): Dieselbe Datei ergäbe sonst nicht mehr dasselbe PDF, und ohne
Netz entstünde keine Rechnung. Der Monat steht daneben, obwohl er nichts
rechnet – er macht den Kurs auf dem Blatt nachprüfbar.

**Notiert wie beim BMF.** BMF und EZB veröffentlichen Einheiten der
Fremdwährung je Euro. So wird der Kurs abgeschrieben und nicht vorher
umgerechnet, und ein Quotient mit abgeschnittenen Stellen kommt nie in die
Datei.

**Welcher Monat, prüft der Generator nicht.** Maßgeblich ist nach §16 Abs. 6
UStG der Monat der Leistung, bei einer Anzahlung und für jeden, der nach
vereinnahmten Entgelten versteuert (§20), der Monat der Vereinnahmung. Das
hängt an der Versteuerung des Rechnungsstellers, die der Generator nicht
kennt – wie die Einmaligkeit der Rechnungsnummer (ADR-0005) liegt es bei dem,
der die Datei schreibt.

**Nicht auf Abschlags- und Schlussrechnung.** Auf der Abschlagsrechnung zählt
der Monat der Vereinnahmung, und der steht beim Erzeugen oft noch nicht fest.
Die Schlussrechnung setzte Abschläge ab, die zu verschiedenen Kursen
versteuert wurden; die Absetzung bräuchte dann einen Kurs je Abschlag. Das
Storno dagegen hebt eine Rechnung ganz auf und übernimmt ihren Kurs.

**Umgerechnet wird die Summe.** BT-111 ist eine einzige Zahl. Umgerechnete
Gruppen könnten zusammen um einen Cent von ihr abweichen, und dann stünden auf
dem Blatt und im XML zwei Euro-Beträge für dieselbe Steuer.

**Kurs und Monat stehen auf dem Blatt.** Ohne sie ließe sich der Euro-Betrag
nicht nachrechnen, und Mustang rechnet ihn auch nicht nach: Er kennt den Kurs
nicht und nimmt jeden Betrag an.

**Ein eigenes Makro für den Euro-Betrag.** Die Währung als Argument von
`\betrag` hatte ADR-0026 verworfen. `\eurobetrag` behält Zeichen und Stellung
der Sprache, weil es `\betrag` selbst aufruft.

## Verworfen

- **Den Kurs nachschlagen**, beim BMF oder der EZB. Siehe oben, ADR-0001.
- **Eine Kursart** neben Kurs und Monat, `monatsdurchschnitt`, `vormonat`
  oder `tageskurs`. Die beiden anderen muss das Finanzamt gestatten (§16
  Abs. 6 Satz 3 UStG, UStAE 16.4 Abs. 2), und gerechnet wird mit jedem gleich.
  Ein Feld mehr, das nichts ändert, solange niemand danach fragt.
- **Soll oder Ist in den Stammdaten**, damit eine Regel den Monat prüfen
  kann. Bei Ist stünde der Monat beim Erzeugen ohnehin nicht fest, und ein
  neuer Schlüssel in den Stammdaten landete auf jeder Rechnung.
- **Umsatzsteuer in Fremdwährung auf jeder Art**, samt Euro-Beträgen je
  Abschlag im Block `abschlaege`. Das berührte ADR-0001, ADR-0002 und
  `rechnungsgenerator abschlaege`, ohne dass jemand danach gefragt hätte.
- **Je Gruppe umrechnen.** Siehe oben.
- **Nur den Euro-Betrag nennen**, ohne Kurs und Monat. Knapper, aber nicht
  nachprüfbar.
- **Den Kurs im XML**, als `TaxApplicableTradeCurrencyExchange`. Das gibt es
  nur in EXTENDED, und in welcher Richtung `ConversionRate` gilt, belegt keine
  Quelle, die wir erreichen konnten: Das Schematron prüft nur die Codes. Ein
  falsch herum gelesener Kurs wäre schlimmer als keiner.

## Konsequenzen

- **Eine Rechnung ohne `umrechnung` sieht aus wie zuvor.** Der Platzhalter
  bleibt leer, und die Vergleichsdateien haben sich nicht geändert.
- **Das Storno übernimmt `umrechnung` von Hand.** Prüfen kann das nur, wer die
  Ablage liest; `offene-abschlaege.py` tut es nicht.
- **Eine neue Sprache** braucht in jeder Sprachdatei `\eurobetrag` und in
  `ItemBlocks.conversionNote` einen Satz.
- **Das Pflichtangaben-Netz** verlangt an jeder Rechnung mit Umrechnung
  Euro-Betrag, Kurs und Monat und setzt `umsatzsteuer-in-dollar.yaml` je
  Design in allen drei Sprachen, mit Zeichen und Stellung wörtlich.
- **Offen bleibt**, ob eine deutsche Vorschrift den Euro-Betrag auf der
  Rechnung selbst verlangt. Art. 230 MwStSystRL richtet sich an die
  Mitgliedstaaten, und Abschnitt 14.5 UStAE nennt keine Währung. Auf dem Blatt
  steht er trotzdem: falsch ist er nie.

Umgesetzt in #418.
