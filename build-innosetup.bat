@echo off
setlocal EnableExtensions EnableDelayedExpansion

echo ========================================
echo   DisciplineApp - Build Installer
echo ========================================
echo.

cd /d "%~dp0"

if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found!
    echo Project directory:
    echo %CD%
    pause
    exit /b 1
)

echo [1/6] Searching for JDK 25...
echo.

set "JAVA_HOME="

for /f "delims=" %%D in ('powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
    "$roots = @('C:\Program Files\Eclipse Adoptium','C:\Program Files\Java'); ^
     $jdks = foreach ($root in $roots) { ^
         if (Test-Path $root) { ^
             Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue ^
         } ^
     }; ^
     $jdks | Where-Object { ^
         $_.Name -match '^jdk-25' -and ^
         (Test-Path (Join-Path $_.FullName 'bin\java.exe')) -and ^
         (Test-Path (Join-Path $_.FullName 'bin\javac.exe')) -and ^
         (Test-Path (Join-Path $_.FullName 'bin\jpackage.exe')) ^
     } | ^
     Sort-Object Name -Descending | ^
     Select-Object -First 1 -ExpandProperty FullName"') do (
    set "JAVA_HOME=%%D"
)

if not defined JAVA_HOME (
    echo ERROR: JDK 25 was not found!
    echo.
    echo Required:
    echo   java.exe
    echo   javac.exe
    echo   jpackage.exe
    echo.
    echo Checked:
    echo   C:\Program Files\Eclipse Adoptium
    echo   C:\Program Files\Java
    echo.
    pause
    exit /b 1
)

echo JDK 25 found:
echo %JAVA_HOME%

echo.
echo Java version:
"%JAVA_HOME%\bin\java.exe" -version

echo.
echo javac version:
"%JAVA_HOME%\bin\javac.exe" -version

echo.
echo jpackage version:
"%JAVA_HOME%\bin\jpackage.exe" --version

echo.
echo ========================================
echo   [2/6] Searching for Inno Setup 7...
echo ========================================
echo.

set "ISCC_PATH="

if exist "C:\Program Files\Inno Setup 7\ISCC.exe" (
    set "ISCC_PATH=C:\Program Files\Inno Setup 7\ISCC.exe"
)

if not defined ISCC_PATH if exist "C:\Program Files (x86)\Inno Setup 7\ISCC.exe" (
    set "ISCC_PATH=C:\Program Files (x86)\Inno Setup 7\ISCC.exe"
)

if not defined ISCC_PATH (
    for /f "delims=" %%I in ('where ISCC.exe 2^>nul') do (
        if not defined ISCC_PATH set "ISCC_PATH=%%I"
    )
)

if not defined ISCC_PATH (
    echo ERROR: Inno Setup 7 compiler was not found!
    echo.
    echo Expected:
    echo   C:\Program Files\Inno Setup 7\ISCC.exe
    echo.
    pause
    exit /b 1
)

echo Inno Setup compiler found:
echo %ISCC_PATH%

echo.
echo ========================================
echo   [3/6] Cleaning previous build
echo ========================================
echo.

if exist "build\DisciplineApp" (
    echo Removing build\DisciplineApp...
    rmdir /s /q "build\DisciplineApp"
)

if exist "installer\Output" (
    echo Removing installer\Output...
    rmdir /s /q "installer\Output"
)

echo.
echo ========================================
echo   [4/6] Building Fat JAR
echo ========================================
echo.

call gradlew.bat fatJar --no-daemon

if errorlevel 1 (
    echo.
    echo ERROR: Gradle build failed!
    pause
    exit /b 1
)

if not exist "build\libs\DisciplineApp-1.0.0-all.jar" (
    echo.
    echo ERROR: Fat JAR was not created!
    echo.
    echo Expected:
    echo   build\libs\DisciplineApp-1.0.0-all.jar
    echo.
    pause
    exit /b 1
)

echo.
echo Fat JAR successfully created:
echo   build\libs\DisciplineApp-1.0.0-all.jar

echo.
echo ========================================
echo   [5/6] Creating application image
echo ========================================
echo.

"%JAVA_HOME%\bin\jpackage.exe" ^
    --type app-image ^
    --name "DisciplineApp" ^
    --app-version "1.0.0" ^
    --input "build\libs" ^
    --main-jar "DisciplineApp-1.0.0-all.jar" ^
    --main-class "app.MainApp" ^
    --dest "build"

if errorlevel 1 (
    echo.
    echo ERROR: jpackage failed!
    pause
    exit /b 1
)

if not exist "build\DisciplineApp\DisciplineApp.exe" (
    echo.
    echo ERROR: DisciplineApp.exe was not created!
    echo.
    pause
    exit /b 1
)

if not exist "build\DisciplineApp\runtime" (
    echo.
    echo ERROR: Java runtime was not created!
    echo.
    pause
    exit /b 1
)

echo.
echo jpackage completed successfully.
echo.
echo Application image:
echo   build\DisciplineApp\
echo.
echo Executable:
echo   build\DisciplineApp\DisciplineApp.exe
echo.
echo Runtime:
echo   build\DisciplineApp\runtime\

echo.
echo ========================================
echo   [6/6] Building Inno Setup installer
echo ========================================
echo.

if not exist "installer\discipline-app.iss" (
    echo ERROR: Inno Setup script not found!
    echo.
    echo Expected:
    echo   installer\discipline-app.iss
    echo.
    pause
    exit /b 1
)

"%ISCC_PATH%" "installer\discipline-app.iss"

if errorlevel 1 (
    echo.
    echo ERROR: Inno Setup compilation failed!
    pause
    exit /b 1
)

if not exist "installer\Output\DisciplineApp-Setup-1.0.0.exe" (
    echo.
    echo ERROR: Installer was not created!
    echo.
    echo Expected:
    echo   installer\Output\DisciplineApp-Setup-1.0.0.exe
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo   BUILD COMPLETED SUCCESSFULLY!
echo ========================================
echo.

echo Installer:
echo   installer\Output\DisciplineApp-Setup-1.0.0.exe
echo.

dir "installer\Output\DisciplineApp-Setup-1.0.0.exe"

echo.
echo ========================================
echo   Done!
echo ========================================
echo.

pause
endlocal
