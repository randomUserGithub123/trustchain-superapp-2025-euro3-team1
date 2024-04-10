package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element

object GrothSahai {

    val bilinearGroup = CentralAuthority.groupDescription
    val crs = CentralAuthority.crs

    val pairing = bilinearGroup.pairing
    val g = bilinearGroup.g
    val h = bilinearGroup.h

    fun createTransactionProof (
        privateKey: Element,
        publicKey: Element,
        target: Element,
        previousT: Element,
        randomizationElements: RandomizationElements
    ): Pair<TransactionProof, Element> {
        val signatureElement = target.immutable
        val X = publicKey
        val y = signatureElement.div(privateKey).immutable
        val Y = h.powZn(y).immutable
        val T = pairing.pairing(X,Y).immutable

        val u = crs.u
        val v = crs.v

        val r = pairing.zr.newRandomElement().immutable

        val s = previousT.mul(-1).invert()

        val c1 = g.powZn(r).immutable
        val c2 = u.powZn(r).mul(X).immutable
        val d1 = h.powZn(s).immutable
        val d2 = v.powZn(s).mul(Y).immutable

        val pi1 = d1.powZn(r).mul(randomizationElements.group2T)
        val pi2 = d2.powZn(r).mul(randomizationElements.vT)

        val theta1 = randomizationElements.group1TInv
        val theta2 = X.powZn(s).mul(randomizationElements.uTInv).immutable

        val grothSahaiProof = GrothSahaiProof(c1, c2, d1, d2, theta1, theta2, pi1, pi2, T)

        return Pair(TransactionProof(grothSahaiProof, Y, v.powZn(s).immutable), r)
    }

    fun verifyTransactionProof(transactionProof: TransactionProof): Boolean {
        val (c1, c2, d1, d2, theta1, theta2, pi1, pi2, target) = transactionProof.grothSahaiProof

        val u = crs.u
        val v = crs.v

        val topleft = pairing.pairing(c1, d1).immutable
        val topright = pairing.pairing(c1, d2).immutable
        val bottomleft = pairing.pairing(c2, d1).immutable
        val bottomright = pairing.pairing(c2, d2).immutable

        val tl2 = pairing.pairing(g, pi1)
        val tl3 = pairing.pairing(theta1, h)
        val tlchecker = (tl2.mul(tl3)).equals(topleft)

        val tr2 = pairing.pairing(g, pi2)
        val tr3 = pairing.pairing(theta1, v)
        val trchecker = (tr2.mul(tr3)).equals(topright)

        val bl2 = pairing.pairing(u, pi1)
        val bl3 = pairing.pairing(theta2, h)
        val blchecker = bottomleft.equals(bl2.mul(bl3))

        val br2 = pairing.pairing(u, pi2)
        val br3 = pairing.pairing(theta2, v)
        val brchecker = (br2.mul(br3).mul(target)).equals(bottomright)

        return tlchecker && trchecker && blchecker && brchecker

    }

    fun tToRandomizationElements(t: Element): Pair<Element, RandomizationElements> {
        val bilinearGroup = CentralAuthority.groupDescription
        val crs = CentralAuthority.crs

        val group2T = bilinearGroup.h.powZn(t).immutable
        val vT = crs.v.powZn(t).immutable
        val tInv = t.mul(-1)
        val group1TInv = bilinearGroup.g.powZn(tInv).immutable
        val uTInv = crs.u.powZn(tInv).immutable

        return Pair(t, RandomizationElements(group2T, vT, group1TInv, uTInv))
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

        return c1 == other.c1
            && c2 == other.c2
            && d1 == other.d1
            && d2 == other.d2
            && theta1 == other.theta1
            && theta2 == other.theta2
            && pi1 == other.pi1
            && c2 == other.c2
            && pi2 == other.pi2
            && target == other.target

    }
}

data class RandomizationElements (
    val group2T: Element,
    val vT: Element,
    val group1TInv: Element,
    val uTInv: Element
)

data class TransactionProof (
    val grothSahaiProof: GrothSahaiProof,
    val usedY: Element,
    val usedVS: Element,
)
