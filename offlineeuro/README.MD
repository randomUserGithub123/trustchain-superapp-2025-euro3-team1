# Offline Euro 

⚠️ **Important information when testing the app**

To ensure the most reliable connection and successful transactions between devices, please adhere to the following best practices:
- For best results, the two devices initiating a transaction should be in close proximity to one another.
- Keep other Bluetooth-enabled devices far away from the transacting pair (or turn off bluetooth). This reduces the chance of signal interference.
- The application is designed to only consider connecting to devices with a Received Signal Strength Indicator (RSSI) value greater than **-60 dBm**. This threshold helps filter out devices that are too far away. While RSSI is a useful metric, it is not a perfect measure of distance. We found that the signal strength does not always correspond to the distance between the devices. A device that is physically close might sometimes report a weaker signal than a more distant device in an open space. 


## Bluetooth communication

### Overview
We implemented a **Bluetooth-based peer-to-peer communication protocol** that enables offline transactions between participants over bluetooth. The protocol supports direct device-to-device communication for secure token transfers, blind signature requests, and BloomFilter synchronization.


### Core Components
- **BluetoothCommunicationProtocol**: Main protocol implementation in `src/main/java/nl/tudelft/trustchain/offlineeuro/communication/BluetoothCommunicationProtocol.kt`
- **Service Configuration**: Uses standard RFCOMM service with UUID `00001101-0000-1000-8000-00805F9B34FB`
- **Message Serialization**: Java Object Serialization for secure data transfer


### Transaction Flow Examples

#### 1. User-to-User Transaction
```
1. Sender discovers receiver via Bluetooth
2. Sender requests transaction randomness from receiver
3. Sender creates transaction details with proofs
4. Sender sends transaction details to receiver
5. Receiver validates and accepts/rejects transaction
6. Both participants update their BloomFilters
```

#### 2. Bank Withdrawal
```
1. User discovers bank device via Bluetooth
2. User requests blind signature randomness
3. User creates blind signature request
4. Bank validates and creates blind signature
5. User receives signed tokens
6. User receives bank's BloomFilter
```

#### 3. Bank Deposit
```
1. User discovers bank device via Bluetooth
2. User sends signed token(s) to the bank
4. Bank verifies token validity and Schnorr proof
6. Bank stores token in local database
7. Bank confirms deposit to user
```

#### 4. BloomFilter Synchronization
```
1. Participant A requests BloomFilter from Participant B
2. Participant B sends serialized BloomFilter data
3. Participant A reconstructs and merges BloomFilter
4. Both participants now have synchronized knowledge
```

### Error Handling and Reliability

#### Connection Management
- **Automatic Retry**: Failed connections are retried with exponential backoff
- **Timeout Handling**: 20-second timeout for message operations
- **User Feedback**: Toast messages inform users of connection status

### Usage guidelines
1. **Proximity**: Devices should be within 10 meters for optimal connection
2. **Permissions**: Ensure all required Bluetooth permissions are granted
3. **Battery**: Keep devices charged as Bluetooth discovery is power-intensive
4. **Interference**: Minimize interference from other Bluetooth devices

## BloomFilter Implementation for Double-Spending Detection

### Overview
OfflineEuro implements a **BloomFilter-based double-spending detection system** to efficiently identify potentially spent tokens without requiring a complete linear search through all known tokens.

### Bloom Filter
A BloomFilter is a probabilistic data structure that efficiently tests whether an element is a member of a set. It provides:
- **Fast lookups**: O(k) time complexity where k is the number of hash functions
- **Memory efficiency**: Much smaller memory footprint compared to storing all elements
- **False positives**: May incorrectly report an element as present (but never false negatives)

### Implementation Details

#### Core Components
- **BloomFilter Class**: Located in `src/main/java/nl/tudelft/trustchain/offlineeuro/cryptography/BloomFilter.kt`
- **Bluetooth Bloom Filter sharing**: New message messages between users
- **Optimal Sizing**: Automatically calculates optimal size and hash function count based on expected elements and false positive rate

#### Key Methods
```kotlin
// Add a token to the filter
fun add(euro: DigitalEuro)

// Check if a token might be in the filter (potential double-spend)
fun mightContain(euro: DigitalEuro): Boolean

// Update filter using Algorithm 2 for distributed sharing
fun applyAlgorithm2Update(receivedBF: BloomFilter, myReceivedMonies: List<DigitalEuro>): String
```


### BloomFilter Transfer and Communication

#### Communication Protocol
- **Request/Reply Pattern**: Participants can request and send BloomFilters
- **Serialization**: Filters are converted to byte arrays for transmission
- **Message Types**: 
  - `BloomFilterRequestMessage`: Request for a participant's filter
  - `BloomFilterReplyMessage`: Response containing the filter data

#### Transfer Scenarios
1. **User-to-User Transactions**: Sender shares their BloomFilter with receiver
2. **Bank Withdrawal**: User receives Bank's BloomFilter after withdrawal
3. **Periodic Synchronization**: Participants exchange filters to update knowledge

#### Implementation Classes
- **Messages**: `BloomFilterRequestMessage.kt`, `BloomFilterReplyMessage.kt`
- **Payloads**: `BloomFilterRequestPayload.kt`, `BloomFilterReplyPayload.kt`
- **Communication**: `OfflineEuroCommunity.kt` handles the network communication

### Performance Characteristics

#### Speed Improvements
- **Linear Search**: O(n) time complexity where n is number of tokens
- **BloomFilter Lookup**: O(k) time complexity where k is number of hash functions (typically 5-10)
- **Speedup Factor**: 10x to 1000x faster depending on dataset size

#### Memory Efficiency
- **Traditional Storage**: ~277 bytes per token (full DigitalEuro object)
- **BloomFilter**: ~12 bytes per 1000 expected tokens
- **Memory Reduction**: 95%+ reduction in memory usage

#### Benefits
- **Distributed Knowledge**: All participants share information about spent tokens
- **Faster Performance**: Maintains efficient lookup times
- **User Double Spend detection**: Users can efficiently check their bloom filters to determine if there has been a double-spend

### Transaction Flow
1. **Token Creation**: New tokens are not in any BloomFilter
2. **Token Spending**: Spent tokens are added to sender's BloomFilter
3. **Token Receiving**: Receiver checks their BloomFilter for potential double-spends
4. **Filter Sharing**: Participants exchange filters to synchronize knowledge

### Default Parameters
- **Expected Elements**: 1000 tokens (can be very large, depends on phone bluetooth capabilities)
- **False Positive Rate**: 0.1% (0.001)
- **Hash Functions**: Automatically calculated for optimal performance
- **Filter Size**: Automatically calculated based on expected elements and false positive rate


## NFC HCE Communication Attempt
There was an attempt at implementing the NFC (HCE) communication protocol in addition to the Bluetooth communication protocol, which can be seen in this [branch](https://github.com/randomUserGithub123/trustchain-superapp-2025-euro3-team1/tree/NFC_implementation)

In our approach, we have tried to stay on track with the `OfflineEuro` implementation, which would mean that similar to Bluetooth (ideally) we would have a listener on each device listen indefinitely for new messages and the sender can be initiated with a certain callback, such as a button click. However, we did not manage to trigger NFC communication in the direction of the user pressing the button towards the receiver, as the NFC communication would be triggered as soon as the devices would be in proximity of each other and the direction would be arbitrary. The possible cause of this is that both devices would listen for NFC messages at the same time, which is the approach we have had for our Bluetooth protocol. 

A solution to the following problem would be to dynamically adjust the listener, such that not both devices listen to the messages at the same time. We had tried with this approach but in order to implement it, we would have to change most of the protocol as adding state to the HostApduService.kt instantiated class (receiver in the NFC protocol) is not enough.