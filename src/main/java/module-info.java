module sun.visor {
    
    // --- Módulos que REQUERIMOS ---
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires tess4j;
    requires java.desktop; 
    requires java.net.http;
    requires com.google.gson;
    requires com.github.kwhat.jnativehook;

    
    // --- Módulos que ABRIMOS  ---
    opens sun.visor to javafx.fxml, com.google.gson; 

    exports sun.visor;
}