package nl.tudelft.trustchain.offlineeuro.entity

import android.os.Build
import androidx.annotation.RequiresApi
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import java.math.BigInteger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class User {

    // Private Values
    private var rsaParameters: RSAParameters
    private var I: Pair<BigInteger?, BigInteger?> = Pair(null, null)
    private var tokenList: ArrayList<Pair<Token, Triple<BigInteger, BigInteger, BigInteger>>> = arrayListOf()

    // TODO randomize
    private val m: BigInteger = BigInteger("42142132")
    private val rm: BigInteger = BigInteger("4")

    // Public RSA values of the bank
    private var eb: BigInteger = BigInteger.ZERO
    private var nb: BigInteger = BigInteger.ZERO

    val banks: ArrayList<Pair<String, Peer>> = arrayListOf()

    // Variables after registering
    private lateinit var v: BigInteger
    private lateinit var r: BigInteger

    lateinit var w: BigInteger
    lateinit var y: BigInteger
    init {
        rsaParameters = Cryptography.generateRSAParameters(2048)
    }

    /***
     * For now input the bank, later find a bank through IP-v8
     * TODO Bank with IP-v8
     */
    fun findBank(bank: Bank) {
        val (eb, nb) = bank.getPublicRSAValues()
        this.eb = eb
        this.nb = nb
    }
    fun computeI() {
        // I = (H1(m||a^r_m),m)^e_b) (mod n_b)
        val arm = CentralAuthority.alpha.modPow(rm, nb)
        val concat = m.toString() + arm.toString()
        val hash = CentralAuthority.H1(concat)
        val hasheb = hash.modPow(eb, nb)
        val meb = hash.modPow(eb, nb)
        I = Pair(hasheb, meb)
    }

    fun registerWithBank(bank: Bank) {

        if (eb == BigInteger.ZERO || nb == BigInteger.ZERO)
            findBank(bank)

        // Send the message (I, (alpha^r_m mod p)),

        if (I.first == null || I.second == null ) {
            computeI()
        }
        val arm = CentralAuthority.alpha.modPow(rm, CentralAuthority.p)
        val message = Pair(I, arm)
        val (v, r) = bank.registerUser(message)
        this.v = v
        this.r = r
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
