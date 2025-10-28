package com.ucaribe.sunvisor;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class ResultWindow {

    private final String ocrText;
    private Stage stage; // Guardar referencia al stage

    public ResultWindow(String text) {
        this.ocrText = text;
    }

    public void show() {
        stage = new Stage();
        stage.setTitle("Texto Extraído (Sun Visor)");

        TextArea textArea = new TextArea(ocrText);
        textArea.setWrapText(true);
        textArea.setEditable(false);

        Button copyButton = new Button("Copiar Todo");
        copyButton.setOnAction(e -> {
            try {
                // Intentar copiar al portapapeles
                ClipboardContent content = new ClipboardContent();
                content.putString(ocrText);
                Clipboard.getSystemClipboard().setContent(content);

                copyButton.setText("Copiado!");

                Thread resetThread = new Thread(() -> {
                    try {
                        Thread.sleep(2000);

                        javafx.application.Platform.runLater(() -> {
                            if (stage.isShowing()) {
                                copyButton.setText("Copiar Todo");
                            }
                        });

                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        System.out.println("Thread de reset interrumpido");
                    }
                });

                resetThread.setDaemon(true);
                resetThread.setName("ResetButtonThread");
                resetThread.start();

            } catch (Exception ex) {
                // Manejar error de clipboard
                System.err.println("Error al copiar al portapapeles: " + ex.getMessage());
                ex.printStackTrace();

                copyButton.setText("Error");

                // Restaurar después de 2 segundos
                Thread errorResetThread = new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        javafx.application.Platform.runLater(() -> {
                            if (stage.isShowing()) {
                                copyButton.setText("Copiar Todo");
                            }
                        });
                    } catch (InterruptedException iex) {
                        Thread.currentThread().interrupt();
                    }
                });

                errorResetThread.setDaemon(true);
                errorResetThread.start();
            }
        });
        Button closeButton = new Button("Cerrar");
        closeButton.setOnAction(e -> stage.close());

        HBox buttonBox = new HBox(10, copyButton, closeButton);
        buttonBox.setStyle("-fx-padding: 10;");

        BorderPane root = new BorderPane();
        root.setCenter(textArea);
        root.setBottom(buttonBox);

        Scene scene = new Scene(root, 500, 300);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.show();
    }
}
