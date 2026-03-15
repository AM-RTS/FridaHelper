package com.amrts.fridahelper.cli;

import com.amrts.fridahelper.core.FridaHelperVersion;

import java.util.Scanner;

/**
 * Entry point for the FridaHelper CLI application.
 * Thin shell that delegates all work to {@link CliMenuHandler}.
 *
 * Menu structure:
 *   1. Java Hook    (single hook, original flow)
 *   2. Native Hook  (single hook)
 *   3. Multi-Hook Session (compose multiple hooks into one script)
 *   4. About
 *   5. Exit
 */
public final class FridaHelperCli {

    private static final String BANNER =
            FridaHelperVersion.FULL + "\n"
            + "Welcome to FridaHelper - Generate Frida hook scripts easily!\n"
            + "Options:\n"
            + "1. Java Hook (from smali signature)\n"
            + "2. Native Hook (lib + symbol)\n"
            + "3. Multi-Hook Session\n"
            + "4. About\n"
            + "5. Exit\n";

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
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
                        handler.handleMultiHookSession();
                        break;
                    case "4":
                        handler.showAbout();
                        break;
                    case "5":
                        System.out.println("\nExiting...");
                        running = false;
                        break;
                    default:
                        System.out.println("Invalid option. Please enter 1-5.");
                        break;
                }
            }
        }
    }
}
