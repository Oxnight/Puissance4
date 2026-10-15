package Puissance4.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;

/** Atelier window chrome, including real close, minimize, maximize and edge resizing. */
final class AtelierShell extends JPanel {
  private static final long serialVersionUID = 1L;
  private final JFrame owner;
  private final AWTEventListener resizeListener;
  private Rectangle restored, resizeBounds;
  private Point dragOrigin, resizeOrigin;
  private int resizeCursor;
  private boolean manuallySized;

  AtelierShell(JFrame owner, JComponent content, Runnable close) {
    super(new BorderLayout());
    this.owner = owner;
    setOpaque(false);
    JPanel bar = Theme.panel(new BorderLayout());
    bar.setPreferredSize(new Dimension(0, 43));
    bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
    JPanel traffic = Theme.panel(new FlowLayout(FlowLayout.LEFT, 8, 15));
    traffic.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
    traffic.setPreferredSize(new Dimension(125, 43));
    traffic.add(traffic("Fermer", new Color(237, 108, 93), close, "closeWindow"));
    traffic.add(
        traffic(
            "Réduire", new Color(241, 191, 79), () -> owner.setState(Frame.ICONIFIED), "minimize"));
    traffic.add(traffic("Agrandir", new Color(96, 191, 105), this::maximize, "maximize"));
    JLabel title = Theme.label("Puissance 4", 12, Theme.MUTED, false);
    title.setHorizontalAlignment(SwingConstants.CENTER);
    JPanel spacer = Theme.panel(null);
    spacer.setPreferredSize(new Dimension(125, 43));
    bar.add(traffic, BorderLayout.WEST);
    bar.add(title);
    bar.add(spacer, BorderLayout.EAST);
    MouseAdapter drag =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)
                && cursorAt(
                        SwingUtilities.convertPoint(
                            (Component) e.getSource(), e.getPoint(), AtelierShell.this))
                    == Cursor.DEFAULT_CURSOR)
              dragOrigin =
                  new Point(e.getXOnScreen() - owner.getX(), e.getYOnScreen() - owner.getY());
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            if (dragOrigin != null && restored == null)
              owner.setLocation(e.getXOnScreen() - dragOrigin.x, e.getYOnScreen() - dragOrigin.y);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            dragOrigin = null;
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) maximize();
          }
        };
    bar.addMouseListener(drag);
    bar.addMouseMotionListener(drag);
    title.addMouseListener(drag);
    title.addMouseMotionListener(drag);
    add(bar, BorderLayout.NORTH);
    add(content, BorderLayout.CENTER);
    resizeListener =
        e -> {
          if (!(e instanceof MouseEvent m)
              || !(m.getSource() instanceof Component c)
              || (c instanceof Window w ? w : SwingUtilities.getWindowAncestor(c)) != owner) return;
          Point p = SwingUtilities.convertPoint(c, m.getPoint(), this);
          if (m.getID() == MouseEvent.MOUSE_MOVED && restored == null) {
            int cursor = cursorAt(p);
            if (cursor != Cursor.DEFAULT_CURSOR || resizeCursor != Cursor.DEFAULT_CURSOR)
              owner.setCursor(Cursor.getPredefinedCursor(cursor));
            resizeCursor = cursor;
          } else if (m.getID() == MouseEvent.MOUSE_PRESSED
              && SwingUtilities.isLeftMouseButton(m)
              && restored == null) {
            resizeCursor = cursorAt(p);
            if (resizeCursor != Cursor.DEFAULT_CURSOR) {
              resizeOrigin = m.getLocationOnScreen();
              resizeBounds = owner.getBounds();
            }
          } else if (m.getID() == MouseEvent.MOUSE_DRAGGED && resizeOrigin != null) {
            int dx = m.getXOnScreen() - resizeOrigin.x, dy = m.getYOnScreen() - resizeOrigin.y;
            Rectangle b = new Rectangle(resizeBounds);
            boolean left =
                resizeCursor == Cursor.W_RESIZE_CURSOR
                    || resizeCursor == Cursor.NW_RESIZE_CURSOR
                    || resizeCursor == Cursor.SW_RESIZE_CURSOR;
            boolean right =
                resizeCursor == Cursor.E_RESIZE_CURSOR
                    || resizeCursor == Cursor.NE_RESIZE_CURSOR
                    || resizeCursor == Cursor.SE_RESIZE_CURSOR;
            boolean top =
                resizeCursor == Cursor.N_RESIZE_CURSOR
                    || resizeCursor == Cursor.NW_RESIZE_CURSOR
                    || resizeCursor == Cursor.NE_RESIZE_CURSOR;
            boolean bottom =
                resizeCursor == Cursor.S_RESIZE_CURSOR
                    || resizeCursor == Cursor.SW_RESIZE_CURSOR
                    || resizeCursor == Cursor.SE_RESIZE_CURSOR;
            if (left) {
              int w = Math.max(owner.getMinimumSize().width, b.width - dx);
              b.x += b.width - w;
              b.width = w;
            }
            if (right) b.width = Math.max(owner.getMinimumSize().width, b.width + dx);
            if (top) {
              int h = Math.max(owner.getMinimumSize().height, b.height - dy);
              b.y += b.height - h;
              b.height = h;
            }
            if (bottom) b.height = Math.max(owner.getMinimumSize().height, b.height + dy);
            manuallySized = true;
            owner.setBounds(b);
          } else if (m.getID() == MouseEvent.MOUSE_RELEASED) {
            resizeOrigin = null;
            resizeBounds = null;
          }
        };
    Toolkit.getDefaultToolkit()
        .addAWTEventListener(
            resizeListener, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    owner.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            shape();
          }
        });
  }

  private JButton traffic(String text, Color color, Runnable action, String name) {
    JButton b =
        new JButton() {
          private static final long serialVersionUID = 1L;

          @Override
          protected void paintComponent(Graphics source) {
            Graphics2D g = Theme.graphics(source);
            g.setColor(color);
            g.fillOval(0, 0, 12, 12);
            if (getModel().isRollover() || isFocusOwner() && Theme.keyboardFocus) {
              g.setColor(new Color(40, 40, 35, 140));
              g.setStroke(new BasicStroke(1.2f));
              if ("closeWindow".equals(name)) {
                g.drawLine(4, 4, 8, 8);
                g.drawLine(4, 8, 8, 4);
              } else if ("minimize".equals(name)) g.drawLine(3, 6, 9, 6);
              else g.drawRect(4, 4, 4, 4);
            }
            g.dispose();
          }
        };
    b.setName(name);
    b.getAccessibleContext().setAccessibleName(text + " la fenêtre");
    b.setToolTipText(text);
    b.setPreferredSize(new Dimension(12, 12));
    b.setBorder(null);
    b.setContentAreaFilled(false);
    b.setFocusPainted(false);
    b.addActionListener(e -> action.run());
    return b;
  }

  private int cursorAt(Point p) {
    boolean l = p.x < 6, r = p.x >= getWidth() - 6, t = p.y < 6, b = p.y >= getHeight() - 6;
    if (t && l) return Cursor.NW_RESIZE_CURSOR;
    if (t && r) return Cursor.NE_RESIZE_CURSOR;
    if (b && l) return Cursor.SW_RESIZE_CURSOR;
    if (b && r) return Cursor.SE_RESIZE_CURSOR;
    if (l) return Cursor.W_RESIZE_CURSOR;
    if (r) return Cursor.E_RESIZE_CURSOR;
    if (t) return Cursor.N_RESIZE_CURSOR;
    if (b) return Cursor.S_RESIZE_CURSOR;
    return Cursor.DEFAULT_CURSOR;
  }

  private void maximize() {
    if (restored != null) {
      Rectangle b = restored;
      restored = null;
      owner.setBounds(b);
    } else {
      restored = owner.getBounds();
      Rectangle b = owner.getGraphicsConfiguration().getBounds();
      Insets i = Toolkit.getDefaultToolkit().getScreenInsets(owner.getGraphicsConfiguration());
      owner.setBounds(
          b.x + i.left, b.y + i.top, b.width - i.left - i.right, b.height - i.top - i.bottom);
    }
  }

  boolean canAutoFit() {
    return restored == null && !manuallySized;
  }

  void shape() {
    if (owner
        .getGraphicsConfiguration()
        .getDevice()
        .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT))
      try {
        owner.setShape(
            new RoundRectangle2D.Double(0, 0, owner.getWidth(), owner.getHeight(), 18, 18));
      } catch (UnsupportedOperationException ignored) {
      }
  }

  void shutdown() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(resizeListener);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = Theme.graphics(graphics);
    g.setColor(Theme.BG);
    g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
    g.dispose();
    super.paintComponent(graphics);
  }
}
