package com.jaceg18.Gameplay.UI;


import java.awt.*;

class Theme {
    static final Color LIGHT_SQ = new Color(240, 217, 181);
    static final Color DARK_SQ  = new Color(181, 136, 99);
    static final Color LIGHT_EDGE = new Color(255, 240, 210);
    static final Color DARK_EDGE  = new Color(150, 110, 80);

    static final Color HOVER  = new Color(0, 0, 0, 30);
    static final Color SELECT = new Color(50, 130, 240, 90);
    static final Color LEGAL  = new Color(50, 200, 50, 120);
    static final Color CHECK  = new Color(220, 20, 60, 110);

    static final Color BOARD_BORDER = new Color(40, 30, 20, 180);
    static final Color COORD = new Color(30, 30, 30, 160);

    static final int R = 10; // corner radius
    static final Stroke DASH = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 6f}, 0f);

    static Font coordFont(int S) { return new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(11, S / 7)); }
}

