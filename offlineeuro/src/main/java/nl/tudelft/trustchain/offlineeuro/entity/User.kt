package nl.tudelft.trustchain.offlineeuro.entity

import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography

class User(
    private var rsaParameters: RSAParameters,
    private val m: Int = 42142132,
    private val r_m: Int = 4,
) {
    init {
        rsaParameters = Cryptography.generateRSAParameters(2048)
    }
}
