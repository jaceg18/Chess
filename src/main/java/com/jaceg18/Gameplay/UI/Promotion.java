package com.jaceg18.Gameplay.UI;


import javax.swing.*;
import java.awt.*;

class Promotion {
    /** return 0..3 mapped to GameState.promoKind(): 0=Knight,1=Bishop,2=Rook,3=Queen */
    static int ask(Component parent) {
        Object[] o = {"Queen", "Rook", "Bishop", "Knight"};
        int c = JOptionPane.showOptionDialog(parent, "Promote to:", "Promotion", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, o, o[0]);
        if (c == 3) return 0; if (c == 2) return 1; if (c == 1) return 2; return 3;
    }
}