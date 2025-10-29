# Guía Completa: Crear un Instalador .EXE para SunVisor OCR

## Introducción
Esta guía explica cómo crear un instalador completo para la aplicación SunVisor OCR, que incluye Java, Python y todas las dependencias necesarias. El resultado es un único archivo `.exe` que el usuario puede instalar fácilmente.

---

## Cómo Crear el Instalador

### Opción 1: Automático (Recomendado)
Ejecuta el siguiente comando para empaquetar todo automáticamente:
```powershell
.\build-completo.ps1
```
Esto descargará Python, instalará dependencias, compilará y empaquetará la aplicación.

### Opción 2: Paso a Paso
Si prefieres realizar el proceso manualmente, consulta la guía detallada en `EMPAQUETADO.md`.

---

## Contenido del Instalador

El instalador genera una carpeta con la siguiente estructura:
```
SunVisor-OCR/
├── SunVisor-OCR.exe          ← Ejecutable principal
├── runtime/                  ← Java Runtime (JRE 17+)
├── app/
│   ├── sunvisor-0.1.jar      ← Aplicación Java
│   ├── python/               ← Python embebido
│   │   ├── python.exe
│   │   ├── Lib/
│   │   │   └── site-packages/
│   │   │       ├── manga_ocr/
│   │   │       ├── torch/
│   │   │       ├── fastapi/
│   │   │       └── ...
│   └── backend/
│       ├── servicio_ocr.py   ← Servidor FastAPI
│       └── requirements.txt
└── tessdata/                 ← Archivos de Tesseract OCR
```

**Tamaño total**: Aproximadamente 800 MB a 1.2 GB (incluye todo).

---

## Cambios Realizados en el Código

### Detección Automática de Python Embebido
Se actualizó `MangaOCRServerManager.java` para buscar Python embebido antes de usar el Python del sistema:
```java
private String getPythonCommand() {
    String pythonEmbebido = buscarPythonEmbebido();
    if (pythonEmbebido != null) {
        return pythonEmbebido;
    }
    return "python"; // Fallback al Python del sistema
}

private String buscarPythonEmbebido() {
    String[] rutas = {
        appHome + "/app/python/python.exe",
        appHome + "/python/python.exe",
        "python/python.exe"
    };
    for (String ruta : rutas) {
        if (new File(ruta).exists()) {
            return ruta;
        }
    }
    return null;
}
```

### Mejora en la Búsqueda del Script
Se mejoró la función `encontrarScript()` para localizar el archivo `servicio_ocr.py` en diferentes ubicaciones:
```java
private File encontrarScript() {
    String[] rutas = {
        appHome + "/app/backend/servicio_ocr.py",
        appHome + "/backend/servicio_ocr.py",
        "src/main/resources/backend/servicio_ocr.py"
    };
    for (String ruta : rutas) {
        File f = new File(ruta);
        if (f.exists()) return f;
    }
    extraerDesdeJAR();
}
```

---

## Cómo Funciona el Instalador

1. El usuario instala el archivo `.exe` generado:
   ```
   dist/SunVisor-OCR-0.1.0.exe
   ```
   Esto instala la aplicación en `C:\Program Files\SunVisor-OCR\`.

2. El usuario ejecuta la aplicación:
   ```
   C:\Program Files\SunVisor-OCR\SunVisor-OCR.exe
   ```

3. Internamente, la aplicación:
   - Inicia el servidor FastAPI con Python embebido.
   - Ejecuta la aplicación JavaFX para el OCR.

---

## Probar en Desarrollo

Si deseas probar la aplicación sin empaquetarla:

1. Asegúrate de tener Python instalado:
   ```powershell
   python --version
   ```

2. Instala las dependencias:
   ```powershell
   pip install -r src/main/resources/backend/requirements.txt
   ```

3. Ejecuta la aplicación:
   ```powershell
   mvn javafx:run
   ```

---

## Requisitos para Crear el Instalador

| Herramienta     | Versión | Propósito                     |
|-----------------|---------|-------------------------------|
| Java JDK        | 17+     | Compilar y ejecutar jpackage  |
| Maven           | 3.8+    | Compilar el proyecto          |
| WiX Toolset     | 3.11+   | Crear instalador .exe (Windows) |
| Conexión a Internet | -   | Descargar Python embebido     |

### Verificar Requisitos

```powershell
java -version      # Debe mostrar 17+
mvn -version       # Debe mostrar 3.8+
candle.exe -?      # Debe mostrar WiX Toolset
```

### Instalar WiX Toolset

1. Descarga: https://wixtoolset.org/releases/
2. Instala `wix311.exe`
3. Agrega al PATH: `C:\Program Files (x86)\WiX Toolset v3.11\bin`

---

## Resolución de Problemas

### Python no detectado en producción
Verifica que la carpeta `python/` se empaquetó correctamente:
```powershell
ls package-resources/python/python.exe  # Debe existir
```

### Servidor FastAPI no inicia
Posibles causas:
1. Python embebido no tiene las dependencias instaladas.
2. El script `servicio_ocr.py` no se copió correctamente.

Solución:
```powershell
.\build-completo.ps1 -Verbose
```

### Error de WiX Toolset
Si aparece el error "WiX Toolset not found":
1. Instala WiX Toolset desde [WiX Toolset](https://wixtoolset.org/releases/).
2. Agrega WiX al PATH.

---

## Resumen

Con el script `build-completo.ps1`, puedes generar un instalador completo que incluye todo lo necesario para ejecutar SunVisor OCR en cualquier sistema Windows. El usuario final solo necesita instalar el `.exe` y ejecutar la aplicación.

Comando final:
```powershell
.\build-completo.ps1
```
Resultado:
```
dist/SunVisor-OCR-0.1.0.exe  ← Instalador completo
```
