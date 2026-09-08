# Abschläge werden abgesetzt, nicht als Position verrechnet

Die Schlussrechnung rechnet die **gesamte** Leistung ab und zieht die
vereinnahmten Abschläge erst **unterhalb** der Steueraufschlüsselung ab, mit
Netto und Steuerbetrag getrennt. Ein Abschlag ist nie eine (negative)
Rechnungsposition und nie ein Nachlass auf Dokumentenebene.

Der Grund ist nicht Geschmack: EN 16931 koppelt die Bemessungsgrundlage (BT-116)
über BR-CO-10 und BR-CO-13 zwingend an die Summe der Positionsbeträge. Eine
negative Position würde also die ausgewiesene Umsatzsteuer der Schlussrechnung
mindern – §14 Abs. 5 Satz 2 UStG verlangt aber, den vollen Umsatz zu versteuern
und die bereits versteuerten Teilentgelte danach abzusetzen. Wer das verwechselt,
weist zu wenig Steuer aus. Syntaktisch verboten ist die negative Position
übrigens nicht (die KoSIT hat eine Vorzeichenregel bewusst abgelehnt) – sie ist
nur fachlich falsch, und deshalb steht es hier.

## Konsequenz für den späteren ZUGFeRD-Export

Das **Kernmodell** der EN 16931 kann die deutsche Aufteilung nicht abbilden: Es
kennt für vorausgezahlte Beträge nur BT-113, einen einzigen Bruttobetrag ohne
Steueraufteilung. KoSIT und FeRD sagen das offen ("im Kernmodell der
EN 16931-1:2019 noch nicht möglich", Empfehlungen zu Pflichtangaben, 25.07.2025)
und verweisen auf Anlagen. Strukturiert geht es nur in **ZUGFeRD EXTENDED** über
`SpecifiedAdvancePayment`. Wenn der Export kommt, ist EXTENDED das Zielprofil,
nicht das EN16931-Profil und nicht XRechnung.

Für XRechnung wäre zusätzlich zu bedenken: Der Dokumenttyp 386
(Anzahlungsrechnung) steht nicht in deren Codevorrat (BR-DE-17, allerdings nur
`warning`), und mehrere Verweise auf Vorrechnungen (BG-3) gehen dort in CII gar
nicht, weil das Syntax-Binding sie auf 0..1 begrenzt. Für das PDF ist beides
ohne Belang.
