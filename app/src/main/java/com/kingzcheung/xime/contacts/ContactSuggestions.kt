package com.kingzcheung.xime.contacts

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.kingzcheung.xime.handwriting.HanziPinyin
import com.kingzcheung.xime.plugin.PluginConfigStoreImpl
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 通讯录插件（manifest `capabilities.contacts: true`）的宿主一侧。
 *
 * 隐私约定：
 * - 只在插件启用、并且用户给了通讯录权限时才读；权限只在打开插件时申请。
 * - 读到的名字和号码只放在内存里：不写文件，所以不进备份、不同步；插件脚本拿不到。
 * - 关掉插件、卸载插件或收回权限：内存里的表立刻丢掉。
 * - 候选上屏走 [com.kingzcheung.xime.service.CandidateAction.isPrivate]：不学习、不进联想、
 *   不进打字统计、不发给插件；密码框里不给。
 */
object ContactSuggestions {
    private const val TAG = "ContactSuggestions"
    const val KEY_NAMES = "names"
    const val KEY_PHONES = "phones"
    private const val MAX_CONTACTS = 5000
    private const val RELOAD_DELAY_MS = 2_000L

    @Volatile private var index: ContactIndex? = null
    @Volatile private var showNames = true
    @Volatile private var showPhones = true
    /** 每次清空或重读加一：在途的后台读取发现代际变了就丢掉结果。 */
    private val generation = AtomicInteger()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "contacts-index").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val RELOAD_TOKEN = Any()
    private var observer: ContentObserver? = null
    private var observedApp: Context? = null

    val isActive: Boolean get() = index != null

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** 已安装、已启用的通讯录插件 id；没有返回 null。 */
    fun enabledPluginId(context: Context): String? = runCatching {
        if (!PluginManager.isInitialized) return null
        PluginManager.getAllInstallPlugins().firstOrNull {
            it.capabilities?.contacts == true && SettingsPreferences.isPluginEnabled(context, it.id)
        }?.id
    }.getOrNull()

    fun isContactsPlugin(pluginId: String): Boolean = runCatching {
        PluginManager.isInitialized && PluginManager.getAllInstallPlugins().any { it.id == pluginId && it.capabilities?.contacts == true }
    }.getOrDefault(false)

    /**
     * 按插件开关、权限和插件设置开始或停止。弹出键盘、拨插件开关、卸载插件时调用；主线程调用。
     * 只读偏好和权限，真正读通讯录在后台。
     */
    fun sync(context: Context) {
        val app = context.applicationContext
        val pluginId = enabledPluginId(app)
        if (pluginId == null || !hasPermission(app)) {
            clear()
            return
        }
        ensureObserver(app)
        val gen = generation.get()
        worker.execute {
            // 插件设置是加密存的，解密放后台。
            val store = PluginConfigStoreImpl(app as Application, pluginId)
            showNames = store.get(KEY_NAMES)?.let { it == "true" } ?: true
            showPhones = store.get(KEY_PHONES)?.let { it == "true" } ?: true
            if (index == null && generation.get() == gen) load(app, gen)
        }
    }

    /** 丢掉内存里的通讯录，不再监听变化。 */
    fun clear() {
        generation.incrementAndGet()
        index = null
        val app = observedApp
        val current = observer
        if (app != null && current != null) runCatching { app.contentResolver.unregisterContentObserver(current) }
        observer = null
        observedApp = null
    }

    /** 当前编码对得上的人名、号码（密码框由调用方挡）。按键线程调用，只查内存。 */
    internal fun itemsFor(letters: String?, digits: String?): List<ContactItem> {
        val table = index ?: return emptyList()
        if (letters == null && digits == null) return emptyList()
        val names = showNames
        val phones = showPhones
        if (!names && !phones) return emptyList()
        val hits = table.lookup(letters, digits)
        if (hits.isEmpty()) return emptyList()
        return ContactCandidates.items(hits, names, phones)
    }

    /** 剪贴板历史和同步用：这段字是联系人的名字或号码时不记、不发。 */
    fun isContactText(text: CharSequence?): Boolean {
        val table = index ?: return false
        return text != null && table.containsText(text.toString())
    }

    private fun ensureObserver(app: Context) {
        if (observer != null) return
        val created = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                // 通讯录改了：过一会儿重读一次（批量导入时只读最后一次）。
                main.removeCallbacksAndMessages(RELOAD_TOKEN)
                main.postAtTime({
                    if (observer === this && hasPermission(app)) {
                        val gen = generation.incrementAndGet()
                        worker.execute { if (generation.get() == gen) load(app, gen) }
                    }
                }, RELOAD_TOKEN, android.os.SystemClock.uptimeMillis() + RELOAD_DELAY_MS)
            }
        }
        runCatching {
            app.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, created)
            observer = created
            observedApp = app
        }.onFailure { FileLogger.w(TAG, "通讯录变化监听失败：${it.message}") }
    }

    private fun load(app: Context, gen: Int) {
        try {
            val contacts = read(app)
            val built = ContactIndex.build(contacts, HanziPinyin.readTable(app)::lookup)
            if (generation.get() == gen) index = built
            // 只记条数，不记名字。
            FileLogger.i(TAG, "通讯录已读入：${built.size} 人")
        } catch (error: SecurityException) {
            if (generation.get() == gen) index = null
        } catch (error: Exception) {
            FileLogger.w(TAG, "通讯录读取失败：${error.javaClass.simpleName}")
        }
    }

    private fun read(app: Context): List<ContactIndex.RawContact> {
        val resolver = app.contentResolver
        val names = LinkedHashMap<Long, String>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext() && names.size < MAX_CONTACTS) {
                val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                names[c.getLong(0)] = name
            }
        }
        // 号码：手机排前面，其次是设为默认的号码。
        val phones = HashMap<Long, MutableList<Pair<Int, String>>>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id !in names) continue
                val number = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val rank = when {
                    c.getInt(2) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> 0
                    c.getInt(3) != 0 -> 1
                    else -> 2
                }
                phones.getOrPut(id) { ArrayList(1) } += rank to number
            }
        }
        return names.map { (id, name) ->
            ContactIndex.RawContact(name, phones[id].orEmpty().sortedBy { it.first }.map { it.second })
        }
    }
}
