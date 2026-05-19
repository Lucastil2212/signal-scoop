#!/usr/bin/env bash
# Source before ./gradlew if builds fail with "JAVA_HOME is not set":
#   source env.sh

export JAVA_HOME="${JAVA_HOME:-/home/lucas/.local/jdk/jdk-17}"
export ANDROID_HOME="${ANDROID_HOME:-/home/lucas/.local/android-sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
