package com.kingzcheung.xime.clipboard

/** A group the user made (常用语, 工作 …). Records put into a group are fixed ones. */
data class ClipboardGroup(val id: String, val name: String)

/**
 * The user's groups in tab order, the group each record is in (record key → group id; one group per
 * record), and the built-in categories hidden from the tab row.
 */
data class ClipboardGroupState(
    val groups: List<ClipboardGroup> = emptyList(),
    val members: Map<String, String> = emptyMap(),
    val hidden: Set<ClipboardFilter> = emptySet(),
) {
    fun groupOf(key: String): ClipboardGroup? = members[key]?.let { id -> groups.firstOrNull { it.id == id } }
    fun count(groupId: String): Int = members.values.count { it == groupId }

    fun withGroup(group: ClipboardGroup) = copy(groups = groups + group)
    fun renamed(id: String, name: String) = copy(groups = groups.map { if (it.id == id) it.copy(name = name) else it })
    /** The records stay (fixed) in the clipboard; only the group goes. */
    fun withoutGroup(id: String) = copy(groups = groups.filterNot { it.id == id }, members = members.filterValues { it != id })
    /** Null [groupId] takes the records out of their group. */
    fun assigned(keys: Collection<String>, groupId: String?) = copy(members =
        if (groupId == null || groups.none { it.id == groupId }) members - keys.toSet()
        else members + keys.associateWith { groupId })
    fun withHidden(filter: ClipboardFilter, hide: Boolean) = copy(hidden =
        if (filter == ClipboardFilter.ALL) hidden else if (hide) hidden + filter else hidden - filter)
}

/** One tab of the board: a built-in category, or one of the user's groups (after the categories). */
sealed interface ClipboardTab {
    val key: String
    val label: String
    data class Category(val filter: ClipboardFilter) : ClipboardTab {
        override val key: String get() = filter.name
        override val label: String get() = filter.label
    }
    data class Group(val group: ClipboardGroup) : ClipboardTab {
        override val key: String get() = "group:${group.id}"
        override val label: String get() = group.name
    }
}

/** 全部 is always there, the other categories unless hidden, then the user's groups. */
internal fun clipboardTabs(state: ClipboardGroupState): List<ClipboardTab> =
    ClipboardFilter.entries.filter { it == ClipboardFilter.ALL || it !in state.hidden }.map { ClipboardTab.Category(it) } +
        state.groups.map { ClipboardTab.Group(it) }

internal fun clipboardMatches(card: ClipboardCard, tab: ClipboardTab, state: ClipboardGroupState): Boolean = when (tab) {
    is ClipboardTab.Category -> clipboardMatches(card, tab.filter)
    is ClipboardTab.Group -> state.members[card.key] == tab.group.id
}

/** The tab shown for [current]: a renamed group keeps its tab; a deleted group or hidden category falls back to 全部. */
internal fun List<ClipboardTab>.resolve(current: ClipboardTab): ClipboardTab =
    firstOrNull { it.key == current.key } ?: first()

/** One release changes at most one tab, as with the built-in categories alone. */
internal fun List<ClipboardTab>.afterSwipe(current: ClipboardTab, delta: Int): ClipboardTab {
    val index = indexOfFirst { it.key == current.key }.coerceAtLeast(0)
    return this[(index + delta).coerceIn(0, lastIndex)]
}

internal const val CLIPBOARD_GROUP_NAME_MAX = 10

internal fun cleanClipboardGroupName(name: String): String = name.filterNot { it.isISOControl() }.trim()

/** Null when [name] can be used (for a new group, or for group [renaming]); otherwise why not. */
internal fun clipboardGroupNameError(name: String, groups: List<ClipboardGroup>, renaming: String? = null): String? {
    val clean = cleanClipboardGroupName(name)
    return when {
        clean.isEmpty() -> "请输入分组名称"
        clean.codePointCount(0, clean.length) > CLIPBOARD_GROUP_NAME_MAX -> "分组名称最多 $CLIPBOARD_GROUP_NAME_MAX 个字"
        ClipboardFilter.entries.any { it.label == clean } -> "「$clean」是自带分类"
        groups.any { it.id != renaming && it.name == clean } -> "已经有「$clean」分组"
        else -> null
    }
}

/** Preference values: groups as "id\tname" lines in tab order, members as "groupId\trecordKey". */
internal object ClipboardGroupCodec {
    fun encodeGroups(groups: List<ClipboardGroup>): String = groups.joinToString("\n") { "${it.id}\t${it.name}" }

    fun decodeGroups(raw: String?): List<ClipboardGroup> = raw.orEmpty().lineSequence().mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0) null else ClipboardGroup(line.substring(0, tab), cleanClipboardGroupName(line.substring(tab + 1)))
            .takeIf { it.name.isNotEmpty() }
    }.distinctBy { it.id }.toList()

    fun encodeMembers(members: Map<String, String>): Set<String> = members.mapTo(mutableSetOf()) { (key, group) -> "$group\t$key" }

    /** Members of groups that no longer exist are dropped. */
    fun decodeMembers(raw: Set<String>?, groups: List<ClipboardGroup>): Map<String, String> {
        val ids = groups.mapTo(mutableSetOf()) { it.id }
        return raw.orEmpty().mapNotNull { entry ->
            val tab = entry.indexOf('\t')
            if (tab <= 0 || tab == entry.lastIndex) null else (entry.substring(tab + 1) to entry.substring(0, tab))
        }.filter { it.second in ids }.toMap()
    }

    fun decodeHidden(raw: Set<String>?): Set<ClipboardFilter> = raw.orEmpty()
        .mapNotNullTo(mutableSetOf()) { name -> ClipboardFilter.entries.firstOrNull { it.name == name && it != ClipboardFilter.ALL } }
}
