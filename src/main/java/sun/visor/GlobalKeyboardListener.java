package sun.visor;

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

    /**
     * Constructor
     * 
     * @param onHotkeyPressed acción a ejecutar cuando se presione el atajo
     */
    public GlobalKeyboardListener(Runnable onHotkeyPressed) {
        this.onHotkeyPressed = onHotkeyPressed;
    }

    /**
     * Registra el listener global de teclado
     */
    public void register() {
        try {
            // Deshabilitar logs de JNativeHook (son muy verbosos)
            Logger jnativeLogger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            jnativeLogger.setLevel(Level.OFF);
            jnativeLogger.setUseParentHandlers(false);

            // Registrar hook global
            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook();
            }

            // Agregar este listener
            GlobalScreen.addNativeKeyListener(this);

            LOGGER.info("Atajos de teclado globales activados: Ctrl+VC_ALT+T");

        } catch (NativeHookException e) {
            LOGGER.log(Level.SEVERE, "Error al registrar atajos globales", e);
        }
    }

    /**
     * Desregistra el listener
     */
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
        
        System.out.println("hola" + ctrlPressed + altPressed + tPressed);

        // Detectar Ctrl+VC_ALT+T
        if (ctrlPressed && altPressed && e.getKeyCode() == NativeKeyEvent.VC_T) {
            LOGGER.info("Atajo detectado: Ctrl+VC_ALT+T");

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