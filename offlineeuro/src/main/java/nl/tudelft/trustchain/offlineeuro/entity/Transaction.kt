package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element


data class TransactionDetails (
    val digitalEuro: DigitalEuro,
    val currentTransactionProof: TransactionProof,
    val spenderPublicKey: Element,
)

object Transaction {

    val bilinearGroup = CentralAuthority.groupDescription
    val g = bilinearGroup.g
    val h = bilinearGroup.h

    fun createTransaction (
        privateKey: Element,
        publicKey: Element,
        walletEntry: WalletEntry,
        randomizationElements: RandomizationElements
    ): TransactionDetails {

        val digitalEuro = walletEntry.digitalEuro

        val target = if (digitalEuro.proofs.isEmpty()) {
            CentralAuthority.groupDescription.pairing.zr.newElementFromBytes(digitalEuro.signature.toByteArray())
        } else
        {
            val targetBytes = digitalEuro.proofs.last().target.toCanonicalRepresentation()
            CentralAuthority.groupDescription.pairing.zr.newElementFromBytes(targetBytes)
        }
        val transactionProof = GrothSahai.createTransactionProof(
            privateKey,
            publicKey,
            target,
            walletEntry.t,
            randomizationElements
        )

        return TransactionDetails(digitalEuro, transactionProof, publicKey)
    }


    fun validate(transaction: TransactionDetails): Boolean {

        val transactionProof = transaction.currentTransactionProof

        // Validate if the given Transaction proof is a valid Groth-Sahai proof
        if (!GrothSahai.verifyTransactionProof(transactionProof)) {
            return false
        }

        // Validate that d2 is constructed correctly
        val usedY = transactionProof.usedY
        val usedVS = transactionProof.usedVS
        val d2 = transactionProof.grothSahaiProof.d2

        if (d2.div(usedY) != usedVS)
            return false

        // Validate if the public key is used correctly
        val spenderPublicKey = transaction.spenderPublicKey
        val expectedTarget = bilinearGroup.pair(spenderPublicKey, usedY)

        if (expectedTarget != transactionProof.grothSahaiProof.target)
            return false


        return validateProofChain(transaction)
    }

    fun validateProofChain(currentTransactionDetails: TransactionDetails) : Boolean {

        val digitalEuro = currentTransactionDetails.digitalEuro
        val currentProof = currentTransactionDetails.currentTransactionProof
        val previousProofs = digitalEuro.proofs

        // Current proof is the first proof
        if (previousProofs.isEmpty()) {
            return validateTSRelation(digitalEuro.firstTheta1, currentProof.grothSahaiProof.d1)
        }

        // Special case for the first proof
        val firstProof = previousProofs.first()

        if (!validateTSRelation(digitalEuro.firstTheta1, firstProof.d1))
            return false


        // Verify the remainder of the chain
        var previousProof = firstProof
        for (i: Int in 1 until previousProofs.size) {
            val proof = previousProofs[i]
            if(!verifyProofChain(previousProof, proof))
                return false
            previousProof = proof
        }

        // Verify the proof of the transaction to the last proof in the chain
        return verifyProofChain(previousProof, currentProof.grothSahaiProof)
    }

    fun verifyProofChain(previousProof: GrothSahaiProof, currentProof: GrothSahaiProof): Boolean {
        val previousTargetToBytes = previousProof.target.toCanonicalRepresentation()
        val previousTargetAsPower = bilinearGroup.pairing.zr.newElementFromBytes(previousTargetToBytes)
        val expectedTarget = bilinearGroup.pair(g, h).powZn(previousTargetAsPower)

        if (expectedTarget != currentProof.target)
            return false

        // Check if the relation with t and s is correct
        val previousTheta1 = previousProof.theta1
        val currentD1 = currentProof.d1
        return validateTSRelation(previousTheta1, currentD1)
    }

    fun validateTSRelation(theta: Element, s: Element): Boolean {
        val expectedPairing = bilinearGroup.pair(g, h)
        val actualPairing = bilinearGroup.pair(theta, s)
        return expectedPairing == actualPairing
    }

}
