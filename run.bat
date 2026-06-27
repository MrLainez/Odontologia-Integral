@echo off
setlocal

cd /d "%~dp0"

echo Iniciando Odontologia Integral en http://localhost:8080
echo.
echo Asegurate de que MariaDB este encendido y que config.properties tenga tus credenciales.
echo.

call gradlew.bat run

endlocal
