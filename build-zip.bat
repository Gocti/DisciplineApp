@echo off
setlocal

echo ========================================
echo   DisciplineApp - ZIP Build
echo ========================================
echo.

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d "%~dp0"

echo Project:
echo %CD%
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

if exist "build\DisciplineApp-1.0.0.zip" (
    del /q "build\DisciplineApp-1.0.0.zip"
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

if %ERRORLEVEL% neq 0 (
    echo.
    echo ========================================
    echo ERROR: Gradle build failed!
    echo ========================================
    exit /b 1
)

if not exist "build\libs\DisciplineApp-1.0.0-all.jar" (
    echo.
    echo ========================================
    echo ERROR: JAR file not found!
    echo ========================================
    echo.
    echo Expected:
    echo build\libs\DisciplineApp-1.0.0-all.jar
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

copy "build\libs\DisciplineApp-1.0.0-all.jar" "build\distribution\"

if exist "run.bat" (
    copy "run.bat" "build\distribution\"
)

if exist "README.md" (
    copy "README.md" "build\distribution\"
)

if exist "LICENSE" (
    copy "LICENSE" "build\distribution\"
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
    "Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::CreateFromDirectory('build\distribution', 'build\DisciplineApp-1.0.0.zip')"

if %ERRORLEVEL% neq 0 (
    echo.
    echo ========================================
    echo ERROR: ZIP creation failed!
    echo ========================================
    exit /b 1
)

REM ========================================
REM Проверка результата
REM ========================================

if not exist "build\DisciplineApp-1.0.0.zip" (
    echo.
    echo ========================================
    echo ERROR: ZIP file was not created!
    echo ========================================
    exit /b 1
)

echo.
echo ========================================
echo   ZIP CREATED SUCCESSFULLY!
echo ========================================
echo.
echo File:
echo build\DisciplineApp-1.0.0.zip
echo.

dir "build\DisciplineApp-1.0.0.zip"

echo.
echo ========================================
echo   Done!
echo ========================================

endlocal
