package com.jaceg18.Gameplay.UI;


import javax.swing.*;
import java.awt.*;

class Promotion {
    static int ask(Component parent) {
        Object[] o = {"Queen", "Rook", "Bishop", "Knight"};
        int c = JOptionPane.showOptionDialog(parent, "Promote to:", "Promotion", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, o, o[0]);
        if (c == 3) return 0; if (c == 2) return 1; if (c == 1) return 2; return 3;
    }
}