package com.ucaribe.sunvisor;

/**
 * Esta clase es un "POJO" (Plain Old Java Object).
 * Gson la usará como plantilla para mapear la respuesta JSON de LibreTranslate.
 * La respuesta es: {"translatedText":"..."}
 */
public class TranslationResponse {

    // El nombre de esta variable DEBE coincidir con la llave del JSON
    private String translatedText;

    // Gson necesita los getters y setters
    public String getTranslatedText() {
        return translatedText;
    }

    public void setTranslatedText(String translatedText) {
        this.translatedText = translatedText;
    }
}
