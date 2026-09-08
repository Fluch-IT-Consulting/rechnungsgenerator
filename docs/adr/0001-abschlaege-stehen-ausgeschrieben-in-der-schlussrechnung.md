# Abschläge stehen ausgeschrieben in der Schlussrechnung

Die Schlussrechnung muss die vereinnahmten Abschläge mit Nettobetrag und
Steuerbetrag absetzen. Diese Werte werden **in der Schlussrechnungs-Datei selbst
eingetragen** – Nummer, Datum, Zahldatum, Steuerkategorie, Satz, Netto, Steuer –
und nicht aus den Dateien der Abschlagsrechnungen nachgeschlagen. Der Generator
bleibt damit eine reine Abbildung einer YAML-Datei auf ein PDF, deren Ergebnis
nicht davon abhängt, was sonst noch im Verzeichnis liegt.

## Verworfene Alternative

Nur die Rechnungsnummern zu referenzieren und die früheren Dateien zu lesen,
wäre redundanzfrei, macht das Ergebnis aber vom Verzeichnisinhalt und vom
Codestand des Erzeugungszeitpunkts abhängig. Zwischen Abschlag und
Schlussrechnung liegen Monate: Dateien wandern in die Jahresordner, alte
Rechnungen existieren irgendwann nur noch als PDF, und eine geänderte
Rundungs- oder Steuerregel würde die abgesetzten Beträge still verschieben.
Abgesetzt gehört aber, was damals berechnet wurde, nicht was der heutige Code
daraus machen würde. Außerdem wäre es die erste Stelle, an der die Steuerlogik
das Dateisystem anfasst.

## Konsequenzen

Die Beträge werden abgeschrieben, und Abschreiben erzeugt Zahlendreher. Zwei
Netze dagegen:

- Der Calculator rechnet den eingetragenen Steuerbetrag aus Netto und Satz nach
  und bricht bei Abweichung ab – dieselbe Bauart wie die IBAN-Prüfsumme.
- Das Übertragen macht nicht der Mensch, sondern der `rechnung`-Skill: Er kennt
  das Ablageverzeichnis, holt die Werte aus den Quelldateien und meldet
  Abweichungen. Die Prüfung sitzt dort, wo das Verzeichnis bekannt ist; die
  Rechnung bleibt für sich allein lesbar.

Nebeneffekt: Die eingetragene Struktur entspricht 1:1 dem
`SpecifiedAdvancePayment` aus ZUGFeRD EXTENDED (Bruttobetrag, Zahldatum,
Steueranteile, Verweis auf die Vorrechnung). Ein späterer Export muss nichts
rekonstruieren.
