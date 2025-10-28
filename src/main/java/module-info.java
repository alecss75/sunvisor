module com.ucaribe.sunvisor {
    
    // --- Modulos que REQUERIMOS ---
    requires transitive javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires tess4j;
    requires java.desktop; 
    requires java.prefs;
    requires java.logging;
    requires java.net.http;
    requires com.google.gson;
    requires transitive com.github.kwhat.jnativehook;

    
    // --- Modulos que ABRIMOS  ---
    opens com.ucaribe.sunvisor to javafx.fxml,  javafx.graphics;

    exports com.ucaribe.sunvisor;
}