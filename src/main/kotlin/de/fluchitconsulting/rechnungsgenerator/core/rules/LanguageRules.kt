package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.values.Language

/**
 * Was eine Sprache schon setzen kann (#377).
 *
 * Eine Sprache kommt schrittweise, wie ZUGFeRD (ADR-0023). Englisch gibt es
 * für jede Rechnungsart (#405), aber noch ohne die Kategorien AE, K und G und
 * ohne Skonto. Deren Sätze haben rechtliche Folgen – ein Befreiungsgrund mit
 * gesetzlichem Wortlaut, die im Voraus vereinbarte Minderung –, und ihr
 * englischer Wortlaut wird je in einem eigenen Schritt entschieden statt
 * nebenbei übersetzt.
 *
 * Die Ausschlüsse stehen hier ausdrücklich, je als `when` ohne `else`. Aus den
 * Sätzen selbst ableiten lassen sie sich nicht: Die stehen zum Teil im Rand
 * (`generate.render`), und den kennt der Kern nicht. Die englischen Zweige
 * eines ausgeschlossenen Satzes werfen deshalb mit Verweis auf diese Regel,
 * und ein Test setzt jedes Beispiel auf Englisch und verlangt entweder diesen
 * Abbruch oder ein PDF – so bleiben die beiden Hälften beisammen.
 */
internal object LanguageRules {
    fun check(calculated: CalculatedInvoice): List<Finding> {
        val invoice = calculated.invoice
        val language = invoice.language
        // Ohne `else`: Eine neue Sprache sagt hier, was sie schon kann.
        return when (language) {
            Language.GERMAN -> {
                emptyList()
            }
            Language.BRITISH_ENGLISH -> {
                buildList {
                    invoice.items.forEachIndexed { index, item ->
                        if (!supportsCategory(item.taxCategory)) {
                            add(
                                Finding(
                                    Rule.SPRACHE_NOCH_NICHT_MOEGLICH,
                                    Subject.Item(index, item.name),
                                    "Die Steuerkategorie ${item.taxCategory} (${item.taxCategory.label}) gibt es auf " +
                                        "$language noch nicht: Ihr Befreiungsgrund hat einen gesetzlichen Wortlaut, " +
                                        "dessen englische Fassung noch nicht entschieden ist.",
                                ),
                            )
                        }
                    }
                    if (invoice.skonto.isNotEmpty()) {
                        add(
                            Finding(
                                Rule.SPRACHE_NOCH_NICHT_MOEGLICH,
                                Subject.WholeInvoice,
                                "Skonto gibt es auf $language noch nicht. Bitte 'skonto' oder 'sprache' weglassen.",
                            ),
                        )
                    }
                }
            }
        }
    }

    // Die Frage nur für Englisch, weil es heute die einzige zweite Sprache
    // ist. Kommt eine dritte mit anderem Umfang, bekommt sie die Sprache.
    //
    // S, Z, E und O ja: Bei E und O schreibt der Rechnungssteller den Grund
    // selbst, und O – die Leistung an einen Unternehmer im Drittland – ist der
    // häufigste Fall einer englischen Rechnung.
    private fun supportsCategory(category: TaxCategory): Boolean =
        when (category) {
            TaxCategory.STANDARD, TaxCategory.ZERO_RATED, TaxCategory.EXEMPT, TaxCategory.OUT_OF_SCOPE -> true
            TaxCategory.REVERSE_CHARGE, TaxCategory.INTRA_COMMUNITY, TaxCategory.EXPORT -> false
        }
}
