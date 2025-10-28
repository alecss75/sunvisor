# src/main/resources/backend/servicio_ocr.py
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from base64 import b64decode
from io import BytesIO
from PIL import Image
from manga_ocr import MangaOcr
import logging
import sys

# Configurar logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Manga OCR API",
    description="API para reconocimiento de texto en manga japones",
    version="1.0.0"
)

# Configurar CORS (permitir requests desde Java)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ✅ Inicializar Manga OCR una sola vez
logger.info("Inicializando Manga OCR...")
try:
    mocr = MangaOcr()
    logger.info("✅ Manga OCR listo")
except Exception as e:
    logger.error(f"Error al cargar Manga OCR: {e}")
    sys.exit(1)

class ImageInput(BaseModel):
    """Modelo de datos para recibir la imagen codificada desde JavaFX."""
    image_base64: str

class OCRResponse(BaseModel):
    """Modelo de respuesta del OCR"""
    status: str
    text: str
    message: str = ""

@app.get("/")
def root():
    """Endpoint raiz - informacion basica"""
    return {
        "service": "Manga OCR API",
        "version": "1.0.0",
        "status": "running",
        "endpoints": {
            "health": "/health",
            "ocr": "/ocr/manga"
        }
    }

@app.get("/health")
def health_check():
    """
    Endpoint para verificar que el servidor esta funcionando.
    Usado por Java para detectar si el servidor esta listo.
    """
    return {
        "status": "healthy",
        "engine": "manga-ocr",
        "ready": True
    }

@app.post("/ocr/manga", response_model=OCRResponse)
def process_manga_ocr(input_data: ImageInput):
    """
    Recibe una imagen en Base64, realiza el OCR y devuelve el texto.
    
    Args:
        input_data: Objeto con la imagen en base64
        
    Returns:
        OCRResponse con el texto extraido
    """
    try:
        logger.info(" Recibiendo imagen para OCR...")
        
        # Decodificar la imagen Base64
        image_data = b64decode(input_data.image_base64)
        image = Image.open(BytesIO(image_data))
        
        logger.info(f"Imagen cargada: {image.size} ({image.mode})")

        # Realizar el OCR con manga-ocr
        extracted_text = mocr(image)
        
        logger.info(f"OCR completado: {len(extracted_text)} caracteres")
        logger.info(f"Texto: {extracted_text[:50]}...")

        return OCRResponse(
            status="success",
            text=extracted_text,
            message="OCR completado exitosamente"
        )

    except Exception as e:
        logger.error(f"Error en OCR: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=str(e)
        )

@app.on_event("startup")
async def startup_event():
    """Evento al iniciar el servidor"""
    logger.info("=" * 60)
    logger.info("Manga OCR API Server")
    logger.info("Puerto: 8080")
    logger.info("Endpoints disponibles:")
    logger.info("  - GET  /         (Informacion)")
    logger.info("  - GET  /health   (Estado)")
    logger.info("  - POST /ocr/manga (OCR)")
    logger.info("=" * 60)

@app.on_event("shutdown")
async def shutdown_event():
    """Evento al cerrar el servidor"""
    logger.info("Cerrando Manga OCR API Server...")

if __name__ == "__main__":
    import uvicorn
    
    # Configuracion del servidor
    uvicorn.run(
        app,
        host="127.0.0.1",
        port=8080,
        log_level="info",
        access_log=False  # Desactivar logs de acceso para menos ruido
    )