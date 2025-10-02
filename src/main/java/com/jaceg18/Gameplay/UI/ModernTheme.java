package com.jaceg18.Gameplay.UI;

import java.awt.*;

public class ModernTheme {
    public static final Color LIGHT_SQ = new Color(255, 248, 220);
    public static final Color DARK_SQ = new Color(139, 69, 19);
    public static final Color LIGHT_EDGE = new Color(255, 255, 240);
    public static final Color DARK_EDGE = new Color(101, 67, 33);
    
    public static final Color HOVER = new Color(65, 105, 225, 40);
    public static final Color SELECT = new Color(50, 205, 50, 120);
    public static final Color LEGAL = new Color(255, 215, 0, 100);
    public static final Color CHECK = new Color(220, 20, 60, 140);
    public static final Color LAST_MOVE = new Color(255, 165, 0, 80);
    
    public static final Color PRIMARY = new Color(63, 81, 181);
    public static final Color PRIMARY_DARK = new Color(48, 63, 159);
    public static final Color ACCENT = new Color(255, 87, 34);
    public static final Color BACKGROUND = new Color(250, 250, 250);
    public static final Color SURFACE = Color.WHITE;
    public static final Color ON_SURFACE = new Color(33, 33, 33);
    public static final Color ON_PRIMARY = Color.WHITE;
    
    public static final Color CONSOLE_BG = new Color(248, 249, 250);
    public static final Color CONSOLE_BORDER = new Color(218, 220, 224);
    public static final Color TEXT_PRIMARY = new Color(32, 33, 36);
    public static final Color TEXT_SECONDARY = new Color(95, 99, 104);
    
    public static final Color SUCCESS = new Color(76, 175, 80);
    public static final Color WARNING = new Color(255, 152, 0);
    public static final Color ERROR = new Color(244, 67, 54);
    public static final Color INFO = new Color(33, 150, 243);
    
    public static final Color BOARD_BORDER = new Color(62, 39, 35, 200);
    public static final Color COORD = new Color(101, 67, 33, 180);
    
    public static final int ANIMATION_DURATION = 250;
    public static final int CORNER_RADIUS = 8;
    public static final BasicStroke THICK_STROKE = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final BasicStroke THIN_STROKE = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final BasicStroke DASH_STROKE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{8f, 4f}, 0f);
    
    public static Font getCoordFont(int squareSize) {
        return new Font("Segoe UI", Font.BOLD, Math.max(12, squareSize / 6));
    }
    
    public static Font getUIFont(int size) {
        return new Font("Segoe UI", Font.PLAIN, size);
    }
    
    public static Font getUIFontBold(int size) {
        return new Font("Segoe UI", Font.BOLD, size);
    }
    
    public static Font getMonospaceFont(int size) {
        return new Font("JetBrains Mono", Font.PLAIN, size);
    }
    
    public static Paint createGradient(int x, int y, int width, int height, Color start, Color end) {
        return new GradientPaint(x, y, start, x + width, y + height, end);
    }
    
    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
    
    public static void drawRoundedRect(Graphics2D g, int x, int y, int width, int height, Color color) {
        g.setColor(color);
        g.fillRoundRect(x, y, width, height, CORNER_RADIUS, CORNER_RADIUS);
    }
    
    public static void drawRoundedBorder(Graphics2D g, int x, int y, int width, int height, Color color) {
        g.setColor(color);
        g.setStroke(THIN_STROKE);
        g.drawRoundRect(x, y, width, height, CORNER_RADIUS, CORNER_RADIUS);
    }
}
