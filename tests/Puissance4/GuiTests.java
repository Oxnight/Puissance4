package Puissance4;

import Puissance4.gui.GameWindow;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Real Swing smoke tests. Use an ordinary desktop or Xvfb. */
public final class GuiTests {
  private static GameWindow window;
  private static int checks;
  private static final AtomicReference<Throwable> failure = new AtomicReference<>();

  public static void main(String[] args) throws Exception {
    if (GraphicsEnvironment.isHeadless())
      throw new IllegalStateException("A display is required for --gui");
    Thread.setDefaultUncaughtExceptionHandler(
        (t, e) -> {
          failure.compareAndSet(null, e);
          e.printStackTrace();
        });
    try {
      edt(
          () -> {
            window = new GameWindow();
          });
      edt(() -> snapshot("setup.png"));
      edt(
          () -> {
            window.setSize(900, 680);
          });
      edt(() -> snapshot("setup-small.png"));
      edt(
          () -> {
            window.setSize(1120, 800);
            combo("mode").setSelectedIndex(1);
            checkbox("animate").setSelected(false);
            buttonNamed(window, "startGame").doClick();
          });
      check(game().getMode() == Game.Mode.HUMAN_VS_HUMAN, "two-player mode");
      check(!checkbox("openingKnowledge").isEnabled(), "opening option disabled for two humans");
      Robot robot = new Robot();
      robot.setAutoDelay(30);
      robot.keyPress(KeyEvent.VK_1);
      robot.keyRelease(KeyEvent.VK_1);
      await(
          () -> game().getBoard().getMoveCount() == 1, 2000, "physical number key plays one disc");
      boolean physicalNumpad = true;
      try {
        if (!Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_NUM_LOCK)) {
          robot.keyPress(KeyEvent.VK_NUM_LOCK);
          robot.keyRelease(KeyEvent.VK_NUM_LOCK);
        }
      } catch (UnsupportedOperationException unavailable) {
        physicalNumpad = false;
      }
      if (physicalNumpad) {
        robot.keyPress(KeyEvent.VK_NUMPAD2);
        robot.keyRelease(KeyEvent.VK_NUMPAD2);
      } else edt(() -> key("num1"));
      await(
          () -> game().getBoard().getMoveCount() == 2,
          2000,
          "numpad press and typed event do not duplicate a move");
      for (int col : new int[] {0, 1, 0, 1, 0}) edt(() -> key("col" + col));
      check(game().getWinner() == 'X', "human game victory");
      check(label("redScore").getText().equals("1"), "score counted once");
      edt(() -> snapshot("game-win.png"));
      edt(() -> key("undo"));
      check(
          !game().isGameOver() && label("redScore").getText().equals("0"),
          "undo terminal move and score");
      edt(
          () -> {
            acceptNextConfirmation();
            key("setup");
          });
      edt(
          () -> {
            combo("mode").setSelectedIndex(0);
            combo("preset").setSelectedIndex(5);
            check(
                checkbox("openingKnowledge").isSelected(), "maximum preset enables exact openings");
            checkbox("openingKnowledge").setSelected(false);
            checkbox("animate").setSelected(false);
            buttonNamed(window, "startGame").doClick();
            key("col3");
          });
      check(game().getBoard().getMoveCount() == 1, "human move submitted before AI");
      // Event thread must remain available while a 42-ply search is running.
      long start = System.nanoTime();
      edt(() -> {});
      check((System.nanoTime() - start) / 1_000_000 < 1000, "EDT responsive during search");
      edt(() -> key("undo"));
      Thread.sleep(350);
      check(game().getBoard().getMoveCount() == 0, "cancelled worker cannot play stale move");
      edt(() -> key("col3"));
      edt(() -> button("stop").doClick());
      await(
          () -> game().getBoard().getMoveCount() == 2,
          4000,
          "manual stop plays safe last completed result");
      edt(() -> snapshot("game-ai.png"));
      edt(
          () -> {
            acceptNextConfirmation();
            key("setup");
          });
      edt(
          () -> {
            combo("preset").setSelectedIndex(0);
            combo("starter").setSelectedIndex(1);
            checkbox("animate").setSelected(true);
            buttonNamed(window, "startGame").doClick();
          });
      await(() -> game().getBoard().getMoveCount() == 1, 4000, "AI can start");
      await(
          () -> !((Puissance4.gui.BoardPanel) field("boardPanel")).isAnimating(),
          2000,
          "first drop completes");
      edt(() -> key("col2"));
      await(
          () -> game().getBoard().getMoveCount() == 3, 5000, "animation and AI response complete");
      edt(() -> snapshot("game-animation.png"));
      edt(
          () -> {
            window.setSize(900, 680);
          });
      edt(() -> snapshot("game-small.png"));
      edt(
          () -> {
            acceptNextConfirmation();
            key("setup");
          });
      edt(
          () -> {
            combo("preset").setSelectedIndex(5);
            combo("starter").setSelectedIndex(1);
            checkbox("animate").setSelected(false);
            buttonNamed(window, "startGame").doClick();
          });
      await(
          () -> game().getBoard().getMoveCount() == 1 && !game().isAITurn(),
          4000,
          "exact book plays the AI opening");
      edt(
          () -> {
            check(
                game().getBoard().getPiece(5, 3) == 'O', "yellow AI chooses exact central opening");
            check(
                label("depthInfo").getText().equals("Ouverture exacte"),
                "opening source displayed clearly");
            check(
                label("cacheInfo").getText().startsWith("Cache : 0,0"),
                "opening does not allocate search cache");
            snapshot("game-book-small.png");
            window.setSize(1120, 800);
            snapshot("game-book.png");
          });
      int played = 1;
      for (int col : new int[] {3, 2, 4}) {
        edt(() -> key("col" + col));
        int expected = played + 2;
        await(
            () -> game().getBoard().getMoveCount() == expected && !game().isAITurn(),
            4000,
            "exact opening replies complete");
        played = expected;
      }
      edt(() -> snapshot("game-demo.png"));
      if (failure.get() != null) throw new AssertionError("EDT exception", failure.get());
      System.out.println(
          "OK - " + checks + " GUI checks (modes, score, undo, cancellation, stop, animation)");
    } finally {
      if (window != null)
        edt(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
    }
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  private static Object field(String name) {
    try {
      Field f = GameWindow.class.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(window);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Game game() {
    return (Game) field("game");
  }

  private static JComboBox<?> combo(String name) {
    return (JComboBox<?>) field(name);
  }

  private static JCheckBox checkbox(String name) {
    return (JCheckBox) field(name);
  }

  private static JLabel label(String name) {
    return (JLabel) field(name);
  }

  private static JButton button(String name) {
    return (JButton) field(name);
  }

  private static void edt(Runnable action) throws Exception {
    SwingUtilities.invokeAndWait(action);
  }

  private static void key(String name) {
    window
        .getRootPane()
        .getActionMap()
        .get(name)
        .actionPerformed(new ActionEvent(window, ActionEvent.ACTION_PERFORMED, name));
  }

  private static JButton buttonNamed(Container root, String name) {
    for (Component c : root.getComponents()) {
      if (c instanceof JButton b && name.equals(b.getName())) return b;
      if (c instanceof Container child) {
        JButton b = buttonNamed(child, name);
        if (b != null) return b;
      }
    }
    return null;
  }

  private static void acceptNextConfirmation() {
    Timer t =
        new Timer(
            100,
            e -> {
              for (Window w : Window.getWindows())
                if (w instanceof JDialog d && d.isVisible()) {
                  JButton primary = buttonNamed(d, "dialogConfirm");
                  if (primary != null) primary.doClick();
                }
            });
    t.setRepeats(false);
    t.start();
  }

  private static void await(BooleanSupplier condition, long timeout, String message)
      throws Exception {
    long end = System.nanoTime() + timeout * 1_000_000;
    while (System.nanoTime() < end) {
      AtomicReference<Boolean> done = new AtomicReference<>();
      edt(() -> done.set(condition.getAsBoolean()));
      if (done.get()) {
        check(true, message);
        return;
      }
      if (failure.get() != null) throw new AssertionError("EDT exception", failure.get());
      Thread.sleep(20);
    }
    check(false, message);
  }

  private static void snapshot(String name) {
    window.validate();
    try {
      Path dir = Path.of("build", "screenshots");
      Files.createDirectories(dir);
      BufferedImage image =
          new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      window.paintAll(g);
      g.dispose();
      ImageIO.write(image, "png", dir.resolve(name).toFile());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
