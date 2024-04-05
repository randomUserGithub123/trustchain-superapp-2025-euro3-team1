package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element


data class TransactionDetails (
    val digitalEuro: DigitalEuro,
    val currentProofs: Pair<TransactionProof, GrothSahaiProof>,
    val spenderPublicKey: Element,
)

object Transaction {

    val bilinearGroup = CentralAuthority.groupDescription
    val crs = CentralAuthority.crs

    val pairing = bilinearGroup.pairing
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
        val (firstStatement, y) = GrothSahai.createFirstStatement(
            privateKey,
            publicKey,
            target,
            walletEntry.t,
            randomizationElements
        )

        val secondStatement = GrothSahai.createSecondStatement(privateKey, y)

        return TransactionDetails(digitalEuro, Pair(firstStatement, secondStatement), publicKey)
    }


    fun validate(transaction: TransactionDetails): Boolean {

        // Check the GS proofs
        val firstProofValid = GrothSahai.verifyFirstProof(transaction.currentProofs.first)
        val secondProofValid = GrothSahai.verifySecondStatement(transaction.currentProofs.second)

        // Check if the public key is used in the proof
        val publicKeyPairing = pairing.pairing(transaction.spenderPublicKey, transaction.currentProofs.first.usedY)

        if (publicKeyPairing != transaction.currentProofs.first.grothSahaiProof.target)
            return false

        if (!verifyTransactionProof(transaction.currentProofs.first))
            return false

        val previousProofs = transaction.digitalEuro.proofs
        if (previousProofs.isEmpty())
            return firstProofValid && secondProofValid

        var previousProof = previousProofs.first()

        // Check previous Proofs
        for (i: Int in 1 until previousProofs.size ) {
            val currentProof = previousProofs[i]

            if (!verifyProofChain(previousProof, currentProof))
                return false

            previousProof = currentProof
        }

        // Compare last proof to current transaction proof
        if (!verifyProofChain(previousProof, transaction.currentProofs.first.grothSahaiProof)) {

            return false

        }

        if (transaction.currentProofs.first.grothSahaiProof.target == previousProof.target)
            throw Exception("Big mistake")

        return true
    }

    fun verifyProofChain(previousProof: GrothSahaiProof, currentProof: GrothSahaiProof): Boolean {
        val previousTargetToBytes = previousProof.target.toCanonicalRepresentation()
        val previousTargetAsPower = pairing.zr.newElementFromBytes(previousTargetToBytes)
        val expectedTarget = pairing.pairing(g, h).powZn(previousTargetAsPower)

        if (currentProof.target == previousProof.target)
            throw Exception("Big mistake")

        if (expectedTarget != currentProof.target)
            return false

        // Check if the relation with t and s is correct
        val previousTheta1 = previousProof.theta1
        val currentD1 = currentProof.d1
        val expectedPairing = pairing.pairing(g, h)
        val actualPairing = pairing.pairing(previousTheta1, currentD1)

        if (expectedPairing != actualPairing)
            return false

        return true
    }

    fun verifyTransactionProof(transactionProof: TransactionProof): Boolean {
        val usedY = transactionProof.usedY
        val usedVS = transactionProof.usedVS

        val d2 = transactionProof.grothSahaiProof.d2

        if (usedVS.mul(usedY) != d2)
            return false

        return true
    }
}
