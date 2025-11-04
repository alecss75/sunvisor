package com.ucaribe.sunvisor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Screen;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

public class App extends Application {
    
    // ==================== CONSTANTES ====================
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    private static final double WINDOW_WIDTH = 380;
    private static final double WINDOW_HEIGHT = 280;
    private static final String TESSDATA_DIR = "tessdata";
    private static final String[] LANGUAGES = {"eng", "spa", "jpn", "jpn_vert"};
    private static final String APP_TITLE = "SunVisor OCR";
    
    // Tipos de alfabeto disponibles
    private enum AlphabetType {
        LATIN("Alfabeto Latino", "eng+spa", false),
        JAPANESE("Alfabeto Japonés", "jpn+jpn_vert", true);
        
        final String displayName;
        final String tessCode;
        final boolean needsAdvancedPreprocessing;
        
        AlphabetType(String displayName, String tessCode, boolean needsAdvancedPreprocessing) {
            this.displayName = displayName;
            this.tessCode = tessCode;
            this.needsAdvancedPreprocessing = needsAdvancedPreprocessing;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    // ==================== VARIABLES DE INSTANCIA ====================
    private final ITesseract tesseract = new Tesseract();
    private SelectionScreen currentScreen = null;
    private Stage primaryStage;
    private boolean isProcessing = false;
    
    // UI Components
    private Button startButton;
    private CheckBox translationCheckBox;
    private TranslationClient translationClient;
    private Label statusLabel;
    private ProgressIndicator loadingIndicator;
    private ChoiceBox<AlphabetType> languageSelector;
    
    // Selected alphabet type
    private AlphabetType selectedAlphabet = AlphabetType.JAPANESE; // Por defecto japonés
    
    // OCR Components
    private GlobalKeyboardListener globalKeyListener;
    private MangaOCRServerManager serverManager;
    private MangaOCRClient mangaOCR;
    private boolean useMangaOCR = false;
    
    // Performance Optimization: Reutilizar Robot y usar ExecutorService
    private Robot robot;
    private final ExecutorService ocrExecutor = Executors.newSingleThreadExecutor();

    // ==================== INICIO DE APLICACIoN ====================
    
    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle(APP_TITLE);
        
        // IMPORTANTE: Evitar que JavaFX cierre la aplicación automáticamente
        // cuando se oculta la ventana principal para mostrar SelectionScreen
        Platform.setImplicitExit(false);

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
        LOGGER.info("Iniciando SunVisor OCR");
        LOGGER.info("=".repeat(60));
        
        // Inicializar Robot (reutilizar para todas las capturas)
        try {
            robot = new Robot();
            LOGGER.info("Robot inicializado");
        } catch (java.awt.AWTException e) {
            LOGGER.log(Level.SEVERE, "No se pudo crear Robot", e);
            throw new Exception("No se pudo crear Robot para capturar pantalla", e);
        }
        
        // Inicializar Tesseract 
        actualizarEstado("Inicializando Tesseract...");
        if (!inicializarTesseract()) {
            throw new Exception("No se pudo inicializar Tesseract OCR");
        }
        LOGGER.info("Tesseract inicializado");

        // Inicializar cliente de traducción
        actualizarEstado("Inicializando servicio de traducción...");
        translationClient = new TranslationClient();
        LOGGER.info("Cliente de traducción inicializado");

        // 2. Intentar iniciar servidor Manga OCR
        actualizarEstado("Iniciando Manga OCR Server...");
        iniciarMangaOCRServer();

        // 3. Configurar atajos globales
        actualizarEstado("Configurando atajos de teclado...");
        try {
            configurarAtajosGlobales();
            LOGGER.info("Atajos configurados (Ctrl+Alt+T)");
        } catch (Exception e) {
            LOGGER.warning("No se pudieron configurar atajos globales (se requieren permisos de administrador)");
            LOGGER.warning("La aplicacion funcionara normalmente sin atajos globales");
        }

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
            
            // Mostrar mensaje inicial
            Platform.runLater(() -> {
                if (primaryStage != null) {
                    primaryStage.setTitle(APP_TITLE + " - Preparando servidor...");
                }
            });
            
            // Establecer callback para actualizar título con el progreso
            serverManager.setProgressCallback(mensaje -> {
                javafx.application.Platform.runLater(() -> {
                    if (primaryStage != null) {
                        primaryStage.setTitle(APP_TITLE + " - " + mensaje);
                    }
                });
            });
            
            LOGGER.info("Intentando iniciar servidor FastAPI...");
            boolean servidorIniciado = serverManager.iniciarServidor();

            if (!servidorIniciado) {
                LOGGER.warning("No se pudo iniciar servidor Manga OCR");
                
                // Restaurar título en caso de error
                Platform.runLater(() -> {
                    if (primaryStage != null) {
                        primaryStage.setTitle(APP_TITLE);
                    }
                });
                
                return false;
            }

            // Crear cliente
            mangaOCR = new MangaOCRClient();
            
            // Esperar a que el servidor este listo (maximo 60 segundos para primera carga del modelo)
            LOGGER.info("Esperando a que el servidor este listo...");
            int intentos = 0;
            int maxIntentos = 60;
            
            while (intentos < maxIntentos) {
                if (mangaOCR.isServerAvailable()) {
                    LOGGER.info("Manga OCR Server listo");
                    useMangaOCR = true;
                    
                    // Restaurar título original
                    Platform.runLater(() -> {
                        if (primaryStage != null) {
                            primaryStage.setTitle(APP_TITLE);
                        }
                    });
                    
                    return true;
                }
                
                Thread.sleep(1000);
                intentos++;
                
                if (intentos % 10 == 0) {
                    LOGGER.info("Esperando servidor... " + intentos + "/" + maxIntentos + "s");
                }
            }
            
            LOGGER.warning("Timeout esperando servidor (60s) - Continuará con Tesseract");
            
            // Restaurar título en caso de timeout
            Platform.runLater(() -> {
                if (primaryStage != null) {
                    primaryStage.setTitle(APP_TITLE);
                }
            });
            
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
        Text shortcutInfo = new Text("Atajo de teclado: Ctrl+Shift+T");
        shortcutInfo.setStyle("-fx-font-size: 11px; -fx-fill: #999;");

        // Selector de alfabeto
        Label languageLabel = new Label("Tipo de texto:");
        languageLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        
        languageSelector = new ChoiceBox<>();
        languageSelector.getItems().addAll(AlphabetType.values());
        languageSelector.setValue(selectedAlphabet);
        languageSelector.setPrefWidth(300);
        languageSelector.setStyle("-fx-font-size: 12px;");
        
        // Tooltip del selector
        Tooltip languageTooltip = new Tooltip(
            "Selecciona el tipo de alfabeto del texto:\n" +
            "• Alfabeto Latino: Español, Inglés (procesamiento rápido)\n" +
            "• Alfabeto Japonés: Hiragana, Katakana, Kanji (usa Manga OCR si está disponible)"
        );
        languageSelector.setTooltip(languageTooltip);
        
        // Actualizar alfabeto cuando cambie
        languageSelector.setOnAction(e -> {
            selectedAlphabet = languageSelector.getValue();
            LOGGER.info("Alfabeto seleccionado: " + selectedAlphabet.displayName);
            actualizarTesseractLanguage();
            
            // Mostrar/ocultar checkbox de traducción según el idioma
            boolean isJapanese = selectedAlphabet == AlphabetType.JAPANESE;
            translationCheckBox.setVisible(isJapanese);
            translationCheckBox.setManaged(isJapanese); // Para que no ocupe espacio cuando está oculto
        });

        // Checkbox de traducción
        translationCheckBox = new CheckBox("Incluir traducción local (Japonés → Inglés)");
        translationCheckBox.setSelected(false); // Deshabilitado por defecto
        translationCheckBox.setStyle("-fx-font-size: 12px;");
        
        // Inicialmente visible solo si el alfabeto por defecto es japonés
        boolean isJapanese = selectedAlphabet == AlphabetType.JAPANESE;
        translationCheckBox.setVisible(isJapanese);
        translationCheckBox.setManaged(isJapanese);
        
        Tooltip translateTooltip = new Tooltip(
            "Traduce automáticamente el texto japonés al inglés usando modelo local.\n" +
            "• Requiere modelo descargado (~300MB primera vez)\n" +
            "• Agrega 2-5 segundos al procesamiento\n" +
            "• Funciona completamente offline"
        );
        translationCheckBox.setTooltip(translateTooltip);

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
            languageLabel,
            languageSelector,
            translationCheckBox,
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
    
    private void configurarAtajosGlobales() throws Exception {
        try {
            // Intentar cargar la clase GlobalScreen SIN instanciar nada
            // Si esto falla con UnsatisfiedLinkError, no tenemos permisos
            Class.forName("com.github.kwhat.jnativehook.GlobalScreen");
            
            // Si llegamos aquí, la DLL se cargó correctamente
            globalKeyListener = new GlobalKeyboardListener(() -> {
                Platform.runLater(() -> {
                    iniciarSeleccionSecuencial();
                });
            });
            
            globalKeyListener.register();
            
        } catch (UnsatisfiedLinkError e) {
            // La DLL no se pudo cargar (sin permisos de admin)
            LOGGER.warning("JNativeHook DLL no disponible (requiere permisos de administrador)");
            throw new Exception("Atajos globales requieren permisos de administrador", e);
        } catch (ClassNotFoundException e) {
            // La clase no existe (problema de dependencias)
            LOGGER.severe("Clase GlobalScreen no encontrada");
            throw new Exception("JNativeHook no está disponible", e);
        }
    }

    // ==================== INICIALIZACIoN DE TESSERACT ====================
    
    private boolean inicializarTesseract() {
        try {
            LOGGER.info("Iniciando configuración de Tesseract...");
            
            String tessdataPath = prepararTessdata();
            if (tessdataPath == null) {
                LOGGER.severe("No se pudo preparar tessdata");
                return false;
            }
            
            LOGGER.info("Tessdata preparado en: " + tessdataPath);
            
            configurarTesseract(tessdataPath);
            
            // Verificar que Tesseract esté realmente funcional haciendo un test
            try {
                LOGGER.info("Verificando que Tesseract funcione correctamente...");
                BufferedImage testImg = new BufferedImage(100, 30, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = testImg.createGraphics();
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, 100, 30);
                g.setColor(java.awt.Color.BLACK);
                g.drawString("Test", 10, 20);
                g.dispose();
                
                String testResult = tesseract.doOCR(testImg);
                LOGGER.info("Test de Tesseract exitoso, resultado: '" + testResult + "'");
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Test de Tesseract falló", e);
                throw e;
            }
            
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al inicializar Tesseract", e);
            return false;
        }
    }

    private void configurarTesseract(String tessdataPath) {
        // Configurar el directorio de datos
        tesseract.setDatapath(tessdataPath);
        
        // Configurar idioma
        actualizarTesseractLanguage();
        
        // Configurar modo de segmentación de página
        tesseract.setPageSegMode(3);
        
        tesseract.setOcrEngineMode(1);
        
        // Ubicación de las DLLs nativas de Tesseract
        String nativeLibPath = System.getProperty("java.library.path", "");
        LOGGER.info("java.library.path actual: " + nativeLibPath);
        
        // Si estamos empaquetados con jpackage, las DLLs están en app/
        String appHome = System.getProperty("app.home");
        if (appHome != null) {
            String appLibPath = appHome + File.separator + "app";
            LOGGER.info("Agregando ruta de app empaquetada: " + appLibPath);
            System.setProperty("jna.library.path", appLibPath);
        }
        
        LOGGER.info("Tesseract configurado en modo nativo (JNA/LSTM)");
    }
    
    // Actualiza el idioma de Tesseract según la selección del usuario
    private void actualizarTesseractLanguage() {
        tesseract.setLanguage(selectedAlphabet.tessCode);
        LOGGER.info("Tesseract configurado con alfabeto: " + selectedAlphabet.tessCode);
    }

    private String prepararTessdata() {
        try {
            // Usar directorio de datos de usuario (AppData\Local\SunVisor-OCR\tessdata)
            String appDataPath = System.getenv("LOCALAPPDATA");
            File appDataDir;
            
            if (appDataPath != null && !appDataPath.isEmpty()) {
                // Windows: usar %LOCALAPPDATA%\SunVisor-OCR\tessdata
                appDataDir = new File(appDataPath, "SunVisor-OCR");
            } else {
                // Fallback: usar directorio home del usuario
                String userHome = System.getProperty("user.home");
                appDataDir = new File(userHome, ".sunvisor-ocr");
            }
            
            File tessDir = new File(appDataDir, TESSDATA_DIR);
            if (!tessDir.exists() && !tessDir.mkdirs()) {
                LOGGER.severe("No se pudo crear directorio tessdata: " + tessDir.getAbsolutePath());
                return null;
            }

            for (String lang : LANGUAGES) {
                copiarArchivoIdioma(tessDir, lang);
            }

            LOGGER.info("Tessdata preparado en: " + tessDir.getAbsolutePath());
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

        // Ocultar ventana principal
        primaryStage.hide();

        LOGGER.info("Iniciando captura...");

        // Esperar a que la ventana termine de cerrarse y la pantalla se refresque antes de capturar el screenshot
        new Thread(() -> {
            try {
                Thread.sleep(300); // 300ms para que desaparezca completamente y se refresque la pantalla
                
                Platform.runLater(() -> {
                    try {
                        LOGGER.info("Creando SelectionScreen...");
                        
                        // Detectar en qué monitor está la ventana principal
                        Screen currentMonitor = detectarMonitorActual();
                        LOGGER.info("Monitor actual detectado: " + currentMonitor.getBounds());
                        
                        currentScreen = new SelectionScreen(currentMonitor, area -> {
                            Platform.runLater(() -> {
                                finalizarSeleccion(area);
                            });
                        });

                        LOGGER.info("Iniciando selección...");
                        currentScreen.startSelection();
                        LOGGER.info("Selección iniciada correctamente");
                        
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Error al crear SelectionScreen", e);
                        mostrarError("Error de Captura", 
                            "No se pudo iniciar la captura de pantalla.\nError: " + e.getMessage());
                        primaryStage.show();
                        isProcessing = false;
                        startButton.setDisable(false);
                    }
                });
                
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Delay interrumpido", e);
                Platform.runLater(() -> {
                    primaryStage.show();
                    isProcessing = false;
                    startButton.setDisable(false);
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error inesperado en thread de captura", e);
                Platform.runLater(() -> {
                    mostrarError("Error Inesperado", 
                        "Ocurrió un error al preparar la captura.\nError: " + e.getMessage());
                    primaryStage.show();
                    isProcessing = false;
                    startButton.setDisable(false);
                });
            }
        }).start();
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

        // Ejecutar OCR en thread separado usando ExecutorService
        ocrExecutor.submit(() -> {
            try {
                LOGGER.info("Capturando imagen del area...");
                
                // Verificar que Robot esté inicializado
                if (robot == null) {
                    LOGGER.severe("Robot no está inicializado");
                    Platform.runLater(() -> {
                        mostrarError("Error de Inicialización", 
                            "El sistema de captura no está listo.\nIntenta reiniciar la aplicación.");
                        startButton.setText("Capturar Pantalla");
                    });
                    return;
                }
                
                // Capturar pantalla (puede fallar si no hay permisos)
                BufferedImage img;
                try {
                    img = robot.createScreenCapture(area);
                } catch (SecurityException e) {
                    LOGGER.log(Level.SEVERE, "Sin permisos para capturar pantalla", e);
                    Platform.runLater(() -> {
                        mostrarError("Error de Permisos", 
                            "No se tienen permisos para capturar la pantalla.\n" +
                            "Intenta ejecutar como administrador.");
                        startButton.setText("Capturar Pantalla");
                    });
                    return;
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error capturando pantalla", e);
                    Platform.runLater(() -> {
                        mostrarError("Error de Captura", 
                            "No se pudo capturar la pantalla.\n" +
                            "Error: " + e.getMessage());
                        startButton.setText("Capturar Pantalla");
                    });
                    return;
                }
                
                String texto = null;
                String motorUsado = "";
                
                // Intentar con Manga OCR solo si es alfabeto japonés
                if (selectedAlphabet == AlphabetType.JAPANESE && 
                    useMangaOCR && mangaOCR != null && mangaOCR.isServerAvailable()) {
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
                
                // Usar Tesseract si Manga OCR no funciono o si es alfabeto latino
                if (texto == null) {
                    LOGGER.info("Procesando con Tesseract (" + selectedAlphabet.displayName + ")...");
                    
                    // Preprocesar imagen según el alfabeto seleccionado
                    long t0 = System.nanoTime();
                    img = ImagePreprocessor.preprocess(img, selectedAlphabet.needsAdvancedPreprocessing);
                    long preprocessMs = (System.nanoTime() - t0) / 1_000_000;
                    LOGGER.info("Preprocesamiento completado en " + preprocessMs + " ms");
                    
                    texto = tesseract.doOCR(img);
                    motorUsado = "Tesseract (" + selectedAlphabet.displayName + ")";
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
                            "• Selecciona solo el area del texto\n" +
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
                    
                    // Verificar si se debe traducir
                    boolean shouldTranslate = translationCheckBox.isSelected() && 
                                             selectedAlphabet == AlphabetType.JAPANESE;
                    
                    if (shouldTranslate) {
                        // Mostrar ventana con traducción pendiente
                        ResultWindow win = new ResultWindow(textoFinal, motorFinal, null, true);
                        win.show();
                        
                        // Iniciar traducción asíncrona
                        translateTextAsync(textoFinal, win);
                    } else {
                        // Mostrar ventana normal sin traducción
                        ResultWindow win = new ResultWindow(textoFinal, motorFinal);
                        win.show();
                    }
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
        });
    }

    // ==================== TRADUCCIÓN ====================
    
    // Traduce texto de manera asíncrona y actualiza la ventana de resultados
    private void translateTextAsync(String japaneseText, ResultWindow resultWindow) {
        new Thread(() -> {
            try {
                LOGGER.info("Iniciando traducción...");
                
                // Verificar disponibilidad del servicio
                if (!translationClient.isAvailable()) {
                    Platform.runLater(() -> {
                        resultWindow.updateTranslation("Error: Servicio de traducción no disponible.\n" +
                            "Asegúrate de que el servidor Python esté ejecutándose.");
                    });
                    return;
                }
                
                // Realizar traducción
                String translation = translationClient.translate(japaneseText);
                
                // Actualizar UI con la traducción
                Platform.runLater(() -> {
                    resultWindow.updateTranslation(translation);
                    LOGGER.info("Traducción completada y mostrada");
                });
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error durante traducción", e);
                Platform.runLater(() -> {
                    resultWindow.updateTranslation("Error al traducir: " + e.getMessage() + "\n\n" +
                        "Posibles causas:\n" +
                        "• El servidor Python no está ejecutándose\n" +
                        "• El modelo de traducción no está instalado\n" +
                        "• Error de conexión con el servicio");
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

    // ==================== DETECCIÓN DE MONITOR ====================
    
    // Detecta en qué monitor está ubicada la ventana principal
    private Screen detectarMonitorActual() {
        // Obtener la posición de la ventana
        double windowX = primaryStage.getX();
        double windowY = primaryStage.getY();
        double windowCenterX = windowX + primaryStage.getWidth() / 2;
        double windowCenterY = windowY + primaryStage.getHeight() / 2;
        
        LOGGER.info("Ventana en: " + windowX + "," + windowY + " Centro: " + windowCenterX + "," + windowCenterY);
        
        // Buscar en qué monitor está el centro de la ventana
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getVisualBounds();
            if (windowCenterX >= bounds.getMinX() && windowCenterX < bounds.getMaxX() &&
                windowCenterY >= bounds.getMinY() && windowCenterY < bounds.getMaxY()) {
                LOGGER.info("Monitor encontrado: " + bounds + " DPI: " + screen.getDpi());
                return screen;
            }
        }
        
        // Si no se encontró, devolver el primario
        LOGGER.warning("No se encontró monitor específico, usando primario");
        return Screen.getPrimary();
    }
    
    // ==================== CIERRE DE APLICACION ====================
    
    private void cerrarAplicacion() {
        LOGGER.info("Cerrando aplicacion...");
        
        // Desregistrar atajos globales
        if (globalKeyListener != null) {
            try {
                globalKeyListener.unregister();
                LOGGER.info("Atajos globales desregistrados");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error al desregistrar atajos globales", e);
            }
        }
        
        // Cerrar pantalla de seleccion si esta abierta
        if (currentScreen != null) {
            try {
                currentScreen.forceClose();
                LOGGER.info("SelectionScreen cerrada");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error al cerrar SelectionScreen", e);
            }
        }
        
        // Detener ExecutorService de forma ordenada
        if (ocrExecutor != null && !ocrExecutor.isShutdown()) {
            LOGGER.info("Deteniendo OCR executor...");
            ocrExecutor.shutdown();
            try {
                if (!ocrExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOGGER.warning("Timeout esperando OCR executor, forzando shutdown...");
                    ocrExecutor.shutdownNow();
                    
                    // Dar un segundo más para que se termine
                    if (!ocrExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                        LOGGER.severe("OCR executor no respondió al shutdownNow");
                    }
                }
                LOGGER.info("OCR executor detenido");
            } catch (InterruptedException ex) {
                LOGGER.warning("Interrupción durante shutdown del executor");
                ocrExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Detener servidor Manga OCR
        if (serverManager != null) {
            LOGGER.info("Deteniendo servidor Manga OCR...");
            serverManager.detenerServidor();
            LOGGER.info("Servidor Manga OCR detenido");
        }
        
        LOGGER.info("Aplicacion cerrada correctamente");
        
        // Salir de JavaFX Platform
        Platform.exit();
        
        // FORZAR salida del sistema después de un pequeño delay
        // Esto asegura que todos los threads daemon se terminen
        new Thread(() -> {
            try {
                Thread.sleep(500);
                LOGGER.info("Forzando salida del sistema...");
                System.exit(0);
            } catch (InterruptedException e) {
                System.exit(0);
            }
        }).start();
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