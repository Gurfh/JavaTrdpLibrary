# TRDP Protocol Library - Replit Project

## Project Overview
This is a production-ready Java implementation of the Train Real-Time Data Protocol (TRDP) as defined in IEC 61375-2-3. The library provides comprehensive support for railway train communication networks.

## Project Structure
- **Language**: Java 17
- **Build Tool**: Maven 3.8.6
- **Testing**: JUnit 5 with Mockito and AssertJ
- **CI/CD**: GitHub Actions

## Key Components
1. **Protocol Layer** (`src/main/java/com/trdp/protocol/`)
   - TRDP header encoding/decoding
   - Packet structure implementation
   - Protocol constants and message types

2. **Process Data** (`src/main/java/com/trdp/pd/`)
   - Publisher/Subscriber pattern
   - Cyclic data exchange

3. **Message Data** (`src/main/java/com/trdp/md/`)
   - Request/Reply pattern
   - Event-driven communication

4. **Network Layer** (`src/main/java/com/trdp/network/`)
   - UDP transport with multicast support

## Development Workflow
- **Build**: `mvn clean compile`
- **Test**: `mvn test`
- **Verify**: `mvn verify` (includes integration tests)
- **Package**: `mvn package`

## Testing
- Unit tests: 26 tests covering all components
- Integration tests: PD and MD communication scenarios
- Code coverage: JaCoCo reports in `target/site/jacoco/`

## Important Notes
- Header size is 40 bytes per IEC 61375-2-3 specification
- HeaderFCS is always Little Endian (exception to Big Endian byte order)
- All public components implement AutoCloseable for resource management
- Thread-safe implementation throughout

## Recent Changes
- Implemented complete TRDP protocol per IEC 61375-2-3
- Added comprehensive unit and integration tests
- Set up GitHub Actions CI/CD pipeline
- Created detailed README documentation