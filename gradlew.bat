@echo off
REM Requires Gradle to be installed, or open this folder in Android Studio.
gradle %* -p "%~dp0"
