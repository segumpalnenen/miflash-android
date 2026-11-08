package id.miflash.android.miflash.edl

class SignerNotAvailableException(message: String) : Exception(message)

interface SlaSigner {
    fun signChallenge(challenge: ByteArray): ByteArray
}

class UnimplementedSlaSigner : SlaSigner {
    override fun signChallenge(challenge: ByteArray): ByteArray {
        throw SignerNotAvailableException(
            "SLA signer belum disediakan. " +
                "Algoritma signing challenge-response ada di dalam SLA_Challenge.dll (closed-source, x86 Windows) " +
                "dan belum diimplementasikan di aplikasi ini. " +
                "Flash EDL yang meminta autentikasi tidak bisa dilanjutkan tanpa signer resmi."
        )
    }
}
