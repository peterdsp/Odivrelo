package dev.peterdsp.poravia

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The localisation gate.
 *
 * Poravia ships in Greek, English and Albanian and claims all three are
 * complete. This test makes that claim checkable: a key present in one language
 * and missing from another, an empty translation, or a translation whose format
 * arguments disagree with the default, all fail the build. A half-translated
 * release cannot ship by accident.
 *
 * It reads the resource files directly rather than through the Android resource
 * system, because the resource system would happily fall back to Greek and hide
 * exactly the defect this is looking for.
 */
class LocalisationCompletenessTest {

    private val resources = File("src/main/res")

    private data class Catalogue(val locale: String, val strings: Map<String, String>)

    private fun read(folder: String): Catalogue {
        val file = File(resources, folder + "/strings.xml")
        assertTrue("missing string file: " + file.path, file.isFile)
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")
        val strings = LinkedHashMap<String, String>()
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as Element
            strings[element.getAttribute("name")] = element.textContent
        }
        return Catalogue(folder, strings)
    }

    private val greek by lazy { read("values") }
    private val english by lazy { read("values-en") }
    private val albanian by lazy { read("values-sq") }

    @Test
    fun `every key exists in every language`() {
        val expected = greek.strings.keys
        listOf(english, albanian).forEach { catalogue ->
            val missing = expected - catalogue.strings.keys
            val extra = catalogue.strings.keys - expected
            assertTrue(
                catalogue.locale + " is missing " + missing.size + " key(s): " +
                    missing.sorted().take(20),
                missing.isEmpty(),
            )
            assertTrue(
                catalogue.locale + " has " + extra.size + " key(s) the default does not: " +
                    extra.sorted().take(20),
                extra.isEmpty(),
            )
        }
    }

    @Test
    fun `no translation is blank`() {
        listOf(greek, english, albanian).forEach { catalogue ->
            val blank = catalogue.strings.filterValues { it.isBlank() }.keys
            assertTrue(
                catalogue.locale + " has blank value(s): " + blank.sorted().take(20),
                blank.isEmpty(),
            )
        }
    }

    @Test
    fun `format arguments agree across languages`() {
        val pattern = Regex("%(\\d+)\\$[a-zA-Z]")
        fun arguments(value: String): Set<String> =
            pattern.findAll(value).map { it.value }.toSet()

        greek.strings.forEach { (key, value) ->
            val expected = arguments(value)
            listOf(english, albanian).forEach { catalogue ->
                val actual = arguments(catalogue.strings.getValue(key))
                assertEquals(
                    "format arguments differ for '" + key + "' in " + catalogue.locale,
                    expected,
                    actual,
                )
            }
        }
    }

    @Test
    fun `positional arguments are always explicitly numbered`() {
        // A bare %s reorders badly once a sentence is translated, so every
        // argument in this product is numbered.
        val bare = Regex("%[a-zA-Z]")
        listOf(greek, english, albanian).forEach { catalogue ->
            catalogue.strings.forEach { (key, value) ->
                val cleaned = value.replace(Regex("%\\d+\\$[a-zA-Z]"), "").replace("%%", "")
                assertTrue(
                    catalogue.locale + " string '" + key + "' uses an unnumbered argument",
                    !bare.containsMatchIn(cleaned),
                )
            }
        }
    }

    @Test
    fun `no user-visible string carries the rejected identity`() {
        val rejected = Regex("hodomap|hodo\\b|<newname>|perastra", RegexOption.IGNORE_CASE)
        listOf(greek, english, albanian).forEach { catalogue ->
            catalogue.strings.forEach { (key, value) ->
                assertTrue(
                    catalogue.locale + " string '" + key + "' carries a rejected name",
                    !rejected.containsMatchIn(value),
                )
            }
        }
    }

    @Test
    fun `the demonstration notice names Aloria in every language`() {
        listOf(
            greek to "Αλόρια",
            english to "Aloria",
            albanian to "Aloria",
        ).forEach { (catalogue, needle) ->
            assertTrue(
                catalogue.locale + " demonstration notice does not name the invented region",
                catalogue.strings.getValue("demo_notice_body").contains(needle),
            )
            assertTrue(
                catalogue.locale + " demonstration accessibility text does not name it either",
                catalogue.strings.getValue("demo_notice_a11y").contains(needle),
            )
        }
    }
}
