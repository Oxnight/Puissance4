package Puissance4.gui;

import Puissance4.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.function.IntConsumer;
import javax.accessibility.AccessibleContext;
import javax.swing.*;

/** Physical Atelier board, vector painted at the actual window scale. EDT only. */
public final class BoardPanel extends JComponent {
  private static final long serialVersionUID = 1L;
  private Game game;
  private final IntConsumer play;
  private boolean enabledInput, decorative;
  private int hover = 3;
  private Game.Move falling;
  private double progress, tile, gx, gy;
  private Timer animation;
  private List<int[]> winning = List.of();

  public BoardPanel(IntConsumer play) {
    this.play = play;
    setOpaque(false);
    setPreferredSize(new Dimension(500, 490));
    setMinimumSize(new Dimension(300, 310));
    setFocusable(true);
    getAccessibleContext().setAccessibleName("Grille de Puissance 4, sept colonnes et six lignes");
    setToolTipText("Cliquez sur une colonne, ou utilisez les touches 1 à 7.");
    addMouseMotionListener(
        new MouseMotionAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            int next = columnAt(e.getX());
            if (next != hover) {
              hover = next;
              repaint();
            }
          }
        });
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseExited(MouseEvent e) {
            hover = -1;
            repaint();
          }

          @Override
          public void mousePressed(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            requestFocusInWindow();
            int col = columnAt(e.getX());
            if (enabledInput && col >= 0 && e.getY() >= gy - tile && e.getY() <= gy + tile * 6)
              play.accept(col);
          }
        });
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null)
      accessibleContext =
          new AccessibleJComponent() {
            private static final long serialVersionUID = 1L;
          };
    return accessibleContext;
  }

  void setDecorative() {
    decorative = true;
    setFocusable(false);
    setToolTipText(null);
  }

  public void setGame(Game game) {
    stopAnimation();
    this.game = game;
    winning = List.of();
    hover = 3;
    repaint();
  }

  public void setInputEnabled(boolean enabled) {
    enabledInput = enabled;
    setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    repaint();
  }

  public void moveSelection(int direction) {
    hover = Math.floorMod((hover < 0 ? 3 : hover) + direction, 7);
    repaint();
  }

  public int selectedColumn() {
    return hover < 0 ? 3 : hover;
  }

  public boolean isAnimating() {
    return animation != null;
  }

  public void stopAnimation() {
    if (animation != null) animation.stop();
    animation = null;
    falling = null;
  }

  public void shutdown() {
    stopAnimation();
  }

  public void refresh() {
    winning =
        game != null && game.getWinner() != ' '
            ? game.getBoard().winningCells(game.getWinner())
            : List.of();
    repaint();
  }

  public void animate(Game.Move move, Runnable done, boolean enabled) {
    stopAnimation();
    if (!enabled || !Theme.motion) {
      repaint();
      done.run();
      return;
    }
    falling = move;
    progress = 0;
    long start = System.nanoTime();
    animation =
        new Timer(
            16,
            e -> {
              progress = Math.min(1, (System.nanoTime() - start) / 530_000_000.0);
              repaint();
              if (progress >= 1) {
                stopAnimation();
                repaint();
                done.run();
              }
            });
    animation.start();
  }

  private int columnAt(double x) {
    return tile <= 0 || x < gx || x >= gx + tile * 7 ? -1 : (int) ((x - gx) / tile);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = Theme.graphics(graphics);
    tile = Math.min((getWidth() - 24) / 7.0, (getHeight() - (decorative ? 32 : 96)) / 6.0);
    if (tile <= 0) {
      g.dispose();
      return;
    }
    gx = (getWidth() - tile * 7) / 2;
    gy = decorative ? 12 : 70;
    double w = tile * 7, h = tile * 6, radius = tile * .365;
    Board b = game == null ? null : game.getBoard();
    if (!decorative) {
      for (int c = 0; c < 7; c++) {
        double cx = gx + (c + .5) * tile;
        g.setFont(Theme.font(12, false));
        g.setColor(Theme.MUTED);
        g.drawString(Integer.toString(c + 1), (float) (cx - 3.5), (float) (gy - 55));
        if (enabledInput && hover == c && b != null && b.canPlay(c))
          disc(g, cx, gy - 30, tile * .18, game.getCurrentPlayer());
      }
    }
    g.setColor(Theme.EDGE);
    g.fill(new RoundRectangle2D.Double(gx - 8, gy - 8, w + 16, h + 22, 20, 20));
    Area plate = new Area(new RoundRectangle2D.Double(gx - 8, gy - 8, w + 16, h + 16, 20, 20));
    for (int r = 0; r < 6; r++)
      for (int c = 0; c < 7; c++) {
        double cx = gx + (c + .5) * tile, cy = gy + (r + .5) * tile;
        Shape hole = new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2);
        plate.subtract(new Area(hole));
        g.setPaint(
            new RadialGradientPaint(
                new Point2D.Double(cx, cy + 3),
                (float) (radius * 1.1),
                new float[] {0, 1},
                new Color[] {Theme.HOLE, Theme.mix(Theme.HOLE, Color.BLACK, .16)}));
        g.fill(hole);
        if (enabledInput && c == hover && b != null && r == b.getLandingRow(c)) {
          g.setComposite(AlphaComposite.SrcOver.derive(.22f));
          disc(g, cx, cy, radius, game.getCurrentPlayer());
          g.setComposite(AlphaComposite.SrcOver);
        }
        char piece = b == null ? ' ' : b.getPiece(r, c);
        if (piece != ' ' && !(falling != null && falling.row() == r && falling.column() == c))
          disc(g, cx, cy, radius, piece);
      }
    if (falling != null) {
      double ease = drop(Math.min(1, progress / .82)), target = gy + (falling.row() + .5) * tile;
      double y = gy - tile * .6 + (target - gy + tile * .6) * ease;
      if (progress > .82) y -= Math.sin((progress - .82) / .18 * Math.PI) * 8;
      disc(g, gx + (falling.column() + .5) * tile, y, radius, falling.player());
    }
    g.setColor(Theme.BLUE);
    g.fill(plate);
    g.setColor(Theme.EDGE);
    g.setStroke(new BasicStroke(1));
    for (int r = 0; r < 6; r++)
      for (int c = 0; c < 7; c++)
        g.draw(
            new Ellipse2D.Double(
                gx + (c + .5) * tile - radius,
                gy + (r + .5) * tile - radius,
                radius * 2,
                radius * 2));
    g.setColor(Theme.EDGE);
    g.fill(new RoundRectangle2D.Double(gx - 17, gy + h + 10, tile * .95, 13, 7, 7));
    g.fill(
        new RoundRectangle2D.Double(gx + w - tile * .95 + 17, gy + h + 10, tile * .95, 13, 7, 7));
    g.setColor(Theme.BLUE);
    g.fill(new RoundRectangle2D.Double(gx + tile * .1, gy + h + 8, w - tile * .2, 7, 5, 5));
    if (!decorative && falling == null && game != null && game.getLastMove() != null) {
      Game.Move last = game.getLastMove();
      g.setColor(new Color(255, 255, 255, 190));
      g.fill(
          new Ellipse2D.Double(
              gx + (last.column() + .5) * tile - 2, gy + (last.row() + .5) * tile - 2, 4, 4));
    }
    g.setColor(Color.WHITE);
    g.setStroke(new BasicStroke(Math.max(2, (float) (tile * .045))));
    for (int[] cell : winning)
      g.draw(
          new Ellipse2D.Double(
              gx + (cell[1] + .5) * tile - radius * .8,
              gy + (cell[0] + .5) * tile - radius * .8,
              radius * 1.6,
              radius * 1.6));
    g.dispose();
  }

  /** CSS cubic-bezier(.38,.01,.7,1), inverted in x for a time-linear fall. */
  private static double drop(double p) {
    double lo = 0, hi = 1, t = p;
    for (int i = 0; i < 13; i++) {
      t = (lo + hi) / 2;
      double x = 3 * (1 - t) * (1 - t) * t * .38 + 3 * (1 - t) * t * t * .7 + t * t * t;
      if (x < p) lo = t;
      else hi = t;
    }
    return 3 * (1 - t) * (1 - t) * t * .01 + 3 * (1 - t) * t * t + t * t * t;
  }

  static void disc(Graphics2D g, double cx, double cy, double r, char piece) {
    Color color = piece == 'X' ? Theme.RED : Theme.YELLOW;
    g.setPaint(
        new GradientPaint(
            (float) cx,
            (float) (cy - r),
            Theme.mix(color, Color.WHITE, .08),
            (float) cx,
            (float) (cy + r),
            color));
    g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    g.setColor(Theme.mix(color, Color.BLACK, .17));
    g.setStroke(new BasicStroke(Math.max(1, (float) (r * .07))));
    g.draw(new Arc2D.Double(cx - r * .94, cy - r * .94, r * 1.88, r * 1.88, 200, 140, Arc2D.OPEN));
    g.setColor(new Color(255, 255, 255, 46));
    g.setStroke(new BasicStroke(Math.max(1, (float) (r * .035))));
    g.draw(new Ellipse2D.Double(cx - r * .77, cy - r * .77, r * 1.54, r * 1.54));
  }
}
