package com.kingzcheung.xime.service

import com.kingzcheung.xime.util.FileLogger
import com.kingzcheung.xime.rime.resolveRimeCandidateIndex
import com.kingzcheung.xime.ui.keyboard.isT9Schema
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文本上屏与剪贴板提交。
 *
 * 承载 commitImage（图片上屏）、剪贴板候选提交（selectClipboardItem/commitClipboardText/
 * deleteClipboardChars）与语音撤销/搜索动作（performUndo/performSearch）。
 * 共享状态通过 service 引用访问。
 */
internal class ImeTextCommit(private val service: XimeInputMethodService) {
    internal fun performUndo() {
        val currentTextBeforeCursor = service.currentInputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        val currentLength = currentTextBeforeCursor.length
        
        val charsToDelete = currentLength - service.voiceRecognitionHandler.textLengthBeforeVoiceInput
        
        if (charsToDelete > 0) {
            for (i in 0 until charsToDelete) {
                service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        }
        
        service.voiceRecognitionHandler.textBeforeVoiceInput = ""
        service.voiceRecognitionHandler.textLengthBeforeVoiceInput = 0
    }
    
    internal fun performSearch() {
        service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    internal fun commitImage(imagePath: String, mimeType: String = "image/jpeg"): Boolean {
        return try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                FileLogger.e(XimeInputMethodService.TAG, "Image file not found: $imagePath")
                return false
            }

            // 按扩展名修正真实 MIME 类型（PNG/GIF/WebP 表情不应声明为 image/jpeg）
            val actualMimeType = when (imageFile.extension.lowercase()) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "jpg", "jpeg" -> "image/jpeg"
                else -> mimeType
            }

            // 宿主未声明支持图片 MIME 时 commitContent 必然失败，
            // 提前返回 false，由调用方降级为复制到剪贴板
            val supportedMimeTypes = service.currentInputEditorInfo?.contentMimeTypes
            if (!supportsMimeType(supportedMimeTypes, actualMimeType)) {
                Log.i(XimeInputMethodService.TAG, "Host does not support image commit (contentMimeTypes=${supportedMimeTypes?.contentToString()}), falling back to clipboard")
                return false
            }

            val cacheDir = File(service.cacheDir, "emoji_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val cacheFile = File(cacheDir, imageFile.name)
            FileInputStream(imageFile).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            val uri = getContentUriForImage(cacheFile, actualMimeType) ?: return false
            
            val inputContentInfo = InputContentInfo(
                uri,
                android.content.ClipDescription("emoji_image", arrayOf(actualMimeType)),
                null
            )
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            } else {
                0
            }
            
            service.currentInputConnection?.commitContent(inputContentInfo, flags, null) ?: false
            
        } catch (e: Exception) {
            FileLogger.e(XimeInputMethodService.TAG, "Failed to commit image", e)
            false
        }
    }

    /** 判断宿主声明的 contentMimeTypes 是否支持指定 MIME 类型（支持通配符匹配）。 */
    private fun supportsMimeType(declaredMimeTypes: Array<String>?, mimeType: String): Boolean {
        if (declaredMimeTypes.isNullOrEmpty()) return false
        return declaredMimeTypes.any { declared ->
            declared == "*/*" ||
                declared.equals(mimeType, ignoreCase = true) ||
                (declared.endsWith("/*") && mimeType.startsWith(declared.removeSuffix("/*"), ignoreCase = true))
        }
    }
    

    internal fun selectClipboardItem(text: String) {
        if (service.candidateState.value.isComposing) {
            service.keyRouter.postRimeJob {
                service.rimeEngine.clearComposition()
                withContext(Dispatchers.Main) {
                    service.updateUI()
                }
            }
        }
        // 标记为已消费：候选栏/剪贴板点选上屏后不再重复出现在候选栏
        service.clipboardManager.markConsumed(text)
        // 粘贴不计打字统计（不投 text_committed），联想照常
        service.commitPastedText(text)
        // 已通过 InputConnection 完成粘贴，不再回写系统剪贴板触发一次新的复制通知。
    }

    internal fun commitClipboardText(text: String) {
        service.commitPastedText(text)
    }

    /**
     * 键盘滑动数字/符号和选字直接上屏，必须与普通按键走同一队列。
     * 直接借用粘贴会只替换宿主 composing 区，却留下 Rime 编码；下一键会把旧字母再写回来。
     * 先结束当前候选，再提交字面内容；手势预览不走此入口。
     */
    internal fun isFullWidthPunctuation(): Boolean =
        com.kingzcheung.xime.settings.SettingsPreferences.punctuationFullWidth(service,
            !service.uiState.value.isAsciiMode && !service.rimeEngine.getOption("ascii_punct"))

    internal fun commitLiteralText(text: String) {
        if (text.isEmpty()) return
        val literal = punctuationWidth(text, isFullWidthPunctuation(),
            service.uiState.value.currentSchemaId in com.kingzcheung.xime.settings.JapaneseSchemas.ids ||
                service.uiState.value.currentSchemaId == "jaroomaji")
        service.voiceRecognitionHandler.abandonPendingOnManualInput()
        val owner = service.uiState.value.inputSessionId
        val submit: suspend () -> Unit = literal@ {
            if (owner != service.uiState.value.inputSessionId) return@literal
            val engine = service.rimeEngine
            if (isT9Schema(service.uiState.value.currentSchemaId)) {
                // 前驱九键已结束，但它的候选刷新在 Main 队列；先让这一帧同步落地。
                // onT9RefreshComposition 不再二次 post，屏上候选与本队列的引擎输入因此同源。
                withContext(Dispatchers.Main) { Unit }
            }
            if (owner != service.uiState.value.inputSessionId) return@literal
            // 日语已调整过的转换范围按当前预览提交，不能再次强制选择第一项。
            val japaneseCommitted = service.japaneseInputController.commit()
            if (owner != service.uiState.value.inputSessionId) return@literal
            val state = service.uiState.value
            val candidates = service.candidateState.value
            val input = engine.getInput()
            val isT9 = isT9Schema(state.currentSchemaId)
            val hasComposition = input.isNotEmpty() || candidates.isComposing ||
                service.t9PartialSegments.isNotEmpty()
            var prefix = ""
            if (hasComposition && !japaneseCommitted && candidates.pendingEnglishText.isEmpty()) {
                val displayed = candidates.candidates.firstOrNull()
                val action = candidates.candidateActions.firstOrNull()
                if (isT9) {
                    val partial = service.t9PartialSegments.joinToString("") { it.text }
                    // 首候选可能只覆盖一部分数字码；完整转换预览保留它后面的未选编码。
                    // 引擎已空时只提交 partial 全文，不能再次拼候选中的 partial。
                    prefix = when {
                        input.isEmpty() && partial.isNotEmpty() -> partial
                        action?.isPluginCandidate == true -> partial + action.commitText
                        else -> partial + engine.conversionPreview().ifEmpty {
                            displayed ?: candidates.preeditText.ifEmpty { input }
                        }
                    }
                } else if (action?.isPluginCandidate == true) {
                    prefix = action.commitText
                } else {
                    // 部分选词可能不产生 commit，先保存包含余下编码的完整转换预览以免丢字。
                    val preview = engine.conversionPreview().ifEmpty { input }
                    val engineCandidates = engine.getCandidates().toList()
                    val index = action?.engineIndex?.takeIf { it >= 0 }
                        ?: displayed?.let { resolveRimeCandidateIndex(0, it, engineCandidates) }
                        ?: 0
                    prefix = if (engineCandidates.isNotEmpty() && engine.selectCandidate(index)) {
                        val committed = engine.commit()
                        if (committed.isEmpty()) preview else {
                            // 引擎可能只提交前一段；结束本轮字面输入前保留尚未消费的后缀。
                            val remaining = engine.getInput()
                            committed + if (remaining.isEmpty()) "" else engine.conversionPreview().ifEmpty { remaining }
                        }
                    } else preview
                }
            }
            // 在按键队列等锁清空；不能用 tryLock 的 clearComposition 静默跳过。
            if (isT9) engine.clearQueuedT9Composition() else engine.clearQueuedComposition()
            withContext(Dispatchers.Main) {
                if (owner != service.uiState.value.inputSessionId) return@withContext
                // commitText 保留内部快捷发送和工具输入框的焦点路由。
                // 英文 pending 已经逐字上屏，只结束它的状态，绝不能再提交一遍。
                if (prefix.isNotEmpty()) service.commitText(prefix)
                service.t9PartialSegments.clear()
                if (isT9) {
                    // 引擎已在九键 FIFO 清空，仅重置本地显示，不得在队列末尾再清一次。
                    service.keyboardCallbacks?.onT9ResetAfterLiteralCommit?.invoke()
                    service.uiState.value = service.uiState.value.copy(
                        t9RightCandidateSelectedCount = 0,
                        t9SelectedCandidatePinyin = "",
                    )
                }
                service.candidateState.value = service.candidateState.value.copy(
                    inputText = "", preeditText = "", pendingEnglishText = "",
                    candidates = emptyList(), candidateComments = emptyList(),
                    associationCandidates = emptyList(), candidateActions = emptyList(),
                    expandedCandidates = emptyList(), isComposing = false,
                    isShowingRecentClipboard = false, hasNextPage = false, hasPrevPage = false,
                )
                service.commitText(literal)
                service.updateUI()
                service.maybeCollapseCandidatePage()
                // 覆盖尚未送达的旧预编辑快照；取执行时引擎状态，不冻结空态覆盖下一键。
                service.uiEventChannel.trySend { service.updateUI() }
            }
        }
        val t9Queue = if (isT9Schema(service.uiState.value.currentSchemaId) && !service.uiState.value.isAsciiMode) {
            service.keyboardCallbacks?.onT9RunLiteralInput
        } else null
        if (t9Queue != null) t9Queue(submit) else service.keyRouter.postRimeJob { submit() }
    }

    internal fun deleteClipboardChars(count: Int) {
        service.currentInputConnection?.deleteSurroundingText(count, 0)
    }

    /**
     * 生成图片 content URI。
     *
     * 优先使用 FileProvider；Android 12+ 部分厂商 ROM 上
     * FileProvider.getUriForFile 内部 resolveContentProvider 以 USER_ALL(-10000)
     * 校验跨用户权限时抛 "Invalid userId -10000"，此时降级为 MediaStore
     * 插入图片获取系统 content URI（API 29+ 免权限）。
     */
    private fun getContentUriForImage(imageFile: File, mimeType: String): Uri? {
        try {
            return FileProvider.getUriForFile(
                service,
                "${service.packageName}.fileprovider",
                imageFile
            )
        } catch (e: IllegalArgumentException) {
            FileLogger.w(XimeInputMethodService.TAG, "FileProvider unavailable, falling back to MediaStore", e)
        } catch (e: Exception) {
            FileLogger.w(XimeInputMethodService.TAG, "FileProvider getUriForFile failed, falling back to MediaStore", e)
        }

        return insertImageToMediaStore(imageFile, mimeType)
    }

    /** 把图片插入 MediaStore（Pictures/Xime），返回系统 content URI。 */
    private fun insertImageToMediaStore(imageFile: File, mimeType: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            FileLogger.e(XimeInputMethodService.TAG, "MediaStore fallback requires API 29+, image commit failed")
            return null
        }
        return try {
            val resolver = service.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Xime")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(imageFile).use { input -> input.copyTo(output) }
                } ?: return null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val update = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    resolver.update(uri, update, null, null)
                }
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } catch (e: Exception) {
            FileLogger.e(XimeInputMethodService.TAG, "MediaStore insert failed", e)
            null
        }
    }
}