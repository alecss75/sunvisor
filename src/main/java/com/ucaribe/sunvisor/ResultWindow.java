package com.ucaribe.sunvisor;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

// Ventana para mostrar los resultados del OCR
public class ResultWindow {
    
    private Stage stage;
    private String texto;
    private String motorOCR;
    
    public ResultWindow(String texto) {
        this(texto, "OCR");
    }
    
    public ResultWindow(String texto, String motorOCR) {
        this.texto = texto;
        this.motorOCR = motorOCR;
    }
    
    public void show() {
        stage = new Stage();
        stage.setTitle("Resultado OCR");
        
        BorderPane root = new BorderPane();
        
        // Header con informacion
        Label infoLabel = new Label("Motor: " + motorOCR + " | Caracteres: " + texto.length());
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        infoLabel.setPadding(new Insets(5, 10, 5, 10));
        
        // Area de texto
        TextArea textArea = new TextArea(texto);
        textArea.setEditable(true);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(15);
        textArea.setStyle("-fx-font-size: 14px;");
        
        // Botones
        Button copiarBtn = new Button("Copiar");
        copiarBtn.setOnAction(e -> {
            copiarAlPortapapeles();
            copiarBtn.setText("Copiado");
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    javafx.application.Platform.runLater(() -> copiarBtn.setText("Copiar"));
                } catch (InterruptedException ex) {}
            }).start();
        });
        
        Button cerrarBtn = new Button("Cerrar");
        cerrarBtn.setOnAction(e -> stage.close());
        
        HBox buttonBox = new HBox(10, copiarBtn, cerrarBtn);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10));
        
        // Layout
        VBox centerBox = new VBox(5, infoLabel, textArea);
        
        root.setCenter(centerBox);
        root.setBottom(buttonBox);
        
        Scene scene = new Scene(root, 600, 400);
        stage.setScene(scene);
        stage.show();
    }
    
    private void copiarAlPortapapeles() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(texto);
        clipboard.setContent(content);
        
        System.out.println("✅ Texto copiado al portapapeles");
    }
}