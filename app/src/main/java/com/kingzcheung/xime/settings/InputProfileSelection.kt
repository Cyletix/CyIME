package com.kingzcheung.xime.settings

/** Language menus and settings resolve only real entries, preserving their backend IDs and order. */
object InputProfileSelection {
    fun preferred(entries: List<SchemaInfo>, language: InputLanguage, rememberedId: String?,
        currentId: String? = null): SchemaInfo? {
        val group = entries.filter { it.profile.language == language && it.profile.mode == InputMode.KEYBOARD }
        return group.firstOrNull { it.schemaId == rememberedId }
            ?: group.firstOrNull { it.schemaId == currentId } ?: group.firstOrNull()
    }

    fun changeScheme(entries: List<SchemaInfo>, current: SchemaInfo, scheme: InputScheme): SchemaInfo? {
        val group = entries.filter { it.profile.language == current.profile.language &&
            it.profile.scheme == scheme && it.profile.mode == InputMode.KEYBOARD }
        return group.firstOrNull { it.schemaId == current.schemaId }
            ?: group.firstOrNull { it.profile.layout.id == current.profile.layout.id } ?: group.firstOrNull()
    }

    fun schemes(entries: List<SchemaInfo>, language: InputLanguage): List<InputScheme> = entries
        .filter { it.profile.language == language && it.profile.mode == InputMode.KEYBOARD }
        .map { it.profile.scheme }.distinct()

    /** Concrete installed choices, retaining variant names rather than a generic "双拼" option. */
    fun schemeEntries(entries: List<SchemaInfo>, current: SchemaInfo): List<SchemaInfo> =
        schemes(entries, current.profile.language).flatMap { scheme ->
            val group = entries.filter { it.profile.language == current.profile.language &&
                it.profile.scheme == scheme && it.profile.mode == InputMode.KEYBOARD }
            val compatible = group.filter { it.profile.layout.id == current.profile.layout.id }
            if (compatible.isNotEmpty()) compatible else {
                val fallbackLayout = changeScheme(entries, current, scheme)?.profile?.layout?.id
                group.filter { it.profile.layout.id == fallbackLayout }
            }
        }.distinctBy { it.schemaId }

    fun layouts(entries: List<SchemaInfo>, current: SchemaInfo): List<InputLayout> = entries
        .filter { it.profile.language == current.profile.language && it.profile.scheme == current.profile.scheme &&
            it.profile.mode == InputMode.KEYBOARD }
        .flatMap { layoutsOf(it, entries) }.distinctBy { it.id }

    /**
     * Layouts one entry can be typed on. Chinese entries carry theirs in the schema; English and
     * Japanese romaji offer their own 26 keys plus every letter layout the Chinese entries offer.
     */
    fun layoutsOf(entry: SchemaInfo, entries: List<SchemaInfo>): List<InputLayout> {
        val base = entry.baseProfile
        if (base.layout.kind != LayoutKind.ALPHABETIC || !LetterLayouts.appliesTo(base.language, base.scheme))
            return listOf(entry.profile.layout)
        return listOf(base.layout) + LetterLayouts.choices(entries, base.language).map(InputProfiles::customLayout)
    }

    fun offers(entry: SchemaInfo, layoutId: String, entries: List<SchemaInfo>): Boolean =
        layoutsOf(entry, entries).any { it.id == layoutId }

    fun variants(entries: List<SchemaInfo>, current: SchemaInfo): List<SchemaInfo> = entries.filter {
        it.profile.mode == InputMode.KEYBOARD && it.profile.language == current.profile.language &&
            it.profile.scheme == current.profile.scheme && it.profile.layout.id == current.profile.layout.id
    }

    /** Changing shape cannot silently change language or encoding. Missing combinations stay unavailable. */
    fun changeLayout(entries: List<SchemaInfo>, current: SchemaInfo, layoutId: String): SchemaInfo? = entries
        .filter { it.profile.mode == InputMode.KEYBOARD && it.profile.language == current.profile.language &&
            it.profile.scheme == current.profile.scheme && offers(it, layoutId, entries) }
        .let { group -> group.firstOrNull { it.schemaId == current.schemaId } ?: group.firstOrNull() }
}
