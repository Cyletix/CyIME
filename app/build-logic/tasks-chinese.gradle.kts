import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

// Unmodified, pinned upstream files; the complete selected dictionaries ship offline in the APK.
val chineseRevision = "9e66b0729083b37d217312294f6d516c8d7234be"
val chineseArchiveSha = "85c66668255de755b51d3513ecdc612eada0e0b8d4ea27716a046257f17464e0"
val chineseRoot = layout.buildDirectory.dir("generated/chinese-assets/rime_ice")
val chineseTopFiles = setOf("LICENSE", "README.md", "rime_ice.schema.yaml", "rime_ice.dict.yaml",
    "double_pinyin_flypy.schema.yaml", "melt_eng.schema.yaml", "melt_eng.dict.yaml",
    "radical_pinyin.schema.yaml", "radical_pinyin.dict.yaml", "symbols_v.yaml", "symbols_caps_v.yaml")
fun chineseSha(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(65536)
        while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
val prepareChineseDictionaries by tasks.registering {
    group = "build setup"
    inputs.property("revision", chineseRevision)
    inputs.property("sha256", chineseArchiveSha)
    inputs.property("files", chineseTopFiles)
    outputs.dir(chineseRoot)
    doLast {
        val archive = File(rootProject.projectDir, ".gradle/chinese-data/$chineseRevision.zip")
        if (!archive.isFile || chineseSha(archive) != chineseArchiveSha) {
            check(!gradle.startParameter.isOffline) { "Run prepareChineseDictionaries once online." }
            archive.parentFile.mkdirs()
            val pending = File(archive.parentFile, "${archive.name}.download")
            val connection = URI("https://codeload.github.com/iDvel/rime-ice/zip/$chineseRevision").toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 600000
            try {
                check(connection.responseCode == 200)
                connection.inputStream.use { i -> pending.outputStream().use { i.copyTo(it) } }
                check(chineseSha(pending) == chineseArchiveSha) { "Chinese dictionary checksum mismatch" }
                pending.copyTo(archive, overwrite = true)
            } finally { connection.disconnect(); pending.delete() }
        }
        val root = chineseRoot.get().asFile.apply { mkdirs() }
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                val relative = entry.name.substringAfter('/')
                if (relative in chineseTopFiles || listOf("cn_dicts/", "en_dicts/", "lua/", "opencc/").any { relative.startsWith(it) }) {
                    val target = File(root, relative)
                    check(target.canonicalPath.startsWith(root.canonicalPath + File.separator))
                    target.parentFile.mkdirs()
                    zip.getInputStream(entry).use { i -> target.outputStream().use { i.copyTo(it) } }
                }
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareChineseDictionaries) }
