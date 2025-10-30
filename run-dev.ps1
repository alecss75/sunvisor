# Script rápido para desarrollo - Compila y ejecuta sin empaquetar
# Uso: .\run-dev.ps1

$ErrorActionPreference = "Stop"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  SunVisor OCR - Ejecucion en Modo Desarrollo" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""

$MAVEN_CMD = "C:\Users\aleja\OneDrive\Desktop\personal\proyectos-personales\java\sunvisor-app\Maven\apache-maven-3.9.11-bin\apache-maven-3.9.11\bin\mvn.cmd"

# Compilar si hay cambios
Write-Host "[1/2] Compilando proyecto..." -ForegroundColor Yellow
& $MAVEN_CMD clean package -DskipTests -q

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error en compilacion" -ForegroundColor Red
    exit 1
}

Write-Host "Compilacion exitosa" -ForegroundColor Green
Write-Host ""

# Ejecutar aplicacion
Write-Host "[2/2] Ejecutando aplicacion..." -ForegroundColor Yellow
Write-Host "Presiona Ctrl+C para detener" -ForegroundColor Gray
Write-Host ""

cd target
java -jar sunvisor-0.1.jar
