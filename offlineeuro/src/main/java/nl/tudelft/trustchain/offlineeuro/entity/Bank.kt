package nl.tudelft.trustchain.offlineeuro.entity

import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import java.math.BigInteger

class Bank (
    private var rsaParameters: RSAParameters,
    private var x: BigInteger,
    var z: BigInteger,
) {
    init {
        rsaParameters = Cryptography.generateRSAParameters(2048)
        x = BigInteger("321321312")
        val alpha = CentralAuthority.alpha
        val p = CentralAuthority.p
        z = alpha.modPow(x, p)
    }
}
