@echo off
REM Crea FisioAgenda.exe con el icono de la aplicacion y su propio Java (no hace falta tenerlo instalado).
REM Requiere un JDK 17 o superior (incluye jpackage). El resultado queda en build-exe\FisioAgenda\.
cd /d "%~dp0"
set JAR=FisioAgenda.jar
if not exist "%JAR%" set JAR=target\FisioAgenda.jar
set ICO=FisioAgenda.ico
if not exist "%ICO%" set ICO=packaging\FisioAgenda.ico
if exist build-exe rmdir /s /q build-exe
mkdir build-exe\entrada
copy /y "%JAR%" build-exe\entrada\FisioAgenda.jar >nul
jpackage --type app-image --name FisioAgenda --input build-exe\entrada --main-jar FisioAgenda.jar ^
  --icon "%ICO%" --app-version 1.0.0 --vendor "Ulises Gilio" ^
  --description "Agenda de citas para clinicas de fisioterapia" --dest build-exe
if errorlevel 1 (
  echo No se pudo crear el ejecutable. Compruebe que tiene un JDK 17 o superior en el PATH.
  pause
  exit /b 1
)
echo Creado: build-exe\FisioAgenda\FisioAgenda.exe
