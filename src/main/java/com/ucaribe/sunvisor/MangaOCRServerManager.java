package com.ucaribe.sunvisor;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Gestiona el ciclo de vida del servidor Manga OCR FastAPI
 */
public class MangaOCRServerManager {
    
    private static final Logger LOGGER = Logger.getLogger(MangaOCRServerManager.class.getName());
    private static final String PYTHON_SCRIPT = "servicio_ocr.py";
    private static final int SERVER_PORT = 8080;
    
    private Process serverProcess;
    private Thread logReaderThread;
    
    /**
     * Inicia el servidor FastAPI
     * @return true si se inició correctamente
     */
    public boolean iniciarServidor() {
        try {
            // Buscar el script Python
            File scriptFile = encontrarScript();
            
            if (scriptFile == null || !scriptFile.exists()) {
                LOGGER.warning("No se encontró " + PYTHON_SCRIPT);
                return false;
            }
            
            LOGGER.info("Iniciando servidor FastAPI: " + scriptFile.getAbsolutePath());
            
            // Verificar que Python esté instalado
            if (!verificarPython()) {
                LOGGER.severe("Python no está instalado o no está en PATH");
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
            
            LOGGER.info("✅ Servidor FastAPI iniciado (PID: " + serverProcess.pid() + ")");
            
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
                    LOGGER.warning("Servidor no respondió, forzando cierre...");
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
     * Verifica si el servidor está ejecutándose
     */
    public boolean estaEjecutandose() {
        return serverProcess != null && serverProcess.isAlive();
    }
    
    /**
     * Busca el script Python en varias ubicaciones
     */
    private File encontrarScript() {
        // 1. Carpeta resources/backend (desarrollo)
        String[] posiblesPaths = {
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
        
        // 2. Intentar desde classpath (cuando está empaquetado)
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "manga-ocr-server");
            tempDir.mkdirs();
            
            File scriptFile = new File(tempDir, PYTHON_SCRIPT);
            
            // Copiar desde resources si no existe
            if (!scriptFile.exists()) {
                try (var in = getClass().getResourceAsStream("/backend/" + PYTHON_SCRIPT)) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, scriptFile.toPath());
                        LOGGER.info("Script extraído a: " + scriptFile.getAbsolutePath());
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
     * Verifica que Python esté instalado
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
    
    /**
     * Obtiene el comando de Python según el SO
     */
    private String getPythonCommand() {
        String os = System.getProperty("os.name").toLowerCase();
        
        // En Windows, intentar "python" primero, luego "py"
        if (os.contains("win")) {
            return "python";
        }
        
        // En Unix/Mac, usar "python3"
        return "python3";
    }
    
    /**
     * Inicia thread para leer logs del servidor
     */
    private void iniciarLectorLogs() {
        logReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null && !Thread.interrupted()) {
                    // Filtrar solo logs importantes
                    if (line.contains("ERROR") || line.contains("WARNING") || 
                        line.contains("Uvicorn running") || line.contains("Application startup complete")) {
                        LOGGER.info("[FastAPI] " + line);
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
