package nl.tudelft.trustchain.offlineeuro.cryptography

import it.unisa.dia.gas.jpbc.Element
import it.unisa.dia.gas.jpbc.Pairing
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory
import it.unisa.dia.gas.plaf.jpbc.pairing.a.TypeACurveGenerator
import it.unisa.dia.gas.plaf.jpbc.pairing.e.TypeECurveGenerator
import it.unisa.dia.gas.plaf.jpbc.pairing.f.TypeFCurveGenerator
import java.math.BigInteger

enum class PairingTypes {
    A,
    F,
    E,
    FromFile
}

class BilinearGroupElementsBytes(
    val g: ByteArray,
    val h: ByteArray,
    val gt: ByteArray

)
class BilinearGroupElements(
    val g: Element,
    val h: Element,
    val gt: Element

)

class BilinearGroup(
    rBits: Int = 160,
    pairingType: PairingTypes = PairingTypes.A
) {
    val pairing: Pairing
    var g: Element
    var h: Element
    var gt: Element

    init {

            pairing = when (pairingType) {
                PairingTypes.F -> {
                    // JPBC Type F pairing for asymmetric pairings
                    val params = TypeFCurveGenerator(rBits).generate()
                    PairingFactory.getPairing(params)
                }

                PairingTypes.A -> {
                    val qBits = 512
                    // JPBC Type A pairing generator for symmetric pairings
                    val params = TypeACurveGenerator(rBits, qBits).generate()
                    PairingFactory.getPairing(params)
                }

                PairingTypes.E -> {
                    val qBits = 1024
                    val params = TypeECurveGenerator(rBits, qBits).generate()
                    PairingFactory.getPairing(params)
                }

                PairingTypes.FromFile -> {
                    PairingFactory.getPairing("lib/params/a_181_603.properties");
                }
            }

        g = pairing.g1.newRandomElement().immutable
        h = pairing.g2.newRandomElement().immutable
        gt = pairing.gt.newRandomElement().immutable
    }

    fun updateGroupElements(groupElementsBytes: BilinearGroupElementsBytes) {
        this.g = pairing.g1.newElementFromBytes(groupElementsBytes.g)
        this.h = pairing.g2.newElementFromBytes(groupElementsBytes.h)
        this.gt = pairing.gt.newElementFromBytes(groupElementsBytes.gt)
    }


    fun getRandomZr(): Element {
        return pairing.zr.newRandomElement().immutable
    }

    fun pair(elementG: Element,  elementH: Element): Element {
        return pairing.pairing(elementG, elementH).immutable
    }

    fun getZrOrder(): BigInteger {
        return pairing.zr.order
    }

    fun generateRandomElementOfG(): Element {
        val randomZr = getRandomZr()
        return g.powZn(randomZr).immutable
    }

    fun generateRandomElementOfH(): Element {
        val randomZr = getRandomZr()
        return h.powZn(randomZr).immutable
    }

    fun generateRandomElementOfGT(): Element {
        val randomZr = getRandomZr()
        return gt.powZn(randomZr).immutable
    }
    fun gElementFromBytes(bytes: ByteArray): Element {
        return pairing.g1.newElementFromBytes(bytes).immutable
    }

    fun hElementFromBytes(bytes: ByteArray): Element {
        return pairing.g2.newElementFromBytes(bytes).immutable
    }

    fun gtElementFromBytes(bytes: ByteArray): Element {
        return pairing.gt.newElementFromBytes(bytes).immutable
    }

    fun zrElementFromBytes(bytes: ByteArray): Element {
        return pairing.zr.newElementFromBytes(bytes)
    }

    fun toGroupElementBytes(): BilinearGroupElementsBytes {
        return BilinearGroupElementsBytes(g.toBytes(), h.toBytes(), gt.toBytes())
    }
}
