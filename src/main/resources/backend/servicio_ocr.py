# src/main/resources/backend/servicio_ocr.py
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from base64 import b64decode
from io import BytesIO
from PIL import Image
import logging

# Configurar logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Manga OCR API",
    description="API para reconocimiento de texto en manga japones y traducción",
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

# Variables globales para los modelos
mocr = None
translation_model = None
translation_tokenizer = None

@app.on_event("startup")
async def startup_event():
    """Inicializar modelos al arrancar el servidor"""
    global mocr, translation_model, translation_tokenizer
    
    # Inicializar Manga OCR
    logger.info("Inicializando Manga OCR...")
    try:
        from manga_ocr import MangaOcr
        mocr = MangaOcr()
        logger.info("Manga OCR listo")
    except Exception as e:
        logger.warning(f"Manga OCR no disponible: {e}")
        logger.info("El servidor continuará sin Manga OCR (usará Tesseract desde Java)")
    
    # Inicializar modelo de traducción
    logger.info("Cargando modelo de traducción japonés->inglés...")
    try:
        from transformers import MarianMTModel, MarianTokenizer
        model_name = "sephinroth/marian-finetuned-kftt-ja-to-en"
        translation_tokenizer = MarianTokenizer.from_pretrained(model_name)
        translation_model = MarianMTModel.from_pretrained(model_name)
        logger.info("Modelo de traducción listo")
    except Exception as e:
        logger.warning(f"Error al cargar modelo de traducción: {e}")
        logger.info("El servidor continuará sin traducción")

@app.on_event("shutdown")
async def shutdown_event():
    """Limpiar recursos al cerrar el servidor"""
    global mocr, translation_model, translation_tokenizer
    logger.info("Cerrando servidor...")
    mocr = None
    translation_model = None
    translation_tokenizer = None


class ImageInput(BaseModel):
    """Modelo de datos para recibir la imagen codificada desde JavaFX."""
    image_base64: str

class OCRResponse(BaseModel):
    """Modelo de respuesta del OCR"""
    status: str
    text: str
    message: str = ""

class TranslationInput(BaseModel):
    """Modelo de entrada para traducción"""
    text: str

class TranslationResponse(BaseModel):
    """Modelo de respuesta de traducción"""
    status: str
    original_text: str
    translated_text: str
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
            "ocr": "/ocr/manga",
            "translate": "/translate/ja-en"
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
        "manga_ocr_available": mocr is not None,
        "translation_available": translation_model is not None,
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
        if mocr is None:
            raise HTTPException(
                status_code=503,
                detail="Manga OCR no está disponible. Use Tesseract desde Java."
            )
        
        logger.info("📝 Recibiendo imagen para OCR...")
        
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

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error en OCR: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=str(e)
        )

@app.post("/translate/ja-en", response_model=TranslationResponse)
def translate_japanese_to_english(input_data: TranslationInput):
    """
    Traduce texto del japonés al inglés usando el modelo Marian.
    
    Args:
        input_data: Objeto con el texto en japonés
        
    Returns:
        TranslationResponse con el texto traducido
    """
    try:
        if translation_model is None or translation_tokenizer is None:
            raise HTTPException(
                status_code=503,
                detail="Servicio de traducción no disponible"
            )
        
        logger.info(f"📝 Traduciendo texto: {input_data.text[:50]}...")
        
        # Tokenizar el texto
        inputs = translation_tokenizer(input_data.text, return_tensors="pt", padding=True)
        
        # Generar la traducción
        translated = translation_model.generate(**inputs)
        
        # Decodificar el resultado
        translated_text = translation_tokenizer.decode(translated[0], skip_special_tokens=True)
        
        logger.info(f"Traducción completada: {translated_text[:50]}...")
        
        return TranslationResponse(
            status="success",
            original_text=input_data.text,
            translated_text=translated_text,
            message="Traducción completada exitosamente"
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"❌ Error en traducción: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=str(e)
        )

@app.on_event("startup")
async def startup_event():
    """Evento al iniciar el servidor"""
    logger.info("=" * 60)
    logger.info("Manga OCR & Translation API Server")
    logger.info("Puerto: 10000")
    logger.info("Endpoints disponibles:")
    logger.info("  - GET  /              (Información)")
    logger.info("  - GET  /health        (Estado)")
    logger.info("  - POST /ocr/manga     (OCR japonés)")
    logger.info("  - POST /translate/ja-en (Traducción JP→EN)")
    logger.info("=" * 60)

@app.on_event("shutdown")
async def shutdown_event():
    """Evento al cerrar el servidor"""
    logger.info("Cerrando Manga OCR & Translation API Server...")

if __name__ == "__main__":
    import uvicorn
    
    # Configuracion del servidor
    uvicorn.run(
        app,
        host="127.0.0.1",
        port=10000,
        log_level="info",
        access_log=False  # Desactivar logs de acceso para menos ruido
    )