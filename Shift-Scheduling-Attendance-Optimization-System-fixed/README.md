# Shift Scheduling & Attendance Optimization System

A comprehensive full-stack microservices-based application for employee shift scheduling, attendance tracking, and leave management. Built as a B.Tech final year project demonstrating enterprise-grade architecture.

## Features

- **Automatic Schedule Generation** — Greedy constraint-based algorithm with 10 hard constraints and fairness optimization
- **8 Microservices** — Auth, Employee, Scheduling, Attendance, Leave, Notification, Discovery, Gateway
- **Role-Based Access** — Admin, HR, Employee roles with JWT authentication
- **Attendance Tracking** — Real-time check-in/out with hour calculation and CSV export
- **Leave Management** — Apply, approve/reject workflow with balance tracking
- **Responsive UI** — 20+ Angular Material screens with role-based navigation
- **Docker Ready** — One-command deployment with Docker Compose

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1 |
| Frontend | Angular 17, Angular Material, TypeScript |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Auth | JWT (HS512) with Spring Security |
| Discovery | Netflix Eureka |
| Gateway | Spring Cloud Gateway |
| Containers | Docker, Docker Compose |
| Web Server | Nginx (frontend) |

## Architecture

```
Frontend (Angular) → API Gateway (:8080) → Eureka Discovery (:8761)
                                               ↓
                     ┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
                     │Auth:8081 │Emp:8082  │Sched:8083│Attn:8084 │Leave:8085│Notif:8086│
                     └──────────┴──────────┴──────────┴──────────┴──────────┴──────────┘
                                               ↓
                                        PostgreSQL (6 DBs)
```

## Quick Start (Docker)

### Prerequisites
- Docker Desktop installed and running
- Ports 80, 5432, 8080, 8081-8086, 8761 available

### Windows
```batch
# Double-click run.bat or run from command prompt:
run.bat
```

### Linux/Mac
```bash
docker compose up -d
```

### Access
| Service | URL |
|---------|-----|
| Frontend | http://localhost |
| API Gateway | http://localhost:8080 |
| Eureka Dashboard | http://localhost:8761 |
| Swagger (Auth) | http://127.0.0.1:8081/swagger-ui.html (dev override only, see note) |

### Default Credentials
| Role | Username | Password |
|------|----------|----------|
| Admin | admin | admin123 |
| HR | hr001 | hrpass123 |
| Employee | employee1 | emp123 |
| Employee | employee2 | emp123 |
| Employee | employee3 | emp123 |

## Local Development Setup

### Prerequisites
- Java 17+ (JDK)
- Maven 3.9+
- Node.js 20+
- PostgreSQL 16
- Angular CLI (`npm install -g @angular/cli`)

### 1. Database Setup
```sql
CREATE DATABASE shift_auth_db;
CREATE DATABASE shift_employee_db;
CREATE DATABASE shift_scheduling_db;
CREATE DATABASE shift_attendance_db;
CREATE DATABASE shift_leave_db;
CREATE DATABASE shift_notification_db;
```

### 2. Backend
```bash
cd backend

# Build all services
mvn clean package -DskipTests

# Start services in order:
# 1. Discovery Server
cd discovery-server && mvn spring-boot:run &

# 2. Wait ~15 seconds, then start all services
cd ../auth-service && mvn spring-boot:run &
cd ../employee-service && mvn spring-boot:run &
cd ../scheduling-service && mvn spring-boot:run &
cd ../attendance-service && mvn spring-boot:run &
cd ../leave-service && mvn spring-boot:run &
cd ../notification-service && mvn spring-boot:run &

# 3. Start API Gateway
cd ../api-gateway && mvn spring-boot:run &
```

### 3. Frontend
```bash
cd frontend
npm install
ng serve
# Open http://localhost:4200
```

## API Documentation

### Swagger UI
Each service exposes Swagger UI:
- Auth: http://127.0.0.1:8081/swagger-ui.html
- Employee: http://127.0.0.1:8082/swagger-ui.html
- Scheduling: http://127.0.0.1:8083/swagger-ui.html
- Attendance: http://127.0.0.1:8084/swagger-ui.html
- Leave: http://127.0.0.1:8085/swagger-ui.html
- Notification: http://127.0.0.1:8086/swagger-ui.html

### Postman
Import `docs/Shift-Scheduling-API.postman_collection.json` into Postman. The collection includes automatic token extraction from login responses.

## Scheduling Algorithm

The system uses a **Greedy Constraint-Based Scheduling Algorithm**:

1. **Sort** shifts by date and time
2. **For each shift**, evaluate all active employees:
   - **Hard Constraints** (must satisfy ALL):
     - No approved leave overlap
     - Available for shift time slot
     - No concurrent shift assignment
     - Required skills present
     - Weekly hours limit not exceeded
     - Minimum rest period observed
     - Weekly shift limit not exceeded
     - Shift not already at max capacity
   - **Soft Constraints** (scoring for ranking):
     - Fewer existing assignments = higher score (fairness)
     - Higher availability preference = higher score
     - Fewer night shifts = higher night shift score (rotation)
3. **Assign** top-scoring employees (tie-break: score DESC, employee ID ASC)

**Complexity**: O(S × E) where S = shifts, E = employees

## Project Structure

```
shift-scheduling-attendance-system/
├── backend/
│   ├── pom.xml                     # Parent Maven POM
│   ├── discovery-server/           # Eureka Server (:8761)
│   ├── api-gateway/                # Spring Cloud Gateway (:8080)
│   ├── auth-service/               # Authentication & JWT (:8081)
│   ├── employee-service/           # Employee Management (:8082)
│   ├── scheduling-service/         # Shift Scheduling (:8083)
│   ├── attendance-service/         # Attendance Tracking (:8084)
│   ├── leave-service/              # Leave Management (:8085)
│   └── notification-service/       # Notifications (:8086)
├── frontend/                       # Angular 17 application
├── docs/                           # Documentation
│   ├── abstract.md
│   ├── architecture-diagram.md
│   ├── viva-questions.md
│   └── Shift-Scheduling-API.postman_collection.json
├── docker-compose.yml
├── init-databases.sql
├── run.bat                         # Windows start script
├── stop.bat                        # Windows stop script
├── .env.example
├── .gitignore
└── README.md
```

## Stopping Services

### Docker
```bash
# Windows
stop.bat

# Linux/Mac
docker compose down

# Remove data volumes too
docker compose down -v
```

## License

This project is developed for educational purposes as a B.Tech final year project.

## Attendance Overview (Admin/HR)
Date-wise Present / Absent / On Leave / Not Scheduled report at `/attendance/overview`. See [docs/ATTENDANCE_OVERVIEW.md](docs/ATTENDANCE_OVERVIEW.md) for rules, API and tests.

## Security note: internal service ports

Only the frontend (port 80) and the API gateway (127.0.0.1:8080) are reachable from the host by default. Auth, employee, scheduling, attendance, leave and notification services are on the internal Docker network only, so every user request goes through the gateway's authorization rules. To open Swagger UI for development, start the stack with the dev override, which publishes those ports to 127.0.0.1 only:

    docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

Do not use the dev override in any shared or deployed environment.
