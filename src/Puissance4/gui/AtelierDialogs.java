package Puissance4.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Every modal surface uses the same Atelier card and the owner's real dimmed view. */
final class AtelierDialogs {
  private AtelierDialogs() {}

  static boolean confirm(JFrame owner, String title, String text, String action) {
    return show(owner, title, text, action, true, false);
  }

  static void message(JFrame owner, String title, String text) {
    show(owner, title, text, "J’ai compris", false, false);
  }

  static void rules(JFrame owner) {
    show(
        owner,
        "Comment jouer",
        "Pour gagner, alignez quatre pions de votre couleur.",
        "C’est parti",
        false,
        true);
  }

  private static boolean show(
      JFrame owner, String title, String text, String action, boolean confirm, boolean rules) {
    owner.validate();
    BufferedImage snapshot =
        new BufferedImage(owner.getWidth(), owner.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D image = snapshot.createGraphics();
    owner.paint(image);
    image.dispose();
    JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.DOCUMENT_MODAL);
    dialog.setUndecorated(true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    boolean[] accepted = {false};
    JPanel backdrop =
        new JPanel(null) {
          private static final long serialVersionUID = 1L;

          @Override
          protected void paintComponent(Graphics source) {
            Graphics2D g = Theme.graphics(source);
            g.drawImage(snapshot, 0, 0, null);
            g.setColor(Theme.SCRIM);
            g.fillRect(0, 43, getWidth(), getHeight() - 43);
            g.dispose();
          }
        };
    int width = rules ? 570 : 450;
    int height = rules ? 566 : (confirm ? 220 : 270);
    JPanel card = Theme.card(null);
    card.setBorder(null);
    JLabel heading = Theme.label(title, 24, Theme.TEXT, false);
    heading.setFont(Theme.heading(24));
    card.add(heading);
    heading.setBounds(28, 25, width - 90, 32);
    JButton close = Theme.button("", false);
    close.setIcon(Theme.icon("close"));
    close.setName("dialogClose");
    close.getAccessibleContext().setAccessibleName("Fermer");
    close.setBounds(width - 57, 26, 29, 29);
    close.addActionListener(e -> dialog.dispose());
    card.add(close);
    JTextArea body = Theme.paragraph(text, 13, Theme.MUTED);
    body.setBounds(28, 72, width - 56, rules ? 38 : (confirm ? 62 : 92));
    card.add(body);
    if (rules) {
      JPanel alignments = Theme.panel(new GridLayout(1, 3, 12, 0));
      String[] names = {"Horizontale", "Verticale", "Diagonale"};
      for (int k = 0; k < 3; k++) {
        final int direction = k;
        JPanel illustration =
            new JPanel() {
              private static final long serialVersionUID = 1L;

              @Override
              protected void paintComponent(Graphics source) {
                Graphics2D g = Theme.graphics(source);
                g.setColor(Theme.TINT);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                final int diameter = 12;
                final double step = 20.0;
                final double diagonalStep = step / Math.sqrt(2.0);

                for (int i = 0; i < 4; i++) {
                  double offset = i - 1.5;

                  double x =
                      getWidth() / 2.0
                          + (direction == 1 ? 0 : offset * (direction == 2 ? diagonalStep : step));

                  double y =
                      50.0
                          + (direction == 0 ? 0 : offset * (direction == 2 ? -diagonalStep : step));

                  g.setColor(Theme.RED);
                  g.fillOval(
                      (int) Math.round(x - diameter / 2.0),
                      (int) Math.round(y - diameter / 2.0),
                      diameter,
                      diameter);
                }
                g.setFont(Theme.font(11, false));
                g.setColor(Theme.MUTED);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(
                    names[direction], (getWidth() - fm.stringWidth(names[direction])) / 2, 107);
                g.dispose();
              }
            };
        illustration.setOpaque(false);
        alignments.add(illustration);
      }
      alignments.setBounds(28, 119, width - 56, 122);
      card.add(alignments);
      int nextRuleY = 259;

      nextRuleY =
          rule(
                  card,
                  1,
                  "La grille",
                  "Elle comporte 7 colonnes et 6 lignes. Chaque joueur a 21 pions.",
                  nextRuleY,
                  width)
              + 18;

      nextRuleY =
          rule(
                  card,
                  2,
                  "À tour de rôle",
                  "Choisissez qui commence. À chaque tour, placez un pion dans une colonne : il"
                      + " tombe sur la première case libre en partant du bas.",
                  nextRuleY,
                  width)
              + 18;

      nextRuleY =
          rule(
                  card,
                  3,
                  "Gagner la partie",
                  "Quatre pions alignés suffisent pour gagner. Si la grille est pleine sans"
                      + " gagnant, c’est un match nul.",
                  nextRuleY,
                  width)
              + 18;

      JTextArea note =
          Theme.paragraph(
              "Le joueur qui commence change à chaque nouvelle manche.", 11, Theme.MUTED);

      note.setBounds(28, nextRuleY, width - 56, 20);
      card.add(note);
    }
    JButton primary = Theme.button(action, true);
    primary.setName("dialogConfirm");
    primary.setBounds(width - 178, height - 68, 150, 42);
    primary.addActionListener(
        e -> {
          accepted[0] = true;
          dialog.dispose();
        });
    card.add(primary);
    if (confirm) {
      JButton cancel = Theme.button("Continuer", false);
      cancel.setName("dialogCancel");
      cancel.setBounds(width - 340, height - 68, 150, 42);
      cancel.addActionListener(e -> dialog.dispose());
      card.add(cancel);
    } else if (rules) {
      JLabel footer = Theme.label("Bonne partie !", 12, Theme.MUTED, false);
      footer.setBounds(28, height - 64, 230, 34);
      card.add(footer);
    }
    backdrop.add(card);
    dialog.setContentPane(backdrop);
    dialog.setBounds(owner.getBounds());
    int cx = (owner.getWidth() - width) / 2, cy = 43 + (owner.getHeight() - 43 - height) / 2;
    card.setBounds(cx, cy, width, height);
    dialog.getRootPane().setDefaultButton(primary);
    dialog
        .getRootPane()
        .registerKeyboardAction(
            e -> dialog.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    Timer entry = new Timer(16, null);
    if (Theme.motion) {
      long start = System.nanoTime();
      entry.addActionListener(
          e -> {
            double p = Math.min(1, (System.nanoTime() - start) / 230_000_000.0);
            card.setLocation(cx, cy + (int) ((1 - p) * (1 - p) * 12));
            if (p >= 1) entry.stop();
          });
      entry.start();
    }
    dialog.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent e) {
            entry.stop();
          }
        });
    dialog.setLocation(owner.getLocationOnScreen());
    dialog.setVisible(true);
    entry.stop();
    return accepted[0];
  }

  private static int rule(JPanel parent, int number, String title, String text, int y, int width) {
    JLabel n = Theme.label(Integer.toString(number), 12, Theme.GREEN, true);

    n.setHorizontalAlignment(SwingConstants.CENTER);
    n.setBounds(28, y, 24, 24);
    parent.add(n);

    JLabel heading = Theme.label(title, 13, Theme.TEXT, true);

    heading.setBounds(65, y - 1, width - 93, 21);
    parent.add(heading);

    JTextArea p = Theme.paragraph(text, 12, Theme.MUTED);

    int paragraphWidth = width - 96;

    p.setSize(paragraphWidth, 1000);
    int paragraphHeight = p.getPreferredSize().height;

    p.setBounds(65, y + 26, paragraphWidth, paragraphHeight);

    parent.add(p);

    return y + 26 + paragraphHeight;
  }
}
