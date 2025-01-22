# Teflon

Teflon is a peer-to-peer desktop chat application built using Java Swing. The project serves two main purposes:

1. Providing a functional, decentralized chat platform
2. Serving as a test bed for evaluating different AI-assisted development approaches

## Project Overview

This project is being actively developed using various AI coding assistants to evaluate their effectiveness in real-world software development. The development process helps assess:

- Code quality and consistency
- Testing coverage and reliability
- Documentation clarity
- Bug detection and resolution
- Refactoring capabilities

## Technical Architecture

### Core Components

1. **User Interface (UI)**
   - Built with Java Swing for a native desktop experience
   - Features a modern, HTML-rendered chat display
   - Supports system events, message acknowledgments, and status updates
   - Includes connection management and configuration dialogs

2. **Networking**
   - Uses UDP multicast for peer-to-peer communication
   - Supports both IPv4 and IPv6 multicast groups
   - Implements reliable message delivery with acknowledgments
   - Provides network interface selection for flexible deployment

3. **Message Handling**
   - Unique message IDs for tracking and acknowledgment
   - Support for different message types (chat, ACK, NACK, system events)
   - Message validation and checksum verification
   - HTML-safe message rendering with color coding

### Key Features

- **Decentralized Communication**: No central server required
- **Network Discovery**: Automatic peer discovery via multicast
- **Message Reliability**: Acknowledgment system for message delivery confirmation
- **Command System**: Built-in commands for status and help
- **Connection Management**: Interface selection and connection status monitoring
- **Visual Feedback**: Color-coded status indicators and message formatting

## Usage

### Requirements

- Java 21 or higher
- Maven for building

### Building

```bash
./mvnw clean package
```

### Running

```bash
java -jar target/teflon-1.2.0-jar-with-dependencies.jar
```

### Available Commands

- `/help` - Display available commands
- `/status` - Show connection status and message statistics

### Network Configuration

#### Multicast Groups

- IPv6: Uses Link-Local Scope Multicast Addresses (FF02::/16)
- IPv4: Supports Class D addresses (224.0.0.0 - 239.255.255.255)
- Custom multicast addresses can be configured

#### Network Interfaces

- Automatic detection of available network interfaces
- Support for both wired and wireless connections
- Interface selection through configuration dialog

## Development

### Testing

The project includes comprehensive test coverage:

```bash
./mvnw test
```

### Code Style

Follows standard Java conventions, enforced by Checkstyle:

```bash
./mvnw checkstyle:check
```

### Code Coverage

JaCoCo is used for code coverage analysis:

```bash
./mvnw jacoco:report
```

## References

### Networking

- [IPv6 Multicast Addresses](https://www.iana.org/assignments/ipv6-multicast-addresses/ipv6-multicast-addresses.xhtml)
- [Link-local Addresses](https://en.wikipedia.org/wiki/Link-local_address#IPv6)
- [RFC 3171 - IPv4 Multicast Guidelines](https://www.rfc-editor.org/rfc/rfc3171)

### UI Framework

- [Java Swing Documentation](https://docs.oracle.com/javase/tutorial/uiswing/)

## Version History

### 1.3.0

- Enhanced status command with HTML formatting
- Improved message display and formatting
- Refactored common code for better maintainability
- Enhanced test coverage and reliability
- Basic chat functionality
- Network interface selection
- Message acknowledgment system
