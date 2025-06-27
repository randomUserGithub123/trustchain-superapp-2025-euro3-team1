# EuroToken - A Digital Euro
EU member states of the Eurozone [are considering creating a blockchain-inspired digital Eurotoken using a P2P architecture](https://haldus.eestipank.ee/sites/default/files/2021-07/Work%20stream%203%20-%20A%20New%20Solution%20-%20Blockchain%20and%20eID_1.pdf).
The superapp contains a fully functional digital cash implementation, still experimental and tested within 1 retail location for 1 morning in live production usage with real financial transactions.

## Web-of-Trust
In standard operation, EuroToken communicates with a gateway controlled by authorities to verify the authenticity of transactions.
However, EuroToken needs to be able to function in case of world-scale disasters and system outages, and thus in case of a gateway not being available for an extended period of time.
Even more, EuroToken is also built such that it works in remote locations without a large peer-to-peer network.
Not having a large network to confirm transactions with comes at the cost of induced risk, primarily due to the double spending problem.

### Double spending
Double spending is a problem that occurs when someone spends the same amount of money more than once.
This problem is very prevalent with digital offline-capable currencies.

The problem of double spending arises because digital currencies are not regulated by a central authority.
This means that there is no one to verify that a transaction is valid.
If no central authority is available to verify the validity of a transaction, then it is possible for someone to spend the same amount of money more than once.

Double spending is a major problem for digital currencies, as it undermines their trustworthiness.
If people cannot trust that a digital currency is not susceptible to double spending, then they are less likely to use it.

Double spending in EuroToken occurs when a malicious user sends a transaction to a wallet, and then sends the same transaction to another wallet whilst the second receiver is not aware of the first transaction.

#### Requirements for solution
To adhere to the standard of EuroToken, the solution for double spending has to comply to the following requirements:
* Fully offline capable
* Completely distributed
* Permissionless
* Pseudo-anonymous
* Independent of other authorities, such as banks

### Double Spending mitigation
To counter double spending, EuroToken attempts to create a [web-of-trust](https://en.wikipedia.org/wiki/Web_of_trust) to help verify the authenticity of transactions and users.
The basis of the web-of-trust is proof by participation of users in the network.
If someone participates actively in the network, by making transactions, then they are regarded as more trustworthy than someone who does not participate.
[Read about the specific implementation of the web-of-trust](web-of-trust.md)

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

#### 3. BloomFilter Synchronization
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
EuroToken implements a **BloomFilter-based double-spending detection system** to efficiently identify potentially spent tokens without requiring a complete linear search through all known tokens.

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
