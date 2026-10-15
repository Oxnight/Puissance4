package Puissance4;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Book coverage, persistent-cache correctness and comparison to external exact facts. */
public final class AdvancedTests {
  private static int checks;

  private record Position(long own, long mask, int count) {}

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    openingCoverage();
    persistentSearch();
    exactEndgames();
    exactMiddleGames();
    cancellationAndClose();
    System.out.println(
        "OK - "
            + checks
            + " assertions (exact openings, cache reuse, endgames, concurrent cancellation)");
  }

  private static long key(long own, long mask) {
    return Math.min(own + mask, MinimaxAlgorithm.mirror(own) + MinimaxAlgorithm.mirror(mask));
  }

  private static Board board(Position p) {
    Board b = new Board();
    char side = p.count() % 2 == 0 ? 'X' : 'O';
    for (int c = 0; c < 7; c++)
      for (int r = 0; r < 6; r++) {
        long bit = 1L << (c * 7 + r);
        if ((p.mask() & bit) != 0)
          b.play(c, (p.own() & bit) != 0 ? side : Constants.opponent(side));
      }
    return b;
  }

  private static void openingCoverage() {
    OpeningBook book = OpeningBook.get();
    check(book.maximumPly() == 8 && book.positions() == 129498, "complete opening book loaded");
    check(book.bytes() == 1942470, "packed opening memory");
    Map<Long, Position> level = new HashMap<>();
    level.put(0L, new Position(0, 0, 0));
    int visited = 0, samples = 0;
    for (int ply = 0; ply <= 8; ply++) {
      Map<Long, Position> next = new HashMap<>();
      for (var entry : level.entrySet()) {
        Position p = entry.getValue();
        int index = book.find(entry.getKey(), ply);
        check(index >= 0, "every legal early position has an entry");
        boolean flipped =
            MinimaxAlgorithm.mirror(p.own()) + MinimaxAlgorithm.mirror(p.mask())
                < p.own() + p.mask();
        int best = -128;
        for (int c = 0; c < 7; c++) {
          long move = MinimaxAlgorithm.columnMove(p.mask(), c);
          int score = book.scoreAt(index, flipped ? 6 - c : c);
          check(
              (score != OpeningBook.INVALID) == ((MinimaxAlgorithm.possible(p.mask()) & move) != 0),
              "book excludes exactly full columns");
          best = Math.max(best, score);
          if ((MinimaxAlgorithm.possible(p.mask()) & move) != 0
              && !Board.hasFour(p.own() | move)
              && ply < 8) {
            Position child = new Position(p.own() ^ p.mask(), p.mask() | move, ply + 1);
            next.putIfAbsent(key(child.own(), child.mask()), child);
          }
        }
        if (visited++ % 67 == 0) {
          Board b = board(p);
          char player = ply % 2 == 0 ? 'X' : 'O';
          var actual = MinimaxAlgorithm.search(b, player, new SearchSettings(42, 100, 1, true));
          check(actual.proven(), "book/tactical result proven");
          check(
              book.scoreAt(index, flipped ? 6 - actual.column() : actual.column()) == best,
              "book chooses optimal column in both orientations");
          check(
              actual.score() == OpeningBook.toMateScore(best, ply), "book exact score conversion");
          check(actual.cacheBytes() == 0, "early solved positions allocate no search cache");
          samples++;
        }
      }
      level = next;
    }
    check(visited == book.positions(), "book contains only reachable nonterminal early positions");
    var empty = MinimaxAlgorithm.search(new Board(), 'X', new SearchSettings(42, 100, 32, true));
    check(empty.column() == 3 && empty.score() == 99959, "exact central first move");
    System.out.println(
        "Opening coverage: " + visited + " positions, " + samples + " search samples");
  }

  private static void persistentSearch() {
    try (var session = new MinimaxAlgorithm.Session()) {
      Board b = new Board();
      SearchSettings settings = new SearchSettings(8, 0, 32);
      var cold = session.search(b, 'X', settings);
      var warm = session.search(b, 'X', settings);
      check(
          cold.score() == warm.score() && cold.column() == warm.column(),
          "warm cache preserves decision");
      check(
          warm.nodes() < cold.nodes() && warm.cacheHits() > 0,
          "repeat search reuses completed entries");
      check(cold.cacheBytes() < 2L * 1024 * 1024, "depth eight cache stays modest");
      for (int depth : new int[] {4, 9, 6, 3, 8}) {
        var reused = session.search(b, 'X', new SearchSettings(depth, 0, 32));
        var independent = MinimaxAlgorithm.search(b, 'X', new SearchSettings(depth, 0, 32));
        check(
            reused.score() == independent.score(), "different horizons do not reuse wrong scores");
      }
      b.play(3, 'X');
      b.play(2, 'O');
      var reused = session.search(b, 'X', settings);
      var independent = MinimaxAlgorithm.search(b, 'X', settings);
      check(reused.score() == independent.score(), "reuse between real turns preserves score");
      check(reused.cacheBytes() <= 32L * 1024 * 1024, "cache respects budget");
      System.out.println(
          "Persistent cache: "
              + cold.nodes()
              + " -> "
              + warm.nodes()
              + " nodes on repeated depth-eight search");
    }
    var shallow = MinimaxAlgorithm.search(new Board(), 'X', new SearchSettings(2, 0, 256));
    check(shallow.cacheBytes() == 0, "no large allocation at shallow level");
    var small = MinimaxAlgorithm.search(new Board(), 'X', new SearchSettings(4, 0, 256));
    check(small.cacheBytes() < 100000, "depth-four cache below 100 KB");
    Board win = new Board();
    for (int c = 0; c < 3; c++) win.play(c, 'X');
    check(
        MinimaxAlgorithm.search(win, 'X', new SearchSettings(42, 0, 256)).cacheBytes() == 0,
        "no cache for an immediate win");
    var limited = MinimaxAlgorithm.search(new Board(), 'X', new SearchSettings(12, 80, 256));
    check(
        limited.cacheBytes()
            <= Math.max(1, Runtime.getRuntime().maxMemory() / 8 / 1024 / 1024) * 1024L * 1024,
        "heap-aware cache ceiling");
  }

  private static void exactEndgames() throws Exception {
    int solved = 0;
    for (String row : Files.readAllLines(Path.of("tests/data/exact-positions.csv"))) {
      if (row.startsWith("#") || row.isBlank()) continue;
      String[] fields = row.split(",");
      String sequence = fields[0];
      if (sequence.length() < 26) continue;
      Board b = new Board();
      char p = 'X';
      for (char c : sequence.toCharArray()) {
        check(b.play(c - '1', p) >= 0, "reference sequence legal");
        p = Constants.opponent(p);
      }
      int[] scores = Arrays.stream(fields).skip(1).mapToInt(Integer::parseInt).toArray();
      int best = Arrays.stream(scores).max().orElseThrow();
      var actual = MinimaxAlgorithm.search(b, p, new SearchSettings(42, 0, 4));
      check(actual.proven(), "late game fully solved");
      check(
          Integer.signum(scores[actual.column()]) == Integer.signum(best),
          "exact endgame outcome and selected move agree with external solver");
      check(
          Integer.signum(actual.score()) == Integer.signum(best),
          "reported proven outcome agrees with external solver");
      check(scores[actual.column()] == best, "late-game move has optimal exact distance");
      check(
          actual.score() == OpeningBook.toMateScore(best, b.getMoveCount()),
          "late-game distance matches exact reference");
      solved++;
    }
    check(solved == 24, "all late reference positions checked");
  }

  private static void cancellationAndClose() throws Exception {
    var session = new MinimaxAlgorithm.Session();
    var pool = Executors.newSingleThreadExecutor();
    CountDownLatch started = new CountDownLatch(1);
    try {
      var running =
          pool.submit(
              () ->
                  session.search(
                      new Board(),
                      'X',
                      new SearchSettings(42, 0, 1),
                      () -> false,
                      r -> started.countDown()));
      check(started.await(2, TimeUnit.SECONDS), "long search started");
      long begin = System.nanoTime();
      var queued =
          session.search(new Board(), 'X', new SearchSettings(42, 0, 1), () -> true, r -> {});
      check(
          queued.interrupted() && (System.nanoTime() - begin) / 1e6 < 1000,
          "waiting search can be cancelled");
      begin = System.nanoTime();
      session.close();
      check((System.nanoTime() - begin) / 1e6 < 100, "session close does not block the interface");
      check(running.get(2, TimeUnit.SECONDS).interrupted(), "close stops active calculation");
      check(session.cacheBytes() == 0, "closed session releases search cache");
    } finally {
      session.close();
      pool.shutdownNow();
    }
  }

  private static void exactMiddleGames() throws Exception {
    int fullySolved = 0;
    try (var session = new MinimaxAlgorithm.Session()) {
      for (String row : Files.readAllLines(Path.of("tests/data/exact-positions.csv"))) {
        if (row.startsWith("#") || row.isBlank()) continue;
        String[] fields = row.split(",");
        String sequence = fields[0];
        if (sequence.length() < 16 || sequence.length() > 24) continue;
        Board b = new Board();
        char p = 'X';
        for (char c : sequence.toCharArray()) {
          b.play(c - '1', p);
          p = Constants.opponent(p);
        }
        int[] scores = Arrays.stream(fields).skip(1).mapToInt(Integer::parseInt).toArray();
        int best = Arrays.stream(scores).max().orElseThrow();
        var result = session.search(b, p, new SearchSettings(42, 1000, 16));
        if (result.source() == MinimaxAlgorithm.Source.EXACT_SEARCH) {
          check(result.proven(), "terminal-only solve proves result");
          check(
              scores[result.column()] == best,
              "terminal-only root selects exact fastest win / longest loss");
          check(
              result.score() == OpeningBook.toMateScore(best, b.getMoveCount()),
              "terminal-only distance matches independent exact facts");
          fullySolved++;
        }
        var reused = session.search(b, p, new SearchSettings(5, 0, 16));
        var fresh = MinimaxAlgorithm.search(b, p, new SearchSettings(5, 0, 16));
        check(
            reused.score() == fresh.score(),
            "terminal-only bounds do not contaminate depth-limited scores");
      }
    }
    check(fullySolved > 0, "terminal-only solver exercised by reference corpus");
    System.out.println(
        "Terminal-only solver: " + fullySolved + " exact middle-game results verified");
  }
}
