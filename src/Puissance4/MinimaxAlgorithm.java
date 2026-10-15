package Puissance4;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Minimax/negamax with alpha-beta, PVS, bounded tactical extensions and reusable two-way
 * transpositions. Each Session serializes its searches; the EDT never owns its lock. No speculative
 * pruning, background pondering, or neural-model dependency.
 */
public final class MinimaxAlgorithm {
  static final int MATE = 100_000;
  static final int QUIET_PLIES = 4;
  private static final int INF = 200_000;
  private static final int[] CENTER_ORDER = {3, 2, 4, 1, 5, 0, 6};
  private static final int[] SHIFTS = {6, 7, 8};
  static final long BOTTOM_MASK, BOARD_MASK;
  private static final long[] WINDOWS;
  private static final long CENTER = 63L << 21;

  static {
    long bottom = 0, board = 0;
    for (int c = 0; c < 7; c++) {
      bottom |= 1L << (7 * c);
      board |= 63L << (7 * c);
    }
    BOTTOM_MASK = bottom;
    BOARD_MASK = board;
    List<Long> windows = new ArrayList<>(69);
    for (int c = 0; c < 7; c++)
      for (int r = 0; r < 6; r++)
        for (int[] d : new int[][] {{1, 0}, {0, 1}, {1, 1}, {1, -1}}) {
          int ec = c + 3 * d[0], er = r + 3 * d[1];
          if (ec < 0 || ec >= 7 || er < 0 || er >= 6) continue;
          long w = 0;
          for (int i = 0; i < 4; i++) w |= 1L << ((c + i * d[0]) * 7 + r + i * d[1]);
          windows.add(w);
        }
    WINDOWS = windows.stream().mapToLong(Long::longValue).toArray();
  }

  public enum Source {
    TACTICAL,
    OPENING_BOOK,
    SEARCH,
    EXACT_SEARCH
  }

  public record SearchResult(
      int column,
      int score,
      int completedDepth,
      long nodes,
      long elapsedMillis,
      boolean proven,
      boolean interrupted,
      long cacheBytes,
      long cacheHits,
      Source source) {}

  private record Iteration(int column, int score) {}

  /**
   * Retains useful search information for a single match. close() is non-blocking. Concurrent calls
   * wait interruptibly and remain subject to their own deadline.
   */
  public static final class Session implements AutoCloseable {
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean closed;
    private Table table;
    private int generation;
    private final int[][] history = new int[2][7];

    public SearchResult search(
        Board board,
        char player,
        SearchSettings settings,
        BooleanSupplier cancelled,
        Consumer<SearchResult> progress) {
      Constants.opponent(player);
      Objects.requireNonNull(settings);
      Objects.requireNonNull(cancelled);
      Objects.requireNonNull(progress);
      long started = System.nanoTime();
      long deadline =
          settings.timeLimitMillis() == 0
              ? Long.MAX_VALUE
              : started + settings.timeLimitMillis() * 1_000_000L;
      boolean acquired = false;
      try {
        while (!(acquired = lock.tryLock(2, TimeUnit.MILLISECONDS))) {
          if (closed || cancelled.getAsBoolean() || System.nanoTime() >= deadline)
            return fallback(board, player, started, true);
        }
        if (closed
            || cancelled.getAsBoolean()
            || Thread.currentThread().isInterrupted()
            || System.nanoTime() >= deadline) return fallback(board, player, started, true);
        generation = (generation + 1) & 7;
        for (int[] side : history) for (int c = 0; c < 7; c++) side[c] /= 2;
        MinimaxAlgorithm engine =
            new MinimaxAlgorithm(this, settings, cancelled, progress, started, deadline);
        return engine.run(board, player);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return fallback(board, player, started, true);
      } finally {
        if (acquired) {
          if (closed) table = null;
          lock.unlock();
        }
      }
    }

    public SearchResult search(Board b, char p, SearchSettings s) {
      return search(b, p, s, () -> false, r -> {});
    }

    @Override
    public void close() {
      closed = true;
      if (lock.tryLock()) {
        try {
          table = null;
        } finally {
          lock.unlock();
        }
      }
    }

    public long cacheBytes() {
      if (!lock.tryLock()) return -1;
      try {
        return table == null ? 0 : table.bytes();
      } finally {
        lock.unlock();
      }
    }
  }

  private final Session session;
  private final SearchSettings settings;
  private final BooleanSupplier cancelled;
  private final Consumer<SearchResult> progress;
  private final long started, deadline;
  private final int[][] orderedColumns = new int[44][7],
      orderingScores = new int[44][7],
      killers = new int[44][2];
  private Table table;
  private long nodes, cacheHits;
  private long stageDeadline = Long.MAX_VALUE;

  private MinimaxAlgorithm(
      Session session,
      SearchSettings settings,
      BooleanSupplier cancelled,
      Consumer<SearchResult> progress,
      long started,
      long deadline) {
    this.session = session;
    this.settings = settings;
    this.cancelled = cancelled;
    this.progress = progress;
    this.started = started;
    this.deadline = deadline;
    table = session.table;
    for (int[] k : killers) Arrays.fill(k, -1);
  }

  public static SearchResult search(
      Board b, char p, SearchSettings s, BooleanSupplier cancel, Consumer<SearchResult> progress) {
    try (Session session = new Session()) {
      return session.search(b, p, s, cancel, progress);
    }
  }

  public static SearchResult search(Board b, char p, SearchSettings s) {
    return search(b, p, s, () -> false, r -> {});
  }

  private SearchResult run(Board board, char player) {
    if (board.isGameOver())
      return result(-1, terminalScore(board, player), 0, true, false, Source.TACTICAL);
    long own = board.getBits(player), mask = board.occupiedBits();
    int count = board.getMoveCount();
    long legal = possible(mask), wins = winningSquares(own, mask) & legal;
    if (wins != 0)
      return result(firstColumn(wins), MATE - count - 1, 1, true, false, Source.TACTICAL);
    long safe = nonLosingMoves(own, mask);
    if (safe == 0)
      return result(
          firstColumn(legal),
          -MATE + count + 2,
          Math.min(2, settings.maxDepth()),
          true,
          false,
          Source.TACTICAL);
    if (count >= 40) return result(firstColumn(safe), 0, 42 - count, true, false, Source.TACTICAL);
    int fork = settings.maxDepth() >= 3 ? findForkMove(own, mask, safe) : -1;
    if (fork >= 0) return result(fork, MATE - count - 3, 3, true, false, Source.TACTICAL);
    if (settings.useOpeningBook()) {
      OpeningBook book = OpeningBook.get();
      long key = own + mask, reflected = mirror(own) + mirror(mask);
      boolean flipped = reflected < key;
      int entry = book.find(Math.min(key, reflected), count);
      if (entry >= 0) {
        int best = -1, score = OpeningBook.INVALID;
        for (int c : CENTER_ORDER)
          if ((legal & columnMove(mask, c)) != 0) {
            int value = book.scoreAt(entry, flipped ? 6 - c : c);
            if (value > score) {
              best = c;
              score = value;
            }
          }
        if (best >= 0)
          return result(
              best, OpeningBook.toMateScore(score, count), 0, true, false, Source.OPENING_BOOK);
      }
    }
    int best = firstColumn(safe), score = 0, completed = 0;
    boolean proven = false, interrupted = false;
    Source source = Source.SEARCH;
    for (int depth = 1; depth <= Math.min(settings.maxDepth(), 42 - count); depth++) {
      try {
        checkStop();
        ensureTable(depth);
        int alpha = -INF, beta = INF;
        if (depth >= 4 && completed > 0) {
          alpha = score - 110;
          beta = score + 110;
        }
        Iteration iteration = root(own, mask, count, depth, alpha, beta, best);
        if (iteration.score() <= alpha || iteration.score() >= beta)
          iteration = root(own, mask, count, depth, -INF, INF, iteration.column());
        best = iteration.column();
        score = iteration.score();
        completed = depth;
        proven = Math.abs(score) >= MATE - 42 || depth >= 42 - count;
        progress.accept(result(best, score, completed, proven, false, Source.SEARCH));
        if (proven) break;
        if (depth == 8 && count >= 16 && settings.maxDepth() >= 42 - count) {
          // Try a terminal-only proof search while preserving time for ordinary
          // iterative deepening if the position is still too expensive to solve.
          long remaining = deadline - System.nanoTime();
          if (deadline == Long.MAX_VALUE || remaining > 12_000_000L) {
            stageDeadline =
                deadline == Long.MAX_VALUE ? Long.MAX_VALUE : System.nanoTime() + remaining / 2;
            try {
              ensureTable(18);
              Iteration exact = exactRoot(own, mask, count, best);
              best = exact.column();
              score = OpeningBook.toMateScore(exact.score(), count);
              completed = 42 - count;
              proven = true;
              source = Source.EXACT_SEARCH;
              progress.accept(result(best, score, completed, true, false, source));
              break;
            } catch (SearchStopped stopped) {
              if (session.closed
                  || Thread.currentThread().isInterrupted()
                  || cancelled.getAsBoolean()
                  || System.nanoTime() >= deadline) {
                interrupted = true;
                break;
              }
            } finally {
              stageDeadline = Long.MAX_VALUE;
            }
          }
        }
      } catch (SearchStopped stopped) {
        interrupted = true;
        break;
      }
    }
    return result(best, score, completed, proven, interrupted, source);
  }

  private Iteration exactRoot(long own, long mask, int count, int preferred) {
    long moves = nonLosingMoves(own, mask);
    int n = order(own, mask, moves, preferred, 0, count);
    int best = orderedColumns[0][0], bestScore = -22, alpha = -22;
    for (int i = 0; i < n; i++) {
      checkStop();
      int col = orderedColumns[0][i];
      long move = columnMove(mask, col);
      int value = -exact(own ^ mask, mask | move, count + 1, -22, -alpha, 1);
      if (value > bestScore) {
        bestScore = value;
        best = col;
      }
      alpha = Math.max(alpha, value);
    }
    return new Iteration(best, bestScore);
  }

  /**
   * Terminal-only negamax in the compact [-21,21] exact-score domain. Entries tagged 62 never
   * supply heuristic scores to a depth-limited search.
   */
  private int exact(long own, long mask, int count, int alpha, int beta, int ply) {
    visit();
    if (count == 42) return 0;
    long legal = possible(mask);
    if ((winningSquares(own, mask) & legal) != 0) return (43 - count) / 2;
    long moves = nonLosingMoves(own, mask);
    if (moves == 0) return -(42 - count) / 2;
    if (count >= 40) return 0;
    int minimum = -(40 - count) / 2, maximum = (41 - count) / 2;
    alpha = Math.max(alpha, minimum);
    if (alpha >= beta) return alpha;
    beta = Math.min(beta, maximum + 1);
    if (alpha >= beta) return maximum;
    long key = own + mask, reflected = mirror(own) + mirror(mask);
    boolean flipped = reflected < key;
    if (flipped) key = reflected;
    int preferred = -1;
    if (table != null) {
      int entry = table.find(key, 62, count);
      if (entry >= 0) {
        int data = table.dataAt(entry);
        preferred = Table.move(data);
        if (flipped) preferred = 6 - preferred;
        cacheHits++;
        if (Table.depth(data) >= 62) {
          int value =
              Table.depth(data) == 62
                  ? Table.score(data)
                  : OpeningBook.fromMateScore(Table.score(data), count);
          int bound = Table.bound(data);
          if (bound == 1) return value;
          if (bound == 2) alpha = Math.max(alpha, value);
          else beta = Math.min(beta, value);
          if (alpha >= beta) return value;
        }
      }
    }
    int originalAlpha = alpha,
        originalBeta = beta,
        n = order(own, mask, moves, preferred, ply, count),
        best = orderedColumns[ply][0],
        bestScore = -22;
    for (int i = 0; i < n; i++) {
      int col = orderedColumns[ply][i];
      long move = columnMove(mask, col);
      int value = -exact(own ^ mask, mask | move, count + 1, -beta, -alpha, ply + 1);
      if (value > bestScore) {
        bestScore = value;
        best = col;
      }
      alpha = Math.max(alpha, value);
      if (alpha >= beta) break;
    }
    int bound = bestScore <= originalAlpha ? 3 : bestScore >= originalBeta ? 2 : 1;
    if (table != null)
      table.store(key, 62, bestScore, bound, flipped ? 6 - best : best, session.generation);
    return bestScore;
  }

  private void ensureTable(int depth) {
    if (depth <= 2) return;
    int budget =
        (int)
            Math.min(
                settings.cacheMegabytes(),
                Math.max(1, Runtime.getRuntime().maxMemory() / 8 / 1024 / 1024));
    if (table == null || table.budgetMegabytes != budget) {
      table = new Table(budget);
      session.table = table;
    }
    int target =
        depth <= 4
            ? 4096
            : depth <= 6
                ? 32768
                : depth <= 8
                    ? 131072
                    : depth <= 10
                        ? 524288
                        : depth <= 12
                            ? 1048576
                            : depth <= 16 ? 2097152 : depth <= 20 ? 4194304 : 8388608;
    // Migration never doubles the live table. Skip optional growth close to a deadline.
    if (deadline == Long.MAX_VALUE || deadline - System.nanoTime() > 20_000_000L)
      table.grow(Math.min(target, table.maximumSlots));
  }

  private Iteration root(
      long own, long mask, int count, int depth, int alpha, int beta, int preferred) {
    long moves = nonLosingMoves(own, mask);
    int n = order(own, mask, moves, preferred, 0, count);
    int best = orderedColumns[0][0], bestScore = -INF;
    for (int i = 0; i < n; i++) {
      checkStop();
      int col = orderedColumns[0][i];
      long move = columnMove(mask, col);
      int value;
      if (i == 0) value = -negamax(own ^ mask, mask | move, count + 1, depth - 1, -beta, -alpha, 1);
      else {
        value = -negamax(own ^ mask, mask | move, count + 1, depth - 1, -alpha - 1, -alpha, 1);
        if (value > alpha && value < beta)
          value = -negamax(own ^ mask, mask | move, count + 1, depth - 1, -beta, -alpha, 1);
      }
      if (value > bestScore) {
        bestScore = value;
        best = col;
      }
      alpha = Math.max(alpha, value);
      if (alpha >= beta) break;
    }
    return new Iteration(best, bestScore);
  }

  private int negamax(long own, long mask, int count, int depth, int alpha, int beta, int ply) {
    visit();
    if (count == 42) return 0;
    long legal = possible(mask);
    if ((winningSquares(own, mask) & legal) != 0) return MATE - count - 1;
    long moves = nonLosingMoves(own, mask);
    if (moves == 0) return -MATE + count + 2;
    if (count >= 40) return 0;
    if (depth == 0) return quiet(own, mask, count, alpha, beta, QUIET_PLIES, moves);
    int minimum = -MATE + count + 4, maximum = MATE - count - 3;
    if (alpha < minimum) {
      alpha = minimum;
      if (alpha >= beta) return alpha;
    }
    if (beta > maximum + 1) {
      beta = maximum + 1;
      if (alpha >= beta) return maximum;
    }
    long key = own + mask, reflected = mirror(own) + mirror(mask);
    boolean flipped = reflected < key;
    if (flipped) key = reflected;
    int preferred = -1;
    if (table != null) {
      int entry = table.find(key, depth, count);
      if (entry >= 0) {
        int data = table.dataAt(entry);
        preferred = Table.move(data);
        if (flipped) preferred = 6 - preferred;
        cacheHits++;
        if (Table.usable(data, depth, count)) {
          int value = Table.score(data);
          int bound = Table.bound(data);
          if (bound == 1) return value;
          if (bound == 2) alpha = Math.max(alpha, value);
          else beta = Math.min(beta, value);
          if (alpha >= beta) return value;
        }
      }
    }
    int originalAlpha = alpha, originalBeta = beta;
    int n = order(own, mask, moves, preferred, ply, count),
        best = orderedColumns[ply][0],
        bestScore = -INF;
    for (int i = 0; i < n; i++) {
      int col = orderedColumns[ply][i];
      long move = columnMove(mask, col);
      int value;
      if (i == 0)
        value = -negamax(own ^ mask, mask | move, count + 1, depth - 1, -beta, -alpha, ply + 1);
      else {
        value =
            -negamax(own ^ mask, mask | move, count + 1, depth - 1, -alpha - 1, -alpha, ply + 1);
        if (value > alpha && value < beta)
          value = -negamax(own ^ mask, mask | move, count + 1, depth - 1, -beta, -alpha, ply + 1);
      }
      if (value > bestScore) {
        bestScore = value;
        best = col;
      }
      alpha = Math.max(alpha, value);
      if (alpha >= beta) {
        rememberCutoff(col, depth, ply, count);
        break;
      }
    }
    int bound = bestScore <= originalAlpha ? 3 : bestScore >= originalBeta ? 2 : 1;
    if (table != null) {
      // A tactical extension can prove the outcome beyond the nominal horizon. Its
      // distance is not reused as a depth-independent exact score until that whole
      // distance lies inside the nominal search (or the board was fully searched).
      boolean completeProof =
          depth >= 42 - count
              || (Math.abs(bestScore) >= MATE - 42 && MATE - Math.abs(bestScore) - count <= depth);
      int savedDepth = bound == 1 && completeProof ? 63 : depth;
      table.store(key, savedDepth, bestScore, bound, flipped ? 6 - best : best, session.generation);
    }
    return bestScore;
  }

  /**
   * Extend only forced replies (one safe move), plus exact immediate fork detection. Quiet
   * branching stops at four extra plies; no unlimited quiescence explosion.
   */
  private int quiet(
      long own, long mask, int count, int alpha, int beta, int remaining, long moves) {
    if (remaining >= 3) {
      int fork = findForkMove(own, mask, moves);
      if (fork >= 0) return MATE - count - 3;
    }
    if (remaining == 0 || (moves & (moves - 1)) != 0) return evaluate(own, mask);
    long move = moves & -moves;
    long childMask = mask | move, childOwn = own ^ mask;
    int childCount = count + 1;
    visit();
    if (childCount == 42) return 0;
    long legal = possible(childMask);
    if ((winningSquares(childOwn, childMask) & legal) != 0) return -(MATE - childCount - 1);
    long safe = nonLosingMoves(childOwn, childMask);
    if (safe == 0) return -(-MATE + childCount + 2);
    if (childCount >= 40) return 0;
    return -quiet(childOwn, childMask, childCount, -beta, -alpha, remaining - 1, safe);
  }

  private static int findForkMove(long own, long mask, long safe) {
    for (int c : CENTER_ORDER) {
      long move = columnMove(mask, c);
      if ((move & safe) == 0) continue;
      long nextMask = mask | move;
      long threats = winningSquares(own | move, nextMask) & possible(nextMask);
      if ((threats & (threats - 1)) != 0) return c;
    }
    return -1;
  }

  private void rememberCutoff(int col, int depth, int ply, int count) {
    if (killers[ply][0] != col) {
      killers[ply][1] = killers[ply][0];
      killers[ply][0] = col;
    }
    int[] history = session.history[count & 1];
    history[col] = Math.min(8192, history[col] + depth * depth);
  }

  private int order(long own, long mask, long moves, int preferred, int ply, int count) {
    int n = 0;
    for (int col : CENTER_ORDER) {
      long move = columnMove(mask, col);
      if ((moves & move) == 0) continue;
      int value =
          (col == preferred ? 100_000 : 0)
              + 96 * Long.bitCount(winningSquares(own | move, mask | move))
              + (col == killers[ply][0] ? 2048 : col == killers[ply][1] ? 1024 : 0)
              + session.history[count & 1][col]
              + 3
              - Math.abs(3 - col);
      int j = n;
      while (j > 0 && orderingScores[ply][j - 1] < value) {
        orderedColumns[ply][j] = orderedColumns[ply][j - 1];
        orderingScores[ply][j] = orderingScores[ply][j - 1];
        j--;
      }
      orderedColumns[ply][j] = col;
      orderingScores[ply][j] = value;
      n++;
    }
    return n;
  }

  static long possible(long mask) {
    return (mask + BOTTOM_MASK) & BOARD_MASK;
  }

  static long columnMove(long mask, int col) {
    return (mask + (1L << (col * 7))) & (63L << (col * 7));
  }

  static long winningSquares(long bits, long mask) {
    long result = (bits << 1) & (bits << 2) & (bits << 3);
    for (int shift : SHIFTS) {
      long p = (bits << shift) & (bits << (2 * shift));
      result |= p & (bits << (3 * shift));
      result |= p & (bits >>> shift);
      p = (bits >>> shift) & (bits >>> (2 * shift));
      result |= p & (bits << shift);
      result |= p & (bits >>> (3 * shift));
    }
    return result & BOARD_MASK & ~mask;
  }

  static long nonLosingMoves(long own, long mask) {
    long moves = possible(mask),
        enemyWins = winningSquares(own ^ mask, mask),
        forced = moves & enemyWins;
    if (forced != 0) {
      if ((forced & (forced - 1)) != 0) return 0;
      moves = forced;
    }
    return moves & ~(enemyWins >>> 1);
  }

  static int evaluate(long own, long mask) {
    long enemy = own ^ mask;
    int score = 8 * (Long.bitCount(own & CENTER) - Long.bitCount(enemy & CENTER));
    for (long w : WINDOWS) {
      int us = Long.bitCount(own & w), them = Long.bitCount(enemy & w);
      if (them == 0) score += weight(us);
      if (us == 0) score -= weight(them);
    }
    long us = winningSquares(own, mask), them = winningSquares(enemy, mask);
    score += threatScore(us, them, mask) - threatScore(them, us, mask);
    score += 240 * (Long.bitCount(us & (us >>> 1)) - Long.bitCount(them & (them >>> 1)));
    return score;
  }

  private static int threatScore(long threats, long opposing, long mask) {
    int value = 0;
    for (int c = 0; c < 7; c++) {
      int height = Long.bitCount(mask & (63L << (c * 7)));
      long inColumn = (threats >>> (c * 7)) & 63L, enemy = (opposing >>> (c * 7)) & 63L;
      while (inColumn != 0) {
        int row = Long.numberOfTrailingZeros(inColumn);
        int distance = row - height;
        int strength = distance == 0 ? 600 : 180 / (distance + 1);
        if ((enemy & ((1L << row) - 1)) != 0) strength /= 3;
        value += strength;
        inColumn &= inColumn - 1;
      }
    }
    return value;
  }

  private static int weight(int pieces) {
    return switch (pieces) {
      case 1 -> 3;
      case 2 -> 18;
      case 3 -> 65;
      default -> 0;
    };
  }

  static long mirror(long bits) {
    long mirrored = 0;
    for (int c = 0; c < 7; c++) mirrored |= ((bits >>> (c * 7)) & 127L) << ((6 - c) * 7);
    return mirrored;
  }

  private static int firstColumn(long moves) {
    for (int c : CENTER_ORDER) if ((moves & (63L << (c * 7))) != 0) return c;
    return -1;
  }

  private static SearchResult fallback(Board b, char p, long started, boolean stopped) {
    if (b.isGameOver())
      return new SearchResult(
          -1,
          terminalScore(b, p),
          0,
          0,
          (System.nanoTime() - started) / 1_000_000,
          true,
          stopped,
          0,
          0,
          Source.TACTICAL);
    long mask = b.occupiedBits(),
        own = b.getBits(p),
        wins = winningSquares(own, mask) & possible(mask);
    if (wins != 0)
      return new SearchResult(
          firstColumn(wins),
          MATE - b.getMoveCount() - 1,
          1,
          0,
          (System.nanoTime() - started) / 1_000_000,
          true,
          stopped,
          0,
          0,
          Source.TACTICAL);
    long moves = nonLosingMoves(own, mask);
    if (moves == 0) moves = possible(mask);
    return new SearchResult(
        firstColumn(moves),
        0,
        0,
        0,
        (System.nanoTime() - started) / 1_000_000,
        false,
        stopped,
        0,
        0,
        Source.SEARCH);
  }

  private static int terminalScore(Board board, char player) {
    if (board.checkWin(player)) return MATE - board.getMoveCount();
    if (board.checkWin(Constants.opponent(player))) return -MATE + board.getMoveCount();
    return 0;
  }

  private SearchResult result(
      int col, int score, int depth, boolean proven, boolean stopped, Source source) {
    return new SearchResult(
        col,
        score,
        depth,
        nodes,
        (System.nanoTime() - started) / 1_000_000,
        proven,
        stopped,
        table == null ? 0 : table.bytes(),
        cacheHits,
        source);
  }

  private void visit() {
    nodes++;
    if ((nodes & 255) == 0) checkStop();
  }

  private void checkStop() {
    if (session.closed
        || Thread.currentThread().isInterrupted()
        || cancelled.getAsBoolean()
        || System.nanoTime() >= Math.min(deadline, stageDeadline)) throw new SearchStopped();
  }

  private static final class SearchStopped extends RuntimeException {
    private static final long serialVersionUID = 1L;

    SearchStopped() {
      super(null, null, false, false);
    }
  }

  /**
   * Two-way buckets in lazily allocated blocks. Growth splits existing buckets in place. Only key +
   * packed score/depth/bound/move/generation are stored (12 payload bytes/slot). Migration needs no
   * second full table, and capacity stays below the requested budget.
   */
  private static final class Table {
    private static final int BLOCK_SHIFT = 12,
        BLOCK_SIZE = 1 << BLOCK_SHIFT,
        SCORE_MASK = (1 << 18) - 1;

    private static final class Block {
      final long[] keys = new long[BLOCK_SIZE];
      final int[] data = new int[BLOCK_SIZE];
    }

    final int budgetMegabytes, maximumSlots;
    private final Block[] blocks;
    private int slots = BLOCK_SIZE, allocated;

    Table(int budget) {
      budgetMegabytes = budget;
      maximumSlots = Integer.highestOneBit(budget * 1024 * 1024 / 13);
      blocks = new Block[maximumSlots / BLOCK_SIZE];
    }

    long bytes() {
      return (long) allocated * (12L * BLOCK_SIZE + 32) + 16L + 4L * blocks.length;
    }

    private static long hash(long key) {
      long h = key ^ (key >>> 23);
      h *= 0x2127599bf4325c37L;
      return h ^ (h >>> 47);
    }

    private int bucket(long key) {
      return ((int) hash(key) & (slots - 1)) & ~1;
    }

    private Block block(int slot, boolean create) {
      int index = slot >>> BLOCK_SHIFT;
      Block b = blocks[index];
      if (b == null && create) {
        b = new Block();
        blocks[index] = b;
        allocated++;
      }
      return b;
    }

    int dataAt(int slot) {
      Block b = block(slot, false);
      return b == null ? 0 : b.data[slot & (BLOCK_SIZE - 1)];
    }

    private long keyAt(int slot) {
      Block b = block(slot, false);
      return b == null ? 0 : b.keys[slot & (BLOCK_SIZE - 1)];
    }

    static int score(int data) {
      return (data & SCORE_MASK) - MATE;
    }

    static int depth(int data) {
      return (data >>> 18) & 63;
    }

    static int bound(int data) {
      return (data >>> 24) & 3;
    }

    static int move(int data) {
      return (data >>> 26) & 7;
    }

    static int generation(int data) {
      return data >>> 29;
    }

    static boolean usable(int data, int requestedDepth, int count) {
      if (depth(data) == requestedDepth) return true;
      if (depth(data) != 63) return false;
      int distance = score(data) == 0 ? 42 - count : MATE - Math.abs(score(data)) - count;
      return distance <= requestedDepth;
    }

    int find(long key, int requestedDepth, int count) {
      int base = bucket(key), best = -1, quality = -1;
      for (int i = base; i <= base + 1; i++) {
        int data = dataAt(i);
        if (bound(data) == 0 || keyAt(i) != key) continue;
        int q = (usable(data, requestedDepth, count) ? 1000 : 0) + depth(data);
        if (q > quality) {
          quality = q;
          best = i;
        }
      }
      return best;
    }

    void store(long key, int depth, int score, int bound, int move, int generation) {
      int base = bucket(key), slot = -1, quality = Integer.MAX_VALUE;
      for (int i = base; i <= base + 1; i++) {
        int data = dataAt(i);
        if (Table.bound(data) == 0) {
          slot = i;
          break;
        }
        if (keyAt(i) == key && Table.depth(data) == depth) {
          slot = i;
          break;
        }
        int q =
            Table.depth(data)
                + (Table.generation(data) == generation ? 64 : 0)
                + (Table.bound(data) == 1 ? 4 : 0);
        if (q < quality) {
          quality = q;
          slot = i;
        }
      }
      Block b = block(slot, true);
      int at = slot & (BLOCK_SIZE - 1);
      b.keys[at] = key;
      b.data[at] =
          (score + MATE) | (depth << 18) | (bound << 24) | (move << 26) | (generation << 29);
    }

    void grow(int target) {
      while (slots < target) {
        int old = slots;
        slots *= 2;
        for (int blockIndex = 0; blockIndex < old / BLOCK_SIZE; blockIndex++) {
          Block b = blocks[blockIndex];
          if (b == null) continue;
          for (int i = 0; i < BLOCK_SIZE; i++) {
            int data = b.data[i];
            if (bound(data) == 0) continue;
            long key = b.keys[i];
            int source = (blockIndex << BLOCK_SHIFT) + i, destination = bucket(key) + (i & 1);
            if (destination != source) {
              Block to = block(destination, true);
              int at = destination & (BLOCK_SIZE - 1);
              to.keys[at] = key;
              to.data[at] = data;
              b.data[i] = 0;
            }
          }
        }
      }
    }
  }
}
