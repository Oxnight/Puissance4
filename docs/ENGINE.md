# Search engine

`Board` represents the 7 by 6 board and gravity. `Game` owns turns, terminal state and move history without depending on Swing. `GameWindow` searches a board snapshot in a `SwingWorker` and applies a result only while its task token remains current.

## Search and exact results

`MinimaxAlgorithm` uses Negamax, alpha-beta pruning, principal variation search, aspiration windows and iterative deepening. Move ordering combines cached moves, threats, killer moves, history and center preference. The last completed iteration supplies the move when time runs out or the user stops the search.

Two bitboards encode six playable cells and a sentinel per column. Reflected positions share a canonical cache key. Full keys are checked before using entries. Evaluation considers the 69 four-cell lines, center occupation, reachable threats and stacked threats. Terminal scores favor faster wins and slower losses; ties are deterministic.

Immediate wins, forced blocks, double threats and moves allowing an immediate opposing win are handled tactically. At the horizon, forced replies and immediate forks can extend analysis by up to four plies. These checks can go beyond the configured ordinary depth. No probabilistic pruning, random move selection or background pondering is used.

If the maximum depth covers all remaining cells, an exact terminal-only solver may run after depth eight once at least 16 moves have been played. With a deadline, it receives half the remaining time when more than 12 ms remain. If interrupted, ordinary iterative deepening resumes with its earlier result. Exact-solver cache entries are kept distinct from heuristic entries. A tactical proof beyond the nominal horizon is not reused as a depth-independent exact distance.

## Opening data

`resources/Puissance4/openings.bin` contains exact scores for all legal nonterminal positions through eight moves, grouped by reflection: 129,498 positions in 1,942,486 bytes. Each record stores the canonical key and seven signed column scores. Full columns use `-128`.

The book loads lazily on the search thread. Its header records format, depth, count and CRC32; the loader also checks length, key ordering and score ranges. Missing or damaged optional data falls back to ordinary search. No external solver or network access is needed at runtime.

Expert and Maximum enable opening knowledge by default. When enabled, an opening decision is optimal within the book's domain regardless of the depth setting. Outside that domain, a time-limited search remains heuristic unless it finishes a proof or complete resolution.

Positions were enumerated by this project's generator. Exact mathematical scores were queried from the separately installed [Pascal Pons Connect4 solver](https://github.com/PascalPons/connect4). The solver's code and upstream opening book are not bundled. The generator does not record an upstream solver revision; reproducing scores requires supplying a compatible solver and its book.

`tests/data/exact-positions.csv` contains 72 deterministic legal positions with seven exact scores each: 12 early positions, 36 middle-game positions and 24 late positions. It contains no user games or personal data.

To regenerate both datasets, install the external solver separately and run:

```bash
python3 tools/generate_reference.py /path/to/c4solver --book /path/to/7x6.book
```

This overwrites the bundled opening data and reference corpus. `--skip-opening` regenerates only the corpus. Python and the solver are maintenance tools, not Java build dependencies. Preserve the solver's licensing requirements when obtaining or redistributing it; this archive makes no claim to license upstream software.

## Resource limits and lifecycle

A `MinimaxAlgorithm.Session` retains cache entries and history within a round, including after undo. Concurrent searches on a session serialize with an interruptible lock. Waiting is included in each search's deadline. Closing the session does not block the Swing event thread.

The transposition table uses two entries per bucket and allocates blocks on demand. Keys and packed metadata use 12 payload bytes per entry. Growth redistributes entries in place without allocating a second full table. Shallow searches at depths one and two, immediate tactical decisions and opening decisions do not allocate a new table.

The effective cache budget is limited by both the chosen setting and roughly one eighth of Java's maximum heap, with a minimum of 1 MiB. Capacity rounds down to a power of two with allocation headroom. The displayed estimate covers cache arrays, not the whole process. The loaded opening book, Swing and the JVM consume additional memory.

Search uses one worker thread and reuses move-ordering arrays rather than copying a board at each node. Cancellation and deadlines are checked at the root and every 256 nodes. Allocation, cache growth and JVM pauses can cause a small overrun.

`Jouer maintenant` requests a stop without discarding the last completed iteration. If no iteration completed, a legal tactical fallback is available. Undo, a new round, return to settings and window close invalidate pending results. A new round also closes the old session. Searches do not continue between turns.

## Validation and measurements

`EngineTests` checks rules, history, independent victory detection, generated boards, search equivalence and cancellation. `AdvancedTests` checks exhaustive book coverage, sampled book decisions, persistent-cache horizons, concurrent cancellation and exact results. `LimitedHeapTests` runs the engine and book with a 32 MiB heap while requesting a 256 MiB cache. Swing tests exercise the actual window, both modes, scoring, dialogs, keyboard input, animation and worker recovery.

Run benchmarks against the bundled exact corpus:

```bash
./build.sh
./benchmark.sh fixed 3 > build/fixed.csv
./benchmark.sh time 3 > build/time.csv
./benchmark.sh book 3 > build/book.csv
```

Build first so `build/` exists before the shell opens the output file. On Windows use `benchmark.bat`. The second argument selects one to 20 repetitions, defaulting to three. An optional third argument selects another compatible engine JAR instead of rebuilding the current one.

`fixed` uses depth 10 without a deadline. `time` uses depth 42 and 100 ms; `book` adds opening knowledge to that configuration. The 100 ms benchmark budget is below the GUI's 250 ms minimum. Each measured search has a fresh session and a 32 MiB cache limit. Eight warm-up searches precede measurements, and the Java heap is set to `-Xms64m -Xmx512m`.

CSV output distinguishes exact optimal moves from moves that preserve only the best achievable outcome. It includes completed depth, nodes, elapsed time, cache size and decision source. Fresh-session measurements do not include the benefit of retaining a cache between turns. Timing depends on the machine, JVM and deadline boundaries; this corpus is neither an Elo rating nor an exhaustive proof of playing strength.
