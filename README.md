# 📸 SunVisor OCR

Aplicación de escritorio para realizar OCR (Reconocimiento Óptico de Caracteres) en áreas seleccionadas de la pantalla.

![Java](https://img.shields.io/badge/Java-17-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-17-blue)
![Maven](https://img.shields.io/badge/Maven-3.8+-green)
![License](https://img.shields.io/badge/License-MIT-yellow)

## ✨ Características

- 🖱️ **Selección interactiva**: Selecciona cualquier área de tu pantalla
- 🔍 **OCR Multi-idioma**: Soporte para Español, Inglés y Japonés
- 📋 **Copiar al portapapeles**: Resultados listos para usar
- ⚡ **Interfaz intuitiva**: Similar a la herramienta de recortes de Windows
- 🎯 **Precisión**: Utiliza Tesseract OCR Engine
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

## 🚀 Inicio Rápido

### Requisitos Previos

- **Java JDK 17** o superior
- **Maven 3.8** o superior

### Instalación

1. **Clona el repositorio**
   ```bash
   git clone https://github.com/tu-usuario/sunvisor-ocr.git
   cd sunvisor-ocr