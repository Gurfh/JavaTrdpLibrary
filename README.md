# TRDP Protocol Library

A production-ready Java implementation of the Train Real-Time Data Protocol (TRDP) as defined in IEC 61375-2-3. This library provides comprehensive support for Process Data (PD) and Message Data (MD) communication patterns used in railway train communication networks.

## Features

- **Complete TRDP Protocol Implementation**
  - Process Data (PD) for cyclic data exchange
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
  - Publisher/Subscriber pattern
  - UDP multicast and unicast communication
  - Automatic sequence numbering
  - Configurable ComIDs and timeouts
  - Structured data payloads with TrdpDataset

- **Message Data (MD) Support**
  - Request/Reply pattern
  - Asynchronous communication with CompletableFuture
  - Configurable request handlers
  - Timeout management
  - Proper reply routing with ReplyComId and ReplyIpAddress

- **Production-Ready Features**
  - Comprehensive unit and integration tests (52 tests)
  - Thread-safe implementation
  - Proper resource management with AutoCloseable
  - SLF4J logging integration
  - Maven build system
  - GitHub Actions CI/CD pipeline

## Requirements

- Java 17 or later
- Maven 3.8+

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.trdp</groupId>
    <artifactId>trdp-protocol</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

### Process Data (PD) - Publisher/Subscriber

#### Creating a Publisher

```java
import com.trdp.pd.PdPublisher;

// Create a publisher for ComID 1000
try (PdPublisher publisher = new PdPublisher(1000, "239.255.0.1", 17224)) {
    // Publish data
    byte[] data = "Hello TRDP".getBytes();
    publisher.publish(data);
}
```

#### Creating a Subscriber

```java
import com.trdp.pd.PdSubscriber;
import com.trdp.pd.PdDataListener;

// Create a subscriber for ComID 1000
try (PdSubscriber subscriber = new PdSubscriber(1000, "239.255.0.1", 17224)) {
    // Add a data listener
    subscriber.addListener((comId, data, sequenceNumber) -> {
        System.out.println("Received data from ComID " + comId);
        System.out.println("Sequence: " + sequenceNumber);
        System.out.println("Data: " + new String(data));
    });
    
    // Start receiving data
    subscriber.start();
    
    // Keep running to receive data
    Thread.sleep(60000);
}
```

### Message Data (MD) - Request/Reply

#### Creating a Requester

```java
import com.trdp.md.MdRequester;
import com.trdp.md.MdReply;
import java.util.concurrent.CompletableFuture;

// Create a requester
try (MdRequester requester = new MdRequester(17225)) {
    // Send a request
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
}
```

#### Creating a Replier

```java
import com.trdp.md.MdReplier;
import com.trdp.md.MdRequestHandler;

// Create a request handler
MdRequestHandler handler = (comId, requestData) -> {
    System.out.println("Received request for ComID " + comId);
    // Process the request and return reply data
    return "Reply Data".getBytes();
};

// Create a replier
try (MdReplier replier = new MdReplier(17226, handler)) {
    // Start listening for requests
    replier.start();
    
    // Keep running to handle requests
    Thread.sleep(60000);
}
```

### Working with Structured Data (TRDP Data Types)

#### Using TrdpDataset with Process Data

```java
import com.trdp.util.TrdpDataset;
import com.trdp.util.TrdpDataType;
import com.trdp.pd.PdPublisher;
import com.trdp.pd.PdSubscriber;
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

// Publish the structured data
try (PdPublisher publisher = new PdPublisher(3000, "239.255.0.1", 19200)) {
    publisher.publish(encodedData);
}

// Subscribe and decode the data
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

try (PdSubscriber subscriber = new PdSubscriber(3000, "239.255.0.1", 19200)) {
    subscriber.addListener((comId, data, seqNo) -> {
        TrdpDataset decoded = TrdpDataset.decode(data, schema);
        
        int trainId = (int) decoded.getValue("trainId");
        float speed = (float) decoded.getValue("speed");
        boolean doorsClosed = (boolean) decoded.getValue("doorsClosed");
        
        System.out.println("Train " + trainId + " at " + speed + " km/h");
        System.out.println("Doors closed: " + doorsClosed);
    });
    
    subscriber.start();
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

The library implements the full TRDP header structure as specified in IEC 61375-2-3:

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

### Default Ports and Settings

- PD Default Port: 17224 (UDP)
- MD Default Port: 17225 (UDP/TCP)
- Default Multicast Group: 239.255.0.1
- Default PD Timeout: 1000ms
- Default MD Timeout: 5000ms
- Maximum PD Data Size: 1388 bytes
- Maximum MD Data Size: 1364 bytes

## Architecture

```
com.trdp
├── protocol         # Core protocol classes
│   ├── TrdpHeader      # TRDP header encoding/decoding
│   ├── TrdpPacket      # Complete TRDP packet structure
│   ├── TrdpMessageType # Message type enumeration
│   └── TrdpConstants   # Protocol constants
├── pd               # Process Data components
│   ├── PdPublisher     # PD publisher implementation
│   ├── PdSubscriber    # PD subscriber implementation
│   └── PdDataListener  # Data listener interface
├── md               # Message Data components
│   ├── MdRequester     # MD requester implementation
│   ├── MdReplier       # MD replier implementation
│   ├── MdReply         # MD reply data structure
│   └── MdRequestHandler # Request handler interface
├── util             # Data type utilities
│   ├── TrdpDataType    # Data type enumeration
│   ├── TrdpEncoder     # Type-safe data encoder
│   ├── TrdpDecoder     # Type-safe data decoder
│   └── TrdpDataset     # Dataset builder/parser
└── network          # Network layer
    └── UdpTransport    # UDP transport implementation
```

## Building from Source

```bash
# Clone the repository
git clone https://github.com/trdp/trdp-protocol.git
cd trdp-protocol

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
try (PdPublisher publisher = new PdPublisher(1000, "239.255.0.1", 17224)) {
    // Use publisher
} // Automatically closed
```

## Performance Considerations

- PD publishers and subscribers use separate threads for network I/O
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
- GitHub Issues: https://github.com/trdp/trdp-protocol/issues
- Documentation: https://github.com/trdp/trdp-protocol/wiki

## Changelog

### Version 1.0.0 (Initial Release)
- Complete TRDP protocol implementation
- Process Data (PD) publisher/subscriber support
- Message Data (MD) request/reply support
- Comprehensive test coverage
- Production-ready reliability features
