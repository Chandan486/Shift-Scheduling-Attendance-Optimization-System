@echo off
echo ============================================
echo  Shift Scheduling & Attendance System
echo  Starting all services...
echo ============================================
echo.

REM Check Docker
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Docker is not installed or not in PATH.
    echo Please install Docker Desktop from https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)

REM Check Docker Compose
docker compose version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Docker Compose is not available.
    pause
    exit /b 1
)

echo [1/3] Creating databases and starting PostgreSQL...
docker compose up -d postgres
echo Waiting for PostgreSQL to be ready...
timeout /t 10 /nobreak >nul

echo [2/3] Starting Discovery Server...
docker compose up -d discovery-server
echo Waiting for Eureka to be ready...
timeout /t 20 /nobreak >nul

echo [3/3] Starting all microservices, gateway, and frontend...
docker compose up -d

echo.
echo ============================================
echo  All services are starting up!
echo ============================================
echo.
echo  Frontend:          http://localhost
echo  API Gateway:       http://localhost:8080
echo  Eureka Dashboard:  http://localhost:8761
echo.
echo  Default Login Credentials:
echo  Admin:    admin / admin123
echo  HR:       hr001 / hrpass123
echo  Employee: employee1 / emp123
echo.
echo  Use stop.bat to shut down all services.
echo ============================================
pause
