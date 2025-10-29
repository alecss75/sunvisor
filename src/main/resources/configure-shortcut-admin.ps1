# Script post-instalación para configurar acceso directo como administrador
# Este script modifica el acceso directo de SunVisor-OCR para ejecutarse siempre como administrador

$shortcutPath = "C:\ProgramData\Microsoft\Windows\Start Menu\Programs\SunVisor OCR\SunVisor-OCR.lnk"

if (Test-Path $shortcutPath) {
    # Leer el contenido binario del acceso directo
    $bytes = [System.IO.File]::ReadAllBytes($shortcutPath)
    
    # El byte en la posición 0x15 controla los flags del acceso directo
    # Bit 5 (0x20) = Ejecutar como administrador
    $bytes[0x15] = $bytes[0x15] -bor 0x20
    
    # Guardar el archivo modificado
    [System.IO.File]::WriteAllBytes($shortcutPath, $bytes)
    
    Write-Host "Acceso directo configurado para ejecutar como administrador"
} else {
    Write-Host "No se encontro el acceso directo"
}
