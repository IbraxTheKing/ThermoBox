
# ThermoBox

ThermoBox is a modular REST API server designed for managing thermal environments and user-based temperature monitoring. Built with **Kotlin** and **Jersey**, it provides a scalable backend for tracking and managing temperature data across multiple locations (rooms).

## 📋 Overview

ThermoBox offers a robust solution for thermal monitoring, providing:
- **REST API** for real-time temperature monitoring and room management.
- **Role-Based Access Control (RBAC)** for secure user authentication and authorization.
- **CRUD Operations** for rooms, users, and temperature-related attributes.
- **JSON Logging** for structured audit trails and debugging.
- **CORS Support** for seamless integration with web frontends.
- **JPA/Hibernate Persistence** for reliable data storage using MySQL/MariaDB.

## 🏗️ Project Architecture

The project is organized into modular components to ensure separation of concerns:

- **RestServer**: The entry point for the REST API. It handles request/response filtering (CORS, Security), exposes endpoints, and initializes the server.
- **Business**: Contains the core business logic, including service implementations, protocol drivers (e.g., MQTT simulation), and cryptographic utilities.
- **Persistence**: The data access layer. It abstracts the database logic using JPA, providing specialized data services for different entities.
- **Common**: Defines the shared domain model (Entities) and the core service interfaces used across the entire project.
- **ibr-utils**: A shared utility library providing common functionalities such as generic CRUD services, custom logging, and security annotations.

## 🛠️ Tech Stack

| Technology | Purpose |
|------------|---------|
| **Kotlin** | Primary programming language |
| **Jersey** | REST framework |
| **Jetty** | Embedded HTTP server |
| **Jakarta EE** | Enterprise Java specifications |
| **JPA / Hibernate** | Object-Relational Mapping (ORM) |
| **MySQL** | Relational database |


## 📡 API Features

### Security
Endpoints can be protected using the `@RequiresAuth` annotation, supporting role checks and ownership verification:


## 🎯 Use Cases
- 🏢 **Building Management**: Monitoring HVAC and room temperatures.
- 🏭 **Industrial IoT**: Tracking environmental conditions in factories.
- 🏠 **Smart Home**: Backend for distributed temperature control systems.
- 📊 **Data Analytics**: Collecting environmental data for analysis.

## 👤 Author
**ib** - [GitHub Profile](https://github.com/IbraxTheKing)

---
**Language:** Kotlin / Java (Jakarta EE)