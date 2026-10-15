package Puissance4;

import Puissance4.gui.GameWindow;
import java.awt.*;
import java.awt.event.WindowEvent;
import javax.swing.*;

public final class Main {
  private Main() {}

  public static void main(String[] args) {
    System.setProperty("apple.awt.application.name", "Puissance 4");
    if (GraphicsEnvironment.isHeadless()) {
      System.err.println("Puissance 4 nécessite un environnement graphique.");
      System.exit(1);
      return;
    }
    SwingUtilities.invokeLater(
        () -> {
          try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
          } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
          }
          GameWindow window = new GameWindow();
          if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER))
              desktop.setQuitHandler(
                  (event, response) -> {
                    SwingUtilities.invokeLater(
                        () ->
                            window.dispatchEvent(
                                new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
                    response.cancelQuit();
                  });
          }
        });
  }
}
