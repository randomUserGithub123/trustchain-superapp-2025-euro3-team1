package nl.tudelft.trustchain.offlineeuro.libraries

import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.cryptography.GrothSahaiProof
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable


private data class GrothSahaiProofBytes(
    val c1: ByteArray,
    val c2: ByteArray,
    val d1: ByteArray,
    val d2: ByteArray,
    val theta1: ByteArray,
    val theta2: ByteArray,
    val pi1: ByteArray,
    val pi2: ByteArray,
    val target: ByteArray

)  : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GrothSahaiProofBytes

        if (!c1.contentEquals(other.c1)) return false
        if (!c2.contentEquals(other.c2)) return false
        if (!d1.contentEquals(other.d1)) return false
        if (!d2.contentEquals(other.d2)) return false
        if (!theta1.contentEquals(other.theta1)) return false
        if (!theta2.contentEquals(other.theta2)) return false
        if (!pi1.contentEquals(other.pi1)) return false
        if (!pi2.contentEquals(other.pi2)) return false
        return target.contentEquals(other.target)
    }

    override fun hashCode(): Int {
        var result = c1.contentHashCode()
        result = 31 * result + c2.contentHashCode()
        result = 31 * result + d1.contentHashCode()
        result = 31 * result + d2.contentHashCode()
        result = 31 * result + theta1.contentHashCode()
        result = 31 * result + theta2.contentHashCode()
        result = 31 * result + pi1.contentHashCode()
        result = 31 * result + pi2.contentHashCode()
        result = 31 * result + target.contentHashCode()
        return result
    }
}


object GrothSahaiSerializer {
    fun serializeGrothSahaiProofs(proofs: List<GrothSahaiProof>?) : ByteArray? {
        if (proofs.isNullOrEmpty())
            return null

        val proofsAsBytes = proofs.map { x -> grothSahaiProofToBytes(x) }
        return serializeProofBytesList(proofsAsBytes)
    }

    fun deserializeProofBytes(bytes: ByteArray?, group: BilinearGroup): ArrayList<GrothSahaiProof> {
        if (bytes == null) return arrayListOf()
        val proofBytesList = deserializeProofBytesList(bytes)
        return ArrayList(proofBytesList.map { x -> bytesToGrothSahai(x, group) })
    }


    private fun serializeProofBytesList(proofList: List<GrothSahaiProofBytes>): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
        objectOutputStream.writeObject(proofList)
        objectOutputStream.close()
        return byteArrayOutputStream.toByteArray()
    }

    private fun deserializeProofBytesList(bytes: ByteArray):  List<GrothSahaiProofBytes> {
        val byteArrayInputStream = ByteArrayInputStream(bytes)
        val objectInputStream = ObjectInputStream(byteArrayInputStream)
        val proofList = objectInputStream.readObject() as  List<GrothSahaiProofBytes>
        objectInputStream.close()
        return proofList
    }

    private fun grothSahaiProofToBytes(grothSahaiProof: GrothSahaiProof) : GrothSahaiProofBytes {
        val (c1, c2, d1, d2, theta1, theta2, pi1, pi2, target) = grothSahaiProof
        return GrothSahaiProofBytes(
            c1.toBytes(),
            c2.toBytes(),
            d1.toBytes(),
            d2.toBytes(),
            theta1.toBytes(),
            theta2.toBytes(),
            pi1.toBytes(),
            pi2.toBytes(),
            target.toBytes()
        )
    }

    private fun bytesToGrothSahai(grothSahaiProofBytes: GrothSahaiProofBytes, group: BilinearGroup) : GrothSahaiProof {
        val (c1, c2, d1, d2, theta1, theta2, pi1, pi2, target) = grothSahaiProofBytes
        return GrothSahaiProof(
            group.gElementFromBytes(c1),
            group.gElementFromBytes(c2),
            group.hElementFromBytes(d1),
            group.hElementFromBytes(d2),
            group.gElementFromBytes(theta1),
            group.gElementFromBytes(theta2),
            group.hElementFromBytes(pi1),
            group.hElementFromBytes(pi2),
            group.gtElementFromBytes(target)
        )
    }
}
