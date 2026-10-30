import java.security.MessageDigest

val manifestOutput = layout.buildDirectory.dir("generated/rime-manifest")
val roots = listOf(
    "rime_ice" to layout.buildDirectory.dir("generated/chinese-assets/rime_ice").get().asFile,
    "rime_chinese" to file("src/main/assets/rime_chinese"),
    "rime_chinese" to layout.buildDirectory.dir("generated/stroke-assets/rime_chinese").get().asFile,
    "rime_japanese" to layout.buildDirectory.dir("generated/japanese-assets/rime_japanese").get().asFile,
    "rime_japanese" to file("src/main/assets/rime_japanese"),
)
// The elder package leaves the Japanese assets out of the APK, so out of the manifest too.
val elder = providers.gradleProperty("elderEdition").orNull == "true"
val prepareRimeManifest by tasks.registering {
    dependsOn("prepareChineseDictionaries", "prepareStrokeDictionary", "prepareJapaneseDictionaries")
    inputs.property("elder", elder)
    inputs.files(roots.map { it.second })
    outputs.dir(manifestOutput)
    doLast {
        val entries = sortedMapOf<String, String>()
        for ((assetRoot, root) in roots) if (!(elder && assetRoot == "rime_japanese")) root.walkTopDown().filter { it.isFile }.forEach { source ->
            val relative = source.relativeTo(root).invariantSeparatorsPath
            // 长句重排已停用，两种包都不带这个模型（与 build.gradle.kts 的资源忽略规则一致）。
            if (assetRoot == "rime_chinese" && relative == "t9_sentence.onnx") return@forEach
            if (relative.endsWith(".md") || relative.startsWith("LICENSE") || relative.endsWith(".custom.yaml")) return@forEach
            // Optional layouts are installed only when missing; a user's edited schema stays theirs.
            if (assetRoot == "rime_ice" && relative in setOf("pinyin_qwjrtk.schema.yaml", "pinyin_cyletix10.schema.yaml", "pinyin_colemak.schema.yaml", "pinyin_typewise.schema.yaml")) return@forEach
            val target = if (assetRoot == "rime_japanese" && relative.endsWith(".lua")) "lua/$relative" else relative
            val digest = MessageDigest.getInstance("SHA-256")
            source.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            entries[target] = "$hash\t${source.length()}\t$assetRoot/$relative\t$target"
        }
        val output = manifestOutput.get().asFile.apply { mkdirs() }
        File(output, "rime-bundled-manifest.tsv").writeText(entries.values.joinToString("\n", postfix = "\n"))
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareRimeManifest) }
