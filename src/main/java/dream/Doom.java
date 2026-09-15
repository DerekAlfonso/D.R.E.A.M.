package dream;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * doom.exe
 *
 * <p>A deliberately terrible port of Doom, hidden behind the boot menu.
 *
 * <p>The renderer underneath is an honest raycaster: one ray per screen column,
 * fish-eye corrected, with distance shading and a sprite pass. Everything around
 * it is the joke. The terminal only accepts a line of text followed by Enter, so
 * this is Doom with turn-based movement. You type W, you press Enter, you take
 * one step. The imp ignores walls entirely. The sprite is a rectangle.
 *
 * <p>No id Software assets are used or needed. The map, the renderer and the
 * very generous hit detection are all original and all bad on purpose.
 */
final class Doom {

    /** Supplies a line of player input. Lets Doom reuse the game's quit handling. */
    interface Prompter {
        String ask() throws InterruptedException;
    }

    // Viewport. Sized to sit comfortably inside the terminal's text column.
    private static final int VIEW_W = 56;
    private static final int VIEW_H = 13;

    private static final double FOV = Math.toRadians(60);
    private static final double MAX_DEPTH = 16.0;
    private static final double STEP = 0.02;

    private static final double MOVE_DISTANCE = 0.6;
    private static final double TURN_ANGLE = Math.toRadians(22.5);

    /** How close a shot has to be to centre to count. Wildly forgiving. */
    private static final double AIM_TOLERANCE = Math.toRadians(20);
    private static final double SHOT_RANGE = 9.0;

    private static final int IMP_DAMAGE = 7;
    private static final double IMP_SPEED = 0.35;
    private static final double IMP_REACH = 1.3;

    private static final Color VIEW_COLOR = new Color(150, 200, 150);

    /** E1M1 it is not. */
    private static final String[] MAP = {
        "################",
        "#..............#",
        "#..####...##...#",
        "#..#..#...#....#",
        "#..#..#...#..#.#",
        "#..####...#..#.#",
        "#.........#..#.#",
        "#..#####..####.#",
        "#..#...........#",
        "#..#..######...#",
        "#.....#....#...#",
        "#####.#....#...#",
        "#.....#....#...#",
        "#..####....#...#",
        "#..............#",
        "################"
    };

    private final Random random = new Random();

    // Row 1 of the map is open all the way across, so facing east from here
    // opens on a long corridor rather than a wall two feet from your nose.
    private double px = 1.5;
    private double py = 1.5;
    private double dir = 0.0;

    private int health = 100;
    private int ammo = 50;
    private int kills = 0;
    private int turns = 0;

    private double impX;
    private double impY;

    private Doom() {
        placeImp();
    }

    // ------------------------------------------------------------------ entry

    /** Runs the whole bit. Returns when the player backs out or dies. */
    static void play(Prompter prompter) throws InterruptedException {
        intro();

        Doom doom = new Doom();
        Terminal.printFrame(doom.render(), 13, VIEW_COLOR);

        while (true) {
            String command = prompter.ask().trim().toLowerCase();

            if (command.equals("back") || command.equals("return")
                || command.equals("home")) {
                Terminal.print("you have chosen to stop dooming.", 20, 15, Color.WHITE);
                Terminal.blankLine();
                return;
            }

            String message = doom.step(command);
            Terminal.printFrame(doom.render(), 13, VIEW_COLOR);

            if (message != null) {
                Terminal.print(message, 15, 15, Color.WHITE);
            }

            if (doom.health <= 0) {
                Terminal.print("you died. the imp walked through a wall to do it.",
                    20, 15, Color.RED);
                Terminal.print("final score: " + doom.kills
                    + (doom.kills == 1 ? " imp" : " imps"), 20, 15, Color.RED);
                Terminal.blankLine();
                return;
            }
        }
    }

    private static void intro() throws InterruptedException {
        Terminal.blankLine();
        Terminal.print("DOOM.EXE", 30, 20, Color.RED);
        Thread.sleep(700);
        Terminal.print("loading DOOM.WAD...", 20, 15, Color.WHITE);
        Thread.sleep(1200);
        Terminal.print("DOOM.WAD not found.", 20, 15, Color.RED);
        Thread.sleep(900);
        Terminal.print("improvising.", 20, 15, Color.WHITE);
        Thread.sleep(1100);
        Terminal.print("note: movement is turn based. we are aware of this.",
            15, 15, Color.WHITE);
        Thread.sleep(800);
        Terminal.print("W forward, S back, A turn left, D turn right, F fire.",
            15, 15, Color.WHITE);
        Terminal.print("type BACK to return to the main menu.", 15, 15, Color.RED);
        Thread.sleep(600);
    }

    // ------------------------------------------------------------------- turn

    /** Applies one command and lets the imp have its go. Returns a status line. */
    private String step(String command) {
        turns++;
        String message;

        switch (command) {
            case "w":
            case "forward":
                message = move(MOVE_DISTANCE);
                break;
            case "s":
            case "back up":
                message = move(-MOVE_DISTANCE);
                break;
            case "a":
            case "left":
                dir = normalize(dir - TURN_ANGLE);
                message = null;
                break;
            case "d":
            case "right":
                dir = normalize(dir + TURN_ANGLE);
                message = null;
                break;
            case "f":
            case "fire":
            case "shoot":
                message = fire();
                break;
            case "idkfa":
            case "iddqd":
                ammo = 999;
                health = 100;
                message = "cheats work. that was the easiest part to implement.";
                break;
            default:
                return "'" + command + "' is not a verb here. try W A S D or F.";
        }

        String impMessage = advanceImp();
        if (impMessage != null) {
            message = message == null ? impMessage : message + " " + impMessage;
        }

        String quip = quip();
        if (quip != null) {
            message = message == null ? quip : message + " " + quip;
        }
        return message;
    }

    private String move(double distance) {
        double nx = px + Math.cos(dir) * distance;
        double ny = py + Math.sin(dir) * distance;

        if (isWall(nx, ny)) {
            return "you walked into a wall. classic.";
        }
        px = nx;
        py = ny;
        return null;
    }

    private String fire() {
        if (ammo <= 0) {
            return "out of ammo. melee was not implemented.";
        }
        ammo--;
        AudioManager.playRandomSfx(AudioManager.SCROLL_SOUNDS);

        double dx = impX - px;
        double dy = impY - py;
        double distance = Math.hypot(dx, dy);
        double offAxis = Math.abs(normalize(Math.atan2(dy, dx) - dir));

        if (offAxis < AIM_TOLERANCE && distance < SHOT_RANGE) {
            // Bullets ignore geometry, same as the imp does. Only fair.
            boolean throughWall = wallBetween(impX, impY);
            kills++;
            placeImp();
            return throughWall
                ? "the shot went through a wall. the imp died anyway."
                : "the imp is dead. hit detection was extremely generous.";
        }
        if (distance >= SHOT_RANGE) {
            return "you shot a wall. the wall is fine.";
        }
        return "missed. the imp was right there.";
    }

    /** True if solid map geometry sits between the player and a point. */
    private boolean wallBetween(double targetX, double targetY) {
        double dx = targetX - px;
        double dy = targetY - py;
        double distance = Math.hypot(dx, dy);

        for (double travelled = 0; travelled < distance; travelled += STEP) {
            double t = travelled / distance;
            if (isWall(px + dx * t, py + dy * t)) {
                return true;
            }
        }
        return false;
    }

    /** The imp beelines at the player, straight through solid geometry. */
    private String advanceImp() {
        double dx = px - impX;
        double dy = py - impY;
        double distance = Math.hypot(dx, dy);

        if (distance < IMP_REACH) {
            health = Math.max(0, health - IMP_DAMAGE);
            return "the imp hit you for " + IMP_DAMAGE + ".";
        }

        if (distance > 0.001) {
            impX += dx / distance * IMP_SPEED;
            impY += dy / distance * IMP_SPEED;
        }
        return null;
    }

    /** Occasional commentary, because the game cannot help itself. */
    private String quip() {
        if (turns % 7 != 0) {
            return null;
        }
        String[] lines = {
            "(still turn based.)",
            "(the imp has no collision detection. this is intentional.)",
            "(that sprite is a rectangle. we ran out of budget.)",
            "(running at roughly one frame per Enter key.)",
            "(the soundtrack is a startup whirr. licensing is hard.)",
            "(no textures were harmed in the making of this level.)"
        };
        return lines[random.nextInt(lines.length)];
    }

    // --------------------------------------------------------------- renderer

    /** Builds the bordered viewport and HUD as lines of text. */
    private String[] render() {
        char[][] buffer = new char[VIEW_H][VIEW_W];
        double[] wallDistance = new double[VIEW_W];

        // One ray per column. This is the part that genuinely works.
        for (int col = 0; col < VIEW_W; col++) {
            double rayAngle = dir - FOV / 2 + FOV * col / (VIEW_W - 1.0);
            double raw = castRay(rayAngle);

            // Without this correction, flat walls bow outwards at the edges.
            double distance = raw * Math.cos(rayAngle - dir);
            wallDistance[col] = distance;

            int wallHeight = distance < 0.25
                ? VIEW_H
                : (int) Math.round(VIEW_H / distance);
            wallHeight = Math.min(wallHeight, VIEW_H);

            int top = (VIEW_H - wallHeight) / 2;
            int bottom = top + wallHeight - 1;
            char shade = shadeFor(distance);

            for (int row = 0; row < VIEW_H; row++) {
                if (row < top) {
                    buffer[row][col] = ' ';
                } else if (row <= bottom) {
                    buffer[row][col] = shade;
                } else {
                    buffer[row][col] = row > VIEW_H * 0.8 ? ',' : '.';
                }
            }
        }

        drawImp(buffer, wallDistance);
        return frameWithHud(buffer);
    }

    /** Marches along a ray until it meets a wall. Small steps, no cleverness. */
    private double castRay(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        for (double distance = 0; distance < MAX_DEPTH; distance += STEP) {
            if (isWall(px + cos * distance, py + sin * distance)) {
                return distance;
            }
        }
        return MAX_DEPTH;
    }

    private static char shadeFor(double distance) {
        if (distance < 2.5) {
            return '#';
        }
        if (distance < 4.5) {
            return '=';
        }
        if (distance < 7.0) {
            return '-';
        }
        return '.';
    }

    /**
     * Stamps the imp over the walls. It is a solid rectangle, scaled by
     * distance, hidden when a wall in that column is nearer.
     */
    private void drawImp(char[][] buffer, double[] wallDistance) {
        double dx = impX - px;
        double dy = impY - py;
        double distance = Math.hypot(dx, dy);
        double relative = normalize(Math.atan2(dy, dx) - dir);

        if (Math.abs(relative) > FOV / 2 || distance >= MAX_DEPTH) {
            return;
        }

        int centreCol = (int) Math.round((relative + FOV / 2) / FOV * (VIEW_W - 1));
        int height = Math.min(VIEW_H, (int) Math.round(VIEW_H / Math.max(0.6, distance)));
        int width = Math.max(2, height / 2);
        int top = (VIEW_H - height) / 2;

        for (int col = centreCol - width / 2; col <= centreCol + width / 2; col++) {
            if (col < 0 || col >= VIEW_W || distance > wallDistance[col]) {
                continue;
            }
            for (int row = top; row < top + height; row++) {
                if (row >= 0 && row < VIEW_H) {
                    buffer[row][col] = '@';
                }
            }
        }
    }

    private String[] frameWithHud(char[][] buffer) {
        List<String> lines = new ArrayList<>();
        String border = "+" + repeat('-', VIEW_W) + "+";

        lines.add(border);
        for (char[] row : buffer) {
            lines.add("|" + new String(row) + "|");
        }
        lines.add(border);

        String hud = String.format(" HEALTH %3d%%   AMMO %3d   KILLS %2d   E1M1 (unlicensed)",
            health, ammo, kills);
        lines.add("|" + pad(hud) + "|");
        lines.add(border);

        return lines.toArray(new String[0]);
    }

    private static String pad(String text) {
        if (text.length() >= VIEW_W) {
            return text.substring(0, VIEW_W);
        }
        return text + repeat(' ', VIEW_W - text.length());
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    // ------------------------------------------------------------------ world

    private static boolean isWall(double x, double y) {
        int mx = (int) Math.floor(x);
        int my = (int) Math.floor(y);

        if (my < 0 || my >= MAP.length || mx < 0 || mx >= MAP[my].length()) {
            return true;
        }
        return MAP[my].charAt(mx) == '#';
    }

    /** Drops the imp on a random open tile a sensible distance away. */
    private void placeImp() {
        List<int[]> open = new ArrayList<>();
        for (int y = 0; y < MAP.length; y++) {
            for (int x = 0; x < MAP[y].length(); x++) {
                if (MAP[y].charAt(x) == '.' && Math.hypot(x + 0.5 - px, y + 0.5 - py) > 4) {
                    open.add(new int[] {x, y});
                }
            }
        }

        if (open.isEmpty()) {
            impX = px + 3;
            impY = py;
            return;
        }

        int[] tile = open.get(random.nextInt(open.size()));
        impX = tile[0] + 0.5;
        impY = tile[1] + 0.5;
    }

    /** Wraps an angle to the range -PI to PI. */
    private static double normalize(double angle) {
        while (angle > Math.PI) {
            angle -= 2 * Math.PI;
        }
        while (angle < -Math.PI) {
            angle += 2 * Math.PI;
        }
        return angle;
    }
}
