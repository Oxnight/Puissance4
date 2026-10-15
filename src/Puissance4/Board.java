package Puissance4;

import java.util.ArrayList;
import java.util.List;

/**
 * Gravity-based 7 by 6 board. Public row coordinates start at the top. Bitboards use seven bits per
 * column: six playable cells and a sentinel.
 */
public final class Board {
  private static final int[] LINE_SHIFTS = {1, 7, 6, 8};
  private long red;
  private long yellow;
  private final int[] heights = new int[Constants.COLUMNS];
  private int moveCount;

  public Board() {}

  public Board(Board other) {
    red = other.red;
    yellow = other.yellow;
    moveCount = other.moveCount;
    for (int c = 0; c < heights.length; c++) heights[c] = other.heights[c];
  }

  public char getPiece(int row, int col) {
    checkCell(row, col);
    long bit = 1L << (col * 7 + Constants.ROWS - 1 - row);
    return (red & bit) != 0 ? 'X' : (yellow & bit) != 0 ? 'O' : ' ';
  }

  public long getBits(char player) {
    Constants.opponent(player); // validate symbol
    return player == 'X' ? red : yellow;
  }

  public long occupiedBits() {
    return red | yellow;
  }

  public int getMoveCount() {
    return moveCount;
  }

  public boolean canPlay(int col) {
    return col >= 0 && col < Constants.COLUMNS && heights[col] < Constants.ROWS;
  }

  public int getLandingRow(int col) {
    return canPlay(col) ? Constants.ROWS - 1 - heights[col] : -1;
  }

  /** Drops a disc and returns its top-based row, or -1 for a full column. */
  public int play(int col, char player) {
    Constants.opponent(player);
    if (!canPlay(col)) return -1;
    long bit = 1L << (col * 7 + heights[col]);
    if (player == 'X') red |= bit;
    else yellow |= bit;
    int row = Constants.ROWS - 1 - heights[col];
    heights[col] = heights[col] + 1;
    moveCount++;
    assert (red & yellow) == 0 : "Disc colors must not overlap";
    return row;
  }

  /** Removes only the highest disc in a column. Used by game history. */
  public char undo(int col) {
    if (col < 0 || col >= Constants.COLUMNS || heights[col] == 0) return ' ';
    long bit = 1L << (col * 7 + --heights[col]);
    char player = (red & bit) != 0 ? 'X' : 'O';
    red &= ~bit;
    yellow &= ~bit;
    moveCount--;
    return player;
  }

  public List<Integer> getAvailableColumns() {
    List<Integer> cols = new ArrayList<>(7);
    for (int c = 0; c < Constants.COLUMNS; c++) if (canPlay(c)) cols.add(c);
    return cols;
  }

  public boolean checkWin(char symbol) {
    return hasFour(getBits(symbol));
  }

  public boolean isFull() {
    return moveCount == Constants.CAPACITY;
  }

  public boolean isGameOver() {
    return checkWin('X') || checkWin('O') || isFull();
  }

  public static boolean hasFour(long bits) {
    for (int shift : LINE_SHIFTS) {
      long pairs = bits & (bits >>> shift);
      if ((pairs & (pairs >>> (2 * shift))) != 0) return true;
    }
    return false;
  }

  /** All cells belonging to at least one winning line. */
  public List<int[]> winningCells(char player) {
    List<int[]> cells = new ArrayList<>();
    boolean[][] marked = new boolean[6][7];
    for (int r = 0; r < 6; r++)
      for (int c = 0; c < 7; c++) {
        for (int[] d : new int[][] {{0, 1}, {1, 0}, {1, 1}, {1, -1}}) {
          int endR = r + 3 * d[0], endC = c + 3 * d[1];
          if (endR < 0 || endR >= 6 || endC < 0 || endC >= 7) continue;
          boolean win = true;
          for (int i = 0; i < 4; i++)
            if (getPiece(r + i * d[0], c + i * d[1]) != player) win = false;
          if (win) for (int i = 0; i < 4; i++) marked[r + i * d[0]][c + i * d[1]] = true;
        }
      }
    for (int r = 0; r < 6; r++)
      for (int c = 0; c < 7; c++) if (marked[r][c]) cells.add(new int[] {r, c});
    return cells;
  }

  private static void checkCell(int row, int col) {
    if (row < 0 || row >= 6 || col < 0 || col >= 7)
      throw new IndexOutOfBoundsException("Cell outside board");
  }
}
