package Puissance4;

import java.io.*;
import java.util.zip.CRC32;

/**
 * Compact mathematical move scores for our exhaustive early-position enumeration. Loaded lazily on
 * the worker thread; no network or reference solver at runtime.
 */
final class OpeningBook {
  static final int INVALID = -128;
  private static final int RECORD_BYTES = 15;
  private final byte[] records;
  private final int maximumPly;

  private OpeningBook(byte[] records, int maximumPly) {
    this.records = records;
    this.maximumPly = maximumPly;
  }

  private static final class Holder {
    static final OpeningBook INSTANCE = load();
  }

  static OpeningBook get() {
    return Holder.INSTANCE;
  }

  int maximumPly() {
    return maximumPly;
  }

  int positions() {
    return records.length / RECORD_BYTES;
  }

  long bytes() {
    return records.length;
  }

  int find(long canonicalKey, int count) {
    if (count > maximumPly) return -1;
    int low = 0, high = positions() - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      long key = keyAt(mid);
      if (key < canonicalKey) low = mid + 1;
      else if (key > canonicalKey) high = mid - 1;
      else return mid;
    }
    return -1;
  }

  long keyAt(int record) {
    long key = 0;
    int offset = record * RECORD_BYTES;
    for (int i = 0; i < 8; i++) key = (key << 8) | (records[offset + i] & 255L);
    return key;
  }

  int scoreAt(int record, int column) {
    return records[record * RECORD_BYTES + 8 + column];
  }

  static int toMateScore(int points, int count) {
    if (points == 0) return 0;
    int terminalParity = (points > 0 ? count + 1 : count) & 1;
    int terminalMove = (terminalParity == 0 ? 44 : 43) - 2 * Math.abs(points);
    return points > 0 ? 100_000 - terminalMove : -100_000 + terminalMove;
  }

  static int fromMateScore(int score, int count) {
    if (score == 0) return 0;
    int terminal = 100_000 - Math.abs(score);
    int parity = (score > 0 ? count + 1 : count) & 1;
    int points = ((parity == 0 ? 44 : 43) - terminal) / 2;
    return score > 0 ? points : -points;
  }

  private static OpeningBook load() {
    InputStream resource = OpeningBook.class.getResourceAsStream("/Puissance4/openings.bin");
    if (resource == null) return new OpeningBook(new byte[0], -1);
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(resource))) {
      if (in.readInt() != 0x5034424B || in.readUnsignedShort() != 1)
        throw new IOException("Opening book format");
      int depth = in.readUnsignedByte();
      in.readUnsignedByte();
      int count = in.readInt();
      long checksum = Integer.toUnsignedLong(in.readInt());
      if (depth > 12 || count < 0 || count > 1_000_000)
        throw new IOException("Opening book dimensions");
      byte[] bytes = in.readNBytes(Math.multiplyExact(count, RECORD_BYTES));
      if (bytes.length != count * RECORD_BYTES || in.read() != -1)
        throw new IOException("Opening book length");
      CRC32 crc = new CRC32();
      crc.update(bytes);
      if (crc.getValue() != checksum) throw new IOException("Opening book checksum");
      OpeningBook book = new OpeningBook(bytes, depth);
      long previous = -1;
      for (int i = 0; i < count; i++) {
        long key = book.keyAt(i);
        if (key <= previous) throw new IOException("Opening book ordering");
        previous = key;
        for (int c = 0; c < 7; c++) {
          int score = book.scoreAt(i, c);
          if (score != INVALID && (score < -21 || score > 21))
            throw new IOException("Opening book score");
        }
      }
      return book;
    } catch (IOException e) {
      // A missing or damaged optional book cannot invalidate the search engine.
      System.err.println("Ouvertures indisponibles : " + e.getMessage());
      return new OpeningBook(new byte[0], -1);
    }
  }
}
