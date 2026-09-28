#!/bin/sh

if command -v mvn >/dev/null 2>&1 && [ "${MVNW_FORCE_WRAPPER:-false}" != "true" ]; then
  exec mvn "$@"
fi

set -e
MVNW_REPOURL="https://repo.maven.apache.org/maven2"
WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  mkdir -p .mvn/wrapper
  wget -q -O "$WRAPPER_JAR" "$MVNW_REPOURL/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
fi

exec java -Dmaven.multiModuleProjectDirectory="$PWD" -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
