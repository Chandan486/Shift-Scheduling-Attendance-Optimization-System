@echo off
REM Offline scenario harness: compiles the REAL leave, scheduling and notification service classes
REM against small stand-ins for Spring/JPA/Jackson, then runs the workflow scenarios.
REM It needs only a JDK 17+ (javac on PATH). It does NOT replace the Maven/JUnit tests.
setlocal
cd /d "%~dp0\..\.."
set "H=verification\offline-harness"
if exist "%H%\out" rmdir /s /q "%H%\out"
mkdir "%H%\out"

REM Build a javac argument file: real sources, stand-ins, and the harness.
(
  for /f "usebackq delims=" %%f in ("%H%\real-sources.txt") do @echo %%f
  for /r "%H%\stubs" %%f in (*.java) do @echo %%f
  @echo %H%\src\harness\Harness.java
) > "%H%\javac-args.txt"

javac -proc:none -d "%H%\out" @"%H%\javac-args.txt"
if errorlevel 1 (
  echo COMPILE FAILED
  exit /b 1
)
java -cp "%H%\out" harness.Harness
set RC=%errorlevel%
del "%H%\javac-args.txt" >nul 2>&1
exit /b %RC%
