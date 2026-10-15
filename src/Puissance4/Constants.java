package Puissance4;

/** Dimensions and symbols of the classic game. */
public final class Constants {
  public static final int ROWS = 6;
  public static final int COLUMNS = 7;
  public static final int CONNECT = 4;
  public static final int CAPACITY = ROWS * COLUMNS;
  public static final char PLAYER_X = 'X'; // red
  public static final char PLAYER_O = 'O'; // yellow
  public static final char EMPTY = ' ';

  private Constants() {}

  public static char opponent(char player) {
    if (player == PLAYER_X) return PLAYER_O;
    if (player == PLAYER_O) return PLAYER_X;
    throw new IllegalArgumentException("Unknown player: " + player);
  }
}
