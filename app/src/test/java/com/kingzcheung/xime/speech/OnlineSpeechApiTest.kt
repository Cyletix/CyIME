package com.kingzcheung.xime.speech

import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.app.Application
import com.kingzcheung.xime.settings.SettingsPreferences
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 内置在线语音：地址怎么拼、返回里怎么找字、从「在线语音」插件搬设置。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnlineSpeechApiTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun `OpenAI 地址填到 v1 或填全都行，TokenHub 原样用`() {
        assertEquals("https://api.groq.com/openai/v1/audio/transcriptions", OnlineSpeechApi.endpoint("https://api.groq.com/openai/v1/"))
        assertEquals("https://x.cn/v1/audio/transcriptions", OnlineSpeechApi.endpoint("https://x.cn/v1/audio/transcriptions"))
        val hub = "https://tokenhub.tencentmaas.com/v1/wand/asrproxy/sync_transcribe"
        assertEquals(hub, OnlineSpeechApi.endpoint(hub))
        assertTrue(OnlineSpeechApi.isTokenHub(hub))
        assertFalse(OnlineSpeechApi.isTokenHub("https://api.openai.com/v1"))
    }

    @Test fun `返回里的文字：OpenAI text、腾讯 output text、句子列表`() {
        assertEquals("你好", OnlineSpeechApi.extractText(JSONObject("""{"text":"你好"}"""), 0))
        assertEquals("测试一下", OnlineSpeechApi.extractText(JSONObject("""{"status":"completed","output":{"text":"测试一下"}}"""), 0))
        assertEquals("hello world", OnlineSpeechApi.extractText(JSONObject("""{"segments":[{"text":"hello"},{"text":"world"}]}"""), 0))
        assertNull(OnlineSpeechApi.extractText(JSONObject("""{"id":1}"""), 0))
    }

    @Test fun `出错时显示接口自己的原因`() {
        assertEquals("Invalid API key", OnlineSpeechApi.serverMessage(JSONObject("""{"error":{"message":"Invalid API key"}}"""), ""))
        assertEquals("参数错误", OnlineSpeechApi.serverMessage(JSONObject("""{"Response":{"Error":{"Message":"参数错误"}}}"""), ""))
        assertNull(OnlineSpeechApi.serverMessage(null, "<html>502</html>"))
    }

    @Test fun `插件里填过的接口搬回内置，并改选内置`() {
        app.getSharedPreferences("online_speech_api", 0).edit().clear().commit()
        // 插件设置文件；没有 enc: 前缀的值按明文读（测试环境没有 Android 密钥库）。
        app.getSharedPreferences("plugin_cfg_com.kingzcheung.xime.plugin.online_asr", 0).edit()
            .putString("apiKey", "sk-test").putString("baseUrl", "https://api.siliconflow.cn/v1")
            .putString("model", "FunAudioLLM/SenseVoiceSmall").commit()
        SettingsPreferences.setSttOnlinePluginId(app, "com.kingzcheung.xime.plugin.online_asr")
        SettingsPreferences.setSttUseLocal(app, false)

        OnlineSpeechApi.migrateFromPlugin(app)

        val config = OnlineSpeechApi.config(app)
        assertEquals("sk-test", config.apiKey)
        assertEquals("https://api.siliconflow.cn/v1", config.baseUrl)
        assertEquals("FunAudioLLM/SenseVoiceSmall", config.model)
        assertTrue(OnlineSpeechApi.isSelected(app))
        assertTrue(OnlineSpeechApi.isActive(app))
    }
}
