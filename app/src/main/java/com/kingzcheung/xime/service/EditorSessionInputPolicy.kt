package com.kingzcheung.xime.service

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** Field constraints belong to one editor session, never to the user's saved language/profile. */
internal data class EditorFieldPolicy(
    val kind: Kind = Kind.TEXT,
    val signed: Boolean = false,
    val decimal: Boolean = false,
) {
    enum class Kind { TEXT, PASSWORD, NUMBER_PASSWORD, NUMBER, PHONE, DATE, TIME, DATETIME, ADDRESS }

    val scoped: Boolean get() = kind != Kind.TEXT
    val numberPanel: Boolean get() = kind in setOf(Kind.NUMBER_PASSWORD, Kind.NUMBER, Kind.PHONE, Kind.DATE, Kind.TIME, Kind.DATETIME)
    val locksNativeInput: Boolean get() = scoped && kind != Kind.ADDRESS

    /** Only a newly typed key is converted. Existing content and explicit paste never enter here. */
    fun typedLiteral(text: String): String {
        val ascii = buildString {
            text.forEach { c ->
                append(when {
                    c == '\u3000' -> ' '
                    c in '\uff01'..'\uff5e' -> (c.code - 0xfee0).toChar()
                    else -> punctuationWidth(c.toString(), false, false)
                })
            }
        }
        val accepted: (Char) -> Boolean = when (kind) {
            Kind.NUMBER_PASSWORD -> { c -> c in '0'..'9' }
            Kind.NUMBER -> { c -> c in '0'..'9' || signed && c in "+-" || decimal && c in ".," }
            Kind.PHONE -> { c -> c in '0'..'9' || c in "+*#,; ()-.N" }
            Kind.DATE -> { c -> c in '0'..'9' || c in "/-. " }
            Kind.TIME -> { c -> c in '0'..'9' || c in ":. aApPmM" }
            Kind.DATETIME -> { c -> c in '0'..'9' || c in "/-:. aApPmM" }
            Kind.PASSWORD, Kind.ADDRESS -> { c -> c in ' '..'~' }
            Kind.TEXT -> { _ -> true }
        }
        // Do not partially deliver a generated non-ASCII word/candidate.
        return ascii.takeIf { it.all(accepted) }.orEmpty()
    }

    companion object {
        fun from(info: EditorInfo?): EditorFieldPolicy = fromInputType(info?.inputType ?: InputType.TYPE_NULL)

        fun fromInputType(inputType: Int): EditorFieldPolicy {
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            return when (inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_TEXT -> when (variation) {
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> EditorFieldPolicy(Kind.PASSWORD)
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_URI -> EditorFieldPolicy(Kind.ADDRESS)
                    else -> EditorFieldPolicy()
                }
                InputType.TYPE_CLASS_NUMBER -> if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
                    EditorFieldPolicy(Kind.NUMBER_PASSWORD)
                else EditorFieldPolicy(Kind.NUMBER,
                    signed = inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0,
                    decimal = inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0)
                InputType.TYPE_CLASS_PHONE -> EditorFieldPolicy(Kind.PHONE)
                InputType.TYPE_CLASS_DATETIME -> EditorFieldPolicy(when (variation) {
                    InputType.TYPE_DATETIME_VARIATION_DATE -> Kind.DATE
                    InputType.TYPE_DATETIME_VARIATION_TIME -> Kind.TIME
                    else -> Kind.DATETIME
                })
                else -> EditorFieldPolicy()
            }
        }
    }
}

internal data class EditorSessionPresentation(val isAsciiMode: Boolean, val currentSchemaId: String)

internal class EditorSessionInputPolicy {
    @Volatile var field = EditorFieldPolicy(); private set
    @Volatile var asciiOverride: Boolean? = null; private set
    @Volatile var schemaOverride: String? = null; private set
    @Volatile private var restoreAscii: Boolean? = null
    private var original: EditorSessionPresentation? = null
    private var editorIdentity: Triple<String?, Int, Int>? = null

    val scoped: Boolean get() = this.field.scoped
    val locksNativeInput: Boolean get() = this.field.locksNativeInput
    val directInput: Boolean get() = this.field.scoped && asciiOverride == true

    fun begin(info: EditorInfo?, restarting: Boolean, currentAscii: Boolean, currentSchema: String): EditorSessionPresentation {
        val identity = info?.let { Triple(it.packageName, it.fieldId, it.inputType) }
        if (restarting && identity == editorIdentity) {
            return EditorSessionPresentation(asciiOverride ?: currentAscii, schemaOverride ?: currentSchema)
        }
        val wasScoped = field.scoped
        val previous = original ?: EditorSessionPresentation(currentAscii, currentSchema)
        field = EditorFieldPolicy.from(info)
        editorIdentity = identity
        schemaOverride = null
        asciiOverride = true.takeIf { field.scoped }
        restoreAscii = previous.isAsciiMode.takeIf { wasScoped && !field.scoped }
        original = previous.takeIf { field.scoped }
        return EditorSessionPresentation(asciiOverride ?: previous.isAsciiMode, previous.currentSchemaId)
    }

    /** Explicit internationalized email/URL input is allowed, and remains local to this field. */
    fun choose(ascii: Boolean, schema: String? = null): Boolean {
        if (locksNativeInput && !ascii) return false
        if (scoped) {
            asciiOverride = ascii
            if (!schema.isNullOrEmpty()) schemaOverride = schema
        }
        restoreAscii = null
        return true
    }

    fun engineAsciiOverride(): Boolean? = asciiOverride ?: restoreAscii

    fun completePreparation() { if (!scoped) restoreAscii = null }
}
