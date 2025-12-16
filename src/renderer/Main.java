package renderer;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main extends JPanel implements Runnable {

    private static final Color BG_DARK = new Color(30, 30, 30);
    private static final Color BG_MEDIUM = new Color(45, 45, 45);
    private static final Color BG_LIGHT = new Color(60, 60, 60);
    private static final Color BORDER_COLOR = new Color(70, 70, 70);
    private static final Color TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color TEXT_SECONDARY = new Color(150, 150, 150);
    private static final Color ACCENT = new Color(80, 140, 200);

    private static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font FONT_LABEL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_VALUE = new Font("Consolas", Font.PLAIN, 11);
    private static final Font FONT_STATUS = new Font("Consolas", Font.PLAIN, 10);

    private static final int INSPECTOR_WIDTH = 260;
    private static final int INSPECTOR_MIN_WIDTH = 200;
    private static final int STATUS_BAR_HEIGHT = 22;

    private final Render renderer;
    private final PostProcessing postProcessing;
    private final Profiler profiler;
    private Mesh mesh;
    private Camera camera;
    private Material currentMaterial;
    private double angle = 0;
    private double rotationSpeed = 0.02;
    private double orbitDistance = 5.0;
    private double cameraYaw = 0.0;
    private double cameraPitch = 0.0;
    private double panX = 0.0;
    private double panY = 0.0;
    private double targetYaw = 0.0;
    private double targetPitch = 0.0;
    private double targetDistance = 5.0;
    private double targetPanX = 0.0;
    private double targetPanY = 0.0;

    private static final double CAMERA_DAMPING = 0.15;
    private static final double ORBIT_SENSITIVITY = 0.005;
    private static final double PAN_SENSITIVITY = 0.01;
    private static final double ZOOM_SENSITIVITY = 0.5;

    private int lastMouseX = 0;
    private int lastMouseY = 0;
    private boolean isDraggingLeft = false;
    private boolean isDraggingRight = false;

    private long lastFpsTime = System.nanoTime();
    private int frameCount = 0;
    private double currentFps = 0.0;

    private final JFrame frame;
    private JLabel statusLabel;
    private ViewportPanel viewport;

    private boolean isFullscreen = false;
    private Rectangle windowedBounds;
    private JSplitPane mainSplitPane;
    private JScrollPane inspectorScrollPane;
    private JPanel statusBar;
    private int lastDividerLocation;

    private boolean showDebugOverlay = true;
    private CommandPalette commandPalette;
    private InputManager inputManager;

    public Main(JFrame frame) {
        this.frame = frame;

        applyDarkTheme();

        renderer = new Render(1000, 700);
        postProcessing = new PostProcessing(1000, 700);
        profiler = Profiler.getInstance();

        camera = new Camera(
                new Vector3(0, 0, -5),
                new Vector3(0, 0, 0)
        );
        camera.aspect = 1000.0 / 700.0;

        mesh = Mesh.createCube(2.0);
        Mesh.computeVertexNormals(mesh);
        currentMaterial = Material.preset(Material.MaterialPreset.CLAY);

        renderer.setSpecularStrength(1.0);
        renderer.setRoughness(currentMaterial.getRoughness());
        renderer.setMetalness(currentMaterial.getMetalness());
        renderer.setEnvStrength(0.3);

        buildUI();

        inputManager = new InputManager();
        registerShortcuts();

        commandPalette = new CommandPalette(frame);
        populateCommandPalette();

        Thread renderThread = new Thread(this);
        renderThread.setDaemon(true);
        renderThread.start();
    }

    private void applyDarkTheme() {
        UIManager.put("control", BG_MEDIUM);
        UIManager.put("text", TEXT_PRIMARY);
        UIManager.put("nimbusBase", BG_DARK);
        UIManager.put("nimbusFocus", ACCENT);
        UIManager.put("nimbusLightBackground", BG_MEDIUM);
        UIManager.put("info", BG_MEDIUM);
        UIManager.put("nimbusSelectionBackground", ACCENT);

        UIManager.put("Panel.background", BG_DARK);
        UIManager.put("Panel.foreground", TEXT_PRIMARY);

        UIManager.put("Label.background", BG_DARK);
        UIManager.put("Label.foreground", TEXT_PRIMARY);
        UIManager.put("Label.disabledForeground", TEXT_SECONDARY);

        UIManager.put("Button.background", BG_LIGHT);
        UIManager.put("Button.foreground", TEXT_PRIMARY);
        UIManager.put("Button.select", ACCENT);
        UIManager.put("Button.focus", ACCENT);
        UIManager.put("Button.border", BorderFactory.createLineBorder(BORDER_COLOR));
        UIManager.put("Button.disabledText", TEXT_SECONDARY);

        UIManager.put("ToggleButton.background", BG_LIGHT);
        UIManager.put("ToggleButton.foreground", TEXT_PRIMARY);
        UIManager.put("ToggleButton.select", ACCENT);
        UIManager.put("ToggleButton.border", BorderFactory.createLineBorder(BORDER_COLOR));

        UIManager.put("TextField.background", BG_MEDIUM);
        UIManager.put("TextField.foreground", TEXT_PRIMARY);
        UIManager.put("TextField.caretForeground", TEXT_PRIMARY);
        UIManager.put("TextField.selectionBackground", ACCENT);
        UIManager.put("TextField.selectionForeground", Color.WHITE);
        UIManager.put("TextField.border", BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        UIManager.put("TextField.inactiveBackground", BG_DARK);

        UIManager.put("TextArea.background", BG_MEDIUM);
        UIManager.put("TextArea.foreground", TEXT_PRIMARY);
        UIManager.put("TextArea.caretForeground", TEXT_PRIMARY);
        UIManager.put("TextArea.selectionBackground", ACCENT);
        UIManager.put("TextArea.selectionForeground", Color.WHITE);

        UIManager.put("ComboBox.background", BG_LIGHT);
        UIManager.put("ComboBox.foreground", TEXT_PRIMARY);
        UIManager.put("ComboBox.selectionBackground", ACCENT);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("ComboBox.buttonBackground", BG_LIGHT);
        UIManager.put("ComboBox.buttonDarkShadow", BORDER_COLOR);
        UIManager.put("ComboBox.buttonHighlight", BG_LIGHT);
        UIManager.put("ComboBox.buttonShadow", BORDER_COLOR);
        UIManager.put("ComboBox.border", BorderFactory.createLineBorder(BORDER_COLOR));

        UIManager.put("List.background", BG_DARK);
        UIManager.put("List.foreground", TEXT_PRIMARY);
        UIManager.put("List.selectionBackground", ACCENT);
        UIManager.put("List.selectionForeground", Color.WHITE);
        UIManager.put("List.focusCellHighlightBorder", BorderFactory.createEmptyBorder());

        UIManager.put("ScrollBar.background", BG_DARK);
        UIManager.put("ScrollBar.foreground", TEXT_SECONDARY);
        UIManager.put("ScrollBar.thumb", BG_LIGHT);
        UIManager.put("ScrollBar.thumbDarkShadow", BORDER_COLOR);
        UIManager.put("ScrollBar.thumbHighlight", BG_LIGHT);
        UIManager.put("ScrollBar.thumbShadow", BORDER_COLOR);
        UIManager.put("ScrollBar.track", BG_DARK);
        UIManager.put("ScrollBar.trackHighlight", BG_DARK);
        UIManager.put("ScrollBar.width", 12);

        UIManager.put("ScrollPane.background", BG_DARK);
        UIManager.put("ScrollPane.foreground", TEXT_PRIMARY);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("Viewport.background", BG_DARK);
        UIManager.put("Viewport.foreground", TEXT_PRIMARY);

        UIManager.put("Slider.background", BG_MEDIUM);
        UIManager.put("Slider.foreground", TEXT_PRIMARY);
        UIManager.put("Slider.focus", BG_MEDIUM);
        UIManager.put("Slider.tickColor", TEXT_SECONDARY);
        UIManager.put("Slider.thumb", ACCENT);
        UIManager.put("Slider.altTrackColor", BG_LIGHT);

        UIManager.put("ProgressBar.background", BG_DARK);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.selectionBackground", TEXT_PRIMARY);
        UIManager.put("ProgressBar.selectionForeground", BG_DARK);
        UIManager.put("ProgressBar.border", BorderFactory.createLineBorder(BORDER_COLOR));

        UIManager.put("MenuBar.background", BG_MEDIUM);
        UIManager.put("MenuBar.foreground", TEXT_PRIMARY);
        UIManager.put("MenuBar.border", BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        UIManager.put("Menu.background", BG_MEDIUM);
        UIManager.put("Menu.foreground", TEXT_PRIMARY);
        UIManager.put("Menu.selectionBackground", ACCENT);
        UIManager.put("Menu.selectionForeground", Color.WHITE);
        UIManager.put("Menu.border", BorderFactory.createEmptyBorder(4, 6, 4, 6));
        UIManager.put("MenuItem.background", BG_MEDIUM);
        UIManager.put("MenuItem.foreground", TEXT_PRIMARY);
        UIManager.put("MenuItem.selectionBackground", ACCENT);
        UIManager.put("MenuItem.selectionForeground", Color.WHITE);
        UIManager.put("MenuItem.acceleratorForeground", TEXT_SECONDARY);
        UIManager.put("MenuItem.acceleratorSelectionForeground", new Color(200, 200, 200));
        UIManager.put("MenuItem.border", BorderFactory.createEmptyBorder(4, 6, 4, 6));
        UIManager.put("CheckBoxMenuItem.background", BG_MEDIUM);
        UIManager.put("CheckBoxMenuItem.foreground", TEXT_PRIMARY);
        UIManager.put("CheckBoxMenuItem.selectionBackground", ACCENT);
        UIManager.put("CheckBoxMenuItem.selectionForeground", Color.WHITE);
        UIManager.put("CheckBoxMenuItem.acceleratorForeground", TEXT_SECONDARY);
        UIManager.put("RadioButtonMenuItem.background", BG_MEDIUM);
        UIManager.put("RadioButtonMenuItem.foreground", TEXT_PRIMARY);
        UIManager.put("RadioButtonMenuItem.selectionBackground", ACCENT);
        UIManager.put("RadioButtonMenuItem.selectionForeground", Color.WHITE);
        UIManager.put("PopupMenu.background", BG_MEDIUM);
        UIManager.put("PopupMenu.foreground", TEXT_PRIMARY);
        UIManager.put("PopupMenu.border", BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        UIManager.put("Separator.background", BG_MEDIUM);
        UIManager.put("Separator.foreground", BORDER_COLOR);

        UIManager.put("TabbedPane.background", BG_DARK);
        UIManager.put("TabbedPane.foreground", TEXT_PRIMARY);
        UIManager.put("TabbedPane.selected", BG_MEDIUM);
        UIManager.put("TabbedPane.contentAreaColor", BG_DARK);
        UIManager.put("TabbedPane.light", BG_LIGHT);
        UIManager.put("TabbedPane.highlight", BG_LIGHT);
        UIManager.put("TabbedPane.shadow", BORDER_COLOR);
        UIManager.put("TabbedPane.darkShadow", BORDER_COLOR);
        UIManager.put("TabbedPane.focus", ACCENT);
        UIManager.put("TabbedPane.selectHighlight", ACCENT);

        UIManager.put("SplitPane.background", BG_DARK);
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPane.dividerFocusColor", BG_LIGHT);
        UIManager.put("SplitPane.darkShadow", BORDER_COLOR);
        UIManager.put("SplitPane.highlight", BG_LIGHT);

        UIManager.put("ToolTip.background", new Color(50, 50, 55));
        UIManager.put("ToolTip.foreground", TEXT_PRIMARY);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        UIManager.put("ToolTip.font", FONT_LABEL);

        UIManager.put("OptionPane.background", BG_DARK);
        UIManager.put("OptionPane.foreground", TEXT_PRIMARY);
        UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
        UIManager.put("OptionPane.messageFont", FONT_LABEL);
        UIManager.put("OptionPane.buttonFont", FONT_LABEL);

        UIManager.put("FileChooser.background", BG_DARK);
        UIManager.put("FileChooser.foreground", TEXT_PRIMARY);
        UIManager.put("FileChooser.listViewBackground", BG_DARK);

        UIManager.put("Table.background", BG_DARK);
        UIManager.put("Table.foreground", TEXT_PRIMARY);
        UIManager.put("Table.selectionBackground", ACCENT);
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("Table.gridColor", BORDER_COLOR);
        UIManager.put("TableHeader.background", BG_MEDIUM);
        UIManager.put("TableHeader.foreground", TEXT_PRIMARY);

        UIManager.put("Tree.background", BG_DARK);
        UIManager.put("Tree.foreground", TEXT_PRIMARY);
        UIManager.put("Tree.selectionBackground", ACCENT);
        UIManager.put("Tree.selectionForeground", Color.WHITE);
        UIManager.put("Tree.textBackground", BG_DARK);
        UIManager.put("Tree.textForeground", TEXT_PRIMARY);
        UIManager.put("Tree.hash", BORDER_COLOR);

        UIManager.put("TitledBorder.titleColor", TEXT_PRIMARY);
        UIManager.put("TitledBorder.border", BorderFactory.createLineBorder(BORDER_COLOR));

        UIManager.put("CheckBox.background", BG_MEDIUM);
        UIManager.put("CheckBox.foreground", TEXT_PRIMARY);
        UIManager.put("CheckBox.focus", ACCENT);
        UIManager.put("RadioButton.background", BG_MEDIUM);
        UIManager.put("RadioButton.foreground", TEXT_PRIMARY);
        UIManager.put("RadioButton.focus", ACCENT);

        UIManager.put("Spinner.background", BG_MEDIUM);
        UIManager.put("Spinner.foreground", TEXT_PRIMARY);
        UIManager.put("Spinner.border", BorderFactory.createLineBorder(BORDER_COLOR));

        UIManager.put("InternalFrame.background", BG_DARK);
        UIManager.put("InternalFrame.activeTitleBackground", BG_MEDIUM);
        UIManager.put("InternalFrame.activeTitleForeground", TEXT_PRIMARY);
        UIManager.put("InternalFrame.inactiveTitleBackground", BG_DARK);
        UIManager.put("InternalFrame.inactiveTitleForeground", TEXT_SECONDARY);

        UIManager.put("Desktop.background", BG_DARK);

        UIManager.put("EditorPane.background", BG_MEDIUM);
        UIManager.put("EditorPane.foreground", TEXT_PRIMARY);
        UIManager.put("EditorPane.caretForeground", TEXT_PRIMARY);
        UIManager.put("EditorPane.selectionBackground", ACCENT);
        UIManager.put("TextPane.background", BG_MEDIUM);
        UIManager.put("TextPane.foreground", TEXT_PRIMARY);
        UIManager.put("TextPane.caretForeground", TEXT_PRIMARY);
        UIManager.put("TextPane.selectionBackground", ACCENT);

        UIManager.put("PasswordField.background", BG_MEDIUM);
        UIManager.put("PasswordField.foreground", TEXT_PRIMARY);
        UIManager.put("PasswordField.caretForeground", TEXT_PRIMARY);
        UIManager.put("FormattedTextField.background", BG_MEDIUM);
        UIManager.put("FormattedTextField.foreground", TEXT_PRIMARY);
        UIManager.put("FormattedTextField.caretForeground", TEXT_PRIMARY);
    }

    private void buildUI() {
        frame.setBackground(BG_DARK);

        // Main content panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_DARK);

        // Create menu bar
        JMenuBar menuBar = createMenuBar();
        frame.setJMenuBar(menuBar);

        // Create viewport (takes majority of space)
        viewport = new ViewportPanel();
        viewport.setMinimumSize(new Dimension(400, 300));

        // Create inspector panel (fixed width, docked right)
        inspectorScrollPane = new JScrollPane(createInspectorPanel());
        inspectorScrollPane.setPreferredSize(new Dimension(INSPECTOR_WIDTH, 700));
        inspectorScrollPane.setMinimumSize(new Dimension(INSPECTOR_MIN_WIDTH, 200));
        inspectorScrollPane.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_COLOR));
        inspectorScrollPane.setBackground(BG_DARK);
        inspectorScrollPane.getViewport().setBackground(BG_DARK);
        inspectorScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        inspectorScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        styleScrollBar(inspectorScrollPane);
        statusBar = createStatusBar();
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, viewport, inspectorScrollPane);
        mainSplitPane.setResizeWeight(1.0);
        mainSplitPane.setDividerSize(4);
        mainSplitPane.setBorder(null);
        mainSplitPane.setBackground(BG_DARK);
        mainSplitPane.setContinuousLayout(true);
        mainSplitPane.setDividerLocation(Integer.MAX_VALUE);
        mainPanel.add(mainSplitPane, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        frame.setContentPane(mainPanel);
        setupInputHandlers();
        SwingUtilities.invokeLater(() -> {
            int frameWidth = frame.getWidth();
            int dividerPos = frameWidth - INSPECTOR_WIDTH - mainSplitPane.getDividerSize();
            mainSplitPane.setDividerLocation(dividerPos);
            lastDividerLocation = dividerPos;
        });
    }


    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(BG_MEDIUM);
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        JMenu fileMenu = createStyledMenu("File");
        fileMenu.add(createMenuItem("Open Model...", "Ctrl+O", this::openObjDialogAndLoad));
        fileMenu.add(createMenuItem("Load Albedo...", null, this::loadAlbedoTexture));
        fileMenu.add(createMenuItem("Load Environment...", null, this::loadEnvironmentMap));
        fileMenu.addSeparator();
        fileMenu.add(createMenuItem("Save Screenshot", "Ctrl+S", this::saveScreenshot));
        fileMenu.addSeparator();
        fileMenu.add(createMenuItem("Exit", "Alt+F4", () -> System.exit(0)));
        menuBar.add(fileMenu);
        JMenu viewMenu = createStyledMenu("View");
        viewMenu.add(createMenuItem("Toggle Fullscreen", "F11", this::toggleFullscreen));
        viewMenu.add(createMenuItem("Toggle Inspector", "Tab", this::toggleInspector));
        viewMenu.addSeparator();
        viewMenu.add(createCheckMenuItem("Wireframe", "W", renderer::isWireframe,
                                         b -> renderer.setWireframe(b)));
        viewMenu.add(createCheckMenuItem("Deferred Rendering", "D", renderer::isDeferredMode,
                                         b -> renderer.setDeferredMode(b)));
        viewMenu.add(createCheckMenuItem("Parallel Rendering", "P", renderer::isParallelEnabled,
                                         b -> renderer.setParallelEnabled(b)));
        viewMenu.add(createCheckMenuItem("Accurate sRGB", "G", renderer::isAccurateColorSpace,
                                         b -> renderer.setAccurateColorSpace(b)));
        viewMenu.add(createCheckMenuItem("Image-Based Lighting", "I", renderer::isUseIBL,
                                         b -> renderer.setUseIBL(b)));
        viewMenu.addSeparator();
        viewMenu.add(createMenuItem("Cycle Debug View", "V", () -> renderer.cycleDebugMode()));
        menuBar.add(viewMenu);
        JMenu helpMenu = createStyledMenu("Help");
        helpMenu.add(createMenuItem("Keyboard Shortcuts", "F1", this::showShortcutsDialog));
        helpMenu.add(createMenuItem("About", null, this::showAboutDialog));
        menuBar.add(helpMenu);

        return menuBar;
    }

    private JMenu createStyledMenu(String title) {
        JMenu menu = new JMenu(title);
        menu.setForeground(TEXT_PRIMARY);
        menu.setFont(FONT_LABEL);
        return menu;
    }

    private JMenuItem createMenuItem(String text, String shortcut, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(FONT_LABEL);
        item.setBackground(BG_MEDIUM);
        item.setForeground(TEXT_PRIMARY);
        if (shortcut != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(shortcut));
        }
        item.addActionListener(e -> action.run());
        return item;
    }

    private JCheckBoxMenuItem createCheckMenuItem(String text, String shortcut,
                                                   java.util.function.BooleanSupplier getter,
                                                   java.util.function.Consumer<Boolean> setter) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(text);
        item.setFont(FONT_LABEL);
        item.setBackground(BG_MEDIUM);
        item.setForeground(TEXT_PRIMARY);
        item.setSelected(getter.getAsBoolean());
        if (shortcut != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(shortcut));
        }
        item.addActionListener(e -> setter.accept(item.isSelected()));
        return item;
    }
    private JPanel createInspectorPanel() {
        JPanel inspector = new JPanel();
        inspector.setLayout(new BoxLayout(inspector, BoxLayout.Y_AXIS));
        inspector.setBackground(BG_DARK);
        inspector.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        inspector.add(createSection("Scene", createScenePanel()));
        inspector.add(Box.createRigidArea(new Dimension(0, 8)));

        inspector.add(createSection("Material", createMaterialPanel()));
        inspector.add(Box.createRigidArea(new Dimension(0, 8)));

        inspector.add(createSection("Lighting", createLightingPanel()));
        inspector.add(Box.createRigidArea(new Dimension(0, 8)));

        inspector.add(createSection("Camera", createCameraPanel()));
        inspector.add(Box.createRigidArea(new Dimension(0, 8)));

        inspector.add(createSection("Textures", createTexturesPanel()));
        inspector.add(Box.createRigidArea(new Dimension(0, 8)));

        inspector.add(createSection("Post-Processing", createPostProcessingPanel()));
        inspector.add(Box.createRigidArea(new Dimension(0, 8)));

        inspector.add(createSection("Rendering", createRenderingPanel()));

        inspector.add(Box.createVerticalGlue());

        return inspector;
    }

    private JPanel createSection(String title, JPanel content) {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(BG_MEDIUM);
        section.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, content.getPreferredSize().height + 30));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_LIGHT);
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FONT_HEADER);
        titleLabel.setForeground(TEXT_PRIMARY);
        header.add(titleLabel, BorderLayout.WEST);

        section.add(header, BorderLayout.NORTH);
        content.setBackground(BG_MEDIUM);
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        section.add(content, BorderLayout.CENTER);

        return section;
    }

    private JPanel createScenePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_MEDIUM);

        JButton loadModelBtn = createStyledButton("Open Model...");
        loadModelBtn.setToolTipText("Load an OBJ model file (Ctrl+O)");
        loadModelBtn.addActionListener(e -> openObjDialogAndLoad());
        panel.add(loadModelBtn);

        return panel;
    }

    private JPanel createMaterialPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_MEDIUM);

        JPanel presetRow = new JPanel(new BorderLayout(8, 0));
        presetRow.setBackground(BG_MEDIUM);
        presetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel presetLabel = new JLabel("Preset");
        presetLabel.setFont(FONT_LABEL);
        presetLabel.setForeground(TEXT_SECONDARY);
        presetRow.add(presetLabel, BorderLayout.WEST);

        JComboBox<Material.MaterialPreset> presetCombo = new JComboBox<>(Material.MaterialPreset.values());
        presetCombo.setSelectedItem(Material.MaterialPreset.CLAY);
        presetCombo.setFont(FONT_VALUE);
        presetCombo.setBackground(BG_LIGHT);
        presetCombo.setForeground(TEXT_PRIMARY);
        presetCombo.addActionListener(e -> applyMaterialPreset((Material.MaterialPreset) presetCombo.getSelectedItem()));
        presetRow.add(presetCombo, BorderLayout.CENTER);

        panel.add(presetRow);
        panel.add(createTooltipLabel("Quick access to realistic materials"));
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(createSliderRow("Roughness", 1, 100, (int)(renderer.getRoughness() * 100),
                v -> renderer.setRoughness(Math.max(0.01, v / 100.0)),
                "%.2f", renderer::getRoughness));
        panel.add(createTooltipLabel("Low = shiny mirror, High = matte diffuse"));
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(createSliderRow("Metalness", 0, 100, (int)(renderer.getMetalness() * 100),
                v -> renderer.setMetalness(v / 100.0),
                "%.2f", renderer::getMetalness));
        panel.add(createTooltipLabel("0 = plastic/wood, 1 = metal"));
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(createSliderRow("Specular", 0, 100, (int)(renderer.getSpecularStrength() * 100),
                v -> renderer.setSpecularStrength(v / 100.0),
                "%.2f", renderer::getSpecularStrength));
        panel.add(createTooltipLabel("Highlight intensity for non-metals"));

        return panel;
    }
    private void applyMaterialPreset(Material.MaterialPreset preset) {
        currentMaterial = Material.preset(preset);
        renderer.setRoughness(currentMaterial.getRoughness());
        renderer.setMetalness(currentMaterial.getMetalness());
        updateStatus("Material: " + preset.name());
    }
    private JLabel createTooltipLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.ITALIC, 9));
        label.setForeground(TEXT_SECONDARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        return label;
    }
    private JPanel createLightingPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_MEDIUM);
        panel.add(createSliderRow("Env Intensity", 0, 200, (int)(renderer.getEnvStrength() * 100),
                v -> renderer.setEnvStrength(v / 100.0),
                "%.2f", renderer::getEnvStrength));
        panel.add(createTooltipLabel("Environment map contribution to lighting"));
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        JToggleButton iblToggle = createToggleButton("Image-Based Lighting",
                renderer::isUseIBL, b -> renderer.setUseIBL(b));
        iblToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        iblToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        panel.add(iblToggle);
        panel.add(createTooltipLabel("Use environment map for realistic reflections"));

        return panel;
    }
    private JPanel createCameraPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_MEDIUM);
        panel.add(createSliderRow("Zoom", 10, 200, (int)(targetDistance * 10),
                v -> targetDistance = v / 10.0,
                "%.1f", () -> orbitDistance));
        panel.add(createTooltipLabel("Scroll wheel or drag to adjust"));
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(createSliderRow("Turntable", 0, 100, (int)(rotationSpeed * 1000),
                v -> rotationSpeed = v / 1000.0,
                "%.0f%%", () -> rotationSpeed * 5000)); // Convert to approx percentage
        panel.add(createTooltipLabel("Auto-rotate speed (0 = stopped)"));
        panel.add(Box.createRigidArea(new Dimension(0, 8)));

        JButton resetBtn = createStyledButton("Reset View");
        resetBtn.setToolTipText("Home key");
        resetBtn.addActionListener(e -> resetCameraView());
        panel.add(resetBtn);

        return panel;
    }

    private JPanel createTexturesPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_MEDIUM);

        JButton albedoBtn = createStyledButton("Load Albedo...");
        albedoBtn.setToolTipText("Load a color/diffuse texture");
        albedoBtn.addActionListener(e -> loadAlbedoTexture());
        panel.add(albedoBtn);

        panel.add(Box.createRigidArea(new Dimension(0, 6)));

        JButton envBtn = createStyledButton("Load Environment...");
        envBtn.setToolTipText("Load an HDR environment map for IBL");
        envBtn.addActionListener(e -> loadEnvironmentMap());
        panel.add(envBtn);

        return panel;
    }
    private JPanel createPostProcessingPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_MEDIUM);
        JToggleButton ssaoToggle = createToggleButton("SSAO",
                postProcessing::isSSAOEnabled, b -> postProcessing.setSSAOEnabled(b));
        ssaoToggle.setToolTipText("Screen Space Ambient Occlusion");
        ssaoToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        ssaoToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        panel.add(ssaoToggle);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
        panel.add(createSliderRow("AO Intensity", 0, 200, (int)(postProcessing.getSSAOIntensity() * 100),
                v -> postProcessing.setSSAOIntensity(v / 100.0),
                "%.2f", postProcessing::getSSAOIntensity));
        panel.add(Box.createRigidArea(new Dimension(0, 6)));

        JToggleButton bloomToggle = createToggleButton("Bloom",
                postProcessing::isBloomEnabled, b -> postProcessing.setBloomEnabled(b));
        bloomToggle.setToolTipText("HDR glow effect on bright areas");
        bloomToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        bloomToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        panel.add(bloomToggle);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));

        panel.add(createSliderRow("Bloom", 0, 100, (int)(postProcessing.getBloomIntensity() * 100),
                v -> postProcessing.setBloomIntensity(v / 100.0),
                "%.2f", postProcessing::getBloomIntensity));
        panel.add(Box.createRigidArea(new Dimension(0, 6)));

        JToggleButton vignetteToggle = createToggleButton("Vignette",
                postProcessing::isVignetteEnabled, b -> postProcessing.setVignetteEnabled(b));
        vignetteToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        vignetteToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        panel.add(vignetteToggle);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));

        panel.add(createSliderRow("Exposure", 10, 300, (int)(postProcessing.getExposure() * 100),
                v -> postProcessing.setExposure(v / 100.0),
                "%.2f", postProcessing::getExposure));
        panel.add(createTooltipLabel("HDR exposure adjustment"));

        return panel;
    }

    private JPanel createRenderingPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_MEDIUM);

        JPanel debugRow = new JPanel(new BorderLayout(8, 0));
        debugRow.setBackground(BG_MEDIUM);
        debugRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        JLabel debugLabel = new JLabel("Debug View");
        debugLabel.setFont(FONT_LABEL);
        debugLabel.setForeground(TEXT_SECONDARY);
        debugRow.add(debugLabel, BorderLayout.WEST);

        JComboBox<Render.DebugMode> debugCombo = new JComboBox<>(Render.DebugMode.values());
        debugCombo.setSelectedItem(renderer.getDebugMode());
        debugCombo.setFont(FONT_VALUE);
        debugCombo.setBackground(BG_LIGHT);
        debugCombo.setForeground(TEXT_PRIMARY);
        debugCombo.addActionListener(e ->
            renderer.setDebugMode((Render.DebugMode) debugCombo.getSelectedItem()));
        debugRow.add(debugCombo, BorderLayout.CENTER);

        panel.add(debugRow);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));

        JPanel toggles = new JPanel(new GridLayout(2, 2, 4, 4));
        toggles.setBackground(BG_MEDIUM);

        toggles.add(createToggleButton("Wireframe", renderer::isWireframe,
                                       b -> renderer.setWireframe(b)));
        toggles.add(createToggleButton("Deferred", renderer::isDeferredMode,
                                       b -> renderer.setDeferredMode(b)));
        toggles.add(createToggleButton("Parallel", renderer::isParallelEnabled,
                                       b -> renderer.setParallelEnabled(b)));
        toggles.add(createToggleButton("sRGB", renderer::isAccurateColorSpace,
                                       b -> renderer.setAccurateColorSpace(b)));

        panel.add(toggles);

        return panel;
    }
    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_MEDIUM);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)
        ));
        bar.setPreferredSize(new Dimension(0, STATUS_BAR_HEIGHT));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(FONT_STATUS);
        statusLabel.setForeground(TEXT_SECONDARY);
        bar.add(statusLabel, BorderLayout.WEST);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        modePanel.setBackground(BG_MEDIUM);
        bar.add(modePanel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightPanel.setBackground(BG_MEDIUM);

        JLabel resolutionLabel = new JLabel("1000×700");
        resolutionLabel.setFont(FONT_STATUS);
        resolutionLabel.setForeground(TEXT_SECONDARY);
        rightPanel.add(resolutionLabel);

        JLabel memoryLabel = new JLabel();
        memoryLabel.setFont(FONT_STATUS);
        memoryLabel.setForeground(TEXT_SECONDARY);
        rightPanel.add(memoryLabel);

        bar.add(rightPanel, BorderLayout.EAST);

        Timer updateTimer = new Timer(500, e -> {  // Update 2x per second for snappier feel
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long total = rt.maxMemory() / (1024 * 1024);
            memoryLabel.setText(String.format("Mem: %d/%dMB", used, total));

            BufferedImage img = renderer.getImage();
            if (img != null) {
                resolutionLabel.setText(String.format("%d×%d", img.getWidth(), img.getHeight()));
            }
        });
        updateTimer.start();

        return bar;
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(FONT_LABEL);
        button.setBackground(BG_LIGHT);
        button.setForeground(TEXT_PRIMARY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(ACCENT);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(BG_LIGHT);
            }
        });

        return button;
    }

    private JToggleButton createToggleButton(String text,
                                              java.util.function.BooleanSupplier getter,
                                              java.util.function.Consumer<Boolean> setter) {
        JToggleButton button = new JToggleButton(text);
        button.setFont(FONT_LABEL);
        button.setSelected(getter.getAsBoolean());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        updateToggleButtonStyle(button);

        button.addActionListener(e -> {
            setter.accept(button.isSelected());
            updateToggleButtonStyle(button);
        });

        return button;
    }

    private void updateToggleButtonStyle(JToggleButton button) {
        if (button.isSelected()) {
            button.setBackground(ACCENT);
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(BG_LIGHT);
            button.setForeground(TEXT_SECONDARY);
        }
    }

    private JPanel createSliderRow(String label, int min, int max, int initial,
                                   java.util.function.IntConsumer onChange,
                                   String format, java.util.function.DoubleSupplier getter) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(BG_MEDIUM);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel nameLabel = new JLabel(label);
        nameLabel.setFont(FONT_LABEL);
        nameLabel.setForeground(TEXT_SECONDARY);
        nameLabel.setPreferredSize(new Dimension(80, 20));
        row.add(nameLabel, BorderLayout.WEST);

        JSlider slider = new JSlider(min, max, initial);
        slider.setBackground(BG_MEDIUM);
        slider.setForeground(TEXT_PRIMARY);
        slider.setFocusable(false);
        row.add(slider, BorderLayout.CENTER);

        JLabel valueLabel = new JLabel(String.format(format, getter.getAsDouble()));
        valueLabel.setFont(FONT_VALUE);
        valueLabel.setForeground(TEXT_PRIMARY);
        valueLabel.setPreferredSize(new Dimension(45, 20));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(valueLabel, BorderLayout.EAST);

        slider.addChangeListener(e -> {
            onChange.accept(slider.getValue());
            valueLabel.setText(String.format(format, getter.getAsDouble()));
        });

        return row;
    }

    private void styleScrollBar(JScrollPane scrollPane) {
        JScrollBar vBar = scrollPane.getVerticalScrollBar();
        vBar.setBackground(BG_DARK);
        vBar.setUnitIncrement(16);
    }
    private class ViewportPanel extends JPanel {
        private final Font overlayFont = new Font("Consolas", Font.PLAIN, 11);
        private final Font overlayFontBold = new Font("Consolas", Font.BOLD, 11);

        ViewportPanel() {
            setBackground(BG_DARK);
            setPreferredSize(new Dimension(1000, 700));
            setMinimumSize(new Dimension(400, 300));
            setFocusable(true);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    requestFocusInWindow();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            BufferedImage img = renderer.getImage();
            if (img != null) {
                int imgW = img.getWidth();
                int imgH = img.getHeight();
                int panelW = getWidth();
                int panelH = getHeight();

                int x = Math.max(0, (panelW - imgW) / 2);
                int y = Math.max(0, (panelH - imgH) / 2);
                if (panelW > imgW || panelH > imgH) {
                    g.setColor(BG_DARK);
                    g.fillRect(0, 0, panelW, panelH);
                }

                g.drawImage(img, x, y, null);
                if (x > 0 || y > 0) {
                    g.setColor(BORDER_COLOR);
                    g.drawRect(x - 1, y - 1, imgW + 1, imgH + 1);
                }

                if (showDebugOverlay) {
                    drawDebugOverlay((Graphics2D) g, x, y, imgW, imgH);
                }
            }
        }
        private void drawDebugOverlay(Graphics2D g2, int ox, int oy, int vw, int vh) {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                               RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int pad = 8;
            int lineH = 14;
            int x = ox + pad;
            int y = oy + pad;
            java.util.List<String> texts = new java.util.ArrayList<>();
            java.util.List<Color> colors = new java.util.ArrayList<>();
            java.util.List<Boolean> bolds = new java.util.ArrayList<>();
            Color fpsColor = currentFps < 30 ? new Color(255, 100, 100) :
                            currentFps < 60 ? new Color(255, 200, 100) :
                            new Color(100, 255, 100);
            texts.add(String.format("%.1f FPS", currentFps));
            colors.add(fpsColor);
            bolds.add(true);

            texts.add(String.format("%d × %d", vw, vh));
            colors.add(TEXT_SECONDARY);
            bolds.add(false);
            texts.add("─────────────");
            colors.add(BORDER_COLOR);
            bolds.add(false);

            String stats = renderer.getLastFrameStats();
            int[] triStats = parseTriangleStats(stats);
            texts.add(String.format("Tris: %d / %d", triStats[0], triStats[1]));
            colors.add(TEXT_SECONDARY);
            bolds.add(false);

            if (triStats[2] > 0 || triStats[3] > 0) {
                texts.add(String.format("Culled: %d bf, %d fr", triStats[2], triStats[3]));
                colors.add(new Color(120, 120, 120));
                bolds.add(false);
            }

            texts.add("─────────────");
            colors.add(BORDER_COLOR);
            bolds.add(false);

            StringBuilder modes = new StringBuilder();
            if (renderer.isWireframe()) modes.append("[WIRE] ");
            if (renderer.isDeferredMode()) modes.append("[DEF] ");
            if (renderer.isParallelEnabled()) modes.append("[PAR] ");
            if (renderer.isAccurateColorSpace()) modes.append("[sRGB] ");
            if (renderer.isUseIBL()) modes.append("[IBL] ");

            if (!modes.isEmpty()) {
                texts.add(modes.toString().trim());
                colors.add(ACCENT);
                bolds.add(false);
            }
            Render.DebugMode dbgMode = renderer.getDebugMode();
            if (dbgMode != Render.DebugMode.NONE) {
                texts.add("VIEW: " + dbgMode.name());
                colors.add(new Color(255, 200, 50));
                bolds.add(true);
            }
            int maxW = 0;
            for (int i = 0; i < texts.size(); i++) {
                g2.setFont(bolds.get(i) ? overlayFontBold : overlayFont);
                maxW = Math.max(maxW, g2.getFontMetrics().stringWidth(texts.get(i)));
            }

            int bgW = maxW + pad * 2;
            int bgH = texts.size() * lineH + pad * 2 - 4;


            g2.setColor(new Color(15, 15, 20, 210));
            g2.fillRoundRect(x - pad / 2, y - pad / 2, bgW, bgH, 6, 6);
            g2.setColor(new Color(60, 60, 70, 100));
            g2.drawRoundRect(x - pad / 2, y - pad / 2, bgW, bgH, 6, 6);

            int ty = y + lineH - 3;
            for (int i = 0; i < texts.size(); i++) {
                g2.setFont(bolds.get(i) ? overlayFontBold : overlayFont);
                g2.setColor(colors.get(i));
                g2.drawString(texts.get(i), x, ty);
                ty += lineH;
            }

            g2.setFont(overlayFont);
            g2.setColor(new Color(80, 80, 90));
            String hint = "F3: Toggle Overlay";
            int hw = g2.getFontMetrics().stringWidth(hint);
            g2.drawString(hint, ox + vw - hw - pad, oy + vh - pad);
        }
        private int[] parseTriangleStats(String stats) {
            int[] result = {0, 0, 0, 0};
            try {
                int trisIdx = stats.indexOf("Tris:");
                if (trisIdx >= 0) {
                    int slash = stats.indexOf("/", trisIdx);
                    int space = stats.indexOf(" ", slash);
                    result[0] = Integer.parseInt(stats.substring(trisIdx + 5, slash).trim());
                    result[1] = Integer.parseInt(stats.substring(slash + 1, space).trim());
                }
                int cullIdx = stats.indexOf("Cull:");
                if (cullIdx >= 0) {
                    int slash = stats.indexOf("/", cullIdx);
                    int space = stats.indexOf(" ", slash);
                    if (space < 0) space = stats.length();
                    result[2] = Integer.parseInt(stats.substring(cullIdx + 5, slash).trim());
                    result[3] = Integer.parseInt(stats.substring(slash + 1, space).trim());
                }
            } catch (Exception ignored) {}
            return result;
        }
    }

    private class InputManager {

        record KeyBinding(
            int keyCode,
            int modifiers,
            String displayName,
            String category,
            String description,
            Runnable action
        ) {}

        private final java.util.Map<String, KeyBinding> bindings = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, String> categoryOrder = new java.util.LinkedHashMap<>();

        InputManager() {
            categoryOrder.put("file", "File");
            categoryOrder.put("view", "View");
            categoryOrder.put("camera", "Camera");
            categoryOrder.put("debug", "Debug");
            categoryOrder.put("help", "Help");
        }
        void register(String id, int keyCode, int modifiers, String category,
                      String description, Runnable action) {
            String displayName = formatShortcut(keyCode, modifiers);
            bindings.put(id, new KeyBinding(keyCode, modifiers, displayName,
                                           category, description, action));
        }
        void handleKeyEvent(KeyEvent e) {
            int keyCode = e.getKeyCode();
            int modifiers = e.getModifiersEx() &
                (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK |
                 InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK);

            for (KeyBinding binding : bindings.values()) {
                if (binding.keyCode == keyCode && binding.modifiers == modifiers) {
                    binding.action.run();
                    return;
                }
            }
        }
        java.util.Collection<KeyBinding> getAllBindings() {
            return bindings.values();
        }
        java.util.Map<String, java.util.List<KeyBinding>> getBindingsByCategory() {
            java.util.Map<String, java.util.List<KeyBinding>> result = new java.util.LinkedHashMap<>();
            for (String cat : categoryOrder.keySet()) {
                result.put(categoryOrder.get(cat), new java.util.ArrayList<>());
            }
            for (KeyBinding b : bindings.values()) {
                String displayCat = categoryOrder.getOrDefault(b.category, "Other");
                result.computeIfAbsent(displayCat, key -> new java.util.ArrayList<>()).add(b);
            }
            return result;
        }
        String generateHelpText() {
            StringBuilder sb = new StringBuilder();
            sb.append("GETTING STARTED\n");
            sb.append("─────────────────────────────\n");
            sb.append("  • Load a model with Ctrl+O or File → Open Model\n");
            sb.append("  • Orbit with left mouse drag\n");
            sb.append("  • Pan with right mouse drag\n");
            sb.append("  • Zoom with scroll wheel\n\n");

            sb.append("KEYBOARD SHORTCUTS\n");
            sb.append("─────────────────────────────\n\n");

            var byCategory = getBindingsByCategory();
            for (var entry : byCategory.entrySet()) {
                if (entry.getValue().isEmpty()) continue;

                sb.append(entry.getKey().toUpperCase()).append("\n");
                for (KeyBinding b : entry.getValue()) {
                    sb.append(String.format("  %-12s  %s\n", b.displayName, b.description));
                }
                sb.append("\n");
            }

            sb.append("MOUSE CONTROLS\n");
            sb.append("─────────────────────────────\n");
            sb.append("  Left Drag      Orbit (rotate around model)\n");
            sb.append("  Right Drag     Pan (shift view)\n");
            sb.append("  Scroll Wheel   Zoom in/out\n");
            sb.append("  L+R Drag       Alternative zoom\n");

            return sb.toString();
        }
        private String formatShortcut(int keyCode, int modifiers) {
            StringBuilder sb = new StringBuilder();

            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) sb.append("Ctrl+");
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) sb.append("Shift+");
            if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) sb.append("Alt+");
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) sb.append("Meta+");

            sb.append(KeyEvent.getKeyText(keyCode));
            return sb.toString();
        }
    }
    private void registerShortcuts() {

        inputManager.register("open_model", KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK,
            "file", "Open Model", this::openObjDialogAndLoad);
        inputManager.register("save_screenshot", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK,
            "file", "Save Screenshot", this::saveScreenshot);


        inputManager.register("toggle_wireframe", KeyEvent.VK_W, 0,
            "view", "Toggle Wireframe", () -> renderer.setWireframe(!renderer.isWireframe()));
        inputManager.register("toggle_deferred", KeyEvent.VK_D, 0,
            "view", "Toggle Deferred", () -> renderer.setDeferredMode(!renderer.isDeferredMode()));
        inputManager.register("toggle_parallel", KeyEvent.VK_P, 0,
            "view", "Toggle Parallel", () -> renderer.setParallelEnabled(!renderer.isParallelEnabled()));
        inputManager.register("toggle_srgb", KeyEvent.VK_G, 0,
            "view", "Toggle sRGB", () -> renderer.setAccurateColorSpace(!renderer.isAccurateColorSpace()));
        inputManager.register("toggle_ibl", KeyEvent.VK_I, 0,
            "view", "Toggle IBL", () -> renderer.setUseIBL(!renderer.isUseIBL()));
        inputManager.register("cycle_debug", KeyEvent.VK_V, 0,
            "view", "Cycle Debug View", renderer::cycleDebugMode);
        inputManager.register("toggle_inspector", KeyEvent.VK_TAB, 0,
            "view", "Toggle Inspector", this::toggleInspector);
        inputManager.register("toggle_fullscreen", KeyEvent.VK_F11, 0,
            "view", "Toggle Fullscreen", this::toggleFullscreen);

        inputManager.register("reset_camera", KeyEvent.VK_HOME, 0,
            "camera", "Reset Camera", this::resetCameraView);
        inputManager.register("exit_fullscreen", KeyEvent.VK_ESCAPE, 0,
            "camera", "Exit Fullscreen", () -> { if (isFullscreen) toggleFullscreen(); });


        inputManager.register("toggle_overlay", KeyEvent.VK_F3, 0,
            "debug", "Toggle Debug Overlay", () -> showDebugOverlay = !showDebugOverlay);
        inputManager.register("show_profiler", KeyEvent.VK_F2, 0,
            "debug", "Show Profiler Report", this::showProfilerReport);
        inputManager.register("print_resources", KeyEvent.VK_R, 0,
            "debug", "Print Resources", () -> {
                System.out.println(ResourceManager.getInstance().getMemoryReport());
                ResourceManager.getInstance().listResources();
            });

        inputManager.register("command_palette", KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK,
            "help", "Command Palette", () -> commandPalette.showPalette());
        inputManager.register("show_shortcuts", KeyEvent.VK_F1, 0,
            "help", "Show Shortcuts", this::showShortcutsDialog);
    }
    private void showProfilerReport() {
        String report = profiler.getReport();
        JTextArea textArea = new JTextArea(report);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        textArea.setEditable(false);
        textArea.setBackground(BG_MEDIUM);
        textArea.setForeground(TEXT_PRIMARY);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(550, 450));

        JOptionPane.showMessageDialog(frame, scrollPane, "Performance Profiler", JOptionPane.PLAIN_MESSAGE);
    }
    private void populateCommandPalette() {
        for (var binding : inputManager.getAllBindings()) {
            commandPalette.addCommand(binding.description(), binding.displayName(), binding.action());
        }


        commandPalette.addCommand("View: Normal", null, () ->
            renderer.setDebugMode(Render.DebugMode.NONE));
        commandPalette.addCommand("View: Depth Buffer", null, () ->
            renderer.setDebugMode(Render.DebugMode.DEPTH));
        commandPalette.addCommand("View: Normals", null, () ->
            renderer.setDebugMode(Render.DebugMode.NORMALS));
        commandPalette.addCommand("View: Tangents", null, () ->
            renderer.setDebugMode(Render.DebugMode.TANGENTS));
        commandPalette.addCommand("View: UVs", null, () ->
            renderer.setDebugMode(Render.DebugMode.UVS));
        commandPalette.addCommand("View: Overdraw", null, () ->
            renderer.setDebugMode(Render.DebugMode.OVERDRAW));

        commandPalette.addCommand("Material: Gold", null, () -> applyMaterialPreset(Material.MaterialPreset.GOLD));
        commandPalette.addCommand("Material: Silver", null, () -> applyMaterialPreset(Material.MaterialPreset.SILVER));
        commandPalette.addCommand("Material: Copper", null, () -> applyMaterialPreset(Material.MaterialPreset.COPPER));
        commandPalette.addCommand("Material: Chrome", null, () -> applyMaterialPreset(Material.MaterialPreset.CHROME));
        commandPalette.addCommand("Material: Plastic Red", null, () -> applyMaterialPreset(Material.MaterialPreset.PLASTIC_RED));
        commandPalette.addCommand("Material: Rubber", null, () -> applyMaterialPreset(Material.MaterialPreset.RUBBER));
        commandPalette.addCommand("Material: Marble", null, () -> applyMaterialPreset(Material.MaterialPreset.MARBLE));
        commandPalette.addCommand("Material: Glass", null, () -> applyMaterialPreset(Material.MaterialPreset.GLASS));
        commandPalette.addCommand("Material: Clay", null, () -> applyMaterialPreset(Material.MaterialPreset.CLAY));

        commandPalette.addCommand("Load Albedo Texture...", null, this::loadAlbedoTexture);
        commandPalette.addCommand("Load Environment Map...", null, this::loadEnvironmentMap);
        commandPalette.addCommand("About SimpleRenderer", null, this::showAboutDialog);
    }
    private class CommandPalette extends JDialog {
        private final JTextField searchField;
        private final JList<Command> commandList;
        private final DefaultListModel<Command> listModel;
        private final java.util.List<Command> allCommands = new java.util.ArrayList<>();

        CommandPalette(JFrame parent) {
            super(parent, false); // Non-modal
            setUndecorated(true);
            setBackground(new Color(0, 0, 0, 0));
            JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
            mainPanel.setBackground(BG_DARK);
            mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 1),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
            ));

            searchField = new JTextField();
            searchField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            searchField.setBackground(BG_MEDIUM);
            searchField.setForeground(TEXT_PRIMARY);
            searchField.setCaretColor(TEXT_PRIMARY);
            searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));
            searchField.putClientProperty("JTextField.placeholderText", "Type to search commands...");
            mainPanel.add(searchField, BorderLayout.NORTH);
            listModel = new DefaultListModel<>();
            commandList = new JList<>(listModel);
            commandList.setBackground(BG_DARK);
            commandList.setForeground(TEXT_PRIMARY);
            commandList.setSelectionBackground(ACCENT);
            commandList.setSelectionForeground(TEXT_PRIMARY);
            commandList.setFixedCellHeight(32);
            commandList.setCellRenderer(new CommandCellRenderer());

            JScrollPane scrollPane = new JScrollPane(commandList);
            scrollPane.setBorder(null);
            scrollPane.setBackground(BG_DARK);
            scrollPane.getViewport().setBackground(BG_DARK);
            mainPanel.add(scrollPane, BorderLayout.CENTER);
            JLabel hintLabel = new JLabel("↑↓ Navigate  Enter Select  Esc Close");
            hintLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            hintLabel.setForeground(TEXT_SECONDARY);
            hintLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            hintLabel.setBackground(BG_MEDIUM);
            hintLabel.setOpaque(true);
            mainPanel.add(hintLabel, BorderLayout.SOUTH);

            setContentPane(mainPanel);
            setupInputHandling();
        }

        private void setupInputHandling() {
            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { filterCommands(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { filterCommands(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { filterCommands(); }
            });
            searchField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_DOWN -> {
                            int idx = commandList.getSelectedIndex();
                            if (idx < listModel.size() - 1) {
                                commandList.setSelectedIndex(idx + 1);
                                commandList.ensureIndexIsVisible(idx + 1);
                            }
                        }
                        case KeyEvent.VK_UP -> {
                            int idx = commandList.getSelectedIndex();
                            if (idx > 0) {
                                commandList.setSelectedIndex(idx - 1);
                                commandList.ensureIndexIsVisible(idx - 1);
                            }
                        }
                        case KeyEvent.VK_ENTER -> executeSelected();
                        case KeyEvent.VK_ESCAPE -> hidePalette();
                    }
                }
            });

            commandList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        executeSelected();
                    }
                }
            });

            // Close on focus loss
            addWindowFocusListener(new WindowFocusListener() {
                public void windowGainedFocus(WindowEvent e) {}
                public void windowLostFocus(WindowEvent e) { hidePalette(); }
            });
        }

        void addCommand(String name, String shortcut, Runnable action) {
            allCommands.add(new Command(name, shortcut, action));
        }

        void showPalette() {

            searchField.setText("");
            filterCommands();

            int width = 500;
            int height = 350;
            Point parentLoc = frame.getLocationOnScreen();
            int x = parentLoc.x + (frame.getWidth() - width) / 2;
            int y = parentLoc.y + 80;

            setSize(width, height);
            setLocation(x, y);
            setVisible(true);
            searchField.requestFocusInWindow();
        }

        void hidePalette() {
            setVisible(false);
            viewport.requestFocusInWindow();
        }

        private void filterCommands() {
            String query = searchField.getText().toLowerCase().trim();
            listModel.clear();

            for (Command cmd : allCommands) {
                if (query.isEmpty() || matchesQuery(cmd.name, query)) {
                    listModel.addElement(cmd);
                }
            }
            if (!listModel.isEmpty()) {
                commandList.setSelectedIndex(0);
            }
        }
        private boolean matchesQuery(String text, String query) {
            text = text.toLowerCase();
            int textIdx = 0;
            for (char c : query.toCharArray()) {
                int found = text.indexOf(c, textIdx);
                if (found < 0) return false;
                textIdx = found + 1;
            }
            return true;
        }

        private void executeSelected() {
            Command selected = commandList.getSelectedValue();
            if (selected != null) {
                hidePalette();
                SwingUtilities.invokeLater(selected.action);
            }
        }

        private class CommandCellRenderer extends DefaultListCellRenderer {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {

                Command cmd = (Command) value;

                JPanel panel = new JPanel(new BorderLayout());
                panel.setBackground(isSelected ? ACCENT : BG_DARK);
                panel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

                JLabel nameLabel = new JLabel(cmd.name);
                nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                nameLabel.setForeground(isSelected ? Color.WHITE : TEXT_PRIMARY);
                panel.add(nameLabel, BorderLayout.WEST);

                if (cmd.shortcut != null && !cmd.shortcut.isEmpty()) {
                    JLabel shortcutLabel = new JLabel(cmd.shortcut);
                    shortcutLabel.setFont(new Font("Consolas", Font.PLAIN, 10));
                    shortcutLabel.setForeground(isSelected ? new Color(200, 200, 200) : TEXT_SECONDARY);
                    panel.add(shortcutLabel, BorderLayout.EAST);
                }

                return panel;
            }
        }
        private record Command(String name, String shortcut, Runnable action) {
            @Override
            public String toString() {
                return name;
            }
        }
    }

    private void showShortcutsDialog() {
        String shortcuts = inputManager.generateHelpText();
        JTextArea textArea = new JTextArea(shortcuts);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        textArea.setEditable(false);
        textArea.setBackground(BG_MEDIUM);
        textArea.setForeground(TEXT_PRIMARY);
        JOptionPane.showMessageDialog(frame, textArea, "Keyboard Shortcuts", JOptionPane.PLAIN_MESSAGE);
    }

    private void showAboutDialog() {
        String about = """
            SimpleRenderer v1.0
            
            A CPU-based software rasterizer
            with PBR lighting, IBL, normal mapping,
            deferred rendering, and parallel rasterization.
            
            Built with pure Java - no external libraries.
            """;
        JOptionPane.showMessageDialog(frame, about, "About SimpleRenderer", JOptionPane.INFORMATION_MESSAGE);
    }

    private void toggleFullscreen() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (!isFullscreen) {
            windowedBounds = frame.getBounds();
            lastDividerLocation = mainSplitPane.getDividerLocation();
            frame.getJMenuBar().setVisible(false);
            inspectorScrollPane.setVisible(false);
            statusBar.setVisible(false);
            if (gd.isFullScreenSupported()) {
                frame.dispose();
                frame.setUndecorated(true);
                gd.setFullScreenWindow(frame);
                frame.setVisible(true);
            } else {
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
            isFullscreen = true;
            updateStatus("Fullscreen (F11 or Esc to exit)");
        } else {
            if (gd.getFullScreenWindow() == frame) {
                gd.setFullScreenWindow(null);
                frame.dispose();
                frame.setUndecorated(false);
                frame.setVisible(true);
            }
            frame.getJMenuBar().setVisible(true);
            inspectorScrollPane.setVisible(true);
            statusBar.setVisible(true);
            frame.setBounds(windowedBounds);
            mainSplitPane.setDividerLocation(lastDividerLocation);
            isFullscreen = false;
            updateStatus("Ready");
        }
        viewport.requestFocusInWindow();
    }

    private void toggleInspector() {
        if (inspectorScrollPane.isVisible()) {
            lastDividerLocation = mainSplitPane.getDividerLocation();
            inspectorScrollPane.setVisible(false);
        } else {
            inspectorScrollPane.setVisible(true);
            mainSplitPane.setDividerLocation(lastDividerLocation);
        }
    }

    private void updateStatus(String message) {
        if (statusLabel != null) statusLabel.setText(message);
    }

    //FILE OPERATIONS

    private void openObjDialogAndLoad() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("OBJ Models", "obj"));
        chooser.setCurrentDirectory(new File("assets"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            loadObjFile(chooser.getSelectedFile());
        }
    }

    private void loadObjFile(File file) {
        try {
            updateStatus("Loading " + file.getName() + "...");
            mesh = ObjLoader.load(file.getAbsolutePath());
            Mesh.computeVertexNormals(mesh);
            // Note: Tangent computation would go here when implemented
            normalizeMesh(mesh);
            centerMesh(mesh);
            updateStatus("Loaded: " + file.getName() + " (" + mesh.triangles.size() + " triangles)");
        } catch (Exception ex) {
            updateStatus("Error loading: " + ex.getMessage());
            System.err.println("Error loading model: " + ex.getMessage());
        }
    }

    private void loadAlbedoTexture() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                Texture tex = ResourceManager.getInstance().loadTexture(chooser.getSelectedFile().getAbsolutePath());
                renderer.setAlbedoTexture(tex);
                updateStatus("Loaded albedo texture");
            } catch (Exception ex) {
                updateStatus("Error loading texture: " + ex.getMessage());
            }
        }
    }


    private void loadEnvironmentMap() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "hdr"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                EnvironmentMap envMap = ResourceManager.getInstance().loadEnvironmentMap(chooser.getSelectedFile().getAbsolutePath());
                renderer.setEnvironmentMap(envMap);
                updateStatus("Loaded environment map");
            } catch (Exception ex) {
                updateStatus("Error loading environment: " + ex.getMessage());
            }
        }
    }

    private void saveScreenshot() {
        BufferedImage img = renderer.getImage();
        if (img == null) return;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "screenshot_" + timestamp + ".png";
        try {
            ImageIO.write(img, "PNG", new File(filename));
            updateStatus("Screenshot saved: " + filename);
        } catch (IOException ex) {
            updateStatus("Error saving screenshot: " + ex.getMessage());
        }
    }

    private static void normalizeMesh(Mesh mesh) {
        double maxDist = 0;
        for (Triangle t : mesh.triangles) {
            maxDist = Math.max(maxDist, t.v0.length());
            maxDist = Math.max(maxDist, t.v1.length());
            maxDist = Math.max(maxDist, t.v2.length());
        }
        if (maxDist < 0.0001) return;
        double s = 2.0 / maxDist;
        for (Triangle t : mesh.triangles) {
            t.v0 = t.v0.scale(s);
            t.v1 = t.v1.scale(s);
            t.v2 = t.v2.scale(s);
        }
    }

    private static void centerMesh(Mesh mesh) {
        double cx = 0, cy = 0, cz = 0;
        int count = 0;
        for (Triangle t : mesh.triangles) {
            cx += t.v0.x + t.v1.x + t.v2.x;
            cy += t.v0.y + t.v1.y + t.v2.y;
            cz += t.v0.z + t.v1.z + t.v2.z;
            count += 3;
        }
        if (count == 0) return;
        Vector3 center = new Vector3(cx / count, cy / count, cz / count);
        for (Triangle t : mesh.triangles) {
            t.v0 = t.v0.sub(center);
            t.v1 = t.v1.sub(center);
            t.v2 = t.v2.sub(center);
        }
    }

    private void updateCameraOrbit() {
        cameraYaw += (targetYaw - cameraYaw) * CAMERA_DAMPING;
        cameraPitch += (targetPitch - cameraPitch) * CAMERA_DAMPING;
        orbitDistance += (targetDistance - orbitDistance) * CAMERA_DAMPING;
        panX += (targetPanX - panX) * CAMERA_DAMPING;
        panY += (targetPanY - panY) * CAMERA_DAMPING;

        double x = orbitDistance * Math.cos(cameraPitch) * Math.sin(cameraYaw);
        double y = orbitDistance * Math.sin(cameraPitch);
        double z = -orbitDistance * Math.cos(cameraPitch) * Math.cos(cameraYaw);

        Vector3 focusPoint = new Vector3(panX, panY, 0);
        camera.position = new Vector3(x + panX, y + panY, z);
        camera.lookAt(focusPoint);
    }

    private void resetCameraView() {
        targetYaw = 0.0;
        targetPitch = 0.0;
        targetDistance = 5.0;
        targetPanX = 0.0;
        targetPanY = 0.0;
        rotationSpeed = 0.02;
    }
    private void setupInputHandlers() {

        viewport.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                if (SwingUtilities.isLeftMouseButton(e)) {
                    isDraggingLeft = true;
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    isDraggingRight = true;
                }
                viewport.requestFocusInWindow();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    isDraggingLeft = false;
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    isDraggingRight = false;
                }
            }
        });

        viewport.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastMouseX;
                int dy = e.getY() - lastMouseY;
                lastMouseX = e.getX();
                lastMouseY = e.getY();

                if (isDraggingLeft && !isDraggingRight) {
                    targetYaw += dx * ORBIT_SENSITIVITY;
                    targetPitch += dy * ORBIT_SENSITIVITY;
                    targetPitch = Math.max(-Math.PI / 2 + 0.1, Math.min(Math.PI / 2 - 0.1, targetPitch));
                } else if (isDraggingRight && !isDraggingLeft) {
                    targetPanX -= dx * PAN_SENSITIVITY;
                    targetPanY += dy * PAN_SENSITIVITY;
                } else if (isDraggingLeft && isDraggingRight) {
                    targetDistance += dy * ZOOM_SENSITIVITY * 0.1;
                    targetDistance = Math.max(1.0, Math.min(50.0, targetDistance));
                }
            }
        });
        viewport.addMouseWheelListener(e -> {
            targetDistance += e.getWheelRotation() * ZOOM_SENSITIVITY;
            targetDistance = Math.max(1.0, Math.min(50.0, targetDistance));
        });
        viewport.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                inputManager.handleKeyEvent(e);
            }
        });
    }

    @Override
    public void run() {
        while (true) {
            profiler.beginFrame();

            long frameStart = System.nanoTime();
            angle += rotationSpeed;
            updateCameraOrbit();

            profiler.beginSection("Clear");
            renderer.clear();
            profiler.endSection("Clear");

            profiler.beginSection("Render");
            renderer.render(mesh, camera, angle);
            profiler.endSection("Render");

            viewport.repaint();

            profiler.endFrame();

            frameCount++;
            long now = System.nanoTime();
            if (now - lastFpsTime >= 1_000_000_000L) {
                currentFps = frameCount * 1_000_000_000.0 / (now - lastFpsTime);
                frameCount = 0;
                lastFpsTime = now;
                String stats = renderer.getLastFrameStats();
                SwingUtilities.invokeLater(() -> updateStatus(String.format("%.1f FPS | %s", currentFps, stats)));
            }

            long frameTime = System.nanoTime() - frameStart;
            long sleepMs = Math.max(1, 16 - frameTime / 1_000_000);
            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
        }
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SimpleRenderer Pro - CPU Software Rasterizer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setBackground(BG_DARK);

            Main app = new Main(frame);

            frame.setPreferredSize(new Dimension(1280, 800));
            frame.pack();
            frame.setMinimumSize(new Dimension(900, 600));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            app.viewport.requestFocusInWindow();
        });
    }
}
