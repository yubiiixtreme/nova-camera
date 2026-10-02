#!/bin/sh
# Gradle wrapper stub — package-ready. Replace with full wrapper jar via `gradle wrapper` if needed.
APP_BASE_NAME="gradlew"
APP_HOME=$(cd "${APP_HOME:-./}" > /dev/null && pwd -P || exit)
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
exec java $DEFAULT_JVM_OPTS -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
