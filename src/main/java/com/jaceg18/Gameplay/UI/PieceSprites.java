package com.jaceg18.Gameplay.UI;

import com.jaceg18.Gameplay.Utility.GameState;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

class PieceSprites {
    private Image[] raw, scaled;

    void load(String resourcePath) {
        try (InputStream in = ChessBoardPanel.class.getResourceAsStream(resourcePath)) {
            if (in == null) return;
            BufferedImage sheet = ImageIO.read(in);
            int cw = sheet.getWidth() / 6, ch = sheet.getHeight() / 2;
            raw = new Image[12]; int idx = 0;
            for (int r = 0; r < 2; r++) for (int c = 0; c < 6; c++) raw[idx++] = sheet.getSubimage(c * cw, r * ch, cw, ch);
        } catch (Exception ignored) { raw = null; }
    }
    void ensureScaled(int S) {
        if (raw == null) { scaled = null; return; }
        if (scaled != null && scaled[0] != null && scaled[0].getWidth(null) == S) return;
        scaled = new Image[12];
        for (int i = 0; i < 12; i++) scaled[i] = raw[i].getScaledInstance(S, S, Image.SCALE_SMOOTH);
    }
    Image sprite(int i) { return (scaled == null || scaled.length != 12) ? null : scaled[i]; }
    Image imageAt(GameState s, int sq, int S) {
        ensureScaled(S);
        boolean white = ((s.whitePieces() >>> sq) & 1L) != 0L;
        int idx = -1; long mask = 1L << sq;
        if ((s.pawns(white) & mask) != 0) idx = white ? 5 : 11;
        else if ((s.knights(white) & mask) != 0) idx = white ? 3 : 9;
        else if ((s.bishops(white) & mask) != 0) idx = white ? 2 : 8;
        else if ((s.rooks(white) & mask) != 0) idx = white ? 4 : 10;
        else if ((s.queens(white) & mask) != 0) idx = white ? 1 : 7;
        else if ((s.king(white) & mask) != 0) idx = white ? 0 : 6;
        return idx >= 0 ? sprite(idx) : null;
    }
}