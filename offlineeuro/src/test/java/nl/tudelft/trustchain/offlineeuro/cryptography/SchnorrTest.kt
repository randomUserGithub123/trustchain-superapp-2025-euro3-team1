package nl.tudelft.trustchain.offlineeuro.cryptography

import org.junit.Assert
import org.junit.Test

class SchnorrTest {

    @Test
    fun signAndVerifyTest() {
        val group = BilinearGroup()
        val g = group.g
        val privateKey = group.getRandomZr()
        val publicKey = g.powZn(privateKey).immutable
        val elementToSign = g.powZn(group.getRandomZr())

        val schnorrSignature = Schnorr.schnorrSignature(privateKey, elementToSign.toBytes(), group)
        val verificationResult = Schnorr.verifySchnorrSignature(schnorrSignature, publicKey, group)
        Assert.assertTrue("The signature should be valid", verificationResult)
    }

    @Test
    fun blindSignAndVerifyTest() {
        val group = BilinearGroup()
        val g = group.g
        val privateKey = group.getRandomZr()
        val publicKey = g.powZn(privateKey).immutable
        val elementToSign = g.powZn(group.getRandomZr()).immutable.toBytes()
        val serialNumber = "TestSerialNumber"

        val bytesToSign = serialNumber.toByteArray() + elementToSign

        // Construct randomness for user
        val k = group.getRandomZr()
        val r = g.powZn(k).immutable

        val blindedChallenge = Schnorr.createBlindedChallenge(r, bytesToSign, publicKey, group)
        val blindSignature = Schnorr.signBlindedChallenge(k, blindedChallenge.blindedChallenge, privateKey)
        val blindSchnorrSignature = Schnorr.unblindSignature(blindedChallenge, blindSignature)

        val verificationResult = Schnorr.verifySchnorrSignature(blindSchnorrSignature, publicKey, group)
        Assert.assertTrue("The signature should be valid", verificationResult)
    }
}
