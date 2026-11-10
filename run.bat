@echo off
REM Double-click (or run from a terminal) to start FinSense.
REM Uses E:\Tools\m2 and E:\Tools\m2home to save C: drive space.
cd /d "%~dp0"
set MAVEN_USER_HOME=E:\Tools\m2home
call mvnw.cmd -Dmaven.repo.local=E:\Tools\m2 -q javafx:run
if errorlevel 1 pause
