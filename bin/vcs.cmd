@echo off
setlocal DisableDelayedExpansion
rem Keep the caller's current directory: that is the repository the user wants to operate on.
set "VCS_JAR=%~dp0..\vcs-cli\target\vcs-cli-0.0.1-SNAPSHOT.jar"
if not exist "%VCS_JAR%" (
    echo VCS CLI is not built. From the project root, run: mvn -pl vcs-cli -am package -DskipTests 1>&2
    exit /b 1
)
where java.exe >nul 2>nul
if errorlevel 1 (
    echo Java was not found. Install JDK 25 and add its bin folder to PATH. 1>&2
    exit /b 1
)
java.exe -jar "%VCS_JAR%" %*
exit /b %errorlevel%
