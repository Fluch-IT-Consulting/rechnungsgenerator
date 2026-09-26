package de.fluchitconsulting.rechnungsgenerator.generate.render

/**
 * Die Platzhalter der Vorlage, `<<FIRMA>>` und so weiter – das Gegenstück zu
 * [TemplateMacro] (#188). Der Name des Eintrags ist der des Platzhalters.
 *
 * Die Werte-Map des Renderers hat dieses Enum als Schlüssel. Ein vertippter
 * Schlüssel übersetzt dann nicht mehr, statt erst beim Setzen aufzufallen.
 *
 * Eine einzelne Angabe kommt als eigener Platzhalter und darf leer sein. Ob sie
 * dasteht, entscheidet der Renderer über leer oder nicht leer; Ort,
 * Reihenfolge und Beschriftung entscheidet die Vorlage, mit `\ifblank`.
 *
 * [required] heißt: Keine Vorlage darf diesen Platzhalter weglassen, das prüft
 * der Bau eines [Template]. Das sind die, deren Inhalt an Rechnungsart oder
 * Rechnung hängt – ließe ein Design sie weg, fehlte auf einem Teil der
 * Rechnungen eine Angabe, und kein Beispiel zeigte es. Für die
 * Stammdaten gibt es keine solche Pflicht: "Steuernummer **oder** USt-IdNr."
 * lässt sich als Kennzeichen nicht ausdrücken. Das prüft das
 * Pflichtangaben-Netz am fertigen PDF.
 *
 * An beiden Hälften fällt etwas heraus (#248). Ein **Schalter** hat gar keinen
 * Inhalt: Er sagt der Vorlage nicht, *was* dasteht, sondern *ob* sie ein
 * Mittel einsetzt – [LOGO] die Einbindung, [RABATTSPALTE] die Spalte. Eine
 * Vorlage, die das Mittel nicht kennt, braucht ihn nicht; dass die Spalte
 * genau dann steht, wenn eine Position einen Rabatt hat, hält ohnehin das
 * Pflichtangaben-Netz an `teilrechnung`. Und die [EINLEITUNG] darf ein Design
 * weglassen (#209): Sie begleitet die Rechnung, statt eine ihrer Angaben zu
 * sein.
 *
 * Freiwillig heißt aber nicht frei: [KUNDENNUMMER] (#190) und
 * [AUFTRAGSNUMMER] (#191) bleiben Pflicht, und mit ihnen [KUNDENREFERENZ]
 * und [SCHLUSSSATZ]. Das sind Angaben und
 * keine Begleitsätze – jemand hat sie für diese Rechnung hingeschrieben –,
 * und Kundennummer, Auftragsnummer und `bemerkung` nennt kein Beispiel: Ein
 * Design, das sie weglässt, verschluckt sie, und nichts sieht es.
 *
 * Wo es für dieselbe Angabe zwei Wege gibt, verlangt der Bau einen davon:
 * [ZAHLUNGSANGABE].
 */
enum class TemplateValue(
    val required: Boolean,
) {
    // Die Stammdaten, einzeln, maskiert und formatiert.
    FIRMA(false),
    INHABER(false),
    STRASSE(false),
    PLZ(false),
    ORT(false),
    EMAIL(false),
    TELEFON(false),
    WEBSEITE(false),
    USTID(false),
    STEUERNUMMER(false),
    REGISTERGERICHT(false),
    REGISTERNUMMER(false),
    GESCHAEFTSFUEHRUNG(false),
    KONTOINHABER(false),
    INSTITUT(false),
    IBAN(false),
    BIC(false),

    // Ein Schalter und kein Inhalt (#198): leer oder der Name, unter dem das
    // Logo neben der .tex liegt. Die Vorlage bindet es selbst ein.
    LOGO(false),

    // Die Akzentfarbe aus den Stammdaten (#371), sechs Hexadezimalziffern oder
    // leer. Die Vorgabe ohne Eintrag hat die Vorlage, nicht Kotlin: Farbe ist
    // Satzbild (ADR-0017), und ein Design ohne Akzentfarbe übergeht den Wert.
    AKZENTFARBE(false),

    // Kopfangaben der Rechnung.
    DOKUMENTART(true),
    RECHNUNGSNUMMER(true),
    RECHNUNGSDATUM(true),

    // Ein Block und keine einzelnen Zeilen: Die Landzeile hängt an
    // CalculatedInvoice.isDomestic, und die Reihenfolge einer Anschrift ist
    // Postkonvention, kein Satzbild.
    EMPFAENGER(true),
    KUNDE_USTID(true),
    KUNDENNUMMER(true),
    AUFTRAGSNUMMER(true),

    // Die eine Beschriftung, die vom Renderer kommt: "Voraussichtlicher
    // Leistungszeitraum" folgt aus servicePeriodIsForecast (UStAE 14.8) und ist
    // kein Satzbild. Ob es "Rechnungsnr." oder "Rechnungsnummer" heißt,
    // entscheidet dagegen das Design.
    ZEITRAUM_BESCHRIFTUNG(true),
    LEISTUNGSZEITRAUM(true),
    VEREINNAHMT_AM(true),
    KUNDENREFERENZ(true),

    // Das Zahlungsziel als Datum, für eine Kopfzeile. Nicht Pflicht, sondern
    // eines von zweien mit ZAHLUNGSFRIST (ZAHLUNGSANGABE).
    ZAHLUNGSZIEL(false),

    // Der Satz über den Positionen, aus der Rechnungsdatei (einleitung). Eine
    // einzelne Angabe, auch wenn sie ein Satz ist: Er kommt aus den Daten und
    // nicht aus Kotlin, und ob er dasteht, heißt nur leer oder nicht.
    //
    // Keine Pflicht (#209): Der Satz begleitet die Rechnung, statt eine ihrer
    // Angaben zu sein – was berechnet wird, steht in den Positionen. Ein
    // Design ohne Absatz über der Tabelle darf ihn weglassen, und kein Netz
    // hält das auf.
    EINLEITUNG(false),

    // Ein Schalter wie LOGO (#213): nicht leer, wenn eine Position einen Rabatt
    // hat. Dann setzt die Vorlage die Spalte, sonst nicht. Und keine Pflicht,
    // wie LOGO keine ist – ein Schalter ist kein Inhalt. Dass ein Design den
    // geminderten Betrag nicht ohne Grund zeigt, hält das Pflichtangaben-Netz:
    // `teilrechnung` hat einen Rabatt, und dort wird je Design geprüft, dass
    // die Spalte genau dann steht.
    RABATTSPALTE(false),

    // Wiederholtes und fachliche Sätze: Makroaufrufe oder nichts.
    POSITIONEN(true),
    SUMMEN(true),
    ARTHINWEIS(true),
    STEUERHINWEISE(true),
    ABSETZUNG(true),

    // Das Zahlungsziel als Satz, für prag statt der Kopfzeile (#211). Ein
    // ganzer Satz aus Kotlin, weil "sofort", "1 Tag" und "x Tage" eine
    // Fallunterscheidung über \ifblank hinaus sind.
    ZAHLUNGSFRIST(false),
    SKONTO(true),
    BANKVERBINDUNG(true),

    // Kann aus der Rechnungsdatei kommen (bemerkung) und ist deshalb Pflicht,
    // obwohl die Vorgabe aus den Stammdaten stammt: Wer eine Bemerkung für
    // diese eine Rechnung schreibt, soll sie nicht an ein Design verlieren.
    SCHLUSSSATZ(true),
    ;

    val placeholder: String get() = "<<$name>>"

    companion object {
        /**
         * Jede Vorlage sagt etwas über die Zahlung: `helsinki` nennt das
         * Zahlungsziel als Datum im Kopf ([ZAHLUNGSZIEL]), `prag` die Frist
         * als Satz unter den Summen ([ZAHLUNGSFRIST], #211). Pflicht ist
         * deshalb keiner der beiden, aber einer von beiden – eine Vorlage, die
         * keinen setzt, ließe die Zahlung ganz vom Blatt.
         *
         * Geprüft beim Bau der Vorlage statt am fertigen PDF: Sonst fiele es
         * erst im Pflichtangaben-Netz auf, im Lauf mit den echten Werkzeugen.
         *
         * Eine benannte Regel und keine allgemeine Liste von Gruppen (#249):
         * Es gibt genau diesen einen Fall. Ein zweiter soll nicht als
         * Listeneintrag hereinkommen, sondern hingeschrieben werden – und wer
         * ihn hinschreibt, steht vor der Frage aus #188, welches Wort ein
         * Design überhaupt braucht.
         */
        val ZAHLUNGSANGABE: Set<TemplateValue> = setOf(ZAHLUNGSZIEL, ZAHLUNGSFRIST)
    }
}
