# Script de Empaquetado Completo SunVisor OCR
param(
    [switch]$SkipPython,
    [switch]$SkipBuild,
    [ValidateSet('msi', 'exe', 'app-image', 'auto')]
    [string]$InstallerType = 'auto'
)

$ErrorActionPreference = "Stop"

# Función para mostrar barra de progreso
function Show-Progress {
    param(
        [string]$Activity,
        [int]$PercentComplete,
        [string]$Status
    )
    Write-Progress -Activity $Activity -Status $Status -PercentComplete $PercentComplete
}

# Función para logging detallado
function Write-Log {
    param(
        [string]$Message,
        [string]$Level = "Info"
    )
    $timestamp = Get-Date -Format "HH:mm:ss"
    switch ($Level) {
        "Success" { Write-Host "[$timestamp] ✓ $Message" -ForegroundColor Green }
        "Error"   { Write-Host "[$timestamp] ✗ $Message" -ForegroundColor Red }
        "Warning" { Write-Host "[$timestamp] ! $Message" -ForegroundColor Yellow }
        "Info"    { Write-Host "[$timestamp]   $Message" -ForegroundColor Gray }
        default   { Write-Host "[$timestamp]   $Message" -ForegroundColor White }
    }
}

# Agregar WiX Toolset al PATH si existe
# IMPORTANTE: jpackage solo funciona con WiX v3.x, NO con WiX v4+
Write-Log "Buscando WiX Toolset v3..."
$wixPaths = @(
    # WiX v3 (requerido para jpackage --type exe)
    "C:\Program Files (x86)\WiX Toolset v3.14\bin",
    "C:\Program Files (x86)\WiX Toolset v3.11\bin",
    "C:\Program Files\WiX Toolset v3.14\bin",
    "C:\Program Files\WiX Toolset v3.11\bin"
)

$wixFound = $false
foreach ($wixPath in $wixPaths) {
    if (Test-Path $wixPath) {
        # Verificar que sea WiX v3 buscando candle.exe
        $candlePath = Join-Path $wixPath "candle.exe"
        if (Test-Path $candlePath) {
            $env:PATH = "$wixPath;$env:PATH"
            Write-Log "WiX Toolset v3 encontrado: $wixPath" "Success"
            $wixFound = $true
            break
        }
    }
}
if (-not $wixFound) {
    Write-Log "WiX Toolset v3 no encontrado (requerido para instaladores EXE)" "Warning"
    Write-Log "MSI se puede crear sin WiX. Para EXE instala WiX v3.11 desde:" "Info"
    Write-Log "https://github.com/wixtoolset/wix3/releases/tag/wix3112rtm" "Info"
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  SunVisor OCR - Empaquetado Completo" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Rutas
$ROOT = $PSScriptRoot
$PACKAGE_RESOURCES = Join-Path $ROOT "package-resources"
$PYTHON_DIR = Join-Path $PACKAGE_RESOURCES "python"
$BACKEND_DIR = Join-Path $PACKAGE_RESOURCES "backend"
$TARGET_DIR = Join-Path $ROOT "target"
$DIST_DIR = Join-Path $ROOT "dist"

$PYTHON_VERSION = "3.11.9"
$PYTHON_URL = "https://www.python.org/ftp/python/$PYTHON_VERSION/python-$PYTHON_VERSION-embed-amd64.zip"
$PYTHON_ZIP = Join-Path $PACKAGE_RESOURCES "python-embed.zip"

# ============================================================
# [1/6] Preparar estructura de carpetas
# ============================================================
Write-Host "[1/6] Preparando estructura de carpetas..." -ForegroundColor Yellow
Show-Progress -Activity "Paso 1/6: Preparando carpetas" -PercentComplete 0 -Status "Iniciando..."

Write-Log "Limpiando carpeta package-resources..."
if (Test-Path $PACKAGE_RESOURCES) { 
    Remove-Item $PACKAGE_RESOURCES -Recurse -Force 
    Write-Log "Carpeta package-resources eliminada" "Info"
}

Show-Progress -Activity "Paso 1/6: Preparando carpetas" -PercentComplete 33 -Status "Creando directorios..."
Write-Log "Creando directorios necesarios..."
New-Item -ItemType Directory -Path $PACKAGE_RESOURCES -Force | Out-Null
New-Item -ItemType Directory -Path $PYTHON_DIR -Force | Out-Null
New-Item -ItemType Directory -Path $BACKEND_DIR -Force | Out-Null

Show-Progress -Activity "Paso 1/6: Preparando carpetas" -PercentComplete 100 -Status "Completado"
Write-Log "Estructura de carpetas creada" "Success"
Write-Host ""

# ============================================================
# [2/6] Python y dependencias
# ============================================================
Write-Host "[2/6] Configurando Python y dependencias..." -ForegroundColor Yellow

if (-not $SkipPython) {
    Show-Progress -Activity "Paso 2/6: Python y dependencias" -PercentComplete 0 -Status "Verificando Python embebido..."
    
    # Descargar Python embebido
    if (-not (Test-Path $PYTHON_ZIP)) {
        Write-Log "Descargando Python $PYTHON_VERSION embebido..."
        Show-Progress -Activity "Paso 2/6: Python y dependencias" -PercentComplete 10 -Status "Descargando Python..."
        try {
            Invoke-WebRequest -Uri $PYTHON_URL -OutFile $PYTHON_ZIP -UseBasicParsing
            Write-Log "Python descargado: $(([math]::Round((Get-Item $PYTHON_ZIP).Length / 1MB, 2))) MB" "Success"
        } catch {
            Write-Log "Error al descargar Python: $($_.Exception.Message)" "Error"
            exit 1
        }
    } else {
        Write-Log "Python ya descargado, usando cache" "Info"
    }
    
    # Extraer Python
    Show-Progress -Activity "Paso 2/6: Python y dependencias" -PercentComplete 25 -Status "Extrayendo Python..."
    Write-Log "Extrayendo Python a $PYTHON_DIR..."
    Expand-Archive -Path $PYTHON_ZIP -DestinationPath $PYTHON_DIR -Force
    Write-Log "Python extraído correctamente" "Success"
    
    # Habilitar site-packages
    Show-Progress -Activity "Paso 2/6: Python y dependencias" -PercentComplete 35 -Status "Configurando site-packages..."
    Write-Log "Habilitando site-packages..."
    $pthFile = Get-ChildItem -Path $PYTHON_DIR -Filter "python*._pth" | Select-Object -First 1
    if ($pthFile) {
        (Get-Content $pthFile.FullName) -replace "^#import site", "import site" | Set-Content $pthFile.FullName
        Write-Log "Site-packages habilitado" "Success"
    } else {
        Write-Log "Archivo ._pth no encontrado" "Warning"
    }
    
    # Instalar pip
    Show-Progress -Activity "Paso 2/6: Python y dependencias" -PercentComplete 45 -Status "Instalando pip..."
    Write-Log "Descargando e instalando pip..."
    $getPip = Join-Path $PYTHON_DIR "get-pip.py"
    Invoke-WebRequest -Uri "https://bootstrap.pypa.io/get-pip.py" -OutFile $getPip -UseBasicParsing
    $pythonExe = Join-Path $PYTHON_DIR "python.exe"
    & $pythonExe $getPip --quiet --no-warn-script-location
    Write-Log "Pip instalado correctamente" "Success"
    
    # Instalar dependencias
    Show-Progress -Activity "Paso 2/6: Python y dependencias" -PercentComplete 60 -Status "Instalando dependencias..."
    Write-Log "Instalando dependencias de Python (esto puede tardar varios minutos)..."
    $requirementsFile = Join-Path $ROOT "src\main\resources\backend\requirements.txt"
    
    if (Test-Path $requirementsFile) {
        Write-Log "Usando requirements.txt: $requirementsFile" "Info"
        $startTime = Get-Date
        & $pythonExe -m pip install --quiet --no-warn-script-location -r $requirementsFile
        $elapsed = ((Get-Date) - $startTime).TotalSeconds
        Write-Log "Dependencias instaladas en $([math]::Round($elapsed, 1)) segundos" "Success"
    } else {
        Write-Log "requirements.txt no encontrado, instalando dependencias básicas..." "Warning"
        & $pythonExe -m pip install --quiet --no-warn-script-location fastapi uvicorn manga-ocr torch torchvision pillow
        Write-Log "Dependencias básicas instaladas" "Success"
    }
    
    # Verificar instalación
    Show-Progress -Activity "Paso 2/6: Python y dependencias" -PercentComplete 90 -Status "Verificando instalación..."
    Write-Log "Verificando paquetes instalados..."
    $packages = & $pythonExe -m pip list --format=freeze 2>$null | Measure-Object -Line
    Write-Log "Total de paquetes instalados: $($packages.Lines)" "Info"
    
    Show-Progress -Activity "Paso 2/6: Python y dependencias" -PercentComplete 100 -Status "Completado"
    Write-Log "Python y dependencias configurados correctamente" "Success"
} else {
    Write-Log "Omitiendo descarga e instalación de Python (usando cache)" "Warning"
}
Write-Host ""

# ============================================================
# [3/6] Copiar backend
# ============================================================
Write-Host "[3/6] Copiando archivos del backend..." -ForegroundColor Yellow
Show-Progress -Activity "Paso 3/6: Copiando backend" -PercentComplete 0 -Status "Buscando archivos..."

$backendSource = Join-Path $ROOT "src\main\resources\backend"
if (Test-Path $backendSource) {
    Write-Log "Copiando backend desde: $backendSource"
    Show-Progress -Activity "Paso 3/6: Copiando backend" -PercentComplete 30 -Status "Copiando archivos..."
    
    Copy-Item -Path "$backendSource\*" -Destination $BACKEND_DIR -Recurse -Force -Exclude "venv","__pycache__","*.pyc"
    
    Show-Progress -Activity "Paso 3/6: Copiando backend" -PercentComplete 80 -Status "Verificando archivos..."
    $backendFiles = Get-ChildItem -Path $BACKEND_DIR -File | Measure-Object
    Write-Log "Archivos copiados: $($backendFiles.Count)" "Info"
    
    Show-Progress -Activity "Paso 3/6: Copiando backend" -PercentComplete 100 -Status "Completado"
    Write-Log "Backend copiado correctamente" "Success"
} else {
    Write-Log "Backend no encontrado en: $backendSource" "Error"
    exit 1
}
Write-Host ""

# ============================================================
# [4/6] Compilar con Maven
# ============================================================
Write-Host "[4/6] Compilando proyecto con Maven..." -ForegroundColor Yellow

if (-not $SkipBuild) {
    Show-Progress -Activity "Paso 4/6: Compilando Maven" -PercentComplete 0 -Status "Buscando Maven..."
    
    # Buscar Maven en varias ubicaciones
    Write-Log "Buscando instalación de Maven..."
    $mvnCmd = $null
    $mvnLocations = @(
        "mvn",  # Maven en PATH
        "C:\Program Files\Apache\Maven\bin\mvn.cmd",
        "C:\Program Files (x86)\Apache\Maven\bin\mvn.cmd",
        "$env:MAVEN_HOME\bin\mvn.cmd",
        "C:\Users\$env:USERNAME\OneDrive\Desktop\personal\proyectos-personales\java\sunvisor-app\Maven\apache-maven-3.9.11-bin\apache-maven-3.9.11\bin\mvn.cmd",
        "$ROOT\..\Maven\apache-maven-3.9.11-bin\apache-maven-3.9.11\bin\mvn.cmd"
    )
    
    foreach ($loc in $mvnLocations) {
        try {
            $null = & $loc -version 2>&1
            if ($LASTEXITCODE -eq 0) {
                $mvnCmd = $loc
                Write-Log "Maven encontrado: $loc" "Success"
                break
            }
        } catch { }
    }
    
    if (-not $mvnCmd) {
        Write-Log "Maven no encontrado en ninguna ubicación" "Error"
        Write-Log "Instala Maven o agrégalo al PATH" "Warning"
        Write-Log "O usa el parámetro -SkipBuild si ya compilaste el proyecto" "Warning"
        exit 1
    }
    
    # Compilar proyecto
    Show-Progress -Activity "Paso 4/6: Compilando Maven" -PercentComplete 20 -Status "Compilando proyecto (puede tardar varios minutos)..."
    Write-Log "Iniciando compilación con Maven..."
    Write-Log "Comando: mvn clean package -DskipTests" "Info"
    
    $startTime = Get-Date
    $buildOutput = & $mvnCmd clean package -DskipTests -f "$ROOT\pom.xml" 2>&1
    
    if ($LASTEXITCODE -ne 0) {
        Write-Log "Error durante la compilación" "Error"
        Write-Log "Salida de Maven:" "Error"
        $buildOutput | ForEach-Object { Write-Host $_ -ForegroundColor Red }
        exit 1
    }
    
    $elapsed = ((Get-Date) - $startTime).TotalSeconds
    Show-Progress -Activity "Paso 4/6: Compilando Maven" -PercentComplete 90 -Status "Verificando resultado..."
    
    # Verificar JAR generado
    $jarFile = Get-ChildItem -Path $TARGET_DIR -Filter "sunvisor-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jarFile) {
        $jarSizeMB = [math]::Round($jarFile.Length / 1MB, 2)
        Write-Log "JAR generado: $($jarFile.Name) ($jarSizeMB MB)" "Success"
    } else {
        Write-Log "JAR no encontrado después de la compilación" "Error"
        exit 1
    }
    
    Show-Progress -Activity "Paso 4/6: Compilando Maven" -PercentComplete 100 -Status "Completado"
    Write-Log "Compilación exitosa en $([math]::Round($elapsed, 1)) segundos" "Success"
} else {
    Write-Log "Omitiendo compilación (usando JAR existente)" "Warning"
    
    # Verificar que existe el JAR
    $jarFile = Get-ChildItem -Path $TARGET_DIR -Filter "sunvisor-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jarFile) {
        Write-Log "No se encontró JAR compilado en target/" "Error"
        Write-Log "Debes compilar el proyecto primero o quitar el parámetro -SkipBuild" "Warning"
        exit 1
    }
    Write-Log "Usando JAR existente: $($jarFile.Name)" "Info"
}
Write-Host ""

# ============================================================
# [5/6] Preparar archivos para empaquetado
# ============================================================
Write-Host "[5/6] Preparando archivos para jpackage..." -ForegroundColor Yellow
Show-Progress -Activity "Paso 5/6: Preparando empaquetado" -PercentComplete 0 -Status "Extrayendo DLLs nativas de JavaFX..."

# Extraer DLLs nativas de JavaFX desde los JARs
Write-Log "Extrayendo DLLs nativas de JavaFX..."
$javafxNativesDir = Join-Path $TARGET_DIR "javafx-natives"
if (Test-Path $javafxNativesDir) {
    Remove-Item $javafxNativesDir -Recurse -Force
}
New-Item -ItemType Directory -Path $javafxNativesDir -Force | Out-Null

# Buscar JARs de JavaFX con clasificador -win
$javafxWinJars = Get-ChildItem -Path (Join-Path $TARGET_DIR "libs") -Filter "*javafx*-win.jar"
$dllCount = 0
foreach ($jar in $javafxWinJars) {
    Write-Log "  - Extrayendo: $($jar.Name)" "Info"
    # Copiar JAR como ZIP temporalmente (PowerShell Expand-Archive solo acepta .zip)
    try {
        $tempZip = Join-Path $javafxNativesDir "$($jar.BaseName).zip"
        Copy-Item -Path $jar.FullName -Destination $tempZip -Force
        
        $tempExtract = Join-Path $javafxNativesDir $jar.BaseName
        Expand-Archive -Path $tempZip -DestinationPath $tempExtract -Force
        
        # Copiar solo las DLLs al directorio raíz de javafx-natives
        $dllFiles = Get-ChildItem -Path $tempExtract -Filter "*.dll" -Recurse
        foreach ($dll in $dllFiles) {
            Copy-Item -Path $dll.FullName -Destination $javafxNativesDir -Force
            $dllCount++
        }
        
        # Limpiar archivos temporales
        Remove-Item $tempZip -Force
        Remove-Item $tempExtract -Recurse -Force
    } catch {
        Write-Log "    Error extrayendo $($jar.Name): $($_.Exception.Message)" "Warning"
    }
}

# Mostrar las DLLs importantes de JavaFX (solo las esenciales)
$criticalDlls = @("glass.dll", "prism_d3d.dll", "prism_sw.dll", "javafx_font.dll")
foreach ($dllName in $criticalDlls) {
    if (Test-Path (Join-Path $javafxNativesDir $dllName)) {
        Write-Log "    ✓ $dllName encontrada" "Success"
    }
}

Write-Log "DLLs nativas extraídas: $dllCount" "Success"

Show-Progress -Activity "Paso 5/6: Preparando empaquetado" -PercentComplete 30 -Status "Copiando Python a target/..."

# Copiar Python embebido a target/
Write-Log "Copiando Python embebido a target/..."
if (Test-Path (Join-Path $PACKAGE_RESOURCES "python")) {
    $targetPython = Join-Path $TARGET_DIR "python"
    if (Test-Path $targetPython) { 
        Write-Log "Eliminando Python anterior en target/" "Info"
        Remove-Item $targetPython -Recurse -Force 
    }
    
    Show-Progress -Activity "Paso 5/6: Preparando empaquetado" -PercentComplete 20 -Status "Copiando Python..."
    Copy-Item -Path (Join-Path $PACKAGE_RESOURCES "python") -Destination $targetPython -Recurse -Force
    
    $pythonSize = (Get-ChildItem -Path $targetPython -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB
    Write-Log "Python copiado a target/ ($([math]::Round($pythonSize, 2)) MB)" "Success"
} else {
    Write-Log "Python no encontrado en package-resources/" "Warning"
}

# Copiar backend a target/
Show-Progress -Activity "Paso 5/6: Preparando empaquetado" -PercentComplete 50 -Status "Copiando backend a target/..."
Write-Log "Copiando backend a target/..."
if (Test-Path $BACKEND_DIR) {
    $targetBackend = Join-Path $TARGET_DIR "backend"
    if (Test-Path $targetBackend) { 
        Write-Log "Eliminando backend anterior en target/" "Info"
        Remove-Item $targetBackend -Recurse -Force 
    }
    
    Copy-Item -Path $BACKEND_DIR -Destination $targetBackend -Recurse -Force
    $backendFiles = Get-ChildItem -Path $targetBackend -File -Recurse | Measure-Object
    Write-Log "Backend copiado a target/ ($($backendFiles.Count) archivos)" "Success"
} else {
    Write-Log "Backend no encontrado" "Warning"
}

# Copiar icono a package-resources para jpackage
Show-Progress -Activity "Paso 5/6: Preparando empaquetado" -PercentComplete 75 -Status "Copiando icono..."
$iconSource = Join-Path $ROOT "src\main\resources\icon.ico"
if (Test-Path $iconSource) {
    $iconDest = Join-Path $PACKAGE_RESOURCES "icon.ico"
    Copy-Item -Path $iconSource -Destination $iconDest -Force
    Write-Log "Icono copiado a package-resources/" "Success"
} else {
    Write-Log "Icono no encontrado en src/main/resources/icon.ico" "Warning"
}

# Copiar manifiesto a package-resources para jpackage
$manifestSource = Join-Path $ROOT "src\main\resources\SunVisor-OCR.manifest"
if (Test-Path $manifestSource) {
    $manifestDest = Join-Path $PACKAGE_RESOURCES "SunVisor-OCR.manifest"
    Copy-Item -Path $manifestSource -Destination $manifestDest -Force
    Write-Log "Manifiesto copiado a package-resources/" "Success"
} else {
    Write-Log "Manifiesto no encontrado en src/main/resources/SunVisor-OCR.manifest" "Warning"
}

# Copiar launcher de administrador a target/
$launcherSource = Join-Path $ROOT "src\main\resources\SunVisor-OCR-Admin.bat"
if (Test-Path $launcherSource) {
    $launcherDest = Join-Path $TARGET_DIR "SunVisor-OCR-Admin.bat"
    Copy-Item -Path $launcherSource -Destination $launcherDest -Force
    Write-Log "Launcher de administrador copiado a target/" "Success"
} else {
    Write-Log "Launcher de administrador no encontrado" "Warning"
}

# Copiar DLLs de JavaFX a target/ para que jpackage las incluya en app/
Show-Progress -Activity "Paso 5/6: Preparando empaquetado" -PercentComplete 80 -Status "Copiando DLLs nativas al directorio de input..."
if (Test-Path $javafxNativesDir) {
    $dllFiles = Get-ChildItem -Path $javafxNativesDir -Filter "*.dll"
    foreach ($dll in $dllFiles) {
        Copy-Item -Path $dll.FullName -Destination $TARGET_DIR -Force
    }
    Write-Log "DLLs de JavaFX copiadas a target/ para empaquetado" "Success"
}

# Limpiar directorio classes/ para evitar conflicto de módulos duplicados
Show-Progress -Activity "Paso 5/6: Preparando empaquetado" -PercentComplete 90 -Status "Limpiando directorio classes..."
$classesDir = Join-Path $TARGET_DIR "classes"
if (Test-Path $classesDir) {
    Remove-Item $classesDir -Recurse -Force -ErrorAction SilentlyContinue
    Write-Log "Directorio classes/ eliminado para evitar conflictos" "Success"
}

Show-Progress -Activity "Paso 5/6: Preparando empaquetado" -PercentComplete 100 -Status "Completado"
Write-Log "Archivos preparados para empaquetado" "Success"
Write-Host ""

# ============================================================
# [6/6] Crear ejecutable con jpackage
# ============================================================
Write-Host "[6/6] Creando instalador ejecutable..." -ForegroundColor Yellow
Show-Progress -Activity "Paso 6/6: Creando ejecutable" -PercentComplete 0 -Status "Verificando JAR..."

# Verificar JAR
$jarFile = Get-ChildItem -Path $TARGET_DIR -Filter "sunvisor-*.jar" | Select-Object -First 1
if (-not $jarFile) {
    Write-Log "JAR no encontrado en target/" "Error"
    Write-Log "Archivos encontrados en target/:" "Info"
    Get-ChildItem -Path $TARGET_DIR -Filter "*.jar" | ForEach-Object { Write-Log "  - $($_.Name)" "Info" }
    exit 1
}
Write-Log "JAR encontrado: $($jarFile.Name)" "Success"

# Limpiar dist/
Show-Progress -Activity "Paso 6/6: Creando ejecutable" -PercentComplete 10 -Status "Limpiando carpeta dist/..."
Write-Log "Limpiando carpeta dist/..."
if (Test-Path $DIST_DIR) {
    try {
        Remove-Item $DIST_DIR -Recurse -Force -ErrorAction Stop
        Write-Log "Carpeta dist/ limpiada" "Success"
    } catch {
        Write-Log "Carpeta dist/ en uso, intentando limpieza parcial..." "Warning"
        Get-ChildItem $DIST_DIR -File -Recurse -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
    }
}
New-Item -ItemType Directory -Path $DIST_DIR -Force | Out-Null

# Buscar jpackage
Show-Progress -Activity "Paso 6/6: Creando ejecutable" -PercentComplete 15 -Status "Buscando jpackage..."
Write-Log "Buscando jpackage..."
$jpackageCmd = $null
$jpackageLocations = @(
    "jpackage",
    "$env:JAVA_HOME\bin\jpackage.exe",
    "C:\Program Files\Java\jdk-17\bin\jpackage.exe",
    "C:\Program Files\Java\jdk-21\bin\jpackage.exe"
)

foreach ($loc in $jpackageLocations) {
    try {
        $version = & $loc --version 2>&1
        if ($LASTEXITCODE -eq 0) {
            $jpackageCmd = $loc
            Write-Log "jpackage encontrado: $loc (versión: $version)" "Success"
            break
        }
    } catch { }
}

if (-not $jpackageCmd) {
    Write-Log "jpackage no encontrado" "Error"
    Write-Log "Necesitas JDK 17+ instalado" "Warning"
    Write-Log "Descarga desde: https://adoptium.net/" "Info"
    exit 1
}

# Determinar tipo de paquete según el sistema operativo
$os = [System.Environment]::OSVersion.Platform

if ($os -eq "Win32NT") {
    Write-Log "Sistema operativo: Windows" "Info"
    Write-Log "Intentando crear instalador nativo..." "Info"
    
    # Obtener path de JavaFX desde Maven
    $mavenRepo = Join-Path $env:USERPROFILE ".m2\repository"
    $javafxPath = Join-Path $mavenRepo "org\openjfx\javafx-base\17.0.6"

    # SOLUCIÓN HÍBRIDA: module-path SOLO para JavaFX (para jlink)
    # La app y tess4j irán al classpath (modo clásico)
    $libsPath = Join-Path $TARGET_DIR "libs"
    $modulePath = $libsPath
    
    Write-Log "Module-path (solo JavaFX): $modulePath" "Info"
    
    # Temporalmente permitir errores para probar fallbacks
    $ErrorActionPreference = "Continue"
    
    # Determinar qué tipo de instalador crear
    $installerCreated = $false
    $installerTypeVar = ""
    
    # Función para crear instalador
    function Create-Installer {
        param([string]$Type, [string]$Description)
        
        Write-Log "Creando instalador $Description..." "Info"
        Show-Progress -Activity "Paso 6/6: Creando ejecutable" -PercentComplete 50 -Status "Creando $Description..."
        
        $startTime = Get-Date
        
        $jpackageArgs = @(
            "--input", $TARGET_DIR,
            "--name", "SunVisor-OCR",
            "--main-jar", $jarFile.Name,
            "--main-class", "com.ucaribe.sunvisor.Launcher",
            "--module-path", $modulePath,
            "--add-modules", "javafx.controls,javafx.fxml,javafx.swing,javafx.graphics,javafx.base,java.logging,java.prefs,java.net.http,java.desktop",
            "--type", $Type,
            "--dest", $DIST_DIR,
            "--app-version", "0.1.0",
            "--description", "OCR para manga con soporte multiidioma",
            "--vendor", "UCaribe",
            "--resource-dir", $PACKAGE_RESOURCES,
            "--java-options", "--add-opens=javafx.graphics/javafx.css=ALL-UNNAMED",
            "--java-options", "--add-opens=javafx.graphics/com.sun.javafx.css=ALL-UNNAMED",
            "--java-options", "-Dapp.home=`"`$APPDIR`"",
            "--java-options", "-Djava.library.path=`"`$APPDIR\app`""
        )
        
        # Agregar icono si existe
        $iconPath = Join-Path $PACKAGE_RESOURCES "icon.ico"
        if (Test-Path $iconPath) {
            $jpackageArgs += "--icon"
            $jpackageArgs += $iconPath
        }
        
        if ($Type -ne "app-image") {
            # $jpackageArgs += "--win-console"  # TEMPORAL: Para ver logs de coordenadas
            $jpackageArgs += "--win-shortcut"
            $jpackageArgs += "--win-menu"
            $jpackageArgs += "--win-menu-group"
            $jpackageArgs += "SunVisor OCR"
            
            # Agregar manifiesto de administrador si existe
            $manifestPath = Join-Path $PACKAGE_RESOURCES "SunVisor-OCR.manifest"
            if (Test-Path $manifestPath) {
                $jpackageArgs += "--win-shortcut-prompt"
            }
        }
        
        $output = & $jpackageCmd @jpackageArgs 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            $elapsed = ((Get-Date) - $startTime).TotalSeconds
            Write-Log "Instalador $Description creado exitosamente en $([math]::Round($elapsed, 1)) segundos" "Success"
            return $true
        } else {
            Write-Log "No se pudo crear instalador $Description" "Warning"
            Write-Log "Error de jpackage:" "Error"
            $output | ForEach-Object { Write-Log "  $_" "Error" }
            return $false
        }
    }
    
    # Estrategia según el parámetro InstallerType
    if ($InstallerType -eq 'exe') {
        # Solo intentar EXE
        if (Create-Installer -Type "exe" -Description "EXE (WiX v3)") {
            $installerCreated = $true
            $installerTypeVar = "EXE"
        } else {
            Write-Log "Error: No se pudo crear instalador EXE. Verifica que WiX v3.11 esté instalado." "Error"
            Write-Log "Descarga desde: https://github.com/wixtoolset/wix3/releases/tag/wix3112rtm" "Info"
            exit 1
        }
    } elseif ($InstallerType -eq 'msi') {
        # Solo intentar MSI
        if (Create-Installer -Type "msi" -Description "MSI (nativo de Windows)") {
            $installerCreated = $true
            $installerTypeVar = "MSI"
        } else {
            Write-Log "Error: No se pudo crear instalador MSI" "Error"
            exit 1
        }
    } elseif ($InstallerType -eq 'app-image') {
        # Solo crear app-image portable
        if (Create-Installer -Type "app-image" -Description "Aplicación portable (app-image)") {
            $installerCreated = $true
            $installerTypeVar = "Portable (app-image)"
        } else {
            Write-Log "Error: No se pudo crear aplicación portable" "Error"
            exit 1
        }
    } else {
        # Auto: intentar MSI -> EXE -> app-image
        if (Create-Installer -Type "msi" -Description "MSI (nativo de Windows)") {
            $installerCreated = $true
            $installerTypeVar = "MSI"
        } elseif (Create-Installer -Type "exe" -Description "EXE (WiX v3)") {
            $installerCreated = $true
            $installerTypeVar = "EXE"
        } elseif (Create-Installer -Type "app-image" -Description "Aplicación portable (app-image)") {
            $installerCreated = $true
            $installerTypeVar = "Portable (app-image)"
        } else {
            $ErrorActionPreference = "Stop"
            Write-Log "Error: No se pudo crear ningún tipo de instalador" "Error"
            exit 1
        }
    }
    
    if (-not $installerCreated) {
        $ErrorActionPreference = "Stop"
        Write-Log "Error al crear instalador" "Error"
        exit 1
    }
    
    Show-Progress -Activity "Paso 6/6: Creando ejecutable" -PercentComplete 100 -Status "Completado"
    $installerType = $installerTypeVar
    
    $ErrorActionPreference = "Stop"
} else {
    # Linux/Unix
    Write-Log "Sistema operativo: Linux/Unix" "Info"
    Show-Progress -Activity "Paso 6/6: Creando ejecutable" -PercentComplete 30 -Status "Creando aplicación portable..."
    Write-Log "Creando aplicación portable para Linux..." "Info"
    
    $startTime = Get-Date
    & $jpackageCmd `
        --input $TARGET_DIR `
        --name "SunVisor-OCR" `
        --main-jar $jarFile.Name `
        --main-class "com.ucaribe.sunvisor.App" `
        --type app-image `
        --dest $DIST_DIR `
        --app-version "0.1.0" `
        --resource-dir $PACKAGE_RESOURCES `
        --module-path "$modulePath" `
        --add-modules "javafx.controls,javafx.fxml,javafx.swing,javafx.graphics,javafx.base" `
        --java-options "-Dapp.home=`$APPDIR"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Log "Error al crear aplicación portable" "Error"
        exit 1
    }
    
    $elapsed = ((Get-Date) - $startTime).TotalSeconds
    Show-Progress -Activity "Paso 6/6: Creando ejecutable" -PercentComplete 100 -Status "Completado"
    Write-Log "Aplicación portable creada exitosamente en $([math]::Round($elapsed, 1)) segundos" "Success"
    $installerType = "Portable (Linux)"
}

Write-Host ""

# ============================================================
# Resumen Final
# ============================================================
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  EMPAQUETADO COMPLETADO EXITOSAMENTE" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Mostrar información del instalador generado
$installerFile = Get-ChildItem -Path $DIST_DIR -Filter "SunVisor-OCR*.*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($installerFile) {
    $installerSizeMB = [math]::Round($installerFile.Length / 1MB, 2)
    Write-Host "Tipo de instalador: $installerType" -ForegroundColor White
    Write-Host "Archivo: $($installerFile.Name)" -ForegroundColor White
    Write-Host "Tamaño: $installerSizeMB MB" -ForegroundColor White
    Write-Host "Ubicación: $($installerFile.FullName)" -ForegroundColor White
} else {
    # Si no es un archivo, es una carpeta (app-image)
    $appImageDir = Get-ChildItem -Path $DIST_DIR -Directory -Filter "SunVisor-OCR*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($appImageDir) {
        $appImageSize = (Get-ChildItem -Path $appImageDir.FullName -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB
        Write-Host "Tipo de instalador: $installerType" -ForegroundColor White
        Write-Host "Carpeta: $($appImageDir.Name)" -ForegroundColor White
        Write-Host "Tamaño total: $([math]::Round($appImageSize, 2)) MB" -ForegroundColor White
        Write-Host "Ubicación: $($appImageDir.FullName)" -ForegroundColor White
    } else {
        Write-Host "Ubicación: $DIST_DIR" -ForegroundColor White
    }
}

Write-Host ""
Write-Log "Proceso de empaquetado finalizado correctamente" "Success"
Write-Host ""

# Abrir carpeta dist/ en el explorador
if (Test-Path $DIST_DIR) { 
    Write-Log "Abriendo carpeta dist/ en el explorador..." "Info"
    explorer $DIST_DIR 
}
