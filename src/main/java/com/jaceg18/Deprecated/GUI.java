package com.jaceg18.Deprecated;


import com.jaceg18.Gameplay.Opening.OpeningBook;
import com.jaceg18.Gameplay.Search.AI.AiFactory;
import com.jaceg18.Gameplay.UI.AudioPlayer;
import com.jaceg18.Gameplay.Utility.Attacks;
import com.jaceg18.Gameplay.Utility.GameState;
import com.jaceg18.Gameplay.Utility.MoveGen;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntConsumer;

@Deprecated
public class GUI extends JPanel implements MouseListener, MouseMotionListener {

    public static final int BOARD_PX = 640;
    private static final Color LIGHT_SQ = new Color(240, 217, 181);
    private static final Color DARK_SQ  = new Color(181, 136, 99);
    private static final Color HOVER    = new Color(0, 0, 0, 40);
    private static final Color SELECT   = new Color(50, 130, 240, 90);
    private static final Color LEGAL    = new Color(50, 200, 50, 120);
    private static final Color CHECK    = new Color(220, 20, 60, 120);
    private static final Stroke DASH    = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 6f}, 0f);

    public interface AiProvider {
        int pickMove(GameState snapshot);
        void setMaxDepth(int maxDepth);
        int getMaxDepth();
        default void setProgressCallback(IntConsumer cb) {}
    }

    private final GameState state = new GameState();
    private final Deque<GameState.Undo> undo = new ArrayDeque<>();
    private final SelectionModel sel = new SelectionModel();
    private final MoveAnimator animator = new MoveAnimator();
    private final PieceSprites sprites = new PieceSprites();
    private final BoardPainter painter = new BoardPainter();

    private final JProgressBar progress = new JProgressBar(0, 100);
    private boolean whiteAtBottom = true;
    private boolean aiPlaysWhite = false;
    private AiProvider ai;
    private int actionSeq = 0;

    public GUI() { this(null); }
    public GUI(String fen) {
        setPreferredSize(new Dimension(BOARD_PX, BOARD_PX));
        setLayout(new BorderLayout());
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);

        progress.setStringPainted(true);
        progress.setVisible(false);
        add(progress, BorderLayout.SOUTH);

        sprites.load();
        var engine = AiFactory.balanced();
        engine.setMaxDepth(6);
        try {
            var book = OpeningBook.load("src/main/resources/openings.txt");
            engine.setOpeningBook(book);
        } catch (IOException ignore) { System.err.println("Opening book not found"); }
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), this::undoMove);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK), () -> adjustDepth(+1));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), () -> adjustDepth(-1));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), () -> { whiteAtBottom = !whiteAtBottom; repaint(); });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), () -> setAiPlaysWhite(true));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), () -> setAiPlaysWhite(false));
        if (aiPlaysWhite && state.whiteToMove()) startAiTurn();
    }

    public void setAi(AiProvider ai) { this.ai = ai; }
    public void setAiPlaysWhite(boolean asWhite) {
        aiPlaysWhite = asWhite;
        if (ai != null && state.whiteToMove() == aiPlaysWhite) startAiTurn();
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        var g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        var S = squareSize();
        sprites.ensureScaled(S);

        painter.drawBoard(g, S);
        painter.drawCheckIfAny(g, S);
        painter.drawSelection(g, S);
        painter.drawHover(g, S);
        painter.drawPieces(g, S);
        animator.drawAnimatingPiece(g, S);
        painter.drawDragged(g, S);

        g.dispose();
    }

    @Override public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        var S = squareSize();
        var sq = boardSq(e.getY() / S, e.getX() / S);

        if (!sel.hasSelection()) {
            if (isOwnPiece(sq)) {
                sel.select(sq);
                sel.legal = MoveGen.legalMovesFromSquare(state, sq);
                animator.startDragIfPiece(e, sq, S);
            }
        } else {
            var move = chooseMove(sel.selected, sq);
            if (move != -1) { playAnimated(move, this::startAiTurn); return; }
            if (isOwnPiece(sq)) { sel.select(sq); animator.startDragIfPiece(e, sq, S); }
            else sel.clear();
            repaint();
        }
    }
    @Override public void mouseDragged(MouseEvent e) {
        if (!sel.hasSelection() || !animator.isDragging()) return;
        animator.dragPoint = e.getPoint();
        repaint();
    }
    @Override public void mouseReleased(MouseEvent e) {
        if (state.whiteToMove() == aiPlaysWhite || !animator.isDragging()) return;
        var S = squareSize();
        int to = boardSq(e.getY() / S, e.getX() / S);
        var move = chooseMove(animator.draggingFrom, to);
        animator.stopDrag();
        if (move != -1) { sel.clear(); playAnimated(move, this::startAiTurn); }
        else { if (isOwnPiece(to)) sel.select(to); else sel.clear(); repaint(); }
    }
    @Override public void mouseMoved(MouseEvent e) { painter.hoverSq = boardSq(e.getY() / squareSize(), e.getX() / squareSize()); repaint(); }
    @Override public void mouseExited(MouseEvent e) { painter.hoverSq = -1; repaint(); }
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}

    private int chooseMove(int from, int to) {
        if (sel.legal.isEmpty()) return -1;
        List<Integer> cand = new ArrayList<>();
        for (var m : sel.legal) if (GameState.from(m) == from && GameState.to(m) == to) cand.add(m);
        if (cand.isEmpty()) return -1;
        if (cand.size() == 1 && !GameState.isPromotion(cand.get(0))) return cand.get(0);
        int want = Promotion.ask(this);
        for (var m : cand) if (GameState.promoKind(m) == want) return m;
        for (var m : cand) if (GameState.promoKind(m) == 3) return m;
        return cand.getFirst();
    }

    private void playAnimated(int m, Runnable then) {
        var S = squareSize();
        int from = GameState.from(m), to = GameState.to(m);
        animator.preparePieceOverlay(from, to, sprites.imageAt(state, from, S));

        var u = state.make(m);
        undo.push(u);
        sel.clear();

        animator.start(then, this::repaint);
        AudioPlayer.playSound(GameState.isCapture(m));
    }

    private void undoMove() {
        if (undo.isEmpty()) return;
        state.unmake(undo.pop());
        sel.clear();
        animator.reset();
        repaint();
    }

    private void startAiTurn() {
        if (ai == null || state.whiteToMove() != aiPlaysWhite) return;
        var snap = state.copy();
        progress.setVisible(true);
        progress.setIndeterminate(true);
        ai.setProgressCallback(p -> SwingUtilities.invokeLater(() -> { progress.setIndeterminate(false); progress.setValue(p); progress.setString(p + "%"); }));
        new Thread(() -> {
            int best = ai.pickMove(snap);
            SwingUtilities.invokeLater(() -> {
                progress.setVisible(false); progress.setIndeterminate(false); progress.setValue(0); progress.setString(null);
                if (best != -1 && MoveGen.generateAllLegal(state).contains(best)) playAnimated(best, this::startAiTurn);
            });
        }, "AI").start();
    }

    private boolean isOwnPiece(int sq) {
        long own = state.whiteToMove() ? state.whitePieces() : state.blackPieces();
        return ((own >>> sq) & 1L) != 0L;
    }
    private int squareSize() { return Math.min(getWidth(), getHeight()) / 8; }
    private int boardSq(int vr, int vc) {
        int br = whiteAtBottom ? (7 - vr) : vr;
        int bc = whiteAtBottom ? vc : (7 - vc);
        return (br << 3) | bc;
    }
    private int[] viewRC(int sq) {
        int br = sq >>> 3, bc = sq & 7;
        int vr = whiteAtBottom ? (7 - br) : br;
        int vc = whiteAtBottom ? bc : (7 - bc);
        return new int[]{vr, vc};
    }

    private void bind(KeyStroke ks, Runnable r) {
        var name = "a" + (actionSeq++);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        getActionMap().put(name, new AbstractAction() { public void actionPerformed(ActionEvent e) { r.run(); }});
    }
    private void adjustDepth(int d) { if (ai != null) { ai.setMaxDepth(Math.max(1, ai.getMaxDepth() + d)); System.out.println("Max depth: " + ai.getMaxDepth()); } }

    private final class BoardPainter {
        int hoverSq = -1;
        void drawBoard(Graphics2D g, int S) {
            for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
                g.setColor(((r + c) & 1) == 0 ? LIGHT_SQ : DARK_SQ);
                g.fillRect(c * S, r * S, S, S);
            }
        }
        void drawCheckIfAny(Graphics2D g, int S) {
            boolean wtm = state.whiteToMove();
            long kbb = state.king(wtm);
            if (kbb == 0) return;
            int ksq = Long.numberOfTrailingZeros(kbb);
            if (Attacks.isSquareAttackedBy(state, ksq, !wtm)) {
                var rc = viewRC(ksq);
                g.setColor(CHECK);
                g.fillRect(rc[1] * S, rc[0] * S, S, S);
            }
        }
        void drawSelection(Graphics2D g, int S) {
            if (!sel.hasSelection()) return;
            var rc = viewRC(sel.selected);
            g.setColor(SELECT);
            g.fillRect(rc[1] * S, rc[0] * S, S, S);
            g.setColor(LEGAL);
            for (int m : sel.legal) {
                var to = viewRC(GameState.to(m));
                int cx = to[1] * S + S / 2, cy = to[0] * S + S / 2, r = S / 6;
                g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
            }
        }
        void drawHover(Graphics2D g, int S) {
            if (hoverSq == -1 || animator.isDragging()) return;
            var rc = viewRC(hoverSq);
            g.setColor(HOVER);
            g.fillRect(rc[1] * S, rc[0] * S, S, S);
        }
        void drawPieces(Graphics2D g, int S) {
            long animMask = animator.maskOmit();
            long[] w = {state.pawns(true), state.knights(true), state.bishops(true), state.rooks(true), state.queens(true), state.king(true)};
            long[] b = {state.pawns(false), state.knights(false), state.bishops(false), state.rooks(false), state.queens(false), state.king(false)};
            int[] wi = {5, 3, 2, 4, 1, 0}, bi = {11, 9, 8, 10, 7, 6};
            for (int i = 0; i < 6; i++) drawBitboard(g, w[i] & animMask, S, sprites.sprite(wi[i]));
            for (int i = 0; i < 6; i++) drawBitboard(g, b[i] & animMask, S, sprites.sprite(bi[i]));
        }
        void drawDragged(Graphics2D g, int S) {
            if (!animator.isDragging() || animator.dragImg == null) return;
            var old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));
            g.drawImage(animator.dragImg, animator.dragPoint.x - animator.dragOffX, animator.dragPoint.y - animator.dragOffY, null);
            g.setComposite(old);
            var rc = viewRC(animator.draggingFrom);
            g.setStroke(DASH);
            g.setColor(new Color(0, 0, 0, 60));
            g.drawRect(rc[1] * S, rc[0] * S, S, S);
        }
        private void drawBitboard(Graphics2D g, long bb, int S, Image img) {
            while (bb != 0) {
                long ls1 = bb & -bb;
                int sq = Long.numberOfTrailingZeros(ls1);
                var rc = viewRC(sq);
                if (img != null) g.drawImage(img, rc[1] * S, rc[0] * S, null);
                bb ^= ls1;
            }
        }
    }

    private static final class SelectionModel {
        int selected = -1;
        List<Integer> legal = List.of();
        boolean hasSelection() { return selected != -1; }
        void select(int sq) { selected = sq; }
        void clear() { selected = -1; legal = List.of(); }
    }

    private final class MoveAnimator {
        int draggingFrom = -1; Point dragPoint; Image dragImg; int dragOffX, dragOffY;
        boolean animating; long start; int fromSq = -1, toSq = -1; Image animImg; final Timer timer;
        private static final int ANIM_MS = 180;
        MoveAnimator() { timer = new Timer(1000 / 165, e -> tick((Timer) e.getSource())); }
        void start(Runnable after, Runnable repaint) {
            if (animImg != null) {
                start = System.currentTimeMillis(); animating = true;
                timer.addActionListener(new AbstractAction() { @Override public void actionPerformed(ActionEvent e) {}});
                timer.start();
                new Timer(ANIM_MS, e -> { animating = false; timer.stop(); repaint.run(); if (after != null) after.run(); }).start();
            } else { repaint.run(); if (after != null) after.run(); }
        }
        void preparePieceOverlay(int from, int to, Image img) { fromSq = from; toSq = to; animImg = img; }
        void tick(Timer src) { if (!animating) { src.stop(); } else repaint(); }
        void drawAnimatingPiece(Graphics2D g, int S) {
            if (!animating || animImg == null) return;
            double t = Math.min(1.0, (System.currentTimeMillis() - start) / (double) ANIM_MS);
            t = 1 - Math.pow(1 - t, 3);
            Point a = sqCenter(fromSq, S), b = sqCenter(toSq, S);
            int x = (int) Math.round(lerp(a.x - S / 2, b.x - S / 2, t));
            int y = (int) Math.round(lerp(a.y - S / 2, b.y - S / 2, t));
            g.drawImage(animImg, x, y, null);
        }
        long maskOmit() {
            long m = ~0L;
            if (animating && fromSq != -1) m &= ~(1L << fromSq);
            if (isDragging()) m &= ~(1L << draggingFrom);
            return m;
        }
        void startDragIfPiece(MouseEvent e, int fromSq, int S) {
            draggingFrom = fromSq;
            dragImg = sprites.imageAt(state, fromSq, S);
            if (dragImg != null) {
                var rc = viewRC(fromSq); int originX = rc[1] * S, originY = rc[0] * S;
                dragOffX = e.getX() - originX; dragOffY = e.getY() - originY; dragPoint = e.getPoint();
            }
        }
        void stopDrag() { draggingFrom = -1; dragImg = null; dragPoint = null; }
        boolean isDragging() { return draggingFrom != -1; }
        void reset() { animating = false; animImg = null; stopDrag(); if (timer.isRunning()) timer.stop(); }
    }

    private static final class PieceSprites {
        Image[] raw, scaled;
        void load() {
            try (InputStream in = GUI.class.getResourceAsStream("/chess.png")) {
                if (in == null) return;
                var sheet = ImageIO.read(in);
                int cw = sheet.getWidth() / 6, ch = sheet.getHeight() / 2;
                raw = new Image[12]; int idx = 0;
                for (int r = 0; r < 2; r++) for (int c = 0; c < 6; c++) raw[idx++] = sheet.getSubimage(c * cw, r * ch, cw, ch);
            } catch (Exception ignore) { raw = null; }
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

    private static final class Promotion {
        static int ask(Component parent) {
            Object[] o = {"Queen", "Rook", "Bishop", "Knight"};
            int c = JOptionPane.showOptionDialog(parent, "Promote to:", "Promotion", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, o, o[0]);
            if (c == 3) return 0;
            if (c == 2) return 1;
            if (c == 1) return 2;
            return 3;
        }
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private Point sqCenter(int sq, int S) { var rc = viewRC(sq); return new Point(rc[1] * S + S / 2, rc[0] * S + S / 2); }
}
