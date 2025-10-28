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
    private static final double WINDOW_WIDTH = 380;
    private static final double WINDOW_HEIGHT = 240;
    private static final String TESSDATA_DIR = "tessdata";
    private static final String[] LANGUAGES = {"eng", "spa", "jpn", "jpn_vert"};
    private static final String APP_TITLE = "SunVisor OCR - Manga Edition";
    
    // ==================== VARIABLES DE INSTANCIA ====================
    private final ITesseract tesseract = new Tesseract();
    private SelectionScreen currentScreen = null;
    private Stage primaryStage;
    private boolean isProcessing = false;
    
    // UI Components
    private Button startButton;
    private Label statusLabel;
    private ProgressIndicator loadingIndicator;
    
    // OCR Components
    private GlobalKeyboardListener globalKeyListener;
    private MangaOCRServerManager serverManager;
    private MangaOCRClient mangaOCR;
    private boolean useMangaOCR = false;

    // ==================== INICIO DE APLICACIoN ====================
    
    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle(APP_TITLE);

        // Mostrar splash screen mientras carga
        mostrarSplashScreen();

        // Inicializar en background thread
        new Thread(() -> {
            try {
                inicializarAplicacion();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error durante inicializacion", e);
                Platform.runLater(() -> {
                    mostrarError("Error Fatal", 
                        "No se pudo inicializar la aplicacion.\n" +
                        "Error: " + e.getMessage());
                    Platform.exit();
                });
            }
        }).start();
    }

    
    // Inicializa todos los componentes de la aplicacion
    private void inicializarAplicacion() throws Exception {
        LOGGER.info("=".repeat(60));
        LOGGER.info("Iniciando SunVisor OCR - Manga Edition");
        LOGGER.info("=".repeat(60));
        
        // Inicializar Tesseract 
        actualizarEstado("Inicializando Tesseract...");
        if (!inicializarTesseract()) {
            throw new Exception("No se pudo inicializar Tesseract OCR");
        }
        LOGGER.info("Tesseract inicializado");

        // 2. Intentar iniciar servidor Manga OCR
        actualizarEstado("Iniciando Manga OCR Server...");
        boolean serverIniciado = iniciarMangaOCRServer();

        // 3. Configurar atajos globales
        actualizarEstado("Configurando atajos de teclado...");
        configurarAtajosGlobales();
        LOGGER.info("Atajos configurados (Ctrl+Alt+T)");

        // 4. Mostrar interfaz principal
        Platform.runLater(() -> {
            configurarInterfaz(primaryStage);
            LOGGER.info("Aplicacion lista");
            LOGGER.info("=".repeat(60));
        });
    }

    /**
     * Intenta iniciar el servidor Manga OCR
     * @return true si se inicio correctamente
     */
    private boolean iniciarMangaOCRServer() {
        try {
            serverManager = new MangaOCRServerManager();
            
            LOGGER.info("Intentando iniciar servidor FastAPI...");
            boolean servidorIniciado = serverManager.iniciarServidor();

            if (!servidorIniciado) {
                LOGGER.warning("No se pudo iniciar servidor Manga OCR");
                return false;
            }

            // Crear cliente
            mangaOCR = new MangaOCRClient();
            
            // Esperar a que el servidor este listo (maximo 30 segundos)
            LOGGER.info("Esperando a que el servidor este listo...");
            int intentos = 0;
            int maxIntentos = 30;
            
            while (intentos < maxIntentos) {
                if (mangaOCR.isServerAvailable()) {
                    LOGGER.info("Manga OCR Server listo");
                    useMangaOCR = true;
                    return true;
                }
                
                Thread.sleep(1000);
                intentos++;
                
                if (intentos % 5 == 0) {
                    LOGGER.info("Esperando servidor... " + intentos + "/" + maxIntentos);
                }
            }
            
            LOGGER.warning("Timeout esperando servidor (30s)");
            return false;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al iniciar Manga OCR Server", e);
            return false;
        }
    }

    /**
     * Actualiza el mensaje de estado en el splash screen
     */
    private void actualizarEstado(String mensaje) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(mensaje);
            }
        });
    }

    // ==================== INTERFAZ DE USUARIO ====================
    
    /**
     * Muestra pantalla de carga inicial
     */
    private void mostrarSplashScreen() {
        Label titleLabel = new Label("SunVisor OCR");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        
        statusLabel = new Label("Inicializando...");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(50, 50);
        
        VBox splashLayout = new VBox(20);
        splashLayout.setAlignment(Pos.CENTER);
        splashLayout.setPadding(new Insets(40));
        splashLayout.getChildren().addAll(titleLabel, loadingIndicator, statusLabel);
        
        Scene splashScene = new Scene(splashLayout, WINDOW_WIDTH, WINDOW_HEIGHT);
        primaryStage.setScene(splashScene);
        primaryStage.show();
    }

    /**
     * Configura la interfaz principal de la aplicacion
     */
    private void configurarInterfaz(Stage stage) {
        // Titulo
        Label titleLabel = new Label("Manga OCR");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        // Estado del motor OCR
        Label engineStatusLabel = new Label();
        if (useMangaOCR) {
            engineStatusLabel.setText("Manga OCR Activo (Alta Precision)");
            engineStatusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else {
            engineStatusLabel.setText("Modo Tesseract (Precision Reducida)");
            engineStatusLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-size: 11px; -fx-font-weight: bold;");
        }

        // Tooltip informativo
        Tooltip engineTooltip = new Tooltip();
        if (useMangaOCR) {
            engineTooltip.setText(
                "Manga OCR esta activo.\n" +
                "Optimizado para manga japones con 90%+ de precision.\n" +
                "Funciona con hiragana, katakana y kanji."
            );
        } else {
            engineTooltip.setText(
                "Manga OCR no esta disponible.\n" +
                "Usando Tesseract como alternativa.\n" +
                "Precision reducida para manga (60-70%).\n\n" +
                "Para activar Manga OCR:\n" +
                "1. Instala Python\n" +
                "2. Ejecuta: pip install -r requirements.txt\n" +
                "3. Reinicia la aplicacion"
            );
        }
        engineStatusLabel.setTooltip(engineTooltip);

        // Boton principal
        startButton = new Button("Capturar");
        startButton.setOnAction(e -> iniciarSeleccionSecuencial());
        startButton.setPrefWidth(300);
        startButton.setPrefHeight(55);
        startButton.setStyle(
            "-fx-font-size: 15px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-color: #4CAF50; " +
            "-fx-text-fill: white; " +
            "-fx-background-radius: 8px; " +
            "-fx-cursor: hand;"
        );

        // Efectos hover
        startButton.setOnMouseEntered(e -> 
            startButton.setStyle(
                "-fx-font-size: 15px; " +
                "-fx-font-weight: bold; " +
                "-fx-background-color: #45a049; " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 8px; " +
                "-fx-cursor: hand;"
            )
        );
        startButton.setOnMouseExited(e -> 
            startButton.setStyle(
                "-fx-font-size: 15px; " +
                "-fx-font-weight: bold; " +
                "-fx-background-color: #4CAF50; " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 8px; " +
                "-fx-cursor: hand;"
            )
        );

        // Informacion de atajo
        Text shortcutInfo = new Text("⌨️ Atajo de teclado: Ctrl+Shift+S");
        shortcutInfo.setStyle("-fx-font-size: 11px; -fx-fill: #999;");

        // Separador
        Separator separator = new Separator();

        // Layout principal
        VBox root = new VBox(12);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(25));
        root.getChildren().addAll(
            titleLabel,
            engineStatusLabel,
            separator,
            startButton,
            shortcutInfo
        );

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            e.consume(); // Prevenir cierre directo
            cerrarAplicacion();
        });
        stage.show();
    }

    // ==================== ATAJOS GLOBALES ====================
    
    private void configurarAtajosGlobales() {
        globalKeyListener = new GlobalKeyboardListener(() -> {
            Platform.runLater(() -> {
                iniciarSeleccionSecuencial();
            });
        });
        
        globalKeyListener.register();
    }

    // ==================== INICIALIZACIoN DE TESSERACT ====================
    
    private boolean inicializarTesseract() {
        try {
            String tessdataPath = prepararTessdata();
            if (tessdataPath == null) {
                LOGGER.severe("No se pudo preparar tessdata");
                return false;
            }
            
            configurarTesseract(tessdataPath);
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al inicializar Tesseract", e);
            return false;
        }
    }

    private void configurarTesseract(String tessdataPath) {
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage("jpn+eng+spa");
        tesseract.setPageSegMode(3);
        tesseract.setOcrEngineMode(1);
    }

    private String prepararTessdata() {
        try {
            File tessDir = new File(TESSDATA_DIR);
            if (!tessDir.exists() && !tessDir.mkdirs()) {
                return null;
            }

            for (String lang : LANGUAGES) {
                copiarArchivoIdioma(tessDir, lang);
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
                return true;
            }

            String resourcePath = "/tessdata/" + fileName;
            try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    LOGGER.warning("No se encontro: " + resourcePath);
                    return false;
                }
                
                Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error copiando: " + lang, e);
            return false;
        }
    }

    // ==================== LoGICA DE SELECCIoN ====================
    
    private void iniciarSeleccionSecuencial() {
        if (isProcessing) {
            LOGGER.warning("Proceso ya activo");
            mostrarAdvertencia("Proceso Activo", 
                "Ya hay una captura en progreso.\nEspera a que finalice.");
            return;
        }

        isProcessing = true;
        startButton.setDisable(true);

        if (currentScreen != null) {
            currentScreen.forceClose();
        }

        primaryStage.hide();

        LOGGER.info("Iniciando captura...");

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
            LOGGER.info(String.format("area seleccionada: x=%d, y=%d, w=%d, h=%d", 
                area.x, area.y, area.width, area.height));
            hacerOCR(area);
        } else {
            LOGGER.info("Seleccion cancelada");
        }

        primaryStage.show();
        isProcessing = false;
        startButton.setDisable(false);
    }

    // ==================== PROCESAMIENTO OCR ====================
    
    private void hacerOCR(Rectangle area) {
        // Mostrar indicador de progreso
        Platform.runLater(() -> {
            startButton.setText("Procesando...");
        });

        // Ejecutar OCR en thread separado
        new Thread(() -> {
            try {
                LOGGER.info("Capturando imagen del...");
                
                Robot robot = new Robot();
                BufferedImage img = robot.createScreenCapture(area);
                
                String texto = null;
                String motorUsado = "";
                
                // Intentar con Manga OCR primero
                if (useMangaOCR && mangaOCR != null && mangaOCR.isServerAvailable()) {
                    try {
                        LOGGER.info("Procesando con Manga OCR...");
                        texto = mangaOCR.processImage(img);
                        motorUsado = "Manga OCR";
                        LOGGER.info("Manga OCR exitoso");
                        
                    } catch (Exception e) {
                        LOGGER.warning("Manga OCR fallo, intentando con Tesseract...");
                        LOGGER.log(Level.FINE, "Error de Manga OCR", e);
                        texto = null;
                    }
                }
                
                // Fallback a Tesseract si Manga OCR no funciono
                if (texto == null) {
                    LOGGER.info("Procesando con Tesseract...");
                    
                    // Preprocesar imagen para mejorar precision
                    img = ImagePreprocessor.preprocess(img, true);
                    
                    texto = tesseract.doOCR(img);
                    motorUsado = "Tesseract";
                    LOGGER.info("Tesseract completado");
                }

                // Validar resultado
                if (texto == null || texto.trim().isEmpty()) {
                    LOGGER.info("No se detecto texto");
                    
                    final String motorFinal = motorUsado;
                    Platform.runLater(() -> {
                        startButton.setText("Capturar");
                        mostrarInfo("Sin Resultados", 
                            "No se detecto texto en el area seleccionada.\n" +
                            "Motor usado: " + motorFinal + "\n\n" +
                            "Sugerencias:\n" +
                            "• Selecciona solo el area del\n" +
                            "• Asegurate de que el texto sea claro\n" +
                            "• Aumenta el tamaño del area seleccionada");
                    });
                    return;
                }

                // Mostrar resultado
                final String textoFinal = texto.trim();
                final String motorFinal = motorUsado;
                int caracteres = textoFinal.length();
                
                LOGGER.info("Texto detectado: " + caracteres + " caracteres");
                LOGGER.info("Texto: " + textoFinal.substring(0, Math.min(50, textoFinal.length())) + "...");
                
                Platform.runLater(() -> {
                    startButton.setText("Capturar");
                    ResultWindow win = new ResultWindow(textoFinal, motorFinal);
                    win.show();
                });

            } catch (java.awt.AWTException e) {
                LOGGER.log(Level.SEVERE, "Error al capturar pantalla", e);
                Platform.runLater(() -> {
                    startButton.setText("Capturar");
                    mostrarError("Error de Captura", 
                        "No se pudo capturar la pantalla.\n" +
                        "Error: " + e.getMessage());
                });
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error durante OCR", e);
                Platform.runLater(() -> {
                    startButton.setText("Capturar");
                    mostrarError("Error OCR", 
                        "Error al procesar el texto.\n" +
                        "Error: " + e.getMessage());
                });
            }
        }).start();
    }

    // ==================== CIERRE DE APLICACIoN ====================
    
    @Override
    public void stop() throws Exception {
        cerrarAplicacion();
        super.stop();
    }

    private void cerrarAplicacion() {
        LOGGER.info("Cerrando aplicacion...");
        
        // Desregistrar atajos globales
        if (globalKeyListener != null) {
            globalKeyListener.unregister();
        }
        
        // Cerrar pantalla de seleccion si esta abierta
        if (currentScreen != null) {
            try {
                currentScreen.forceClose();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error al cerrar SelectionScreen", e);
            }
        }
        
        // Detener servidor Manga OCR
        if (serverManager != null) {
            serverManager.detenerServidor();
        }
        
        LOGGER.info("Aplicacion cerrada correctamente");
        Platform.exit();
    }

    // ==================== DIaLOGOS ====================
    
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

    // ==================== PUNTO DE ENTRADA ====================
    
    public static void main(String[] args) {
        launch(args);
    }
}