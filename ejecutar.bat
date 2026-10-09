@echo off
REM Lanza FisioAgenda (requiere Java 17 o superior en el PATH)
cd /d "%~dp0"
if exist FisioAgenda.jar (
  java -Dsun.java2d.d3d=false -jar FisioAgenda.jar
) else (
  java -Dsun.java2d.d3d=false -jar target\FisioAgenda.jar
)
if errorlevel 1 pause
