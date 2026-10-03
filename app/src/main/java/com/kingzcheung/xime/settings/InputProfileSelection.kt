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

    fun layouts(entries: List<SchemaInfo>, current: SchemaInfo): List<InputLayout> = entries
        .filter { it.profile.language == current.profile.language && it.profile.scheme == current.profile.scheme &&
            it.profile.mode == InputMode.KEYBOARD }
        .map { it.profile.layout }.distinctBy { it.id }

    fun variants(entries: List<SchemaInfo>, current: SchemaInfo): List<SchemaInfo> = entries.filter {
        it.profile.mode == InputMode.KEYBOARD && it.profile.language == current.profile.language &&
            it.profile.scheme == current.profile.scheme && it.profile.layout.id == current.profile.layout.id
    }

    /** Changing shape cannot silently change language or encoding. Missing combinations stay unavailable. */
    fun changeLayout(entries: List<SchemaInfo>, current: SchemaInfo, layoutId: String): SchemaInfo? = entries
        .filter { it.profile.mode == InputMode.KEYBOARD && it.profile.language == current.profile.language &&
            it.profile.scheme == current.profile.scheme && it.profile.layout.id == layoutId }
        .let { group -> group.firstOrNull { it.schemaId == current.schemaId } ?: group.firstOrNull() }
}
