# Viva Questions and Answers

## Architecture & Design

**Q1: Why did you choose a microservices architecture over a monolithic approach?**
A: Microservices offer independent deployment, technology flexibility, and scalability for each service. In a shift scheduling system, the scheduling engine might need more resources during generation while the notification service stays lightweight. Each service can be developed, tested, and deployed independently, improving team productivity and system resilience.

**Q2: Explain the role of the API Gateway in your system.**
A: The API Gateway (Spring Cloud Gateway) serves as the single entry point for all client requests. It handles: (1) JWT token validation before forwarding requests, (2) request routing to appropriate microservices using Eureka service discovery, (3) CORS configuration, (4) load balancing across service instances, and (5) forwarding user identity headers (X-User-Id, X-User-Role) to downstream services.

**Q3: How does service discovery work in your system?**
A: We use Netflix Eureka Server. Each microservice registers itself with Eureka on startup, providing its host, port, and health status. The API Gateway queries Eureka to resolve service names (e.g., "auth-service") to actual network locations. This eliminates hardcoded URLs and supports dynamic scaling — new instances register automatically.

**Q4: What is the Database-per-Service pattern and why did you use it?**
A: Each microservice owns its own PostgreSQL database (shift_auth_db, shift_employee_db, etc.). This ensures loose coupling — services can't directly query each other's data, maintaining clear boundaries. It also allows each service to optimize its schema independently and prevents a single database from becoming a bottleneck.

## Scheduling Algorithm

**Q5: Explain your scheduling algorithm. Why greedy over other approaches?**
A: We use a Greedy Constraint-Based algorithm. It processes shifts one by one (sorted by date/time), and for each shift, evaluates all eligible employees against hard constraints (filtering) and soft constraints (scoring). The top-scoring employees are assigned. We chose greedy because: (1) It provides O(S×E) time complexity, which is practical for real-world sizes, (2) it's deterministic and explainable, (3) it satisfies all hard constraints by design, and (4) the soft-constraint scoring provides good-enough fairness without the exponential cost of optimal solutions (which would be NP-hard).

**Q6: What are the hard constraints in your scheduling engine?**
A: The 10 hard constraints are: (1) No scheduling during approved leave, (2) Employee must be available for the shift time, (3) No overlapping shift assignments, (4) Employee must possess required skills, (5) Maximum weekly hours not exceeded, (6) Minimum rest hours between consecutive shifts, (7) Maximum shifts per week not exceeded, (8) Shift maximum staffing not exceeded, (9) Employee must be active, (10) Department matching where applicable. A violation of ANY hard constraint eliminates the candidate.

**Q7: How do soft constraints improve schedule quality?**
A: Soft constraints don't eliminate candidates but influence selection through scoring: (1) **Fairness** — employees with fewer existing assignments score higher, distributing workload evenly, (2) **Preference** — availability preference ratings boost scores for preferred time slots, (3) **Night shift rotation** — employees with fewer recent night shifts score higher, preventing burnout. The combined score, with deterministic tie-breaking (score DESC, employee ID ASC), ensures reproducible schedules.

**Q8: What is the time complexity of your algorithm?**
A: O(S × E × C) where S = number of shifts, E = number of employees, C = number of constraints (constant). For each shift, we iterate all employees and check each constraint. Since C is fixed (≈13), the effective complexity is O(S × E). For a typical organization with 100 shifts/week and 50 employees, this means ~5,000 evaluations — completing in milliseconds.

## Security

**Q9: How does JWT authentication work in your system?**
A: On login, the Auth Service validates credentials against BCrypt-hashed passwords and generates a JWT token containing the userId, username, role, and expiration time, signed with HS512. The client stores this token and sends it as a Bearer token in the Authorization header. The API Gateway's JwtAuthFilter validates the signature and expiration on every request, then forwards user identity as custom headers (X-User-Id, X-User-Role) to downstream services.

**Q10: How do you implement role-based access control?**
A: Three roles exist: ADMIN, HR, EMPLOYEE. Access control is enforced at multiple layers: (1) API Gateway rejects unauthenticated requests, (2) Spring Security in each service checks roles via @PreAuthorize or SecurityConfig rules, (3) The Angular frontend uses route guards to hide unauthorized navigation, and (4) UI components conditionally render based on the user's role.

## Frontend

**Q11: Why Angular with standalone components instead of NgModule-based?**
A: Angular 17 standalone components reduce boilerplate by eliminating NgModule declarations. Each component directly imports its dependencies, making the dependency graph explicit and enabling better tree-shaking. This results in smaller bundle sizes and faster development.

**Q12: How does the frontend handle authentication state?**
A: The AuthService maintains a BehaviorSubject with the current user state, initialized from localStorage on app start. The authInterceptor automatically attaches the JWT token to all HTTP requests. On 401 responses, the interceptor triggers logout and redirects to login. Route guards prevent unauthenticated access.

## Database & Deployment

**Q13: What is Flyway and why do you use it?**
A: Flyway is a database migration tool that manages schema versioning. Each service has numbered migration scripts (V1__, V2__) in src/main/resources/db/migration/. On startup, Flyway automatically applies pending migrations in order, ensuring the database schema matches the application code. This enables reproducible deployments and version-controlled schema changes.

**Q14: Explain your Docker deployment architecture.**
A: We use Docker Compose to orchestrate all services. PostgreSQL starts first with health checks, then the Eureka discovery server. Once Eureka is healthy, all microservices start in parallel, each connecting to their own database and registering with Eureka. The API Gateway starts after all services are available. Finally, the Angular frontend runs on Nginx, proxying API requests to the gateway.

**Q15: How would you scale this system for production?**
A: Several improvements: (1) Add Redis for caching and session management, (2) Implement a message broker (RabbitMQ/Kafka) for inter-service communication, (3) Use Kubernetes instead of Docker Compose for orchestration and auto-scaling, (4) Add a circuit breaker pattern (Resilience4j) for fault tolerance, (5) Implement distributed tracing (Zipkin/Jaeger), (6) Add centralized logging (ELK stack), (7) Use a proper secrets manager instead of environment variables.
