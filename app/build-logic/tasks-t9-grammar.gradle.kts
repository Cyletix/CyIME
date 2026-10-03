import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

// Immutable release asset ID, with content verification even if upstream removes
// or replaces the LTS tag. Data: amzxyz/RIME-LMDG, CC-BY-4.0, unmodified.
val grammarSha = "873cbbb359fcf4df8b200183683ddc8be7b321eac4c864f8d2c7fc3136d4279f"
val grammarAsset = "602588267"
val grammarName = "wanxiang-lts-zh-hans.gram"
val grammarOutput = layout.buildDirectory.dir("generated/t9-grammar/rime_chinese")
val prepareT9Grammar by tasks.registering {
    inputs.property("sha256", grammarSha)
    inputs.property("asset", grammarAsset)
    outputs.dir(grammarOutput)
    doLast {
        fun sha(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        val cached = rootProject.file(".gradle/t9-grammar/$grammarName")
        if (!cached.isFile || sha(cached) != grammarSha) {
            check(!gradle.startParameter.isOffline) { "Run prepareT9Grammar once online." }
            cached.parentFile.mkdirs()
            val pending = File(cached.parentFile, "$grammarName.download")
            val connection = URI("https://api.github.com/repos/amzxyz/RIME-LMDG/releases/assets/$grammarAsset")
                .toURL().openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.connectTimeout = 30000
            connection.readTimeout = 600000
            try {
                check(connection.responseCode == 200) { "Grammar download failed: ${connection.responseCode}" }
                connection.inputStream.use { i -> pending.outputStream().use { i.copyTo(it) } }
                check(sha(pending) == grammarSha) { "T9 grammar checksum mismatch" }
                pending.copyTo(cached, overwrite = true)
            } finally {
                connection.disconnect()
                pending.delete()
            }
        }
        val output = grammarOutput.get().asFile.apply { mkdirs() }
        cached.copyTo(File(output, grammarName), overwrite = true)
    }
}
// Kept as an explicit preparation task for native replay/development. The grammar
// is an optional download in both APK editions, not a prerequisite for packaging.
