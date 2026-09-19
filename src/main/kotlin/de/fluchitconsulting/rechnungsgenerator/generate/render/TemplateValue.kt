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
 * lässt sich als Kennzeichen nicht ausdrücken. Das prüft das Netz aus #187 am
 * fertigen PDF.
 *
 * Wo es für dieselbe Angabe zwei Wege gibt, verlangt der Bau einen davon:
 * [requiredOneOf].
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
    // eines von zweien mit ZAHLUNGSFRIST (requiredOneOf).
    ZAHLUNGSZIEL(false),

    // Der Satz über den Positionen, aus der Rechnungsdatei (einleitung). Eine
    // einzelne Angabe, auch wenn sie ein Satz ist: Er kommt aus den Daten und
    // nicht aus Kotlin, und ob er dasteht, heißt nur leer oder nicht.
    EINLEITUNG(true),

    // Wiederholtes und fachliche Sätze: Makroaufrufe oder nichts.
    POSITIONEN(true),
    SUMMEN(true),
    ARTHINWEIS(true),
    STEUERHINWEISE(true),
    ABSETZUNG(true),

    // Das Zahlungsziel als Satz, für raster statt der Kopfzeile (#211). Ein
    // ganzer Satz aus Kotlin, weil "sofort", "1 Tag" und "x Tage" eine
    // Fallunterscheidung über \ifblank hinaus sind.
    ZAHLUNGSFRIST(false),
    SKONTO(true),
    BANKVERBINDUNG(true),

    // Kann aus der Rechnungsdatei kommen (bemerkung).
    SCHLUSSSATZ(true),
    ;

    val placeholder: String get() = "<<$name>>"

    companion object {
        /**
         * Angaben, für die es zwei Platzhalter gibt: Jede Vorlage setzt aus
         * jeder Gruppe mindestens einen. Das Zahlungsziel nennt `balken` als
         * Datum im Kopf, `raster` als Frist unter den Summen (#211) – ein
         * Design, das keines von beiden setzt, ließe es weg.
         *
         * Eine Gruppe statt zweier optionaler Platzhalter: Sonst fiele das erst
         * im Netz aus #187 auf, im Lauf mit den echten Werkzeugen, und nicht
         * schon beim Bau der Vorlage.
         */
        val requiredOneOf: List<Set<TemplateValue>> = listOf(setOf(ZAHLUNGSZIEL, ZAHLUNGSFRIST))
    }
}
