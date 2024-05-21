package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahai
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import nl.tudelft.trustchain.offlineeuro.cryptography.RandomizationElements
import nl.tudelft.trustchain.offlineeuro.cryptography.Schnorr
import nl.tudelft.trustchain.offlineeuro.cryptography.SchnorrSignature
import nl.tudelft.trustchain.offlineeuro.cryptography.TransactionProof
import nl.tudelft.trustchain.offlineeuro.cryptography.TransactionProofBytes
import nl.tudelft.trustchain.offlineeuro.libraries.SchnorrSignatureSerializer
import kotlin.system.measureTimeMillis

data class TransactionDetailsBytes(
    val digitalEuroBytes: DigitalEuroBytes,
    val currentTransactionProofBytes: TransactionProofBytes,
    val previousThetaSignatureBytes: ByteArray,
    val theta1SignatureBytes: ByteArray,
    val spenderPublicKeyBytes: ByteArray,
) {
    fun toTransactionDetails(group: BilinearGroup): TransactionDetails {
        return TransactionDetails(
            digitalEuroBytes.toDigitalEuro(group),
            currentTransactionProofBytes.toTransactionProof(group),
            SchnorrSignatureSerializer.deserializeSchnorrSignatureBytes(previousThetaSignatureBytes),
            SchnorrSignatureSerializer.deserializeSchnorrSignatureBytes(theta1SignatureBytes)!!,
            group.gElementFromBytes(spenderPublicKeyBytes)
        )
    }
}

data class TransactionDetails(
    val digitalEuro: DigitalEuro,
    val currentTransactionProof: TransactionProof,
    val previousThetaSignature: SchnorrSignature?,
    val theta1Signature: SchnorrSignature,
    val spenderPublicKey: Element,
) {
    fun toTransactionDetailsBytes(): TransactionDetailsBytes {
        return TransactionDetailsBytes(
            digitalEuro.toDigitalEuroBytes(),
            currentTransactionProof.toTransactionProofBytes(),
            SchnorrSignatureSerializer.serializeSchnorrSignature(previousThetaSignature),
            SchnorrSignatureSerializer.serializeSchnorrSignature(theta1Signature),
            spenderPublicKey.toBytes()
        )
    }
}

object Transaction {
    val bilinearGroup = CentralAuthority.groupDescription
    val g = bilinearGroup.g
    val h = bilinearGroup.h

    fun createTransaction(
        privateKey: Element,
        publicKey: Element,
        walletEntry: WalletEntry,
        randomizationElements: RandomizationElements,
    ): TransactionDetails {
        val digitalEuro = walletEntry.digitalEuro

        val target =
            if (digitalEuro.proofs.isEmpty()) {
                CentralAuthority.groupDescription.pairing.zr.newElementFromBytes(digitalEuro.signature.toBytes())
            } else {
                val targetBytes = digitalEuro.proofs.last().target.toBytes()
                CentralAuthority.groupDescription.pairing.zr.newElementFromBytes(targetBytes)
            }
        val (transactionProof, r) =
            GrothSahai.createTransactionProof(
                privateKey,
                publicKey,
                target,
                walletEntry.t,
                randomizationElements
            )

        val theta1Signature = Schnorr.schnorrSignature(r, randomizationElements.group1TInv.toBytes(), bilinearGroup)
        val previousThetaSignature = walletEntry.transactionSignature
        return TransactionDetails(digitalEuro, transactionProof, previousThetaSignature, theta1Signature, publicKey)
    }

    fun validate(
        transaction: TransactionDetails,
        publicKeyBank: Element
    ): Boolean {
        // Verify if the Digital euro is signed
        val digitalEuro = transaction.digitalEuro
        val timeInMillis =
            measureTimeMillis {
                if (!digitalEuro.verifySignature(publicKeyBank, bilinearGroup)) {
                    return false
                }
            }
        // println("Bank signature verification $timeInMillis")

        // Verify if the current transaction signature is correct
        val transactionProof = transaction.currentTransactionProof

        val transactionSignatureTime =
            measureTimeMillis {
                val transactionSignature = transaction.theta1Signature
                if (!Schnorr.verifySchnorrSignature(transactionSignature, transactionProof.grothSahaiProof.c1, bilinearGroup)) {
                    return false
                }
            }
        // println("Transaction signature verification $transactionSignatureTime")

        val grothProofValidation =
            measureTimeMillis {
                // Validate if the given Transaction proof is a valid Groth-Sahai proof
                if (!GrothSahai.verifyTransactionProof(transactionProof.grothSahaiProof)) {
                    return false
                }
            }
        // println("Groth proof verification $grothProofValidation")

        // Validate that d2 is constructed correctly
        val usedY = transactionProof.usedY
        val usedVS = transactionProof.usedVS
        val d2 = transactionProof.grothSahaiProof.d2

        if (d2.div(usedY) != usedVS) {
            return false
        }

        // Validate if the public key is used correctly
        val spenderPublicKey = transaction.spenderPublicKey
        val expectedTarget = bilinearGroup.pair(spenderPublicKey, usedY)

        if (expectedTarget != transactionProof.grothSahaiProof.target) {
            return false
        }

        var result = false

        val chainValidation =
            measureTimeMillis {
                result = validateProofChain(transaction)
            }

        println("$timeInMillis, $transactionSignatureTime, $grothProofValidation, $chainValidation")
        return result
    }

    fun validateProofChain(currentTransactionDetails: TransactionDetails): Boolean {
        val digitalEuro = currentTransactionDetails.digitalEuro
        val currentProof = currentTransactionDetails.currentTransactionProof
        val previousProofs = digitalEuro.proofs

        // Current proof is the first proof
        if (previousProofs.isEmpty()) {
            return validateTSRelation(digitalEuro.firstTheta1, currentProof.grothSahaiProof.d1)
        }

        // Special cases for the first proof
        val firstProof = previousProofs.first()

        if (!validateTSRelation(digitalEuro.firstTheta1, firstProof.d1) || !GrothSahai.verifyTransactionProof(firstProof)) {
            return false
        }

        // Verify the remainder of the chain
        var previousProof = firstProof
        for (i: Int in 1 until previousProofs.size) {
            val proof = previousProofs[i]
            GrothSahai.verifyTransactionProof(proof)
            if (!verifyProofChain(previousProof, proof)) {
                return false
            }
            previousProof = proof
        }

        // Verify if the signature of the last transaction is valid
        val usedC1 = previousProof.c1
        val usedTheta = previousProof.theta1

        val previousTransactionProof = currentTransactionDetails.previousThetaSignature
        if (previousTransactionProof == null ||
            !Schnorr.verifySchnorrSignature(previousTransactionProof, usedC1, bilinearGroup) ||
            !usedTheta.toBytes().contentEquals(previousTransactionProof.signedMessage)
        ) {
            return false
        }
        // Verify the proof of the transaction to the last proof in the chain
        return verifyProofChain(previousProof, currentProof.grothSahaiProof)
    }

    fun verifyProofChain(
        previousProof: GrothSahaiProof,
        currentProof: GrothSahaiProof
    ): Boolean {
        val previousTargetToBytes = previousProof.target.toCanonicalRepresentation()
        val previousTargetAsPower = bilinearGroup.pairing.zr.newElementFromBytes(previousTargetToBytes)
        val expectedTarget = bilinearGroup.pair(g, h).powZn(previousTargetAsPower)

        if (expectedTarget != currentProof.target) {
            return false
        }

        // Check if the relation with t and s is correct
        val previousTheta1 = previousProof.theta1
        val currentD1 = currentProof.d1
        return validateTSRelation(previousTheta1, currentD1)
    }

    fun validateTSRelation(
        theta: Element,
        s: Element
    ): Boolean {
        val expectedPairing = bilinearGroup.pair(g, h)
        val actualPairing = bilinearGroup.pair(theta, s)
        return expectedPairing == actualPairing
    }
}
