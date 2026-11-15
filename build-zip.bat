@echo off
setlocal

echo ========================================
echo   DisciplineApp - ZIP Build
echo ========================================
echo.

if not defined JAVA_HOME (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot"
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d "%~dp0"

echo Project:
echo %CD%
echo.

REM ========================================
REM Чтение версии из gradle.properties
REM ========================================

if not exist "gradle.properties" (
    echo ERROR: gradle.properties not found!
    exit /b 1
)

for /f "tokens=1,* delims==" %%A in ('findstr /R /C:"^[ ]*appVersion[ ]*=" "gradle.properties"') do (
    set "APP_VERSION=%%B"
)

if not defined APP_VERSION (
    echo ERROR: appVersion not found in gradle.properties!
    exit /b 1
)

set "APP_VERSION=%APP_VERSION: =%"
set "JAR_FILE=DisciplineApp-%APP_VERSION%-all.jar"
set "ZIP_FILE=DisciplineApp-%APP_VERSION%.zip"

echo Application version: %APP_VERSION%
echo.

REM ========================================
REM Проверка JDK 25
REM ========================================

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ========================================
    echo ERROR: JDK 25 not found!
    echo ========================================
    echo.
    echo Expected:
    echo %JAVA_HOME%
    echo.
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo ========================================
    echo ERROR: javac.exe not found!
    echo ========================================
    echo.
    exit /b 1
)

echo Java version:
"%JAVA_HOME%\bin\java.exe" -version

echo.

REM ========================================
REM Очистка
REM ========================================

echo ========================================
echo Cleaning previous distribution...
echo ========================================
echo.

if exist "build\distribution" (
    rmdir /s /q "build\distribution"
)

if exist "build\%ZIP_FILE%" (
    del /q "build\%ZIP_FILE%"
)

mkdir "build\distribution"

REM ========================================
REM Сборка Fat JAR
REM ========================================

echo ========================================
echo Building application...
echo ========================================
echo.

call gradlew.bat fatJar --no-daemon

if errorlevel 1 (
    echo.
    echo ERROR: Gradle build failed!
    exit /b 1
)

if not exist "build\libs\%JAR_FILE%" (
    echo.
    echo ERROR: JAR file not found!
    echo Expected: build\libs\%JAR_FILE%
    exit /b 1
)

echo.
echo Fat JAR created successfully.
echo.

REM ========================================
REM Копирование файлов
REM ========================================

echo ========================================
echo Preparing ZIP contents...
echo ========================================
echo.

copy /y "build\libs\%JAR_FILE%" "build\distribution\"

if errorlevel 1 (
    echo ERROR: Failed to copy JAR!
    exit /b 1
)

if exist "run.bat" (
    copy /y "run.bat" "build\distribution\"
)

if exist "gradle.properties" (
    copy /y "gradle.properties" "build\distribution\"
)

if exist "README.md" (
    copy /y "README.md" "build\distribution\"
)

if exist "LICENSE" (
    copy /y "LICENSE" "build\distribution\"
)

REM ========================================
REM Создание ZIP
REM ========================================

echo.
echo ========================================
echo Creating ZIP archive...
echo ========================================
echo.

powershell -NoProfile -Command ^
    "Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::CreateFromDirectory('build\distribution', 'build\%ZIP_FILE%')"

if errorlevel 1 (
    echo.
    echo ERROR: ZIP creation failed!
    exit /b 1
)

REM ========================================
REM Проверка результата
REM ========================================

if not exist "build\%ZIP_FILE%" (
    echo.
    echo ERROR: ZIP file was not created!
    exit /b 1
)

echo.
echo ========================================
echo   ZIP CREATED SUCCESSFULLY!
echo ========================================
echo.
echo File:
echo build\%ZIP_FILE%
echo.

dir "build\%ZIP_FILE%"

echo.
echo ========================================
echo   Done!
echo ========================================

endlocal