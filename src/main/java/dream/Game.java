package dream;

import java.awt.Color;
import java.util.List;
import java.util.Random;

/**
 * The story itself, run on its own thread so it can sleep between beats without
 * freezing the window.
 *
 * <p>The dialogue is unchanged from the original. What changed is the plumbing
 * around it: string comparisons that used {@code ==} never matched, so name
 * validation and the credits screen's BACK command could not fire, and the boot
 * menu never redrew itself after an option finished.
 */
public class Game implements Runnable {

    private static final Color PURPLE = new Color(141, 20, 222);

    private final Random random = new Random();
    private volatile boolean running = true;

    @Override
    public void run() {
        try {
            boot();
            while (running) {
                showMenu();
                handleChoice(Terminal.prompt().trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.info("Story thread interrupted; shutting down.");
        } catch (Exception e) {
            Log.error("The story thread hit an unexpected problem.", e);
        }
    }

    public void stop() {
        running = false;
    }

    // ------------------------------------------------------------------ boot

    private void boot() throws InterruptedException {
        List<String> splashes = Assets.loadSplashes();
        String chosenSplash = splashes.get(random.nextInt(splashes.size()));

        Terminal.print("INITIALIZING SYSTEM...", 20, 15, Color.GREEN);
        pause(2400);
        Terminal.print("- " + chosenSplash, 20, 15, Color.WHITE);
        Terminal.print("----------------------", 0, 15, Color.WHITE);
        pause(2000);
    }

    private void showMenu() {
        Terminal.print("Select a file to open: ", 20, 15, Color.WHITE);
        Terminal.print("[-] ProjectDream.iso", 20, 15, Color.WHITE);
        Terminal.print("[-] settings.bin", 20, 15, Color.WHITE);
        Terminal.print("[-] credits.txt", 20, 15, Color.WHITE);
    }

    private void handleChoice(String choice) throws InterruptedException {
        String normalized = choice.toLowerCase();

        if (normalized.equals("projectdream.iso")) {
            runProjectDream();
        } else if (normalized.equals("settings.bin")) {
            runSettings();
        } else if (normalized.equals("credits.txt")) {
            runCredits();
        } else if (normalized.equals("exit") || normalized.equals("quit")) {
            Terminal.print("Goodbye.", 40, 15, Color.GREEN);
            Terminal.awaitIdle();
            pause(800);
            Main.shutdown();
        } else {
            Terminal.print("please select a valid file name", 20, 15, Color.RED);
        }
    }

    // --------------------------------------------------------- ProjectDream

    private void runProjectDream() throws InterruptedException {
        Terminal.blankLine();
        Terminal.print("----------------------", 1, 15, Color.WHITE);
        Terminal.print("Hello friend :)", 20, 15, Color.WHITE);
        pause(2000);
        Terminal.print("Thank you for choosing to help participate in the testing for the. . .",
            20, 15, Color.WHITE);
        pause(2000);
        Terminal.print("Digital Recreation of Emotional Aptitude Model", 20, 17, Color.CYAN);
        pause(2000);
        Terminal.print("the Eris Digital company appreciates your help", 20, 15, Color.WHITE);
        pause(2000);
        Terminal.print("My job is to keep you company and entertained!", 20, 15, Color.WHITE);
        pause(2000);
        Terminal.print("Think of me as your personal digital friend :D", 20, 15, Color.WHITE);
        pause(2000);
        Terminal.print("lets start off with the simple stuff, whats your name?", 20, 15, Color.WHITE);
        pause(2000);

        SaveState.username = askForName(2);
        pause(1000);
        Terminal.print(SaveState.username + ", huh?", 20, 15, Color.WHITE);
        pause(2000);
        Terminal.print("I like that name!", 20, 15, Color.WHITE);
        pause(2000);
        Terminal.print("Now I need a name. . . ", 20, 15, Color.WHITE);
        pause(2000);
        Terminal.print("what should I be called?", 20, 15, Color.WHITE);
        pause(2000);

        SaveState.cpuName = askForName(1);
        pause(2000);
        Terminal.print(SaveState.cpuName + "?", 200, 15, Color.WHITE);
        pause(3000);
        Terminal.print(". . .", 500, 15, Color.WHITE);
        pause(3000);
        Terminal.print("thats nice.", 500, 15, Color.WHITE);
        pause(2000);
        Terminal.print("Okay now on to the fun part!", 20, 15, Color.WHITE);
        Terminal.awaitIdle();

        SaveState.save();
        pause(1500);
        Terminal.blankLine();
    }

    /**
     * Keeps asking until the answer is usable. The original compared strings
     * with {@code ==}, so every answer including an empty one was accepted.
     */
    private String askForName(int minimumLength) {
        while (true) {
            String input = Terminal.prompt().trim();

            if (input.isEmpty()
                || input.length() < minimumLength
                || input.equalsIgnoreCase("eris")) {
                Terminal.print("input a valid name", 20, 15, Color.RED);
            } else {
                return input;
            }
        }
    }

    // -------------------------------------------------------------- credits

    private void runCredits() throws InterruptedException {
        Terminal.print("----------------------", 1, 15, Color.WHITE);
        Terminal.print("Developed and Written by: ", 100, 20, Color.WHITE);
        Terminal.print("Aedan Alfonso (Aesfo)", 1, 35, PURPLE);
        Terminal.blankLine();
        Terminal.print("Special thanks to my parents for inspiring my love for creative "
            + "storytelling and computers", 100, 20, Color.WHITE);
        Terminal.print("to my friends and family, for always encouraging me :D",
            100, 20, Color.WHITE);
        Terminal.print("to my girlfriend, who believed in my ability to make something "
            + "before ever once seeing anything I had made, I love you btw <3",
            100, 20, Color.WHITE);
        Terminal.print("to you, for playing my silly little side project "
            + "(and hopefully liking it)", 100, 20, Color.WHITE);
        Terminal.print("type BACK to go to the main menu.", 50, 20, Color.RED);

        // Wait for BACK rather than a fixed 40 second sleep, and actually leave
        // when it is typed. The original did the opposite of both.
        while (running) {
            String input = Terminal.prompt().trim().toLowerCase();
            if (input.equals("exit") || input.equals("back")
                || input.equals("return") || input.equals("home")) {
                Terminal.blankLine();
                return;
            }
            Terminal.print("type BACK to go to the main menu.", 20, 15, Color.RED);
        }
    }

    // ------------------------------------------------------------- settings

    private void runSettings() throws InterruptedException {
        Terminal.print("----------------------", 1, 15, Color.WHITE);
        Terminal.print("settings.bin", 20, 17, Color.CYAN);

        while (running) {
            showSettingValues();
            Terminal.print("change a value with: set <name> <value>", 15, 15, Color.WHITE);
            Terminal.print("type BACK to go to the main menu.", 15, 15, Color.RED);

            String input = Terminal.prompt().trim();
            String normalized = input.toLowerCase();

            if (normalized.equals("back") || normalized.equals("exit")
                || normalized.equals("return") || normalized.equals("home")) {
                Settings.save();
                Terminal.print("settings saved.", 20, 15, Color.GREEN);
                Terminal.blankLine();
                return;
            }

            if (normalized.startsWith("set ")) {
                applySetting(input.substring(4).trim());
            } else {
                Terminal.print("unrecognised command", 20, 15, Color.RED);
            }
        }
    }

    private void showSettingValues() {
        Terminal.print("[-] sfxVolume     = " + format(Settings.sfxVolume)
            + "   (0.0 - 1.0)", 10, 15, Color.WHITE);
        Terminal.print("[-] musicVolume   = " + format(Settings.musicVolume)
            + "   (0.0 - 1.0)", 10, 15, Color.WHITE);
        Terminal.print("[-] textSpeed     = " + format(Settings.textSpeed)
            + "   (0.1 - 5.0, higher is faster)", 10, 15, Color.WHITE);
        Terminal.print("[-] typingSounds  = " + Settings.typingSounds
            + "   (true / false)", 10, 15, Color.WHITE);
    }

    private void applySetting(String assignment) {
        String[] parts = assignment.split("\\s+", 2);
        if (parts.length < 2) {
            Terminal.print("usage: set <name> <value>", 20, 15, Color.RED);
            return;
        }

        String key = parts[0].toLowerCase();
        String value = parts[1].trim();

        try {
            switch (key) {
                case "sfxvolume":
                    Settings.sfxVolume = clamp01(Float.parseFloat(value));
                    // Play a click so the new level is immediately audible.
                    Settings.apply();
                    AudioManager.playRandomSfx(AudioManager.KEYBOARD_CLICKS);
                    break;
                case "musicvolume":
                    Settings.musicVolume = clamp01(Float.parseFloat(value));
                    Settings.apply();
                    break;
                case "textspeed":
                    Settings.textSpeed = Double.parseDouble(value);
                    Settings.apply();
                    break;
                case "typingsounds":
                    Settings.typingSounds = Boolean.parseBoolean(value);
                    Settings.apply();
                    break;
                default:
                    Terminal.print("no setting called " + parts[0], 20, 15, Color.RED);
                    return;
            }
            Terminal.print("ok", 20, 15, Color.GREEN);
        } catch (NumberFormatException e) {
            Terminal.print("'" + value + "' is not a number", 20, 15, Color.RED);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    private void pause(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
