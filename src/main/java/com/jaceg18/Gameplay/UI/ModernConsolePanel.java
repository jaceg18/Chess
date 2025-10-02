package com.jaceg18.Gameplay.UI;

import com.jaceg18.Gameplay.Utility.GameState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;

public class ModernConsolePanel extends JPanel implements ConsoleInterface {
    private final JTextPane logArea = new JTextPane();
    private final StyledDocument doc = logArea.getStyledDocument();
    private final EvalBar evalBar = new EvalBar();
    private final ChessBoardPanel board;
    private final OpeningPanel openingPanel = new OpeningPanel();
    
    private final JLabel gameStatusLabel = new JLabel("Ready to play");
    private final JLabel moveCountLabel = new JLabel("Move: 1");
    private final JLabel timeLabel = new JLabel("Time: 5.0s");
    
    private final JLabel tbBest = new JLabel("—");
    private final JLabel tbDTM = new JLabel("—");
    private final JLabel tbDTZ = new JLabel("—");
    private final JLabel tbStatus = new JLabel("—");
    
    public ModernConsolePanel(ChessBoardPanel board) {
        this.board = board;
        initializeComponents();
        setupLayout();
        setupStyling();
    }
    
    private void initializeComponents() {
        logArea.setEditable(false);
        logArea.setFont(ModernTheme.getMonospaceFont(12));
        logArea.setBackground(ModernTheme.CONSOLE_BG);
        logArea.setBorder(new EmptyBorder(8, 12, 8, 12));
        gameStatusLabel.setFont(ModernTheme.getUIFontBold(14));
        gameStatusLabel.setForeground(ModernTheme.TEXT_PRIMARY);
        
        moveCountLabel.setFont(ModernTheme.getUIFont(12));
        moveCountLabel.setForeground(ModernTheme.TEXT_SECONDARY);
        
        timeLabel.setFont(ModernTheme.getUIFont(12));
        timeLabel.setForeground(ModernTheme.TEXT_SECONDARY);
        setupTablebaseLabels();
        addTextStyles();
    }
    
    private void setupTablebaseLabels() {
        Font tbFont = ModernTheme.getUIFont(11);
        tbBest.setFont(tbFont);
        tbDTM.setFont(tbFont);
        tbDTZ.setFont(tbFont);
        tbStatus.setFont(tbFont);
        
        tbBest.setForeground(ModernTheme.TEXT_SECONDARY);
        tbDTM.setForeground(ModernTheme.TEXT_SECONDARY);
        tbDTZ.setForeground(ModernTheme.TEXT_SECONDARY);
        tbStatus.setForeground(ModernTheme.TEXT_SECONDARY);
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        add(openingPanel, BorderLayout.NORTH);
        JPanel centerPanel = createCenterPanel();
        add(centerPanel, BorderLayout.CENTER);
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(8, 0));
        centerPanel.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(logArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createLineBorder(ModernTheme.CONSOLE_BORDER, 1));
        scrollPane.getViewport().setBackground(ModernTheme.CONSOLE_BG);
        
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(evalBar, BorderLayout.EAST);
        
        return centerPanel;
    }
    
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 8));
        bottomPanel.setOpaque(false);
        JPanel gameInfoPanel = createGameInfoPanel();
        bottomPanel.add(gameInfoPanel, BorderLayout.NORTH);
        JPanel controlPanel = createControlPanel();
        bottomPanel.add(controlPanel, BorderLayout.CENTER);
        JPanel tbPanel = createTablebasePanel();
        bottomPanel.add(tbPanel, BorderLayout.SOUTH);
        
        return bottomPanel;
    }
    
    private JPanel createGameInfoPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        panel.setOpaque(false);
        
        panel.add(gameStatusLabel);
        panel.add(createSeparator());
        panel.add(moveCountLabel);
        panel.add(createSeparator());
        panel.add(timeLabel);
        
        return panel;
    }
    
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 0, 8));
        panel.setOpaque(false);
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row1.setOpaque(false);
        row1.add(new ModernButton("↶ Undo", ModernButton.ButtonStyle.SECONDARY));
        row1.add(new ModernButton("Clear Log", ModernButton.ButtonStyle.SECONDARY));
        row1.add(new ModernButton("Flip Board", ModernButton.ButtonStyle.SECONDARY));
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row2.setOpaque(false);
        row2.add(new ModernButton("AI: White", ModernButton.ButtonStyle.PRIMARY));
        row2.add(new ModernButton("AI: Black", ModernButton.ButtonStyle.PRIMARY));
        row2.add(new ModernButton("AI vs AI", ModernButton.ButtonStyle.ACCENT));
        row2.add(new ModernButton("Human vs Human", ModernButton.ButtonStyle.SUCCESS));
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row3.setOpaque(false);
        row3.add(new ModernButton("Time −", ModernButton.ButtonStyle.WARNING));
        row3.add(new ModernButton("Time +", ModernButton.ButtonStyle.WARNING));
        row3.add(new ModernButton("Probe TB", ModernButton.ButtonStyle.SECONDARY));
        wireButtonActions(row1, row2, row3);
        
        panel.add(row1);
        panel.add(row2);
        panel.add(row3);
        
        return panel;
    }
    
    private void wireButtonActions(JPanel row1, JPanel row2, JPanel row3) {
        ((ModernButton) row1.getComponent(0)).addActionListener(e -> board.undoMove());
        ((ModernButton) row1.getComponent(1)).addActionListener(e -> logArea.setText(""));
        ((ModernButton) row1.getComponent(2)).addActionListener(e -> {
            board.whiteAtBottom = !board.whiteAtBottom;
            board.repaint();
        });
        ((ModernButton) row2.getComponent(0)).addActionListener(e -> board.setAiPlaysWhite(true));
        ((ModernButton) row2.getComponent(1)).addActionListener(e -> board.setAiPlaysBlack(true));
        ((ModernButton) row2.getComponent(2)).addActionListener(e -> board.setAiBoth(true));
        ((ModernButton) row2.getComponent(3)).addActionListener(e -> board.setAiNone());
        ((ModernButton) row3.getComponent(0)).addActionListener(e -> {
            board.adjustTime(-500);
            setThinkingTime(board.AI_THINKING_TIME_MS);
        });
        ((ModernButton) row3.getComponent(1)).addActionListener(e -> {
            board.adjustTime(+500);
            setThinkingTime(board.AI_THINKING_TIME_MS);
        });
        ((ModernButton) row3.getComponent(2)).addActionListener(e -> board.probeTB());
    }
    
    private JPanel createTablebasePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ModernTheme.CONSOLE_BORDER),
                "Tablebase Info",
                0, 0,
                ModernTheme.getUIFont(11),
                ModernTheme.TEXT_SECONDARY
        ));
        
        panel.add(createLabelPair("Best:", tbBest));
        panel.add(createLabelPair("DTM:", tbDTM));
        panel.add(createLabelPair("DTZ:", tbDTZ));
        panel.add(createLabelPair("Status:", tbStatus));
        
        return panel;
    }
    
    private JPanel createLabelPair(String label, JLabel value) {
        JPanel pair = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pair.setOpaque(false);
        
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(ModernTheme.getUIFont(11));
        labelComp.setForeground(ModernTheme.TEXT_SECONDARY);
        
        pair.add(labelComp);
        pair.add(value);
        
        return pair;
    }
    
    private JLabel createSeparator() {
        JLabel sep = new JLabel("•");
        sep.setForeground(ModernTheme.TEXT_SECONDARY);
        return sep;
    }
    
    private void setupStyling() {
        setBackground(ModernTheme.BACKGROUND);
        setPreferredSize(new Dimension(450, ChessBoardPanel.BOARD_PX));
        setMinimumSize(new Dimension(350, 400));
    }
    
    private void addTextStyles() {
        addStyle("INFO", ModernTheme.INFO);
        addStyle("WARN", ModernTheme.WARNING);
        addStyle("ERROR", ModernTheme.ERROR);
        addStyle("MOVE", ModernTheme.SUCCESS);
        addStyle("OPENING", ModernTheme.ACCENT);
        addStyle("DEFAULT", ModernTheme.TEXT_PRIMARY);
    }
    
    private void addStyle(String name, Color color) {
        Style style = logArea.addStyle(name, null);
        StyleConstants.setForeground(style, color);
        if ("OPENING".equals(name)) {
            StyleConstants.setBold(style, true);
        }
    }
    
    public void setEvalCp(int cp) {
        evalBar.setEvalCp(cp);
    }
    
    public void setGameStatus(String status) {
        gameStatusLabel.setText(status);
    }
    
    public void setMoveCount(int fullMoves) {
        moveCountLabel.setText("Move: " + fullMoves);
    }
    
    public void setThinkingTime(double seconds) {
        timeLabel.setText(String.format("Time: %.1fs", seconds));
    }
    
    public void displayOpening(String openingName) {
        openingPanel.displayOpening(openingName);
        logOpening(openingName);
    }
    
    public void setTbInfo(String category, Integer dtm, Integer dtz, String bestUci) {
        tbBest.setText(bestUci != null ? bestUci : "—");
        tbDTM.setText(dtm != null ? dtm.toString() : "—");
        tbDTZ.setText(dtz != null ? dtz.toString() : "—");
        tbStatus.setText(category != null ? category : "unknown");
        
        Color statusColor = ModernTheme.TEXT_SECONDARY;
        if ("win".equalsIgnoreCase(category)) statusColor = ModernTheme.SUCCESS;
        else if ("draw".equalsIgnoreCase(category)) statusColor = ModernTheme.TEXT_SECONDARY;
        else if ("loss".equalsIgnoreCase(category)) statusColor = ModernTheme.ERROR;
        
        tbStatus.setForeground(statusColor);
    }
    
    public void clearTbInfo() {
        tbBest.setText("—");
        tbDTM.setText("—");
        tbDTZ.setText("—");
        tbStatus.setText("—");
        tbStatus.setForeground(ModernTheme.TEXT_SECONDARY);
    }
    
    private void log(String level, String msg, String style) {
        try {
            String timestamp = String.format("[%02d:%02d] ", 
                System.currentTimeMillis() / 60000 % 60,
                System.currentTimeMillis() / 1000 % 60);
            doc.insertString(doc.getLength(), timestamp + level + ": " + msg + "\n", 
                logArea.getStyle(style));
            logArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            System.err.println("Failed to log: " + msg);
        }
    }
    
    public void logInfo(String msg) {
        log("INFO", msg, "INFO");
    }
    
    public void logWarn(String msg) {
        log("WARN", msg, "WARN");
    }
    
    public void logError(String msg) {
        log("ERROR", msg, "ERROR");
    }
    
    public void logMove(GameState state, int move) {
        String uci = toUCI(move);
        String moveText = uci + (GameState.isCapture(move) ? " (capture)" : "");
        log("MOVE", moveText, "MOVE");
    }
    
    public void logOpening(String openingName) {
        log("OPENING", openingName, "OPENING");
    }
    
    private String toUCI(int move) {
        int from = GameState.from(move);
        int to = GameState.to(move);
        return squareToString(from) + squareToString(to);
    }
    
    private String squareToString(int square) {
        return "abcdefgh".charAt(square & 7) + String.valueOf((square >>> 3) + 1);
    }
}
