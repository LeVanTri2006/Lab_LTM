@echo off
setlocal
cd /d "%~dp0"
call mvn -B compile org.apache.maven.plugins:maven-dependency-plugin:3.9.0:copy-dependencies -DincludeScope=runtime
exit /b %errorlevel%
