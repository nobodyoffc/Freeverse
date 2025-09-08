# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Freeverse is a distributed blockchain ecosystem built around the Freecash (FCH) blockchain. It implements a modular microservices architecture where each component can operate independently while communicating through standardized APIs and protocols.

## Build System and Development

This is a Maven multi-module project using Java 17.

### Build Commands
```bash
# Build entire project
mvn clean compile

# Build with tests
mvn clean test

# Package all modules (creates JARs and WARs in out/ directory)
mvn clean package

# Build specific module
mvn clean compile -pl FC-JDK
mvn clean compile -pl APIP/ApipServer
```

### Run Commands

**Main Applications:**
```bash
# FEIP Parser (blockchain protocol parser)
java -cp FEIP/FeipParser/target/classes startFEIP.StartFEIP

# FCH Parser (blockchain data parser)
java -cp FchParser/target/classes startFCH.StartFCH

# APIP Server (API service)
# Deploy APIP-v1.war to Tomcat or similar web server

# APIP Manager (service management)
java -cp APIP/ApipManager/target/classes startAPIP.StartApipManager

# DISK Manager (storage service management)
java -cp DISK/DiskManager/target/classes startManager.StartDiskManager

# Talk Server (communication service)
java -cp Talk/target/classes talkServer.StartTalkServer
```

**Client Applications:**
```bash
# APIP Client
java -cp APIP/ApipClient/target/classes startApipClient.StartApipClient

# DISK Client
java -cp DISK/DiskClient/target/classes startClient.StartDiskClient

# FEIP Client
java -cp FEIP/FeipClient/target/classes start.StartFeipClient

# Talk Client
java -cp Talk/target/classes nettyClient.StartTalkClient
```

### Testing
```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl FC-JDK
```

## Architecture Overview

The system follows a **modular microservices architecture** with the following key components:

### FC-JDK (Foundation Library)
- **Location**: `FC-JDK/`
- **Purpose**: Core library providing cryptographic operations, blockchain integration, client frameworks, and utilities
- **Key packages**: 
  - `core.crypto.*` - ECC, AES, digital signatures, key management
  - `clients.*` - Abstract client framework with authentication
  - `fcData.*` - Data models for blockchain and protocol data
  - `config.*` - Configuration and service management
  - `javaTools.*` - Utilities (HTTP, JSON, database wrappers)
  - `server.*` - Web server framework

### APIP (API Provider Service)
- **Location**: `APIP/`
- **Purpose**: RESTful API service for blockchain data access and operations
- **Modules**:
  - `ApipServer` - Web service with 27+ API modules (APIP0-APIP26)
  - `ApipClient` - Client library for consuming APIP services
  - `ApipManager` - Service management and monitoring
- **API Categories**: Blockchain data, identity management, publishing protocols, crypto utilities, wallet operations

### FEIP (Freecash Extension Identity Protocol)
- **Location**: `FEIP/`
- **Purpose**: Blockchain-based protocol parser and client for identity and metadata operations
- **Modules**:
  - `FeipParser` - Parses blockchain operations, maintains protocol state
  - `FeipClient` - Creates and manages FEIP operations
- **Protocols**: Identity (CID, NID), organizations (groups, teams), publishing (papers, artwork), personal (contacts, mail), financial (tokens, services)

### DISK (Decentralized Storage Service)
- **Location**: `DISK/`
- **Purpose**: Distributed file storage with blockchain integration
- **Modules**: `DiskServer`, `DiskClient`, `DiskManager`
- **Operations**: put, get, list, carve with content addressing and blockchain-based access control

### TALK (Communication Service)
- **Location**: `Talk/`
- **Purpose**: Real-time messaging and coordination
- **Features**: Netty-based real-time messaging, UDP/TCP support, integrated authentication

### FchParser (Blockchain Parser)
- **Location**: `FchParser/`
- **Purpose**: Processes Freecash blockchain data
- **Features**: Block parsing, Elasticsearch integration, chain analysis

## Key Design Patterns

1. **Microservices Architecture** - Independent deployable services
2. **Client-Server Pattern** - Each service provides both server and client components
3. **Event-Driven Architecture** - Blockchain events trigger protocol processing
4. **Plugin Architecture** - Modular protocol handlers in FEIP
5. **Repository Pattern** - Data access abstraction for multiple storage backends
6. **Factory Pattern** - Service and client instantiation

## Configuration

- **Location**: `config/` directory
- **Format**: JSON-based configuration files
- **Types**: Service settings, API endpoints, authentication credentials
- **Key files**: 
  - `config.json` - Main configuration
  - `*_settings.json` - Service-specific settings
  - `*Account.json` - Account and authentication data

## Database and Storage

The system uses multiple storage backends:
- **LevelDB** - Local key-value storage (in `db/` directory)
- **Elasticsearch** - Full-text search and blockchain data indexing
- **Redis** - Caching and session storage
- **File System** - Configuration, logs, and temporary files

## Security Architecture

- **ECC-based authentication** using secp256k1 digital signatures
- **AES encryption** for sensitive data and session management
- **Multi-cryptocurrency support** (Bitcoin, Ethereum, Litecoin, Dogecoin, etc.)
- **Blockchain-based access control** for services and data
- **FCH micropayment system** for API access
- **Offline security guidelines** documented in `FC-JDK/src/main/resources/security_guidelines.md`

## Common Development Workflows

### Adding New API Endpoints
1. Add new API class in `APIP/ApipServer/src/main/java/APIP*/`
2. Register API in the appropriate APIP module
3. Update API documentation and version numbers
4. Add corresponding client methods in `APIP/ApipClient/`

### Implementing New FEIP Protocols
1. Create protocol parser in `FEIP/FeipParser/src/main/java/*/`
2. Add rollback functionality for protocol operations
3. Update protocol indices in `startFEIP/IndicesFEIP.java`
4. Implement client operations in `FEIP/FeipClient/`

### Working with Blockchain Data
- Use `FchParser` to index blockchain data into Elasticsearch
- Access indexed data through APIP endpoints
- Utilize `FC-JDK` utilities for transaction parsing and wallet operations

## Service Integration

Services communicate through:
- **REST APIs** - HTTP-based service calls
- **Blockchain operations** - On-chain data and transactions  
- **Real-time messaging** - Netty-based communication via TALK service
- **Shared storage** - LevelDB, Elasticsearch, and Redis backends

## Important File Locations

- **Built JARs/WARs**: `out/` directory
- **Configuration**: `config/` directory
- **Databases**: `db/` directory
- **Logs**: `logs/` directory
- **Development tasks**: `task.md`
- **Output directory for builds**: Each module's `target/` directory

## Development Notes

- The codebase uses **Java 17** with Maven for dependency management
- **Blockchain integration** is handled through custom libraries (`freecashj`, `shadeBitcoinj159`)
- **Web services** are designed for deployment to **Tomcat** or similar servlet containers
- **Client applications** are standalone Java applications with CLI interfaces
- The system is designed for **distributed deployment** across multiple servers
- **Protocol parsing** is event-driven with rollback capabilities for blockchain reorganizations