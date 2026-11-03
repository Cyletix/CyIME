package com.kingzcheung.xime.speech

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/** 火山引擎(豆包)语音识别：地址识别、鉴权头、二进制帧的编码和解析。服务器帧按官方帧格式用 Python 生成。 */
class VolcengineSpeechApiTest {
    private val api = VolcengineSpeechApi

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test fun `火山引擎地址按 wss 或域名认出来，其他接口不受影响`() {
        assertTrue(api.isVolcengine("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"))
        assertTrue(api.isVolcengine("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"))
        assertTrue(api.isVolcengine("openspeech.bytedance.com"))
        assertTrue(api.isVolcengine("ws://192.168.1.2:8080/asr"))
        assertFalse(api.isVolcengine("https://api.openai.com/v1"))
        assertFalse(api.isVolcengine("https://tokenhub.tencentmaas.com/v1/wand/asrproxy/sync_transcribe"))
    }

    @Test fun `地址原样用，https 换成 wss，只填域名补流式输入接口，极速版用 https`() {
        val async = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"
        assertEquals(async, api.endpoint("$async/"))
        assertEquals(async, OnlineSpeechApi.endpoint(async))
        assertEquals("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel", api.endpoint("https://openspeech.bytedance.com/api/v3/sauc/bigmodel"))
        assertEquals("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream", api.endpoint("openspeech.bytedance.com"))
        assertEquals("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream", api.endpoint("https://openspeech.bytedance.com/"))
        assertEquals("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash",
            api.endpoint("openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"))
    }

    @Test fun `模型一栏是资源 ID，没填或还是 whisper-1 时用默认资源`() {
        val stream = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel"
        assertEquals("volc.bigasr.sauc.duration", api.resourceId(" volc.bigasr.sauc.duration ", stream))
        assertEquals("volc.seedasr.sauc.duration", api.resourceId("whisper-1", stream))
        assertEquals("volc.bigasr.auc_turbo", api.resourceId("", "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"))
    }

    @Test fun `只填密钥走新版 API Key，填了 App ID 走旧版 App Key 加 Access Key`() {
        val modern = api.headers("", " key ", "volc.seedasr.sauc.duration", "rid")
        assertEquals("key", modern["X-Api-Key"])
        assertNull(modern["X-Api-App-Key"]); assertNull(modern["X-Api-Access-Key"])
        assertEquals("volc.seedasr.sauc.duration", modern["X-Api-Resource-Id"])
        assertEquals("rid", modern["X-Api-Request-Id"]); assertEquals("rid", modern["X-Api-Connect-Id"])

        val legacy = api.headers("1234567890", "token", "volc.bigasr.sauc.duration", "rid")
        assertEquals("1234567890", legacy["X-Api-App-Key"])
        assertEquals("token", legacy["X-Api-Access-Key"])
        assertNull(legacy["X-Api-Key"])
    }

    @Test fun `请求参数帧：帧头 11 10 11 00，大端长度，gzip 的 JSON`() {
        val frame = api.encodeFullClientRequest(api.requestJson())
        assertArrayEquals(byteArrayOf(0x11, 0x10, 0x11, 0x00), frame.copyOfRange(0, 4))
        val size = ByteBuffer.wrap(frame, 4, 4).int
        assertEquals(frame.size - 8, size)
        val json = JSONObject(String(api.gunzip(frame.copyOfRange(8, frame.size)), Charsets.UTF_8))
        val audio = json.getJSONObject("audio")
        assertEquals("pcm", audio.getString("format")); assertEquals(16000, audio.getInt("rate"))
        assertEquals(16, audio.getInt("bits")); assertEquals(1, audio.getInt("channel"))
        assertEquals("bigmodel", json.getJSONObject("request").getString("model_name"))
        assertTrue(json.getJSONObject("request").getBoolean("enable_punc"))
    }

    @Test fun `音频帧 200 ms 一包，只有最后一包带结束标记，拼回来和原录音一样`() {
        val pcm = ByteArray(api.PACKET_BYTES * 2 + 100) { (it % 251).toByte() }
        val packets = api.encodeAudioPackets(pcm)
        assertEquals(3, packets.size)
        packets.forEachIndexed { i, p ->
            assertEquals(0x11.toByte(), p[0])
            assertEquals(if (i == 2) 0x22.toByte() else 0x20.toByte(), p[1])   // 音频帧；最后一包标志 0b0010
            assertEquals(0x01.toByte(), p[2])                                      // 不序列化，gzip
            assertEquals(p.size - 8, ByteBuffer.wrap(p, 4, 4).int)
        }
        val joined = packets.flatMap { api.gunzip(it.copyOfRange(8, it.size)).toList() }.toByteArray()
        assertArrayEquals(pcm, joined)
        assertEquals(api.PACKET_BYTES, api.gunzip(packets[0].copyOfRange(8, packets[0].size)).size)
        // 空录音也发一个结束包，服务器才会收尾。
        assertEquals(0x22.toByte(), api.encodeAudioPackets(ByteArray(0)).single()[1])
    }

    @Test fun `解析带序号的中间结果`() {
        val frame = api.parse(hex("11911100000000010000004e1f8b08000000000002ffab564a2c4dc9cc8fcfcc4bcb57b252a8564a292d4a2cc9cccf03720c0d0c0c6a7514948a528b4b734ac0b225a9152086d293bd0b9e2eddab545b0b00cc211a1c40000000"))
        assertEquals(api.FULL_SERVER_RESPONSE, frame.type)
        assertFalse(frame.last)
        assertEquals(1, frame.sequence)
        assertEquals("你好", api.resultText(frame.json))
    }

    @Test fun `解析最后一条结果：负序号带结束标记，text 为空时拼 utterances`() {
        val frame = api.parse(hex("11931100fffffffd000000681f8b08000000000002ffab562a4a2d2ecd2951b252a8562a49ad003194947414944a4b4a528b12f392538b8122d108b927bbbb9e2e590944cf364c01a94b494dcbcccb2c4905ca951495a6d6ea2099f3745fc7d3a57b1f37346153185b5b0b00915463bf7d000000"))
        assertTrue(frame.last)
        assertEquals(-3, frame.sequence)
        // result.text 是空的：把 utterances 的句子拼起来。
        assertEquals("今天天气很好。", api.resultText(frame.json))
        assertEquals("", api.resultText(JSONObject("""{"result":{"text":""}}""")))
        assertNull(api.resultText(JSONObject("""{"audio_info":{"duration":0}}""")))
    }

    @Test fun `解析错误帧：错误码和接口原因`() {
        val frame = api.parse(hex("11f0100002aea541000000207b226572726f72223a2022696e76616c6964207265736f75726365206964227d"))
        assertEquals(api.SERVER_ERROR, frame.type)
        assertEquals(45000001L, frame.code)
        assertEquals("invalid resource id", frame.json?.optString("error"))
        assertEquals("请求参数不对(45000001)：invalid resource id", api.errorText(frame.code, "invalid resource id"))
        assertEquals("服务器出错(55000000)", api.errorText(55000000L, null))
    }
}
