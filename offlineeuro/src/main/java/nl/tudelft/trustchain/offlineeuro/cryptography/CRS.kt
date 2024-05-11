package nl.tudelft.trustchain.offlineeuro.cryptography

import it.unisa.dia.gas.jpbc.Element

data class CRS(
    val g: Element,
    val u: Element,
    val gPrime: Element,
    val uPrime: Element,

    // Commitment key pairs for the second group G2
    val h: Element,
    val v: Element,
    val hPrime: Element,
    val vPrime: Element,
) {
    fun toCRSBytes(): CRSBytes {
        return CRSBytes(
            g.toBytes(),
            u.toBytes(),
            gPrime.toBytes(),
            uPrime.toBytes(),
            h.toBytes(),
            v.toBytes(),
            hPrime.toBytes(),
            vPrime.toBytes()
        )
    }
}

data class CRSBytes (
    val g: ByteArray,
    val u: ByteArray,
    val gPrime: ByteArray,
    val uPrime: ByteArray,

    // Commitment key pairs for the second group G2
    val h: ByteArray,
    val v: ByteArray,
    val hPrime: ByteArray,
    val vPrime: ByteArray,
)



object CRSGenerator {

    fun generateCRSMap(bilinearGroup: BilinearGroup): Pair<CRS, Map<Element, Element>> {

        val group1 = bilinearGroup.g
        val group2 = bilinearGroup.h

        val gGenerator = bilinearGroup.pairing.zr.newRandomElement().immutable
        val g = group1.duplicate().powZn(gGenerator).immutable

        val uGenerator = bilinearGroup.pairing.zr.newRandomElement().immutable
        val u = group1.duplicate().powZn(uGenerator).immutable

        val gPrimeGenerator = bilinearGroup.pairing.zr.newRandomElement().immutable
        val gPrime = group1.duplicate().powZn(gPrimeGenerator).immutable

        val uPrimeGenerator = bilinearGroup.pairing.zr.newRandomElement().immutable
        val uPrime = group1.duplicate().powZn(uPrimeGenerator).immutable

        // Commitment key pairs for the second group G2
        val hGenerator = bilinearGroup.pairing.zr.newRandomElement().immutable
        val h = group2.duplicate().powZn(hGenerator).immutable

        val vGenerator = bilinearGroup.pairing.zr.newRandomElement().immutable
        val v = group2.duplicate().powZn(vGenerator).immutable

        val hPrimeGenerator = bilinearGroup.pairing.zr.newRandomElement().immutable
        val hPrime = group2.duplicate().powZn(hPrimeGenerator).immutable

        val vPrimeGenerator = bilinearGroup.pairing.zr.newRandomElement().immutable
        val vPrime = group2.duplicate().powZn(vPrimeGenerator).immutable

        val crs = CRS(g, u, gPrime, uPrime, h, v, hPrime, vPrime)

        val crsMap = mapOf(
                g to gGenerator,
                u to uGenerator,
                gPrime to gPrimeGenerator,
                uPrime to uPrimeGenerator,
                h to hGenerator,
                v to vGenerator,
                hPrime to hGenerator,
                vPrime to vPrimeGenerator
            )

        return Pair(crs, crsMap)
    }
}
