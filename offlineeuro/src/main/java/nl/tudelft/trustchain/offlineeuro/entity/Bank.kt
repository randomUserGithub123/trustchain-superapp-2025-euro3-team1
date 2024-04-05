package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import nl.tudelft.trustchain.offlineeuro.db.DepositedTokenManager
import nl.tudelft.trustchain.offlineeuro.db.RegisteredUserManager
import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import java.math.BigInteger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

class Bank (
    val name: String = "BestBank",
    private val context: Context?,
    private val registeredUserManager: RegisteredUserManager = RegisteredUserManager(context),
    private val depositedTokensManager: DepositedTokenManager = DepositedTokenManager(context)
){
    // Values from the Central Authority
    private val p: BigInteger = CentralAuthority.p
    private val q: BigInteger = CentralAuthority.q
    private val alpha: BigInteger = CentralAuthority.alpha

    val depositedEuros: ArrayList<DigitalEuro> = arrayListOf()

    // Secret x of the bank TODO RANDOM
    private val x: BigInteger = Cryptography.generateRandomBigInteger(p)//BigInteger("123254213215123")

    val z: BigInteger = alpha.modPow(x, p)

    private var rsaParameters: RSAParameters = Cryptography.generateRSAParameters(512)

    fun getPublicRSAValues(): Pair<BigInteger, BigInteger> {
        return Pair(rsaParameters.e, rsaParameters.n)
    }

    fun handleUserRegistration(userRegistrationMessage: UserRegistrationMessage): UserRegistrationResponseMessage {
        val encryptedI = userRegistrationMessage.i
        val senderI = Pair(encryptedI.first.modPow(rsaParameters.d, rsaParameters.n),
            encryptedI.second.modPow(rsaParameters.d, rsaParameters.n))

        val k = Cryptography.generateRandomBigInteger(p)
        val m = senderI.second
        val s = BigInteger(k.toString() + m.toString()).mod(p)
        val v = alpha.modPow(s, p)
        val r = v.modPow(x, p)
        val user = RegisteredUser(-1, userRegistrationMessage.userName, s, k, v, r)

        if (registeredUserManager.addRegisteredUser(user))
            return UserRegistrationResponseMessage(MessageResult.SuccessFul, name, v, r, "")
        // TODO More detailed error message. F.e. Name already in use
        return UserRegistrationResponseMessage(MessageResult.Failed, name, BigInteger.ZERO, BigInteger.ZERO, "Something went wrong")
    }


    fun getRegisteredUsers(): List<RegisteredUser> {
        return registeredUserManager.getAllRegisteredUsers()
    }

    // TODO Make this a spend transaction
    fun depositEuro(euro: DigitalEuro): String {

        for (depositedEuro in depositedEuros) {
            if (depositedEuro.signature == euro.signature){
                // Loop over the proofs to find the double spending
                val depositedEuroProofs = depositedEuro.proofs
                val euroProofs = euro.proofs

                for (i in 0 until min(depositedEuroProofs.size, euroProofs.size)) {
                    val currentDepositProof = depositedEuroProofs[i]
                    val currentProof = euroProofs[i]

                    if (currentDepositProof == currentProof)
                        continue

                    val doubleSpendingPK = CentralAuthority.getUserFromProofs(Pair(currentProof, currentDepositProof))

                    return if (doubleSpendingPK != null) {
                        "Double spending detected. PK of the perpetrator: $doubleSpendingPK"
                    } else {
                        "Detected double spending but could not blame anyone"
                    }
                }

                return "Detected double spending by finding the exact two same tokens, the depositor tries to double spend"

            }

        }

        depositedEuros.add(euro)
        return "Deposit was successful!"

    }

    fun depositToken(receipt: Receipt): String {
        val newToken = receipt.token
        val receipts = getDepositedTokens()
        for (depositedReceipt in receipts) {
            val token = depositedReceipt.token
            if (token == newToken) {
                val maliciousY = Cryptography.solve_for_y(depositedReceipt.gamma, receipt.gamma, depositedReceipt.challenge, receipt.challenge, p)
                val maliciousW = Cryptography.solve_for_w(token.u, maliciousY, depositedReceipt.gamma, depositedReceipt.challenge, p)
                val maliciousUser = registeredUserManager.getRegisteredUserByW(maliciousW)
                return "Double Spending detected! This is done by y: $maliciousY and w: $maliciousW, username ${maliciousUser!!.name}"
            }
        }
        depositedTokensManager.depositToken(receipt)
        return "Deposit was successful!"
    }

    fun getDepositedTokens(): List<Receipt> {
        return depositedTokensManager.getAllReceipts()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun handleSignUnsignedTokenRequest(userName: String, unsignedTokenSignRequestEntries: List<UnsignedTokenSignRequestEntry>): ArrayList<UnsignedTokenSignResponseEntry> {

        val responseList = arrayListOf<UnsignedTokenSignResponseEntry>()
        // TODO Error handling if user not found
        val user = registeredUserManager.getRegisteredUserByName(userName)

        for (unsignedTokenSignRequestEntry in unsignedTokenSignRequestEntries) {
            responseList.add(signToken(user!!, unsignedTokenSignRequestEntry))
        }

        return responseList
    }

    fun handleOnDeposit(receipts: List<Receipt>, userName: String, context: Context? = null): List<String> {
        // TODO better return data structure
        val results = arrayListOf<String>()
        for (receipt in receipts) {
            val result = depositToken(receipt)
            results.add(result)
            if (context != null) {
                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
            }
        }
        return results
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun signToken(user: RegisteredUser, unsignedToken: UnsignedTokenSignRequestEntry): UnsignedTokenSignResponseEntry {
        val (id, a, c) = unsignedToken
        val timeStamp = LocalDateTime.now().plusYears(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val timeStampString = timeStamp.format(formatter)
        val cPrime = (c * x + user.s).mod(q)
        val hash = CentralAuthority.H1(timeStampString)
        val aPrime = (a * hash).modPow(rsaParameters.d, rsaParameters.n)
        return UnsignedTokenSignResponseEntry(id, aPrime, cPrime, timeStampString, UnsignedTokenStatus.SIGNED)
    }
}
