package nl.tudelft.trustchain.offlineeuro


object ByteCounter {
    private val detailedSentLog = mutableListOf<Triple<String, String, Int>>()
    private val aggregatedMessageTotals = mutableMapOf<String, Long>()
    private var overallTotalBytes: Long = 0

    /**
     * Fundamental logging method called by others.
     * @param conceptualMessageType The high-level name of the message (e.g., "Simulated_FraudControlRequest").
     * @param partDescription Description of the specific part being logged (e.g., "proof1", "Payload").
     * @param bytes The size of this part in bytes.
     */
    @Synchronized
    private fun logPart(conceptualMessageType: String, partDescription: String, bytes: Int) {
        if (bytes < 0) return

        detailedSentLog.add(Triple(conceptualMessageType, partDescription, bytes))
        aggregatedMessageTotals[conceptualMessageType] = (aggregatedMessageTotals[conceptualMessageType] ?: 0L) + bytes
        overallTotalBytes += bytes
        // immediate print of the result can be done also
        // println("[BYTE_COUNT] Logged: '$conceptualMessageType - $partDescription': $bytes bytes")
    }

    /**
     * Records multiple byte array parts that conceptually belong to a single message type.
     * @param conceptualMessageType The high-level name for this group of parts (e.g., "Simulated_FraudControlRequest").
     * @param parts A map where the key is the part name (e.g., "proof1") and value is the ByteArray.
     */
    @Synchronized
    fun recordSentParts(conceptualMessageType: String, parts: Map<String, ByteArray>) {
        parts.forEach { (partName, data) ->
            logPart(conceptualMessageType, partName, data.size)
        }
    }

    /**
     * Records a single byte array payload that represents a conceptual message or a major part of it.
     * @param conceptualMessageType The high-level name for this payload (e.g., "Simulated_BlindSigReply_Signature").
     * @param payload The ByteArray itself.
     */
    @Synchronized
    fun recordSentSinglePayload(conceptualMessageType: String, payload: ByteArray) {
        logPart(conceptualMessageType, "Payload", payload.size)
    }

    @Synchronized
    fun printStats() {
        println("\n--- Network Byte Transfer Stats (Application Payload) ---")
        if (detailedSentLog.isEmpty()) {
            println("No bytes recorded.")
            println("-------------------------------------------------------")
            return
        }

        // 1. Detailed Component Log, Grouped by Conceptual Message Type
        println("\nDetailed Component Log:")
        val groupedByMessageType = detailedSentLog.groupBy { it.first } // Group by conceptualMessageType

        groupedByMessageType.forEach { (messageType, parts) ->
            println("  For Message Type: '$messageType'")
            parts.forEach { (_, partName, bytes) -> // Unpack the Triple
                println("    - Part '$partName': $bytes bytes")
            }
        }

        // 2. Aggregated Totals per Conceptual Message Type
        println("\nAggregated Bytes per Conceptual Message Type:")
        if (aggregatedMessageTotals.isEmpty()) {
            println("  No aggregated totals to display.")
        } else {
            aggregatedMessageTotals.toSortedMap().forEach { (messageType, totalBytes) -> // Sort by key for consistent output
                println("  - Total for '$messageType': $totalBytes bytes")
            }
        }
        println("-------------------------------------------------------")
        println("### Overall Total Bytes Sent (Application Payload): $overallTotalBytes bytes ###")
        println("-------------------------------------------------------")
    }

    @Synchronized
    fun reset() {
        detailedSentLog.clear()
        aggregatedMessageTotals.clear()
        overallTotalBytes = 0
    }
}
