package com.kingzcheung.xime.clipboard.sync

import com.kingzcheung.xime.plugin.core.api.ClipboardProfile
import java.net.InetAddress
import java.net.URI
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import org.json.JSONObject

data class WindowsPeer(val id: String, val name: String, val origin: String, val fingerprint: String, val token: String = "")

object WindowsPairingProtocol {
    fun localOrigin(value: String): String {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.path.isNullOrEmpty() || uri.path == "/")
        val host = requireNotNull(uri.host).removePrefix("[").removeSuffix("]")
        require(host.matches(Regex("[0-9.]+")) || host.contains(':')) // No DNS/public endpoint substitution.
        val address = InetAddress.getByName(host)
        val bytes = address.address
        require(address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
            (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc))
        require(uri.port in 1..65535)
        return value.trimEnd('/')
    }
    fun fingerprint(value: String): String = value.also { require(it.matches(Regex("[0-9a-f]{64}"))) }
    fun code(pin: String, proof: String): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest("cyime-nearby-v1\n${fingerprint(pin)}\n$proof".toByteArray(Charsets.UTF_8))
        return ((ByteBuffer.wrap(hash).int.toLong() and 0xffffffffL) % 100000000).toString().padStart(8, '0')
    }
    fun offer(uri: String): JSONObject {
        require(uri.length <= 8192 && uri.startsWith("cyime://pair?v=2&data="))
        val bytes = Base64.getUrlDecoder().decode(uri.substringAfter("&data="))
        require(bytes.size <= 4096)
        return JSONObject(bytes.toString(Charsets.UTF_8)).also { data ->
            require(data.getInt("v") == 2)
            UUID.fromString(data.getString("server_id")); UUID.fromString(data.getString("pairing_id"))
            fingerprint(data.getString("certificate_sha256"))
            require(data.getString("secret").matches(Regex("[A-Za-z0-9_-]{43}")))
            val origins = data.getJSONArray("endpoints")
            require(origins.length() in 1..8)
            repeat(origins.length()) { localOrigin(origins.getString(it)) }
        }
    }
    fun profile(json: JSONObject): ClipboardProfile {
        val text = json.getString("text")
        val profile = ClipboardProfile.fromText(text)
        require(json.getString("type") == "text" && !json.getBoolean("has_data") && json.isNull("data_name"))
        require(text.isNotBlank() && !text.contains('\u0000') && profile.size <= 65536)
        require(profile.hash == json.getString("hash") && profile.size == json.getLong("size"))
        return profile
    }
}
