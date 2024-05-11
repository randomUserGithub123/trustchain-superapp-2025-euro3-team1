package nl.tudelft.trustchain.offlineeuro.enums

enum class Role {
    Bank,
    User;

    companion object {
        fun fromInt(value: Int): Role {
            return entries.find { it.ordinal == value }!!
        }

        fun fromLong(value: Long): Role {
            return entries.find { it.ordinal == value.toInt() }!!
        }
    }
}
