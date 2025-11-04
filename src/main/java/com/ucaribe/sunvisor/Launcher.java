package com.ucaribe.sunvisor;

////////////////////////////////////////////////////////////////
// Clase "puente" no modular.                                 //
// jpackage ejecutará este main(). Este main() luego llamará  //
// al main() de la aplicación JavaFX real.                    //
////////////////////////////////////////////////////////////////
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
