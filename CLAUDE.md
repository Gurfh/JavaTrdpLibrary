# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Java implementation of TRDP (Train Real-Time Data Protocol, IEC 61375-2-3) for railway communication systems. Supports Process Data (PD) cyclic/on-demand exchange and Message Data (MD) request/reply communication over UDP and TCP.

## Build Commands

```bash
mvn clean compile                                    # Build
mvn test                                             # Unit tests (*Test.java)
mvn verify                                           # Unit + integration tests (*IT.java)
mvn test -Dtest=PdPublisherTest                      # Single test class
mvn test -Dtest=PdPublisherTest#testPublishData      # Single test method
mvn package                                          # Package JAR
```

Java 17 minimum. CI tests on Java 17 and 21.

## Architecture

All source lives under `src/main/java/com/trdp/`, tests under `src/test/java/com/trdp/`.

### Package Structure

- **`protocol`** — Core TRDP wire format: `TrdpPdHeader` (40 bytes), `TrdpMdHeader` (116 bytes), `TrdpPacket` encode/decode, `TrdpMessageType` enum, `TrdpConstants`. All fields Big Endian except HeaderFCS (Little Endian CRC32 per IEEE 802.3).
- **`pd`** — Process Data layer: `PdPublisher` (push/pull/cyclic), `PdSubscriber` (multicast/unicast receive with sequence counter validation), `PdRequester` (pull pattern initiator, per-ComID sequence counters), `PdEvent` immutable event object, `PdEventListener` callback interface (onData/onTimeout/onValidityRestored).
- **`md`** — Message Data layer: `MdRequester`/`MdReplier` for async request/reply via `CompletableFuture`, `MdRequestHandler` callback interface. Supports UDP and TCP (`TransportProtocol` enum).
- **`util`** — `TrdpEncoder`/`TrdpDecoder` for type-safe Big Endian serialization of all IEC 61375-2-3 data types, `TrdpDataset` builder for structured payloads, `FcsUtils` for CRC32, `TrdpTopologyUtils` for shared topology validation.
- **`network`** — `UdpTransport` (with multicast group management), `TcpTransport` (client/server with connection pooling).

### Key Design Patterns

- All main components (`PdPublisher`, `PdSubscriber`, `MdRequester`, `MdReplier`) implement `AutoCloseable` and use separate I/O threads
- `TrdpEncoder` and `TrdpDataset` use fluent/builder interfaces for chaining
- Static `decode()` factory methods on headers and packets
- Payloads are 4-byte aligned with automatic padding

### Protocol Constants

- Default PD port: 17224, MD port: 17225
- Default multicast: 239.255.0.1
- Max PD data: 1432 bytes, max MD data: 1356 bytes
- Protocol version: 0x0100

## Testing Conventions

- Unit tests: `*Test.java` (run by Surefire)
- Integration tests: `*IT.java` (run by Failsafe during `verify`)
- Uses JUnit 5, Mockito, and AssertJ fluent assertions
- Integration tests use real network sockets with unique ports/ComIDs per test to avoid conflicts
- JaCoCo coverage reports generated at `target/site/jacoco/index.html`

## Known Pitfalls

### Byte array ownership
All public `byte[]` boundaries make defensive copies: `TrdpPacket` (constructor and `getPayload()`), `ReceivedPacket` (constructor and `getData()`), `PdPublisher.putData()`, and `PdSubscriber.notifyListeners()` (per-listener copy). Follow this pattern when adding new code that stores or exposes byte arrays.

### TCP message framing
TCP does not preserve message boundaries. Any TCP receiver must read the fixed-size header first (MD: 116 bytes), decode `datasetLength`, then read the exact payload — never assume a single `InputStream.read()` returns a complete TRDP packet. `MdReplier.handleTcpConnection()` and `MdRequester.startTcpReplyListener()` both use `DataInputStream.readFully()` for this. `TcpTransport.receive()` does a single read and should not be used for framed message protocols.

### Java enum static member access
Java allows accessing static enum members through an instance (e.g., `TrdpMessageType.MD_REQUEST.PD_REQUEST` compiles but silently resolves to `TrdpMessageType.PD_REQUEST`). Always use the class name to qualify enum constants.

### TimeDate48 encoding
The 16-bit fractional part of TIMEDATE48 uses binary fractions (ticks of 1/65536 second), not microseconds. This gives ~15.26us precision across the full second. TIMEDATE64 uses actual microseconds in its 32-bit fractional field.

### UINT64 range
`TrdpDecoder.getUInt64()` returns Java `long`. Values above `Long.MAX_VALUE` appear negative. Use `Long.toUnsignedString()` and `Long.compareUnsigned()` for the full unsigned range.

### PdPublisher cyclic engine
`PdPublisher` supports three send modes: (1) cyclic auto-retransmission via `start()` when constructed with `intervalUs > 0`, (2) immediate out-of-cycle send via `putDataImmediate(byte[])`, and (3) data-only update via `putData(byte[])` for pull replies or deferred cyclic pickup. The former `publish()` method was removed — use `putDataImmediate()` instead. The cyclic scheduler is a `ScheduledExecutorService` that skips sending when the data buffer is empty (no `putData()` called yet).

### PdRequester per-ComID sequence counters
`PdRequester` maintains independent sequence counters per ComID via `ConcurrentHashMap<Integer, AtomicInteger>`. The `request()` method also has a 6-parameter overload accepting an optional `byte[] payload`.

### Multicast interface selection
`UdpTransport.joinMulticastGroup(InetAddress)` auto-selects a network interface. On multi-homed systems, use the overload `joinMulticastGroup(InetAddress, NetworkInterface)` to specify the interface explicitly.

### PdSubscriber sequence counter validation
`PdSubscriber` validates incoming sequence counters per IEC 61375-2-3 Table A.3. It tracks the last sequence counter per source via a `ConcurrentHashMap` keyed by `(sourceAddress, comId, messageType)`. Rules: (1) first packet from unknown source or `seqCnt == 0` (sender restart) or subscriber was timed out → accept and reset, (2) `seqCnt > lastSeqCnt` (unsigned compare via `Integer.compareUnsigned()`) → accept and count gap as missed, (3) `seqCnt <= lastSeqCnt` → discard as duplicate/old. Timeout clears all per-source tracking. Statistics are available via `getMissedCount()`, `getDuplicateCount()`, `getTopoErrorCount()`, and `resetStatistics()`. The validity-restored event always fires before sequence validation so it is never suppressed.

### PdSubscriber topology counter validation
`PdSubscriber` validates incoming PD packets against local topology counters per IEC 61375-2-3 Table A.5. Both PD and MD layers use the shared `TrdpTopologyUtils.isValidTopology(localEtb, localOpTrn, remoteEtb, remoteOpTrn)` method. A zero value in either local or remote counter acts as a wildcard (always matches). Mismatched packets are silently discarded, incrementing `topoErrorCount`. Set local counters via `setTopologyCounters(etb, opTrn)`.

### URI field limits
`TrdpMdHeader` source/destination URI fields are 32 bytes. Strings exceeding this are truncated at valid UTF-8 character boundaries (never splits multi-byte sequences). Keep URIs short.
