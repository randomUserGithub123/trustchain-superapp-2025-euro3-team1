package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.enums.Role

data class Address (
    val name: String,
    val type: Role,
    val publicKey: Element,
    val peerPublicKey: ByteArray?,

) {

}
