@echo off
:: SunVisor OCR Debug Launcher
:: Ejecuta la aplicación y guarda todos los errores en un archivo de log

set LOG_FILE=%TEMP%\sunvisor-ocr-debug.log

echo Iniciando SunVisor OCR con debug... > "%LOG_FILE%"
echo Fecha: %DATE% %TIME% >> "%LOG_FILE%"
echo ================================== >> "%LOG_FILE%"
echo. >> "%LOG_FILE%"

"%~dp0SunVisor-OCR.exe" >> "%LOG_FILE%" 2>&1

echo. >> "%LOG_FILE%"
echo ================================== >> "%LOG_FILE%"
echo Aplicacion cerrada >> "%LOG_FILE%"

:: Abrir el archivo de log
notepad "%LOG_FILE%"
