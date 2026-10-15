package Puissance4.gui;

import Puissance4.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Keyboard navigation, window sizing and numeric input regression tests. */
public final class InteractionAuditTests {
  private static GameWindow window;
  private static int checks;
  private static final List<String> defects = new ArrayList<>();
  private static final boolean record = Boolean.getBoolean("puissance4.audit.record");

  public static void main(String[] args) throws Exception {
    try {
      edt(() -> window = new GameWindow());
      edt(
          () -> {
            Rectangle screen = window.getGraphicsConfiguration().getBounds();
            Insets in =
                Toolkit.getDefaultToolkit().getScreenInsets(window.getGraphicsConfiguration());
            Rectangle usable =
                new Rectangle(
                    screen.x + in.left,
                    screen.y + in.top,
                    screen.width - in.left - in.right,
                    screen.height - in.top - in.bottom);
            check(usable.contains(window.getBounds()), "startup window fits the visible desktop");
          });
      if (args.length > 0) {
        finish();
        return;
      }
      Robot robot = new Robot();
      robot.setAutoDelay(30);
      edt(
          () -> {
            named("advanced").doClick();
            JFormattedTextField text =
                ((JSpinner.DefaultEditor) ((JSpinner) field("depth")).getEditor()).getTextField();
            text.setText("");
            text.requestFocusInWindow();
          });
      robot.keyPress(KeyEvent.VK_7);
      robot.keyRelease(KeyEvent.VK_7);
      robot.keyPress(KeyEvent.VK_ENTER);
      robot.keyRelease(KeyEvent.VK_ENTER);
      robot.waitForIdle();
      edt(
          () ->
              check(
                  ((Number) ((JSpinner) field("depth")).getValue()).intValue() == 7,
                  "number shortcuts do not swallow settings input"));
      edt(
          () -> {
            JSpinner d = (JSpinner) field("depth"), t = (JSpinner) field("seconds");
            check(rejected(d, "7abc"), "partial integer text rejected");
            check(rejected(d, "7,5"), "fractional depth rejected");
            JFormattedTextField tf = ((JSpinner.DefaultEditor) t.getEditor()).getTextField();
            tf.setText("1,5");
            try {
              t.commitEdit();
            } catch (java.text.ParseException e) {
              throw new AssertionError(e);
            }
            check(((Number) t.getValue()).doubleValue() == 1.5, "French decimal comma accepted");
            tf.setText("1.75");
            try {
              t.commitEdit();
            } catch (java.text.ParseException e) {
              throw new AssertionError(e);
            }
            check(((Number) t.getValue()).doubleValue() == 1.75, "decimal point also accepted");
            check(rejected(t, "2s"), "partial time text rejected");
            combo("preset").setSelectedIndex(6);
            d.setValue(10);
            ((JSpinner.DefaultEditor) d.getEditor()).getTextField().setText("invalide");
            combo("preset").setSelectedIndex(2);
            check(
                "10".equals(((JSpinner.DefaultEditor) d.getEditor()).getTextField().getText()),
                "preset restores invalid editor even if model value is unchanged");
          });
      edt(
          () -> {
            combo("preset").requestFocusInWindow();
            combo("preset").showPopup();
          });
      robot.waitForIdle();
      robot.keyPress(KeyEvent.VK_HOME);
      robot.keyRelease(KeyEvent.VK_HOME);
      robot.keyPress(KeyEvent.VK_ENTER);
      robot.keyRelease(KeyEvent.VK_ENTER);
      robot.waitForIdle();
      edt(
          () ->
              check(
                  combo("preset").getSelectedIndex() == 0 && !combo("preset").isPopupVisible(),
                  "styled dropdown remains keyboard-selectable"));
      edt(
          () -> {
            combo("mode").setSelectedIndex(1);
            checkbox("animate").setSelected(false);
            check(!Theme.motion, "animation checkbox synchronizes all animations");
            named("startGame").requestFocusInWindow();
          });
      robot.waitForIdle();
      robot.keyPress(KeyEvent.VK_ENTER);
      robot.keyRelease(KeyEvent.VK_ENTER);
      robot.waitForIdle();
      edt(
          () -> {
            boolean started = (Boolean) field("playing");
            check(started, "Enter activates focused start button");
            if (!started) named("startGame").doClick();
            // A focused navigation button must activate rather than silently drop a disc.
            named("settings").requestFocusInWindow();
          });
      robot.waitForIdle();
      robot.keyPress(KeyEvent.VK_ENTER);
      robot.keyRelease(KeyEvent.VK_ENTER);
      robot.waitForIdle();
      edt(
          () -> {
            boolean returned = !(Boolean) field("playing");
            check(returned, "Enter on settings activates settings, not a board move");
            if (!returned) {
              ((Game) field("game")).undo();
              key("setup");
            }
            combo("mode").setSelectedIndex(0);
            if ((Boolean) field("advanced")) named("advanced").doClick();
            window.setSize(960, 727);
            window.validate();
          });
      robot.waitForIdle();
      AtomicReference<Point> corner = new AtomicReference<>();
      edt(
          () ->
              corner.set(
                  new Point(
                      window.getX() + window.getWidth() / 2,
                      window.getY() + window.getHeight() - 3)));
      Point p = corner.get();
      robot.mouseMove(p.x, p.y);
      robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
      for (int i = 1; i <= 6; i++) robot.mouseMove(p.x + i * 6, p.y + i * 15);
      robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
      robot.waitForIdle();
      edt(
          () -> {
            Dimension chosen = window.getSize();
            check(chosen.height > 727, "edge dragging actually resizes the native window");
            named("advanced").doClick();
            check(
                window.getSize().equals(chosen),
                "advanced settings preserve size selected by edge dragging");
          });
      finish();
    } finally {
      if (window != null)
        edt(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
    }
  }

  private static boolean rejected(JSpinner spinner, String value) {
    ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(value);
    try {
      spinner.commitEdit();
      return false;
    } catch (java.text.ParseException expected) {
      return true;
    }
  }

  private static void finish() {
    for (String d : defects) System.out.println("DEFECT " + d);
    if (!record && !defects.isEmpty()) throw new AssertionError(defects.toString());
    System.out.println("Interaction audit: " + checks + " checks, " + defects.size() + " defects");
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
    if (!ok) defects.add(description);
  }

  private static void edt(Runnable r) throws Exception {
    SwingUtilities.invokeAndWait(r);
  }
}
