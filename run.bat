@echo off
setlocal

rem Uruchamia aplikacje WTP Swing Navigator z ustawieniami pamieci JVM.
rem Zbuduj JAR poleceniem: mvn package
rem Nadpisanie parametrow: set JAVA_OPTS=-Xmx1g  (przed uruchomieniem skryptu)

set "APP_DIR=%~dp0"
set "JAR_FILE=%APP_DIR%target\wtp-swing-navigator-0.0.2-SNAPSHOT-shaded.jar"

if not exist "%JAR_FILE%" (
    echo Nie znaleziono pliku "%JAR_FILE%".
    echo Zbuduj projekt poleceniem: mvn package
    exit /b 1
)

if "%JAVA_OPTS%"=="" set "JAVA_OPTS=-Xms128m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UseStringDeduplication -Dfile.encoding=UTF-8"

if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

"%JAVA_CMD%" %JAVA_OPTS% -jar "%JAR_FILE%" %*
exit /b %ERRORLEVEL%
