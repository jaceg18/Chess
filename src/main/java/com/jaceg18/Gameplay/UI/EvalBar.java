package com.jaceg18.Gameplay.UI;

import javax.swing.*;
import java.awt.*;

public class EvalBar extends JComponent {
    private int evalCp = 0;

    private static final int CLAMP = 1000;

    public EvalBar() {
        setPreferredSize(new Dimension(28, 160));
        setMinimumSize(new Dimension(24, 120));
        setOpaque(false);
        setToolTipText("Evaluation (white minus black, centipawns)");
    }

    public void setEvalCp(int cp) {
        int clamped = Math.max(-CLAMP, Math.min(CLAMP, cp));
        if (clamped != this.evalCp) {
            this.evalCp = clamped;
            repaint();
        }
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        g.setColor(new Color(30, 30, 30, 80));
        g.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);


        double t = (evalCp + CLAMP) / (2.0 * CLAMP);
        int fill = (int) Math.round(t * (h - 10));


        g.setColor(new Color(235, 235, 235));
        g.fillRoundRect(5, 5, w - 10, fill, 8, 8);

        g.setColor(new Color(20, 20, 20));
        g.fillRoundRect(5, 5 + fill, w - 10, (h - 10) - fill, 8, 8);


        g.setColor(new Color(0, 0, 0, 60));
        int mid = (h - 10) / 2 + 5;
        g.drawLine(7, mid, w - 7, mid);
    }
}

