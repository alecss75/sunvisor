package sun.visor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
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
    private static final double WINDOW_HEIGHT = 200;
    private static final String TESSDATA_DIR = "tessdata";
    private static final String[] LANGUAGES = { "eng", "spa", "jpn" };
    private static final String APP_TITLE = "SunVisor OCR";

    // ==================== VARIABLES DE INSTANCIA ====================
    private final ITesseract tesseract = new Tesseract();
    private SelectionScreen currentScreen = null;
    private Stage primaryStage;
    private boolean isProcessing = false;
    private Button startButton;

    // Listener de atajos globales
    private GlobalKeyboardListener globalKeyListener;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle(APP_TITLE);

        // Preparar Tesseract
        if (!inicializarTesseract()) {
            mostrarError("Error Fatal",
                    "No se pudo inicializar Tesseract OCR.\n" +
                            "Verifica que los archivos de idioma estén disponibles.");
            Platform.exit();
            return;
        }

        // Configurar atajos globales
        configurarAtajosGlobales();

        // Configurar GUI
        configurarInterfaz(stage);

        LOGGER.info("Aplicacion iniciada correctamente");
        LOGGER.info("Atajo global: Ctrl+Shift+T");
    }

    /**
     * Configura los atajos de teclado globales
     */
    private void configurarAtajosGlobales() {
        globalKeyListener = new GlobalKeyboardListener(() -> {
            // Este codigo se ejecuta cuando se presiona Ctrl+Shift+S
            Platform.runLater(() -> {
                iniciarSeleccionSecuencial();
            });
        });

        globalKeyListener.register();
    }

    private void configurarInterfaz(Stage stage) {
        // Boton principal
        startButton = new Button("Iniciar Seleccion OCR");
        startButton.setOnAction(e -> iniciarSeleccionSecuencial());
        startButton.setPrefWidth(250);
        startButton.setPrefHeight(50);
        startButton.setStyle("-fx-font-size: 14px;");

        // Texto informativo sobre el atajo
        Text infoText = new Text("Atajo de teclado: Ctrl+Alt+T");
        infoText.setStyle("-fx-font-size: 12px; -fx-fill: gray;");

        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.getChildren().addAll(startButton, infoText);

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
        // Desregistrar atajos globales
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
        LOGGER.info("Aplicacion cerrada");
    }

    // ==================== INICIALIZACIoN DE TESSERACT ====================

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

    private void configurarTesseract(String tessdataPath) {
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(String.join("+", LANGUAGES));
        tesseract.setPageSegMode(3); // Automatic sin OSD
        tesseract.setOcrEngineMode(1); // LSTM only

        LOGGER.info("Tesseract configurado con idiomas: " + String.join(", ", LANGUAGES));
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
                    LOGGER.warning("No se encontro el recurso: " + resourcePath);
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

    // ==================== LoGICA DE SELECCIoN ====================

    private void iniciarSeleccionSecuencial() {
        if (isProcessing) {
            LOGGER.warning("Proceso ya activo, ignorando llamada");
            mostrarAdvertencia("Proceso Activo",
                    "Ya hay un proceso de seleccion en curso.\n" +
                            "Por favor, espera a que finalice.");
            return;
        }

        isProcessing = true;
        startButton.setDisable(true);

        if (currentScreen != null) {
            currentScreen.forceClose();
        }

        primaryStage.hide();

        LOGGER.info("Iniciando seleccion de área");

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
            LOGGER.info("Seleccion cancelada o área inválida");
        }

        primaryStage.show();
        isProcessing = false;
        startButton.setDisable(false);
    }

    // ==================== PROCESAMIENTO OCR ====================

    private void hacerOCR(Rectangle area) {
        try {
            LOGGER.info("Capturando área de pantalla...");

            Robot robot = new Robot();
            BufferedImage img = robot.createScreenCapture(area);

            LOGGER.info("Ejecutando OCR...");

            String texto = tesseract.doOCR(img);

            if (texto == null || texto.trim().isEmpty()) {
                LOGGER.info("No se detecto texto en el área seleccionada");
                mostrarInfo("Sin Resultados",
                        "No se detecto texto en el área seleccionada.\n\n" +
                                "Sugerencias:\n" +
                                "• Asegúrate de que el área contenga texto legible\n" +
                                "• Aumenta el tamaño del área seleccionada\n" +
                                "• Verifica que el texto tenga buen contraste");
                return;
            }

            int caracteres = texto.trim().length();
            LOGGER.info("Texto detectado: " + caracteres + " caracteres");

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

    private void mostrarAdvertencia(String titulo, String mensaje) {
        mostrarAlerta(Alert.AlertType.WARNING, titulo, mensaje);
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