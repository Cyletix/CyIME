package com.kingzcheung.xime.service

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** Field constraints belong to one editor session, never to the user's saved language/profile. */
internal data class EditorFieldPolicy(
    val kind: Kind = Kind.TEXT,
    val signed: Boolean = false,
    val decimal: Boolean = false,
) {
    enum class Kind { TEXT, PASSWORD, NUMBER_PASSWORD, NUMBER, PHONE, DATE, TIME, DATETIME, ADDRESS, SEARCH_OR_ADDRESS }

    val scoped: Boolean get() = kind != Kind.TEXT
    val isAddressField: Boolean get() = kind == Kind.ADDRESS || kind == Kind.SEARCH_OR_ADDRESS
    val numberPanel: Boolean get() = kind in setOf(Kind.NUMBER_PASSWORD, Kind.NUMBER, Kind.PHONE, Kind.DATE, Kind.TIME, Kind.DATETIME)
    val locksNativeInput: Boolean get() = scoped && !isAddressField

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
            Kind.TEXT, Kind.SEARCH_OR_ADDRESS -> { _ -> true }
        }
        // Do not partially deliver a generated non-ASCII word/candidate.
        return ascii.takeIf { it.all(accepted) }.orEmpty()
    }

    companion object {
        private val searchHint = Regex("""\bsearch\b|搜索|搜尋|搜寻|検索|검색""", RegexOption.IGNORE_CASE)
        private val addressHint = Regex("""https?://|\b(?:url|uri|website|web address|link)\b|网址|網址|地址|链接|連結""", RegexOption.IGNORE_CASE)
        private val alternative = Regex("""\bor\b|或|または|하거나""", RegexOption.IGNORE_CASE)
        // Match a dedicated prompt as a whole. A translated omnibox hint may also contain "URL".
        private val addressOnlyHint = Regex(
            """(?:(?:https?://|www\.)\S*|""" +
                """(?:(?:enter|paste|type)\s+(?:(?:an?|the|your)\s+)?)?(?:(?:website|server|callback|search engine)\s+)?(?:url|uri|website|(?:web )?address|link)|""" +
                """(?:请|請)?(?:输入|輸入|填写|填寫|粘贴|貼上)?\s*(?:网站|網站|服务器|伺服器|回调|回呼|搜索引擎|搜尋引擎)?\s*(?:网址|網址|地址|链接|連結|url|uri))[:：]?""",
            RegexOption.IGNORE_CASE,
        )

        fun from(info: EditorInfo?): EditorFieldPolicy {
            val type = info?.inputType ?: InputType.TYPE_NULL
            val policy = fromInputType(type)
            if (info == null || policy.kind != Kind.ADDRESS ||
                type and InputType.TYPE_MASK_VARIATION != InputType.TYPE_TEXT_VARIATION_URI) return policy

            val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
            if (action == EditorInfo.IME_ACTION_SEARCH) return EditorFieldPolicy(Kind.SEARCH_OR_ADDRESS)
            val hint = info.hintText?.take(256)?.toString()?.trim().orEmpty()
            val namesAddress = addressHint.containsMatchIn(hint)
            val offersSearch = searchHint.containsMatchIn(hint) && (!namesAddress || alternative.containsMatchIn(hint))
            // A URI keyboard with a Go button may submit a search or a URL. Web editors can
            // omit the hint altogether. Do not force ASCII unless the purpose is explicit.
            val purpose = hint.ifEmpty { info.fieldName?.substringAfterLast('/')?.replace('_', ' ').orEmpty() }
            val mixed = offersSearch || (action == EditorInfo.IME_ACTION_GO && !addressOnlyHint.matches(purpose))
            return if (mixed) EditorFieldPolicy(Kind.SEARCH_OR_ADDRESS) else policy
        }

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
    /** Search-or-address bars, and email/URL fields when 「网址、邮箱框自动切英文」 is off, keep the current language. */
    @Volatile private var keepsLanguage = false
    private var original: EditorSessionPresentation? = null
    private var editorIdentity: Triple<String?, Int, Int>? = null

    val scoped: Boolean get() = this.field.scoped
    val locksNativeInput: Boolean get() = this.field.locksNativeInput
    val directInput: Boolean get() = this.field.scoped && this.field.kind != EditorFieldPolicy.Kind.SEARCH_OR_ADDRESS && asciiOverride == true

    fun begin(info: EditorInfo?, restarting: Boolean, currentAscii: Boolean, currentSchema: String,
        addressEnglish: Boolean = true): EditorSessionPresentation {
        val identity = info?.let { Triple(it.packageName, it.fieldId, it.inputType) }
        val next = EditorFieldPolicy.from(info)
        // Browsers can restart a named navigation bar as a new connection and change
        // suggestion flags, even though the user is still typing in the same view.
        val sameNavigationBar = next.kind == EditorFieldPolicy.Kind.SEARCH_OR_ADDRESS && field == next &&
            identity != null && !identity.first.isNullOrEmpty() && (restarting || identity.second > 0) &&
            editorIdentity?.first == identity.first && editorIdentity?.second == identity.second
        if ((restarting && identity == editorIdentity && field == next) || sameNavigationBar) {
            editorIdentity = identity
            return EditorSessionPresentation(asciiOverride ?: currentAscii, schemaOverride ?: currentSchema)
        }
        val wasScoped = field.scoped
        val previous = original ?: EditorSessionPresentation(currentAscii, currentSchema)
        field = next
        editorIdentity = identity
        schemaOverride = null
        keepsLanguage = next.kind == EditorFieldPolicy.Kind.SEARCH_OR_ADDRESS ||
            next.kind == EditorFieldPolicy.Kind.ADDRESS && !addressEnglish
        asciiOverride = when {
            !next.scoped -> null
            keepsLanguage -> previous.isAsciiMode.takeIf { wasScoped }
            else -> true
        }
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

    fun completePreparation(engineAscii: Boolean, engineSchema: String) {
        // On a cold start the saved language and layout are available only after Rime is ready.
        if (keepsLanguage && asciiOverride == null)
            original = EditorSessionPresentation(engineAscii, engineSchema)
        if (!scoped) restoreAscii = null
    }
}
