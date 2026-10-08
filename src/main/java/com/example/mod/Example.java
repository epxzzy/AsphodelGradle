// asphodel::merge -> version=1.20.1 -> com.example.mod.Example20
// asphodel::merge -> version=1.21.1 -> com.example.mod.Example21
package com.example.mod;

import java.util.logging.Logger;

public class Example {

    private static final int EXAMPLE = 26;

    public static final String MOD_ID = "example";
    public static final Logger LOGGER = Logger.getLogger("Example Mod");

    public static void sharedSetup() {
        // asphodel::patch -> loader=!neoforge, version=>=1.20.4, id=patch21

        // System.out.println("Hatsune Miku: I like leek! this greeting was patched!"); *patch21
        System.out.println("Kasane Teto: I like bread!"); // remove*patch21
    }

    public static void setup20() {} // stub
    public static void setup21() {} // stub
}