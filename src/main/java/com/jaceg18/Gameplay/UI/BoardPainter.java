package com.jaceg18.Gameplay.UI;

import com.jaceg18.Gameplay.Utility.Attacks;
import com.jaceg18.Gameplay.Utility.GameState;

import java.awt.*;

class BoardPainter {
    int hoverSq = -1;
    private final ChessBoardPanel host;
    BoardPainter(ChessBoardPanel host) { this.host = host; }

    void drawBoard(Graphics2D g, int S, boolean whiteBottom) {
        // wood gradient
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            boolean light = ((r + c) & 1) == 0;
            Color base = light ? Theme.LIGHT_SQ : Theme.DARK_SQ;
            Color edge = light ? Theme.LIGHT_EDGE : Theme.DARK_EDGE;
            Paint p = new GradientPaint(c * S, r * S, base, (c + 1) * S, (r + 1) * S, edge);
            g.setPaint(p); g.fillRect(c * S, r * S, S, S);
        }
        // subtle border
        g.setColor(Theme.BOARD_BORDER);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(0, 0, S * 8 - 1, S * 8 - 1);
    }

    void drawCoords(Graphics2D g, int S, boolean whiteBottom) {
        g.setFont(Theme.coordFont(S));
        g.setColor(Theme.COORD); FontMetrics fm = g.getFontMetrics();
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            int br = whiteBottom ? (7 - r) : r; int bc = whiteBottom ? c : (7 - c);
            String file = "abcdefgh".substring(bc, bc + 1);
            String rank = String.valueOf(br + 1);
            if (r == 7) g.drawString(file, c * S + 4, (r + 1) * S - 4);
            if (c == 0) g.drawString(rank, c * S + 4, r * S + fm.getAscent() + 2);
        }
    }

    void drawCheck(Graphics2D g, int S, boolean whiteBottom, GameState state) {
        boolean wtm = state.whiteToMove(); long kbb = state.king(wtm); if (kbb == 0) return;
        int ksq = Long.numberOfTrailingZeros(kbb);
        if (Attacks.isSquareAttackedBy(state, ksq, !wtm)) {
            int[] rc = host.viewRC(ksq);
            g.setColor(Theme.CHECK);
            g.fillRoundRect(rc[1] * S + 2, rc[0] * S + 2, S - 4, S - 4, Theme.R, Theme.R);
        }
    }

    void drawSelection(Graphics2D g, int S, boolean whiteBottom, SelectionModel sel) {
        if (!sel.hasSelection()) return;
        int[] rc = host.viewRC(sel.selected);
        g.setColor(Theme.SELECT);
        g.fillRoundRect(rc[1] * S + 2, rc[0] * S + 2, S - 4, S - 4, Theme.R, Theme.R);
        g.setColor(Theme.LEGAL);
        for (int m : sel.legal) {
            int[] to = host.viewRC(GameState.to(m));
            int cx = to[1] * S + S / 2, cy = to[0] * S + S / 2; int r = S / 7;
            g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        }
    }

    void drawHover(Graphics2D g, int S, boolean whiteBottom) {
        if (hoverSq == -1) return;
        int[] rc = host.viewRC(hoverSq);
        g.setColor(Theme.HOVER);
        g.fillRoundRect(rc[1] * S + 2, rc[0] * S + 2, S - 4, S - 4, Theme.R, Theme.R);
    }

    void drawPieces(Graphics2D g, int S, boolean whiteBottom, GameState state, PieceSprites sprites, MoveAnimator animator) {
        long mask = animator.omitMask();
        long[] w = {state.pawns(true), state.knights(true), state.bishops(true), state.rooks(true), state.queens(true), state.king(true)};
        long[] b = {state.pawns(false), state.knights(false), state.bishops(false), state.rooks(false), state.queens(false), state.king(false)};
        int[] wi = {5, 3, 2, 4, 1, 0}, bi = {11, 9, 8, 10, 7, 6};
        for (int i = 0; i < 6; i++) drawBitboard(g, w[i] & mask, S, sprites.sprite(wi[i]));
        for (int i = 0; i < 6; i++) drawBitboard(g, b[i] & mask, S, sprites.sprite(bi[i]));
    }

    void drawDragged(Graphics2D g, int S, boolean whiteBottom, MoveAnimator animator, java.util.function.IntFunction<int[]> rcFunc) {
        if (!animator.isDragging() || animator.dragImg == null) return;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));
        g.drawImage(animator.dragImg, animator.dragPoint.x - animator.dragOffX, animator.dragPoint.y - animator.dragOffY, null);
        g.setComposite(old);
        int[] rc = rcFunc.apply(animator.draggingFrom);
        g.setStroke(Theme.DASH);
        g.setColor(new Color(0, 0, 0, 60));
        g.drawRoundRect(rc[1] * S + 2, rc[0] * S + 2, S - 4, S - 4, Theme.R, Theme.R);
    }

    private void drawBitboard(Graphics2D g, long bb, int S, Image img) {
        while (bb != 0) {
            long ls1 = bb & -bb; int sq = Long.numberOfTrailingZeros(ls1);
            int[] rc = host.viewRC(sq); if (img != null) g.drawImage(img, rc[1] * S, rc[0] * S, null);
            bb ^= ls1;
        }
    }
}
