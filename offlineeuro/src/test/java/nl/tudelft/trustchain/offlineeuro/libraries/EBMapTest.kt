package nl.tudelft.trustchain.offlineeuro.libraries

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import org.junit.Assert
import org.junit.Test

class EBMapTest {

    private val group = BilinearGroup()
    @Test
    fun noComputeMapTest() {
        val elementList = arrayListOf<Element>()

        for (i in 0 until 4) {
            val randomZr = group.getRandomZr()
            val element = group.g.powZn(randomZr).immutable
            elementList.add(element)
        }

        val ebMap = EBMap(elementList, group, false)
        Assert.assertEquals(elementList[0], ebMap[0, 0])
        Assert.assertEquals(elementList[1], ebMap[0, 1])
        Assert.assertEquals(elementList[2], ebMap[1, 0])
        Assert.assertEquals(elementList[3], ebMap[1, 1])
    }

    @Test
    fun computeMapTest() {
        val elementList = arrayListOf<Element>()

        // Two elements from group g first
        for (i in 0 until 2) {
            val randomZr = group.getRandomZr()
            val element = group.g.powZn(randomZr).immutable
            elementList.add(element)
        }

        // Two elements group h after
        for (i in 0 until 2) {
            val randomZr = group.getRandomZr()
            val element = group.h.powZn(randomZr).immutable
            elementList.add(element)
        }

        val expectedPairings = arrayListOf<Element>(
            group.pair(elementList[0], elementList[2]),
            group.pair(elementList[0], elementList[3]),
            group.pair(elementList[1], elementList[2]),
            group.pair(elementList[1], elementList[3]),
        )

        val ebMap = EBMap(elementList, group)
        Assert.assertEquals(expectedPairings[0], ebMap[0, 0])
        Assert.assertEquals(expectedPairings[1], ebMap[0, 1])
        Assert.assertEquals(expectedPairings[2], ebMap[1, 0])
        Assert.assertEquals(expectedPairings[3], ebMap[1, 1])
    }
}
