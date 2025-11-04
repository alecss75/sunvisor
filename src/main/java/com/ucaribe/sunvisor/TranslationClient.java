package com.ucaribe.sunvisor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Cliente para el servicio de traducción japonés->inglés
 */
public class TranslationClient {
    
    /**
     * Clase interna para mapear la respuesta JSON del servicio de traducción
     */
    private static class TranslationResponse {
        String status;
        String message;
        String translated_text;
    }
    
    private static final Logger logger = Logger.getLogger(TranslationClient.class.getName());
    private static final String TRANSLATION_URL = "http://127.0.0.1:10000/translate/ja-en";
    private static final int TIMEOUT_SECONDS = 30;
    
    private final HttpClient httpClient;
    private final Gson gson;
    
    public TranslationClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        this.gson = new Gson();
    }

    //////////////////////////////////////////////////////////
    /// Traduce texto del japonés al inglés                 //
    /// @param japaneseText Texto en japonés                // 
    /// @return Texto traducido al inglés                   //
    /// @throws Exception Si hay error en la traducción     //
    //////////////////////////////////////////////////////////
    public String translate(String japaneseText) throws Exception {
        if (japaneseText == null || japaneseText.trim().isEmpty()) {
            throw new IllegalArgumentException("El texto no puede estar vacío");
        }
        
        logger.info("Enviando texto a traducir: " + japaneseText.substring(0, Math.min(50, japaneseText.length())) + "...");
        
        // Crear el JSON de entrada
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("text", japaneseText);
        String jsonBody = gson.toJson(requestBody);
        
        // Crear la petición HTTP
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TRANSLATION_URL))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        
        try {
            // Enviar la petición
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new Exception("Error en traducción: HTTP " + response.statusCode() + " - " + response.body());
            }
            
            // Parsear la respuesta
            TranslationResponse translationResponse = gson.fromJson(response.body(), TranslationResponse.class);
            
            if (!"success".equals(translationResponse.status)) {
                throw new Exception("Error en traducción: " + translationResponse.message);
            }
            
            logger.info("Traducción completada: " + translationResponse.translated_text.length() + " caracteres");
            return translationResponse.translated_text;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Traducción interrumpida", e);
        } catch (Exception e) {
            logger.severe("❌ Error al traducir: " + e.getMessage());
            throw new Exception("Error al comunicarse con el servicio de traducción: " + e.getMessage(), e);
        }
    }
    
    /**
     * Verifica si el servicio de traducción está disponible
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:10000/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject health = gson.fromJson(response.body(), JsonObject.class);
                // Verificar que el campo translation_available existe y es true
                if (health.has("translation_available")) {
                    boolean available = health.get("translation_available").getAsBoolean();
                    logger.info("Estado del servicio de traducción: " + (available ? "disponible" : "no disponible"));
                    return available;
                }
            }
            
            logger.warning("Respuesta de health check inválida");
            return false;
        } catch (Exception e) {
            logger.warning("No se pudo verificar el servicio de traducción: " + e.getMessage());
            return false;
        }
    }
}
