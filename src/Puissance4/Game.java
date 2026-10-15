package Puissance4;

import java.util.ArrayList;
import java.util.List;

/** Rules and history, independent of Swing and of the search engine. */
public final class Game {
  public enum Mode {
    HUMAN_VS_AI,
    HUMAN_VS_HUMAN
  }

  public record Move(int row, int column, char player) {}

  private final Board board = new Board();
  private final List<Move> history = new ArrayList<>();
  private final Mode mode;
  private final char humanColor;
  private char currentPlayer;
  private char winner = ' ';

  public Game(Mode mode, char humanColor, char startingColor) {
    Constants.opponent(humanColor);
    Constants.opponent(startingColor);
    this.mode = mode;
    this.humanColor = humanColor;
    currentPlayer = startingColor;
  }

  public Move play(int column) {
    if (isGameOver()) return null;
    int row = board.play(column, currentPlayer);
    if (row < 0) return null;
    Move move = new Move(row, column, currentPlayer);
    history.add(move);
    if (board.checkWin(currentPlayer)) winner = currentPlayer;
    else if (!board.isFull()) currentPlayer = Constants.opponent(currentPlayer);
    return move;
  }

  public Move undo() {
    if (history.isEmpty()) return null;
    Move move = history.remove(history.size() - 1);
    board.undo(move.column());
    currentPlayer = move.player();
    winner = ' ';
    return move;
  }

  /** In solo mode, returns to the previous human decision whenever possible. */
  public void undoHumanTurn() {
    if (history.isEmpty()) return;
    Move removed = undo();
    if (mode == Mode.HUMAN_VS_AI && removed.player() != humanColor && !history.isEmpty()) undo();
  }

  public Board getBoard() {
    return board;
  }

  public Mode getMode() {
    return mode;
  }

  public char getHumanColor() {
    return humanColor;
  }

  public char getCurrentPlayer() {
    return currentPlayer;
  }

  public char getWinner() {
    return winner;
  }

  public boolean isGameOver() {
    return winner != ' ' || board.isFull();
  }

  public boolean isAITurn() {
    return mode == Mode.HUMAN_VS_AI && !isGameOver() && currentPlayer != humanColor;
  }

  public boolean canUndo() {
    if (history.isEmpty()) return false;
    return mode == Mode.HUMAN_VS_HUMAN
        || history.size() > 1
        || history.get(0).player() == humanColor;
  }

  public Move getLastMove() {
    return history.isEmpty() ? null : history.get(history.size() - 1);
  }

  public List<Move> getHistory() {
    return List.copyOf(history);
  }
}
