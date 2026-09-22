@echo off
setlocal

REM ========================================
REM DisciplineApp - Development Launcher
REM Требуется Java 25 или выше
REM ========================================

set "SCRIPT_DIR=%~dp0"
set "JAVA_EXE=java.exe"

REM Если JAVA_HOME задан, используем его
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    )
)

REM Проверяем Java
"%JAVA_EXE%" -version >nul 2>&1

if errorlevel 1 (
    echo.
    echo ERROR: Java 25 or newer was not found!
    echo.
    echo Install Java 25:
    echo https://adoptium.net/
    echo.
    echo Or set JAVA_HOME to a JDK 25 installation.
    echo.
    pause
    exit /b 1
)

REM Проверяем наличие JAR
if not exist "%SCRIPT_DIR%DisciplineApp-1.0.0-all.jar" (
    echo.
    echo ERROR: DisciplineApp JAR not found!
    echo.
    echo Expected:
    echo %SCRIPT_DIR%DisciplineApp-1.0.0-all.jar
    echo.
    echo Run the Gradle fatJar task first.
    echo.
    pause
    exit /b 1
)

echo.
echo Starting DisciplineApp...
echo.

"%JAVA_EXE%" -jar "%SCRIPT_DIR%DisciplineApp-1.0.0-all.jar" %*

set "EXIT_CODE=%ERRORLEVEL%"

endlocal & exit /b %EXIT_CODE%
