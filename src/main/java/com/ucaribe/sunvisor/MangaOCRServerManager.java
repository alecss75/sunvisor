package com.ucaribe.sunvisor;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.logging.Level;

// Gestiona el ciclo de vida del servidor Manga OCR FastAPI
public class MangaOCRServerManager {
    
    private static final Logger LOGGER = Logger.getLogger(MangaOCRServerManager.class.getName());
    private static final String PYTHON_SCRIPT = "servicio_ocr.py";
    private static final int SERVER_PORT = 8080;
    
    private Process serverProcess;
    private Thread logReaderThread;
    private Consumer<String> progressCallback;
    
    /**
     * Establece el callback para actualizar el progreso
     */
    public void setProgressCallback(Consumer<String> callback) {
        this.progressCallback = callback;
    }
    
    ///////////////////////////////////////////////
    // Inicia el servidor FastAPI               //
    // @return true si se inicio correctamente  //
    //////////////////////////////////////////////
    public boolean iniciarServidor() {
        try {
            // Buscar el script Python
            File scriptFile = encontrarScript();
            
            if (scriptFile == null || !scriptFile.exists()) {
                LOGGER.warning("No se encontro " + PYTHON_SCRIPT);
                return false;
            }
            
            LOGGER.info("Iniciando servidor FastAPI: " + scriptFile.getAbsolutePath());
            
            // Verificar que Python este instalado
            if (!verificarPython()) {
                LOGGER.severe("Python no esta instalado o no esta en PATH");
                return false;
            }
            
            // Construir comando
            List<String> command = new ArrayList<>();
            command.add(getPythonCommand());
            command.add("-m");
            command.add("uvicorn");
            command.add("servicio_ocr:app");
            command.add("--host");
            command.add("127.0.0.1");
            command.add("--port");
            command.add(String.valueOf(SERVER_PORT));
            command.add("--log-level");
            command.add("info");
            
            // Iniciar proceso
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(scriptFile.getParentFile());
            pb.redirectErrorStream(true);
            
            serverProcess = pb.start();
            
            // Leer logs del servidor en thread separado
            iniciarLectorLogs();
            
            LOGGER.info("Servidor FastAPI iniciado (PID: " + serverProcess.pid() + ")");
            
            return true;
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error al iniciar servidor", e);
            return false;
        }
    }
    
    /**
     * Detiene el servidor
     */
    public void detenerServidor() {
        if (serverProcess != null && serverProcess.isAlive()) {
            LOGGER.info("Deteniendo servidor FastAPI...");
            
            // Intentar cerrar gracefully
            serverProcess.destroy();
            
            try {
                boolean terminated = serverProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!terminated) {
                    LOGGER.warning("Servidor no respondio, forzando cierre...");
                    serverProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                serverProcess.destroyForcibly();
            }
            
            // Detener thread de logs
            if (logReaderThread != null && logReaderThread.isAlive()) {
                logReaderThread.interrupt();
            }
            
            LOGGER.info("✅ Servidor detenido");
        }
    }
    
    /**
     * Verifica si el servidor esta ejecutandose
     */
    public boolean estaEjecutandose() {
        return serverProcess != null && serverProcess.isAlive();
    }
    
    /**
     * Busca el script Python en varias ubicaciones
     */
    private File encontrarScript() {
        // 1. Intentar desde la instalacion (cuando esta empaquetado con jpackage)
        String appPath = System.getProperty("app.home");
        if (appPath == null) {
            appPath = System.getProperty("user.dir");
        }
        
        String[] posiblesPaths = {
            // En instalacion con jpackage
            appPath + File.separator + "app" + File.separator + "backend" + File.separator + PYTHON_SCRIPT,
            appPath + File.separator + "backend" + File.separator + PYTHON_SCRIPT,
            // En desarrollo
            "src/main/resources/backend/" + PYTHON_SCRIPT,
            "resources/backend/" + PYTHON_SCRIPT,
            "backend/" + PYTHON_SCRIPT,
            PYTHON_SCRIPT
        };
        
        for (String path : posiblesPaths) {
            File file = new File(path);
            if (file.exists()) {
                LOGGER.info("Script encontrado en: " + file.getAbsolutePath());
                return file;
            }
        }
        
        // 2. Intentar desde classpath (cuando esta empaquetado en JAR)
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "manga-ocr-server");
            tempDir.mkdirs();
            
            File scriptFile = new File(tempDir, PYTHON_SCRIPT);
            
            // Copiar desde resources si no existe
            if (!scriptFile.exists()) {
                try (var in = getClass().getResourceAsStream("/backend/" + PYTHON_SCRIPT)) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, scriptFile.toPath());
                        LOGGER.info("Script extraido a: " + scriptFile.getAbsolutePath());
                        return scriptFile;
                    }
                }
            } else {
                return scriptFile;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al extraer script", e);
        }
        
        return null;
    }
    
    /**
     * Verifica que Python este instalado
     */
    private boolean verificarPython() {
        try {
            ProcessBuilder pb = new ProcessBuilder(getPythonCommand(), "--version");
            Process process = pb.start();
            
            boolean completed = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            
            if (completed && process.exitValue() == 0) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                );
                String version = reader.readLine();
                LOGGER.info("Python detectado: " + version);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    // Obtiene el comando de Python segun el SO
    private String getPythonCommand() {
        // 1. Intentar usar Python embebido (si esta empaquetado)
        String pythonEmbebido = buscarPythonEmbebido();
        if (pythonEmbebido != null) {
            LOGGER.info("Usando Python embebido: " + pythonEmbebido);
            return pythonEmbebido;
        }
        
        // 2. Fallback a Python del sistema
        String os = System.getProperty("os.name").toLowerCase();
        
        // En Windows, intentar "python" primero, luego "py"
        if (os.contains("win")) {
            return "python";
        }
        
        // En Unix/Mac, usar "python3"
        return "python3";
    }
    
    /**
     * Busca el Python embebido en la instalacion
     * @return ruta completa al python.exe embebido, o null si no existe
     */
    private String buscarPythonEmbebido() {
        try {
            // Obtener la ruta del ejecutable (.exe) actual
            String appPath = System.getProperty("app.home");
            
            // Si no esta definido, intentar con user.dir (directorio actual)
            if (appPath == null) {
                appPath = System.getProperty("user.dir");
            }
            
            // Posibles ubicaciones del Python embebido
            String[] posiblesRutas = {
                // Cuando esta instalado con jpackage
                appPath + File.separator + "app" + File.separator + "python" + File.separator + "python.exe",
                appPath + File.separator + "python" + File.separator + "python.exe",
                // Cuando esta en desarrollo o portable
                "python" + File.separator + "python.exe",
                "app" + File.separator + "python" + File.separator + "python.exe"
            };
            
            for (String ruta : posiblesRutas) {
                File pythonExe = new File(ruta);
                if (pythonExe.exists() && pythonExe.canExecute()) {
                    return pythonExe.getAbsolutePath();
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error buscando Python embebido", e);
        }
        
        return null;
    }
    
    // Inicia thread para leer logs del servidor
    private void iniciarLectorLogs() {
        logReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null && !Thread.interrupted()) {
                    
                    // Parsear progreso de instalación (ejemplo: "Downloading packages: 5/30")
                    if (progressCallback != null && line.contains("/")) {
                        String trimmed = line.trim();
                        // Buscar patron "X/Y" en la línea
                        if (trimmed.matches(".*\\d+/\\d+.*")) {
                            progressCallback.accept(trimmed);
                        }
                    }
                    
                    // Filtrar solo logs importantes
                    if (line.contains("ERROR") || line.contains("WARNING") || 
                        line.contains("Uvicorn running") || line.contains("Application startup complete")) {
                        LOGGER.info("[FastAPI] " + line);
                        
                        // Actualizar título cuando el servidor esté listo
                        if (progressCallback != null && line.contains("Application startup complete")) {
                            progressCallback.accept("Servidor listo");
                        }
                    }
                }
                
            } catch (IOException e) {
                if (!Thread.interrupted()) {
                    LOGGER.log(Level.WARNING, "Error leyendo logs del servidor", e);
                }
            }
        });
        
        logReaderThread.setDaemon(true);
        logReaderThread.start();
    }
}
