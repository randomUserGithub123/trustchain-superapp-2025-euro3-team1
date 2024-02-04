package nl.tudelft.trustchain.offlineeuro.entity

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import nl.tudelft.trustchain.offlineeuro.community.OfflineEuroCommunity
import nl.tudelft.trustchain.offlineeuro.db.BankRegistrationManager
import nl.tudelft.trustchain.offlineeuro.db.OwnedTokenManager
import nl.tudelft.trustchain.offlineeuro.db.ReceiptManager
import nl.tudelft.trustchain.offlineeuro.db.UnsignedTokenManager
import nl.tudelft.trustchain.offlineeuro.libraries.Cryptography
import java.math.BigInteger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class CommunicationState {
    INPROGRESS,
    COMPLETE,
    FAILED
}

class User (
    var name: String,
    private val context: Context?,
    private val ownedTokenManager: OwnedTokenManager = OwnedTokenManager(context),
    private val bankRegistrationManager: BankRegistrationManager = BankRegistrationManager(context),
    private val unsignedTokenManager: UnsignedTokenManager = UnsignedTokenManager(context),
    private val receiptManager: ReceiptManager = ReceiptManager(context)
)
{

    // Values of the CA for easier reference
    private val p = CentralAuthority.p
    private val q = CentralAuthority.q
    private val alpha = CentralAuthority.alpha

    // TODO Cleaner solution for this
    var communicationState: CommunicationState = CommunicationState.COMPLETE

    var keepToken = true
    fun computeI(bankDetails: BankDetails, m: BigInteger, rm: BigInteger): Pair<BigInteger, BigInteger> {
        // I = (H1(m||a^r_m),m)^e_b) (mod n_b)
        val arm = alpha.modPow(rm, bankDetails.nb)
        val concat = m.toString() + arm.toString()
        val hash = CentralAuthority.H1(concat).modPow(bankDetails.eb, bankDetails.nb)
        val meb = m.modPow(bankDetails.eb, bankDetails.nb)
        return Pair(hash, meb)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun registerWithBank(bankName: String, community: OfflineEuroCommunity, userName: String): Boolean {
        communicationState = CommunicationState.INPROGRESS

        val bank = bankRegistrationManager.getBankRegistrationByName(bankName)

        if (bank == null) {
            communicationState = CommunicationState.FAILED
            return false
        }

        val m: BigInteger = Cryptography.generateRandomBigInteger(p)
        val rm: BigInteger = Cryptography.generateRandomBigInteger(p)

        val i = computeI(bank.bankDetails, m, rm)

        // Send the message (I, (alpha^r_m mod p)),
        val arm = alpha.modPow(rm, p)
        val registrationMessage = UserRegistrationMessage(userName, i, arm)
        if (!bankRegistrationManager.setOwnValuesForBank(bankName, m, rm)) {
            communicationState = CommunicationState.FAILED
            return false

        }

        return community.sendUserRegistrationMessage(registrationMessage, bank.bankDetails.publicKeyBytes)
    }

    fun handleRegistrationResponse(userRegistrationResponseMessage: UserRegistrationResponseMessage): Boolean {
        if (userRegistrationResponseMessage.result == MessageResult.Failed)
            //TODO display error message
            return false

        val bankName = userRegistrationResponseMessage.bankName
        val v = userRegistrationResponseMessage.v
        val r = userRegistrationResponseMessage.r
        val result: Boolean = bankRegistrationManager.setBankRegistrationValues(bankName, v, r)

        communicationState = if (result)
            CommunicationState.COMPLETE
        else
            CommunicationState.FAILED
        return result
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun withdrawToken(bankName: String, community: OfflineEuroCommunity) {
        communicationState = CommunicationState.INPROGRESS
        val bank = bankRegistrationManager.getBankRegistrationByName(bankName)

        if (bank?.m == null || bank.rm == null || bank.v == null) {
            CommunicationState.FAILED
            return
        }
        val nb = bank.bankDetails.nb
        val eb = bank.bankDetails.eb
        val r = bank.r
        val v = bank.v

        // Generate the random values
        var y = Cryptography.generateRandomBigInteger(BigInteger.ONE, p)
        var u = CentralAuthority.alpha.modPow(y, p)

        var l = Cryptography.generateRandomBigInteger(BigInteger.ONE, p)

        val e = Cryptography.generateRandomBigInteger(BigInteger.ONE, (p / BigInteger("4")))
        var beta1 = Cryptography.generateRandomBigInteger(BigInteger.ONE, p)
        val beta2 = Cryptography.generateRandomBigInteger(BigInteger.ONE, p)

        // Conditions on the values
        // Required: gdc(y,p-1) = 1
        while (y.gcd(p - BigInteger.ONE) != BigInteger.ONE ||
            u.gcd(p - BigInteger.ONE) != BigInteger.ONE) {
            y = Cryptography.generateRandomBigInteger(BigInteger.ONE, p)
            u = alpha.modPow(y, p)
        }

        // Required: gdc(l,nb) = 1
        while (l.gcd(nb) != BigInteger.ONE)
            l = Cryptography.generateRandomBigInteger(BigInteger.ONE, p)

        // Required: gdc(beta1,q) = 1
        while (beta1.gcd(q) != BigInteger.ONE)
            beta1 = Cryptography.generateRandomBigInteger(BigInteger.ONE, p)

        // Computations

        //TODO Find out how to make it work with e
        //w = BigInteger(r.toString() + e.toString())
        val w = BigInteger(r.toString())

        val g = alpha.modPow(w, p)
        val bigA =  ((v.modPow(beta1, p)) * (alpha.modPow(beta2, p))).mod(p)
        val beta1Inv = beta1.modInverse(q)
        val hash = CentralAuthority.H(u, g, bigA)
        val c = (beta1Inv * hash).mod(q)
        val a = (bigA * l.modPow(eb, nb)).mod(nb)

        val unsignedTokenToAdd = UnsignedTokenAdd(a, c, bigA, beta1, beta2, l, u, g, y, w, bank.id)
        val tokenId = unsignedTokenManager.addUnsignedToken(unsignedTokenToAdd)

        // TODO Put in loop to send more than one at the same time
        val signRequest = UnsignedTokenSignRequestEntry(tokenId, a, c)
        val tokensToSign = arrayListOf(signRequest)
        community.sendUnsignedTokens(tokensToSign, bank.bankDetails.publicKeyBytes)
    }

    fun handleUnsignedTokenSignResponse(bankName: String,
        unsignedTokenSignResponseEntries: List<UnsignedTokenSignResponseEntry>) {
        val bank = bankRegistrationManager.getBankRegistrationByName(bankName) ?: return

        if (bank.m == null || bank.rm == null || bank.v == null)
        // TODO User should register first error
            return

        val nb = bank.bankDetails.nb

        for (unsignedTokenSignResponseEntry in unsignedTokenSignResponseEntries) {
            val (id, aPrime, cPrime, t, status) = unsignedTokenSignResponseEntry

            if (status == UnsignedTokenStatus.REJECTED) {
                // TODO Consider what to do if a token is rejected
                unsignedTokenManager.updateUnsignedTokenStatusById(status, id)
                continue
            }

            val unsignedToken = unsignedTokenManager.getUnsignedTokenById(id)
            val bigA = unsignedToken.bigA
            val beta1 = unsignedToken.beta1
            val beta2 = unsignedToken.beta2
            val l = unsignedToken.l
            val u = unsignedToken.u
            val g = unsignedToken.g
            val w = unsignedToken.w
            val y = unsignedToken.y

            val r2 = (beta1 * cPrime + beta2).mod(q)
            val aDoublePrime = (l.modInverse(nb) * aPrime).mod(nb)
            val signedToken = Token(u, g, bigA, r2, aDoublePrime, t)

            ownedTokenManager.addToken(signedToken, w, y, unsignedToken.bankId)
            // TODO Batch?
            unsignedTokenManager.updateUnsignedTokenStatusById(UnsignedTokenStatus.SIGNED, id)
            communicationState = CommunicationState.COMPLETE
        }
    }

    fun checkReceivedToken(token: Token, bankName: String) : Boolean {
        val bank = bankRegistrationManager.getBankRegistrationByName(bankName) ?: return false

        if (bank.m == null || bank.rm == null || bank.v == null)
        // TODO User should register first error
            return false

        val nb = bank.bankDetails.nb
        val eb = bank.bankDetails.eb
        val z = bank.bankDetails.z

        // AH1(t) ?= A"^e_b mod nb
        val checkOne = (token.a * CentralAuthority.H1(token.t)).mod(nb) == token.aPrime.modPow(eb, nb)

        //a^r = Az^(H(u,g,A)) mod p
        val zhash = z.modPow(CentralAuthority.H(token.u, token.g, token.a), CentralAuthority.p)
        val checkTwo = CentralAuthority.alpha.modPow(token.r, CentralAuthority.p) == (token.a * zhash).mod(CentralAuthority.p)
        return checkOne && checkTwo
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun sendTokenToRandomPeer(community: OfflineEuroCommunity, keepToken: Boolean = false) {
        communicationState = CommunicationState.INPROGRESS
        this.keepToken = keepToken
        val tokenToSent = getTokens().first()
        val tokensToSent = arrayListOf<Token>(tokenToSent.token)
        val bank = bankRegistrationManager.getBankById(tokenToSent.bankId)!!
        community.sendTokensToRandomPeer(tokensToSent, bank)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun onTokensReceived(tokens: List<Token>, bankName: String) : List<Challenge> {
        val challenges = arrayListOf<Challenge>()
        val bank = bankRegistrationManager.getBankRegistrationByName(bankName)

        // Check if you are registered to this bank
        if (bank?.m == null || bank.rm == null || bank.v == null) {
            return arrayListOf()
        }
        // TODO State per token
        val myI = computeI(bank.bankDetails, bank.m, bank.rm)
        for (token in tokens) {
            if (!checkReceivedToken(token, bankName)) {
                return arrayListOf()
            }
            val challenge = computeChallenge(token, myI.first)
            challenges.add(Challenge(token, challenge))
        }
        return challenges
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun computeChallenge(token: Token, merchantID: BigInteger): BigInteger {
        val timeStamp = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        return CentralAuthority.H0(token.u, token.g, merchantID, timeStamp.format(formatter))
    }

    fun onChallenges(challenges: List<Challenge>, bankName: String, doubleSpend: Boolean = false): List<ChallengeResponse> {
        val responses = arrayListOf<ChallengeResponse>()
        for (challenge in challenges) {
            val response = resolveChallenge(challenge, keepToken)
            responses.add(response)
        }

        CommunicationState.COMPLETE
        return responses
    }

    fun resolveChallenge(challenge: Challenge, doubleSpend: Boolean = false): ChallengeResponse {
        val tokenList = getTokens()
        val challengedToken = challenge.token
        val challengeValue = challenge.challenge
        for (storedToken in tokenList) {
            if (storedToken.token == challengedToken) {
                val w = storedToken.w
                val u = challengedToken.u
                val y = storedToken.y
                val gamma = Cryptography.solve_for_gamma(w, u, y, challengeValue, p)
                if (!doubleSpend)
                    ownedTokenManager.removeTokenById(storedToken.id)
                return ChallengeResponse(challengedToken, challengeValue, gamma)
            }
        }

        // Token is not found
        return ChallengeResponse(challengedToken, challengeValue, BigInteger.ZERO)
    }

    fun onChallengesResponseReceived(challenges: List<ChallengeResponse>, bankName: String): List<ReceiptEntry> {

        val receipts = arrayListOf<ReceiptEntry>()
        val bankRegistration: BankRegistration = bankRegistrationManager.getBankRegistrationByName(bankName)?: return receipts
        val bankId = bankRegistration.id
        for (challengeResponse in challenges) {
            val (token, challenge, gamma) = challengeResponse
            if (verifyChallengeResponse(token, gamma, challenge)) {
                val receipt = ReceiptEntry(token, challenge, gamma, bankId)
                receiptManager.addReceipt(receipt)
                receipts.add(ReceiptEntry(token, challenge, gamma, bankId))
            }
        }

        return receipts
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun depositReceiptsAtBank(bankName: String, community: OfflineEuroCommunity) {
        // TODO Possible join?
        val bank = bankRegistrationManager.getBankRegistrationByName(bankName)?: return
        val receipts = receiptManager.getAllReceiptsByBankId(bank.id)
        val message = receipts.map { Receipt(it.token, it.gamma, it.challenge) }
        community.sendReceiptsToBank(message, name, bank.bankDetails.publicKeyBytes)
    }
    fun verifyChallengeResponse(token: Token, gamma: BigInteger, challenge: BigInteger): Boolean {
        val g = token.g
        val u = token.u
        val alpha = CentralAuthority.alpha
        val p = CentralAuthority.p
        val gu = g.modPow(u, p)
        val uGamma = u.modPow(gamma, p)
        val leftSide = (gu * uGamma).mod(p)
        val ad = alpha.modPow(challenge, p)

        return leftSide == ad
    }
    fun getBalance(): Int {
        return ownedTokenManager.getAllTokens().size
    }

    fun getTokens(): List<TokenEntry> {
        return ownedTokenManager.getAllTokens()
    }

    fun handleBankDetailsReply(bankDetails: BankDetails) {
        bankRegistrationManager.addNewBank(bankDetails)
    }

    fun getBankRegistrationByName(bankName: String): BankRegistration? {
        return bankRegistrationManager.getBankRegistrationByName(bankName)
    }

    fun getBankRegistrations(): List<BankRegistration> {
        return bankRegistrationManager.getBankRegistrations()
    }

    fun getReceipts(): List<ReceiptEntry> {
        return receiptManager.getAllReceipts()
    }

    fun removeAllTokens() {
        ownedTokenManager.removeAllTokens()
    }
}
