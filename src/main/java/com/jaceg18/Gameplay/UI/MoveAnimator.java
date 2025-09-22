package com.jaceg18.Gameplay.UI;


import javax.swing.*;
import java.awt.*;

class MoveAnimator {
    int draggingFrom = -1; Point dragPoint; Image dragImg; int dragOffX, dragOffY;
    private boolean animating; long start; int fromSq = -1, toSq = -1; Image animImg; final Timer timer;
    private static final int ANIM_MS = 200;
    private final JComponent repainter;

    MoveAnimator(JComponent repainter) { this.repainter = repainter; timer = new Timer(1000 / 165, e -> tick((Timer) e.getSource())); }

    void prepare(int from, int to, Image img) { fromSq = from; toSq = to; animImg = img; }

    void start(Runnable after, Runnable repaint) {
        if (animImg != null) {
            start = System.currentTimeMillis(); animating = true; if (!timer.isRunning()) timer.start();
            new javax.swing.Timer(ANIM_MS, e -> { animating = false; timer.stop(); repaint.run(); if (after != null) after.run(); }).start();
        } else { repaint.run(); if (after != null) after.run(); }
    }

    void drawAnimatingPiece(Graphics2D g, int S, boolean whiteBottom, java.util.function.IntFunction<int[]> rcFunc) {
        if (!animating || animImg == null) return;
        double t = Math.min(1.0, (System.currentTimeMillis() - start) / (double) ANIM_MS);
        t = 1 - Math.pow(1 - t, 3);
        int[] aRC = rcFunc.apply(fromSq), bRC = rcFunc.apply(toSq);
        Point a = new Point(aRC[1] * S + S / 2, aRC[0] * S + S / 2);
        Point b = new Point(bRC[1] * S + S / 2, bRC[0] * S + S / 2);
        int x = (int) Math.round(lerp(a.x - S / 2, b.x - S / 2, t));
        int y = (int) Math.round(lerp(a.y - S / 2, b.y - S / 2, t));
        g.drawImage(animImg, x, y, null);
    }

    long omitMask() {
        long m = ~0L; if (animating && fromSq != -1) m &= ~(1L << fromSq); if (isDragging()) m &= ~(1L << draggingFrom); return m;
    }

    void startDragIfPiece(java.awt.event.MouseEvent e, int fromSq, int S, PieceSprites sprites, com.jaceg18.Gameplay.Utility.GameState state, java.util.function.IntFunction<int[]> rcFunc) {
        draggingFrom = fromSq;
        dragImg = sprites.imageAt(state, fromSq, S);
        if (dragImg != null) {
            int[] rc = rcFunc.apply(fromSq); int originX = rc[1] * S, originY = rc[0] * S;
            dragOffX = e.getX() - originX; dragOffY = e.getY() - originY; dragPoint = e.getPoint();
        }
    }
    void stopDrag() { draggingFrom = -1; dragImg = null; dragPoint = null; }
    boolean isDragging() { return draggingFrom != -1; }
    boolean isAnimating() { return animating; }
    void reset() { animating = false; animImg = null; stopDrag(); if (timer.isRunning()) timer.stop(); repainter.repaint(); }

    private void tick(Timer src) { if (!animating) { src.stop(); } else repainter.repaint(); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
