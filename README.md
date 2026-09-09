# ThermoBox

A modular REST API server for managing thermal environments and user-based temperature monitoring. Built with **Kotlin** and **Jersey**, ThermoBox provides a scalable backend for temperature tracking and management across multiple rooms.

## 📋 Overview

ThermoBox is a Kotlin-based backend application designed to manage thermal environments across multiple rooms ("Salles" in French). It provides:

- **REST API** for temperature monitoring and room management
- **User authentication & authorization** with role-based access control
- **CRUD operations** for rooms, users, and temperature attributes
- **JSON logging** for audit trails and debugging
- **CORS support** for cross-origin requests
- **JPA/Hibernate integration** for persistent data storage

## 🏗️ Project Structure

```
ThermoBox/
├── RestServer/              # Main REST API server module
│   ├── src/main/kotlin/
│   │   └── ax/ibr/thermobox/restserver/
│   │       ├── Main.kt      # Server entry point
│   │       ├── resources/   # REST endpoints
│   │       └── filters/     # Request/Response filters
│   └── ...
├── Common/                  # Shared domain & service interfaces
│   ├── src/main/kotlin/
│   │   └── ax/ibr/thermobox/common/
│   │       ├── entities/    # JPA entities (User, Salle, etc.)
│   │       └── services/    # Service interfaces
│   └── ...
├── ibr-utils/               # Reusable utility library
│   ├── src/main/kotlin/
│   │   └── ax/ibr/utils/
│   │       ├── Logger.kt    # Flexible logging utility
│   │       ├── services/    # Generic service interfaces
│   │       ├── rest/        # REST utilities & annotations
│   │       └── ...
│   └── ...
└── README.md               # This file
```

## 🛠️ Tech Stack

| Technology | Purpose |
|-----------|---------|
| **Kotlin** | Primary language (98.3% of codebase) |
| **Jersey** | REST framework |
| **Jetty** | Embedded HTTP server |
| **JPA/Hibernate** | ORM & database persistence |
| **Jakarta EE** | Enterprise Java specifications |
| **kotlinx.serialization** | JSON serialization |

## 🚀 Getting Started

### Prerequisites

- **JDK 11+** (Kotlin requires Java 11 or higher)
- **Gradle** (for building and running)
- A database configured for JPA (PostgreSQL, MySQL, H2, etc.)

### Installation

1. Clone the repository:
```bash
git clone https://github.com/IbraxTheKing/ThermoBox.git
cd ThermoBox
```

2. Build the project:
```bash
gradle build
```

3. Run the server:
```bash
gradle run
```

The REST server will start on **http://localhost:8080/**

## 📡 API Features

### Core Modules

#### RestServer
- Hosts REST endpoints for temperature and room management
- Implements CORS filtering for browser-based clients
- Integrates authentication and authorization

#### Common Module
Services for domain entities:
- **UserService** - User CRUD and username/type lookup
- **SalleService** - Room CRUD and name-based queries
- **SalleTempAttrService** - Temperature attributes by room

#### ibr-utils Library
Reusable components:
- **CrudService** - Generic interface for CRUD operations
- **CrudJpaService** - JPA implementation with transaction management
- **Logger** - Flexible logging (JSON output with timestamps)
- **RequiresAuth** - Annotation-based endpoint authentication

### Authentication

Endpoints can be protected using the `@RequiresAuth` annotation:

```kotlin
@RequiresAuth
@GET
fun getProfile(): User { ... }

@RequiresAuth(roles = ["ADMIN", "MODERATOR"])
@DELETE
fun deleteUser() { ... }

@RequiresAuth(allowOwner = true, ownerParam = "userId")
@PUT
fun updateUser(@PathParam("userId") userId: Long) { ... }
```

## 📝 Logging

The project uses a flexible logging system:

```kotlin
// Info level
Logger("Application started")

// With console output
Logger("Debug info", showInConsole = true)

// Specific log level
Logger("Error occurred", LogLevel.ERROR)
```

Logs are persisted to `env/axibr/logs.json` in JSON format with timestamps.

## 🔐 CORS Configuration

The `CorsFilter` enables cross-origin requests:
- **Allowed Origins:** All (`*`)
- **Allowed Methods:** GET, POST, PUT, DELETE, OPTIONS, HEAD
- **Allowed Headers:** Content-Type, Accept, Authorization
- **Max Age:** 86400 seconds (24 hours)

## 📦 Key Services

### CrudService Interface
Generic interface for persistent entity management:
```kotlin
interface CrudService<T> {
    fun add(t: T)
    fun update(t: T)
    fun save(t: T) // Alias for update
    fun remove(t: T)
    fun delete(t: T) // Alias for remove
    fun getAll(): List<T>
    fun findAll(): List<T> // Alias for getAll
    fun getById(id: Long): T?
    fun findById(id: Long): T? // Alias for getById
}
```

### CrudJpaService Implementation
Provides JPA-based CRUD with automatic transaction management:
- Handles entity persistence, updates, and deletion
- Automatic transaction rollback on errors
- Support for detached entity merging

## 🎯 Use Cases

ThermoBox is ideal for:
- 🏢 Building management systems tracking room temperatures
- 🏭 Industrial environments monitoring thermal conditions
- 🏠 Smart home temperature control backends
- 📊 Temperature data collection and analysis platforms

## 🤝 Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is currently unlicensed. See the repository settings for license information.

## 👤 Author

**IbraxTheKing** - [GitHub Profile](https://github.com/IbraxTheKing)

## 📞 Support

For issues, questions, or suggestions, please open an [GitHub Issue](https://github.com/IbraxTheKing/ThermoBox/issues).

---

**Last Updated:** 2026-09-09  
**Language:** Kotlin (98.3%), Java (1.7%)  
**Repository:** [IbraxTheKing/ThermoBox](https://github.com/IbraxTheKing/ThermoBox)
