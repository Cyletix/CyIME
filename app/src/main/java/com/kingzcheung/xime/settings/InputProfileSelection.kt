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
        return group.firstOrNull { it.profile.layout.id == current.profile.layout.id } ?: group.firstOrNull()
    }
}
