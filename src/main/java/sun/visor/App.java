package sun.visor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

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

/**
 * Aplicacion JavaFX para realizar OCR en areas seleccionadas de la pantalla.
 * Utiliza Tesseract para el reconocimiento optico de caracteres.
 */
public class App extends Application {

    // ==================== CONSTANTES ====================
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    private static final double WINDOW_WIDTH = 300;
    private static final double WINDOW_HEIGHT = 150;
    private static final String TESSDATA_DIR = "tessdata";
    private static final String[] LANGUAGES = { "eng", "spa", "jpn" };
    private static final String APP_TITLE = "Selector OCR Secuencial";

    // ==================== VARIABLES DE INSTANCIA ====================
    private final ITesseract tesseract = new Tesseract();
    private SelectionScreen currentScreen = null;
    private Stage primaryStage;
    private boolean isProcessing = false;
    private Button startButton;

    // ==================== MeTODOS PRINCIPALES ====================

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle(APP_TITLE);

        // Preparar Tesseract
        if (!inicializarTesseract()) {
            mostrarError("Error Fatal",
                    "No se pudo inicializar Tesseract OCR.\n" +
                            "Verifica que los archivos de idioma esten disponibles.");
            Platform.exit();
            return;
        }

        // Configurar GUI
        configurarInterfaz(stage);

        LOGGER.info("Aplicacion iniciada correctamente");
    }

    /**
     * Configura la interfaz grafica de usuario
     */
    private void configurarInterfaz(Stage stage) {
        startButton = new Button("Iniciar Seleccion OCR");
        startButton.setOnAction(e -> iniciarSeleccionSecuencial());
        startButton.setPrefWidth(200);
        startButton.setPrefHeight(40);

        StackPane root = new StackPane(startButton);
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

    /**
     * Cierra recursos y finaliza la aplicacion
     */
    private void cerrarAplicacion() {
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

    /**
     * Inicializa y configura Tesseract OCR
     * 
     * @return true si la inicializacion fue exitosa
     */
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
     * Configura los parametros de Tesseract
     */
    private void configurarTesseract(String tessdataPath) {
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(String.join("+", LANGUAGES));
        tesseract.setPageSegMode(1); // Automatic page segmentation with OSD
        tesseract.setOcrEngineMode(1); // Neural nets LSTM engine only

        LOGGER.info("Tesseract configurado con idiomas: " + String.join(", ", LANGUAGES));
    }

    private String prepararTessdata() {
        try {
            File tessDir = new File(TESSDATA_DIR);
            if (!tessDir.exists()) {
                tessDir.mkdirs();
            }

            // Archivos necesarios (incluye osd)
            String[] archivos = { "eng", "spa", "jpn", "osd" };

            for (String archivo : archivos) {
                File targetFile = new File(tessDir, archivo + ".traineddata");

                if (!targetFile.exists()) {
                    String resourcePath = "/tessdata/" + archivo + ".traineddata";

                    try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
                        if (in == null) {
                            LOGGER.warning("No se encontro: " + resourcePath);

                            // Si es osd y no esta, advertir pero continuar
                            if ("osd".equals(archivo)) {
                                LOGGER.warning("OSD no disponible - la deteccion de orientacion no funcionara");
                                continue;
                            }

                            return null; // Fallar si falta un idioma principal
                        }

                        Files.copy(in, targetFile.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        LOGGER.info("Copiado: " + archivo + ".traineddata");
                    }
                }
            }

            return tessDir.getAbsolutePath();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al preparar tessdata", e);
            return null;
        }
    }

    /**
     * Copia un archivo de idioma especifico al directorio tessdata
     * 
     * @param tessDir directorio de destino
     * @param lang    codigo del idioma
     * @return true si la copia fue exitosa o el archivo ya existe
     */
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

    /**
     * Inicia el proceso de seleccion de area en pantalla
     */
    private void iniciarSeleccionSecuencial() {
        // Verificar si ya hay un proceso activo
        if (isProcessing) {
            LOGGER.warning("Proceso ya activo, ignorando llamada");
            mostrarAdvertencia("Proceso Activo",
                    "Ya hay un proceso de seleccion en curso.\n" +
                            "Por favor, espera a que finalice.");
            return;
        }

        // Establecer estado de procesamiento
        isProcessing = true;
        startButton.setDisable(true);

        // Limpiar pantalla de seleccion anterior si existe
        if (currentScreen != null) {
            currentScreen.forceClose();
        }

        // Ocultar ventana principal
        primaryStage.hide();

        LOGGER.info("Iniciando seleccion de area");

        // Crear y mostrar pantalla de seleccion
        currentScreen = new SelectionScreen(area -> {
            // Callback ejecutado cuando el usuario finaliza la seleccion
            Platform.runLater(() -> {
                finalizarSeleccion(area);
            });
        });

        currentScreen.startSelection();
    }

    /**
     * Finaliza el proceso de seleccion y procesa el area seleccionada
     * 
     * @param area el area seleccionada, o null si se cancelo
     */
    private void finalizarSeleccion(Rectangle area) {
        currentScreen = null;

        if (area != null && area.width > 0 && area.height > 0) {
            LOGGER.info("area seleccionada: " +
                    String.format("x=%d, y=%d, w=%d, h=%d",
                            area.x, area.y, area.width, area.height));
            hacerOCR(area);
        } else {
            LOGGER.info("Seleccion cancelada o area invalida");
        }

        // Restaurar estado
        primaryStage.show();
        isProcessing = false;
        startButton.setDisable(false);
    }

    // ==================== PROCESAMIENTO OCR ====================

    /**
     * Captura el area seleccionada y ejecuta OCR
     * 
     * @param area el area de la pantalla a procesar
     */
    private void hacerOCR(Rectangle area) {
        try {
            LOGGER.info("Capturando area de pantalla...");

            // Capturar imagen
            Robot robot = new Robot();
            BufferedImage img = robot.createScreenCapture(area);

            LOGGER.info("Ejecutando OCR...");

            // Ejecutar OCR
            String texto = tesseract.doOCR(img);

            // Verificar resultados
            if (texto == null || texto.trim().isEmpty()) {
                LOGGER.info("No se detecto texto en el area seleccionada");
                mostrarInfo("Sin Resultados",
                        "No se detecto texto en el area seleccionada.\n\n" +
                                "Sugerencias:\n" +
                                "• Asegurate de que el area contenga texto legible\n" +
                                "• Aumenta el tamaño del area seleccionada\n" +
                                "• Verifica que el texto tenga buen contraste");
                return;
            }

            // Mostrar resultados
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

    // ==================== MeTODOS DE DIaLOGO ====================

    /**
     * Muestra un dialogo de error
     */
    private void mostrarError(String titulo, String mensaje) {
        mostrarAlerta(Alert.AlertType.ERROR, titulo, mensaje);
    }

    /**
     * Muestra un dialogo informativo
     */
    private void mostrarInfo(String titulo, String mensaje) {
        mostrarAlerta(Alert.AlertType.INFORMATION, titulo, mensaje);
    }

    /**
     * Muestra un dialogo de advertencia
     */
    private void mostrarAdvertencia(String titulo, String mensaje) {
        mostrarAlerta(Alert.AlertType.WARNING, titulo, mensaje);
    }

    /**
     * Muestra un dialogo de alerta generico
     */
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

    // ==================== PUNTO DE ENTRADA ====================

    public static void main(String[] args) {
        launch(args);
    }
}