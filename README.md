# 📸 SunVisor OCR

Aplicación de escritorio multiplataforma para reconocimiento óptico de caracteres (OCR) con soporte para **alfabeto latino** (español/inglés) y **alfabeto japonés** (manga). Incluye selección interactiva de pantalla, procesamiento inteligente de imágenes y servidor Python embebido para Manga OCR.

![Java](https://img.shields.io/badge/Java-17-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-17-blue)
![Python](https://img.shields.io/badge/Python-3.11-blue)
![Maven](https://img.shields.io/badge/Maven-3.8+-green)
![License](https://img.shields.io/badge/License-MIT-yellow)

## ✨ Características Principales

### 🎯 Funcionalidades Core
- **Selección interactiva de pantalla**: Selecciona cualquier área con el mouse (similar a la herramienta de recortes de Windows)
- **Soporte multi-monitor**: Detecta y funciona en configuraciones con múltiples pantallas
- **Dual OCR Engine**:
  - **Alfabeto Latino**: Procesamiento local ultrarrápido con Tesseract OCR (español e inglés)
  - **Alfabeto Japonés**: Alta precisión con Manga OCR (servidor FastAPI embebido)
- **Preprocesamiento inteligente**: Ajuste automático de contraste y binarización según el alfabeto seleccionado
- **Atajo de teclado global**: `F9` para capturar desde cualquier aplicación
- **Resultados instantáneos**: Copiar al portapapeles automáticamente

### ⚡ Optimizaciones de Rendimiento
- **Gestión eficiente de recursos**: Singleton `Robot`, `ExecutorService` para hilos, shutdown ordenado
- **Procesamiento de imágenes acelerado**: Operaciones en arrays de píxeles, bit operations (10-50x más rápido)
- **Servidor Python embebido**: Inicia automáticamente en segundo plano, detección inteligente de Python

### 🔧 Tecnologías
- **Frontend**: JavaFX 17 (interfaz gráfica nativa)
- **OCR Local**: Tesseract 5.x con tess4j 5.11.0
- **OCR Japonés**: Manga OCR 0.1.10 (PyTorch + transformers)
- **Backend**: FastAPI + Uvicorn (servidor embebido)
- **Build**: Maven 3.9+ con Maven Shade Plugin
- **Packaging**: jpackage (JDK 17+) + WiX Toolset 3.14

## ✨ Características

- 🖱️ **Selección interactiva**: Selecciona cualquier área de tu pantalla
- �️ **Soporte multi-monitor**: Funciona en configuraciones con múltiples pantallas
- �🔤 **Selector de alfabeto**: Elige entre dos tipos de texto:
  - **Alfabeto Latino**: Español e Inglés (procesamiento rápido con Tesseract)
  - **Alfabeto Japonés**: Usa Manga OCR (alta precisión) con fallback a Tesseract
- 📋 **Copiar al portapapeles**: Resultados listos para usar
- ⚡ **Interfaz intuitiva**: Similar a la herramienta de recortes de Windows
- 🎯 **Precisión adaptativa**: Preprocesamiento optimizado según el tipo de alfabeto
- ⚡ **Alto rendimiento**: Optimizado con procesamiento de imágenes acelerado y gestión eficiente de hilos

## 🚀 Optimizaciones de Rendimiento

### Mejoras Implementadas (Octubre 2025)

**1. Gestión eficiente de recursos**
- ✅ Reutilización de instancia `Robot` para captura de pantalla (eliminando latencia de creación repetida)
- ✅ `ExecutorService` para control ordenado de tareas OCR (reemplaza `new Thread()` directo)
- ✅ Shutdown ordenado con timeout en cierre de aplicación

**2. Procesamiento de imágenes acelerado**
- ✅ `increaseContrast()` optimizado: usa arrays de píxeles en lugar de `getRGB()`/`setRGB()` por píxel
- ✅ `binarize()` optimizado: bit operations en lugar de objetos `Color`
- ✅ Reducción de 95%+ en llamadas a métodos costosos de `BufferedImage`
- ✅ Medición de tiempos con logs (`System.nanoTime()`) para preprocesamiento

**Impacto esperado**:
- Preprocesamiento de imágenes: **10-50x más rápido** (dependiendo del tamaño)
- Captura de pantalla: **latencia reducida** al eliminar creación repetida de `Robot`
- Shutdown: **ordenado y sin bloqueos** con timeout de 3 segundos

**Cómo medir mejoras**:
Revisa los logs de la aplicación para ver tiempos de preprocesamiento:
```
INFO: Preprocesamiento completado en XX ms
```

## � Estructura del Proyecto

```
sunvisor/
├── src/
│   └── main/
│       ├── java/               # Código fuente Java
│       └── resources/
│           ├── backend/        # Servidor Python (Manga OCR)
│           ├── sun/visor/      # FXML de JavaFX
│           └── tessdata/       # Modelos de Tesseract
├── build-completo.ps1          # Script de empaquetado (.exe)
├── SOLUCION-COMPLETA.md        # Guía de empaquetado
├── pom.xml                     # Configuración Maven
└── README.md                   # Este archivo
```

## �🚀 Inicio Rápido

### Requisitos Previos

- **Java JDK 17** o superior
- **Maven 3.8** o superior

### Instalación

1. **Clona el repositorio**
   ```bash
   git clone https://github.com/tu-usuario/sunvisor-ocr.git
   cd sunvisor-ocr
   ```

2. **Compila el proyecto**
   ```bash
   mvn clean package
   ```

3. **Ejecuta la aplicación**
   ```bash
   mvn javafx:run
   ```
   
   O usa el launcher:
   ```bash
   .\run.bat
   ```

## 📦 Crear Ejecutable (.exe)

Para distribuir la aplicación como un ejecutable de Windows:

### Prerrequisitos
- JDK 17+ con jpackage
- WiX Toolset (opcional, para instaladores .exe)

### Empaquetar

```powershell
# Opción 1: Script automático (recomendado)
.\build-exe.ps1

# Opción 2: Manual
mvn clean package
jpackage --input target --name SunVisor-OCR --main-jar sunvisor-0.1.jar --main-class com.ucaribe.sunvisor.App --type exe
```

El instalador se generará en: `dist/SunVisor-OCR-0.1.0.exe`

📄 **Guía completa de empaquetado**: Ver [EMPAQUETADO.md](EMPAQUETADO.md)

### Distribución

El `.exe` generado incluye:
- ✅ Aplicación Java completa
- ✅ JRE (Java Runtime) integrado
- ✅ Tesseract OCR con datos de idiomas
- ✅ Scripts del servidor Python (requiere Python instalado)

**Tamaño del instalador**: ~ 150-200 MB