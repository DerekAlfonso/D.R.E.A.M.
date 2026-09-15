# D.R.E.A.M

**Digital Recreation of Emotional Aptitude Model**

A retro CRT terminal narrative game. Originally written for CodeHS, now a
standalone desktop application.

Developed and written by Aedan Alfonso (Aesfo).

## Running it

Double-click `run.bat` (Windows) or run `./run.sh` (macOS / Linux).

Or build and launch by hand:

```bash
mvn package
java -jar target/dream.jar
```

Requires a JDK 11 or newer. Add `--debug` for verbose logging.

The build produces a single self-contained `target/dream.jar` (~2 MB). Every
asset (audio, font, splash text) and the MP3 decoder are embedded inside it, so
it runs offline and can be copied anywhere on its own.

## Controls

| Input | Action |
| --- | --- |
| Type + `Enter` | Answer a prompt |
| `Backspace` | Delete a character |
| Mouse wheel | Scroll back through the log |

At the boot menu, type `ProjectDream.iso`, `settings.bin` or `credits.txt`.
`exit` quits. On the credits and settings screens, `back` returns to the menu.

## Settings

`settings.bin` on the boot menu edits these live, and they persist to
`settings.txt` next to the jar:

| Setting | Range | Meaning |
| --- | --- | --- |
| `sfxVolume` | 0.0 - 1.0 | Keyboard and scroll click volume |
| `musicVolume` | 0.0 - 1.0 | Startup whirr volume |
| `textSpeed` | 0.1 - 5.0 | Typing speed, higher is faster |
| `textScale` | 0.5 - 3.0 | Text size, on top of automatic screen scaling |
| `typingSounds` | true / false | Click as dialogue types itself out |

Change a value with `set <name> <value>`, for example `set textSpeed 2.0` or
`set textScale 1.4`. Changes apply immediately, including to text already on
screen.

Text already scales itself to the screen height, so `textScale` is a nudge on
top of that rather than the whole story. The terminal keeps dialogue in a
centred column about 85 characters wide so lines stay readable on an ultrawide
display.

Your name and the name you give the AI are saved to `saveState.txt`.

## Project layout

```
pom.xml                          Maven build, produces target/dream.jar
run.bat / run.sh                 Launchers
src/main/java/dream/
  Main.java                      Entry point, window setup, shutdown
  Game.java                      The story, on its own thread
  Terminal.java                  Typing animation, word wrap, input, scrolling
  CurveCanvas.java               CRT rendering with barrel distortion
  AudioManager.java              MP3 decoding and playback
  Assets.java                    Loads embedded files
  Settings.java / SaveState.java Persistence
  InputController.java           Keyboard and mouse wheel
  Layer.java / TextData.java / CurveData.java
src/main/resources/assets/
  audio/*.mp3                    10 embedded sound files
  pixelFont.ttf                  Perfect DOS VGA 437
  splash.txt                     Random boot splash lines
```

## How the audio works

`javax.sound.sampled` cannot decode MP3 on its own. The build adds
[mp3spi](https://mvnrepository.com/artifact/com.googlecode.soundlibs/mp3spi),
which registers a service provider so `AudioSystem` reads `.mp3` the same way it
reads `.wav`. The shade plugin merges the `META-INF/services` entries into the
final jar; without that merge the packaged build would go silent.

Short effects are decoded once into memory and replayed from a pool of four
clips each, so overlapping keystrokes do not cut each other off. Leading and
trailing silence is trimmed on load, because the source files are padded with up
to 1.3 seconds of it. Music streams on a background thread.

If no sound device is available, the game logs one message and runs silently.

## Credits

Font: [Perfect DOS VGA 437](https://www.dafont.com/perfect-dos-vga-437.font) by
Zeh Fernando, free for any use.
