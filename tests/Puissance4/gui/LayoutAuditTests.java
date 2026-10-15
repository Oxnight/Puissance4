package Puissance4.gui;

import Puissance4.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;

/** Text and layout audit across actual visible views, sizes and modal dialogs. */
public final class LayoutAuditTests {
  private static GameWindow window;
  private static final List<String> defects = new ArrayList<>();
  private static int checks;
  private static String state;
  private static final boolean record = Boolean.getBoolean("puissance4.audit.record");

  public static void main(String[] args) throws Exception {
    try {
      edt(() -> window = new GameWindow());
      for (int[] size : new int[][] {{900, 680}, {1016, 727}, {1280, 900}}) {
        edt(
            () -> {
              window.setSize(size[0], size[1]);
              window.validate();
              state = "setup-" + size[0];
              scan(window);
              named("advanced").doClick();
              window.setSize(size[0], size[1]);
              window.validate();
              state += "-advanced";
              scan(window);
              combo("mode").setSelectedIndex(1);
              window.setSize(size[0], size[1]);
              window.validate();
              state = "local-setup-" + size[0];
              scan(window);
              combo("mode").setSelectedIndex(0);
              if ((Boolean) field("advanced")) named("advanced").doClick();
            });
      }
      edt(
          () -> {
            checkbox("animate").setSelected(false);
            combo("mode").setSelectedIndex(1);
            named("startGame").doClick();
            window.validate();
            state = "game-local";
            scan(window);
            for (int[] size : new int[][] {{900, 680}, {1016, 798}, {1280, 900}}) {
              window.setSize(size[0], size[1]);
              window.validate();
              state = "local-game-" + size[0];
              scan(window);
              BoardPanel board = (BoardPanel) field("boardPanel");
              check(
                  board.getY() + board.getHeight() <= board.getParent().getHeight(),
                  "all six rows are visible");
            }
            key("col3");
            modalAudit("restart");
            key("restart");
            modalAudit("rules");
            named("rules").doClick();
            modalAudit("quit");
            key("setup");
          });
      edt(
          () -> {
            combo("mode").setSelectedIndex(0);
            combo("preset").setSelectedIndex(0);
            combo("humanColor").setSelectedIndex(1);
            combo("starter").setSelectedIndex(1);
            named("startGame").doClick();
            window.validate();
            state = "game-yellow-human";
            scan(window);
            ((JLabel) field("depthInfo")).setText("Résolution exacte : 42 coups");
            ((JLabel) field("nodeInfo")).setText("2 147 483 647 positions explorées");
            ((JLabel) field("timeInfo")).setText("300,00 s · résultat démontré");
            ((JLabel) field("cacheInfo")).setText("Cache : 256,0 / 256 Mo");
            named("analysis").doClick();
            window.validate();
            state = "analysis-long-values";
            scan(window);
            for (int[] size : new int[][] {{900, 680}, {1016, 798}, {1280, 900}}) {
              window.setSize(size[0], size[1]);
              window.validate();
              state = "analysis-" + size[0];
              scan(window);
            }
            set("roundNumber", 1000);
            invoke("updateUIState");
            window.validate();
            state = "round-1000";
            scan(window);
          });
      if (!defects.isEmpty()) {
        Path out =
            Path.of(
                "build", "audit-" + System.getProperty("puissance4.appearance", "light") + ".txt");
        Files.createDirectories(out.getParent());
        Files.write(out, defects);
        for (String defect : defects) System.out.println("DEFECT " + defect);
        if (!record) throw new AssertionError(defects.size() + " visual layout defects");
      }
      System.out.println("Layout audit: " + checks + " checks, " + defects.size() + " defects");
    } finally {
      if (window != null)
        edt(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
    }
  }

  private static void scan(Container parent) {
    for (Component c : parent.getComponents()) {
      if (!c.isShowing() || c.getWidth() == 0 || c.getHeight() == 0) continue;
      if (c instanceof JLabel l
          && l.getText() != null
          && !l.getText().isBlank()
          && !l.getText().startsWith("<html>")) {
        Insets in = l.getInsets();
        Rectangle view =
            new Rectangle(
                in.left,
                in.top,
                l.getWidth() - in.left - in.right,
                l.getHeight() - in.top - in.bottom);
        String visible =
            SwingUtilities.layoutCompoundLabel(
                l,
                l.getFontMetrics(l.getFont()),
                l.getText(),
                l.getIcon(),
                l.getVerticalAlignment(),
                l.getHorizontalAlignment(),
                l.getVerticalTextPosition(),
                l.getHorizontalTextPosition(),
                view,
                new Rectangle(),
                new Rectangle(),
                l.getIconTextGap());
        check(
            visible.equals(l.getText()),
            "clipped label '" + l.getText() + "' (" + l.getWidth() + " px)");
      }
      if (c instanceof Theme.Button b) {
        check(b.contentFont().getSize() >= 12, "button remains readable");
        FontMetrics fm = b.getFontMetrics(b.contentFont());
        int width =
            fm.stringWidth(b.getText())
                + (b.getIcon() == null
                    ? 0
                    : b.getIcon().getIconWidth()
                        + (b.getText().isEmpty() ? 0 : b.getIconTextGap()));
        check(
            width <= b.getWidth() - 2 * b.contentPadding(),
            "button lacks text padding '" + b.getText() + "' (" + width + "/" + b.getWidth() + ")");
      }
      if (c instanceof JTextArea a && !a.getText().isBlank()) {
        Dimension preferred = a.getPreferredSize();
        check(
            preferred.height <= a.getHeight(),
            "clipped paragraph '"
                + a.getText()
                + "' ("
                + preferred.height
                + "/"
                + a.getHeight()
                + ")");
      }
      if (c instanceof Container child) scan(child);
    }
  }

  private static void modalAudit(String name) {
    Timer timer = new Timer(80, null);
    timer.setRepeats(false);
    timer.addActionListener(
        e -> {
          for (Window w : Window.getWindows())
            if (w instanceof JDialog d && d.isVisible()) {
              state = "dialog-" + name;
              d.validate();
              scan(d);
              JButton button = find(d, "dialogConfirm");
              if (button != null) button.doClick();
              return;
            }
        });
    timer.start();
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

  private static void set(String name, Object value) {
    try {
      Field f = GameWindow.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(window, value);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static void invoke(String name) {
    try {
      Method m = GameWindow.class.getDeclaredMethod(name);
      m.setAccessible(true);
      m.invoke(window);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static JComboBox<?> combo(String name) {
    return (JComboBox<?>) field(name);
  }

  private static JCheckBox checkbox(String name) {
    return (JCheckBox) field(name);
  }

  private static JButton named(String name) {
    return find(window, name);
  }

  private static JButton find(Container parent, String name) {
    for (Component c : parent.getComponents()) {
      if (c instanceof JButton b && name.equals(b.getName())) return b;
      if (c instanceof Container k) {
        JButton b = find(k, name);
        if (b != null) return b;
      }
    }
    return null;
  }

  private static void key(String id) {
    window.getRootPane().getActionMap().get(id).actionPerformed(new ActionEvent(window, 0, id));
  }

  private static void check(boolean ok, String description) {
    checks++;
    if (!ok) defects.add(state + ": " + description);
  }

  private static void edt(Runnable r) throws Exception {
    SwingUtilities.invokeAndWait(r);
  }
}
