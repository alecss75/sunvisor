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

    // 4 rectángulos que forman el overlay
    private Rectangle topRect, bottomRect, leftRect, rightRect;
    private Rectangle selectionBorder;

    private static int contadorInstancias = 0;
    private final int idInstancia;

    private final double screenWidth;
    private final double screenHeight;

    public SelectionScreen(Consumer<java.awt.Rectangle> onSelectionComplete) {
        this.idInstancia = ++contadorInstancias;
        this.onSelectionComplete = onSelectionComplete;

        System.out.println("SelectionScreen #" + idInstancia + " CREADA");

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        this.screenWidth = bounds.getWidth();
        this.screenHeight = bounds.getHeight();

        // Capturar screenshot de la pantalla
        ImageView screenshotView = capturarPantalla(bounds);

        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED); // ✅ UNDECORATED en lugar de TRANSPARENT
        stage.setAlwaysOnTop(true);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(screenWidth);
        stage.setHeight(screenHeight);

        // Color del overlay semitransparente
        Color overlayColor = Color.rgb(0, 0, 0, 0.4); // Negro al 40%

        // Crear overlay inicial que cubre toda la pantalla
        Rectangle fullOverlay = new Rectangle(screenWidth, screenHeight);
        fullOverlay.setFill(overlayColor);

        // Crear 4 rectángulos para el overlay (inicialmente ocultos)
        topRect = new Rectangle(0, 0, screenWidth, 0);
        topRect.setFill(overlayColor);

        bottomRect = new Rectangle(0, 0, screenWidth, 0);
        bottomRect.setFill(overlayColor);

        leftRect = new Rectangle(0, 0, 0, 0);
        leftRect.setFill(overlayColor);

        rightRect = new Rectangle(0, 0, 0, 0);
        rightRect.setFill(overlayColor);

        // Borde de la selección
        selectionBorder = new Rectangle();
        selectionBorder.setFill(Color.TRANSPARENT);
        selectionBorder.setStroke(Color.RED);
        selectionBorder.setStrokeWidth(2);
        selectionBorder.setVisible(false);

        // Pane para los overlays y selección
        Pane overlayPane = new Pane();
        overlayPane.getChildren().addAll(fullOverlay, topRect, bottomRect, leftRect, rightRect, selectionBorder);

        overlayPane.setOnMousePressed(this::onMousePressed);
        overlayPane.setOnMouseDragged(this::onMouseDragged);
        overlayPane.setOnMouseReleased(this::onMouseReleased);

        // StackPane con screenshot de fondo y overlay encima
        root = new StackPane(screenshotView, overlayPane);

        Scene scene = new Scene(root);
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
     * Captura la pantalla y retorna un ImageView
     */
    private ImageView capturarPantalla(Rectangle2D bounds) {
        try {
            Robot robot = new Robot();
            BufferedImage screenshot = robot.createScreenCapture(
                    new java.awt.Rectangle(
                            (int) bounds.getMinX(),
                            (int) bounds.getMinY(),
                            (int) bounds.getWidth(),
                            (int) bounds.getHeight()));

            WritableImage fxImage = SwingFXUtils.toFXImage(screenshot, null);
            ImageView imageView = new ImageView(fxImage);
            imageView.setFitWidth(bounds.getWidth());
            imageView.setFitHeight(bounds.getHeight());

            return imageView;

        } catch (Exception e) {
            e.printStackTrace();
            return new ImageView(); // Retornar vacío si falla
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
        System.out.println("[Debug] Mouse PRESSED en (" + e.getX() + ", " + e.getY() + ")");
        startX = e.getX();
        startY = e.getY();
        selectionBorder.setVisible(true);
    }

    private void onMouseDragged(MouseEvent e) {
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
        double endX = e.getX();
        double endY = e.getY();

        int x = (int) Math.round(Math.min(startX, endX) + stage.getX());
        int y = (int) Math.round(Math.min(startY, endY) + stage.getY());
        int w = (int) Math.round(Math.abs(endX - startX));
        int h = (int) Math.round(Math.abs(endY - startY));

        if (w > 5 && h > 5 && w < 10000 && h < 10000) {
            x = Math.max(0, x);
            y = Math.max(0, y);
            cerrarVentana(new java.awt.Rectangle(x, y, w, h));
        } else {
            System.out.println("Selección inválida: w=" + w + ", h=" + h);
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
