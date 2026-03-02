# TRDP Protocol Library
[![JitPack](https://jitpack.io/v/Gurfh/JavaTrdpLibrary.svg)](https://jitpack.io/#Gurfh/JavaTrdpLibrary)
[![codecov](https://codecov.io/gh/Gurfh/JavaTrdpLibrary/branch/master/graph/badge.svg)](https://app.codecov.io/gh/Gurfh/JavaTrdpLibrary)


A Java implementation of the Train Real-Time Data Protocol (TRDP) as defined in IEC 61375-2-3. This library provides comprehensive support for Process Data (PD) and Message Data (MD) communication patterns used in railway train communication networks.

## Features

- **Complete TRDP Protocol Implementation**
  - Process Data (PD) for cyclic data exchange and pull patterns
  - Message Data (MD) for request/reply communication
  - Full compliance with IEC 61375-2-3 specification
  - CRC32 checksums per IEEE 802.3

- **TRDP Data Type System**
  - Standard IEC 61375-2-3 data types (INT8, INT16, INT32, UINT8, UINT16, UINT32, REAL32, REAL64, etc.)
  - Big Endian encoding (network byte order)
  - Dataset builder for structured data
  - Type-safe encoder/decoder utilities
  - TIMEDATE timestamp support

- **Process Data (PD) Support**
  - Publisher/Subscriber pattern (Push)
  - Requester/Replier pattern (Pull)
  - High-performance session manager (`TrdpPdSession`) with shared socket and thread pool
  - Traffic shaping: staggered initial delays prevent network bursts when many publishers share the same interval
  - Cyclic auto-retransmission with configurable interval
  - Immediate out-of-cycle send
  - UDP multicast and unicast communication
  - Automatic per-ComID sequence numbering
  - Sequence counter validation per IEC 61375-2-3 (duplicate/old packet rejection, gap detection, sender restart handling)
  - Topology counter validation per IEC 61375-2-3 Table A.5 (ETB/OpTrn with wildcard support)
  - Configurable ComIDs and timeouts
  - Structured data payloads with TrdpDataset

- **Message Data (MD) Support**
  - Request/Reply pattern over UDP and TCP
  - UDP automatic retries with configurable maxRetries (0..2, default 2 per IEC 61375-2-3)
  - TCP confirmation (Mc) sent on the same connection as the request
  - Asynchronous communication with CompletableFuture
  - Configurable request handlers
  - Timeout management
  - Proper reply routing with ReplyComId and ReplyIpAddress

- **Production-Ready Features**
  - Comprehensive unit and integration tests (300+ tests)
  - Thread-safe implementation
  - Proper resource management with AutoCloseable
  - SLF4J logging integration
  - Maven build system
  - GitHub Actions CI/CD pipeline

## Requirements

- Java 17 or later
- Maven 3.8+

## Installation
 
This library is hosted on **JitPack**. To use it, you need to add the JitPack repository and then the dependency to your `pom.xml`.

**1. Add the JitPack repository:**
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>[https://jitpack.io](https://jitpack.io)</url>
    </repository>
</repositories>
```

**2. Add the dependency:**

For a stable release (e.g., based on a Git tag like `v1.0.0`):
```xml
 <dependency>
     <groupId>com.github.Gurfh</groupId>
     <artifactId>JavaTrdpLibrary</artifactId>
    <version>1.0.0</version>
 </dependency>
```

## Usage

### Process Data (PD) - Publisher/Subscriber

All PD communication is managed through `TrdpPdSession`, which consolidates publishers and subscribers onto a single shared UDP socket with just 2 threads.

```java
import com.trdp.pd.TrdpPdSession;
import com.trdp.pd.PdPublisherHandle;
import com.trdp.pd.PdSubscriberHandle;
import com.trdp.pd.PdEvent;
import com.trdp.pd.PdEventListener;

// Create a session on the standard PD port
try (TrdpPdSession session = new TrdpPdSession(17224)) {
    // Register publishers and subscribers before starting
    PdPublisherHandle pub = session.addPublisher(
        1000,            // ComID
        "239.255.0.1",   // Destination address
        17224,           // Destination port
        100_000          // Cyclic interval (100ms), 0 for no cyclic
    );

    PdSubscriberHandle sub = session.addSubscriber(
        2000,            // ComID
        "239.255.0.1",   // Multicast group to join (null for unicast)
        100_000,         // Timeout in microseconds (0 to disable)
        new PdEventListener() {
            @Override
            public void onData(PdEvent event) {
                System.out.println("Received ComID " + event.getComId());
            }

            @Override
            public void onTimeout(PdEvent event) {
                System.out.println("Timeout for ComID " + event.getComId());
            }

            @Override
            public void onValidityRestored(PdEvent event) {
                System.out.println("Validity restored for ComID " + event.getComId());
            }
        }
    );

    // Optional: set topology counters (session-wide)
    session.setTopologyCounters(1, 1);

    // Optional: traffic shaping is enabled by default, staggering cyclic
    // publishers with the same interval to prevent network bursts.
    // Disable if you need all publishers to fire simultaneously:
    // session.setTrafficShapingEnabled(false);

    // Start the session (no more registrations allowed after this)
    session.start();

    // Stage data for cyclic transmission
    pub.putData("Cyclic payload".getBytes());

    // Or send immediately
    pub.putDataImmediate("Urgent data".getBytes());

    // Query subscriber state
    if (sub.isTimedOut()) {
        System.out.println("No data received");
    }
    System.out.println("Missed packets: " + sub.getMissedCount());

    Thread.sleep(60000);
}
```

### Process Data (PD) - Pull Pattern

The library supports the PD Pull pattern where a `PdRequester` solicits data from a `TrdpPdSession` publisher (acting as a Replier), which then sends a `PD_REPLY` to subscribers.

```java
import com.trdp.pd.TrdpPdSession;
import com.trdp.pd.PdPublisherHandle;
import com.trdp.pd.PdRequester;

// Publisher session: listens for pull requests and replies
try (TrdpPdSession session = new TrdpPdSession(17224)) {
    PdPublisherHandle pub = session.addPublisher(1000, "239.255.0.1", 17224, 0);
    pub.putData("Pull me!".getBytes());
    session.start();

    // Requester: sends a pull request, asking for reply to a multicast group
    try (PdRequester requester = new PdRequester(0)) {
        requester.request(
            1000,               // ComID
            "192.168.1.50",     // Publisher IP
            17224,              // Publisher Port
            0,                  // Reply ComID (0 = use requested ComID)
            "239.255.0.1"       // Reply to this Multicast Group
        );
    }

    Thread.sleep(60000);
}
```

### Message Data (MD) - Request/Reply

#### Creating a Requester

```java
import com.trdp.md.MdRequester;
import com.trdp.md.MdReply;
import com.trdp.md.TransportProtocol;
import java.util.concurrent.CompletableFuture;

// Create a requester (default 5s reply timeout, 60s connect timeout)
try (MdRequester requester = new MdRequester(17225)) {
    // Send a UDP request (default)
    byte[] requestData = "Request Data".getBytes();
    CompletableFuture<MdReply> future = requester.sendRequest(
        2000,                    // ComID
        requestData,             // Request payload
        "192.168.1.100",        // Destination IP
        17226                    // Destination port
    );

    // Wait for reply
    MdReply reply = future.get();
    System.out.println("Reply data: " + new String(reply.getData()));

    // Send a TCP request
    CompletableFuture<MdReply> tcpFuture = requester.sendRequest(
        2001,
        "TCP Request".getBytes(),
        "192.168.1.100",
        17226,
        TransportProtocol.TCP
    );

    MdReply tcpReply = tcpFuture.get();
    System.out.println("TCP Reply: " + new String(tcpReply.getData()));
}

// Custom timeouts: 2s reply timeout, 30s connect/idle timeout
try (MdRequester requester = new MdRequester(17225, 2_000_000, 30_000_000)) {
    // Per-request timeout override (500ms for this request only)
    CompletableFuture<MdReply> future = requester.sendRequest(
        2000, "data".getBytes(), "192.168.1.100", 17226,
        TransportProtocol.UDP, null, null, 500_000);

    MdReply reply = future.get();
}

// UDP retries: default is 2 retries (3 total attempts per IEC 61375-2-3)
// Each retry reuses the same session UUID with an incremented sequence counter
try (MdRequester requester = new MdRequester(17225, 1_000_000)) {
    // Explicit retry control: 1 retry (2 total attempts), total timeout = 2s
    CompletableFuture<MdReply> future = requester.sendRequest(
        2000, "data".getBytes(), "192.168.1.100", 17226,
        TransportProtocol.UDP, null, null, 0, 1);

    // Disable retries for a specific request
    CompletableFuture<MdReply> noRetry = requester.sendRequest(
        2000, "data".getBytes(), "192.168.1.100", 17226,
        TransportProtocol.UDP, null, null, 0, 0);
}
```

#### Creating a Replier

```java
import com.trdp.md.MdReplier;
import com.trdp.md.MdRequestHandler;
import com.trdp.md.MdResponse;

// Create a request handler
MdRequestHandler handler = (request) -> {
    System.out.println("Received request for ComID " + request.getComId());
    // Process the request and return reply data
    return new MdResponse("Reply Data".getBytes());
};

// Create a replier (default 1s confirm timeout)
try (MdReplier replier = new MdReplier(17226, handler)) {
    // Start listening for requests
    replier.start();

    // Keep running to handle requests
    Thread.sleep(60000);
}

// Custom confirm timeout (3s)
try (MdReplier replier = new MdReplier(17226, handler, 3_000_000)) {
    replier.start();
    Thread.sleep(60000);
}
```

### Working with Structured Data (TRDP Data Types)

#### Using TrdpDataset with Process Data

```java
import com.trdp.util.TrdpDataset;
import com.trdp.util.TrdpDataType;
import com.trdp.pd.TrdpPdSession;
import com.trdp.pd.PdPublisherHandle;
import com.trdp.pd.PdEvent;
import com.trdp.pd.PdEventListener;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

// Create structured data for train telemetry
TrdpDataset trainData = new TrdpDataset()
    .addUInt16("trainId", 1234)
    .addUInt8("carNumber", 3)
    .addReal32("speed", 85.5f)
    .addReal32("temperature", 22.3f)
    .addBool8("doorsClosed", true)
    .addBool8("emergencyBrake", false)
    .addUInt32("odometer", 567890L)
    .addTimeDate64("timestamp", Instant.now());

// Encode the dataset to bytes
byte[] encodedData = trainData.encode();

// Define the schema for decoding
List<TrdpDataset.FieldDefinition> schema = Arrays.asList(
    new TrdpDataset.FieldDefinition("trainId", TrdpDataType.UINT16),
    new TrdpDataset.FieldDefinition("carNumber", TrdpDataType.UINT8),
    new TrdpDataset.FieldDefinition("speed", TrdpDataType.REAL32),
    new TrdpDataset.FieldDefinition("temperature", TrdpDataType.REAL32),
    new TrdpDataset.FieldDefinition("doorsClosed", TrdpDataType.BOOL8),
    new TrdpDataset.FieldDefinition("emergencyBrake", TrdpDataType.BOOL8),
    new TrdpDataset.FieldDefinition("odometer", TrdpDataType.UINT32),
    new TrdpDataset.FieldDefinition("timestamp", TrdpDataType.TIMEDATE64)
);

// Publish and subscribe using TrdpPdSession
try (TrdpPdSession session = new TrdpPdSession(19200)) {
    PdPublisherHandle pub = session.addPublisher(3000, "239.255.0.1", 19200, 0);

    session.addSubscriber(3000, "239.255.0.1", 0, new PdEventListener() {
        @Override
        public void onData(PdEvent event) {
            TrdpDataset decoded = TrdpDataset.decode(event.getData(), schema);

            int trainId = (int) decoded.getValue("trainId");
            float speed = (float) decoded.getValue("speed");
            boolean doorsClosed = (boolean) decoded.getValue("doorsClosed");

            System.out.println("Train " + trainId + " at " + speed + " km/h");
            System.out.println("Doors closed: " + doorsClosed);
        }

        @Override
        public void onTimeout(PdEvent event) { }

        @Override
        public void onValidityRestored(PdEvent event) { }
    });

    session.start();
    pub.putDataImmediate(encodedData);
    Thread.sleep(60000);
}
```

#### Using TrdpEncoder/TrdpDecoder Directly

```java
import com.trdp.util.TrdpEncoder;
import com.trdp.util.TrdpDecoder;

// Encode individual values
TrdpEncoder encoder = new TrdpEncoder(100);
encoder.putInt32(12345)
       .putReal32(3.14f)
       .putBool8(true)
       .putString("TRAIN", 16);

byte[] encoded = encoder.toByteArray();

// Decode the values
TrdpDecoder decoder = new TrdpDecoder(encoded);
int value = decoder.getInt32();
float pi = decoder.getReal32();
boolean flag = decoder.getBool8();
String label = decoder.getString(16);
```

### Supported Data Types

| Type | Java Type | Size | Description |
|------|-----------|------|-------------|
| BOOL8 | boolean | 1 byte | Boolean value |
| CHAR8 | char | 1 byte | 8-bit character |
| UTF16 | char | 2 bytes | Unicode character |
| INT8 | byte | 1 byte | Signed 8-bit integer |
| INT16 | short | 2 bytes | Signed 16-bit integer |
| INT32 | int | 4 bytes | Signed 32-bit integer |
| INT64 | long | 8 bytes | Signed 64-bit integer |
| UINT8 | int | 1 byte | Unsigned 8-bit integer (0-255) |
| UINT16 | int | 2 bytes | Unsigned 16-bit integer (0-65535) |
| UINT32 | long | 4 bytes | Unsigned 32-bit integer |
| UINT64 | long | 8 bytes | Unsigned 64-bit integer |
| REAL32 | float | 4 bytes | IEEE 754 single-precision |
| REAL64 | double | 8 bytes | IEEE 754 double-precision |
| TIMEDATE32 | Instant | 4 bytes | Seconds since epoch |
| TIMEDATE48 | Instant | 6 bytes | Seconds + microseconds |
| TIMEDATE64 | Instant | 8 bytes | Seconds + microseconds |

All multi-byte values are encoded in **Big Endian** (network byte order) format as per IEC 61375-2-3.

## Protocol Details

### TRDP Header Structure

The library implements the full TRDP header structure as specified in IEC 61375-2-3.

**PD Header (40 bytes):**

- Sequence Counter (4 bytes)
- Protocol Version (2 bytes)
- Message Type (2 bytes)
- Communication ID (4 bytes)
- ETB Topology Counter (4 bytes)
- Operational Train Topology Counter (4 bytes)
- Dataset Length (4 bytes)
- Reserved (4 bytes)
- Reply Communication ID (4 bytes)
- Reply IP Address (4 bytes)
- Header FCS (4 bytes, Little Endian CRC32)

**MD Header (116 bytes):**

- Sequence Counter (4 bytes)
- Protocol Version (2 bytes)
- Message Type (2 bytes)
- Communication ID (4 bytes)
- Dataset Length (4 bytes)
- Reply ComID (4 bytes)
- Reply IP Address (4 bytes)
- Reply Status (4 bytes)
- Session ID (16 bytes)
- Reply Timeout (4 bytes)
- Source URI (32 bytes)
- Destination URI (32 bytes)
- Header FCS (4 bytes, Little Endian CRC32)

### Message Types

The library supports the following TRDP message types:

| Type | Code | Description |
|---|---|---|
| PD | 0x5064 | Process Data |
| PD_REQUEST | 0x5072 | Process Data Request |
| PD_REPLY | 0x5070 | Process Data Reply |
| PD_ERROR | 0x5065 | Process Data Error |
| MD_REQUEST | 0x4D72 | Message Data Request |
| MD_REPLY | 0x4D70 | Message Data Reply |
| MD_CONFIRM | 0x4D63 | Message Data Confirm |
| MD_ERROR | 0x4D65 | Message Data Error |
| MD_NOTIFICATION | 0x4D6E | Message Data Notification |
| MD_REPLY_CONFIRM | 0x4D71 | Message Data Reply with Confirm |

### Default Ports and Settings

- PD Default Port: 17224 (UDP)
- MD Default Port: 17225 (UDP/TCP)
- Default Multicast Group: 239.255.0.1
- Default PD Timeout: 100ms (100,000μs)
- Default MD Reply Timeout: 5s (5,000,000μs)
- Default MD Confirm Timeout: 1s (1,000,000μs)
- Default MD Connect Timeout: 60s (60,000,000μs)
- Default MD Max Retries: 2 (UDP only, per IEC 61375-2-3 Table A.19)
- Maximum PD Data Size: 1432 bytes
- Maximum MD Data Size: 1400 bytes

## Architecture

```
com.trdp
├── protocol         # Core protocol classes
│   ├── TrdpHeader      # TRDP header encoding/decoding
│   ├── TrdpPacket      # Complete TRDP packet structure
│   ├── TrdpMessageType # Message type enumeration
│   └── TrdpConstants   # Protocol constants
├── pd               # Process Data components
│   ├── TrdpPdSession      # Shared-socket PD session manager
│   ├── PdRequester        # PD pull pattern requester
│   ├── PdPublisherHandle  # Publisher handle interface
│   ├── PdSubscriberHandle # Subscriber handle interface
│   ├── PdEvent            # Immutable PD event object
│   └── PdEventListener    # PD event listener interface
├── md               # Message Data components
│   ├── MdRequester     # MD requester implementation
│   ├── MdReplier       # MD replier implementation
│   ├── MdReply         # MD reply data structure
│   └── MdRequestHandler # Request handler interface
├── util             # Data type utilities
│   ├── TrdpDataType    # Data type enumeration
│   ├── TrdpEncoder     # Type-safe data encoder
│   ├── TrdpDecoder     # Type-safe data decoder
│   ├── TrdpDataset     # Dataset builder/parser
│   └── TrdpTopologyUtils # Shared topology validation
└── network          # Network layer
    ├── UdpTransport    # UDP transport implementation
    └── TcpTransport    # TCP transport implementation
```

## Building from Source

```bash
# Clone the repository
git clone [https://github.com/Gurfh/JavaTrdpLibrary](https://github.com/Gurfh/JavaTrdpLibrary)
cd JavaTrdpLibrary

# Build the project
mvn clean install

# Run tests
mvn test

# Run integration tests
mvn verify

# Generate JavaDoc
mvn javadoc:javadoc
```

## Testing

The library includes comprehensive unit and integration tests:

- Unit tests for all protocol components
- Integration tests for PD and MD communication
- Test coverage reporting with JaCoCo

```bash
# Run all tests with coverage
mvn clean verify

# View coverage report
open target/site/jacoco/index.html
```

## Logging

The library uses SLF4J for logging. Configure your preferred logging implementation (Logback, Log4j2, etc.) in your application.

Example Logback configuration:

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <logger name="com.trdp" level="DEBUG"/>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

## Thread Safety

All public APIs are thread-safe. The library uses:
- Concurrent collections for managing listeners and pending requests
- Atomic counters for sequence numbers
- Proper synchronization for shared resources

## Resource Management

All main components implement `AutoCloseable` for proper resource cleanup:

```java
try (TrdpPdSession session = new TrdpPdSession(17224)) {
    // Use session
} // Automatically closed
```

## Performance Considerations

- `TrdpPdSession` consolidates all publishers/subscribers onto a shared socket with 2 threads and O(1) ComId dispatch via HashMap
- Traffic shaping staggers cyclic sends to distribute network load evenly across each interval window
- MD requesters use asynchronous futures for non-blocking operations
- Multicast is used for efficient PD distribution
- Configurable timeouts for all communication patterns

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## References

- IEC 61375-2-3: Electronic railway equipment - Train communication network (TCN) - Part 2-3: TCN communication profile
- TCNOpen: Open source TRDP implementation initiative
- IEEE 802.3: Ethernet standard (for FCS calculation)

## Support

For issues, questions, or contributions:
- GitHub Issues: https://github.com/Gurfh/JavaTrdpLibrary/issues
