# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Java implementation of TRDP (Train Real-Time Data Protocol, IEC 61375-2-3) for railway communication systems. Supports Process Data (PD) cyclic/on-demand exchange and Message Data (MD) request/reply communication over UDP and TCP.

## Build Commands

```bash
mvn clean compile                                    # Build
mvn test                                             # Unit tests (*Test.java)
mvn verify                                           # Unit + integration tests (*IT.java)
mvn test -Dtest=TrdpPdSessionTest                    # Single test class
mvn test -Dtest=TrdpPdSessionTest#testAddPublisher   # Single test method
mvn package                                          # Package JAR
```

Java 17 minimum. CI tests on Java 17 and 21.

## Architecture

All source lives under `src/main/java/com/trdp/`, tests under `src/test/java/com/trdp/`.

### Package Structure

- **`protocol`** — Core TRDP wire format: `TrdpPdHeader` (40 bytes), `TrdpMdHeader` (116 bytes), `TrdpPacket` encode/decode, `TrdpMessageType` enum, `TrdpConstants`. All fields Big Endian except HeaderFCS (Little Endian CRC32 per IEEE 802.3).
- **`pd`** — Process Data layer: `TrdpPdSession` (shared-socket session manager for publishers and subscribers), `PdRequester` (pull pattern initiator, per-ComID sequence counters), `PdPublisherHandle`/`PdSubscriberHandle` (handle interfaces), `PdEvent` immutable event object, `PdEventListener` callback interface (onData/onTimeout/onValidityRestored).
- **`md`** — Message Data layer: `MdRequester`/`MdReplier` for async request/reply via `CompletableFuture`, `MdRequestHandler` callback interface. Supports UDP and TCP (`TransportProtocol` enum).
- **`util`** — `TrdpEncoder`/`TrdpDecoder` for type-safe Big Endian serialization of all IEC 61375-2-3 data types, `TrdpDataset` builder for structured payloads, `FcsUtils` for CRC32, `TrdpTopologyUtils` for shared topology validation.
- **`network`** — `UdpTransport` (with multicast group management), `TcpTransport` (client/server with connection pooling).

### Key Design Patterns

- All main components (`TrdpPdSession`, `PdRequester`, `MdRequester`, `MdReplier`) implement `AutoCloseable` and use separate I/O threads
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
All public `byte[]` boundaries make defensive copies: `TrdpPacket` (constructor and `getPayload()`), `ReceivedPacket` (constructor and `getData()`), `TrdpPdSession.PublisherEntry.putData()`, and `TrdpPdSession` subscriber dispatch (per-listener copy). Follow this pattern when adding new code that stores or exposes byte arrays.

### TCP message framing
TCP does not preserve message boundaries. Any TCP receiver must read the fixed-size header first (MD: 116 bytes), decode `datasetLength`, then read the exact payload — never assume a single `InputStream.read()` returns a complete TRDP packet. `MdReplier.handleTcpConnection()` and `MdRequester.startTcpReplyListener()` both use `DataInputStream.readFully()` for this. `TcpTransport.receive()` does a single read and should not be used for framed message protocols.

### Java enum static member access
Java allows accessing static enum members through an instance (e.g., `TrdpMessageType.MD_REQUEST.PD_REQUEST` compiles but silently resolves to `TrdpMessageType.PD_REQUEST`). Always use the class name to qualify enum constants.

### TimeDate48 encoding
The 16-bit fractional part of TIMEDATE48 uses binary fractions (ticks of 1/65536 second), not microseconds. This gives ~15.26us precision across the full second. TIMEDATE64 uses actual microseconds in its 32-bit fractional field.

### UINT64 range
`TrdpDecoder.getUInt64()` returns Java `long`. Values above `Long.MAX_VALUE` appear negative. Use `Long.toUnsignedString()` and `Long.compareUnsigned()` for the full unsigned range.

### PdRequester per-ComID sequence counters
`PdRequester` maintains independent sequence counters per ComID via `ConcurrentHashMap<Integer, AtomicInteger>`. The `request()` method also has a 6-parameter overload accepting an optional `byte[] payload`.

### Multicast interface selection
`UdpTransport.joinMulticastGroup(InetAddress)` auto-selects a network interface. On multi-homed systems, use the overload `joinMulticastGroup(InetAddress, NetworkInterface)` to specify the interface explicitly.

### URI field limits
`TrdpMdHeader` source/destination URI fields are 32 bytes. Strings exceeding this are truncated at valid UTF-8 character boundaries (never splits multi-byte sequences). Keep URIs short.

### MD configurable timeouts
`MdRequester` supports three constructor overloads: `(port)`, `(port, replyTimeoutUs)`, `(port, replyTimeoutUs, connectTimeoutUs)`. The `sendRequest` 8-param overload accepts `perRequestReplyTimeoutUs` (0 = use instance default). The 9-param overload adds `maxRetries` (0..2, default `DEFAULT_MD_MAX_RETRIES = 2`). `MdReplier` accepts `(port, handler)` or `(port, handler, confirmTimeoutUs)`. Defaults are in `TrdpConstants`: reply 5s, confirm 1s, connect 60s — all in microseconds matching PD convention.

### MD UDP retry behavior
`MdRequester` automatically retries UDP requests on timeout using demand-driven scheduling. Default `maxRetries` is 2 (per IEC 61375-2-3 Table A.19, value range 0..2). Each retry reuses the same session UUID with an incremented sequence counter (per Table A.24). TCP requests never retry (`effectiveRetries` forced to 0). Total timeout is `(maxRetries + 1) × replyTimeout`. Retry state is tracked in `RetryContext` (captures topology counters and a defensive copy of payload at session start). The `retryScheduler` is a single-thread `ScheduledExecutorService` created eagerly; tasks are scheduled only when retries are needed. On reply receipt, `cancelRetry(sessionId)` cancels pending retry. On `close()`, all pending retries are cancelled and the scheduler is shut down.

### MD TCP confirmation routing
`MdRequester` tracks TCP connections per session via `ConcurrentHashMap<UUID, TcpTransport> tcpSessionTransports`. When `sendRequest()` uses TCP, the `TcpTransport` is stored keyed by session UUID. When an `MD_REPLY_CONFIRM` (Mq) reply arrives over TCP, `sendConfirmation()` looks up the transport and sends the `MD_CONFIRM` (Mc) packet on the same TCP connection. The mapping is cleaned up on session completion, timeout, error, and `close()`.

### MD demand-driven timeout scheduling
`MdRequester` TCP idle eviction and `MdReplier` confirmation timeout checking use demand-driven single-shot scheduling (not fixed-rate polling). Tasks are scheduled only when entries are added and self-reschedule if entries remain after expiry. This means zero CPU overhead when no TCP connections or pending confirmations exist. The `ScheduledExecutorService` is created eagerly (with `prestartCoreThread()` for thread visibility in tests) but no task runs until needed.

### TrdpPdSession shared-socket session manager
`TrdpPdSession` manages multiple PD publishers and subscribers on a single UDP socket with a single receive thread and shared cyclic send scheduler (2 threads and 1 socket total). Registration methods (`addPublisher`/`addSubscriber`) must be called before `start()`; calling them after throws `IllegalStateException`. Each subscriber registration gets independent sequence counter tracking, timeout state, and statistics (like separate `PdSubscriber` instances). Callbacks run on the receive thread — avoid blocking in listeners. Sends are synchronized on the shared transport. One publisher per ComId; multiple subscribers per ComId are allowed. Handles returned by registration implement `PdPublisherHandle` and `PdSubscriberHandle` interfaces. `putDataImmediate()` is restricted to non-cyclic publishers (`intervalUs == 0`); calling it on a cyclic publisher throws `IllegalStateException`. This matches the C library's `tlp_putImmediate()` design where immediate sends are for application-controlled (non-cyclic) publishers only. For cyclic publishers, use `putData()` to stage data for the next cyclic send.

### TrdpPdSession traffic shaping
`TrdpPdSession` supports traffic shaping to prevent network bursts when many cyclic publishers share the same interval. Enabled by default (`isTrafficShapingEnabled()` returns `true`). Toggle via `setTrafficShapingEnabled(boolean)` before `start()`. When enabled, `computeInitialDelays()` groups cyclic publishers by interval, computes `offset = interval / N` for each group of size N, and assigns staggered initial delays `offset * index` (index 0..N-1). Safety check: if `2 * offset > interval` (i.e., group of size 1), falls back to `initialDelay = interval` — single publishers behave unchanged. Publishers with different intervals are staggered independently per group. Example: 10 publishers at 10ms interval → first sends at 0ms, 1ms, 2ms, ..., 9ms instead of all at 10ms.
