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
  - `clients.*` - Abstract client framework (ApipClient, TalkClient, DiskClient) with authentication
  - `data.*` - Data models organized by domain:
    - `fcData.*` - Core framework data (Module, FcEntity, ReplyBody, etc.)
    - `fchData.*` - Blockchain data (Block, Tx, OpReturn, Cash, plus Mask classes for marking/filtering)
    - `feipData.*` - Protocol data (Contact, Mail, Secret, Service, etc.)
    - `apipData.*` - API-specific data models
    - `nasa.*` - NASA (Native API Service Access) data models
  - `config.*` - Configuration and service management (Settings, Starter)
  - `utils.*` - Utilities (FcUtils, FcDate, HttpUtils, JsonUtils, etc.)
  - `db.*` - Database utilities (EsUtils, RedisUtils, LevelDbUtils)
  - `server.*` - Web server framework (FcHttpRequestHandler, HttpRequestChecker)
  - `handlers.*` - Business logic handlers (ContactManager, MailManager, SecretManager)
  - `exception.*` - Custom exception classes

### APIP (API Provider Service)
- **Location**: `APIP/`
- **Purpose**: RESTful API service for blockchain data access and operations
- **Modules**:
  - `ApipServer` - Web service with 27 API modules (APIP0-APIP26), packaged as WAR for Tomcat deployment
  - `ApipClient` - Client library for consuming APIP services
  - `ApipManager` - Service management and monitoring
- **API Modules** (each in its own package):
  - APIP0: OpenAPI - Service info, ping, totals
  - APIP1: FCDSL - Freecash Domain Specific Language for queries
  - APIP2: Blockchain - Block and transaction data
  - APIP3: Cid - Crypto ID management
  - APIP4-7: Protocols, Code, Service, App
  - APIP8-9: Group, Team - Organization management
  - APIP10-12: Box, Contact, Secret - Personal data management
  - APIP13: Mail - Messaging
  - APIP14-15: Proof, Statement - Publishing
  - APIP16: Token - Token management
  - APIP17: Crypto - Cryptographic utilities
  - APIP18: Wallet - Wallet operations (cashValid, broadcastTx)
  - APIP19: Nid - Name ID management
  - APIP20: Webhook - Event notifications
  - APIP21-25: Essay, Report, Paper, Book, Artwork - Publishing protocols
  - APIP26: Remark - Comments and annotations

### FEIP (Freecash Extension Identity Protocol)
- **Location**: `FEIP/`
- **Purpose**: Blockchain-based protocol parser and client for identity and metadata operations
- **Modules**:
  - `FeipParser` - Parses blockchain OpReturn operations, maintains protocol state in Elasticsearch
  - `FeipClient` - Creates and manages FEIP operations
- **Protocol Categories** (each with Parser and Rollbacker):
  - `identity/` - CID (Crypto ID), NID (Name ID), reputation
  - `personal/` - Contacts, Mail, Secret (encrypted messages)
  - `organize/` - Groups, Teams, organizational structures
  - `publish/` - Papers, Essays, Reports, Books, Artwork, Statements, Proofs
  - `finance/` - Tokens, Services, Apps
  - `construct/` - Protocols, Code definitions
- **Key Concepts**:
  - Reads from `opreturn/` directory (opreturn*.byte files)
  - Each protocol has rollback capabilities for blockchain reorganizations
  - Uses `ParseMark` to track parsing progress (fileName, pointer, lastHeight, lastIndex)

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
- **LevelDB** - Local key-value storage (in `db/` directory) for persistent state
- **Elasticsearch** - Full-text search and blockchain data indexing (indices named in `constants.IndicesNames`)
- **Redis** - Caching, session storage, and real-time data
- **File System**:
  - `config/` - Configuration files (JSON format)
  - `logs/` - Application logs
  - `opreturn/` - Blockchain OpReturn data files (opreturn*.byte)
  - `db/` - LevelDB databases
  - `out/` - Build artifacts (JARs and WARs)

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
- **Module dependencies**: All modules depend on FC-JDK; APIP modules depend on ApipManager; ApipServer depends on FC-JDK
- **Blockchain integration** is handled through custom libraries (`freecashj`, `shadeBitcoinj159`) from JitPack
- **Web services** are designed for deployment to **Tomcat** or similar servlet containers (WAR files)
- **Client applications** are standalone Java applications with CLI interfaces using `Menu` class
- The system is designed for **distributed deployment** across multiple servers
- **Protocol parsing** is event-driven with rollback capabilities for blockchain reorganizations
- **Naming conventions**:
  - `*Mask` classes (BlockMask, TxMask, CashMask) - Used for marking/filtering blockchain data
  - `*Manager` classes - Business logic handlers
  - `Start*` classes - Entry points for applications
  - `*Settings` classes - Configuration management
- **Key dependencies**:
  - Elasticsearch 8.4.1 for indexing
  - Jedis 5.1.0 for Redis
  - Jackson 2.15.2 for JSON
  - Netty 4.1.114 for networking
  - LevelDB 0.12 for local storage