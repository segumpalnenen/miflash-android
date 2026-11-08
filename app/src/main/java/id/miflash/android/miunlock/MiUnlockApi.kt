package id.miflash.android.miunlock

import android.util.Base64
import id.miflash.android.miunlock.auth.MiAccountCredentials
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

class MiUnlockApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class MiUnlockHst(
    val host: String,
    val ssecurity: String,
    val serviceToken: String
)

class MiUnlockApi {

    companion object {
        const val SID = "miui_unlocktool_client"
        const val CLIENT_VERSION = "7.6.727.43"
        private const val USER_AGENT = "XiaomiPCSuite"
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val HMAC_KEY = "2tBeoEyJTunmWUGq7bQH2Abn0k2NhhurOaqBfyxCuLVgn4AVj7swcawe53uDUno"
        private const val AES_IV = "0102030405060708"
        private const val REGION_URL = "https://account.xiaomi.com/pass/user/login/region"
        private const val REGION_CONFIG_URL = "https://account.xiaomi.com/pass2/config?key=regionConfig"
        private const val SERVICE_LOGIN_URL = "https://account.xiaomi.com/pass/serviceLogin"
    }

    fun fetchHst(credentials: MiAccountCredentials): MiUnlockHst {
        val cookie = "passToken=${credentials.passToken}; deviceId=${credentials.deviceId}; userId=${credentials.userId}"
        val regionBody = stripPrefix(httpGetFull(REGION_URL, cookie).body)
        val region = JSONObject(regionBody).getJSONObject("data").getString("region")
        val configBody = stripPrefix(httpGetFull(REGION_CONFIG_URL, cookie).body)
        val regionConfigs = JSONObject(configBody).getJSONObject("regionConfig")
        var regionConfig: String? = null
        val keys = regionConfigs.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val config = regionConfigs.getJSONObject(key)
            if (config.has("region.codes")) {
                val codes = config.getJSONArray("region.codes")
                for (i in 0 until codes.length()) {
                    if (region == codes.getString(i)) {
                        regionConfig = key
                        break
                    }
                }
            }
            if (regionConfig != null) break
        }
        val host = "https://${hostFor(regionConfig ?: throw MiUnlockApiException("Region tidak dikenal: $region"))}.miui.com"
        val serviceResult = httpGetFull("$SERVICE_LOGIN_URL?sid=unlockApi", cookie)
        val pragma = serviceResult.extensionPragma ?: throw MiUnlockApiException("ssecurity tidak ditemukan")
        val ssecurity = JSONObject(pragma).getString("ssecurity")
        val serviceToken = serviceResult.setCookies
            .map { it.substringBefore(";").trim() }
            .filter { it.isNotEmpty() && !it.endsWith("=null") }
            .joinToString(";")
        if (serviceToken.isEmpty()) throw MiUnlockApiException("serviceToken tidak ditemukan")
        return MiUnlockHst(host, ssecurity, serviceToken)
    }

    fun nonce(host: String, ssecurity: String, serviceToken: String): JSONObject {
        val random = (1..16).map { ('a'..'z').random() }.joinToString("")
        return send(host, "/api/v2/nonce", listOf("r", "sid"), mapOf("r" to random, "sid" to SID), ssecurity, serviceToken)
    }

    fun deviceClear(host: String, ssecurity: String, serviceToken: String, product: String, nonce: String): JSONObject {
        val data = JSONObject().put("product", product).toString()
        return send(host, "/api/v2/unlock/device/clear", listOf("data", "nonce", "sid"), mapOf("data" to data, "nonce" to nonce, "sid" to SID), ssecurity, serviceToken)
    }

    fun ahaUnlock(host: String, ssecurity: String, serviceToken: String, deviceToken: String, product: String, pcId: String, userId: String, nonce: String): JSONObject {
        val deviceInfo = JSONObject().apply {
            put("boardVersion", "")
            put("deviceName", "")
            put("product", product)
            put("socId", "")
        }
        val data = JSONObject().apply {
            put("clientId", "2")
            put("clientVersion", CLIENT_VERSION)
            put("deviceInfo", deviceInfo)
            put("deviceToken", deviceToken)
            put("language", "en")
            put("operate", "unlock")
            put("pcId", pcId)
            put("region", "")
            put("uid", userId)
        }.toString()
        return send(host, "/api/v3/ahaUnlock", listOf("appId", "data", "nonce", "sid"), mapOf("appId" to "1", "data" to data, "nonce" to nonce, "sid" to SID), ssecurity, serviceToken)
    }

    fun pcId(deviceId: String): String {
        return hex(MessageDigest.getInstance("MD5").digest(deviceId.toByteArray(Charsets.UTF_8)))
    }

    fun send(host: String, path: String, paramOrder: List<String>, paramsRaw: Map<String, String>, ssecurity: String, serviceToken: String): JSONObject {
        return try {
            val key = Base64.decode(ssecurity, Base64.DEFAULT)
            val iv = AES_IV.toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            val params = paramsRaw.toMutableMap()
            if (params.containsKey("data")) {
                params["data"] = Base64.encodeToString(params["data"]!!.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }
            if (!params.containsKey("sid")) params["sid"] = SID
            val encrypt: (String) -> String = { input ->
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
                Base64.encodeToString(cipher.doFinal(input.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            }
            val signParams = paramOrder.joinToString("&") { k -> "$k=${params[k] ?: ""}" }
            val signStr = "POST\n$path\n$signParams"
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(HMAC_KEY.toByteArray(Charsets.UTF_8), "HmacSHA1"))
            val hexHmac = hex(mac.doFinal(signStr.toByteArray(Charsets.UTF_8)))
            val currentSign = encrypt(hexHmac)
            val encodedParams = paramOrder.map { k -> "$k=${encrypt(params[k] ?: "")}" }
            val sha1Input = "POST&$path&${encodedParams.joinToString("&")}&sign=$currentSign&$ssecurity"
            val signature = Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest(sha1Input.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            val form = LinkedHashMap<String, String>()
            for (pair in encodedParams) {
                val eq = pair.indexOf('=')
                form[pair.substring(0, eq)] = pair.substring(eq + 1)
            }
            form["sign"] = currentSign
            form["signature"] = signature
            val cookie = serviceToken.split(";").map { it.trim() }.filter { it.isNotEmpty() && it.contains("=") }.joinToString("; ")
            val responseBody = httpPostForm("$host$path", form, cookie)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decrypted = cipher.doFinal(Base64.decode(responseBody.trim(), Base64.DEFAULT))
            val inner = String(Base64.decode(String(decrypted, Charsets.UTF_8).trim(), Base64.DEFAULT), Charsets.UTF_8)
            JSONObject(inner)
        } catch (e: Exception) {
            JSONObject().put("error", "Request failed: ${e.javaClass.simpleName} - ${e.message}")
        }
    }

    private fun hostFor(regionConfig: String): String {
        return when (regionConfig) {
            "Singapore" -> "unlock.update.intl"
            "China" -> "unlock.update"
            "India" -> "in-unlock.update.intl"
            "Russia" -> "ru-unlock.update.intl"
            "Europe" -> "eu-unlock.update.intl"
            else -> throw MiUnlockApiException("Region tidak dikenal: $regionConfig")
        }
    }

    private fun stripPrefix(body: String): String {
        return if (body.startsWith("&&&START&&&")) body.substring(11) else body
    }

    private fun hex(bytes: ByteArray): String {
        val chars = "0123456789abcdef"
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(chars[v ushr 4]).append(chars[v and 0x0F])
        }
        return out.toString()
    }

    private data class HttpResult(
        val body: String,
        val extensionPragma: String?,
        val setCookies: List<String>
    )

    private fun httpGetFull(url: String, cookie: String?): HttpResult {
        var currentUrl = url
        var redirects = 0
        var extensionPragma: String? = null
        val setCookies = mutableListOf<String>()
        while (true) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                if (!cookie.isNullOrEmpty()) connection.setRequestProperty("Cookie", cookie)
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = CONNECT_TIMEOUT_MS
                val code = connection.responseCode
                connection.getHeaderField("extension-pragma")?.let { if (extensionPragma == null) extensionPragma = it }
                for ((key, values) in connection.headerFields) {
                    if (key != null && key.equals("Set-Cookie", ignoreCase = true)) setCookies.addAll(values)
                }
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    val location = connection.getHeaderField("Location")
                    if (location == null || redirects >= 5) throw MiUnlockApiException("Redirect gagal")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) throw MiUnlockApiException("HTTP $code")
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                return HttpResult(body, extensionPragma, setCookies)
            } catch (e: IOException) {
                throw MiUnlockApiException("Tidak ada koneksi", e)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun httpPostForm(url: String, params: Map<String, String>, cookie: String?): String {
        val body = params.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            if (!cookie.isNullOrEmpty()) connection.setRequestProperty("Cookie", cookie)
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = CONNECT_TIMEOUT_MS
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw MiUnlockApiException("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw MiUnlockApiException("Tidak ada koneksi", e)
        } finally {
            connection.disconnect()
        }
    }
}
