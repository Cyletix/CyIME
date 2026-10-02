package com.kingzcheung.xime.service

import android.content.ClipDescription
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo

/** Native delivery first, followed by legacy support-library protocols when advertised. */
internal object ImagePasteProtocol {
    fun supports(types: Array<String>?, mime: String): Boolean = types.orEmpty().any {
        ClipDescription.compareMimeTypes(mime, it)
    }

    fun commit(connection: InputConnection, editor: EditorInfo, content: InputContentInfo): Boolean {
        val flags = InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        // Some editors accept content without advertising MIME types; don't reject them early.
        if (runCatching { connection.commitContent(content, flags, null) }.getOrDefault(false)) return true
        for (prefix in listOf("androidx.core.view.inputmethod", "android.support.v13.view.inputmethod")) {
            val declared = editor.extras?.getStringArray("$prefix.EditorInfoCompat.CONTENT_MIME_TYPES")
            if (!supports(declared, content.description.getMimeType(0))) continue
            val base = "$prefix.InputConnectionCompat"
            val data = Bundle().apply {
                putParcelable("$base.CONTENT_URI", content.contentUri)
                putParcelable("$base.CONTENT_DESCRIPTION", content.description)
                putParcelable("$base.CONTENT_LINK_URI", content.linkUri)
                putInt("$base.CONTENT_FLAGS", flags)
            }
            if (runCatching { connection.performPrivateCommand("$base.COMMIT_CONTENT", data) }.getOrDefault(false)) return true
        }
        return false
    }
}
