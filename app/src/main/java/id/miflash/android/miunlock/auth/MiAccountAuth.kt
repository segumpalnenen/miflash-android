package id.miflash.android.miunlock.auth

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import org.json.JSONObject

class MiAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PollTimeoutException : Exception("Login timeout")

data class MiAccountCredentials(
    val deviceId: String,
    val passToken: String,
    val userId: String
)

data class MiServiceSession(
    val ssecurity: String,
    val nonce: String,
    val cUserId: String?,
    val psecurity: String?,
    val deviceId: String,
    val cookies: Map<String, String>
)

class MiCookieJar {
    private val cookies = LinkedHashMap<String, String>()

    fun header(): String? {
        if (cookies.isEmpty()) return null
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun store(connection: HttpURLConnection) {
        for ((key, values) in connection.headerFields) {
            if (key == null || !key.equals("Set-Cookie", ignoreCase = true)) continue
            for (raw in values) {
                val pair = raw.substringBefore(";").trim()
                val eq = pair.indexOf('=')
                if (eq > 0) cookies[pair.substring(0, eq)] = pair.substring(eq + 1)
            }
        }
    }

    fun get(name: String): String? = cookies[name]

    fun putAll(values: Map<String, String>) {
        cookies.putAll(values)
    }

    fun snapshot(): Map<String, String> = cookies.toMap()
}

class MiAccountAuth {

    companion object {
        const val SID = "unlockApi"
        private const val SERVICE_LOGIN_URL = "https://account.xiaomi.com/pass/serviceLogin"
        private const val USER_AGENT = "offici5l/migate"
        private const val CONNECT_TIMEOUT_MS = 30_000
    }

    fun getService(credentials: MiAccountCredentials): MiServiceSession {
        val jar = MiCookieJar()
        jar.putAll(
            mapOf(
                "deviceId" to credentials.deviceId,
                "passToken" to credentials.passToken,
                "userId" to credentials.userId
            )
        )
        val params = mapOf("sid" to SID, "checkSafeAddress" to "True", "_json" to "True")
        val json = JSONObject(stripPrefix(httpGet(SERVICE_LOGIN_URL, params, jar, CONNECT_TIMEOUT_MS)))
        val nonce = json.optString("nonce", "")
        val ssecurity = json.optString("ssecurity", "")
        val location = json.optString("location", "")
        if (nonce.isEmpty() || ssecurity.isEmpty() || location.isEmpty()) {
            throw MiAuthException("Service data tidak lengkap")
        }
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("nonce=$nonce&$ssecurity".toByteArray(Charsets.UTF_8))
        val clientSign = URLEncoder.encode(Base64.encodeToString(digest, Base64.NO_WRAP), "UTF-8")
        val before = jar.snapshot()
        httpGet("$location&clientSign=$clientSign", emptyMap(), jar, CONNECT_TIMEOUT_MS)
        val serviceCookies = jar.snapshot().filterKeys { it !in before }
        return MiServiceSession(
            ssecurity = ssecurity,
            nonce = nonce,
            cUserId = json.optString("cUserId").ifEmpty { null },
            psecurity = json.optString("psecurity").ifEmpty { null },
            deviceId = credentials.deviceId,
            cookies = serviceCookies
        )
    }

    private fun stripPrefix(body: String): String {
        return if (body.startsWith("&&&START&&&")) body.substring(11) else body
    }

    private fun buildUrl(url: String, params: Map<String, String>): String {
        if (params.isEmpty()) return url
        val query = params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        return if (url.contains("?")) "$url&$query" else "$url?$query"
    }

    private fun httpGet(url: String, params: Map<String, String>, jar: MiCookieJar, readTimeoutMs: Int): String {
        var currentUrl = buildUrl(url, params)
        var redirects = 0
        while (true) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/json;charset=UTF-8")
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                jar.header()?.let { connection.setRequestProperty("Cookie", it) }
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = readTimeoutMs
                val code = connection.responseCode
                jar.store(connection)
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    val location = connection.getHeaderField("Location")
                    if (location == null || redirects >= 5) throw MiAuthException("Redirect gagal")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) throw MiAuthException("HTTP $code")
                return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e: SocketTimeoutException) {
                throw PollTimeoutException()
            } catch (e: MiAuthException) {
                throw e
            } catch (e: IOException) {
                throw MiAuthException("Tidak ada koneksi", e)
            } finally {
                connection.disconnect()
            }
        }
    }
}
