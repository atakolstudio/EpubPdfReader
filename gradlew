#!/bin/sh
##############################################################################
# Gradle start up script (POSIX). Requires Gradle to be installed, or use
# Android Studio which bundles its own Gradle and does not need this script.
##############################################################################
DIR="$(cd "$(dirname "$0")" && pwd)"
exec gradle "$@" -p "$DIR"
