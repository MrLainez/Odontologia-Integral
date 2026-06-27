Set-Location $PSScriptRoot

Write-Host "Iniciando Odontologia Integral en http://localhost:8080"
Write-Host ""
Write-Host "Asegurate de que MariaDB este encendido y que config.properties tenga tus credenciales."
Write-Host ""

.\gradlew.bat run
