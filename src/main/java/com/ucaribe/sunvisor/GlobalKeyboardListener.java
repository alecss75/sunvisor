package com.ucaribe.sunvisor;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listener para capturar atajos de teclado globales del sistema
 */
public class GlobalKeyboardListener implements NativeKeyListener {

    private static final Logger LOGGER = Logger.getLogger(GlobalKeyboardListener.class.getName());

    private final Runnable onHotkeyPressed;
    private boolean ctrlPressed = false;
    private boolean altPressed = false;
    private boolean tPressed = false;

    // Constructor
    public GlobalKeyboardListener(Runnable onHotkeyPressed) {
        this.onHotkeyPressed = onHotkeyPressed;
    }

    // Registra el listener global de teclado
    public void register() throws Exception {
        try {
            // Intentar cargar la clase GlobalScreen usando reflection
            // Esto captura el UnsatisfiedLinkError del inicializador estático
            Class<?> globalScreenClass = Class.forName("com.github.kwhat.jnativehook.GlobalScreen");
            
            // Deshabilitar logs de JNativeHook (muchotexto)
            Logger jnativeLogger = Logger.getLogger(globalScreenClass.getPackage().getName());
            jnativeLogger.setLevel(Level.OFF);
            jnativeLogger.setUseParentHandlers(false);

            // Registrar hook global
            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook();
            }

            // Agregar este listener
            GlobalScreen.addNativeKeyListener(this);

            LOGGER.info("Atajos de teclado globales activados: Ctrl+ALT+T");

        } catch (UnsatisfiedLinkError e) {
            LOGGER.log(Level.WARNING, "No se pudo cargar la librería nativa de JNativeHook (se requieren permisos de administrador)");
            throw new Exception("JNativeHook requiere permisos de administrador", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al registrar atajos globales", e);
            throw e; // Re-lanzar para que App.java lo capture
        }
    }

    // Desregistra el listener
    public void unregister() {
        try {
            GlobalScreen.removeNativeKeyListener(this);

            if (GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.unregisterNativeHook();
            }

            LOGGER.info("Atajos globales desactivados");

        } catch (NativeHookException e) {
            LOGGER.log(Level.WARNING, "Error al desregistrar atajos", e);
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        // Detectar teclas modificadoras
        if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL) {
            ctrlPressed = true;
        }

        if (e.getKeyCode() == NativeKeyEvent.VC_ALT) {
            altPressed = true;
        }

        if (e.getKeyCode() == NativeKeyEvent.VC_T) {
            tPressed = true;
        }

        // Detectar Ctrl+VC_ALT+T
        if (ctrlPressed && altPressed && tPressed) {
            LOGGER.info("Atajo detectado: Ctrl+ALT+T");

            if (onHotkeyPressed != null) {
                // Ejecutar en hilo separado para no bloquear el hook
                new Thread(() -> {
                    try {
                        onHotkeyPressed.run();
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, "Error al ejecutar atajo", ex);
                    }
                }).start();
            }
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        // Resetear estado de teclas modificadoras
        if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL) {
            ctrlPressed = false;
        }

        if (e.getKeyCode() == NativeKeyEvent.VC_ALT) {
            altPressed = false;
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // No necesitamos implementar esto
    }
}
