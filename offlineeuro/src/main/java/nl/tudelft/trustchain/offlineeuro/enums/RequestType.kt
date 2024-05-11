package nl.tudelft.trustchain.offlineeuro.enums

enum class RequestType {
    REQUEST_GROUP_DESCRIPTION_AND_CRS,
    BLIND_SIGNATURE_RANDOMNESS,
    TRANSACTION_RANDOMNESS;

    companion object {
        fun fromInt(value: Int): RequestType {
            return RequestType.entries.find { it.ordinal == value }!!
        }

        fun fromLong(value: Long): RequestType {
            return RequestType.entries.find { it.ordinal == value.toInt() }!!
        }
    }
}
