# Puissance 4

A local Connect Four game written in Java Swing. Play against another person on the same computer or against a configurable Minimax engine. The interface is in French and runs entirely offline.

## Run

Install Java 17 or newer with the `java.desktop` module. A headless Java runtime cannot display the game. No Java runtime is bundled.

The complete archive includes `dist/Puissance4.jar`.

| Platform | Launch |
| --- | --- |
| macOS | Double-click `RUN.command` |
| Windows | Double-click `run.bat` |
| Linux | Run `./run.sh` |
| Any desktop platform | Run `java -jar dist/Puissance4.jar` |

If macOS extraction does not preserve executable permissions:

```bash
cd /path/to/Puissance4
chmod +x RUN.command build.sh run.sh test.sh benchmark.sh
./RUN.command
```

Launch scripts use `JAVA_HOME` when it points to the required Java tools, otherwise `PATH`. If the JAR is absent, they build it; this requires a JDK.

## Play

Choose the mode, your color and the first player. Drop discs into the seven-column, six-row board. The first player to align four discs horizontally, vertically or diagonally wins. Full columns reject further discs; a full board without a winner is a draw.

Each player has 21 discs. Session scores remain between rounds, and the starting color alternates. Undo restores one move in two-player mode or the previous human decision in solo mode. Scores reset when starting a new session from settings.

The [Hasbro A5640 instructions](https://instructions.hasbro.com/en-gb/instruction/the-classic-game-of-connect-4) specify that the youngest player starts. Select that player's color in `Premier joueur`. Random selection, alternating starters and undo are application conveniences.

| Action | Control |
| --- | --- |
| Drop a disc | Click a column, or press `1` to `7` |
| Select a column | Left/right arrows, then `Enter` |
| Undo | `U` |
| New round | `R` |
| Return to settings | `Escape` |

`Réglages avancés` exposes depth, time, cache and opening knowledge. `Analyse de l’IA` expands search statistics. During a search, `Jouer maintenant` uses the last completed result.

| Preset | Maximum depth | Time per move | Cache limit | Exact openings |
| --- | ---: | ---: | ---: | --- |
| Découverte | 2 | 0.3 s | 16 MiB | No |
| Facile | 4 | 0.5 s | 16 MiB | No |
| Équilibré | 10 | 2 s | 32 MiB | No |
| Avancé | 14 | 4 s | 64 MiB | No |
| Expert | 20 | 8 s | 64 MiB | Yes |
| Maximum | 42 | 15 s | 128 MiB | Yes |

Depth counts individual moves and is a ceiling; the time limit can stop the search earlier. Exact opening knowledge is independent of depth. Maximum does not guarantee perfect play in every position within its time budget. See [the engine documentation](docs/ENGINE.md) for search behavior, memory limits and benchmarks.

## Appearance

The Atelier interface has light and dark palettes. On macOS it reads the system appearance and reduced-motion setting at launch. The `Animations` checkbox controls motion unless reduced motion is enforced.

```bash
java -Dpuissance4.appearance=dark -jar dist/Puissance4.jar
java -Dpuissance4.appearance=light -jar dist/Puissance4.jar
java -Dpuissance4.reducedMotion=true -jar dist/Puissance4.jar
```

Fonts use local fallbacks, so text rendering depends on installed fonts and the platform.

## Development

Use a JDK 17 or newer. There are no external Java dependencies or package-manager requirements. Sources target Java 17.

```bash
./build.sh
./test.sh
./test.sh --gui
```

On Windows, use `build.bat`, `test.bat` and `test.bat --gui`. GUI tests need a desktop. On Linux without a display, use `xvfb-run -a ./test.sh --gui` with Xvfb installed.

Compilation uses `javac -Xlint:all`. Tests cover rules, bitboards, reference search results, opening coverage, cache reuse, cancellation, a 32 MiB heap, Swing interactions, dialogs and layout. CI runs engine checks on Linux, macOS and Windows, with GUI checks on Linux. A Linux result does not validate native macOS or Windows behavior.

Sources are under `src/`, resources under `resources/`, tests under `tests/`, and the benchmark harness under `benchmarks/`. Generated output belongs in `build/` and `dist/`; both are ignored by Git. The version is defined in `resources/MANIFEST.MF`. To use IntelliJ IDEA, open this directory, select a JDK, mark `src` as the source root and run `Puissance4.Main`.

The game opens no network connections, has no telemetry and does not save session data to disk. The optional reference-generation tool invokes a separately installed solver; it is not part of normal play, compilation or testing.

Puissance 4 and Connect 4 are Hasbro trademarks. This independent project does not bundle Hasbro logos, artwork or instruction files. The board and discs are drawn in Java. No project license file is included in this archive; the reference-data provenance is documented in [ENGINE.md](docs/ENGINE.md).
