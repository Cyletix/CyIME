package com.kingzcheung.xime.settings

/**
 * 「我的短语」里的换行（发票抬头、地址这类多行短语）。Rime 短语表一行一条，换行写进去会把一条拆成两半，
 * 所以表里存成 U+2028（行分隔符）：表里仍是一行，候选上显示成 ↵，上屏时换回换行。
 */
object PhraseLineBreaks {
    /** 短语表里代替换行的字符。 */
    const val STORED = ' '

    /** 候选栏、短语列表里显示的换行。 */
    const val SHOWN = '↵'

    /** 写进短语表：各种换行统一成 [STORED]；制表符是表里分隔编码的，换成空格，免得编码被短语顶掉。 */
    fun stored(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n').replace('\n', STORED).replace('\t', ' ')

    /** 上屏、编辑框里用：[STORED] 换回换行。 */
    fun typed(text: String): String = if (STORED in text) text.replace(STORED, '\n') else text

    /** 候选、列表里显示：[STORED] 显示成 [SHOWN]，候选栏还是一行。 */
    fun shown(text: String): String = if (STORED in text) text.replace(STORED, SHOWN) else text
}
