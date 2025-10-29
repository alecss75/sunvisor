# Guía de Empaquetado Multiplataforma - SunVisor OCR

## Introducción
Esta guía explica cómo empaquetar y preparar la aplicación SunVisor OCR para su distribución en sistemas Windows y Linux. Incluye los pasos necesarios para crear instaladores y paquetes portables.

---

## Windows

### Requisitos
- **Java JDK 17 o superior**: Necesario para usar `jpackage`.
- **Maven 3.8 o superior**: Herramienta de construcción.
- **WiX Toolset 3.14** (opcional): Requerido para crear instaladores `.exe`.

### Tipos de Instalador
El script genera los siguientes tipos de instaladores, en este orden:

1. **EXE** (requiere WiX Toolset):
   - Instalador nativo de Windows.
   - Crea accesos directos en el menú inicio.
2. **MSI** (opción alternativa si no hay WiX):
   - Instalador estándar de Windows.
   - Compatible con todas las versiones de Windows.
3. **App-image** (portable):
   - No requiere instalación.
   - Carpeta autocontenida con todos los archivos necesarios.

### Instalación de WiX Toolset (Opcional)
Para crear instaladores `.exe`, instala WiX Toolset:

1. Descarga e instala desde [WiX Toolset](https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314.exe).
2. O usa Chocolatey:
   ```powershell
   choco install wixtoolset
   ```

### Ejecución del Script

Ejecuta el script para empaquetar la aplicación:
```powershell
# Empaquetado completo
.\build-completo.ps1

# Omitir descarga de Python (si ya se ejecutó antes)
.\build-completo.ps1 -SkipPython

# Omitir compilación Maven (si ya está compilado)
.\build-completo.ps1 -SkipPython -SkipBuild
```

---

## Linux

### Requisitos
- **Java JDK 17 o superior**:
  ```bash
  sudo apt install openjdk-17-jdk  # Ubuntu/Debian
  sudo dnf install java-17-openjdk-devel  # Fedora
  ```
- **Maven 3.8 o superior**:
  ```bash
  sudo apt install maven  # Ubuntu/Debian
  sudo dnf install maven  # Fedora
  ```
- **Python 3.11 o superior**:
  ```bash
  sudo apt install python3 python3-pip python3-venv
  ```

### Tipo de Paquete
En Linux, se genera un **app-image** (aplicación portable):
- No requiere instalación.
- Carpeta autocontenida con todos los archivos necesarios.

### Ejecución del Script

```bash
# Dar permisos de ejecución
chmod +x build-completo.sh

# Empaquetado completo
./build-completo.sh

# Omitir Python y compilación
./build-completo.sh --skip-python --skip-build
```

### Ejecutar la Aplicación

```bash
cd dist/SunVisor-OCR
./run.sh
```
O directamente:
```bash
dist/SunVisor-OCR/bin/SunVisor-OCR
```

---

## Contenido del Paquete

Todos los paquetes incluyen:

```
SunVisor-OCR/
├── bin/
│   └── SunVisor-OCR(.exe)     # Ejecutable principal
├── runtime/                    # Java Runtime (JRE)
├── app/
│   ├── sunvisor-0.1.jar       # Aplicación Java
│   ├── python/                # Python embebido (Windows) o venv (Linux)
│   │   ├── python(.exe)
│   │   └── Lib/site-packages/
│   │       ├── manga_ocr/
│   │       ├── torch/
│   │       └── ...
│   └── backend/
│       └── servicio_ocr.py    # Servidor FastAPI
└── (recursos adicionales)
```

---

## Resolución de Problemas

### Windows

**Error: "Maven no encontrado"**
```powershell
# Instalar Maven
winget install Apache.Maven

# O agregar al PATH
$env:PATH += ";C:\apache-maven-3.9.11\bin"
```

**Error: "WiX Toolset no encontrado"**
- No es obligatorio. El script generará un MSI o app-image automáticamente.
- Para instalar WiX: [WiX Toolset](https://wixtoolset.org/releases/).

**Error: "jpackage no encontrado"**
```powershell
# Verificar Java 17+
java -version

# Instalar JDK 17
winget install Oracle.JDK.17
```

### Linux

**Error: "Python3 no encontrado"**
```bash
sudo apt install python3 python3-pip python3-venv
```

**Error: "Maven no encontrado"**
```bash
sudo apt install maven
```

**Error: "jpackage no encontrado"**
```bash
# Verificar Java
java -version

# Instalar OpenJDK 17
sudo apt install openjdk-17-jdk
```

---

## Comparación de Paquetes

| Característica       | EXE (Windows) | MSI (Windows) | App-image (Win/Linux) |
|----------------------|---------------|---------------|-----------------------|
| Requiere WiX        | Sí            | No            | No                    |
| Instalación         | Completa      | Estándar      | No requiere           |
| Accesos directos    | Sí            | Sí            | No                    |
| Desinstalación      | Panel de Control | Panel de Control | Eliminar carpeta |
| Portable            | No            | No            | Sí                    |
| Tamaño              | ~150 MB       | ~150 MB       | ~1.2 GB (completo)    |
| Multiplataforma     | No            | No            | Sí                    |

---

## Resumen

### Windows

Con WiX:
```powershell
.\build-completo.ps1
# Resultado: dist/SunVisor-OCR-0.1.0.exe
```

Sin WiX:
```powershell
.\build-completo.ps1
# Resultado: dist/SunVisor-OCR-0.1.0.msi o dist/SunVisor-OCR/ (portable)
```

### Linux
```bash
./build-completo.sh
# Resultado: dist/SunVisor-OCR/ (portable)
```

Tu aplicación está lista para usarse en Windows y Linux.
