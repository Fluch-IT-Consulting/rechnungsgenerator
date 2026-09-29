package de.fluchitconsulting.rechnungsgenerator.core.values

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LanguageTest : FunSpec({

    test("jeder Tag ist einer, den das JDK in derselben Schreibweise zurückgibt") {
        // Ein vertippter Tag ergäbe still ein Locale ohne Land.
        Language.entries.forEach { language ->
            withClue(language.name) {
                language.locale.toLanguageTag() shouldBe language.tag
            }
        }
    }
})
