package com.ucaribe.sunvisor;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public class SelectionScreen {

    private final Stage stage;
    private final Pane root;
    private final Consumer<java.awt.Rectangle> onSelectionComplete;

    private double startX, startY;
    private boolean callbackEjecutado = false;
    private boolean ventanaMostrada = false;

    // 4 rectangulos que forman el overlay
    private Rectangle topRect, bottomRect, leftRect, rightRect;
    private Rectangle selectionBorder;
    private Rectangle fullOverlay; // Referencia al overlay completo inicial

    private static int contadorInstancias = 0;
    private final int idInstancia;

    private final double screenWidth;
    private final double screenHeight;
    
    // Factores de escala DPI
    private final double outputScaleX;
    private final double outputScaleY;
    private final double boundsMinX;
    private final double boundsMinY;
    
    // Offset entre coordenadas JavaFX y AWT
    private int awtOffsetX;
    private int awtOffsetY;
    private double fxToAwtScaleX;
    private double fxToAwtScaleY;

    public SelectionScreen(Screen targetScreen, Consumer<java.awt.Rectangle> onSelectionComplete) {
        this.idInstancia = ++contadorInstancias;
        this.onSelectionComplete = onSelectionComplete;

        System.out.println("SelectionScreen #" + idInstancia + " CREADA para monitor: " + targetScreen.getBounds());

        // Usar solo el monitor especificado
        Rectangle2D bounds = targetScreen.getVisualBounds();
        this.screenWidth = bounds.getWidth();
        this.screenHeight = bounds.getHeight();
        this.boundsMinX = bounds.getMinX();
        this.boundsMinY = bounds.getMinY();
        
        // Guardar factores de escala DPI de este monitor
        this.outputScaleX = targetScreen.getOutputScaleX();
        this.outputScaleY = targetScreen.getOutputScaleY();
        
        System.out.println("[Monitor] Bounds: " + bounds);
        System.out.println("[DPI] Escala: " + outputScaleX + "x" + outputScaleY);

        // Capturar screenshot de ESTE monitor únicamente
        ImageView screenshotView = capturarPantalla(bounds, targetScreen);

        stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT); // Cambio de UNDECORATED a TRANSPARENT para evitar cambios de DPI en Windows
        stage.setAlwaysOnTop(true);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(screenWidth);
        stage.setHeight(screenHeight);

        // Color del overlay semitransparente
        Color overlayColor = Color.rgb(0, 0, 0, 0.4); // Negro al 40%

        // Crear overlay inicial que cubre toda la pantalla
        fullOverlay = new Rectangle(screenWidth, screenHeight);
        fullOverlay.setFill(overlayColor);

        // Crear 4 rectangulos para el overlay (inicialmente ocultos)
        topRect = new Rectangle(0, 0, screenWidth, 0);
        topRect.setFill(overlayColor);

        bottomRect = new Rectangle(0, 0, screenWidth, 0);
        bottomRect.setFill(overlayColor);

        leftRect = new Rectangle(0, 0, 0, 0);
        leftRect.setFill(overlayColor);

        rightRect = new Rectangle(0, 0, 0, 0);
        rightRect.setFill(overlayColor);

        // Borde de la seleccidon
        selectionBorder = new Rectangle();
        selectionBorder.setFill(Color.TRANSPARENT);
        selectionBorder.setStroke(Color.RED);
        selectionBorder.setStrokeWidth(2);
        selectionBorder.setVisible(false);

        // Pane para los overlays y seleccidon
        Pane overlayPane = new Pane();
        overlayPane.getChildren().addAll(fullOverlay, topRect, bottomRect, leftRect, rightRect, selectionBorder);

        overlayPane.setOnMousePressed(this::onMousePressed);
        overlayPane.setOnMouseDragged(this::onMouseDragged);
        overlayPane.setOnMouseReleased(this::onMouseReleased);

        // StackPane con screenshot de fondo y overlay encima
        root = new StackPane(screenshotView, overlayPane);
        root.setStyle("-fx-background-color: transparent;"); // Hacer el root transparente

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT); // Hacer la Scene transparente
        stage.setScene(scene);

        stage.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                System.out.println("ESC en #" + idInstancia);
                cerrarVentana(null);
                e.consume();
            }
        });

        stage.setOnCloseRequest(e -> {
            if (!callbackEjecutado) {
                cerrarVentana(null);
            }
        });
    }

    /**
     * Calcula el rectángulo que engloba TODAS las pantallas disponibles
     * Usa las coordenadas VISUALES (no físicas) para JavaFX
     */
    // Captura la pantalla de un monitor específico
    private ImageView capturarPantalla(Rectangle2D fxBounds, Screen targetScreen) {
        try {
            System.out.println("[Captura] Creando Robot...");
            Robot robot = new Robot();
            System.out.println("[Captura] Robot creado exitosamente");
            
            // Buscar el GraphicsDevice de AWT que corresponde a este monitor JavaFX
            java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
            java.awt.GraphicsDevice[] screens = ge.getScreenDevices();
            
            java.awt.Rectangle awtBounds = null;
            
            // Convertir las coordenadas JavaFX a físicas esperadas
            int expectedX = (int) Math.round(fxBounds.getMinX() * outputScaleX);
            int expectedY = (int) Math.round(fxBounds.getMinY() * outputScaleY);
            
            System.out.println("[Búsqueda] Buscando monitor AWT para JavaFX: " + fxBounds);
            System.out.println("[Búsqueda] Coordenadas físicas esperadas: " + expectedX + "," + expectedY);
            
            // Buscar el monitor que contiene estas coordenadas
            for (java.awt.GraphicsDevice screen : screens) {
                java.awt.Rectangle bounds = screen.getDefaultConfiguration().getBounds();
                System.out.println("[AWT Monitor] " + bounds);
                
                // Verificar si las coordenadas esperadas están dentro de este monitor
                // Dar un margen de tolerancia de 300 píxeles para diferencias en Y
                if (Math.abs(expectedX - bounds.x) < 100 &&
                    Math.abs(expectedY - bounds.y) < 300) {
                    awtBounds = bounds;
                    System.out.println("[Monitor Match] ✓ Este es el monitor correcto (por posición)!");
                    break;
                }
            }
            
            // Si no se encontró por coordenadas, intentar por tamaño
            if (awtBounds == null) {
                System.out.println("[Búsqueda] No encontrado por coordenadas, buscando por tamaño...");
                for (java.awt.GraphicsDevice screen : screens) {
                    java.awt.Rectangle bounds = screen.getDefaultConfiguration().getBounds();
                    double awtScaledWidth = bounds.width / outputScaleX;
                    double awtScaledHeight = bounds.height / outputScaleY;
                    
                    if (Math.abs(awtScaledWidth - fxBounds.getWidth()) < 10 &&
                        Math.abs(awtScaledHeight - fxBounds.getHeight()) < 10) {
                        awtBounds = bounds;
                        System.out.println("[Monitor Match] ✓ Encontrado por tamaño");
                        break;
                    }
                }
            }
            
            // Si aún no se encontró, usar conversión directa
            if (awtBounds == null) {
                System.out.println("[Fallback] Usando conversión directa");
                awtBounds = new java.awt.Rectangle(
                    (int) Math.round(fxBounds.getMinX() * outputScaleX),
                    (int) Math.round(fxBounds.getMinY() * outputScaleY),
                    (int) Math.round(fxBounds.getWidth() * outputScaleX),
                    (int) Math.round(fxBounds.getHeight() * outputScaleY)
                );
            }
            
            // Guardar offset y escala para conversión posterior
            this.awtOffsetX = awtBounds.x;
            this.awtOffsetY = awtBounds.y;
            this.fxToAwtScaleX = (double) awtBounds.width / fxBounds.getWidth();
            this.fxToAwtScaleY = (double) awtBounds.height / fxBounds.getHeight();
            
            System.out.println("[AWT] Capturando: " + awtBounds);
            System.out.println("[FX->AWT] Scale: " + fxToAwtScaleX + "x" + fxToAwtScaleY + ", Offset: " + awtOffsetX + "," + awtOffsetY);
            
            BufferedImage screenshot = robot.createScreenCapture(awtBounds);
            System.out.println("[Captura] Pantalla capturada: " + screenshot.getWidth() + "x" + screenshot.getHeight());

            WritableImage fxImage = SwingFXUtils.toFXImage(screenshot, null);
            ImageView imageView = new ImageView(fxImage);
            
            // Ajustar al tamaño de la ventana JavaFX
            imageView.setFitWidth(fxBounds.getWidth());
            imageView.setFitHeight(fxBounds.getHeight());
            imageView.setPreserveRatio(false);

            return imageView;

        } catch (java.awt.AWTException e) {
            System.err.println("[ERROR] No se pudo crear Robot (AWTException): " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("No se puede crear Robot para captura de pantalla", e);
        } catch (SecurityException e) {
            System.err.println("[ERROR] Sin permisos para captura de pantalla: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Sin permisos para captura de pantalla", e);
        } catch (Exception e) {
            System.err.println("[ERROR] Error inesperado al capturar pantalla: " + e.getMessage());
            e.printStackTrace();
            return new ImageView(); // Retornar vacio si falla
        }
    }

    public void startSelection() {
        synchronized (this) {
            if (ventanaMostrada || callbackEjecutado) {
                System.out.println("startSelection() YA FUE LLAMADO en #" + idInstancia);
                return;
            }
            ventanaMostrada = true;
        }

        System.out.println("Mostrando ventana #" + idInstancia);
        Platform.runLater(() -> {
            stage.show();
            stage.toFront();
            root.requestFocus();
            System.out.println("[Debug] Ventana mostrada con overlay semitransparente");
        });
    }

    public void forceClose() {
        cerrarVentana(null);
    }

    private void onMousePressed(MouseEvent e) {
        System.out.println("[Debug] Mouse PRESSED - Scene: (" + e.getX() + ", " + e.getY() + ") Screen: (" + e.getScreenX() + ", " + e.getScreenY() + ")");
        // Usar coordenadas relativas a la VENTANA, no a la pantalla
        startX = e.getX();
        startY = e.getY();
        selectionBorder.setVisible(true);
        fullOverlay.setVisible(false); // Ocultar el overlay completo para que solo se vean los 4 rectángulos
    }

    private void onMouseDragged(MouseEvent e) {
        // Usar coordenadas relativas a la ventana
        double endX = e.getX();
        double endY = e.getY();

        double x = Math.min(startX, endX);
        double y = Math.min(startY, endY);
        double w = Math.abs(endX - startX);
        double h = Math.abs(endY - startY);

        // Actualizar overlay para crear un "agujero"
        actualizarOverlay(x, y, w, h);

        // Actualizar borde
        selectionBorder.setX(x);
        selectionBorder.setY(y);
        selectionBorder.setWidth(w);
        selectionBorder.setHeight(h);
    }

    private void actualizarOverlay(double selX, double selY, double selW, double selH) {
        topRect.setX(0);
        topRect.setY(0);
        topRect.setWidth(screenWidth);
        topRect.setHeight(selY);

        bottomRect.setX(0);
        bottomRect.setY(selY + selH);
        bottomRect.setWidth(screenWidth);
        bottomRect.setHeight(screenHeight - (selY + selH));

        leftRect.setX(0);
        leftRect.setY(selY);
        leftRect.setWidth(selX);
        leftRect.setHeight(selH);

        rightRect.setX(selX + selW);
        rightRect.setY(selY);
        rightRect.setWidth(screenWidth - (selX + selW));
        rightRect.setHeight(selH);
    }

    private void onMouseReleased(MouseEvent e) {
        System.out.println("[Debug] Mouse RELEASED");
        double endX = e.getX();  // Coordenadas relativas a la ventana
        double endY = e.getY();

        // Coordenadas relativas a la ventana
        double relX = Math.min(startX, endX);
        double relY = Math.min(startY, endY);
        double relW = Math.abs(endX - startX);
        double relH = Math.abs(endY - startY);
        
        System.out.println("[Selección] Relativa a ventana: " + relX + "," + relY + " " + relW + "x" + relH);
        System.out.println("[Conversión] AWT offset: " + awtOffsetX + "," + awtOffsetY);
        System.out.println("[Conversión] FX->AWT scale: " + fxToAwtScaleX + "x" + fxToAwtScaleY);
        
        // Convertir directamente a coordenadas AWT usando la escala y offset calculados
        int awtX = awtOffsetX + (int) Math.round(relX * fxToAwtScaleX);
        int awtY = awtOffsetY + (int) Math.round(relY * fxToAwtScaleY);
        int awtW = (int) Math.round(relW * fxToAwtScaleX);
        int awtH = (int) Math.round(relH * fxToAwtScaleY);
        
        System.out.println("[AWT] Coordenadas físicas finales: " + awtX + "," + awtY + " " + awtW + "x" + awtH);

        if (awtW > 5 && awtH > 5 && awtW < 10000 && awtH < 10000) {
            // NO aplicar Math.max(0, ...) porque las coordenadas pueden ser negativas en multi-monitor
            System.out.println("[OK] Selección válida");
            cerrarVentana(new java.awt.Rectangle(awtX, awtY, awtW, awtH));
        } else {
            System.out.println("[Error] Selección inválida: w=" + awtW + ", h=" + awtH);
            cerrarVentana(null);
        }
    }

    private void cerrarVentana(java.awt.Rectangle resultado) {
        synchronized (this) {
            if (callbackEjecutado) {
                System.out.println("Callback YA ejecutado en #" + idInstancia);
                return;
            }
            callbackEjecutado = true;
        }

        System.out.println("Cerrando #" + idInstancia);

        Platform.runLater(() -> {
            stage.hide();
            stage.close();

            System.out.println("Ventana #" + idInstancia + " cerrada completamente");

            if (onSelectionComplete != null) {
                onSelectionComplete.accept(resultado);
            }
        });
    }
}
