package nl.tudelft.trustchain.offlineeuro.cryptography

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.libraries.EBMap
import nl.tudelft.trustchain.offlineeuro.libraries.GrothSahaiSerializer

object GrothSahai {
    fun createTransactionProof(
        privateKey: Element,
        publicKey: Element,
        target: Element,
        previousT: Element,
        randomizationElements: RandomizationElements,
        bilinearGroup: BilinearGroup,
        crs: CRS
    ): Pair<TransactionProof, Element> {
        val g = bilinearGroup.g
        val h = bilinearGroup.h
        val pairing = bilinearGroup.pairing

        val signatureElement = target.immutable
        val y = signatureElement.div(privateKey).immutable
        val computedY = h.powZn(y).immutable
        val newTarget = pairing.pairing(publicKey, computedY).immutable

        val u = crs.u
        val v = crs.v

        val r = pairing.zr.newRandomElement().immutable

        val s = previousT.mul(-1).invert()

        val c1 = g.powZn(r).immutable
        val c2 = u.powZn(r).mul(publicKey).immutable
        val d1 = h.powZn(s).immutable
        val d2 = v.powZn(s).mul(computedY).immutable

        val pi1 = d1.powZn(r).mul(randomizationElements.group2T)
        val pi2 = d2.powZn(r).mul(randomizationElements.vT)

        val theta1 = randomizationElements.group1TInv
        val theta2 = publicKey.powZn(s).mul(randomizationElements.uTInv).immutable

        val grothSahaiProof = GrothSahaiProof(c1, c2, d1, d2, theta1, theta2, pi1, pi2, newTarget)

        return Pair(TransactionProof(grothSahaiProof, computedY, v.powZn(s).immutable), r)
    }

    fun verifyTransactionProof(
        grothSahaiProof: GrothSahaiProof,
        bilinearGroup: BilinearGroup,
        crs: CRS
    ): Boolean {
        val (c1, c2, d1, d2, theta1, theta2, pi1, pi2, target) = grothSahaiProof
        val g = bilinearGroup.g
        val h = bilinearGroup.h
        val gt = bilinearGroup.gt

        val u = crs.u
        val v = crs.v

        val commitEBMap = EBMap(listOf(c1, c2, d1, d2), bilinearGroup)
        val piEBMap = EBMap(listOf(g, u, pi1, pi2), bilinearGroup)
        val thetaEBMap = EBMap(listOf(theta1, theta2, h, v), bilinearGroup)

        val oneElement = gt.duplicate().setToOne()
        val targetMap = EBMap(listOf(oneElement, oneElement, oneElement, target), bilinearGroup, false)

        for (i: Int in 0 until 2) {
            for (j: Int in 0 until 2) {
                val commitElement = commitEBMap[i, j]

                val piEBMapElement = piEBMap[i, j]
                val thetaEBMapElement = thetaEBMap[i, j]
                val targetMapElement = targetMap[i, j]
                val computed = piEBMapElement.mul(thetaEBMapElement).mul(targetMapElement)

                if (commitElement != computed) {
                    return false
                }
            }
        }

        return true
    }

    fun tToRandomizationElements(
        t: Element,
        group: BilinearGroup,
        crs: CRS
    ): RandomizationElements {
        val group2T = group.h.powZn(t).immutable
        val vT = crs.v.powZn(t).immutable
        val tInv = t.mul(-1)
        val group1TInv = group.g.powZn(tInv).immutable
        val uTInv = crs.u.powZn(tInv).immutable

        return RandomizationElements(group2T, vT, group1TInv, uTInv)
    }
}

data class GrothSahaiProof(
    val c1: Element,
    val c2: Element,
    val d1: Element,
    val d2: Element,
    val theta1: Element,
    val theta2: Element,
    val pi1: Element,
    val pi2: Element,
    val target: Element
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GrothSahaiProof) return false

        return c1 == other.c1 &&
            c2 == other.c2 &&
            d1 == other.d1 &&
            d2 == other.d2 &&
            theta1 == other.theta1 &&
            theta2 == other.theta2 &&
            pi1 == other.pi1 &&
            pi2 == other.pi2 &&
            target == other.target
    }
}

data class RandomizationElements(
    val group2T: Element,
    val vT: Element,
    val group1TInv: Element,
    val uTInv: Element
) {
    fun toRandomizationElementsBytes(): RandomizationElementsBytes {
        return RandomizationElementsBytes(
            group2T.toBytes(),
            vT.toBytes(),
            group1TInv.toBytes(),
            uTInv.toBytes()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RandomizationElements) return false
        return this.group2T == other.group2T && this.vT == other.vT && this.group1TInv == other.group1TInv && this.uTInv == other.uTInv
    }
}

data class RandomizationElementsBytes(
    val group2T: ByteArray,
    val vT: ByteArray,
    val group1TInv: ByteArray,
    val uTInv: ByteArray
) {
    fun toRandomizationElements(group: BilinearGroup): RandomizationElements {
        return RandomizationElements(
            group.hElementFromBytes(group2T),
            group.hElementFromBytes(vT),
            group.gElementFromBytes(group1TInv),
            group.gElementFromBytes(uTInv)
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RandomizationElementsBytes) return false
        return this.group2T.contentEquals(other.group2T) && this.vT.contentEquals(other.vT) &&
            this.group1TInv.contentEquals(other.group1TInv) && this.uTInv.contentEquals(other.uTInv)
    }
}

data class TransactionProofBytes(
    val grothSahaiProofBytes: ByteArray,
    val usedYBytes: ByteArray,
    val usedVSBytes: ByteArray,
) {
    fun toTransactionProof(group: BilinearGroup): TransactionProof {
        return TransactionProof(
            GrothSahaiSerializer.deserializeProofBytes(grothSahaiProofBytes, group),
            group.hElementFromBytes(usedYBytes),
            group.hElementFromBytes(usedVSBytes)
        )
    }
}

data class TransactionProof(
    val grothSahaiProof: GrothSahaiProof,
    val usedY: Element,
    val usedVS: Element,
) {
    fun toTransactionProofBytes(): TransactionProofBytes {
        return TransactionProofBytes(
            GrothSahaiSerializer.serializeGrothSahaiProof(grothSahaiProof),
            usedY.toBytes(),
            usedVS.toBytes()
        )
    }
}
