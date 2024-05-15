package nl.tudelft.trustchain.offlineeuro.cryptography

import org.junit.Assert
import org.junit.Test

internal class BilinearGroupTest {
    @Test
    fun generateTwoGroupsFromFileShouldBeEqual() {
        val groupA = BilinearGroup(PairingTypes.FromFile)
        val groupB = BilinearGroup(PairingTypes.FromFile)
        Assert.assertEquals("The two generated groups should be equal", groupA.pairing, groupB.pairing)
    }

    @Test
    fun updateSecondGroupTest() {
        val groupA = BilinearGroup(PairingTypes.FromFile)
        val groupB = BilinearGroup(PairingTypes.FromFile)

        val randomElementToTest = groupA.getRandomZr()

        val groupElementsBytes = groupA.toGroupElementBytes()
        groupB.updateGroupElements(groupElementsBytes)

        Assert.assertEquals(groupA.g, groupB.g)
        Assert.assertEquals(groupA.h, groupB.h)
        Assert.assertEquals(groupA.gt, groupB.gt)

        Assert.assertEquals(groupA.g.powZn(randomElementToTest), groupB.g.powZn(randomElementToTest))
        Assert.assertEquals(groupA.h.powZn(randomElementToTest), groupB.h.powZn(randomElementToTest))
        Assert.assertEquals(groupA.gt.powZn(randomElementToTest), groupB.gt.powZn(randomElementToTest))
    }

}
