package Puissance4.gui;

import Puissance4.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** One window with setup and game views; searches run on a snapshot in a SwingWorker. */
public final class GameWindow extends JFrame {
  private static final long serialVersionUID = 1L;
  private JPanel headerPanel;
  private int headerLeftMargin = 28;
  private int headerRightMargin = 28;

  private final CardLayout cards = new CardLayout();
  private final JPanel content = new MotionContent(cards);
  private final JComboBox<String> mode =
      new JComboBox<>(new String[] {"Humain contre IA", "Deux joueurs"});
  private final JComboBox<String> humanColor = new JComboBox<>(new String[] {"Rouge", "Jaune"});
  private final JComboBox<String> starter =
      new JComboBox<>(new String[] {"Rouge", "Jaune", "Aléatoire"});
  private final JComboBox<String> preset =
      new JComboBox<>(
          new String[] {
            "Découverte", "Facile", "Équilibré", "Avancé", "Expert", "Maximum", "Personnalisé"
          });
  private final JSpinner depth = new JSpinner(new SpinnerNumberModel(10, 1, 42, 1));
  private final JSpinner seconds = new JSpinner(new SpinnerNumberModel(2.0, .25, 300.0, .25));
  private final JComboBox<Integer> memory = new JComboBox<>(new Integer[] {16, 32, 64, 128, 256});
  private final JCheckBox unlimited = new JCheckBox("Sans limite de temps");
  private final JCheckBox openingKnowledge = new JCheckBox("Ouvertures exactes (jusqu’à 8 coups)");
  private final JCheckBox animate = new JCheckBox("Animer la chute des pions", true);
  private final JLabel status = Theme.label(" ", 23, Theme.TEXT, true);
  private final JLabel subtitle = Theme.label(" ", 13, Theme.MUTED, false);
  private final JLabel redScore = Theme.label("0", 30, Theme.RED, true);
  private final JLabel yellowScore = Theme.label("0", 30, Theme.YELLOW, true);
  private final JLabel draws = Theme.label("0", 22, Theme.MUTED, true);
  private final JLabel redName = Theme.label("Rouge", 14, Theme.TEXT, true);
  private final JLabel yellowName = Theme.label("Jaune", 14, Theme.TEXT, true);
  private final JLabel redRemaining = Theme.label("21 pions", 12, Theme.MUTED, false);
  private final JLabel yellowRemaining = Theme.label("21 pions", 12, Theme.MUTED, false);
  private final JLabel depthInfo = Theme.label(" ", 13, Theme.TEXT, false);
  private final JLabel nodeInfo = Theme.label(" ", 12, Theme.MUTED, false);
  private final JPanel actions = Theme.panel(new GridLayout(0, 1, 0, 9));
  private final JLabel timeInfo = Theme.label(" ", 12, Theme.MUTED, false);
  private final JLabel cacheInfo = Theme.label(" ", 12, Theme.MUTED, false);
  private final JButton undo = Theme.button("Annuler le coup", false);
  private final JButton stop = Theme.button("Jouer maintenant", false);
  private final JButton rematch = Theme.button("Nouvelle manche", true);
  private final BoardPanel boardPanel = new BoardPanel(this::playHuman);
  private Game game;
  private SearchSettings settings;
  private MinimaxAlgorithm.Session searchSession;
  private boolean playing, changingPreset, counted;
  private int winsRed, winsYellow, drawCount;
  private char roundStarter = 'X';
  private long taskToken;
  private long searchStart;
  private SwingWorker<MinimaxAlgorithm.SearchResult, MinimaxAlgorithm.SearchResult> worker;
  private AtomicBoolean stopRequested;
  private final Timer elapsed = new Timer(100, e -> updateElapsed());
  private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.FRANCE);

  public GameWindow() {
    super("Puissance 4");
    Theme.install();
    setUndecorated(true);
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(900, 680));
    setSize(1016, 727);
    setLocationRelativeTo(null);
    setIconImage(icon());
    if (Taskbar.isTaskbarSupported()
        && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE))
      Taskbar.getTaskbar().setIconImage(icon());
    content.setBackground(Theme.BG);
    JPanel app = Theme.panel(new BorderLayout());
    app.add(header(), BorderLayout.NORTH);
    app.add(content, BorderLayout.CENTER);
    app.add(footer(), BorderLayout.SOUTH);
    shell = new AtelierShell(this, app, this::close);
    setContentPane(shell);
    content.add(setupView(), "setup");
    content.add(gameView(), "game");
    preset.setSelectedIndex(2);
    memory.setSelectedItem(32);
    mode.addActionListener(
        e -> {
          updateSetup();
          if (!playing) fitView();
        });
    preset.addActionListener(e -> applyPreset());
    depth.addChangeListener(e -> customPreset());
    seconds.addChangeListener(e -> customPreset());
    memory.addActionListener(e -> customPreset());
    unlimited.addActionListener(
        e -> {
          customPreset();
          updateSetup();
        });
    openingKnowledge.addActionListener(e -> customPreset());
    undo.addActionListener(e -> undoTurn());
    stop.addActionListener(
        e -> {
          if (stopRequested != null) stopRequested.set(true);
          stop.setEnabled(false);
        });
    rematch.addActionListener(e -> newRound());
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            close();
          }
        });
    shortcuts();
    animate.addItemListener(e -> Theme.motion = animate.isSelected() && !Theme.REDUCED);
    Theme.motion = animate.isSelected() && !Theme.REDUCED;
    updateSetup();
    fitView();
    Rectangle usable = usableDesktop();
    setLocation(
        usable.x + (usable.width - getWidth()) / 2, usable.y + (usable.height - getHeight()) / 2);
    setVisible(true);
    shell.shape();
  }

  private final JButton advancedToggle = Theme.button("Réglages avancés", false);
  private final JButton analysisToggle = Theme.button("Analyse de l’IA", false);
  private final JPanel analysis = Theme.card(null);
  private SetupView setup;
  private GameView gameView;
  private AtelierShell shell;
  private boolean advanced, analysisExpanded;
  private int roundNumber = 1;

  private JPanel setupView() {
    setup = new SetupView();
    return wrap(setup);
  }

  private JPanel gameView() {
    gameView = new GameView();
    return wrap(gameView);
  }

  private JPanel wrap(JComponent view) {
    JPanel p = Theme.panel(new BorderLayout());
    p.add(Theme.scroll(view));
    return p;
  }

  private JPanel header() {
    JPanel p = Theme.panel(new BorderLayout());
    headerPanel = p;
    p.setBorder(new EmptyBorder(22, 28, 22, 28));
    p.setPreferredSize(new Dimension(0, 86));
    JLabel brand = Theme.label("Puissance 4", 16, Theme.TEXT, true);
    brand.setIcon(
        new Icon() {
          public int getIconWidth() {
            return 31;
          }

          public int getIconHeight() {
            return 18;
          }

          public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = Theme.graphics(graphics);
            g.setColor(Theme.RED);
            g.fillOval(x, y + 3, 13, 13);
            g.setColor(Theme.YELLOW);
            g.fillOval(x + 18, y + 3, 13, 13);
            g.dispose();
          }
        });
    brand.setIconTextGap(10);
    p.add(brand, BorderLayout.WEST);
    JPanel nav = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    JButton rules = Theme.button("Règles", false);
    rules.setName("rules");
    rules.putClientProperty("Atelier.padding", 12);
    rules.setIcon(Theme.icon("rules"));
    rules.setPreferredSize(new Dimension(102, 42));
    rules.addActionListener(e -> showRules());
    JButton config = Theme.button("Réglages", false);
    config.setName("settings");
    config.putClientProperty("Atelier.padding", 12);
    config.setIcon(Theme.icon("settings"));
    config.setPreferredSize(new Dimension(119, 42));
    config.addActionListener(e -> returnToSetup());
    nav.add(rules);
    nav.add(Box.createHorizontalStrut(8));
    nav.add(config);
    p.add(nav, BorderLayout.EAST);
    return p;
  }

  private void setHeaderMargins(int left, int right) {
    left = Math.max(28, left);
    right = Math.max(28, right);

    if (headerPanel == null || (headerLeftMargin == left && headerRightMargin == right)) {
      return;
    }

    headerLeftMargin = left;
    headerRightMargin = right;

    headerPanel.setBorder(new EmptyBorder(22, left, 22, right));

    headerPanel.revalidate();
    headerPanel.repaint();
  }

  private JLabel footer() {
    JLabel l =
        Theme.label(
            "1–7  Jouer     ·     U  Annuler     ·     R  Nouvelle manche", 11, Theme.MUTED, false);
    l.setHorizontalAlignment(SwingConstants.CENTER);
    l.setPreferredSize(new Dimension(0, 40));
    return l;
  }

  private void fitView() {
    int h = 43 + 86 + 40 + (playing ? 629 : Math.max(580, setup.formHeight()));
    Rectangle usable = usableDesktop();
    if (shell.canAutoFit()) {
      int minHeight = Math.min(680, usable.height);
      int minWidth = Math.min(900, usable.width);
      setMinimumSize(new Dimension(minWidth, minHeight));
      int height =
          Math.max(minHeight, Math.min(h, usable.height - Math.min(16, usable.height - minHeight)));
      int preferredWidth = playing ? 920 : 1016;
      int width = Math.max(minWidth, Math.min(preferredWidth, usable.width));

      // Preserve the horizontal center when switching between window widths.
      int centerX = getX() + getWidth() / 2;

      setSize(width, height);

      setLocation(
          Math.max(usable.x, Math.min(centerX - width / 2, usable.x + usable.width - width)),
          Math.max(usable.y, Math.min(getY(), usable.y + usable.height - height)));
    }
    content.revalidate();
  }

  private Rectangle usableDesktop() {
    Rectangle bounds = getGraphicsConfiguration().getBounds();
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
    return new Rectangle(
        bounds.x + insets.left,
        bounds.y + insets.top,
        bounds.width - insets.left - insets.right,
        bounds.height - insets.top - insets.bottom);
  }

  private void transition(String name) {
    cards.show(content, name);

    if ("setup".equals(name)) {
      setHeaderMargins(28, 28);
    }

    fitView();
    if (content instanceof MotionContent m) m.enter();
  }

  private final class SetupView extends JPanel implements Scrollable {
    private static final long serialVersionUID = 1L;
    private final JPanel editorial = new Editorial();
    private final JPanel form = Theme.card(null);
    private final JLabel colorLabel = Theme.label("Votre couleur", 12, Theme.MUTED, false);
    private final JLabel aiLabel = Theme.label("Niveau de l’IA", 12, Theme.MUTED, false);
    private final JPanel colorChoice = Theme.segmented(humanColor, true);
    private final JPanel localPlayers =
        new JPanel(new GridLayout(1, 2, 4, 0)) {
          @Override
          protected void paintComponent(Graphics source) {
            Graphics2D g = Theme.graphics(source);
            g.setColor(Theme.TINT);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
            g.dispose();
            super.paintComponent(source);
          }
        };

    private final JLabel localInfoLabel = Theme.label("Partie à deux", 12, Theme.MUTED, false);

    private final JPanel localInfo =
        new JPanel(null) {
          @Override
          protected void paintComponent(Graphics source) {
            Graphics2D g = Theme.graphics(source);
            g.setColor(Theme.TINT);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g.dispose();
            super.paintComponent(source);
          }
        };

    private final JLabel localInfoTitle = Theme.label("Sur le même écran", 13, Theme.TEXT, true);

    private final JTextArea localInfoText =
        Theme.paragraph(
            "Jouez chacun votre tour. Le premier joueur change à chaque manche.", 12, Theme.MUTED);

    private final JPanel advancedFields = Theme.panel(null);
    private final JButton start = Theme.button("Commencer la partie  →", true);

    SetupView() {
      super(null);
      setOpaque(false);
      add(editorial);
      add(form);
      JLabel title = Theme.label("Nouvelle partie", 24, Theme.TEXT, false);
      title.setFont(Theme.heading(24));
      title.setBounds(28, 23, 350, 34);
      form.add(title);
      JLabel modeLabel = Theme.label("Mode de jeu", 12, Theme.MUTED, false);
      modeLabel.setBounds(28, 76, 300, 18);
      form.add(modeLabel);
      JPanel modes = Theme.segmented(mode, false);
      modes.setName("modeSelector");
      form.add(modes);
      colorChoice.setName("colorSelector");
      form.add(colorChoice);
      form.add(colorLabel);
      localPlayers.setOpaque(false);
      localPlayers.setBorder(new EmptyBorder(4, 4, 4, 4));

      JLabel localRed = Theme.label("Rouge", 12, Theme.TEXT, true);
      localRed.setIcon(Theme.dot(Theme.RED, 12));
      localRed.setIconTextGap(6);
      localRed.setHorizontalAlignment(SwingConstants.CENTER);

      JLabel localYellow = Theme.label("Jaune", 12, Theme.TEXT, true);
      localYellow.setIcon(Theme.dot(Theme.YELLOW, 12));
      localYellow.setIconTextGap(6);
      localYellow.setHorizontalAlignment(SwingConstants.CENTER);

      localPlayers.add(localRed);
      localPlayers.add(localYellow);
      form.add(localPlayers);

      localInfo.setOpaque(false);
      localInfo.add(localInfoTitle);
      localInfo.add(localInfoText);

      form.add(localInfoLabel);
      form.add(localInfo);
      JLabel first = Theme.label("Premier joueur", 12, Theme.MUTED, false);
      first.setName("firstLabel");
      form.add(first);
      form.add(starter);
      starter.setToolTipText(
          "La règle classique donne le premier tour au plus jeune : choisissez sa couleur.");
      form.add(aiLabel);
      form.add(preset);
      form.add(advancedToggle);
      form.add(advancedFields);
      form.add(animate);
      form.add(start);
      advancedToggle.setName("advanced");
      advancedToggle.setHorizontalAlignment(SwingConstants.LEFT);
      advancedToggle.addActionListener(
          e -> {
            advanced = !advanced;
            refresh();
          });
      advancedFields.add(Theme.label("Profondeur maximale", 12, Theme.MUTED, false));
      advancedFields.add(Theme.label("Temps par coup (s)", 12, Theme.MUTED, false));
      advancedFields.add(depth);
      advancedFields.add(seconds);
      advancedFields.add(Theme.label("Mémoire du cache (Mo)", 12, Theme.MUTED, false));
      advancedFields.add(memory);
      advancedFields.add(unlimited);
      advancedFields.add(openingKnowledge);
      animate.setText("Animations");
      animate.setSelected(!Theme.REDUCED);
      animate.setEnabled(!Theme.REDUCED);
      animate.setToolTipText(
          Theme.REDUCED
              ? "La réduction des animations est activée dans le système ou au lancement."
              : "Animer les pions, les transitions et les survols.");
      depth.setToolTipText("Nombre maximal de demi-coups analysés, de 1 à 42.");
      seconds.setToolTipText(
          "Budget de temps par coup ; le dernier résultat complet est conservé.");
      openingKnowledge.setText("Ouvertures exactes");
      openingKnowledge.setToolTipText(
          "Choix optimal préanalysé jusqu’à 8 coups, entièrement local.");
      start.setName("startGame");
      start.addActionListener(e -> startSession());
      for (JComponent c : new JComponent[] {starter, preset, depth, seconds, memory})
        Theme.field(c);
      for (JCheckBox b : new JCheckBox[] {animate, unlimited, openingKnowledge}) Theme.checkbox(b);
      depth.getAccessibleContext().setAccessibleName("Profondeur maximale");
      seconds.getAccessibleContext().setAccessibleName("Temps par coup en secondes");
      memory.getAccessibleContext().setAccessibleName("Mémoire du cache en mégaoctets");
      modes.setBounds(28, 100, 360, 44);
    }

    int formHeight() {
      return mode.getSelectedIndex() == 0 && advanced ? 742 : 505;
    }

    void refresh() {
      boolean ai = mode.getSelectedIndex() == 0;
      colorLabel.setText(ai ? "Votre couleur" : "Les joueurs");
      colorChoice.setVisible(ai);
      localPlayers.setVisible(!ai);
      localInfoLabel.setVisible(!ai);
      localInfo.setVisible(!ai);

      for (JComponent c : new JComponent[] {aiLabel, preset, advancedToggle}) c.setVisible(ai);
      advancedFields.setVisible(ai && advanced);
      advancedToggle.setText(advanced ? "Masquer les réglages  ↑" : "Réglages avancés  ↓");
      setPreferredSize(new Dimension(1016, Math.max(580, formHeight())));
      revalidate();
      repaint();
    }

    @Override
    public void doLayout() {
      int cw = (getWidth() - 70 - 34) / 2;
      editorial.setBounds(35, 0, cw, 580);
      form.setBounds(35 + cw + 34, 0, cw, formHeight());
      int inner = cw - 56, half = (inner - 12) / 2;
      for (Component c : form.getComponents())
        if ("modeSelector".equals(c.getName())) c.setBounds(28, 100, inner, 44);
      colorLabel.setBounds(28, 162, half, 18);
      colorChoice.setBounds(28, 186, half, 44);
      localPlayers.setBounds(28, 186, half, 44);
      localInfoLabel.setBounds(28, 248, inner, 18);

      localInfo.setBounds(28, 272, inner, 94);

      localInfoTitle.setBounds(16, 13, inner - 32, 20);

      localInfoText.setBounds(16, 40, inner - 32, 46);
      for (Component c : form.getComponents())
        if ("firstLabel".equals(c.getName())) c.setBounds(40 + half, 162, half, 18);
      starter.setBounds(40 + half, 186, half, 44);
      aiLabel.setBounds(28, 248, inner, 18);
      preset.setBounds(28, 272, inner, 42);
      advancedToggle.setBounds(28, 334, inner, 32);
      advancedFields.setBounds(28, 382, inner, 218);
      advancedFields.getComponent(0).setBounds(0, 0, half, 18);
      advancedFields.getComponent(1).setBounds(half + 12, 0, half, 18);
      depth.setBounds(0, 24, half, 42);
      seconds.setBounds(half + 12, 24, half, 42);
      advancedFields.getComponent(4).setBounds(0, 85, inner, 18);
      memory.setBounds(0, 109, inner, 42);
      unlimited.setBounds(0, 167, inner, 19);
      openingKnowledge.setBounds(0, 193, inner, 19);
      boolean ai = mode.getSelectedIndex() == 0;
      animate.setBounds(28, ai && advanced ? 625 : 388, inner, 22);
      start.setBounds(28, formHeight() - 70, inner, 42);
    }

    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
      return 24;
    }

    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
      return Math.max(24, r.height - 24);
    }

    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  private final class Editorial extends JPanel {
    private static final long serialVersionUID = 1L;
    private final BoardPanel hero = new BoardPanel(c -> {});

    Editorial() {
      super(null);
      setOpaque(false);
      Game sample = new Game(Game.Mode.HUMAN_VS_HUMAN, 'X', 'X');
      for (char c : "1251553645171536".toCharArray()) sample.play(c - '1');
      hero.setGame(sample);
      hero.setDecorative();
    }

    @Override
    protected void paintComponent(Graphics source) {
      super.paintComponent(source);
      Graphics2D g = Theme.graphics(source);
      g.setColor(Theme.GREEN);
      g.setFont(Theme.font(10, true));
      g.drawString("UN JEU DE STRATÉGIE", 0, 35);
      g.setColor(Theme.TEXT);
      g.setFont(Theme.heading(48));
      g.drawString("À vous", 0, 101);
      g.drawString("de jouer.", 0, 155);
      g.setFont(Theme.font(13, false));
      g.setColor(Theme.MUTED);
      g.drawString("Affrontez l’IA ou jouez à deux.", 0, 198);
      g.drawString("Alignez quatre pions pour gagner.", 0, 219);
      int w = Math.min(405, getWidth() - 18), h = 286, x = (getWidth() - w) / 2, y = 238;
      g.setColor(Theme.SHADOW);
      g.fillOval(x + 25, y + h - 2, w - 40, 18);
      g.rotate(Math.toRadians(-5), x + w / 2.0, y + h / 2.0);
      g.translate(x, y);
      hero.setSize(w, h);
      hero.paint(g);
      g.dispose();
      Graphics2D caption = Theme.graphics(source);
      caption.setColor(Theme.MUTED);
      caption.setFont(Theme.font(11, false));
      caption.drawString("7 colonnes  ·  6 lignes", 0, 568);
      caption.dispose();
    }
  }

  private final class GameView extends JPanel implements Scrollable {
    private static final long serialVersionUID = 1L;
    private final JPanel turn = Theme.card(null), scores = Theme.card(null);
    private final JLabel round = Theme.label("Manche 1", 11, Theme.MUTED, false);
    private final JLabel marker = Theme.label("", 28, Theme.TEXT, false);
    private final JLabel scoreTitle = Theme.label("LE MATCH", 10, Theme.MUTED, true);
    private final JLabel redDot = Theme.label("", 12, Theme.RED, false),
        yellowDot = Theme.label("", 12, Theme.YELLOW, false);
    private final JLabel drawTitle = Theme.label("Matchs nuls", 11, Theme.MUTED, false);
    private Timer confetti;
    private double celebration;

    private static final int SIDEBAR_WIDTH = 248;
    private static final int SIDEBAR_GAP = 54;
    private static final int SCORE_HEIGHT = 194;
    private static final int SECTION_GAP = 20;

    private int boardWidth = 500, boardHeight = 490, boardX;

    GameView() {
      super(null);
      setOpaque(false);
      setPreferredSize(new Dimension(1016, 629));
      add(turn);
      turn.add(marker);
      turn.add(status);
      turn.add(subtitle);
      add(boardPanel);
      add(scores);
      scores.add(scoreTitle);
      scores.add(round);
      scores.add(redName);
      scores.add(yellowName);
      scores.add(redScore);
      scores.add(yellowScore);
      scores.add(redRemaining);
      scores.add(yellowRemaining);
      scores.add(drawTitle);
      scores.add(draws);
      redDot.setIcon(Theme.dot(Theme.RED, 10));
      yellowDot.setIcon(Theme.dot(Theme.YELLOW, 10));
      scores.add(redDot);
      scores.add(yellowDot);
      status.setFont(Theme.font(21, true));
      subtitle.setFont(Theme.font(12, false));
      redName.setFont(Theme.font(12, true));
      yellowName.setFont(Theme.font(12, true));
      redScore.setForeground(Theme.TEXT);
      yellowScore.setForeground(Theme.TEXT);
      redScore.setFont(Theme.heading(32));
      yellowScore.setFont(Theme.heading(32));
      draws.setFont(Theme.font(12, true));
      for (JLabel l : new JLabel[] {redRemaining, yellowRemaining})
        l.setFont(Theme.font(10, false));
      add(analysis);
      analysis.add(analysisToggle);
      analysis.add(depthInfo);
      analysis.add(nodeInfo);
      analysis.add(timeInfo);
      analysis.add(cacheInfo);
      analysisToggle.setName("analysis");
      analysisToggle.addActionListener(
          e -> {
            if (worker == null) {
              analysisExpanded = !analysisExpanded;
              sync();
            }
          });
      depthInfo.setFont(Theme.font(12, true));
      nodeInfo.setFont(Theme.font(11, false));
      timeInfo.setFont(Theme.font(11, false));
      cacheInfo.setFont(Theme.font(11, false));
      add(actions);
      undo.setIcon(Theme.icon("undo"));
      rematch.setIcon(Theme.icon("restart"));
      undo.setText("Annuler le coup");
      stop.setText("Jouer maintenant");
    }

    void sync() {
      if (game == null) return;
      round.setText("Manche " + roundNumber);
      char visiblePlayer =
          boardPanel.isAnimating() && game.getLastMove() != null
              ? game.getLastMove().player()
              : game.getWinner() != ' ' ? game.getWinner() : game.getCurrentPlayer();
      marker.setIcon(Theme.dot(visiblePlayer == 'X' ? Theme.RED : Theme.YELLOW, 26));
      marker.setVisible(!game.isGameOver() || game.getWinner() != ' ');
      boolean ai = game.getMode() == Game.Mode.HUMAN_VS_AI,
          expanded = worker != null || analysisExpanded;
      analysis.setVisible(ai);
      analysisToggle.setText(
          worker != null
              ? "L’IA réfléchit…"
              : expanded ? "Analyse de l’IA  ↑" : "Analyse de l’IA  ↓");
      for (JLabel l : new JLabel[] {depthInfo, nodeInfo, timeInfo, cacheInfo})
        l.setVisible(expanded);
      actions.removeAll();
      actions.add(rematch);
      actions.add(undo);
      stop.setVisible(worker != null);
      if (worker != null) actions.add(stop);
      Theme.primary(rematch, worker == null);
      revalidate();
      repaint();
    }

    void celebrate() {
      endCelebration();
      if (!Theme.motion || game.getWinner() == ' ') return;
      long start = System.nanoTime();
      confetti =
          new Timer(
              16,
              e -> {
                celebration = (System.nanoTime() - start) / 1_120_000_000.0;
                repaint();
                if (celebration >= 1) endCelebration();
              });
      confetti.start();
    }

    void endCelebration() {
      if (confetti != null) confetti.stop();
      confetti = null;
      celebration = 0;
      repaint();
    }

    @Override
    public void doLayout() {
      boardHeight = Math.min(490, Math.max(343, getHeight() - 115));
      boardWidth = Math.min(500, (int) Math.round(boardHeight / .98));

      int groupWidth = boardWidth + SIDEBAR_GAP + SIDEBAR_WIDTH;
      int x = Math.max(24, (getWidth() - groupWidth) / 2);
      int right = x + boardWidth + SIDEBAR_GAP;

      boardX = x;

      // Keep the header aligned with the board and sidebar as the viewport resizes.
      if (playing) {
        setHeaderMargins(x, headerPanel.getWidth() - (right + SIDEBAR_WIDTH));
      }
      turn.setBounds(x, 0, boardWidth, 76);
      marker.setBounds(21, 24, 32, 30);
      int textX = game != null && game.isGameOver() && game.getWinner() == ' ' ? 28 : 65;
      status.setBounds(textX, 14, boardWidth - textX - 21, 29);
      Font titleFont = Theme.font(21, true);
      while (titleFont.getSize() > 18
          && status.getFontMetrics(titleFont).stringWidth(status.getText()) > status.getWidth())
        titleFont = titleFont.deriveFont(titleFont.getSize2D() - 1);
      status.setFont(titleFont);
      subtitle.setBounds(textX, 46, boardWidth - textX - 21, 18);
      boardPanel.setBounds(x, 99, boardWidth, boardHeight);
      scores.setBounds(right, 0, SIDEBAR_WIDTH, SCORE_HEIGHT);

      scoreTitle.setBounds(18, 17, 105, 18);

      round.setHorizontalAlignment(SwingConstants.RIGHT);
      round.setBounds(SIDEBAR_WIDTH - 116, 17, 98, 18);

      int cellGap = 12;
      int cellWidth = (SIDEBAR_WIDTH - 36 - cellGap) / 2;

      layoutPlayerScore(18, cellWidth, redDot, redName, redScore, redRemaining);

      layoutPlayerScore(
          18 + cellWidth + cellGap, cellWidth, yellowDot, yellowName, yellowScore, yellowRemaining);

      drawTitle.setBounds(18, 158, SIDEBAR_WIDTH - 87, 19);

      draws.setHorizontalAlignment(SwingConstants.RIGHT);
      draws.setBounds(SIDEBAR_WIDTH - 68, 158, 50, 19);

      boolean ai = game != null && game.getMode() == Game.Mode.HUMAN_VS_AI;

      int analysisHeight = worker != null || analysisExpanded ? 161 : 42;

      int actionHeight = actions.getComponentCount() * 50 - 8;

      // Compress gaps to keep all actions visible in a short viewport.
      int gap =
          ai
              ? Math.max(
                  6,
                  Math.min(
                      SECTION_GAP,
                      (getHeight() - SCORE_HEIGHT - analysisHeight - actionHeight) / 2))
              : SECTION_GAP;

      int analysisY = SCORE_HEIGHT + gap;

      analysis.setBounds(right, analysisY, SIDEBAR_WIDTH, analysisHeight);

      analysisToggle.setBounds(0, 0, SIDEBAR_WIDTH, 42);

      depthInfo.setBounds(18, 51, SIDEBAR_WIDTH - 36, 20);
      nodeInfo.setBounds(18, 77, SIDEBAR_WIDTH - 36, 18);
      timeInfo.setBounds(18, 103, SIDEBAR_WIDTH - 36, 18);
      cacheInfo.setBounds(18, 129, SIDEBAR_WIDTH - 36, 18);

      actions.setBounds(
          right,
          ai ? analysisY + analysisHeight + gap : SCORE_HEIGHT + gap,
          SIDEBAR_WIDTH,
          actionHeight);
    }

    private void layoutPlayerScore(
        int left, int width, JLabel dot, JLabel name, JLabel score, JLabel remaining) {
      int dotSize = 10;
      int iconGap = 7;

      int labelWidth = name.getFontMetrics(name.getFont()).stringWidth(name.getText());

      int totalWidth = dotSize + iconGap + labelWidth;

      int start = left + Math.max(0, (width - totalWidth) / 2);

      dot.setBounds(start, 55, dotSize, 18);

      name.setBounds(
          start + dotSize + iconGap, 55, Math.max(1, left + width - start - dotSize - iconGap), 18);

      score.setHorizontalAlignment(SwingConstants.CENTER);
      score.setBounds(left, 82, width, 40);

      remaining.setHorizontalAlignment(SwingConstants.CENTER);
      remaining.setBounds(left, 123, width, 18);
    }

    @Override
    protected void paintComponent(Graphics source) {
      super.paintComponent(source);
      Graphics2D g = Theme.graphics(source);
      g.setColor(Theme.SHADOW);
      g.fillOval(boardX + 23, 99 + boardHeight - 26, boardWidth - 42, 24);
      g.dispose();
    }

    @Override
    protected void paintChildren(Graphics source) {
      super.paintChildren(source);
      if (confetti == null) return;
      Graphics2D g = Theme.graphics(source);
      g.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, 1 - celebration)));
      for (int i = 0; i < 32; i++) {
        double x = getWidth() * (i * .61803398875 % 1), y = 70 + celebration * (220 + (i % 7) * 32);
        g.setColor(i % 3 == 0 ? Theme.GREEN : i % 3 == 1 ? Theme.RED : Theme.YELLOW);
        g.rotate(celebration * 4 + i, x, y);
        g.fillRoundRect((int) x, (int) y, 5, 9, 2, 2);
        g.rotate(-celebration * 4 - i, x, y);
      }
      g.dispose();
    }

    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
      return 24;
    }

    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
      return Math.max(24, r.height - 24);
    }

    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public boolean getScrollableTracksViewportHeight() {
      return true;
    }
  }

  private static final class MotionContent extends JPanel {
    private static final long serialVersionUID = 1L;
    private float progress = 1;
    private Timer entry;

    MotionContent(CardLayout layout) {
      super(layout);
      setOpaque(false);
    }

    void enter() {
      if (entry != null) entry.stop();
      progress = Theme.motion ? 0 : 1;
      if (!Theme.motion) {
        repaint();
        return;
      }
      long start = System.nanoTime();
      entry =
          new Timer(
              16,
              e -> {
                progress = Math.min(1, (System.nanoTime() - start) / 240_000_000f);
                repaint();
                if (progress >= 1) {
                  entry.stop();
                  entry = null;
                }
              });
      entry.start();
    }

    void shutdown() {
      if (entry != null) entry.stop();
      entry = null;
    }

    @Override
    public void paint(Graphics source) {
      Graphics2D g = (Graphics2D) source.create();
      g.setComposite(AlphaComposite.SrcOver.derive(progress));
      g.translate(0, (1 - progress) * 8);
      super.paint(g);
      g.dispose();
    }
  }

  private void applyPreset() {
    int index = preset.getSelectedIndex();
    if (index < 0 || index == 6) return;
    int[] depths = {2, 4, 10, 14, 20, 42};
    double[] times = {.3, .5, 2, 4, 8, 15};
    int[] caches = {16, 16, 32, 64, 64, 128};
    changingPreset = true;
    depth.setValue(depths[index]);
    seconds.setValue(times[index]);
    memory.setSelectedItem(caches[index]);
    unlimited.setSelected(false);
    openingKnowledge.setSelected(index >= 4);
    changingPreset = false;
    for (JSpinner spinner : new JSpinner[] {depth, seconds}) {
      ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setValue(spinner.getValue());
      spinner.putClientProperty("Atelier.invalid", false);
    }
    updateSetup();
  }

  private void customPreset() {
    if (!changingPreset) preset.setSelectedIndex(6);
  }

  private void updateSetup() {
    boolean ai = mode.getSelectedIndex() == 0;
    if (setup != null) setup.refresh();
    humanColor.setEnabled(ai);
    preset.setEnabled(ai);
    depth.setEnabled(ai);
    seconds.setEnabled(ai && !unlimited.isSelected());
    memory.setEnabled(ai);
    unlimited.setEnabled(ai);
    openingKnowledge.setEnabled(ai);
  }

  private boolean commitSettings() {
    if (mode.getSelectedIndex() == 1) return true;
    for (JSpinner field : new JSpinner[] {depth, seconds}) {
      if (field == seconds && unlimited.isSelected()) continue;
      field.putClientProperty("Atelier.invalid", false);
      try {
        field.commitEdit();
      } catch (java.text.ParseException e) {
        field.putClientProperty("Atelier.invalid", true);
        advanced = true;
        setup.refresh();
        fitView();
        AtelierDialogs.message(
            this,
            "Vérifiez vos réglages",
            "Indiquez une profondeur entière de 1 à 42 et un temps de 0,25 à 300 secondes.");
        JFormattedTextField text = ((JSpinner.DefaultEditor) field.getEditor()).getTextField();
        text.requestFocusInWindow();
        text.selectAll();
        field.repaint();
        return false;
      }
    }
    return true;
  }

  private void startSession() {
    if (!commitSettings()) return;
    settings =
        new SearchSettings(
            (Integer) depth.getValue(),
            unlimited.isSelected()
                ? 0
                : Math.round(((Number) seconds.getValue()).doubleValue() * 1000),
            (Integer) memory.getSelectedItem(),
            openingKnowledge.isSelected());
    roundStarter =
        starter.getSelectedIndex() == 2
            ? (new Random().nextBoolean() ? 'X' : 'O')
            : starter.getSelectedIndex() == 0 ? 'X' : 'O';
    winsRed = 0;
    winsYellow = 0;
    drawCount = 0;
    playing = true;
    roundNumber = 1;
    openRound();
    transition("game");
    boardPanel.requestFocusInWindow();
  }

  private void openRound() {
    cancelSearch();
    gameView.endCelebration();
    closeSearchSession();
    searchSession = new MinimaxAlgorithm.Session();
    boardPanel.shutdown();
    game =
        new Game(
            mode.getSelectedIndex() == 0 ? Game.Mode.HUMAN_VS_AI : Game.Mode.HUMAN_VS_HUMAN,
            humanColor.getSelectedIndex() == 0 ? 'X' : 'O',
            roundStarter);
    counted = false;
    boardPanel.setGame(game);
    depthInfo.setText(
        game.getMode() == Game.Mode.HUMAN_VS_HUMAN
            ? "Deux joueurs locaux"
            : "Profondeur visée : " + settings.maxDepth());
    nodeInfo.setText(" ");
    timeInfo.setText(" ");
    cacheInfo.setText(" ");
    updateUIState();
    if (game.isAITurn()) startAI();
  }

  private void playHuman(int column) {
    if (!playing
        || worker != null
        || boardPanel.isAnimating()
        || game.isAITurn()
        || game.isGameOver()) return;
    Game.Move move = game.play(column);
    if (move == null) {
      subtitle.setText("Cette colonne est pleine. Choisissez-en une autre.");
      return;
    }
    animateMove(move);
  }

  private void animateMove(Game.Move move) {
    boardPanel.setInputEnabled(false);
    undo.setEnabled(false);
    rematch.setEnabled(false);
    long token = taskToken;
    gameView.sync();
    boardPanel.animate(
        move,
        () -> {
          if (token != taskToken || !playing) return;
          boardPanel.refresh();
          updateUIState();
          if (game.isAITurn()) startAI();
        },
        animate.isSelected());
    if (boardPanel.isAnimating()) {
      subtitle.setText("Le pion descend…");
      gameView.sync();
    }
  }

  private void startAI() {
    if (worker != null || !playing || !game.isAITurn()) return;
    long token = ++taskToken;
    Board snapshot = new Board(game.getBoard());
    char player = game.getCurrentPlayer();
    MinimaxAlgorithm.Session session = searchSession;
    AtomicBoolean requested = new AtomicBoolean();
    stopRequested = requested;
    searchStart = System.nanoTime();
    depthInfo.setText("Profondeur : 0 / " + settings.maxDepth());
    nodeInfo.setText("0 positions explorées");
    timeInfo.setText("0,0 s");
    if (cacheInfo.getText().isBlank())
      cacheInfo.setText("Cache : — / " + settings.cacheMegabytes() + " Mo");
    elapsed.start();
    worker =
        new SwingWorker<>() {
          @Override
          protected MinimaxAlgorithm.SearchResult doInBackground() {
            return session.search(
                snapshot, player, settings, () -> isCancelled() || requested.get(), this::publish);
          }

          @Override
          protected void process(List<MinimaxAlgorithm.SearchResult> chunks) {
            if (token == taskToken && !chunks.isEmpty()) showSearch(chunks.get(chunks.size() - 1));
          }

          @Override
          protected void done() {
            if (token != taskToken) return;
            worker = null;
            stopRequested = null;
            elapsed.stop();
            try {
              MinimaxAlgorithm.SearchResult result = get();
              showSearch(result);
              Game.Move move = game.play(result.column());
              if (move != null) animateMove(move);
              else updateUIState();
            } catch (CancellationException ignored) {
              updateUIState();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
              updateUIState();
              AtelierDialogs.message(
                  GameWindow.this,
                  "Recherche interrompue",
                  "L’IA n’a pas pu terminer son calcul. Réduisez la mémoire ou la profondeur dans"
                      + " les réglages. La partie peut continuer.");
              // Keep the game playable even if a constrained JVM cannot allocate the requested
              // cache.
              if (playing && game.isAITurn()) {
                var fallback =
                    MinimaxAlgorithm.search(
                        snapshot, player, new SearchSettings(1, 0, 1), () -> true, r -> {});
                Game.Move move = game.play(fallback.column());
                if (move != null) animateMove(move);
              }
            }
          }
        };
    worker.execute();
    updateUIState();
  }

  private void showSearch(MinimaxAlgorithm.SearchResult r) {
    depthInfo.setText(
        switch (r.source()) {
          case OPENING_BOOK -> "Ouverture exacte";
          case TACTICAL -> "Résultat tactique exact";
          case EXACT_SEARCH -> "Résolution exacte : " + r.completedDepth() + " coups";
          case SEARCH -> "Profondeur : " + r.completedDepth() + " / " + settings.maxDepth();
        });
    nodeInfo.setText(numbers.format(r.nodes()) + " positions explorées");
    timeInfo.setText(
        String.format(
            Locale.FRANCE,
            "%.2f s%s",
            r.elapsedMillis() / 1000.0,
            r.proven() ? " · résultat démontré" : ""));
    cacheInfo.setText(
        String.format(
            Locale.FRANCE,
            "Cache : %.1f / %d Mo",
            r.cacheBytes() / (1024.0 * 1024),
            settings.cacheMegabytes()));
    cacheInfo.setToolTipText(
        numbers.format(r.cacheHits()) + " consultations utiles du cache ; plafond des réglages.");
  }

  private void updateElapsed() {
    if (worker != null) {
      subtitle.setText("L’IA prépare son prochain coup.");
      timeInfo.setText(
          String.format(Locale.FRANCE, "Temps : %.1f s", (System.nanoTime() - searchStart) / 1e9));
    }
  }

  private void updateUIState() {
    if (game == null) return;
    boolean busy = worker != null || boardPanel.isAnimating();
    if (game.isGameOver()) {
      char winner = game.getWinner();
      status.setText(
          winner == ' '
              ? "Match nul !"
              : name(winner)
                  + (winner == game.getHumanColor() && game.getMode() == Game.Mode.HUMAN_VS_AI
                      ? " avez gagné !"
                      : " a gagné !"));
      subtitle.setText(
          winner == ' '
              ? "Une grille pleine. On en refait une ?"
              : "Quatre pions alignés. Une revanche ?");
      if (!counted) {
        if (winner == 'X') winsRed++;
        else if (winner == 'O') winsYellow++;
        else drawCount++;
        counted = true;
        gameView.celebrate();
      }
    } else {
      status.setText(
          game.isAITurn()
              ? "Au tour de l’IA"
              : game.getMode() == Game.Mode.HUMAN_VS_AI
                  ? "À vous de jouer"
                  : "Au joueur "
                      + (game.getCurrentPlayer() == 'X' ? "rouge" : "jaune")
                      + " de jouer");
      subtitle.setText(
          worker != null
              ? "L’IA prépare son prochain coup."
              : game.isAITurn()
                  ? "Au tour de l’ordinateur."
                  : "Choisissez une colonne pour déposer votre pion.");
    }
    status.setForeground(Theme.TEXT);
    redScore.setText(Integer.toString(winsRed));
    yellowScore.setText(Integer.toString(winsYellow));
    draws.setText(Integer.toString(drawCount));
    redName.setText(game.getMode() == Game.Mode.HUMAN_VS_HUMAN ? "Rouge" : name('X'));
    yellowName.setText(game.getMode() == Game.Mode.HUMAN_VS_HUMAN ? "Jaune" : name('O'));
    redRemaining.setText((21 - Long.bitCount(game.getBoard().getBits('X'))) + " pions");
    yellowRemaining.setText((21 - Long.bitCount(game.getBoard().getBits('O'))) + " pions");
    undo.setEnabled(game.canUndo() && !boardPanel.isAnimating());
    stop.setEnabled(worker != null && stopRequested != null && !stopRequested.get());
    rematch.setEnabled(!boardPanel.isAnimating());
    boardPanel.setInputEnabled(!busy && !game.isGameOver() && !game.isAITurn());
    gameView.sync();
  }

  private String name(char player) {
    return game.getMode() == Game.Mode.HUMAN_VS_HUMAN
        ? (player == 'X' ? "Joueur rouge" : "Joueur jaune")
        : player == game.getHumanColor() ? "Vous" : "Ordinateur";
  }

  private void undoTurn() {
    if (!playing || !game.canUndo() || boardPanel.isAnimating()) return;
    cancelSearch();
    if (counted) {
      if (game.getWinner() == 'X') winsRed--;
      else if (game.getWinner() == 'O') winsYellow--;
      else drawCount--;
      counted = false;
    }
    gameView.endCelebration();
    game.undoHumanTurn();
    boardPanel.refresh();
    updateUIState();
    boardPanel.requestFocusInWindow();
  }

  private void newRound() {
    if (!playing || game == null || boardPanel.isAnimating()) return;

    if (!game.isGameOver()
        && !AtelierDialogs.confirm(
            this,
            "Nouvelle manche ?",
            "La grille sera remise à zéro et le premier joueur changera."
                + " Les scores seront conservés.",
            "Nouvelle manche")) {
      boardPanel.requestFocusInWindow();
      return;
    }

    roundNumber++;
    roundStarter = Constants.opponent(roundStarter);
    openRound();
    boardPanel.requestFocusInWindow();
  }

  private void returnToSetup() {
    if (!playing) return;
    if (!game.isGameOver()
        && game.getBoard().getMoveCount() > 0
        && !AtelierDialogs.confirm(
            this,
            "Revenir aux réglages ?",
            "Vous quitterez la manche en cours pour préparer une nouvelle partie.",
            "Quitter la partie")) return;
    playing = false;
    cancelSearch();
    closeSearchSession();
    boardPanel.shutdown();
    gameView.endCelebration();
    transition("setup");
  }

  private void cancelSearch() {
    taskToken++;
    elapsed.stop();
    if (worker != null) worker.cancel(true);
    worker = null;
    if (stopRequested != null) stopRequested.set(true);
    stopRequested = null;
  }

  private void close() {
    playing = false;
    cancelSearch();
    closeSearchSession();
    boardPanel.shutdown();
    gameView.endCelebration();
    ((MotionContent) content).shutdown();
    shell.shutdown();
    dispose();
  }

  private void closeSearchSession() {
    if (searchSession != null) searchSession.close();
    searchSession = null;
  }

  private void showRules() {
    AtelierDialogs.rules(this);
  }

  private void shortcuts() {
    JRootPane root = getRootPane();
    for (int c = 0; c < 7; c++) {
      final int col = c;
      bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_1 + c, 0), "col" + c, () -> playHuman(col));
      bind(
          root,
          KeyStroke.getKeyStroke(KeyEvent.VK_1 + c, InputEvent.SHIFT_DOWN_MASK),
          "shift" + c,
          () -> playHuman(col));
      bind(
          root,
          KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1 + c, 0),
          "num" + c,
          () -> playHuman(col));
    }
    bind(
        root,
        KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
        "left",
        () -> {
          if (playing) boardPanel.moveSelection(-1);
        });
    bind(
        root,
        KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
        "right",
        () -> {
          if (playing) boardPanel.moveSelection(1);
        });
    bind(
        root,
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        "play",
        () -> {
          Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
          if (focus instanceof AbstractButton button) button.doClick();
          else if (playing) playHuman(boardPanel.selectedColumn());
        });
    bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_U, 0), "undo", this::undoTurn);
    bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "restart", this::newRound);
    bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "setup", this::returnToSetup);
  }

  private static void bind(JRootPane root, KeyStroke key, String id, Runnable action) {
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, id);
    root.getActionMap()
        .put(
            id,
            new AbstractAction() {
              private static final long serialVersionUID = 1L;

              @Override
              public void actionPerformed(ActionEvent e) {
                action.run();
              }
            });
  }

  private static Image icon() {
    BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.scale(4, 4);
    g.setColor(Theme.BLUE);
    g.fillRoundRect(3, 3, 58, 58, 14, 14);
    g.setColor(Theme.YELLOW);
    g.fillOval(12, 12, 18, 18);
    g.fillOval(34, 34, 18, 18);
    g.setColor(Theme.RED);
    g.fillOval(34, 12, 18, 18);
    g.fillOval(12, 34, 18, 18);
    g.dispose();
    return image;
  }
}
