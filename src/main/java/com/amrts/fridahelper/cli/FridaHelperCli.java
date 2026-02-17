package com.amrts.fridahelper.cli;

import com.amrts.fridahelper.core.FridaHelperVersion;

import java.util.Scanner;

/**
 * Entry point for the FridaHelper CLI application.
 * Thin shell that delegates all work to {@link CliMenuHandler}.
 *
 * Backward-compatible menu structure:
 *   1. Java Hook    (original flow)
 *   2. Native Hook  (new)
 *   3. About
 *   4. Exit
 */
public final class FridaHelperCli {

    private static final String BANNER =
            FridaHelperVersion.FULL + "\n"
            + "Welcome to FridaHelper - Generate Frida hook scripts easily!\n"
            + "Options:\n"
            + "1. Java Hook (from smali signature)\n"
            + "2. Native Hook (lib + symbol)\n"
            + "3. About\n"
            + "4. Exit\n";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        CliMenuHandler handler = new CliMenuHandler(scanner);

        boolean running = true;
        while (running) {
            System.out.println(BANNER);
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            switch (input) {
                case "1":
                    handler.handleJavaHook();
                    break;
                case "2":
                    handler.handleNativeHook();
                    break;
                case "3":
                    handler.showAbout();
                    break;
                case "4":
                    System.out.println("\nExiting...");
                    running = false;
                    break;
                default:
                    System.out.println("Invalid option. Please enter 1-4.");
                    break;
            }
        }

        scanner.close();
    }
}
