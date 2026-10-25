# Shift Scheduling and Attendance Optimization System

## Abstract

The Shift Scheduling and Attendance Optimization System is a comprehensive full-stack web application designed to automate and optimize employee shift scheduling, attendance tracking, and leave management for organizations. Built using a microservices architecture with Spring Boot 3.2.5 backend services and an Angular 17 frontend, the system addresses the complex challenges of workforce management in shift-based environments.

The core innovation of this system is a **Greedy Constraint-Based Scheduling Algorithm** that automatically generates optimal shift schedules while respecting 10 hard constraints (employee availability, maximum work hours, minimum rest periods, skill requirements, leave conflicts, etc.) and multiple soft constraints for fairness optimization (workload distribution, preference scoring, night shift rotation).

### Key Features
- **8 Microservices**: Discovery Server (Eureka), API Gateway, Auth, Employee, Scheduling, Attendance, Leave, and Notification services
- **Automatic Schedule Generation**: Greedy algorithm with constraint satisfaction and fairness scoring
- **JWT-Based Authentication**: Role-based access control (Admin, HR, Employee)
- **Real-Time Attendance**: Check-in/check-out with automatic hour calculation and anomaly detection
- **Leave Management**: Application, approval workflow, and balance tracking
- **Responsive Frontend**: 20+ Angular Material screens with role-based navigation

### Technology Stack
- **Backend**: Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1, PostgreSQL, Flyway
- **Frontend**: Angular 17, Angular Material, TypeScript
- **Infrastructure**: Docker, Docker Compose, Nginx
- **Architecture**: Microservices with API Gateway and Service Discovery

### Results
The system successfully demonstrates enterprise-grade microservices architecture while providing practical workforce management capabilities. The scheduling algorithm achieves constraint-satisfying schedules with O(S × E) time complexity, where S is the number of shifts and E is the number of employees, making it suitable for real-world deployment.

**Keywords**: Microservices, Spring Boot, Angular, Shift Scheduling, Greedy Algorithm, Constraint Satisfaction, Attendance Management, REST API
