package Puissance4;

/**
 * Depth counts plies. Immediate forced replies have a bounded tactical extension. Zero milliseconds
 * means no deadline. Opening knowledge is an explicit separate option.
 */
public record SearchSettings(
    int maxDepth, long timeLimitMillis, int cacheMegabytes, boolean useOpeningBook) {
  public SearchSettings(int maxDepth, long timeLimitMillis, int cacheMegabytes) {
    this(maxDepth, timeLimitMillis, cacheMegabytes, false);
  }

  public SearchSettings {
    if (maxDepth < 1 || maxDepth > 42) throw new IllegalArgumentException("Depth must be 1..42");
    if (timeLimitMillis < 0 || timeLimitMillis > 300_000)
      throw new IllegalArgumentException("Invalid time budget");
    if (cacheMegabytes < 1 || cacheMegabytes > 256)
      throw new IllegalArgumentException("Cache must be 1..256 MB");
  }

  public static SearchSettings standard() {
    return new SearchSettings(10, 2000, 32, false);
  }
}
