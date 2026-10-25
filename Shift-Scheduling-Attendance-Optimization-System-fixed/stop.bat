@echo off
echo ============================================
echo  Stopping all services...
echo ============================================
docker compose down
echo.
echo All services stopped.
echo To also remove data volumes: docker compose down -v
pause
