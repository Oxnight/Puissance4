package Puissance4;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dependency-free deterministic regression/property tests. */
public final class EngineTests {
  private static int assertions;
  private static final Random RANDOM = new Random(0xC04F0L);

  public static void main(String[] args) {
    gravityAndHistory();
    winsAndDraw();
    tactics();
    bitboardProperties();
    searchAgainstReference();
    deadlines();
    System.out.println(
        "OK - "
            + assertions
            + " assertions (rules, bitboards, tactical search, alpha-beta, cancellation)");
  }

  private static void check(boolean value, String message) {
    assertions++;
    if (!value) throw new AssertionError(message);
  }

  private static void gravityAndHistory() {
    Board b = new Board();
    check(b.getAvailableColumns().size() == 7, "seven columns");
    for (int i = 0; i < 6; i++) check(b.play(2, i % 2 == 0 ? 'X' : 'O') == 5 - i, "gravity");
    check(!b.canPlay(2) && b.play(2, 'X') == -1, "full column rejected");
    check(!b.canPlay(-1) && !b.canPlay(7), "invalid columns rejected");
    Board copy = new Board(b);
    copy.undo(2);
    check(b.getMoveCount() == 6 && copy.getMoveCount() == 5, "snapshot isolation");
    Game g = new Game(Game.Mode.HUMAN_VS_AI, 'X', 'X');
    g.play(0);
    g.play(1);
    g.undoHumanTurn();
    check(g.getBoard().getMoveCount() == 0 && g.getCurrentPlayer() == 'X', "solo undo pair");
    g.play(0);
    g.undoHumanTurn();
    check(g.getBoard().getMoveCount() == 0, "undo while AI pending");
    g = new Game(Game.Mode.HUMAN_VS_AI, 'O', 'X');
    g.play(3);
    check(!g.canUndo(), "AI opening cannot create repeated opening undo");
    g.play(4);
    g.play(3);
    g.undoHumanTurn();
    check(g.getBoard().getMoveCount() == 1 && g.getCurrentPlayer() == 'O', "undo after AI opening");
    g = new Game(Game.Mode.HUMAN_VS_HUMAN, 'X', 'O');
    check(g.getCurrentPlayer() == 'O', "selected starter");
    g.play(0);
    g.undo();
    check(g.getCurrentPlayer() == 'O', "restore starter");
  }

  private static void winsAndDraw() {
    Board b = new Board();
    for (int c = 0; c < 4; c++) b.play(c, 'X');
    check(b.checkWin('X'), "horizontal win");
    check(b.winningCells('X').size() == 4, "highlight horizontal");
    b = new Board();
    for (int r = 0; r < 4; r++) b.play(1, 'O');
    check(b.checkWin('O'), "vertical win");
    for (boolean mirror : new boolean[] {false, true}) {
      b = new Board();
      for (int c = 0; c < 4; c++) {
        int col = mirror ? 6 - c : c;
        for (int r = 0; r < c; r++) b.play(col, 'O');
        b.play(col, 'X');
      }
      check(b.checkWin('X'), "both diagonals");
    }
    // Rows XXOOXXO / OOXXOOX repeated vertically have no four in any direction.
    b = new Board();
    for (int r = 0; r < 6; r++)
      for (int c = 0; c < 7; c++) b.play(c, ((c / 2 + r) % 2 == 0) ? 'X' : 'O');
    check(b.isFull() && !b.checkWin('X') && !b.checkWin('O'), "full-board draw");
    Game g = new Game(Game.Mode.HUMAN_VS_HUMAN, 'X', 'X');
    for (int c : new int[] {0, 1, 0, 1, 0, 1, 0}) g.play(c);
    check(g.getWinner() == 'X' && g.play(2) == null, "stop immediately after victory");
    g.undo();
    check(!g.isGameOver() && g.getCurrentPlayer() == 'X', "undo victory");
  }

  private static void tactics() {
    SearchSettings s = new SearchSettings(8, 0, 1);
    Board b = new Board();
    for (int c = 0; c < 3; c++) b.play(c, 'O');
    check(MinimaxAlgorithm.search(b, 'O', s).column() == 3, "take immediate horizontal win");
    b.play(3, 'O');
    var terminal = MinimaxAlgorithm.search(b, 'O', s);
    check(
        terminal.column() == -1 && terminal.proven() && terminal.score() == 99996,
        "terminal victory score");
    terminal = MinimaxAlgorithm.search(b, 'X', s, () -> true, r -> {});
    check(terminal.column() == -1 && terminal.score() == -99996, "cancelled terminal loss score");
    b = new Board();
    for (int i = 0; i < 3; i++) b.play(0, 'X');
    check(MinimaxAlgorithm.search(b, 'O', s).column() == 0, "forced vertical block");
    b = new Board();
    for (int c = 1; c <= 3; c++) b.play(c, 'X');
    var result = MinimaxAlgorithm.search(b, 'O', s);
    check(result.proven() && result.score() < -99000, "double threat proven loss");
    b = new Board();
    b.play(0, 'X');
    b.play(0, 'O');
    b.play(1, 'O');
    b.play(1, 'O');
    b.play(2, 'X');
    b.play(2, 'O');
    check(
        (MinimaxAlgorithm.nonLosingMoves(b.getBits('X'), b.occupiedBits())
                & MinimaxAlgorithm.columnMove(b.occupiedBits(), 3))
            == 0,
        "do not support opponent win above");
  }

  private static char[][] array(Board b) {
    char[][] a = new char[6][7];
    for (int r = 0; r < 6; r++) for (int c = 0; c < 7; c++) a[r][c] = b.getPiece(r, c);
    return a;
  }

  private static boolean win(char[][] a, char p) {
    for (int r = 0; r < 6; r++)
      for (int c = 0; c < 7; c++)
        for (int[] d : new int[][] {{1, 0}, {0, 1}, {1, 1}, {1, -1}}) {
          int er = r + 3 * d[0], ec = c + 3 * d[1];
          if (er < 0 || er >= 6 || ec < 0 || ec >= 7) continue;
          boolean four = true;
          for (int i = 0; i < 4; i++) if (a[r + i * d[0]][c + i * d[1]] != p) four = false;
          if (four) return true;
        }
    return false;
  }

  private static void bitboardProperties() {
    for (int n = 0; n < 2500; n++) {
      Board b = randomBoard(RANDOM.nextInt(39));
      char[][] a = array(b);
      for (char p : new char[] {'X', 'O'}) {
        check(b.checkWin(p) == win(a, p), "independent win detector");
        long wins = MinimaxAlgorithm.winningSquares(b.getBits(p), b.occupiedBits());
        for (int r = 0; r < 6; r++)
          for (int c = 0; c < 7; c++)
            if (a[r][c] == ' ') {
              a[r][c] = p;
              boolean creates = win(a, p);
              a[r][c] = ' ';
              long bit = 1L << (c * 7 + 5 - r);
              check(((wins & bit) != 0) == creates, "winning squares match independent cells");
            }
      }
      long moves = MinimaxAlgorithm.possible(b.occupiedBits());
      for (int c = 0; c < 7; c++)
        check(((moves & (63L << (c * 7))) != 0) == b.canPlay(c), "legal bitboard moves");
      check(
          MinimaxAlgorithm.mirror(MinimaxAlgorithm.mirror(b.occupiedBits())) == b.occupiedBits(),
          "mirror involution");
      long safe = MinimaxAlgorithm.nonLosingMoves(b.getBits('X'), b.occupiedBits());
      if ((MinimaxAlgorithm.winningSquares(b.getBits('X'), b.occupiedBits()) & moves) == 0) {
        for (int c : b.getAvailableColumns()) {
          Board child = new Board(b);
          child.play(c, 'X');
          boolean danger = false;
          for (int enemy : child.getAvailableColumns()) {
            Board reply = new Board(child);
            reply.play(enemy, 'O');
            if (reply.checkWin('O')) danger = true;
          }
          check(
              ((safe & MinimaxAlgorithm.columnMove(b.occupiedBits(), c)) != 0) == !danger,
              "sound tactical pruning at col "
                  + c
                  + " safe="
                  + Long.toHexString(safe)
                  + " danger="
                  + danger
                  + " red="
                  + Long.toHexString(b.getBits('X'))
                  + " yellow="
                  + Long.toHexString(b.getBits('O'))
                  + " board="
                  + Arrays.deepToString(a));
        }
      }
    }
  }

  private static Board randomBoard(int target) {
    Board b = new Board();
    char p = 'X';
    for (int n = 0; n < target; n++) {
      var cols = b.getAvailableColumns();
      Collections.shuffle(cols, RANDOM);
      boolean played = false;
      for (int c : cols) {
        Board next = new Board(b);
        next.play(c, p);
        if (!next.checkWin(p)) {
          b = next;
          played = true;
          break;
        }
      }
      if (!played) break;
      p = Constants.opponent(p);
    }
    return b;
  }

  /** Independent board-copy minimax: no transpositions, PVS or bitboard move filtering. */
  private static int reference(Board b, char p, int depth) {
    return reference(b, p, depth, MinimaxAlgorithm.QUIET_PLIES);
  }

  private static List<Integer> winningMoves(Board b, char p) {
    List<Integer> wins = new ArrayList<>();
    for (int c : b.getAvailableColumns()) {
      Board child = new Board(b);
      child.play(c, p);
      if (child.checkWin(p)) wins.add(c);
    }
    return wins;
  }

  private static int reference(Board b, char p, int depth, int quiet) {
    int count = b.getMoveCount();
    if (b.isFull()) return 0;
    if (!winningMoves(b, p).isEmpty()) return 100000 - count - 1;
    List<Integer> safe = new ArrayList<>();
    for (int c : b.getAvailableColumns()) {
      Board child = new Board(b);
      child.play(c, p);
      if (winningMoves(child, Constants.opponent(p)).isEmpty()) safe.add(c);
    }
    if (safe.isEmpty()) return -100000 + count + 2;
    if (count >= 40) return 0;
    if (depth == 0) {
      if (quiet >= 3)
        for (int c : safe) {
          Board child = new Board(b);
          child.play(c, p);
          if (winningMoves(child, p).size() >= 2) return 100000 - count - 3;
        }
      if (quiet == 0 || safe.size() > 1)
        return MinimaxAlgorithm.evaluate(b.getBits(p), b.occupiedBits());
      Board child = new Board(b);
      child.play(safe.get(0), p);
      return -reference(child, Constants.opponent(p), 0, quiet - 1);
    }
    int best = -200000;
    for (int c : safe) {
      Board child = new Board(b);
      child.play(c, p);
      best =
          Math.max(
              best,
              -reference(child, Constants.opponent(p), depth - 1, MinimaxAlgorithm.QUIET_PLIES));
    }
    return best;
  }

  private static void searchAgainstReference() {
    for (int n = 0; n < 100; n++) {
      Board b = randomBoard(8 + RANDOM.nextInt(27));
      char p = b.getMoveCount() % 2 == 0 ? 'X' : 'O';
      int depth = 1 + RANDOM.nextInt(4);
      long red = b.getBits('X'), yellow = b.getBits('O');
      var actual = MinimaxAlgorithm.search(b, p, new SearchSettings(depth, 0, 1));
      int expected = reference(b, p, depth);
      check(
          actual.score() == expected,
          "alpha-beta/PVS score: " + actual.score() + " vs " + expected + " at depth " + depth);
      check(b.canPlay(actual.column()), "legal chosen move");
      Board child = new Board(b);
      child.play(actual.column(), p);
      int chosenValue =
          child.checkWin(p)
              ? 100000 - b.getMoveCount() - 1
              : -reference(child, Constants.opponent(p), depth - 1);
      check(chosenValue == expected, "chosen column attains minimax score");
      check(b.getBits('X') == red && b.getBits('O') == yellow, "search leaves board untouched");
    }
    for (int n = 0; n < 16; n++) {
      Board b = randomBoard(15 + RANDOM.nextInt(17));
      char p = b.getMoveCount() % 2 == 0 ? 'X' : 'O';
      Board mirrored = new Board();
      for (int c = 0; c < 7; c++)
        for (int r = 5; r >= 0; r--)
          if (b.getPiece(r, c) != ' ') mirrored.play(6 - c, b.getPiece(r, c));
      var left = MinimaxAlgorithm.search(b, p, new SearchSettings(5, 0, 1));
      var right = MinimaxAlgorithm.search(mirrored, p, new SearchSettings(5, 0, 1));
      check(left.score() == right.score(), "symmetric search score");
    }
    for (int n = 0; n < 8; n++) {
      Board b = randomBoard(20 + RANDOM.nextInt(12));
      char p = b.getMoveCount() % 2 == 0 ? 'X' : 'O';
      var deep = MinimaxAlgorithm.search(b, p, new SearchSettings(6, 0, 1));
      check(deep.score() == reference(b, p, 6), "deep transposition/PVS comparison");
    }
    for (int n = 0; n < 20; n++) {
      Board b = randomBoard(36);
      char p = b.getMoveCount() % 2 == 0 ? 'X' : 'O';
      if (b.getMoveCount() < 32) continue;
      var actual = MinimaxAlgorithm.search(b, p, new SearchSettings(42, 0, 1));
      check(actual.score() == reference(b, p, 42 - b.getMoveCount()), "exact endgame");
      check(actual.proven(), "endgame proof");
    }
    try (var session = new MinimaxAlgorithm.Session()) {
      for (int n = 0; n < 80; n++) {
        Board b = randomBoard(10 + RANDOM.nextInt(24));
        char p = b.getMoveCount() % 2 == 0 ? 'X' : 'O';
        for (int depth : new int[] {5, 2, 4}) {
          var actual = session.search(b, p, new SearchSettings(depth, 0, 1));
          check(
              actual.score() == reference(b, p, depth),
              "persistent cache vs independent minimax at multiple horizons");
        }
      }
    }
  }

  private static void deadlines() {
    Board b = new Board();
    long start = System.nanoTime();
    var result = MinimaxAlgorithm.search(b, 'X', new SearchSettings(42, 40, 1));
    check((System.nanoTime() - start) / 1_000_000 < 1500, "bounded deadline");
    check(b.canPlay(result.column()), "deadline legal fallback");
    check(result.interrupted() || result.proven(), "deadline reported");
    AtomicBoolean stop = new AtomicBoolean(true);
    result = MinimaxAlgorithm.search(b, 'X', new SearchSettings(42, 0, 1), stop::get, r -> {});
    check(result.interrupted() && result.completedDepth() == 0, "cooperative cancellation");
    check(result.column() == 3, "safe central fallback");
    result = MinimaxAlgorithm.search(b, 'X', new SearchSettings(7, 0, 1));
    check(result.completedDepth() == 7 && result.column() == 3, "central opening searched");
    System.out.println(
        "Opening benchmark: depth "
            + result.completedDepth()
            + ", "
            + result.nodes()
            + " nodes, "
            + result.elapsedMillis()
            + " ms");
  }
}
