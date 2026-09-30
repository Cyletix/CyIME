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
    inputs.property("qwjrtkPreset", 1)
    inputs.property("cyletix10Preset", 1)
    inputs.property("t9EnglishIndex", 2)
    inputs.property("measuredNeighborCorrection", 1)
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
        // Re-index the pinned English word sources; never lowercase the displayed text.
        // Exact whole-word digits only: no abbreviation/completion collisions with Chinese.
        val english = linkedMapOf<String, String>()
        val digits = "22233344455566677778889999"
        for (source in listOf("en_ext", "en")) {
            File(root, "en_dicts/$source.dict.yaml").useLines { lines ->
                var body = false
                lines.forEach { line ->
                    if (line.trim() == "...") body = true
                    else if (body && !line.startsWith("#")) {
                        val fields = line.split('\t')
                        if (fields.size >= 2 && fields[0].matches(Regex("[A-Za-z]{2,32}")) &&
                            fields[1].matches(Regex("[A-Za-z]{2,32}"))) {
                            val code = fields[0].lowercase(java.util.Locale.ROOT)
                                .map { digits[it - 'a'] }.joinToString("")
                            val weight = fields.getOrNull(2)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
                            val row = fields[0] + "\t" + code + (weight?.let { "\t$it" } ?: "")
                            english.putIfAbsent(fields[0] + "\t" + code, row)
                        }
                    }
                }
            }
        }
        File(root, "cyime_t9_english.dict.yaml").writeText(
            "# Generated from bundled rime-ice en_dicts; licenses retained alongside sources.\n" +
            "---\nname: cyime_t9_english\nversion: \"1\"\nsort: by_weight\n...\n" +
            english.values.joinToString("\n", postfix = "\n"))

        // Same engine, dictionary and options as Chinese26; only the keyboard arrangement differs.
        val baseSchema = File(root, "rime_ice.schema.yaml").readText().replace("\ntranslator:\n",
            "\ntranslator:\n  enable_correction: true\n  cyime_neighbor_correction: true\n")
        check(baseSchema.contains("cyime_neighbor_correction: true"))
        File(root, "rime_ice.schema.yaml").writeText(baseSchema)
        File(root, "pinyin_qwjrtk.schema.yaml").writeText(baseSchema
            .replace("schema_id: rime_ice", "schema_id: pinyin_qwjrtk")
            .replace("name: 雾凇拼音", "name: QWJRTK（双拇指）"))
        File(root, "pinyin_cyletix10.schema.yaml").writeText(baseSchema
            .replace("schema_id: rime_ice", "schema_id: pinyin_cyletix10")
            .replace("name: 雾凇拼音", "name: Cyletix10（实验）"))

    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareChineseDictionaries) }
