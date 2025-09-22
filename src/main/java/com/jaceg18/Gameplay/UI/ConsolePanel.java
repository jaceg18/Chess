package com.jaceg18.Gameplay.UI;


import com.jaceg18.Gameplay.Utility.GameState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;



public class ConsolePanel extends JPanel {
    private final JTextPane area = new JTextPane();
    private final StyledDocument doc = area.getStyledDocument();
    private final EvalBar evalBar = new EvalBar();
    private final ChessBoardPanel board;

    private final JLabel tbBest   = new JLabel("—");
    private final JLabel tbDTM    = new JLabel("—");
    private final JLabel tbDTZ    = new JLabel("—");
    private final JLabel tbStatus = new JLabel("—");



    public ConsolePanel(ChessBoardPanel board) {
        this.board = board;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(6, 8, 6, 8));

        setPreferredSize(new Dimension(700, ChessBoardPanel.BOARD_PX));
        setMinimumSize(new Dimension(260, 100));

        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        add(new JScrollPane(area, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        JButton clear = new JButton("Clear");
        clear.addActionListener(_ -> area.setText(""));
        tb.add(clear);


        JButton undoBtn   = new JButton("↶ Undo");
        undoBtn.setToolTipText("Undo last move (Ctrl+Z)");
        undoBtn.addActionListener(e -> board.undoMove());

        JButton depthDown = new JButton("Depth −");
        depthDown.setToolTipText("Decrease AI depth (Ctrl+↓)");
        depthDown.addActionListener(e -> board.adjustDepth(-1));

        JButton depthUp   = new JButton("Depth +");
        depthUp.setToolTipText("Increase AI depth (Ctrl+↑)");
        depthUp.addActionListener(e -> board.adjustDepth(+1));

        JButton flipBtn   = new JButton("Flip");
        flipBtn.setToolTipText("Flip board (F)");
        flipBtn.addActionListener(e -> { board.whiteAtBottom = !board.whiteAtBottom; repaint(); });

        JButton aiWhite   = new JButton("AI: White");
        aiWhite.setToolTipText("Set AI to play White (Ctrl+W)");
        aiWhite.addActionListener(e -> board.setAiPlaysWhite(true));

        JButton aiBlack   = new JButton("AI: Black");
        aiBlack.setToolTipText("Set AI to play Black (Ctrl+B)");
        aiBlack.addActionListener(e -> board.setAiPlaysBlack(true));

        tb.add(undoBtn);
        tb.addSeparator(new Dimension(8, 0));
        tb.add(depthDown);
        tb.add(depthUp);
        tb.addSeparator(new Dimension(8, 0));
        tb.add(flipBtn);
        tb.addSeparator(new Dimension(8, 0));
        tb.add(aiWhite);
        tb.add(aiBlack);

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.add(new JScrollPane(area,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        centerWrap.add(evalBar, BorderLayout.EAST);
        add(centerWrap, BorderLayout.CENTER);

        add(tb, BorderLayout.NORTH);

        JButton both = new JButton("AI vs AI");
        both.addActionListener(e -> board.setAiBoth(true));

        JButton human = new JButton("Human vs Human");
        human.addActionListener(e -> board.setAiNone());

        tb.addSeparator();
        tb.add(both);
        tb.add(human);

        JButton tbProbe = new JButton("Probe TB");
        tbProbe.setToolTipText("Query 7-man tablebase (Ctrl+T)");
        tbProbe.addActionListener(e -> board.probeTB());
        tb.addSeparator(new Dimension(8, 0));
        tb.add(tbProbe);

        add(centerWrap, BorderLayout.CENTER);

        JPanel tbInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        tbInfo.add(new JLabel("TB Best:"));   tbInfo.add(tbBest);
        tbInfo.add(new JLabel("DTM:"));       tbInfo.add(tbDTM);
        tbInfo.add(new JLabel("DTZ:"));       tbInfo.add(tbDTZ);
        tbInfo.add(new JLabel("Status:"));    tbInfo.add(tbStatus);
        add(tbInfo, BorderLayout.SOUTH);

        addStyle("INFO", Color.BLUE);
        addStyle("WARN", new Color(200, 120, 0));
        addStyle("MOVE", new Color(0, 140, 0));
        addStyle("DEFAULT", Color.DARK_GRAY);
    }

    public void setEvalCp(int cp) { evalBar.setEvalCp(cp); }

    private void addStyle(String name, Color color) {
        Style style = area.addStyle(name, null);
        StyleConstants.setForeground(style, color);
    }
    public void setTbInfo(String category, Integer dtm, Integer dtz, String bestUci) {
        tbBest.setText(bestUci != null ? bestUci : "—");
        tbDTM.setText(dtm != null ? dtm.toString() : "—");
        tbDTZ.setText(dtz != null ? dtz.toString() : "—");
        tbStatus.setText(category != null ? category : "unknown");
        Color c = Color.DARK_GRAY;
        if ("win".equalsIgnoreCase(category))  c = new Color(0,128,0);
        if ("draw".equalsIgnoreCase(category)) c = new Color(80,80,80);
        if ("loss".equalsIgnoreCase(category)) c = new Color(170,0,0);
        tbStatus.setForeground(c);
    }

    public void clearTbInfo() {
        tbBest.setText("—");
        tbDTM.setText("—");
        tbDTZ.setText("—");
        tbStatus.setText("—");
        tbStatus.setForeground(Color.DARK_GRAY);
    }

    private void log(String level, String msg, String style) {
        try {
            doc.insertString(doc.getLength(), level + ": " + msg + "\n", area.getStyle(style));
            area.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            logWarn("Failed to log!");
        }
    }

    public void logInfo(String msg) { log("INFO", msg, "INFO"); }
    public void logWarn(String msg) { log("WARN", msg, "WARN"); }
    public void logMove(GameState state, int m) {
        String uci = toUCI(m);
        log("MOVE", uci + (GameState.isCapture(m) ? " x" : ""), "MOVE");
    }



    private String toUCI(int m) {
        int f = GameState.from(m), t = GameState.to(m);
        return sq(f) + sq(t);
    }
    private String sq(int s) { return "abcdefgh".charAt(s & 7) + String.valueOf((s >>> 3) + 1); }
}
