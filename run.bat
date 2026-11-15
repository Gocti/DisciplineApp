@echo off
setlocal

REM ========================================
REM DisciplineApp - Development Launcher
REM Требуется Java 25 или выше
REM ========================================

set "SCRIPT_DIR=%~dp0"
set "JAVA_EXE=java.exe"

REM Читаем версию из gradle.properties
if not exist "%SCRIPT_DIR%gradle.properties" (
    echo.
    echo ERROR: gradle.properties not found!
    echo.
    pause
    exit /b 1
)

for /f "tokens=1,* delims== " %%A in ('findstr /R /C:"^[ ]*appVersion[ ]*=" "%SCRIPT_DIR%gradle.properties"') do (
    set "APP_VERSION=%%B"
)

if not defined APP_VERSION (
    echo.
    echo ERROR: appVersion not found in gradle.properties!
    echo.
    pause
    exit /b 1
)

set "JAR_FILE=build\libs\DisciplineApp-%APP_VERSION%-all.jar"

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
if not exist "%SCRIPT_DIR%%JAR_FILE%" (
    echo.
    echo ERROR: DisciplineApp JAR not found!
    echo.
    echo Expected:
    echo %SCRIPT_DIR%%JAR_FILE%
    echo.
    echo Run the Gradle fatJar task first.
    echo.
    pause
    exit /b 1
)

echo.
echo Starting DisciplineApp v%APP_VERSION%...
echo.

"%JAVA_EXE%" --enable-native-access=ALL-UNNAMED -jar "%SCRIPT_DIR%%JAR_FILE%" %*

set "EXIT_CODE=%ERRORLEVEL%"

endlocal & exit /b %EXIT_CODE%