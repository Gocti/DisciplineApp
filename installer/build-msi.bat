@echo off
set JAVA_HOME="C:\Program Files\Java\jdk-25"
set PATH=%JAVA_HOME%\bin;%PATH%

set APP=DisciplineApp
set MAIN_JAR=DisciplineApp.jar
set MAIN_CLASS=app.MainApp
set ICON=icons\app.ico
set UUID=9a4c5f3e-1234-4567-8901-acde12345678

jpackage ^
  --type msi ^
  --input build\libs ^
  --main-jar %MAIN_JAR% ^
  --main-class %MAIN_CLASS% ^
  --name %APP% ^
  --vendor "Discipline" ^
  --icon %ICON% ^
  --win-menu ^
  --win-shortcut ^
  --win-per-user-install ^
  --win-upgrade-uuid %UUID%
