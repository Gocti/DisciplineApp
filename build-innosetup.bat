@echo off
setlocal EnableExtensions EnableDelayedExpansion

echo ========================================
echo   DisciplineApp - Build Installer
echo ========================================
echo.

cd /d "%~dp0"

REM ========================================
REM Чтение версии из gradle.properties
REM ========================================

if not exist "gradle.properties" (
    echo ERROR: gradle.properties not found!
    pause
    exit /b 1
)

for /f "tokens=1,* delims==" %%A in ('findstr /R /C:"^[ ]*appVersion[ ]*=" "gradle.properties"') do (
    set "APP_VERSION=%%B"
)

if not defined APP_VERSION (
    echo ERROR: appVersion not found in gradle.properties!
    pause
    exit /b 1
)

for /f "tokens=* delims= " %%V in ("!APP_VERSION!") do set "APP_VERSION=%%V"

set "JAR_FILE=DisciplineApp-!APP_VERSION!-all.jar"
set "INSTALLER_FILE=DisciplineApp-Setup-!APP_VERSION!.exe"

echo Application version: !APP_VERSION!
echo.

REM ========================================
REM [1/6] Поиск JDK 25
REM ========================================

echo [1/6] Searching for JDK 25...
echo.

set "JAVA_HOME="

REM Поиск Eclipse Adoptium
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-25*") do (
    if not defined JAVA_HOME (
        if exist "%%~fD\bin\java.exe" (
            if exist "%%~fD\bin\javac.exe" (
                if exist "%%~fD\bin\jpackage.exe" (
                    set "JAVA_HOME=%%~fD"
                )
            )
        )
    )
)

REM Поиск Oracle JDK и других установок
if not defined JAVA_HOME (
    for /d %%D in ("C:\Program Files\Java\jdk-25*") do (
        if not defined JAVA_HOME (
            if exist "%%~fD\bin\java.exe" (
                if exist "%%~fD\bin\javac.exe" (
                    if exist "%%~fD\bin\jpackage.exe" (
                        set "JAVA_HOME=%%~fD"
                    )
                )
            )
        )
    )
)

if not defined JAVA_HOME (
    echo ERROR: JDK 25 was not found!
    echo.
    echo Required:
    echo   java.exe
    echo   javac.exe
    echo   jpackage.exe
    echo.
    pause
    exit /b 1
)

echo JDK 25 found:
echo !JAVA_HOME!

echo.
echo Java version:
"!JAVA_HOME!\bin\java.exe" -version

echo.
echo javac version:
"!JAVA_HOME!\bin\javac.exe" -version

echo.
echo jpackage version:
"!JAVA_HOME!\bin\jpackage.exe" --version

echo.

REM ========================================
REM [2/6] Поиск Inno Setup 7
REM ========================================

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
    pause
    exit /b 1
)

echo Inno Setup compiler found:
echo !ISCC_PATH!

echo.

REM ========================================
REM [3/6] Очистка предыдущей сборки
REM ========================================

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

REM ========================================
REM [4/6] Сборка Fat JAR
REM ========================================

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

if not exist "build\libs\!JAR_FILE!" (
    echo.
    echo ERROR: Fat JAR was not created!
    echo.
    echo Expected:
    echo   build\libs\!JAR_FILE!
    pause
    exit /b 1
)

echo.
echo Fat JAR successfully created:
echo   build\libs\!JAR_FILE!

echo.

REM ========================================
REM [5/6] Создание application image
REM ========================================

echo ========================================
echo   [5/6] Creating application image
echo ========================================
echo.

"!JAVA_HOME!\bin\jpackage.exe" ^
    --type app-image ^
    --name "DisciplineApp" ^
    --app-version "!APP_VERSION!" ^
    --input "build\libs" ^
    --main-jar "!JAR_FILE!" ^
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
    pause
    exit /b 1
)

if not exist "build\DisciplineApp\runtime" (
    echo.
    echo ERROR: Java runtime was not created!
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

REM ========================================
REM [6/6] Сборка Inno Setup installer
REM ========================================

echo ========================================
echo   [6/6] Building Inno Setup installer
echo ========================================
echo.

if not exist "installer\discipline-app.iss" (
    echo ERROR: Inno Setup script not found!
    echo.
    echo Expected:
    echo   installer\discipline-app.iss
    pause
    exit /b 1
)

"!ISCC_PATH!" /DMyAppVersion=!APP_VERSION! "installer\discipline-app.iss"

if errorlevel 1 (
    echo.
    echo ERROR: Inno Setup compilation failed!
    pause
    exit /b 1
)

if not exist "installer\Output\!INSTALLER_FILE!" (
    echo.
    echo ERROR: Installer was not created!
    echo.
    echo Expected:
    echo   installer\Output\!INSTALLER_FILE!
    pause
    exit /b 1
)

echo.
echo ========================================
echo   BUILD COMPLETED SUCCESSFULLY!
echo ========================================
echo.
echo Application version:
echo   !APP_VERSION!
echo.
echo Installer:
echo   installer\Output\!INSTALLER_FILE!
echo.

dir "installer\Output\!INSTALLER_FILE!"

echo.
echo ========================================
echo   Done!
echo ========================================
echo.

pause
endlocal