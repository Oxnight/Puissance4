package Puissance4;

import java.nio.file.*;
import java.util.*;

/** Measures search decisions against exact scores, including older compatible engine JARs. */
public final class Benchmark {
  public static void main(String[] args) throws Exception {
    String mode = args.length == 0 ? "time" : args[0];
    Path corpus = Path.of(args.length > 1 ? args[1] : "tests/data/exact-positions.csv");
    int repeats = args.length > 2 ? Integer.parseInt(args[2]) : 3;
    if (repeats < 1 || repeats > 20) throw new IllegalArgumentException("repeats: 1..20");
    List<String> rows =
        Files.readAllLines(corpus).stream()
            .filter(s -> !s.startsWith("#") && !s.isBlank())
            .toList();
    // Warm up bytecode without adding any measured reference case to a shared cache.
    for (int i = 0; i < 8; i++)
      MinimaxAlgorithm.search(new Board(), 'X', new SearchSettings(6, 0, 1));
    System.out.println(
        "run,sequence,chosen,optimal,outcome_preserved,depth,nodes,milliseconds,cache_bytes,source");
    for (int run = 1; run <= repeats; run++)
      for (String row : rows) {
        String[] parts = row.split(",");
        String sequence = parts[0];
        Board board = new Board();
        char player = 'X';
        for (char c : sequence.toCharArray()) {
          board.play(c - '1', player);
          player = Constants.opponent(player);
        }
        int[] truth = Arrays.stream(parts).skip(1).mapToInt(Integer::parseInt).toArray();
        int best = Arrays.stream(truth).max().orElseThrow();
        int depth = mode.equals("fixed") ? 10 : 42;
        long budget = mode.equals("fixed") ? 0 : 100;
        SearchSettings settings = new SearchSettings(depth, budget, 32);
        if (mode.equals("book")) {
          try {
            settings =
                SearchSettings.class
                    .getConstructor(int.class, long.class, int.class, boolean.class)
                    .newInstance(depth, budget, 32, true);
          } catch (NoSuchMethodException historical) {
          }
        }
        long start = System.nanoTime();
        var result = MinimaxAlgorithm.search(board, player, settings);
        long duration = System.nanoTime() - start;
        long bytes = 30L * 1024 * 1024;
        String source = "LEGACY";
        try {
          bytes = ((Number) result.getClass().getMethod("cacheBytes").invoke(result)).longValue();
          source = result.getClass().getMethod("source").invoke(result).toString();
        } catch (ReflectiveOperationException historical) {
        }
        int selected = truth[result.column()];
        System.out.printf(
            Locale.ROOT,
            "%d,%s,%d,%s,%s,%d,%d,%.3f,%d,%s%n",
            run,
            sequence,
            result.column() + 1,
            selected == best,
            Integer.signum(selected) == Integer.signum(best),
            result.completedDepth(),
            result.nodes(),
            duration / 1e6,
            bytes,
            source);
      }
  }
}
