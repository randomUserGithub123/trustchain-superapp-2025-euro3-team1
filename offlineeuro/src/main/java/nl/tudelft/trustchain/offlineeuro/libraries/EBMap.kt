package nl.tudelft.trustchain.offlineeuro.libraries

import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup

/**
 * Class to convert a list of elements to an Extended Bilinear Map. These maps are used in the
 * Groth-Sahai proofs.
 *
 * @property elements the list of elements to fill the Extended Bilinear Map with.
 * @property bilinearGroup the bilinear group description used to compute the pairings.
 * @param computeMap flag to set the elements should be paired or not.
 */
class EBMap(
    private val elements: List<Element>,
    private val bilinearGroup: BilinearGroup,
    computeMap: Boolean = true
) {
    private val ebMap: Array<Array<Element>>
    private val mapSize = 2

    init {
        ebMap =
            if (computeMap) {
                Array(mapSize) { i -> Array(mapSize) { j -> ebMapIndexToElement(i, j) } }
            } else {
                Array(mapSize) { i -> Array(mapSize) { j -> indexToElement(i, j) } }
            }
    }

    private fun ebMapIndexToElement(
        row: Int,
        column: Int
    ): Element {
        val elementI = elements[row]
        val elementJ = elements[mapSize + column]
        return bilinearGroup.pair(elementI, elementJ)
    }

    private fun indexToElement(
        row: Int,
        column: Int
    ): Element {
        return elements[2 * row + column]
    }

    operator fun get(
        i: Int,
        j: Int
    ): Element {
        return ebMap[i][j]
    }

    operator fun set(
        i: Int,
        j: Int,
        element: Element
    ) {
        ebMap[i][j] = element
    }
}
