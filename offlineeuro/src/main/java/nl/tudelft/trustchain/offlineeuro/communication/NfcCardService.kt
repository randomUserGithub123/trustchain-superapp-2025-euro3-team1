package nl.tudelft.trustchain.offlineeuro.communication

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import it.unisa.dia.gas.jpbc.Element

import nl.tudelft.trustchain.offlineeuro.entity.Participant
import nl.tudelft.trustchain.offlineeuro.entity.Bank
import nl.tudelft.trustchain.offlineeuro.entity.User
import nl.tudelft.trustchain.offlineeuro.entity.TTP

import nl.tudelft.trustchain.offlineeuro.ui.ParticipantHolder

import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetailsBytes
import nl.tudelft.trustchain.offlineeuro.entity.TransactionDetails

import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElementsBytes

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigInteger

class NfcCardService : HostApduService() {

    private val TAG = "NfcCardService"

    private var bankPublicKey: Element? = null

    private fun getCurrentParticipant(): Participant? {
        return ParticipantHolder.user ?: ParticipantHolder.bank ?: ParticipantHolder.ttp
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (ApduConstants.isSelectAidCommand(commandApdu)) {
            return ApduConstants.SW_SUCCESS
        }

        if (commandApdu.size < 5) {
            Log.e(TAG, "APDU too short (size=${commandApdu.size})")
            return ApduConstants.SW_FAILURE
        }
        val ins = commandApdu[1]
        val lc  = (commandApdu[4].toInt() and 0xFF)

        if (commandApdu.size < 5 + lc) {
            Log.e(TAG, "APDU length mismatch: declared Lc=$lc, actual data=${commandApdu.size - 5}")
            return ApduConstants.SW_FAILURE
        }
        val payload = commandApdu.copyOfRange(5, 5 + lc)

        try {
            val participant = getCurrentParticipant() ?: return ApduConstants.SW_FAILURE

            when (ins) {
                
                ApduConstants.INS_GET_PUBLIC_KEY -> {
                    val pubKeyBytes = participant.publicKey.toBytes()
                    return pubKeyBytes + ApduConstants.SW_SUCCESS
                }

                ApduConstants.INS_BLIND_RANDOMNESS_REQUEST -> {
                    if (participant !is Bank) {
                        Log.e(TAG, "INS_BLIND_RANDOMNESS_REQUEST received on non‐Bank")
                        return ApduConstants.SW_FAILURE
                    }
                    val group = participant.group
                    val publicKeyBytes = payload
                    val publicKey = group.gElementFromBytes(publicKeyBytes)
                    val randomness = participant.getBlindSignatureRandomness(publicKey)
                    val randomnessBytes = randomness.toBytes()
                    return randomnessBytes + ApduConstants.SW_SUCCESS
                }

                ApduConstants.INS_BLIND_SIGNATURE_CHALLENGE -> {
                    if (participant !is Bank) {
                        Log.e(TAG, "INS_BLIND_SIGNATURE_CHALLENGE received on non‐Bank")
                        return ApduConstants.SW_FAILURE
                    }
                    val group = participant.group
                    if (payload.size < 33) {
                        Log.e(TAG, "Payload too short for BLIND_SIGNATURE_CHALLENGE")
                        return ApduConstants.SW_FAILURE
                    }
                    val pkLen = payload.size - 32
                    val publicKeyBytes = payload.copyOfRange(0, pkLen)
                    val challengeBytes = payload.copyOfRange(pkLen, payload.size)
                    val publicKey = group.gElementFromBytes(publicKeyBytes)
                    val challenge = BigInteger(1, challengeBytes)
                    val signature = participant.createBlindSignature(challenge, publicKey)
                    val signatureBytes = signature.toByteArray()
                    return signatureBytes + ApduConstants.SW_SUCCESS
                }

                ApduConstants.INS_TRANSACTION_RANDOMNESS_REQUEST -> {
                    if (participant !is User) {
                        Log.e(TAG, "INS_TRANSACTION_RANDOMNESS_REQUEST received on non‐User")
                        return ApduConstants.SW_FAILURE
                    }
                    val user = participant as User
                    val group = user.group
                    if (payload.size % 2 != 0) {
                        Log.e(TAG, "Payload length not divisible by 2 for TRANSACTION_RANDOMNESS_REQUEST")
                        return ApduConstants.SW_FAILURE
                    }
                    val half = payload.size / 2
                    val senderPubBytes = payload.copyOfRange(0, half)
                    val bankPubBytes   = payload.copyOfRange(half, payload.size)
                    val senderPubKey = group.gElementFromBytes(senderPubBytes)
                    bankPublicKey = group.gElementFromBytes(bankPubBytes)

                    val randElements = user.generateRandomizationElements(senderPubKey)
                    val randWrapper: RandomizationElementsBytes = randElements.toRandomizationElementsBytes()

                    val baos = ByteArrayOutputStream()
                    val oos = ObjectOutputStream(baos)
                    oos.writeObject(randWrapper)
                    oos.flush()
                    val randBytesRaw = baos.toByteArray()

                    return randBytesRaw + ApduConstants.SW_SUCCESS
                }

                ApduConstants.INS_TRANSACTION_DETAILS -> {
                    val group = participant.group
                    val examplePub = participant.publicKey
                    val pubLen = examplePub.toBytes().size

                    if (payload.size < pubLen + 1) {
                        Log.e(TAG, "Payload too short for TRANSACTION_DETAILS")
                        return ApduConstants.SW_FAILURE
                    }
                    val senderPubBytes = payload.copyOfRange(0, pubLen)
                    val txDetailsRaw   = payload.copyOfRange(pubLen, payload.size)
                    val senderPubKey = group.gElementFromBytes(senderPubBytes)

                    val bais = ByteArrayInputStream(txDetailsRaw)
                    val ois = ObjectInputStream(bais)
                    val txDetailsBytesObj = ois.readObject() as TransactionDetailsBytes

                    val bankPub = when (participant) {
                        is Bank -> participant.publicKey
                        is User -> bankPublicKey ?: return ApduConstants.SW_FAILURE
                        else -> {
                            Log.e(TAG, "TTP should not receive TRANSACTION_DETAILS")
                            return ApduConstants.SW_FAILURE
                        }
                    }

                    val result = participant.onReceivedTransaction(
                        txDetailsBytesObj.toTransactionDetails(group),
                        bankPub,
                        senderPubKey
                    )

                    val resultBytes = result.toByteArray(Charsets.UTF_8)
                    return resultBytes + ApduConstants.SW_SUCCESS
                }

                else -> {
                    Log.e(TAG, "Unknown INS byte: 0x${ins.toInt().and(0xFF).toString(16)}")
                    return ApduConstants.SW_FAILURE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in processCommandApdu: ${e.message}", e)
            return ApduConstants.SW_FAILURE
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "HCE Deactivated (reason = $reason)")
    }
}
