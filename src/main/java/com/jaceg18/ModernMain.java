package com.jaceg18;

import com.jaceg18.Gameplay.Opening.OpeningBook;
import com.jaceg18.Gameplay.Search.AI.AiFactory;
import com.jaceg18.Gameplay.Search.AI.SearchConstants;
import com.jaceg18.Gameplay.Search.SearchEngine;
import com.jaceg18.Gameplay.UI.ChessBoardPanel;
import com.jaceg18.Gameplay.UI.EngineAdapter;
import com.jaceg18.Gameplay.UI.ModernConsolePanel;
import com.jaceg18.Gameplay.UI.ModernTheme;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

public class ModernMain {
    private static final String APP_TITLE = "Modern Chess Engine";
    private static final String APP_VERSION = "v2.0";
    private static ModernConsolePanel console;
    
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        
        SwingUtilities.invokeLater(ModernMain::createAndShowGUI);
    }
    
    private static void createAndShowGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            customizeUIDefaults();
            
        } catch (Exception e) {
            System.err.println("Could not set look and feel: " + e.getMessage());
        }
        JFrame frame = new JFrame(APP_TITLE + " " + APP_VERSION);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        try {
        } catch (Exception e) {
        }
        ChessBoardPanel board = new ChessBoardPanel();
        console = new ModernConsolePanel(board);
        ChessBoardPanel.console = console;
        SearchEngine engine = setupChessEngine();
        board.setAi(new EngineAdapter(engine));
        JSplitPane mainSplit = createMainLayout(board, console);
        frame.add(mainSplit, BorderLayout.CENTER);
        JMenuBar menuBar = createMenuBar(frame, board, engine);
        frame.setJMenuBar(menuBar);
        JPanel statusBar = createStatusBar();
        frame.add(statusBar, BorderLayout.SOUTH);
        frame.setMinimumSize(new Dimension(1200, 800));
        frame.setPreferredSize(new Dimension(1400, 900));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int choice = JOptionPane.showConfirmDialog(
                    frame,
                    "Are you sure you want to exit?",
                    "Confirm Exit",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                );
                
                if (choice == JOptionPane.YES_OPTION) {
                    System.exit(0);
                }
            }
        });
        frame.setVisible(true);
        console.logInfo("Welcome to " + APP_TITLE + " " + APP_VERSION);
        console.logInfo("Ready to play! Use the buttons or keyboard shortcuts to control the game.");
    }

    public static void displayOpening(String str){
        console.displayOpening(str);
    }
    
    private static void customizeUIDefaults() {
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ScrollBar.thumbArc", 8);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("TabbedPane.tabArc", 8);
        UIManager.put("TabbedPane.cardArc", 8);
    }
    
    private static SearchEngine setupChessEngine() {
        SearchEngine engine = AiFactory.balanced();
        engine.setMaxDepth(SearchConstants.MAX_DEPTH);
        
        try {
            OpeningBook openingBook = OpeningBook.load("src/main/resources/openings.txt");
            engine.setOpeningBook(openingBook);
            System.out.println("Opening book loaded successfully");
        } catch (IOException e) {
            System.err.println("Warning: Opening book not found - " + e.getMessage());
        }
        
        return engine;
    }
    
    private static JSplitPane createMainLayout(ChessBoardPanel board, ModernConsolePanel console) {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, board, console);
        splitPane.setBorder(null);
        splitPane.setDividerSize(8);
        splitPane.setContinuousLayout(true);
        splitPane.setOneTouchExpandable(false);
        splitPane.setResizeWeight(0.65);
        splitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                super.paint(g, c);
            }
        });
        
        return splitPane;
    }
    
    private static JMenuBar createMenuBar(JFrame frame, ChessBoardPanel board, SearchEngine engine) {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(ModernTheme.SURFACE);
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.CONSOLE_BORDER));
        JMenu gameMenu = new JMenu("Game");
        gameMenu.add(createMenuItem("New Game", e -> board.resetGame()));
        gameMenu.addSeparator();
        gameMenu.add(createMenuItem("Undo Move", e -> board.undoMove()));
        gameMenu.add(createMenuItem("Flip Board", e -> {
            board.whiteAtBottom = !board.whiteAtBottom;
            board.repaint();
        }));
        gameMenu.addSeparator();
        gameMenu.add(createMenuItem("Exit", e -> frame.dispatchEvent(
            new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))));
        JMenu engineMenu = new JMenu("Engine");
        engineMenu.add(createMenuItem("AI plays White", e -> board.setAiPlaysWhite(true)));
        engineMenu.add(createMenuItem("AI plays Black", e -> board.setAiPlaysBlack(true)));
        engineMenu.add(createMenuItem("AI vs AI", e -> board.setAiBoth(true)));
        engineMenu.add(createMenuItem("Human vs Human", e -> board.setAiNone()));
        engineMenu.addSeparator();
        
        JMenu depthMenu = new JMenu("Search Depth");
        for (int depth = 6; depth <= 15; depth++) {
            final int d = depth;
            depthMenu.add(createMenuItem("Depth " + depth, e -> engine.setMaxDepth(d)));
        }
        engineMenu.add(depthMenu);
        JMenu analysisMenu = new JMenu("Analysis");
        analysisMenu.add(createMenuItem("Probe Tablebase", e -> board.probeTB()));
        analysisMenu.add(createMenuItem("Show Evaluation", e -> {
        }));
        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(createMenuItem("Keyboard Shortcuts", e -> showKeyboardShortcuts(frame)));
        helpMenu.add(createMenuItem("About", e -> showAboutDialog(frame)));
        
        menuBar.add(gameMenu);
        menuBar.add(engineMenu);
        menuBar.add(analysisMenu);
        menuBar.add(Box.createHorizontalGlue());
        menuBar.add(helpMenu);
        
        return menuBar;
    }
    
    private static JMenuItem createMenuItem(String text, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(action);
        item.setFont(ModernTheme.getUIFont(12));
        return item;
    }
    
    private static JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(ModernTheme.SURFACE);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ModernTheme.CONSOLE_BORDER));
        statusBar.setPreferredSize(new Dimension(0, 24));
        
        JLabel statusLabel = new JLabel("Ready");
        statusLabel.setFont(ModernTheme.getUIFont(11));
        statusLabel.setForeground(ModernTheme.TEXT_SECONDARY);
        statusLabel.setBorder(new javax.swing.border.EmptyBorder(4, 8, 4, 8));
        
        JLabel versionLabel = new JLabel(APP_VERSION);
        versionLabel.setFont(ModernTheme.getUIFont(11));
        versionLabel.setForeground(ModernTheme.TEXT_SECONDARY);
        versionLabel.setBorder(new javax.swing.border.EmptyBorder(4, 8, 4, 8));
        
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(versionLabel, BorderLayout.EAST);
        
        return statusBar;
    }
    
    private static void showKeyboardShortcuts(JFrame parent) {
        String shortcuts = """
            Keyboard Shortcuts:
            
            Ctrl+Z       - Undo move
            Ctrl+W       - AI plays White
            Ctrl+B       - AI plays Black
            Ctrl+T       - Probe tablebase
            Ctrl+↑       - Increase thinking time
            Ctrl+↓       - Decrease thinking time
            F            - Flip board
            
            Mouse Controls:
            Click piece  - Select piece
            Click square - Move piece
            Drag & Drop  - Move piece
            """;
        
        JOptionPane.showMessageDialog(parent, shortcuts, "Keyboard Shortcuts", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private static void showAboutDialog(JFrame parent) {
        String about = """
            %s %s
            
            A modern chess engine with beautiful UI
            Built with Java Swing
            
            Features:
            • Advanced chess AI with search algorithms
            • Opening book support
            • Tablebase integration
            • Modern, responsive interface
            • Smooth animations and effects
            
            © 2024 Chess Engine Project
            """.formatted(APP_TITLE, APP_VERSION);
        
        JOptionPane.showMessageDialog(parent, about, "About " + APP_TITLE, 
            JOptionPane.INFORMATION_MESSAGE);
    }
}
