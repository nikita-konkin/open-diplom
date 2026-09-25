@echo off
rem Starts the program with the bundled runtime; memory settings: docs/prototype.md
rem Arguments are JVM options, for example -Dopendiplom.port=8091
cd /d "%~dp0"
start "open-diplom" runtime\bin\javaw.exe -Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Dfile.encoding=UTF-8 %* -jar open-diplom.jar
