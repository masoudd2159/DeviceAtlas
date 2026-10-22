package io.github.masoudd2159.deviceatlas

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class LocalizationTest {
    @Test fun persianCoversEveryStringAndPreservesFormattingArguments() {
        val english = strings("values")
        val persian = strings("values-fa")
        assertEquals("Every UI string needs a Persian translation", english.keys, persian.keys)
        val placeholder = Regex("%\\d+\\$[.\\d]*[a-zA-Z]")
        english.forEach { (key, value) ->
            val translated = persian.getValue(key)
            assertTrue("Empty translation: $key", translated.isNotBlank())
            assertEquals("Format arguments: $key", placeholder.findAll(value).map { it.value }.toList(),
                placeholder.findAll(translated).map { it.value }.toList())
            assertFalse("Arabic kaf/yeh in Persian: $key", translated.any { it == 'ك' || it == 'ي' })
        }
    }

    private fun strings(folder: String): Map<String, String> {
        val file = File("src/main/res/$folder/strings.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        val pairs = (0 until nodes.length).map { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
        assertEquals("Duplicate resource names", pairs.size, pairs.toMap().size)
        return pairs.toMap()
    }
}
