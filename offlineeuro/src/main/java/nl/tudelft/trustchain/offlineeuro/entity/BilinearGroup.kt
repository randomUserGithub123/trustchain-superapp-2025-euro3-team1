package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element
import it.unisa.dia.gas.jpbc.Pairing
import it.unisa.dia.gas.jpbc.PairingParametersGenerator
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory
import it.unisa.dia.gas.plaf.jpbc.pairing.a.TypeACurveGenerator
import java.math.BigInteger

class BilinearGroup() {
    val pairing: Pairing
    val g: Element
    val h: Element
    val gt: Element

    init {

        val parametersGenerator: PairingParametersGenerator<*> = TypeACurveGenerator(160, 512)
        val parameters = parametersGenerator.generate()

        // Initialize the pairing
        pairing = PairingFactory.getPairing(parameters)

        g = pairing.g1.newRandomElement().immutable
        h = pairing.g2.newRandomElement().immutable
        gt = pairing.gt.newRandomElement().immutable
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
}
