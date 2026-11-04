package com.kingzcheung.xime.contacts

import com.kingzcheung.xime.service.CandidateAction

/**
 * 候选栏的几列（平行）：显示的字、注释、点了做什么；[origin] 是每行在合并前的位置，
 * 新放进去的联系人条目是 -1（调用方据此保留引擎候选原来的附加信息）。
 */
internal data class CandidateRows(
    val texts: List<String>,
    val comments: List<String>,
    val actions: List<CandidateAction>,
    val origin: List<Int> = texts.indices.toList(),
)

/** 要放进候选栏的一个联系人条目：名字或号码（号码的注释是名字）。 */
internal data class ContactItem(val text: String, val comment: String, val isName: Boolean)

/**
 * 通讯录候选怎么放进候选栏（纯函数，便于单测）。
 *
 * - 名字已经在候选里（引擎也给了同样的字）：原位不动，只把它换成不学习的上屏方式；
 * - 不在：放在首选后面（候选栏空时放第一个）；号码紧跟名字。
 * 首选的位置不变，平时打字的手感不变。
 */
internal object ContactCandidates {

    /** 查到的人 → 条目：名字（开着「人名」时）+ 最多两个号码（开着「手机号」时）。 */
    fun items(entries: List<ContactIndex.Entry>, names: Boolean, phones: Boolean): List<ContactItem> {
        val out = ArrayList<ContactItem>()
        for (entry in entries) {
            if (names) out += ContactItem(entry.name, "", isName = true)
            if (phones) entry.phones.take(ContactIndex.MAX_PHONES).forEach { out += ContactItem(it, entry.name, isName = false) }
        }
        return out
    }

    fun merge(rows: CandidateRows, items: List<ContactItem>): CandidateRows {
        if (items.isEmpty()) return rows
        val texts = rows.texts.toMutableList()
        val comments = rows.texts.indices.mapTo(ArrayList()) { rows.comments.getOrElse(it) { "" } }
        val actions = rows.texts.indices.mapTo(ArrayList()) { rows.actions.getOrElse(it) { CandidateAction.engine(it) } }
        val origin = rows.texts.indices.toMutableList()
        var insertAt = if (texts.isEmpty()) 0 else 1
        for (item in items) {
            val existing = texts.indexOf(item.text)
            if (existing >= 0) {
                actions[existing] = CandidateAction.contact(item.text)
                if (!item.isName && comments[existing].isEmpty()) comments[existing] = item.comment
                insertAt = maxOf(insertAt, existing + 1)
                continue
            }
            val at = insertAt.coerceAtMost(texts.size)
            texts.add(at, item.text)
            comments.add(at, item.comment)
            actions.add(at, CandidateAction.contact(item.text))
            origin.add(at, -1)
            insertAt = at + 1
        }
        return CandidateRows(texts, comments, actions, origin)
    }
}
