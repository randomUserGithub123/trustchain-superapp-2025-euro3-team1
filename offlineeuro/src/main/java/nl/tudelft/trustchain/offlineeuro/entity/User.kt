package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.db.BankRegistrationManager
import nl.tudelft.trustchain.offlineeuro.db.OwnedTokenManager
import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import java.math.BigInteger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class User (
    private val context: Context,
    val ownedTokenManager: OwnedTokenManager = OwnedTokenManager(context),
    val bankRegistrationManager: BankRegistrationManager = BankRegistrationManager(context)
)
{

    // Private Values
    private var rsaParameters: RSAParameters
    private lateinit var I: Pair<BigInteger, BigInteger>
    private var tokenList: ArrayList<Pair<Token, Triple<BigInteger, BigInteger, BigInteger>>> = arrayListOf()

    // Values of the CA for easier reference
    private val p = CentralAuthority.p
    private val q = CentralAuthority.q
    private val alpha = CentralAuthority.alpha

    private val m: BigInteger = Cryptography.generateRandomBigInteger(p)
    private val rm: BigInteger = Cryptography.generateRandomBigInteger(p)

    // Public RSA values of the bank
    private var eb: BigInteger = BigInteger.ZERO
    private var nb: BigInteger = BigInteger.ZERO

    // Variables after registering
    private lateinit var v: BigInteger
    private lateinit var r: BigInteger

    lateinit var w: BigInteger
    lateinit var y: BigInteger
    init {
        rsaParameters = Cryptography.generateRSAParameters(2048)
    }

    fun computeI(bankDetails: BankDetails) {
        // I = (H1(m||a^r_m),m)^e_b) (mod n_b)
        val arm = alpha.modPow(rm, bankDetails.nb)
        val concat = m.toString() + arm.toString()
        val hash = CentralAuthority.H1(concat).modPow(bankDetails.eb, bankDetails.nb)
        val meb = m.modPow(bankDetails.eb, bankDetails.nb)
        I = Pair(hash, meb)
    }

    fun registerWithBank(userName: String, bankName: String, community: OfflineEuroCommunity) {
        val bank = bankRegistrationManager.getBankRegistrationByName(bankName)?: return

        computeI(bank.bankDetails)

        // Send the message (I, (alpha^r_m mod p)),
        val arm = alpha.modPow(rm, p)
        val registrationMessage = UserRegistrationMessage(userName, I, arm)
        bankRegistrationManager.setOwnValuesForBank(bankName, m, rm)
        community
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun withdrawToken(bank: Bank) {

        // Generate the random values
        y = Cryptography.generateRandomBigInteger(BigInteger.ONE, CentralAuthority.p)
        var u = CentralAuthority.alpha.modPow(y, CentralAuthority.p)

        var l = Cryptography.generateRandomBigInteger(BigInteger.ONE, CentralAuthority.p)

        val e = Cryptography.generateRandomBigInteger(BigInteger.ONE, (CentralAuthority.p / BigInteger("4")))
        var beta1 = Cryptography.generateRandomBigInteger(BigInteger.ONE, CentralAuthority.p)
        val beta2 = Cryptography.generateRandomBigInteger(BigInteger.ONE, CentralAuthority.p)

        // Conditions on the values
        // Required: gdc(y,p-1) = 1
        while (y.gcd(CentralAuthority.p - BigInteger.ONE) != BigInteger.ONE ||
            u.gcd(CentralAuthority.p - BigInteger.ONE) != BigInteger.ONE) {
            y = Cryptography.generateRandomBigInteger(BigInteger.ONE, CentralAuthority.p)
            u = CentralAuthority.alpha.modPow(y, CentralAuthority.p)
        }

        // Required: gdc(l,nb) = 1
        while (l.gcd(nb) != BigInteger.ONE)
            l = Cryptography.generateRandomBigInteger(BigInteger.ONE, CentralAuthority.p)

        // Required: gdc(beta1,q) = 1
        while (beta1.gcd(CentralAuthority.q) != BigInteger.ONE)
            beta1 = Cryptography.generateRandomBigInteger(BigInteger.ONE, CentralAuthority.p)

        // Computations

        //TODO Find out how to make it work with e
        //w = BigInteger(r.toString() + e.toString())
        w = BigInteger(r.toString())

        val g = CentralAuthority.alpha.modPow(w, CentralAuthority.p)
        val A =  ((v.modPow(beta1, CentralAuthority.p)) * (CentralAuthority.alpha.modPow(beta2, CentralAuthority.p))).mod(CentralAuthority.p)
        val beta1Inv = beta1.modInverse(CentralAuthority.q)
        val hash = CentralAuthority.H(u, g, A)
        val c = (beta1Inv * hash).mod(CentralAuthority.q)
        val a = (A * l.modPow(eb, nb)).mod(nb)

        val (APrime, cPrime, t) = bank.signToken(Pair(a, c))

        val r2 = (beta1 * cPrime + beta2).mod(CentralAuthority.q)
        val ADoublePrime = (l.modInverse(nb) * APrime).mod(nb)
        val signedToken = Token(u, g, A, r2, ADoublePrime, t)
        tokenList.add(Pair(signedToken, Triple(w, u, y)))

    }

    fun checkReceivedToken(token: Token, bank: Bank) : Boolean {
        // AH1(t) ?= A"^e_b mod nb
        val checkOne = (token.A * CentralAuthority.H1(token.t)).mod(nb) == token.ADoublePrime.modPow(eb, nb)

        //a^r = Az^(H(u,g,A)) mod p
        val zhash = bank.z.modPow(CentralAuthority.H(token.u, token.g, token.A), CentralAuthority.p)
        val checkTwo = CentralAuthority.alpha.modPow(token.r, CentralAuthority.p) == (token.A * zhash).mod(CentralAuthority.p)
        return checkOne && checkTwo
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun onTokenReceived(token: Token, bank: Bank, merchantID: BigInteger) : BigInteger? {
        if (!checkReceivedToken(token, bank)) {
            return null
        }
        return computeChallenge(token, merchantID)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun computeChallenge(token: Token, merchantID: BigInteger): BigInteger {
        val timeStamp = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        return CentralAuthority.H0(token.u, token.g, merchantID, timeStamp.format(formatter))
    }

    fun onChallengeReceived(d: BigInteger, token: Token): BigInteger {

        var randomVars: Triple<BigInteger, BigInteger, BigInteger> = Triple(BigInteger.ZERO,BigInteger.ZERO, BigInteger.ZERO)

        for (storedToken in tokenList) {
            if (storedToken.first == token)
                randomVars = storedToken.second
        }
        val (w, u, y) = randomVars
        val gamma = Cryptography.solve_for_gamma(w, u, y, d, CentralAuthority.p)
        return gamma
    }

    fun onChallengeResponseReceived(token: Token, gamma: BigInteger, challenge: BigInteger): Receipt? {
        if (verifyChallengeResponse(token, gamma, challenge)) {
            // TODO store information
            return Receipt(token, gamma, challenge)
        }
        return null
    }

    fun verifyChallengeResponse(token: Token, gamma: BigInteger, challenge: BigInteger): Boolean {
        val g = token.g
        val u = token.u
        val alpha = CentralAuthority.alpha
        val p = CentralAuthority.p
        val gu = g.modPow(u, p)
        val ugamma = u.modPow(gamma, p)
        val leftside = (gu * ugamma).mod(p)
        val ad = alpha.modPow(challenge, p)

        return leftside == ad
    }
    fun getBalance(): Int {
        return tokenList.size
    }

    fun getTokens(): ArrayList<Pair<Token, Triple<BigInteger, BigInteger, BigInteger>>> {
        return tokenList
    }
}
