#!/bin/bash
# Script de Empaquetado Completo SunVisor OCR para Linux
# Uso: ./build-completo.sh [--skip-python] [--skip-build]

set -e  # Salir si hay errores

SKIP_PYTHON=false
SKIP_BUILD=false

# Procesar argumentos
for arg in "$@"; do
    case $arg in
        --skip-python) SKIP_PYTHON=true ;;
        --skip-build) SKIP_BUILD=true ;;
    esac
done

echo "================================================"
echo "  SunVisor OCR - Empaquetado Completo (Linux)"
echo "================================================"

# Rutas
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_RESOURCES="$ROOT/package-resources"
PYTHON_DIR="$PACKAGE_RESOURCES/python"
BACKEND_DIR="$PACKAGE_RESOURCES/backend"
TARGET_DIR="$ROOT/target"
DIST_DIR="$ROOT/dist"

PYTHON_VERSION="3.11.9"
PYTHON_URL="https://www.python.org/ftp/python/$PYTHON_VERSION/Python-$PYTHON_VERSION.tgz"
PYTHON_TAR="$PACKAGE_RESOURCES/python-source.tgz"

echo "[1/6] Preparando estructura de carpetas..."
rm -rf "$PACKAGE_RESOURCES"
mkdir -p "$PACKAGE_RESOURCES" "$PYTHON_DIR" "$BACKEND_DIR"
echo "   ✓ Carpetas creadas"

echo "[2/6] Python y dependencias..."
if [ "$SKIP_PYTHON" = false ]; then
    # En Linux, usar Python del sistema y crear venv
    if ! command -v python3 &> /dev/null; then
        echo "   ✗ Python3 no encontrado"
        echo "   Instala Python 3.11+: sudo apt install python3 python3-pip python3-venv"
        exit 1
    fi
    
    python3 -m venv "$PYTHON_DIR"
    source "$PYTHON_DIR/bin/activate"
    pip install --upgrade pip
    
    if [ -f "$ROOT/src/main/resources/backend/requirements.txt" ]; then
        pip install -r "$ROOT/src/main/resources/backend/requirements.txt"
    else
        pip install fastapi uvicorn manga-ocr torch torchvision pillow
    fi
    
    deactivate
    echo "   ✓ Python configurado"
else
    echo "  Omitiendo Python"
fi

echo "[3/6] Copiando backend..."
if [ -d "$ROOT/src/main/resources/backend" ]; then
    cp -r "$ROOT/src/main/resources/backend"/* "$BACKEND_DIR/"
    rm -rf "$BACKEND_DIR/venv" "$BACKEND_DIR/__pycache__"
    find "$BACKEND_DIR" -name "*.pyc" -delete
    echo "   ✓ Scripts copiados"
else
    echo "   ✗ Backend no encontrado"
    exit 1
fi

echo "[4/6] Compilando Maven..."
if [ "$SKIP_BUILD" = false ]; then
    # Buscar Maven
    MVN_CMD=""
    if command -v mvn &> /dev/null; then
        MVN_CMD="mvn"
    elif [ -f "$ROOT/../Maven/apache-maven-3.9.11-bin/apache-maven-3.9.11/bin/mvn" ]; then
        MVN_CMD="$ROOT/../Maven/apache-maven-3.9.11-bin/apache-maven-3.9.11/bin/mvn"
    fi
    
    if [ -z "$MVN_CMD" ]; then
        echo "   ✗ Maven no encontrado"
        echo "   Instala Maven: sudo apt install maven"
        exit 1
    fi
    
    echo "   Usando Maven: $MVN_CMD"
    "$MVN_CMD" clean package -DskipTests -f "$ROOT/pom.xml"
    echo "   ✓ Compilación exitosa"
else
    echo "  Omitiendo compilación"
fi

echo "[5/6] Creando ejecutable..."
JAR_FILE=$(find "$TARGET_DIR" -name "sunvisor-*.jar" -type f | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "   ✗ JAR no encontrado"
    exit 1
fi

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

# Buscar jpackage
JPACKAGE_CMD=""
if command -v jpackage &> /dev/null; then
    JPACKAGE_CMD="jpackage"
elif [ -n "$JAVA_HOME" ] && [ -f "$JAVA_HOME/bin/jpackage" ]; then
    JPACKAGE_CMD="$JAVA_HOME/bin/jpackage"
fi

if [ -z "$JPACKAGE_CMD" ]; then
    echo "   ✗ jpackage no encontrado"
    echo "   Necesitas JDK 17+ instalado"
    exit 1
fi

echo "   Usando jpackage: $JPACKAGE_CMD"
echo "   Creando aplicación portable para Linux..."

JAR_NAME=$(basename "$JAR_FILE")
$JPACKAGE_CMD \
    --input "$TARGET_DIR" \
    --name "SunVisor-OCR" \
    --main-jar "$JAR_NAME" \
    --main-class "com.ucaribe.sunvisor.App" \
    --type app-image \
    --dest "$DIST_DIR" \
    --app-version "0.1.0" \
    --resource-dir "$PACKAGE_RESOURCES" \
    --java-options "-Dapp.home=\$APPDIR"

if [ $? -ne 0 ]; then
    echo "   ✗ Error en jpackage"
    exit 1
fi

echo "   ✓ Aplicación creada"

# Crear script de lanzamiento
cat > "$DIST_DIR/SunVisor-OCR/run.sh" << 'EOF'
#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$DIR/bin/SunVisor-OCR" "$@"
EOF
chmod +x "$DIST_DIR/SunVisor-OCR/run.sh"

echo ""
echo "✓ COMPLETADO"
echo "Aplicación en: $DIST_DIR/SunVisor-OCR/"
echo ""
echo "Para ejecutar:"
echo "  cd $DIST_DIR/SunVisor-OCR"
echo "  ./run.sh"
echo ""
echo "O directamente:"
echo "  $DIST_DIR/SunVisor-OCR/bin/SunVisor-OCR"
