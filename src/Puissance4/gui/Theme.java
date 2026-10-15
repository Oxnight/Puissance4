package Puissance4.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.*;

/** Atelier's shared palette, typography and controls. */
final class Theme {
  static final boolean DARK = darkAppearance();
  static final Color BG = color("f6f3ed", "252522"),
      CARD = color("fffdf8", "30302c"),
      TINT = color("eae6dc", "3b3a33"),
      TEXT = color("272a25", "f2eee5"),
      MUTED = color("66685e", "b4b3a5"),
      LINE = color("dfdbd0", "46463e"),
      GREEN = color("345744", "abc7a8"),
      ON_GREEN = color("fffdf8", "18271b"),
      BLUE = color("2e61bd", "386ec9"),
      EDGE = color("234d9b", "2757a7"),
      HOLE = color("d0d9e2", "1b2532"),
      RED = hex("e75048"),
      YELLOW = hex("f3c642"),
      ERROR = color("ac2e28", "ff9a91");
  static final Color SCRIM = DARK ? new Color(9, 14, 27, 179) : new Color(28, 36, 36, 77);
  static final Color SHADOW = DARK ? new Color(0, 0, 0, 66) : new Color(38, 41, 29, 25);
  static final boolean REDUCED =
      Boolean.getBoolean("puissance4.reducedMotion")
          || "1".equals(macDefault("com.apple.universalaccess", "reduceMotion"));
  static boolean motion = !REDUCED, keyboardFocus;
  private static boolean installed;
  private static final String SANS =
      family(
          "SF Pro Text", ".AppleSystemUIFont", "Helvetica Neue", "Segoe UI", "Arial", "SansSerif");
  private static final String SERIF = family("Georgia", "Liberation Serif", "Serif");

  private Theme() {}

  private static Color hex(String s) {
    return new Color(Integer.parseInt(s, 16));
  }

  private static Color color(String light, String dark) {
    return hex(DARK ? dark : light);
  }

  private static boolean darkAppearance() {
    String a = System.getProperty("puissance4.appearance", "system");
    return "dark".equalsIgnoreCase(a)
        || (!"light".equalsIgnoreCase(a)
            && "Dark".equalsIgnoreCase(macDefault("-g", "AppleInterfaceStyle")));
  }

  private static String macDefault(String domain, String key) {
    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) return "";
    Process p = null;
    try {
      p =
          new ProcessBuilder("/usr/bin/defaults", "read", domain, key)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      if (p.waitFor(180, TimeUnit.MILLISECONDS))
        return new String(
                p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .trim();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (java.io.IOException ignored) {
    } finally {
      if (p != null && p.isAlive()) p.destroyForcibly();
    }
    return "";
  }

  private static String family(String... names) {
    Set<String> available =
        new HashSet<>(
            Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
    for (String n : names) if (available.contains(n)) return n;
    return names[names.length - 1];
  }

  static void install() {
    if (installed) return;
    installed = true;
    Toolkit.getDefaultToolkit()
        .addAWTEventListener(
            e -> {
              if (e instanceof KeyEvent k
                  && k.getID() == KeyEvent.KEY_PRESSED
                  && k.getKeyCode() == KeyEvent.VK_TAB) keyboardFocus = true;
              if (e instanceof MouseEvent m && m.getID() == MouseEvent.MOUSE_PRESSED)
                keyboardFocus = false;
            },
            AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
    UIManager.put("ToolTip.background", CARD);
    UIManager.put("ToolTip.foreground", TEXT);
    UIManager.put("ToolTip.font", font(12, false));
    UIManager.put(
        "ToolTip.border",
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE), new EmptyBorder(7, 9, 7, 9)));
  }

  static Font font(int size, boolean bold) {
    return new Font(SANS, bold ? Font.BOLD : Font.PLAIN, size);
  }

  static Font heading(int size) {
    Font f = new Font(SERIF, Font.PLAIN, size);
    Map<TextAttribute, Object> a = new HashMap<>(f.getAttributes());
    a.put(TextAttribute.TRACKING, -.035f);
    return f.deriveFont(a);
  }

  static JLabel label(String text, int size, Color color, boolean bold) {
    JLabel l = new JLabel(text);
    l.setFont(font(size, bold));
    l.setForeground(color);
    return l;
  }

  static JPanel panel(LayoutManager layout) {
    JPanel p = new JPanel(layout);
    p.setOpaque(false);
    return p;
  }

  static JPanel card(LayoutManager layout) {
    return new Card(layout);
  }

  static final class Card extends JPanel {
    private static final long serialVersionUID = 1L;

    Card(LayoutManager layout) {
      super(layout);
      setOpaque(false);
      setBorder(new EmptyBorder(24, 24, 24, 24));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = graphics(graphics);
      g.setColor(CARD);
      g.fillRoundRect(0, 0, getWidth(), getHeight(), 36, 36);
      g.setColor(LINE);
      g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 35, 35);
      g.dispose();
      super.paintComponent(graphics);
    }
  }

  static Graphics2D graphics(Graphics source) {
    Graphics2D g = (Graphics2D) source.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    return g;
  }

  static Color mix(Color a, Color b, double amount) {
    double f = Math.max(0, Math.min(1, amount));
    return new Color(
        (int) (a.getRed() * (1 - f) + b.getRed() * f),
        (int) (a.getGreen() * (1 - f) + b.getGreen() * f),
        (int) (a.getBlue() * (1 - f) + b.getBlue() * f));
  }

  static JButton button(String text, boolean primary) {
    return new Button(text, primary);
  }

  static void primary(JButton b, boolean primary) {
    if (b instanceof Button t) {
      t.primary = primary;
      t.repaint();
    }
  }

  static final class Button extends JButton {
    private static final long serialVersionUID = 1L;
    private boolean primary;
    private float hover;
    private Timer hoverTimer;

    Button(String text, boolean primary) {
      super(text);
      this.primary = primary;
      setFont(font(13, true));
      setIconTextGap(9);
      setForeground(TEXT);
      setBackground(CARD);
      setOpaque(false);
      setBorder(new EmptyBorder(0, 16, 0, 16));
      setContentAreaFilled(false);
      setFocusPainted(false);
      setPreferredSize(new Dimension(180, 42));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
              hover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
              hover(false);
            }
          });
    }

    private void hover(boolean inside) {
      if (hoverTimer != null) hoverTimer.stop();
      float from = hover, target = inside ? 1 : 0;
      if (!motion) {
        hover = target;
        repaint();
        return;
      }
      long start = System.nanoTime();
      hoverTimer =
          new Timer(
              16,
              e -> {
                float p = Math.min(1, (System.nanoTime() - start) / 160_000_000f);
                hover = from + (target - from) * p;
                repaint();
                if (p >= 1) {
                  hoverTimer.stop();
                  hoverTimer = null;
                }
              });
      hoverTimer.start();
    }

    int contentPadding() {
      if (getClientProperty("Atelier.padding") instanceof Integer padding) return padding;
      return getText().isEmpty()
          ? 6
          : Boolean.TRUE.equals(getClientProperty("Atelier.segment")) ? 6 : 16;
    }

    Font contentFont() {
      Font font = getFont();
      Icon icon = getIcon();
      int extra =
          icon == null ? 0 : icon.getIconWidth() + (getText().isEmpty() ? 0 : getIconTextGap());
      int available = getWidth() - 2 * contentPadding() - extra;
      while (font.getSize() > 12 && getFontMetrics(font).stringWidth(getText()) > available)
        font = font.deriveFont(font.getSize2D() - 1);
      return font;
    }

    @Override
    public void removeNotify() {
      if (hoverTimer != null) {
        hoverTimer.stop();
        hoverTimer = null;
      }
      super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = graphics(graphics);
      Color bg = primary ? GREEN : CARD, fg = primary ? ON_GREEN : TEXT;
      if (Boolean.TRUE.equals(getClientProperty("Atelier.segment"))) {
        bg = getModel().isSelected() ? CARD : TINT;
        fg = TEXT;
      }
      if (isEnabled())
        bg = mix(bg, primary ? TEXT : TINT, getModel().isPressed() ? .16 : hover * .1);
      else {
        bg = mix(bg, BG, .4);
        fg = mix(fg, bg, .55);
      }
      g.setColor(bg);
      g.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
      if (!primary
          && (!Boolean.TRUE.equals(getClientProperty("Atelier.segment"))
              || getModel().isSelected())) {
        g.setColor(LINE);
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 21, 21);
      }
      if (isFocusOwner() && keyboardFocus) {
        g.setColor(GREEN);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 16, 16);
      }
      Font paintedFont = contentFont();
      g.setFont(paintedFont);
      g.setColor(fg);
      FontMetrics fm = g.getFontMetrics();
      Icon icon = getIcon();
      int gap = icon == null || getText().isEmpty() ? 0 : getIconTextGap();
      int iw = icon == null ? 0 : icon.getIconWidth() + gap;
      int width = fm.stringWidth(getText()) + iw;
      int padding = contentPadding();
      int alignment = getHorizontalAlignment();
      int x =
          alignment == SwingConstants.LEFT || alignment == SwingConstants.LEADING
              ? padding
              : alignment == SwingConstants.RIGHT || alignment == SwingConstants.TRAILING
                  ? getWidth() - padding - width
                  : (getWidth() - width) / 2;
      int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
      if (icon != null) {
        icon.paintIcon(this, g, x, (getHeight() - icon.getIconHeight()) / 2);
        x += iw;
      }
      g.drawString(getText(), x, y);
      g.dispose();
    }
  }

  static JPanel segmented(JComboBox<String> model, boolean colors) {
    JPanel p =
        new JPanel(new GridLayout(1, 2, 4, 0)) {
          private static final long serialVersionUID = 1L;

          @Override
          protected void paintComponent(Graphics graphics) {
            Graphics2D g = graphics(graphics);
            g.setColor(TINT);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            g.dispose();
            super.paintComponent(graphics);
          }
        };
    p.setOpaque(false);
    p.setBorder(new EmptyBorder(4, 4, 4, 4));
    JButton[] choices = new JButton[2];
    for (int i = 0; i < 2; i++) {
      final int index = i;
      JButton b =
          button(
              colors ? (i == 0 ? "Rouge" : "Jaune") : (i == 0 ? "Contre l’IA" : "À deux"), false);
      b.putClientProperty("Atelier.segment", true);
      if (colors) {
        b.setIcon(dot(i == 0 ? RED : YELLOW, 12));
        b.setIconTextGap(6);
      }
      b.addActionListener(e -> model.setSelectedIndex(index));
      choices[i] = b;
      p.add(b);
    }
    Runnable sync =
        () -> {
          for (int i = 0; i < 2; i++) {
            choices[i].getModel().setSelected(model.getSelectedIndex() == i);
            choices[i].setEnabled(model.isEnabled());
          }
        };
    model.addActionListener(e -> sync.run());
    model.addPropertyChangeListener("enabled", e -> sync.run());
    sync.run();
    return p;
  }

  static Icon dot(Color color, int size) {
    return new Icon() {
      public int getIconWidth() {
        return size;
      }

      public int getIconHeight() {
        return size;
      }

      public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = graphics(graphics);
        g.setColor(color);
        g.fillOval(x, y, size, size);
        g.dispose();
      }
    };
  }

  static Icon icon(String name) {
    return new Icon() {
      public int getIconWidth() {
        return 16;
      }

      public int getIconHeight() {
        return 16;
      }

      public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = graphics(graphics);
        g.translate(x, y);
        g.setColor(!c.isEnabled() ? MUTED : c instanceof Button b && b.primary ? ON_GREEN : TEXT);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (name) {
          case "rules" -> {
            g.drawRoundRect(2, 2, 11, 12, 2, 2);
            g.drawLine(5, 5, 10, 5);
            g.drawLine(5, 8, 10, 8);
            g.drawLine(5, 11, 8, 11);
          }
          case "settings" -> {
            for (int i = 0; i < 3; i++) {
              int yy = 3 + i * 5, xx = i == 1 ? 5 : 10;
              g.drawLine(1, yy, 15, yy);
              g.setColor(CARD);
              g.fillOval(xx - 2, yy - 2, 4, 4);
              g.setColor(TEXT);
              g.drawOval(xx - 2, yy - 2, 4, 4);
            }
          }
          case "undo" -> {
            Path2D arrow = new Path2D.Double();

            arrow.moveTo(6.5, 3.5);
            arrow.lineTo(2.5, 7.5);
            arrow.lineTo(6.5, 11.5);

            g.draw(arrow);

            g.draw(
                new CubicCurve2D.Double(
                    2.5, 7.5,
                    7.0, 7.5,
                    13.5, 5.0,
                    13.5, 13.0));
          }
          case "restart" -> {
            g.draw(new Arc2D.Double(2, 2, 12, 12, 30, 285, Arc2D.OPEN));
            g.drawLine(13, 1, 13, 6);
            g.drawLine(13, 6, 8, 6);
          }
          case "arrow" -> {
            g.drawLine(2, 8, 14, 8);
            g.drawLine(10, 4, 14, 8);
            g.drawLine(10, 12, 14, 8);
          }
          case "close" -> {
            g.drawLine(4, 4, 12, 12);
            g.drawLine(4, 12, 12, 4);
          }
          case "down" -> {
            g.drawLine(4, 6, 8, 10);
            g.drawLine(8, 10, 12, 6);
          }
          case "up" -> {
            g.drawLine(4, 10, 8, 6);
            g.drawLine(8, 6, 12, 10);
          }
          default -> {
            g.drawOval(2, 2, 12, 12);
            g.drawString("i", 7, 12);
          }
        }
        g.dispose();
      }
    };
  }

  static void field(JComponent c) {
    c.setFont(font(13, false));
    c.setForeground(TEXT);
    c.setBackground(CARD);
    c.setPreferredSize(new Dimension(200, 42));
    c.addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent e) {
            c.repaint();
          }

          @Override
          public void focusLost(FocusEvent e) {
            c.repaint();
          }
        });
    if (c instanceof JComboBox<?> combo) {
      combo.setOpaque(false);
      combo.setUI(
          new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
              JButton b = new JButton(icon("down"));
              b.setOpaque(false);
              b.setContentAreaFilled(false);
              b.setBorder(null);
              b.setFocusable(false);
              return b;
            }

            @Override
            protected ComboPopup createPopup() {
              BasicComboPopup popup =
                  new BasicComboPopup(comboBox) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void paintComponent(Graphics graphics) {
                      Graphics2D g = graphics(graphics);
                      g.setColor(CARD);
                      g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                      g.dispose();
                    }
                  };
              popup.setOpaque(false);
              popup.setBackground(CARD);
              popup.getList().setBackground(CARD);
              popup.setBorder(
                  new EmptyBorder(4, 4, 4, 4) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void paintBorder(
                        Component c, Graphics source, int x, int y, int w, int h) {
                      Graphics2D g = graphics(source);
                      g.setColor(LINE);
                      g.drawRoundRect(x, y, w - 1, h - 1, 13, 13);
                      g.dispose();
                    }
                  });
              return popup;
            }

            @Override
            public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean focus) {
              Object selected = comboBox.getSelectedItem();
              if (selected == null) return;

              Graphics2D g = graphics(graphics);
              g.setFont(comboBox.getFont());
              g.setColor(comboBox.isEnabled() ? TEXT : MUTED);

              FontMetrics fm = g.getFontMetrics();

              g.clipRect(bounds.x, bounds.y, bounds.width, bounds.height);
              g.drawString(
                  selected.toString(),
                  bounds.x + 5,
                  bounds.y + (bounds.height - fm.getHeight()) / 2 + fm.getAscent());

              g.dispose();
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean focus) {}

            @Override
            public void paint(Graphics g, JComponent component) {
              fieldBackground(g, component);
              super.paint(g, component);
            }
          });
      combo.setBorder(new EmptyBorder(3, 10, 3, 8));
      combo.setRenderer(
          (list, value, index, selected, focus) -> {
            JLabel l =
                new JLabel(value == null ? "" : value.toString()) {
                  private static final long serialVersionUID = 1L;

                  @Override
                  protected void paintComponent(Graphics source) {
                    if (index >= 0 && selected) {
                      Graphics2D g = graphics(source);
                      g.setColor(TINT);
                      g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                      g.dispose();
                    }
                    super.paintComponent(source);
                  }
                };
            l.setFont(font(13, false));
            l.setForeground(combo.isEnabled() ? TEXT : MUTED);
            l.setOpaque(false);
            l.setBorder(new EmptyBorder(8, index < 0 ? 5 : 11, 8, index < 0 ? 5 : 11));
            return l;
          });
    } else if (c instanceof JSpinner spinner) {
      spinner.setOpaque(false);
      spinner.setBorder(new EmptyBorder(1, 10, 1, 6));
      spinner.setUI(
          new BasicSpinnerUI() {
            @Override
            protected Component createNextButton() {
              JButton b = step("up");
              installNextButtonListeners(b);
              return b;
            }

            @Override
            protected Component createPreviousButton() {
              JButton b = step("down");
              installPreviousButtonListeners(b);
              return b;
            }

            private JButton step(String direction) {
              JButton b = new JButton(icon(direction));
              b.setBorder(null);
              b.setContentAreaFilled(false);
              b.setOpaque(false);
              b.setPreferredSize(new Dimension(25, 15));
              return b;
            }

            @Override
            public void paint(Graphics g, JComponent component) {
              fieldBackground(g, component);
            }
          });
      if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
        editor.setOpaque(false);
        editor.setBorder(null);
        JFormattedTextField f = editor.getTextField();
        f.setOpaque(false);
        f.setBorder(null);
        f.setFont(font(13, false));
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setHorizontalAlignment(SwingConstants.LEFT);
        configureNumberEditor(spinner, f);
        f.setFocusLostBehavior(JFormattedTextField.PERSIST);
        f.setDisabledTextColor(MUTED);
        f.addFocusListener(
            new FocusAdapter() {
              @Override
              public void focusGained(FocusEvent e) {
                spinner.repaint();
              }

              @Override
              public void focusLost(FocusEvent e) {
                spinner.repaint();
              }
            });
      }
    }
  }

  private static void configureNumberEditor(JSpinner spinner, JFormattedTextField field) {
    if (!(spinner.getModel() instanceof SpinnerNumberModel model)) return;
    boolean integer = model.getNumber() instanceof Integer;
    java.text.DecimalFormat format =
        new java.text.DecimalFormat(
            integer ? "0" : "0.###", java.text.DecimalFormatSymbols.getInstance(Locale.FRANCE));
    format.setGroupingUsed(false);
    field.setFormatterFactory(
        new javax.swing.text.DefaultFormatterFactory(
            new JFormattedTextField.AbstractFormatter() {
              private static final long serialVersionUID = 1L;

              @Override
              public Object stringToValue(String text) throws java.text.ParseException {
                String value = text.strip();
                if (!value.matches(integer ? "[0-9]+" : "(?:[0-9]+(?:[,.][0-9]+)?|[,.][0-9]+)"))
                  throw new java.text.ParseException("Nombre invalide", 0);
                try {
                  double number = Double.parseDouble(value.replace(',', '.'));
                  if (!Double.isFinite(number)
                      || model.getMinimum() instanceof Number min && number < min.doubleValue()
                      || model.getMaximum() instanceof Number max && number > max.doubleValue())
                    throw new NumberFormatException();
                  if (integer) return Integer.valueOf(value);
                  return Double.valueOf(number);
                } catch (NumberFormatException e) {
                  throw new java.text.ParseException("Nombre hors limites", 0);
                }
              }

              @Override
              public String valueToString(Object value) {
                return value instanceof Number ? format.format(value) : "";
              }
            }));
    field.setValue(spinner.getValue());
    field.addPropertyChangeListener(
        "value",
        e -> {
          spinner.putClientProperty("Atelier.invalid", false);
          spinner.repaint();
        });
  }

  private static void fieldBackground(Graphics graphics, JComponent c) {
    Graphics2D g = graphics(graphics);
    g.setColor(CARD);
    g.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 20, 20);
    Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    boolean focused =
        keyboardFocus && focus != null && (focus == c || SwingUtilities.isDescendingFrom(focus, c));
    g.setColor(
        Boolean.TRUE.equals(c.getClientProperty("Atelier.invalid"))
            ? ERROR
            : focused ? GREEN : LINE);
    g.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 19, 19);
    g.dispose();
  }

  static void checkbox(JCheckBox b) {
    b.setOpaque(false);
    b.setForeground(TEXT);
    b.setFont(font(12, false));
    b.setBorder(null);
    b.setFocusPainted(false);
    b.setIcon(checkIcon(false));
    b.setSelectedIcon(checkIcon(true));
    b.setIconTextGap(8);
  }

  private static Icon checkIcon(boolean selected) {
    return new Icon() {
      public int getIconWidth() {
        return 17;
      }

      public int getIconHeight() {
        return 17;
      }

      public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = graphics(graphics);
        g.translate(x, y);
        g.setColor(selected ? GREEN : CARD);
        g.fillRoundRect(0, 0, 16, 16, 5, 5);
        g.setColor(c.isFocusOwner() && keyboardFocus ? GREEN : selected ? GREEN : LINE);
        g.drawRoundRect(0, 0, 16, 16, 5, 5);
        if (selected) {
          g.setColor(ON_GREEN);
          g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g.drawLine(4, 8, 7, 11);
          g.drawLine(7, 11, 12, 5);
        }
        g.dispose();
      }
    };
  }

  static JTextArea paragraph(String text, int size, Color color) {
    JTextArea a = new JTextArea(text);
    a.setFont(font(size, false));
    a.setForeground(color);
    a.setOpaque(false);
    a.setEditable(false);
    a.setFocusable(false);
    a.setLineWrap(true);
    a.setWrapStyleWord(true);
    a.setBorder(null);
    return a;
  }

  static JScrollPane scroll(JComponent content) {
    JScrollPane s = new JScrollPane(content);
    s.setBorder(null);
    s.setOpaque(false);
    s.getViewport().setOpaque(false);
    s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    s.getVerticalScrollBar().setUnitIncrement(24);
    s.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
    s.getVerticalScrollBar()
        .setUI(
            new BasicScrollBarUI() {
              @Override
              protected void configureScrollBarColors() {
                thumbColor = LINE;
                trackColor = BG;
              }

              @Override
              protected JButton createDecreaseButton(int o) {
                return zero();
              }

              @Override
              protected JButton createIncreaseButton(int o) {
                return zero();
              }

              private JButton zero() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
              }
            });
    return s;
  }
}
