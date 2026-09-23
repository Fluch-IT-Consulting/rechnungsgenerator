package de.fluchitconsulting.rechnungsgenerator.core.rules

/**
 * Was bei der Prüfung einer Rechnung auffällt – ein Befund.
 *
 * Gesammelt und nicht beim ersten Verstoß geworfen: Sonst gehörten die
 * Reihenfolge der Regeln und die vollständige Menge der Hinweise zur
 * Schnittstelle der Prüfung, und wer eine Regel prüfen wollte, bräuchte eine
 * Rechnung, die alle anderen besteht und keinen fremden Hinweis auslöst. Das
 * Abbrechen bleibt dem Kommando.
 */
data class Finding(val rule: Rule, val subject: Subject, val message: String) {
    val severity: Severity get() = rule.severity

    /** Die Zeile, die ein Mensch zu sehen bekommt: Gegenstand, dann Meldung. */
    fun describe(): String = subject.describe()?.let { "$it: $message" } ?: message
}

/**
 * Eine Ablehnung, die ein Kommando nach außen gibt – mit allen Abbrüchen auf einmal.
 *
 * Beide Kommandos lehnen ab, `erzeuge` mit `Verdict.Rejected` und `abschlaege`
 * mit `CollectedPrepayments.Rejected`. Zu Text wird die Liste nur hier, und
 * übersetzt wird die Ausnahme nur in `execute()` (#119). Baute jedes Kommando
 * seine eigene Zeile und seinen eigenen `CliktError`, liefen zwei Fassungen
 * derselben Regel auseinander (#52).
 *
 * Die Meldung entsteht beim Bauen, und [aborts] bleibt für Tests als Liste
 * erreichbar, die nach Kennungen fragen statt nach Wortlaut.
 */
class RejectedException(val aborts: List<Finding>) : Exception(aborts.joinToString("\n") { it.describe() })

/**
 * Wie ernst ein Befund ist.
 *
 * Beides in einer Liste statt in zweien: Zwei Listen derselben Gestalt werfen
 * bei jedem neuen Fall die Frage auf, in welche er gehört.
 */
enum class Severity {
    /** Die Rechnung entsteht nicht. */
    ABORT,

    /** Die Rechnung entsteht; der Hinweis betrifft die eigene Akte. */
    HINT,
}

/**
 * Worauf sich ein Befund bezieht. Als eigenes Feld und nicht im Meldungstext:
 * Sonst stünde dieselbe Angabe zweimal da, und wer die Meldung weiterreicht
 * (etwa `abschlaege` mit mehreren Dateien), klebte noch etwas davor.
 */
sealed interface Subject {
    /** Der Vorspann der Zeile, oder `null` für die Rechnung als Ganzes. */
    fun describe(): String?

    /** Die Rechnung als Ganzes. */
    data object WholeInvoice : Subject {
        override fun describe(): String? = null
    }

    /** Eine Position, gezählt wie auf dem Papier: die erste ist Position 1. */
    data class Item(val index: Int, val name: String) : Subject {
        override fun describe(): String = "Position ${index + 1} ($name)"
    }

    /** Ein abgesetzter Abschlag, erkennbar an der Nummer seiner Rechnung. */
    data class Prepayment(val invoiceNumber: String) : Subject {
        override fun describe(): String = "Abschlag $invoiceNumber"
    }

    /**
     * Ein Befund aus einer anderen Rechnungsdatei – `abschlaege` liest mehrere
     * auf einmal, und "Position 1 (…)" allein sagt dann nicht, in welcher.
     *
     * Die Herkunft legt sich um den ursprünglichen Gegenstand, statt ihn zu
     * ersetzen: Beides zusammen ergibt "R26-002, Position 1 (Beratung)".
     */
    data class FromInvoice(val invoiceNumber: String, val inner: Subject) : Subject {
        override fun describe(): String = inner.describe()?.let { "$invoiceNumber, $it" } ?: invoiceNumber
    }
}

/**
 * Die Regeln, die dieses Programm prüft – je eine Kennung.
 *
 * Deutsch benannt wie die Meldungen: Jede Regel ist ein Satz über die deutsche
 * Rechnungsstellung, und die Hälfte der Wörter steht ohnehin im Glossar
 * (`abschlag`, `vereinnahmt`, `restbetrag`). Ein englisches Vokabular daneben
 * zwänge beim Lesen zum Übersetzen.
 *
 * Die Kennungen sind nicht nummeriert. Die zehn Nummern der Absetzung standen
 * nur in Kommentaren und Testnamen und liefen dort schon auseinander.
 *
 * Auch die Kennungen, die allein `abschlaege` vergibt, stehen hier und nicht
 * in `prepayments` (#314): Was das Programm ablehnt, steht so an einer Stelle,
 * und `abschlaege` meldet die Abbrüche der gelesenen Abschlagsrechnungen in
 * derselben Liste wie seine eigenen. Getrennt bräuchte [Finding] statt dieses
 * Enums ein offenes Interface.
 */
enum class Rule(val severity: Severity) {
    // Die Form der Rechnungsnummer (ADR-0005)
    RECHNUNGSNUMMER_FEHLT(Severity.ABORT),
    RECHNUNGSNUMMER_MIT_LEERRAUM(Severity.ABORT),
    RECHNUNGSNUMMER_OHNE_DATEINAMEN(Severity.ABORT),
    RECHNUNGSNUMMER_PASST_NICHT(Severity.ABORT),

    // Freitextangaben
    ANSPRECHPARTNER_LEER(Severity.ABORT),
    ANSPRECHPARTNER_MIT_LEERRAUM(Severity.ABORT),
    KUNDENNUMMER_LEER(Severity.ABORT),
    KUNDENNUMMER_MIT_LEERRAUM(Severity.ABORT),
    AUFTRAGSNUMMER_LEER(Severity.ABORT),
    AUFTRAGSNUMMER_MIT_LEERRAUM(Severity.ABORT),
    EINLEITUNG_LEER(Severity.ABORT),
    EINLEITUNG_MIT_LEERRAUM(Severity.ABORT),

    // Positionen und Steuerkategorie
    KEINE_POSITION(Severity.ABORT),
    EINZELPREIS_NEGATIV(Severity.ABORT),
    RABATT_AUSSERHALB_BEREICH(Severity.ABORT),
    RABATT_BETRAG_AUSSERHALB_BEREICH(Severity.ABORT),
    RABATT_DOPPELT(Severity.ABORT),
    SATZ_MUSS_NULL_SEIN(Severity.ABORT),
    REGELSATZ_MIT_NULL(Severity.ABORT),
    BEFREIUNGSGRUND_UNZULAESSIG(Severity.ABORT),
    BEFREIUNGSGRUND_FEHLT(Severity.ABORT),
    BEFREIUNGSGRUENDE_UNEINHEITLICH(Severity.ABORT),
    KUNDEN_UST_ID_FEHLT(Severity.ABORT),

    // Das Storno und seine Ursprungsrechnung (ADR-0006)
    STORNO_OHNE_BEZUG(Severity.ABORT),
    BEZUG_OHNE_STORNO(Severity.ABORT),
    STORNO_AUF_SICH_SELBST(Severity.ABORT),
    URSPRUNGSRECHNUNG_SPAETER_DATIERT(Severity.ABORT),
    STORNO_NICHT_NEGATIV(Severity.ABORT),

    // Zeiten
    ZEITRAUM_VERDREHT(Severity.ABORT),
    ZEITRAUM_NACH_RECHNUNGSDATUM(Severity.ABORT),
    VEREINNAHMUNG_FALSCHE_ART(Severity.ABORT),
    VEREINNAHMUNG_NACH_RECHNUNGSDATUM(Severity.ABORT),

    // Absetzung der Abschläge
    ABSCHLAEGE_FALSCHE_ART(Severity.ABORT),
    ABSCHLAEGE_FEHLEN(Severity.ABORT),
    ABSCHLAG_DOPPELT(Severity.ABORT),
    ABSCHLAG_OHNE_VEREINNAHMUNG(Severity.ABORT),
    ABSCHLAG_NACH_RECHNUNGSDATUM(Severity.ABORT),
    ABSCHLAG_SPAETER_DATIERT(Severity.ABORT),
    ABSCHLAG_OHNE_STEUERANTEILE(Severity.ABORT),
    ABSCHLAG_PRUEFSUMME(Severity.ABORT),
    ABSCHLAG_KOMBINATION_FEHLT(Severity.ABORT),
    RESTBETRAG_NEGATIV(Severity.ABORT),

    // Beträge und Fristen der Rechnung
    GESAMTBETRAG_NEGATIV(Severity.ABORT),
    ABSCHLAGSRECHNUNG_OHNE_BETRAG(Severity.ABORT),
    ZAHLUNGSZIEL_NEGATIV(Severity.ABORT),

    // Skonto (#183)
    SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH(Severity.ABORT),
    SKONTO_OHNE_ZAHLUNGSAUFFORDERUNG(Severity.ABORT),
    SKONTOFRIST_NEGATIV(Severity.ABORT),
    SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL(Severity.ABORT),
    SKONTOFRIST_DOPPELT(Severity.ABORT),
    SKONTOSTUFE_WERTLOS(Severity.ABORT),
    SKONTOSATZ_UNGUELTIG(Severity.ABORT),

    // Bestätigungsabfrage der USt-IdNr.
    BESTAETIGUNGSABFRAGE_FEHLT(Severity.ABORT),
    BESTAETIGUNGSABFRAGE_NACH_RECHNUNGSDATUM(Severity.ABORT),

    // Beim Zusammentragen der Abschläge (`rechnungsgenerator abschlaege`)
    ABSCHLAGSRECHNUNGEN_VERSCHIEDENE_KUNDEN(Severity.ABORT),
    ABSCHLAGSRECHNUNG_DOPPELT(Severity.ABORT),
    KEINE_ABSCHLAGSRECHNUNG(Severity.ABORT),

    // Hinweise: Die Rechnung entsteht trotzdem.
    MENGE_NULL(Severity.HINT),
    GESAMTBETRAG_NULL(Severity.HINT),
    RECHNUNG_SPAET(Severity.HINT),
    EU_LISTE_ALT(Severity.HINT),
    KEIN_VIES_MOEGLICH(Severity.HINT),
    ABFRAGE_EINFACH(Severity.HINT),
    ABFRAGE_OHNE_NACHWEIS(Severity.HINT),
    ABFRAGE_ALT(Severity.HINT),
}
