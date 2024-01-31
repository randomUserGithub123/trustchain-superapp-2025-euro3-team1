package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import java.math.BigInteger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Bank (
    private val context: Context,
    private val registeredUserManager: RegisteredUserManager = RegisteredUserManager(context)
){
    // Values from the Central Authority
    private val p: BigInteger = CentralAuthority.p
    private val alpha: BigInteger = CentralAuthority.alpha

    // Secret x of the bank
    private var x: BigInteger = Cryptography.generateRandomBigInteger(CentralAuthority.p)

    val z: BigInteger = alpha.modPow(x, p)

    val name: String = "BestBank"

    private var rsaParameters: RSAParameters = Cryptography.generateRSAParameters(2048)

    lateinit var r: BigInteger
    private lateinit var firstUserS: BigInteger
    private var depositedTokens: ArrayList<Receipt> = arrayListOf()

    fun getPublicRSAValues(): Pair<BigInteger, BigInteger> {
        return Pair(rsaParameters.e, rsaParameters.n)
    }

    fun registerUser(userRegistrationMessage: UserRegistrationMessage): UserRegistrationResponseMessage {
        val encryptedI = userRegistrationMessage.i
        val senderI = Pair(encryptedI.first.modPow(rsaParameters.d, rsaParameters.n),
            encryptedI.second.modPow(rsaParameters.d, rsaParameters.n))

        val k = Cryptography.generateRandomBigInteger(CentralAuthority.p)
        val m = senderI.second
        val s = BigInteger(k.toString() + m.toString()).mod(CentralAuthority.p)
        val v = CentralAuthority.alpha.modPow(s, CentralAuthority.p)
        val r = v.modPow(x, CentralAuthority.p)
        val user = RegisteredUser(-1, userRegistrationMessage.userName, s, k, v, r)

        if (registeredUserManager.addRegisteredUser(user))
            return UserRegistrationResponseMessage(MessageResult.SuccessFul, name, v, r, "")
        // TODO More detailed error message. F.e. Name already in use
        return UserRegistrationResponseMessage(MessageResult.Failed, name, BigInteger.ZERO, BigInteger.ZERO, "Something went wrong")
    }

    fun getRegisteredUsers(): List<RegisteredUser> {
        return registeredUserManager.getAllRegisteredUsers()
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
