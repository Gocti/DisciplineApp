@echo off
setlocal

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d "%~dp0"

echo Starting jpackage...
echo Working directory: %CD%
echo Input directory: %CD%\build\libs

dir build\libs

echo.
echo Running jpackage...

start /wait "" "%JAVA_HOME%\bin\jpackage.exe" ^
    --type exe ^
    --name "DisciplineApp" ^
    --version "1.0.0" ^
    --input "build\libs" ^
    --main-jar "DisciplineApp-1.0.0-all.jar" ^
    --main-class "app.MainApp" ^
    --win-menu ^
    --win-shortcut ^
    --app-version "1.0.0" ^
    --vendor "DisciplineApp" ^
    --description "Test application" ^
    --output "build\installers"

echo.
echo Exit code: %ERRORLEVEL%

if exist "build\installers" (
    echo Output folder contents:
    dir build\installers
) else (
    echo Output folder does not exist
)

endlocal
