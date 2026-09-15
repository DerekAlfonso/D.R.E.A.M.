@echo off
REM Launches D.R.E.A.M. Builds the jar first if it is missing.
setlocal
cd /d "%~dp0"

if not exist "target\dream.jar" (
  echo Building...
  call mvn -q -B package || goto :fail
)

java -jar "target\dream.jar" %*
goto :eof

:fail
echo Build failed. Check that Maven and a JDK 11+ are installed.
pause
