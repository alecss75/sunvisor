package com.ucaribe.sunvisor;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.Color;
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

    // Aumenta el contraste de la imagen
    private static BufferedImage increaseContrast(BufferedImage original) {
        BufferedImage result = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                original.getType());

        float contrastFactor = 1.5f; // Factor de contraste

        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                Color color = new Color(original.getRGB(x, y));

                int red = adjustChannel(color.getRed(), contrastFactor);
                int green = adjustChannel(color.getGreen(), contrastFactor);
                int blue = adjustChannel(color.getBlue(), contrastFactor);

                Color newColor = new Color(red, green, blue);
                result.setRGB(x, y, newColor.getRGB());
            }
        }

        return result;
    }

    // Ajusta un canal de color con el factor de contraste
    private static int adjustChannel(int channel, float factor) {
        int adjusted = (int) (((channel / 255.0 - 0.5) * factor + 0.5) * 255);
        return Math.max(0, Math.min(255, adjusted));
    }

    // Binarizacion (blanco y negro puro) usando Otsu's method simplificado
    private static BufferedImage binarize(BufferedImage original) {
        BufferedImage binarized = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                BufferedImage.TYPE_BYTE_BINARY);

        // Calcular umbral promedio
        long sum = 0;
        int count = 0;

        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                Color color = new Color(original.getRGB(x, y));
                int gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                sum += gray;
                count++;
            }
        }

        int threshold = (int) (sum / count);

        // Aplicar umbral
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                Color color = new Color(original.getRGB(x, y));
                int gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;

                int newColor = (gray > threshold) ? Color.WHITE.getRGB() : Color.BLACK.getRGB();
                binarized.setRGB(x, y, newColor);
            }
        }

        return binarized;
    }
}
