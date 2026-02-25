@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Building DisciplineApp MSI Installer
echo ========================================

REM Очищаем предыдущие сборки
rmdir /s /q build\installer 2>nul
mkdir build\installer

REM Собираем приложение через Gradle
echo Building application...
call gradlew.bat clean build --no-daemon

if %ERRORLEVEL% neq 0 (
    echo Build failed!
    exit /b 1
)

REM Копируем JAR в папку installer
copy build\libs\DisciplineApp-1.0.0.jar build\installer\

REM Создаём MSI через jpackage
echo Creating MSI installer...

set JAVA_HOME=C:\Program Files\Java\jdk-25

"%JAVA_HOME%\bin\jpackage" ^
    --type msi ^
    --name "DisciplineApp" ^
    --version "1.0.0" ^
    --input build\installer ^
    --main-jar DisciplineApp-1.0.0.jar ^
    --main-class app.MainApp ^
    --runtime-image "%JAVA_HOME%" ^
    --java-options "-Xmx512m" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --win-menu ^
    --win-menu-group "DisciplineApp" ^
    --win-shortcut ^
    --win-dir-chooser ^
    --win-per-user-install ^
    --app-version "1.0.0" ^
    --vendor "DisciplineApp" ^
    --description "Application for blocking programs and task planning" ^
    --icon src\main\resources\icon.ico ^
    --output build\installers

if %ERRORLEVEL% equ 0 (
    echo.
    echo ========================================
    echo MSI installer created successfully!
    echo Location: build\installers\DisciplineApp-1.0.0.msi
    echo ========================================
) else (
    echo.
    echo MSI creation failed!
    echo Make sure you have Java 14+ installed
)

endlocal
