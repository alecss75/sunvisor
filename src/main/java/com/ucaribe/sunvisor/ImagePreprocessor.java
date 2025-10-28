package com.ucaribe.sunvisor;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class ImagePreprocessor {

    private static final Logger LOGGER = Logger.getLogger(ImagePreprocessor.class.getName());

    //////////////////////////////////////////////////////////
    // Preprocesa la imagen para mejorar el OCR de japones  //
    // @param original imagen original                      //
    // @param forJapanese true si es para japones           //
    // @return imagen procesada                             //
    //////////////////////////////////////////////////////////
    public static BufferedImage preprocess(BufferedImage original, boolean forJapanese) {
        BufferedImage processed = original;

        if (forJapanese) {
            // Escalar imagen (japones necesita mas resolucion)
            processed = scaleImage(processed, 2.0);

            // Convertir a escala de grises
            processed = toGrayscale(processed);

            // Aumentar contraste
            processed = increaseContrast(processed);

            // Binarizacion (blanco y negro puro)
            processed = binarize(processed);

            LOGGER.fine("Imagen preprocesada para japones");
        } else {
            // Para otros idiomas, procesamiento mas ligero
            processed = toGrayscale(processed);
            processed = increaseContrast(processed);
        }

        return processed;
    }

    /**
     * Escala la imagen por un factor
     */
    private static BufferedImage scaleImage(BufferedImage original, double scale) {
        int newWidth = (int) (original.getWidth() * scale);
        int newHeight = (int) (original.getHeight() * scale);

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();

        // Usar interpolacion de alta calidad
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return scaled;
    }

    // Convierte a escala de grises
    private static BufferedImage toGrayscale(BufferedImage original) {
        BufferedImage grayscale = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D g2d = grayscale.createGraphics();
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();

        return grayscale;
    }

    // Aumenta el contraste de la imagen (optimizado con array de pixeles)
    private static BufferedImage increaseContrast(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        float factor = 1.5f;

        // Obtener todos los pixeles de una vez (mucho mas rapido que getRGB por pixel)
        int[] pixels = original.getRGB(0, 0, w, h, null, 0, w);
        
        // Procesar pixeles usando bit operations (evita crear objetos Color)
        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            int a = (rgb >> 24) & 0xFF;
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            r = adjustChannel(r, factor);
            g = adjustChannel(g, factor);
            b = adjustChannel(b, factor);

            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        
        // Escribir todos los pixeles de una vez
        result.setRGB(0, 0, w, h, pixels, 0, w);
        return result;
    }

    // Ajusta un canal de color con el factor de contraste
    private static int adjustChannel(int channel, float factor) {
        int adjusted = (int) (((channel / 255.0 - 0.5) * factor + 0.5) * 255);
        return Math.max(0, Math.min(255, adjusted));
    }

    // Binarizacion (blanco y negro puro) optimizada con arrays
    private static BufferedImage binarize(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        
        // Obtener todos los pixeles de una vez
        int[] pixels = original.getRGB(0, 0, w, h, null, 0, w);

        // Calcular umbral promedio (sin crear objetos Color)
        long sum = 0;
        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int gray = (r + g + b) / 3;
            sum += gray;
        }
        int threshold = (int) (sum / pixels.length);

        // Aplicar umbral
        int white = 0xFFFFFFFF;
        int black = 0xFF000000;
        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int gray = (r + g + b) / 3;
            
            pixels[i] = (gray > threshold) ? white : black;
        }

        BufferedImage binarized = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        binarized.setRGB(0, 0, w, h, pixels, 0, w);
        return binarized;
    }
}
