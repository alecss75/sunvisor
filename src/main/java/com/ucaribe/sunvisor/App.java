package com.ucaribe.sunvisor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;
import java.util.logging.Level;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

public class App extends Application {

    // ==================== CONSTANTES ====================
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    private static final double WINDOW_WIDTH = 350;
    private static final double WINDOW_HEIGHT = 180;
    private static final String TESSDATA_DIR = "tessdata";
    private static final String[] LANGUAGES = { "eng", "spa", "jpn", "jpn_vert" };
    private static final String APP_TITLE = "SunVisor OCR";

    // Usar siempre todos los idiomas
    private static final String OCR_LANGUAGES = "spa+eng+jpn";

    // ==================== VARIABLES DE INSTANCIA ====================
    private final ITesseract tesseract = new Tesseract();
    private SelectionScreen currentScreen = null;
    private Stage primaryStage;
    private boolean isProcessing = false;
    private Button startButton;
    private GlobalKeyboardListener globalKeyListener;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle(APP_TITLE);

        if (!inicializarTesseract()) {
            mostrarError("Error Fatal",
                    "No se pudo inicializar Tesseract OCR.\n" +
                            "Verifica que los archivos de idioma estén disponibles.");
            Platform.exit();
            return;
        }

        configurarAtajosGlobales();
        configurarInterfaz(stage);

        LOGGER.info("Aplicación lista - Idiomas: " + OCR_LANGUAGES);
        LOGGER.info("Atajo global: Ctrl+Shift+T");
    }

    private void configurarAtajosGlobales() {
        globalKeyListener = new GlobalKeyboardListener(() -> {
            Platform.runLater(() -> {
                iniciarSeleccionSecuencial();
            });
        });

        globalKeyListener.register();
    }

    /**
     * INTERFAZ
     */
    private void configurarInterfaz(Stage stage) {
        // Título
        Label titleLabel = new Label("📸 SunVisor OCR");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        // Botón principal (más grande y centrado)
        startButton = new Button("🔍 Iniciar Captura");
        startButton.setOnAction(e -> iniciarSeleccionSecuencial());
        startButton.setPrefWidth(280);
        startButton.setPrefHeight(60);
        startButton.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-color: #4CAF50; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 8px;");

        // Efecto hover
        startButton.setOnMouseEntered(e -> startButton.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-color: #45a049; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 8px;"));
        startButton.setOnMouseExited(e -> startButton.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-color: #4CAF50; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 8px;"));

        // Info de idiomas
        Text languageInfo = new Text("🌐 Detecta: Español, English, 日本語");
        languageInfo.setStyle("-fx-font-size: 11px; -fx-fill: #666;");

        // Info de atajo
        Text shortcutInfo = new Text("⌨Atajo: Ctrl+Shift+T");
        shortcutInfo.setStyle("-fx-font-size: 11px; -fx-fill: #999;");

        // Layout
        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(25));
        root.getChildren().addAll(
                titleLabel,
                startButton,
                languageInfo,
                shortcutInfo);

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> cerrarAplicacion());
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        cerrarAplicacion();
        super.stop();
    }

    private void cerrarAplicacion() {
        if (globalKeyListener != null) {
            globalKeyListener.unregister();
        }

        if (currentScreen != null) {
            try {
                currentScreen.forceClose();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error al cerrar SelectionScreen", e);
            }
        }
        LOGGER.info("Aplicación cerrada");
    }

    // ==================== INICIALIZACIÓN DE TESSERACT ====================

    private boolean inicializarTesseract() {
        try {
            String tessdataPath = prepararTessdata();
            if (tessdataPath == null) {
                LOGGER.severe("No se pudo preparar el directorio tessdata");
                return false;
            }

            configurarTesseract(tessdataPath);

            LOGGER.info("Tesseract inicializado correctamente");
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al inicializar Tesseract", e);
            return false;
        }
    }

    /**
     * ✅ SIMPLIFICADO: Configuración fija con todos los idiomas
     */
    private void configurarTesseract(String tessdataPath) {
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(OCR_LANGUAGES); // Siempre todos los idiomas
        tesseract.setPageSegMode(3); // Automatic page segmentation
        tesseract.setOcrEngineMode(1); // LSTM only

        LOGGER.info("Tesseract configurado con: " + OCR_LANGUAGES);
    }

    private String prepararTessdata() {
        try {
            File tessDir = new File(TESSDATA_DIR);
            if (!tessDir.exists()) {
                if (!tessDir.mkdirs()) {
                    LOGGER.severe("No se pudo crear el directorio: " + TESSDATA_DIR);
                    return null;
                }
                LOGGER.info("Directorio tessdata creado: " + tessDir.getAbsolutePath());
            }

            boolean todosCopiados = true;
            for (String lang : LANGUAGES) {
                if (!copiarArchivoIdioma(tessDir, lang)) {
                    todosCopiados = false;
                }
            }

            if (!todosCopiados) {
                LOGGER.warning("Algunos archivos de idioma no se pudieron copiar");
            }

            return tessDir.getAbsolutePath();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al preparar tessdata", e);
            return null;
        }
    }

    private boolean copiarArchivoIdioma(File tessDir, String lang) {
        try {
            String fileName = lang + ".traineddata";
            File targetFile = new File(tessDir, fileName);

            if (targetFile.exists()) {
                LOGGER.fine("Archivo ya existe: " + fileName);
                return true;
            }

            String resourcePath = "/tessdata/" + fileName;
            try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    LOGGER.warning("No se encontró el recurso: " + resourcePath);
                    return false;
                }

                Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Archivo copiado: " + targetFile.getAbsolutePath());
                return true;
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al copiar archivo de idioma: " + lang, e);
            return false;
        }
    }

    // ==================== LÓGICA DE SELECCIÓN ====================

    private void iniciarSeleccionSecuencial() {
        if (isProcessing) {
            LOGGER.warning("Proceso ya activo, ignorando llamada");
            return;
        }

        isProcessing = true;
        startButton.setDisable(true);

        if (currentScreen != null) {
            currentScreen.forceClose();
        }

        primaryStage.hide();

        LOGGER.info("Iniciando selección de área");

        currentScreen = new SelectionScreen(area -> {
            Platform.runLater(() -> {
                finalizarSeleccion(area);
            });
        });

        currentScreen.startSelection();
    }

    private void finalizarSeleccion(Rectangle area) {
        currentScreen = null;

        if (area != null && area.width > 0 && area.height > 0) {
            LOGGER.info("Área seleccionada: " +
                    String.format("x=%d, y=%d, w=%d, h=%d",
                            area.x, area.y, area.width, area.height));
            hacerOCR(area);
        } else {
            LOGGER.info("Selección cancelada o área inválida");
        }

        primaryStage.show();
        isProcessing = false;
        startButton.setDisable(false);
    }

    // ==================== PROCESAMIENTO OCR ====================

    /**
     * OCR con preprocesamiento inteligente
     */
    private void hacerOCR(Rectangle area) {
        try {
            LOGGER.info("Capturando área de pantalla...");

            Robot robot = new Robot();
            BufferedImage img = robot.createScreenCapture(area);

            // Siempre aplicar preprocesamiento ligero
            img = ImagePreprocessor.preprocess(img, true);

            LOGGER.info("Ejecutando OCR multi-idioma...");

            String texto = tesseract.doOCR(img);

            if (texto == null || texto.trim().isEmpty()) {
                LOGGER.info("No se detectó texto");
                mostrarInfo("Sin Resultados",
                        "No se detectó texto en el área seleccionada.\n\n" +
                                "Sugerencias:\n" +
                                "• Aumenta el tamaño del área seleccionada\n" +
                                "• Asegúrate de que haya buen contraste\n" +
                                "• El texto debe ser claro y legible");
                return;
            }

            int caracteres = texto.trim().length();
            LOGGER.info("✅ Texto detectado: " + caracteres + " caracteres");

            Platform.runLater(() -> {
                ResultWindow win = new ResultWindow(texto.trim());
                win.show();
            });

        } catch (java.awt.AWTException e) {
            LOGGER.log(Level.SEVERE, "Error al capturar pantalla", e);
            mostrarError("Error de Captura",
                    "No se pudo capturar la pantalla.\n" +
                            "Error: " + e.getMessage());

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error durante OCR", e);
            mostrarError("Error OCR",
                    "No se pudo procesar el texto.\n" +
                            "Error: " + e.getMessage());
        }
    }

    // ==================== MÉTODOS DE DIÁLOGO ====================

    private void mostrarError(String titulo, String mensaje) {
        mostrarAlerta(Alert.AlertType.ERROR, titulo, mensaje);
    }

    private void mostrarInfo(String titulo, String mensaje) {
        mostrarAlerta(Alert.AlertType.INFORMATION, titulo, mensaje);
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(tipo);
                alert.setTitle(titulo);
                alert.setHeaderText(null);
                alert.setContentText(mensaje);
                alert.showAndWait();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error al mostrar alerta", e);
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}