package sun.visor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;

// Importamos las librerías de Gson
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class TranslationService {

    // private static final String API_URL =
    // "https://translate.argosopentech.com/translate"; // Falló
    private static final String API_URL = "https://libretranslate.com/translate"; // <-- URL de Respaldo

    // Creamos una sola instancia de HttpClient y Gson para reutilizarla
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    /**
     * Clase interna privada.
     * La usaremos para construir el cuerpo JSON de la *petición* que enviaremos.
     */
    private static class TranslationRequest {
        // Usamos @SerializedName para asegurarnos de que el JSON se genere con
        // los nombres correctos que la API espera (q, source, target).
        @SerializedName("q")
        String query;

        @SerializedName("source")
        String source;

        @SerializedName("target")
        String target;

        String format = "text"; // Esto es un valor fijo

        // Constructor
        TranslationRequest(String q, String source, String target) {
            this.query = q;
            this.source = source;
            this.target = target;
        }
    }

    /**
     * Traduce un texto de un idioma a otro usando la API de LibreTranslate.
     *
     * @param text       El texto a traducir.
     * @param sourceLang El código del idioma de origen (ej. "en" para inglés).
     * @param targetLang El código del idioma de destino (ej. "es" para español).
     * @return El texto traducido.
     */
    public String translate(String text, String sourceLang, String targetLang) throws Exception {

        // 1. Crear el objeto de la petición
        TranslationRequest requestBody = new TranslationRequest(text, sourceLang, targetLang);

        // 2. Convertir nuestro objeto Java (requestBody) a un String en formato JSON
        String jsonBody = gson.toJson(requestBody);

        // 3. Crear la petición HTTP POST
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json") // Avisamos que enviamos JSON
                .POST(BodyPublishers.ofString(jsonBody))
                .build();

        // 4. Enviar la petición y recibir la respuesta
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 5. Verificar si la API nos respondió correctamente
        if (response.statusCode() == 200) {

            // 6. ¡Magia! Usar Gson para convertir el String de respuesta JSON
            // en nuestro objeto Java (TranslationResponse).
            TranslationResponse translationResponse = gson.fromJson(response.body(), TranslationResponse.class);

            // 7. Devolver el texto traducido
            return translationResponse.getTranslatedText();

        } else {
            // Si algo salió mal (ej. la API está caída o los idiomas son incorrectos)
            throw new RuntimeException(
                    "Falló la traducción. Código: " + response.statusCode() + " | Cuerpo: " + response.body());
        }
    }
}