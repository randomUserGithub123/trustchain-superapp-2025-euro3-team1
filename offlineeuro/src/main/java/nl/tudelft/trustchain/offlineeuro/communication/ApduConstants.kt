package nl.tudelft.trustchain.offlineeuro.communication

object ApduConstants {

    const val AID: String = "F222222222"

    const val CLA: Byte = 0x00

    const val INS_GET_PUBLIC_KEY: Byte                 = 0xA0.toByte()

    const val INS_BLIND_RANDOMNESS_REQUEST: Byte       = 0xA1.toByte()

    const val INS_BLIND_SIGNATURE_CHALLENGE: Byte      = 0xA2.toByte()

    const val INS_TRANSACTION_RANDOMNESS_REQUEST: Byte = 0xA3.toByte()

    const val INS_TRANSACTION_DETAILS: Byte            = 0xA4.toByte()

    val SW_SUCCESS: ByteArray = byteArrayOf(0x90.toByte(), 0x00.toByte())

    val SW_FAILURE: ByteArray = byteArrayOf(0x6F.toByte(), 0x00.toByte())

    fun buildCommandApdu(ins: Byte, data: ByteArray): ByteArray {
        val p1: Byte = 0x00
        val p2: Byte = 0x00
        val lc: Byte = (data.size and 0xFF).toByte()
        val header = byteArrayOf(CLA, ins, p1, p2, lc)
        return header + data
    }

    fun isSuccess(responseApdu: ByteArray): Boolean {
        if (responseApdu.size < 2) return false
        val sw1 = responseApdu[responseApdu.size - 2]
        val sw2 = responseApdu[responseApdu.size - 1]
        return (sw1 == SW_SUCCESS[0] && sw2 == SW_SUCCESS[1])
    }

    fun stripStatusWord(responseApdu: ByteArray): ByteArray {
        return if (responseApdu.size > 2) {
            responseApdu.copyOf(responseApdu.size - 2)
        } else {
            ByteArray(0)
        }
    }

    fun isSelectAidCommand(commandApdu: ByteArray): Boolean {
        if (commandApdu.size < 6) return false
        val ins = commandApdu[1]
        val p1  = commandApdu[2]
        val p2  = commandApdu[3]
        val lc  = (commandApdu[4].toInt() and 0xFF)
        if (ins != 0xA4.toByte() || p1 != 0x04.toByte() || p2 != 0x00.toByte()) return false
        if (commandApdu.size < 5 + lc) return false
        val aidBytes = commandApdu.copyOfRange(5, 5 + lc)
        val expectedAid = hexStringToByteArray(AID)
        return aidBytes.contentEquals(expectedAid)
    }

    fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        require(len % 2 == 0) { "Hex string must have even length" }
        val result = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            val high = Character.digit(s[i], 16)
            val low  = Character.digit(s[i + 1], 16)
            result[i / 2] = ((high shl 4) + low).toByte()
        }
        return result
    }
}
