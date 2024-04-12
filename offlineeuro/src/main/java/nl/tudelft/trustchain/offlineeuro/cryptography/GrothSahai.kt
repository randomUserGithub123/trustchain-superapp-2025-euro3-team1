package nl.tudelft.trustchain.offlineeuro.cryptography

import it.unisa.dia.gas.jpbc.Element
import kotlinx.serialization.Serializable
import nl.tudelft.trustchain.offlineeuro.entity.CentralAuthority
import nl.tudelft.trustchain.offlineeuro.libraries.EBMap

object GrothSahai {

    val bilinearGroup = CentralAuthority.groupDescription
    val crs = CentralAuthority.crs

    val pairing = bilinearGroup.pairing
    val g = bilinearGroup.g
    val h = bilinearGroup.h
    val gt = bilinearGroup.gt
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

        val commitEBMap = EBMap(listOf(c1, c2, d1, d2), bilinearGroup)
        val piEBMap = EBMap(listOf(g, u, pi1, pi2), bilinearGroup)
        val thetaEBMap = EBMap(listOf(theta1, theta2, h, v), bilinearGroup)

        val oneElement = gt.duplicate().setToOne()
        val targetMap = EBMap(listOf(oneElement, oneElement, oneElement, target), bilinearGroup, false)

        for (i: Int in 0 until  2) {
            for (j: Int in 0 until 2) {

                val commitElement = commitEBMap[i, j]

                val piEBMapElement = piEBMap[i, j]
                val thetaEBMapElement = thetaEBMap[i, j]
                val targetMapElement = targetMap[i, j]
                val computed = piEBMapElement.mul(thetaEBMapElement).mul(targetMapElement)

                if (commitElement != computed)
                    return false
            }
        }

        return true
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


@Serializable
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
            && pi2 == other.pi2
            && target == other.target

    }
}

//object GrothSahaiSerializer : KSerializer<GrothSahaiProofBytes> {
//    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GrothSahaiProof") {
//        element<Element>("c1")
//        element<Element>("c2")
//        element<Element>("d1")
//        element<Element>("d2")
//        element<Element>("theta1")
//        element<Element>("theta2")
//        element<Element>("pi1")
//        element<Element>("pi2")
//        element<Element>("target")
//    }
//
//    override fun serialize(encoder: Encoder, value: GrothSahaiProofBytes) {
//        val composite = encoder.beginStructure(descriptor)
//        composite.encodeStringElement(descriptor, 0, value.c1.toString(Charsets.UTF_8))
//        composite.encodeStringElement(descriptor, 1, value.c1.toString(Charsets.UTF_8))
//        composite.encodeStringElement(descriptor, 2, value.c1.toString(Charsets.UTF_8))
//        composite.encodeStringElement(descriptor, 3, value.c1.toString(Charsets.UTF_8))
//        composite.encodeStringElement(descriptor, 4, value.c1.toString(Charsets.UTF_8))
//        composite.encodeStringElement(descriptor, 5, value.c1.toString(Charsets.UTF_8))
//        composite.encodeStringElement(descriptor, 6, value.c1.toString(Charsets.UTF_8))
//        composite.encodeStringElement(descriptor, 7, value.c1.toString(Charsets.UTF_8))
//        composite.encodeStringElement(descriptor, 8, value.c1.toString(Charsets.UTF_8))
//        composite.encodeStringElement(descriptor, 9, value.c1.toString(Charsets.UTF_8))
//        composite.endStructure(descriptor)
//    }
//
//    override fun deserialize(decoder: Decoder): GrothSahaiProofBytes {
//        val composite = decoder.beginStructure(descriptor)
//        lateinit var c1: ByteArray
//        loop@ while (true) {
//            when (val index = composite.decodeElementIndex(descriptor)) {
//                CompositeDecoder.DECODE_DONE -> break@loop
//                0 -> c1 = composite.decodeStringElement(descriptor, index).toByteArray(Charsets.UTF_8)
//                else -> throw SerializationException("Unknown index: $index")
//            }
//        }
//        composite.endStructure(descriptor)
//        return GrothSahaiProofBytes(
//            c1,
//            c1,
//            c1,
//            c1,
//            c1,
//            c1,
//            c1,
//            c1,
//            c1)
//    }
//}
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
