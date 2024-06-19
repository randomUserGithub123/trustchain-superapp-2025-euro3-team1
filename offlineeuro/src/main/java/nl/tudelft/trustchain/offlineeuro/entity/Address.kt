package nl.tudelft.trustchain.offlineeuro.entity

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.enums.Role

/**
 * Data class to group information regarding an address used in the IPV8 communication protocol.
 *
 * @property name the name of the addressee.
 * @property type the role of the addressee.
 * @property publicKey the public key of the addressee.
 * @property peerPublicKey the public key of the addressee as IPV8-peer.
 */
data class Address(
    val name: String,
    val type: Role,
    val publicKey: Element,
    val peerPublicKey: ByteArray?,
)
