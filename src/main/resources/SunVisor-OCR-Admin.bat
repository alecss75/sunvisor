@echo off
:: SunVisor OCR Launcher con permisos de administrador
:: Este script solicita permisos de administrador y luego ejecuta la aplicación

:: Verificar si ya tenemos permisos de administrador
net session >nul 2>&1
if %errorLevel% == 0 (
    :: Ya somos administrador, ejecutar la aplicación
    "%~dp0SunVisor-OCR.exe"
) else (
    :: No somos administrador, solicitar elevación
    powershell -Command "Start-Process '%~dp0SunVisor-OCR.exe' -Verb RunAs"
)
