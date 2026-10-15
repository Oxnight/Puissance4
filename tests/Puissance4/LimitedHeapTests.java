package Puissance4;

/** Run in a separate JVM with -Xmx32m; the requested cache is deliberately much larger. */
public final class LimitedHeapTests {
  public static void main(String[] args) {
    try (var session = new MinimaxAlgorithm.Session()) {
      for (int i = 0; i < 4; i++) {
        var result = session.search(new Board(), 'X', new SearchSettings(42, 100, 256));
        if (result.cacheBytes() > 4L * 1024 * 1024 || result.column() < 0 || !result.interrupted())
          throw new AssertionError("small-heap cache budget or deadline");
      }
      var opening = session.search(new Board(), 'X', new SearchSettings(42, 1000, 256, true));
      if (opening.source() != MinimaxAlgorithm.Source.OPENING_BOOK
          || opening.column() != 3
          || !opening.proven()) throw new AssertionError("opening book on a constrained heap");
    }
    System.out.println("OK - engine and opening book with 32 MB heap / 256 MB requested cache");
  }
}
