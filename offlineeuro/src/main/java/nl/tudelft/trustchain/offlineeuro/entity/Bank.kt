package nl.tudelft.trustchain.offlineeuro.entity

import android.os.Build
import androidx.annotation.RequiresApi
import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import java.math.BigInteger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Bank {

    // Values from the Central Authority
    private val p: BigInteger = CentralAuthority.p
    private val alpha: BigInteger = CentralAuthority.alpha

    // Secret x of the bank
    private var x: BigInteger = Cryptography.generateRandomBigInteger(CentralAuthority.p)

    val z: BigInteger = alpha.modPow(x, p)

    private var rsaParameters: RSAParameters = Cryptography.generateRSAParameters(2048)

    lateinit var r: BigInteger
    private lateinit var firstUserS: BigInteger
    private var depositedTokens: ArrayList<Receipt> = arrayListOf()

    fun getPublicRSAValues(): Pair<BigInteger, BigInteger> {
        return Pair(rsaParameters.e, rsaParameters.n)
    }

    fun registerUser(senderMessage: Pair<Pair<BigInteger?, BigInteger?>, BigInteger>): Pair<BigInteger, BigInteger> {
        val senderI = Pair(senderMessage.first.first?.modPow(rsaParameters.d, rsaParameters.n),
            senderMessage.first.second?.modPow(rsaParameters.d, rsaParameters.n))
        // TODO store information regarding the user 4.1 step 4.1

        // TODO Make random
        val k = BigInteger("32342424")
        val senderM = senderI.second
        val s = BigInteger(k.toString() + senderM?.toString()) % CentralAuthority.p
        firstUserS = s
        val v = CentralAuthority.alpha.modPow(s, CentralAuthority.p)
        r = v.modPow(x, CentralAuthority.p)

        // TODO Store s, k, v and R in the database and encrypt v and r

        return Pair(v, r)
    }

    fun depositToken(receipt: Receipt): String {
        val newToken = receipt.token
        for (depositedReceipt in depositedTokens) {
            val token = depositedReceipt.token
            if (token == newToken) {
                val maliciousY = Cryptography.solve_for_y(depositedReceipt.gamma, receipt.gamma, depositedReceipt.challenge, receipt.challenge, CentralAuthority.p)
                val maliciousW = Cryptography.solve_for_w(token.u, maliciousY, depositedReceipt.gamma, depositedReceipt.challenge, CentralAuthority.p)
                return "Double Spending detected! This is done by y: $maliciousY and w: $maliciousW"
            }
        }
        depositedTokens.add(receipt)
        return "Deposit was successful!"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun signToken(unsignedToken: Pair<BigInteger, BigInteger>): Triple<BigInteger, BigInteger, String> {
        val (a, c) = unsignedToken
        val timeStamp = LocalDateTime.now().plusYears(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val timeStampString = timeStamp.format(formatter)
        val cPrime = (c * x + firstUserS).mod(CentralAuthority.q)
        val hash = CentralAuthority.H1(timeStampString)
        val aPrime = (a * hash).modPow(rsaParameters.d, rsaParameters.n)
        return Triple(aPrime, cPrime, timeStampString)
    }
}
