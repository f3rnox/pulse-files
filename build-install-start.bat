@echo off
call .\build.bat
if errorlevel 1 exit /b %errorlevel%
call .\install-adb.bat
if errorlevel 1 exit /b %errorlevel%
call .\start-adb.bat
if errorlevel 1 exit /b %errorlevel%