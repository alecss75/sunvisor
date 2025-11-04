package com.ucaribe.sunvisor;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

// Ventana para mostrar los resultados del OCR
public class ResultWindow {

    private Stage stage;
    private String texto;
    private String motorOCR;
    private TextArea translationArea;
    private boolean hasTranslation;

    public ResultWindow(String texto) {
        this(texto, "OCR");
    }

    public ResultWindow(String texto, String motorOCR) {
        this.texto = texto;
        this.motorOCR = motorOCR;
        this.hasTranslation = false;
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Constructor con soporte para traducción                                   //
    // @param texto Texto del OCR                                                //
    // @param motorOCR Motor usado para OCR                                      //
    // @param traduccion Texto traducido (null si está pendiente)                //
    // @param translationPending true si la traducción está en progreso          //
    ///////////////////////////////////////////////////////////////////////////////
    public ResultWindow(String texto, String motorOCR, String traduccion, boolean translationPending) {
        this.texto = texto;
        this.motorOCR = motorOCR;
        this.hasTranslation = true;
    }

    public void show() {
        stage = new Stage();
        stage.setTitle("Resultado OCR" + (hasTranslation ? " + Traducción" : ""));

        if (hasTranslation) {
            showWithTranslation();
        } else {
            showNormal();
        }
    }

    // Muestra ventana normal sin traducción
    private void showNormal() {
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
            copiarAlPortapapeles(texto);
            copiarBtn.setText("✓ Copiado");
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    javafx.application.Platform.runLater(() -> copiarBtn.setText("Copiar"));
                } catch (InterruptedException ex) {
                }
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

    // Muestra ventana con layout dividido para OCR + Traducción
    private void showWithTranslation() {
        BorderPane root = new BorderPane();

        // Header con información
        Label infoLabel = new Label("Motor: " + motorOCR + " | Caracteres: " + texto.length());
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        infoLabel.setPadding(new Insets(5, 10, 5, 10));

        // Panel izquierdo: OCR
        VBox leftPanel = new VBox(5);
        Label ocrLabel = new Label("Texto Original (OCR)");
        ocrLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        TextArea ocrArea = new TextArea(texto);
        ocrArea.setEditable(true);
        ocrArea.setWrapText(true);
        ocrArea.setStyle("-fx-font-size: 14px;");

        leftPanel.getChildren().addAll(ocrLabel, ocrArea);
        VBox.setMargin(ocrLabel, new Insets(5, 5, 0, 5));
        VBox.setMargin(ocrArea, new Insets(0, 5, 5, 5));

        // Panel derecho: Traducción
        VBox rightPanel = new VBox(5);
        Label translationLabel = new Label("Traducción (Japonés → Inglés)");
        translationLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        translationArea = new TextArea("Traduciendo...\n\nPor favor espera mientras se traduce el texto.");
        translationArea.setEditable(true);
        translationArea.setWrapText(true);
        translationArea.setStyle("-fx-font-size: 14px; -fx-text-fill: #999;");

        rightPanel.getChildren().addAll(translationLabel, translationArea);
        VBox.setMargin(translationLabel, new Insets(5, 5, 0, 5));
        VBox.setMargin(translationArea, new Insets(0, 5, 5, 5));

        // SplitPane para dividir OCR y Traducción
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(leftPanel, rightPanel);
        splitPane.setDividerPositions(0.5); // Dividir 50/50

        // Botones
        Button copiarOcrBtn = new Button("Copiar OCR");
        copiarOcrBtn.setOnAction(e -> {
            copiarAlPortapapeles(texto);
            copiarOcrBtn.setText("✓ Copiado");
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    javafx.application.Platform.runLater(() -> copiarOcrBtn.setText("Copiar OCR"));
                } catch (InterruptedException ex) {
                }
            }).start();
        });

        Button copiarTraduccionBtn = new Button("Copiar Traducción");
        copiarTraduccionBtn.setOnAction(e -> {
            String translationText = translationArea.getText();
            if (!translationText.startsWith("Traduciendo")) {
                copiarAlPortapapeles(translationText);
                copiarTraduccionBtn.setText("✓ Copiado");
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        javafx.application.Platform.runLater(() -> copiarTraduccionBtn.setText("Copiar Traducción"));
                    } catch (InterruptedException ex) {
                    }
                }).start();
            }
        });

        Button copiarAmbosBtn = new Button("Copiar Todo");
        copiarAmbosBtn.setOnAction(e -> {
            String translationText = translationArea.getText();
            String combined = "=== TEXTO ORIGINAL ===\n" + texto + "\n\n=== TRADUCCIÓN ===\n" + translationText;
            copiarAlPortapapeles(combined);
            copiarAmbosBtn.setText("✓ Copiado");
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    javafx.application.Platform.runLater(() -> copiarAmbosBtn.setText("Copiar Todo"));
                } catch (InterruptedException ex) {
                }
            }).start();
        });

        Button cerrarBtn = new Button("Cerrar");
        cerrarBtn.setOnAction(e -> stage.close());

        HBox buttonBox = new HBox(10, copiarOcrBtn, copiarTraduccionBtn, copiarAmbosBtn, cerrarBtn);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10));

        // Layout principal
        VBox.setVgrow(splitPane, javafx.scene.layout.Priority.ALWAYS);

        root.setTop(infoLabel);
        root.setCenter(splitPane);
        root.setBottom(buttonBox);

        Scene scene = new Scene(root, 900, 500);
        stage.setScene(scene);
        stage.show();
    }

    // Actualiza el área de traducción con el texto traducido
    // @param translation Texto traducido
    public void updateTranslation(String translation) {
        if (translationArea != null) {
            translationArea.setText(translation);
            translationArea.setStyle("-fx-font-size: 14px; -fx-text-fill: black;");
        }
    }

    private void copiarAlPortapapeles(String texto) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(texto);
        clipboard.setContent(content);

        System.out.println("✅ Texto copiado al portapapeles");
    }
}
