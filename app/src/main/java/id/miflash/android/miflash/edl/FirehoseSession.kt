package id.miflash.android.miflash.edl

import id.miflash.android.miflash.edl.SignerNotAvailableException
import id.miflash.android.common.usb.UsbTransport

class FirehoseException(message: String) : Exception(message)
class AuthRequiredException(message: String) : Exception(message)

data class FirehoseResponse(
    val ack: Boolean,
    val maxPayloadSize: Int?,
    val storageInfo: String?,
    val chipSerial: String?,
    val supportsPartialReset: Boolean,
    val needsAuth: Boolean,
    val rawText: String
)

class FirehoseSession(private val transport: UsbTransport) {

    companion object {
        const val EDL_AUTH_ERROR = "error: only nop and sig tag can be recevied before authentication"
        const val MAX_PAYLOAD_NAK = "WARN: NAK: MaxPayloadSizeToTargetInBytes sent by host 1048576 larger than supported 16384"
    }

    var maxPayloadSizeToTarget: Int? = null
        private set
    var storageInfo: String? = null
        private set
    var chipSerial: String? = null
        private set
    var supportsPartialReset: Boolean = false
        private set
    var sectorSize: Int = 4096
    var lastSawAuthError: Boolean = false
        private set

    fun sendRaw(data: ByteArray, length: Int = data.size): Boolean {
        return transport.write(data, 0, length)
    }

    fun sendCommand(command: String, checkAck: Boolean = false): FirehoseResponse? {
        val bytes = command.toByteArray(Charsets.UTF_8)
        if (!transport.write(bytes)) throw FirehoseException("Gagal mengirim perintah firehose")
        return if (checkAck) waitResponse() else null
    }

    fun ping() {
        val response = sendCommand(FirehoseCommands.nop, checkAck = true)
        if (response?.ack != true) throw FirehoseException("ping target failed")
    }

    fun authenticate(signer: SlaSigner): FirehoseResponse {
        val reqResponse = sendCommand(FirehoseCommands.reqAuth, checkAck = true)
        val challenge = extractChallenge(reqResponse?.rawText ?: "")
            ?: throw FirehoseException("Device tidak mengembalikan challenge untuk ditandatangani")
        val signature: ByteArray
        try {
            signature = signer.signChallenge(challenge)
        } catch (e: SignerNotAvailableException) {
            throw AuthRequiredException(e.message ?: "SLA signer belum disediakan")
        }
        if (signature.isEmpty()) throw FirehoseException("Signer mengembalikan signature kosong")
        val hex = signature.joinToString("") { "%02X".format(it) }
        val authResponse = sendCommand(FirehoseCommands.auth(hex), checkAck = true)
        if (authResponse?.ack != true) throw FirehoseException("authentication failed")
        return authResponse
    }

    fun extractChallenge(rawText: String): ByteArray? {
        val valueRegex = Regex("<sig[^>]*value=\"([0-9A-Fa-f]+)\"")
        val match = valueRegex.find(rawText)
        if (match != null) {
            return hexToBytes(match.groupValues[1])
        }
        val blobRegex = Regex("<sig[^>]*>([0-9A-Fa-f]{16,})</sig>")
        val blobMatch = blobRegex.find(rawText)
        if (blobMatch != null) return hexToBytes(blobMatch.groupValues[1])
        return null
    }

    fun waitResponse(): FirehoseResponse {
        var combined = ""
        var attempts = 0
        while (attempts < 32) {
            attempts++
            val chunk = readAvailable() ?: ""
            if (chunk.isNotEmpty()) {
                combined += chunk
                if (chunk.contains(EDL_AUTH_ERROR)) lastSawAuthError = true
            }
            if (combined.contains("<response") || combined.contains("<sig")) {
                val parsed = parseResponse(combined)
                if (parsed != null) return parsed
            }
            Thread.sleep(50)
        }
        throw FirehoseException("did not detect ACK from target.")
    }

    fun waitSingleAck(): Boolean {
        var attempts = 0
        while (attempts < 10) {
            attempts++
            val chunk = readAvailable() ?: ""
            if (chunk.contains("<response value=\"ACK\"")) return true
            Thread.sleep(10)
        }
        return false
    }

    fun waitAck(chunkCount: Int = 1): Boolean {
        var ackCount = 0
        var attempts = 0
        while (attempts < 10) {
            attempts++
            val chunk = readAvailable() ?: ""
            if (chunk.isNotEmpty()) {
                ackCount += "<response value=\"ACK\"".toRegex().findAll(chunk).count()
            }
            if (ackCount >= chunkCount * 2) return true
            Thread.sleep(10)
        }
        if (ackCount > 0 && chunkCount == 1) return true
        throw FirehoseException("ACK count don't match!")
    }

    private fun readAvailable(): String? {
        val buf = ByteArray(8192)
        val n = transport.read(buf, 1000)
        if (n <= 0) return null
        return String(buf, 0, n, Charsets.UTF_8)
    }

    private fun parseResponse(text: String): FirehoseResponse? {
        if (!text.contains("<response") && !text.contains("<sig")) return null
        val ack = text.contains("<response value=\"ACK\"")
        if (!ack && text.contains(MAX_PAYLOAD_NAK)) {
            return buildResponse(text, true)
        }
        if (!ack && !text.contains("NAK")) return null
        return buildResponse(text, ack)
    }

    private fun buildResponse(text: String, ack: Boolean): FirehoseResponse {
        val maxPayload = Regex("MaxPayloadSizeToTargetInBytes=\"(\\d+)\"")
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (maxPayload != null) maxPayloadSizeToTarget = maxPayload
        if (text.contains("UFS Inquiry Command Output")) storageInfo = text
        if (text.contains("INFO: quick_reset")) supportsPartialReset = true
        if (text.contains("Chip serial num")) {
            val start = text.indexOf('(') + 1
            val end = text.indexOf(')', start)
            if (start > 0 && end > start) {
                var serial = text.substring(start, end)
                if (serial.length < 10) {
                    serial = serial.substring(0, 2) + "00000000".substring(0, 10 - serial.length) +
                        serial.substring(2)
                }
                chipSerial = serial
            }
        }
        return FirehoseResponse(
            ack = ack,
            maxPayloadSize = maxPayload,
            storageInfo = storageInfo,
            chipSerial = chipSerial,
            supportsPartialReset = supportsPartialReset,
            needsAuth = text.contains(EDL_AUTH_ERROR),
            rawText = text
        )
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val clean = hex.trim()
        if (clean.length % 2 != 0) return null
        return try {
            ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
