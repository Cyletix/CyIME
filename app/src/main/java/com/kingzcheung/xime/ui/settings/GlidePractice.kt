package com.kingzcheung.xime.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Gesture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.handwriting.training.HandwritingContribution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

private val practiceClient by lazy { OkHttpClient() }

private suspend fun uploadPractice(context: Context, samples: List<String>) {
    val bytes = ByteArrayOutputStream()
    GZIPOutputStream(bytes).bufferedWriter(Charsets.UTF_8).use { out ->
        samples.forEach { out.appendLine(HandwritingContribution.licensedSample(it)) }
    }
    val request = Request.Builder().url(HandwritingContribution.UPLOAD_URL)
        .header("User-Agent", "CyIME/${com.kingzcheung.xime.BuildConfig.VERSION_NAME}")
        .header("X-CyIME-Writer", HandwritingContribution.writerId(context))
        .header("X-CyIME-Format", "cyime-glide-practice/1")
        .post(bytes.toByteArray().toRequestBody("application/gzip".toMediaType())).build()
    suspendCancellableCoroutine<Unit> { result ->
        val call = practiceClient.newCall(request)
        result.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { result.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val receipt = runCatching { JSONObject(it.body.string()) }.getOrNull()
                    if (it.isSuccessful && receipt?.optBoolean("ok") == true && receipt.optInt("count") == samples.size)
                        result.resume(Unit)
                    else result.resumeWithException(IOException(receipt?.optString("error")?.takeIf(String::isNotBlank)
                        ?: "接收端未确认上传（${it.code}）"))
                }
            }
        })
    }
}

@Composable
internal fun GlidePracticeItem(nine: Boolean) {
    var open by remember { mutableStateOf(false) }
    SettingsItem(Icons.TwoTone.Gesture, "滑行练习与贡献", "按题目滑词，可自愿上传轨迹来改进识别。", { open = true }, showArrow = true)
    if (open) GlidePracticeDialog(nine, { open = false })
}

@Composable
internal fun GlidePracticeDialog(nine: Boolean, onClose: () -> Unit, upload: suspend (Context, List<String>) -> Unit = ::uploadPractice) {
    val context = LocalContext.current.applicationContext
    val prompts = remember(nine) {
        if (nine) listOf("nihao", "shuru", "women", "xihuan")
        else listOf("hello", "world", "good", "after", "nihao", "shuru", "women", "xihuan")
    }
    var index by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var uploaded by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    val pending = remember { mutableStateListOf<String>() }
    LaunchedEffect(enabled, error.isEmpty()) {
        if (!enabled || error.isNotEmpty()) return@LaunchedEffect
        while (true) {
            snapshotFlow { pending.isNotEmpty() }.first { it }
            delay(1500)
            val batch = pending.take(40)
            if (batch.isEmpty()) continue
            try {
                upload(context, batch)
                pending.removeAll(batch.toSet())
                uploaded += batch.size
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "上传失败"; break }
        }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("滑行练习") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsToggleItem(Icons.TwoTone.Gesture, "自动贡献本次练习",
                "将本页题目、键盘布局和轨迹及随机贡献编号上传到魔搭，用于改进滑行识别。关闭或退出会丢弃待传轨迹。",
                enabled, { turnOn ->
                    if (turnOn) showLicense = true else { enabled = false; pending.clear(); error = "" }
                })
            Text("请滑出 ${prompts[index]}", style = MaterialTheme.typography.titleLarge)
            PracticePad(nine, prompts[index]) { sample ->
                if (sample == null) hint = "请从题目的第一个键滑到最后一个键。"
                else if (enabled && pending.size >= 40) hint = "本次待传已满，请先重试或关闭贡献。"
                else {
                    if (enabled) pending.add(sample)
                    index = (index + 1) % prompts.size
                    hint = ""
                }
            }
            if (hint.isNotEmpty()) Text(hint)
            if (enabled) Text("已上传 $uploaded 条，待传 ${pending.size} 条")
            if (error.isNotBlank()) {
                Text(error)
                TextButton(onClick = { error = "" }) { Text("重试上传") }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("关闭") } })
    if (showLicense) SampleLicenseDialog(
        "开启后，本次练习的题目、布局、键盘比例、轨迹坐标与时间、随机编号和应用版本会自动发往魔搭。关闭或退出后停止贡献，日常滑行输入不会上传。",
        onDismiss = { showLicense = false },
    ) { showLicense = false; enabled = true }
}

@Composable
private fun PracticePad(nine: Boolean, target: String, completed: (String?) -> Unit) {
    val rows = remember(nine) { if (nine) listOf("123", "456", "789") else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm") }
    val labels = listOf("", "", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
    val deliver by rememberUpdatedState(completed)
    val cols = rows.first().length
    Column(Modifier.fillMaxWidth().height(if (nine) 170.dp else 138.dp).testTag("glide-practice-pad")
        .pointerInput(nine, target) {
            fun key(p: Offset): Char? {
                if (p.x !in 0f..size.width.toFloat() || p.y < 0f || p.y >= size.height) return null
                val row = rows[(p.y * rows.size / size.height).toInt()]
                val col = p.x * cols / size.width - (cols - row.length) / 2f
                return if (col < 0) null else row.getOrNull(col.toInt())
            }
            fun targetKey(c: Char) = if (nine) "22233344455566677778889999"[c - 'a'] else c
            awaitEachGesture {
                val down = awaitFirstDown()
                if (key(down.position) != targetKey(target.first())) return@awaitEachGesture
                down.consume()
                val points = mutableListOf<List<Int>>()
                fun point(p: Offset, time: Long) {
                    val elapsed = (time - down.uptimeMillis).coerceIn(0L, 30000L).toInt()
                    val x = (p.x * 1000 / size.width).roundToInt().coerceIn(0, 1000)
                    val y = (p.y * 1000 / size.height).roundToInt().coerceIn(0, 1000)
                    if (points.size == 256) {
                        val reduced = points.filterIndexed { i, _ -> i % 2 == 0 }
                        points.clear(); points.addAll(reduced)
                    }
                    points.add(listOf(x, y, elapsed))
                }
                point(down.position, down.uptimeMillis)
                var moved = false
                while (true) {
                    val event = awaitPointerEvent()
                    val touch = event.changes.singleOrNull()?.takeIf { it.id == down.id && !it.isConsumed } ?: break
                    touch.historical.forEach { point(it.position, it.uptimeMillis) }
                    point(touch.position, touch.uptimeMillis)
                    moved = moved || (touch.position - down.position).getDistance() > viewConfiguration.touchSlop
                    touch.consume()
                    if (!touch.pressed) {
                        val valid = moved && key(touch.position) == targetKey(target.last()) && touch.uptimeMillis - down.uptimeMillis <= 30000
                        deliver(if (!valid) null else JSONObject().put("id", UUID.randomUUID().toString().replace("-", ""))
                            .put("layout", if (nine) "t9" else "qwerty").put("target", target)
                            .put("aspect", size.width.toDouble() / size.height).put("points", JSONArray(points)).toString())
                        break
                    }
                }
            }
        }) {
        for (row in rows) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.Center) {
            val side = (cols - row.length) / 2f
            if (side > 0) Spacer(Modifier.weight(side))
            for (c in row) Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center) {
                Text(if (nine) labels[c.digitToInt()].ifEmpty { "1" } else c.toString(), maxLines = 1)
            }
            if (side > 0) Spacer(Modifier.weight(side))
        }
    }
}
