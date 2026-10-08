#!/usr/bin/env sh
# Uruchamia aplikacje WTP Swing Navigator z ustawieniami pamieci JVM.
# Zbuduj JAR poleceniem: mvn package
# Nadpisanie parametrow: JAVA_OPTS="-Xmx1g" ./run.sh

APP_DIR=$(cd "$(dirname "$0")" && pwd)
JAR_FILE="$APP_DIR/target/wtp-swing-navigator-0.0.2-SNAPSHOT-shaded.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Nie znaleziono pliku $JAR_FILE" >&2
    echo "Zbuduj projekt poleceniem: mvn package" >&2
    exit 1
fi

if [ -z "$JAVA_OPTS" ]; then
    JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UseStringDeduplication -Dfile.encoding=UTF-8"
fi

if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD=java
fi

# JAVA_OPTS celowo bez cudzyslowow, aby rozdzielic poszczegolne parametry
exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_FILE" "$@"
