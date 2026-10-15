package Puissance4;

import Puissance4.gui.BoardPanel;
import Puissance4.gui.GameWindow;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Acceptance flows across every Atelier screen and modal, in both appearances. */
public final class AtelierTests {
  private static GameWindow window;
  private static int checks;
  private static final AtomicReference<Throwable> failure = new AtomicReference<>();
  private static final String appearance = System.getProperty("puissance4.appearance", "light");

  public static void main(String[] args) throws Exception {
    Thread.setDefaultUncaughtExceptionHandler(
        (t, e) -> {
          failure.compareAndSet(null, e);
          e.printStackTrace();
        });
    try {
      edt(() -> window = new GameWindow());
      edt(
          () -> {
            check(window.isUndecorated(), "Atelier title bar");
            check(window.getWidth() == 1016, "reference window width");
            check(
                named(window, "closeWindow") != null && named(window, "maximize") != null,
                "working window controls");
            check(
                palette("BG")
                    .equals(Color.decode("dark".equals(appearance) ? "#252522" : "#f6f3ed")),
                "exact Atelier background");
            check(
                palette("GREEN")
                    .equals(Color.decode("dark".equals(appearance) ? "#abc7a8" : "#345744")),
                "exact Atelier action color");
            check(component(window, "modeSelector") != null, "visible mode selector");
            snapshot(window, "configuration");
            named(window, "advanced").doClick();
            check(((JSpinner) field("depth")).isShowing(), "advanced depth visible");
            snapshot(window, "reglages-avances");
          });
      edt(
          () -> {
            JTextField depth =
                ((JSpinner.DefaultEditor) ((JSpinner) field("depth")).getEditor()).getTextField();
            depth.setText("invalide");
            answer("dialogConfirm", "saisie-invalide");
            named(window, "startGame").doClick();
            check(field("game") == null, "invalid value cannot start a solo game");
            check(
                Boolean.TRUE.equals(
                    ((JSpinner) field("depth")).getClientProperty("Atelier.invalid")),
                "invalid field identified");
            ((JButton) ((JPanel) component(window, "modeSelector")).getComponent(1)).doClick();
            check(!((JSpinner) field("depth")).isShowing(), "AI fields hidden with two players");
            snapshot(window, "configuration-deux-joueurs");
            checkbox("animate").setSelected(false);
            named(window, "startGame").doClick();
            check(
                game().getMode() == Game.Mode.HUMAN_VS_HUMAN,
                "local two-player start ignores hidden AI text");
            check(!((JPanel) field("analysis")).isVisible(), "AI analysis absent in local mode");
            window.validate();
            check(
                label("redName")
                        .getFontMetrics(label("redName").getFont())
                        .stringWidth(label("redName").getText())
                    <= label("redName").getWidth(),
                "local score name fits");
            check(!button("stop").isVisible(), "stop button absent without search");
          });
      Robot robot = new Robot();
      robot.setAutoDelay(25);
      AtomicReference<Point> center = new AtomicReference<>();
      edt(
          () -> {
            BoardPanel b = (BoardPanel) field("boardPanel");
            Point p = b.getLocationOnScreen();
            center.set(new Point(p.x + b.getWidth() / 2, p.y + 220));
          });
      robot.mouseMove(center.get().x, center.get().y);
      robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
      robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
      await(() -> game().getBoard().getMoveCount() == 1, 2000, "real grid click");
      edt(
          () -> {
            check(game().getLastMove().column() == 3, "click maps to central column");
            for (int i = 0; i < 6; i++) key("col0");
            int count = game().getBoard().getMoveCount();
            key("col0");
            check(game().getBoard().getMoveCount() == count, "full column rejects additional disc");
            check(label("subtitle").getText().contains("pleine"), "full column feedback");
            snapshot(window, "colonne-pleine");
            answer("dialogCancel", "recommencer");
            key("restart");
            check(game().getBoard().getMoveCount() == count, "cancel restart preserves board");
            answer("dialogConfirm", null);
            key("restart");
            check(game().getBoard().getMoveCount() == 0, "confirmed restart clears board");
            check(game().getCurrentPlayer() == 'O', "starter alternates");
            key("col3");
            answer("dialogConfirm", "regles");
            named(window, "rules").doClick();
            check(game().getBoard().getMoveCount() == 1, "rules preserve live game");
            answer("dialogCancel", "quitter");
            key("setup");
            check((Boolean) field("playing"), "cancel exit preserves game");
            answer("dialogConfirm", null);
            key("setup");
            check(!(Boolean) field("playing"), "confirmed exit returns to setup");
            named(window, "startGame").doClick();
            for (char c : "2131475".toCharArray()) key("col" + (c - '1'));
            check(game().getWinner() == 'X', "legal four-disc victory");
            check("1".equals(label("redScore").getText()), "victory score counted");
            snapshot(window, "victoire");
            key("undo");
            check(
                game().getWinner() == ' ' && "0".equals(label("redScore").getText()),
                "undo restores score and game");
            answer("dialogConfirm", null);
            key("restart");
            for (char c : "444444333333755555522222266666611111177777".toCharArray())
              key("col" + (c - '1'));
            check(game().getBoard().getMoveCount() == 42, "all draw fixture moves accepted");
            check(game().isGameOver() && game().getWinner() == ' ', "full grid draw");
            check("1".equals(label("draws").getText()), "draw score");
            snapshot(window, "match-nul");
            key("restart");
            for (char c : "1251553645171536".toCharArray()) key("col" + (c - '1'));
            check(game().getBoard().getMoveCount() == 16, "reference mid-game fixture");
            snapshot(window, "partie");
            window.setSize(900, 680);
            snapshot(window, "petite-fenetre");
            window.validate();
            BoardPanel compactBoard = (BoardPanel) field("boardPanel");
            check(
                compactBoard.getWidth() >= 350
                    && compactBoard.getY() + compactBoard.getHeight()
                        <= compactBoard.getParent().getHeight(),
                "complete board remains visible at minimum size");
            JButton max = named(window, "maximize");
            Rectangle old = window.getBounds();
            max.doClick();
            check(window.getWidth() > old.width, "maximize expands window");
            max.doClick();
            check(window.getBounds().equals(old), "maximize restores previous window");
            answer("dialogConfirm", null);
            key("setup");
            combo("mode").setSelectedIndex(0);
            combo("starter").setSelectedIndex(0);
            combo("humanColor").setSelectedIndex(0);
            combo("preset").setSelectedIndex(0);
            checkbox("animate").setSelected(false);
            checkbox("openingKnowledge").setSelected(false);
            named(window, "startGame").doClick();
            // Exercise the real worker-failure recovery path, not a detached mock dialog.
            setField("searchSession", null);
            answer("dialogConfirm", "erreur-ia");
            key("col3");
          });
      await(
          () -> game().getBoard().getMoveCount() == 2 && !game().isAITurn(),
          4000,
          "AI failure retains a legal playable game");
      edt(
          () -> {
            check(!button("stop").isVisible(), "stop hidden after error recovery");
            check(
                label("yellowName")
                        .getFontMetrics(label("yellowName").getFont())
                        .stringWidth(label("yellowName").getText())
                    <= label("yellowName").getWidth(),
                "AI score name fits");
            snapshot(window, "reprise-apres-erreur");
            answer("dialogConfirm", null);
            key("setup");
            combo("preset").setSelectedIndex(5);
            checkbox("openingKnowledge").setSelected(false);
            checkbox("animate").setSelected(false);
            named(window, "startGame").doClick();
            key("col3");
            check(button("stop").isVisible(), "manual stop shown only during a real search");
            snapshot(window, "ia-reflechit");
            window.setSize(900, 680);
            window.validate();
            JPanel rail = (JPanel) field("actions");
            check(
                rail.getY() + rail.getHeight() <= rail.getParent().getHeight(),
                "manual stop stays visible in compact game");
            snapshot(window, "ia-reflechit-petite-fenetre");
            key("undo");
            check(game().getBoard().getMoveCount() == 0, "undo cancels AI search");
            check(!button("stop").isVisible(), "cancelled search hides stop");
          });
      if (failure.get() != null) throw new AssertionError("EDT exception", failure.get());
      System.out.println(
          "OK - " + checks + " Atelier checks (" + appearance + ", all screens and five dialogs)");
    } finally {
      if (window != null)
        edt(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
    }
  }

  private static Object field(String name) {
    try {
      Field f = GameWindow.class.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(window);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static void setField(String name, Object value) {
    try {
      Field f = GameWindow.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(window, value);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static Color palette(String name) {
    try {
      Field f = Class.forName("Puissance4.gui.Theme").getDeclaredField(name);
      f.setAccessible(true);
      return (Color) f.get(null);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
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

  private static JButton button(String name) {
    return (JButton) field(name);
  }

  private static JLabel label(String name) {
    return (JLabel) field(name);
  }

  private static Component component(Container parent, String name) {
    for (Component c : parent.getComponents()) {
      if (name.equals(c.getName())) return c;
      if (c instanceof Container child) {
        Component result = component(child, name);
        if (result != null) return result;
      }
    }
    return null;
  }

  private static JButton named(Container parent, String name) {
    for (Component c : parent.getComponents()) {
      if (c instanceof JButton b && name.equals(b.getName())) return b;
      if (c instanceof Container child) {
        JButton found = named(child, name);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static void key(String action) {
    window
        .getRootPane()
        .getActionMap()
        .get(action)
        .actionPerformed(new ActionEvent(window, 0, action));
  }

  private static void answer(String button, String screenshot) {
    long until = System.nanoTime() + 4_000_000_000L;
    Timer t = new Timer(30, null);
    t.addActionListener(
        e -> {
          for (Window w : Window.getWindows())
            if (w instanceof JDialog d && d.isVisible()) {
              try {
                check(d.getWidth() == window.getWidth(), "modal uses owner bounds");
                if (screenshot != null) snapshot(d, screenshot);
                JButton b = named(d, button);
                check(b != null, "styled dialog action");
                t.stop();
                b.doClick();
                return;
              } catch (Throwable problem) {
                failure.compareAndSet(null, problem);
                t.stop();
                d.dispose();
                return;
              }
            }
          if (System.nanoTime() > until) {
            failure.compareAndSet(null, new AssertionError("expected dialog"));
            t.stop();
          }
        });
    t.start();
  }

  private static void snapshot(Window w, String name) {
    w.validate();
    try {
      Path dir = Path.of("build", "screenshots", "atelier", appearance);
      Files.createDirectories(dir);
      BufferedImage image =
          new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      w.paintAll(g);
      g.dispose();
      ImageIO.write(image, "png", dir.resolve(name + ".png").toFile());
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
  }

  private static void edt(Runnable task) throws Exception {
    SwingUtilities.invokeAndWait(task);
  }

  private static void check(boolean pass, String message) {
    checks++;
    if (!pass) throw new AssertionError(message);
  }

  private static void await(BooleanSupplier ready, long millis, String message) throws Exception {
    long end = System.nanoTime() + millis * 1_000_000;
    while (System.nanoTime() < end) {
      AtomicReference<Boolean> pass = new AtomicReference<>();
      edt(() -> pass.set(ready.getAsBoolean()));
      if (pass.get()) {
        check(true, message);
        return;
      }
      if (failure.get() != null) throw new AssertionError(failure.get());
      Thread.sleep(20);
    }
    check(false, message);
  }
}
