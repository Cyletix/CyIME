package com.kingzcheung.xime.service

/** Raw alphabetic encoding is not the formatted preedit. T9 has numeric engine input,
 * so use its decoded reading, keeping partial Chinese selections but removing display separators. */
internal fun rawCompositionCommitText(engineInput: String, preedit: String, t9: Boolean): String =
    if (t9 && preedit.isNotEmpty()) preedit.filterNot { it.isWhitespace() || it == '\'' }
    else engineInput
