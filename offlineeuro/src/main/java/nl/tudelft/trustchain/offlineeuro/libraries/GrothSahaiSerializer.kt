package nl.tudelft.trustchain.offlineeuro.libraries


import nl.tudelft.trustchain.offlineeuro.entity.GrothSahaiProof

data class GrothSahaiProofBytes(
    val c1: ByteArray,
    val c2: ByteArray,
    val d1: ByteArray,
    val d2: ByteArray,
    val theta1: ByteArray,
    val theta2: ByteArray,
    val pi1: ByteArray,
    val pi2: ByteArray,
    val target: ByteArray
)


class GrothSahaiSerializer {

    companion object {
        fun grothSahaiProofToBytes(grothSahaiProof: GrothSahaiProof) : GrothSahaiProofBytes {
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
    }
}
