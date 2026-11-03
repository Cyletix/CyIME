package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** GitHub #118：导入方案的 recognizer 规则以 V、` 起头时，这些键交给 Rime。 */
class RecognizerLeadCharsTest {
    @Test fun `schema patterns give their fixed first characters`() {
        val schema = """
            schema:
              schema_id: my_scheme
            speller:
              alphabet: zyxwvutsrqponmlkjihgfedcba
            recognizer:
              import_preset: default
              patterns:
                calculator: "^V.*${'$'}"
                radical: "^`[a-z]*${'$'}"
                unicode: "^U[a-f0-9]+"
                email: "^[A-Za-z][-_.0-9A-Za-z]*@.*${'$'}"
                upper: "[A-Z][-_+.'0-9A-Za-z]*${'$'}"
                lower: "^az[a-z]*"
        """.trimIndent()
        assertEquals(setOf('V', '`', 'U'), SchemaManager.parseRecognizerLeads(schema))
    }

    @Test fun `custom yaml patches add patterns`() {
        val custom = """
            patch:
              "recognizer/patterns/calc": "^=.*${'$'}"
              recognizer/patterns/reverse: '^\`[a-z]*'
        """.trimIndent()
        assertEquals(setOf('=', '`'), SchemaManager.parseRecognizerLeads("", custom))
    }

    @Test fun `classes, groups and anchors without a fixed lead are ignored`() {
        listOf("^[a-z]+", "^(a|b)", "^.*", "V.*", "^", "^1.*", "^\\d+").forEach {
            assertNull(it, SchemaManager.regexLeadChar(it))
        }
        assertEquals('/', SchemaManager.regexLeadChar("^\\/[a-z]*"))
    }
}
