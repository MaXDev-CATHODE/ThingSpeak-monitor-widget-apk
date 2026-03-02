@echo off
echo Uruchamianie budowania wersji Release (JDK 17)...
set "JAVA_HOME=F:\jdk-17"
set "PATH=F:\jdk-17\bin;%PATH%"
call gradlew.bat clean assembleRelease --no-daemon
pause
