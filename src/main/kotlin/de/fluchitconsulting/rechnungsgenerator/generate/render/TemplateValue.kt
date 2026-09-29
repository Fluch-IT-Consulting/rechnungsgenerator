package de.fluchitconsulting.rechnungsgenerator.generate.render

/**
 * Die Platzhalter der Vorlage, `<<FIRMA>>` und so weiter; das Gegenstück zu [TemplateMacro] (ADR-0017). Eine
 * einzelne Angabe darf leer sein, Ort und Beschriftung entscheidet die Vorlage mit `\ifblank`.
 *
 * [required]: Keine Vorlage darf den Platzhalter weglassen, weil sein Inhalt an Rechnungsart oder Rechnung
 * hängt und sonst auf Rechnungen fehlte, die kein Beispiel zeigt. Die Stammdaten darf ein Design übergehen;
 * ob die nötigen dastehen, prüft das Pflichtangaben-Netz am fertigen PDF.
 */
enum class TemplateValue(
    val required: Boolean,
) {
    // Stammdaten
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

    // Ein Schalter: leer oder der Dateiname des Logos neben der .tex.
    LOGO(false),

    // Leer oder sechs Hexadezimalziffern; die Vorgabe hat die Vorlage (ADR-0024).
    AKZENTFARBE(false),

    // Kopf
    DOKUMENTART(true),
    RECHNUNGSNUMMER(true),
    RECHNUNGSDATUM(true),

    // Der Code nach ISO 4217, aus dem die Sprachdatei das Zeichen in \betrag setzt (ADR-0026).
    WAEHRUNG(true),

    // Ein Block: Die Landzeile hängt an isDomestic, die Reihenfolge einer Anschrift ist Postkonvention.
    EMPFAENGER(true),
    KUNDE_USTID(true),
    KUNDENNUMMER(true),
    AUFTRAGSNUMMER(true),

    // Die eine Beschriftung aus Kotlin: „Voraussichtlicher Leistungszeitraum“ ist eine Rechtsfolge (UStAE 14.8).
    ZEITRAUM_BESCHRIFTUNG(true),
    LEISTUNGSZEITRAUM(true),
    VEREINNAHMT_AM(true),
    KUNDENREFERENZ(true),

    // Siehe ZAHLUNGSANGABE.
    ZAHLUNGSZIEL(false),

    // Freiwillig: Der Satz begleitet die Rechnung, statt eine ihrer Angaben zu sein (#209).
    EINLEITUNG(false),

    // Ein Schalter: nicht leer, wenn eine Position einen Rabatt hat.
    RABATTSPALTE(false),

    // Wiederholtes und fachliche Sätze: Makroaufrufe oder nichts.
    POSITIONEN(true),
    SUMMEN(true),
    ARTHINWEIS(true),
    STEUERHINWEISE(true),

    // Leer ohne Fremdwährung (ADR-0027).
    UMRECHNUNG(true),
    ABSETZUNG(true),

    // Ein Satz aus Kotlin: „sofort“, „1 Tag“ und „x Tage“ gehen über \ifblank hinaus (#211). Siehe ZAHLUNGSANGABE.
    ZAHLUNGSFRIST(false),
    SKONTO(true),
    BANKVERBINDUNG(true),

    // Pflicht, weil `bemerkung` aus der Rechnungsdatei ihn ersetzen kann.
    SCHLUSSSATZ(true),
    ;

    val placeholder: String get() = "<<$name>>"

    companion object {
        /**
         * Einen von beiden setzt jede Vorlage: `helsinki` das Datum im Kopf, `prag` die Frist als Satz. Eine
         * benannte Regel und keine allgemeine Liste von Gruppen, denn es gibt nur diesen einen Fall (#249).
         */
        val ZAHLUNGSANGABE: Set<TemplateValue> = setOf(ZAHLUNGSZIEL, ZAHLUNGSFRIST)
    }
}
