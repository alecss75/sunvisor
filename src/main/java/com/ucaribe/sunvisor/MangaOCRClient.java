package com.ucaribe.sunvisor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// Cliente para Manga OCR FastAPI Server
public class MangaOCRClient {
    
    private static final Logger LOGGER = Logger.getLogger(MangaOCRClient.class.getName());
    private static final String BASE_URL = "http://127.0.0.1:10000";  // Puerto alto para evitar conflictos
    private static final String OCR_ENDPOINT = BASE_URL + "/ocr/manga";
    private static final String HEALTH_ENDPOINT = BASE_URL + "/health";
    
    private final HttpClient httpClient;
    
    public MangaOCRClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    //////////////////////////////////////////////
    // Procesa una imagen con Manga OCR         //
    // @param image imagen a procesar           //
    // @return texto detectado                  //
    // @throws Exception si hay error           //
    //////////////////////////////////////////////
    public String processImage(BufferedImage image) throws Exception {
        // Convertir imagen a base64
        String base64Image = imageToBase64(image);
        
        // Crear JSON request (formato FastAPI)
        String jsonRequest = String.format(
            "{\"image_base64\": \"%s\"}", 
            base64Image
        );
        
        LOGGER.info("Enviando imagen a Manga OCR Server...");
        
        // Enviar request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OCR_ENDPOINT))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
            .build();
        
        HttpResponse<String> response = httpClient.send(
            request, 
            HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() != 200) {
            throw new Exception("Error del servidor HTTP " + response.statusCode());
        }
        
        // Parsear respuesta JSON
        JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        
        String status = jsonResponse.get("status").getAsString();
        
        if (!"success".equals(status)) {
            String message = jsonResponse.has("message") ? 
                jsonResponse.get("message").getAsString() : "Error desconocido";
            throw new Exception("Error en OCR: " + message);
        }
        
        String text = jsonResponse.get("text").getAsString();
        LOGGER.info("OCR completado: " + text.length() + " caracteres");
        
        return text;
    }
    
    /**
     * Verifica si el servidor esta disponible y listo
     */
    public boolean isServerAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HEALTH_ENDPOINT))
                .GET()
                .timeout(Duration.ofSeconds(3))
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                return json.has("ready") && json.get("ready").getAsBoolean();
            }
            
            return false;
            
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Servidor no disponible: " + e.getMessage());
            return false;
        }
    }
    
    // Convierte BufferedImage a Base64
    private String imageToBase64(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }
    
    // Obtiene informacion del servidor
    public String getServerInfo() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .GET()
                .timeout(Duration.ofSeconds(3))
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            return response.body();
            
        } catch (Exception e) {
            return "Server not available";
        }
    }
}