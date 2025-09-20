package com.jaceg18.Gameplay.UI;

import com.jaceg18.Gameplay.Search.AI.AiProvider;
import com.jaceg18.Gameplay.Search.AI.Evaluation.Eval;
import com.jaceg18.Gameplay.TB.FenUtil;
import com.jaceg18.Gameplay.TB.TablebaseClient;
import com.jaceg18.Gameplay.Utility.GameState;
import com.jaceg18.Gameplay.Utility.MoveGen;
import com.jaceg18.Gameplay.Zobrist;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class ChessBoardPanel extends JPanel implements MouseListener, MouseMotionListener {
    public static final int BOARD_PX = 720;

    private final Map<Long,Integer> posCounts = new HashMap<>();
    private boolean gameOver = false;

    private volatile boolean aiRunning = false;
    private Thread aiThread = null;

    // Models
    private final GameState state = new GameState();
    private final Deque<GameState.Undo> undo = new ArrayDeque<>();
    private final SelectionModel selection = new SelectionModel();
    private final MoveAnimator animator = new MoveAnimator(this);
    private final PieceSprites sprites = new PieceSprites();
    private final BoardPainter painter = new BoardPainter(this);

    // Console/logging
    public static ConsolePanel console;

    // UI
    private final JProgressBar progress = new JProgressBar(0, 100);
    boolean whiteAtBottom = true;
    private boolean aiPlaysWhite = false;
    private boolean aiPlaysBlack = false;

    private AiProvider ai;
    private int actionSeq = 0;

    public ChessBoardPanel() {
        setPreferredSize(new Dimension(BOARD_PX, BOARD_PX + 28));
        setLayout(new BorderLayout());
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);

        progress.setStringPainted(true);
        progress.setVisible(false);
        add(progress, BorderLayout.SOUTH);

        console = new ConsolePanel(this);

        sprites.load("/chess.png");

        initGameCounters();


        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), this::undoMove);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK), () -> adjustDepth(+1));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), () -> adjustDepth(-1));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), () -> { whiteAtBottom = !whiteAtBottom; repaint(); });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), () -> setAiPlaysWhite(true));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), () -> setAiPlaysBlack(true));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK), this::probeTB);

    }
    // === Tablebase quick probe (non-blocking; shows result in ConsolePanel) ===
    public void probeTB() {
        // Syzygy: only meaningful up to 7 pieces
        int pieces = Long.bitCount(state.allPieces());
        if (pieces > 7) {
            if (console != null) {
                console.logWarn("TB: position has >7 pieces.");
                console.clearTbInfo();
            }
            return;
        }

        new Thread(() -> {
            try {
                String fen = FenUtil.toFEN(state);   // use your FEN util
                long zkey = Zobrist.compute(state);
                var tb = TablebaseClient.probe(zkey, fen);

                SwingUtilities.invokeLater(() -> {
                    if (tb == null) {
                        console.logWarn("TB: no info (unknown/network).");
                        console.clearTbInfo();
                        return;
                    }
                    console.setTbInfo(
                            tb.category,
                            tb.dtm,   // may be null
                            tb.dtz,   // may be null
                            tb.bestUci
                    );

                    if (tb.bestUci != null) {
                        console.logInfo("TB best: " + tb.bestUci
                                + (tb.dtm != null ? (" | DTM " + tb.dtm) : "")
                                + (tb.dtz != null ? (" | DTZ " + tb.dtz) : "")
                                + " | " + tb.category);
                    } else if (tb.checkmate) {
                        console.logInfo("TB: checkmated.");
                    } else if (tb.stalemate) {
                        console.logInfo("TB: stalemate.");
                    }
                });
            } catch (Throwable t) {
                SwingUtilities.invokeLater(() -> {
                    console.logWarn("TB probe failed: " + t.getMessage());
                    console.clearTbInfo();
                });
            }
        }, "TB-Probe").start();
    }

    private void updateEval(GameState s){
        console.setEvalCp(Eval.evaluate(s));
    }

    public void setAi(AiProvider ai) { this.ai = ai; }
    public void setAiPlaysWhite(boolean asWhite) {
        aiPlaysWhite = asWhite;
        aiPlaysBlack = false;
        if (ai != null && state.whiteToMove() == aiPlaysWhite) startAiTurn();
        console.logInfo("AI now playing " + (asWhite ? "White" : "Black"));
    }
    public void setAiPlaysBlack(boolean asBlack) {
        aiPlaysBlack = asBlack;
        aiPlaysWhite = false;
        if (ai != null && !state.whiteToMove() && asBlack) startAiTurn();
        console.logInfo("AI now playing " + (asBlack ? "Black" : "Human"));
    }
    public void setAiBoth(boolean both) {
        aiPlaysWhite = both;
        aiPlaysBlack = both;
        console.logInfo("AI vs AI: " + (both ? "ON" : "OFF"));
        if (both) startAiTurn();
    }

    // OPTIONAL convenience:
    public void setAiNone() {
        aiPlaysWhite = false;
        aiPlaysBlack = false;
        console.logInfo("AI disabled for both sides.");
    }

    // ===== Painting =====
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int S = squareSize();
        sprites.ensureScaled(S);

        painter.drawBoard(g, S, whiteAtBottom);
        painter.drawCoords(g, S, whiteAtBottom);
        painter.drawCheck(g, S, whiteAtBottom, state);
        painter.drawSelection(g, S, whiteAtBottom, selection);
        painter.drawHover(g, S, whiteAtBottom);
        painter.drawPieces(g, S, whiteAtBottom, state, sprites, animator);
        animator.drawAnimatingPiece(g, S, whiteAtBottom, this::viewRC);
        painter.drawDragged(g, S, whiteAtBottom, animator, this::viewRC);

        g.dispose();
    }


    // ===== Mouse =====
    @Override
    public void mousePressed(MouseEvent e) {
        boolean aiTurn = state.whiteToMove() ? aiPlaysWhite : aiPlaysBlack;
        if (aiTurn || animator.isAnimating()) return;
        requestFocusInWindow();

        int S = squareSize();
        int sq = boardSq(e.getY() / S, e.getX() / S);

        if (!selection.hasSelection()) {
            if (isOwnPiece(sq)) {
                selectWithLegal(sq);
                animator.startDragIfPiece(e, sq, S, sprites, state, this::viewRC);
            }
            repaint();
            return;
        }

        if (sq == selection.selected) {
            selectWithLegal(sq);
            animator.startDragIfPiece(e, sq, S, sprites, state, this::viewRC);
            repaint();
            return;
        }

        int move = chooseMove(selection.selected, sq);
        if (move != -1) { playAnimated(move, this::startAiTurn); return; }

        if (isOwnPiece(sq)) {
            selectWithLegal(sq);
            animator.startDragIfPiece(e, sq, S, sprites, state, this::viewRC);
        } else {
            selection.clear();
        }
        repaint();
    }

    @Override public void mouseDragged(MouseEvent e) {
        if (!selection.hasSelection() || !animator.isDragging()) return;
        animator.dragPoint = e.getPoint();
        repaint();
    }
    @Override
    public void mouseReleased(MouseEvent e) {
        boolean aiTurn = state.whiteToMove() ? aiPlaysWhite : aiPlaysBlack;
        if (aiTurn || !animator.isDragging()) return;

        int S = squareSize();
        int to = boardSq(e.getY() / S, e.getX() / S);

        int from = animator.draggingFrom;
        int move = chooseMove(from, to);

        animator.stopDrag();

        if (move != -1) {
            selection.clear();
            playAnimated(move, this::startAiTurn);
            return;
        }


        if (to == from && isOwnPiece(to)) {
            selectWithLegal(to);
        } else if (isOwnPiece(to)) {
            selectWithLegal(to);
        } else {
            selection.clear();
        }
        repaint();
    }

    @Override public void mouseMoved(MouseEvent e) { painter.hoverSq = boardSq(e.getY() / squareSize(), e.getX() / squareSize()); repaint(); }
    @Override public void mouseExited(MouseEvent e) { painter.hoverSq = -1; repaint(); }
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}

    private void selectWithLegal(int sq) {
        selection.select(sq);
        selection.legal = MoveGen.legalMovesFromSquare(state, sq);
    }


    // ===== Moves =====
    private int chooseMove(int from, int to) {
        if (selection.legal.isEmpty()) return -1;
        List<Integer> cand = new ArrayList<>();
        for (int m : selection.legal) if (GameState.from(m) == from && GameState.to(m) == to) cand.add(m);
        if (cand.isEmpty()) return -1;
        if (cand.size() == 1 && !GameState.isPromotion(cand.getFirst())) return cand.getFirst();
        int want = Promotion.ask(this);
        for (int m : cand) if (GameState.promoKind(m) == want) return m;
        for (int m : cand) if (GameState.promoKind(m) == 3) return m;
        return cand.getFirst();
    }

    private void playAnimated(int m, Runnable then) {
        int S = squareSize();
        int from = GameState.from(m), to = GameState.to(m);
        Image img = sprites.imageAt(state, from, S);
        animator.prepare(from, to, img);

        GameState.Undo u = state.make(m);
        undo.push(u);
        selection.clear();

        addPosCount();

        console.logMove(state, m);

        // === 1) TERMINAL FIRST: mate / stalemate ===
        if (handleTerminalNow()) {
            animator.start(null, this::repaint);   // show the last move anim, then stop
            updateEval(state);
            AudioPlayer.playSound(GameState.isCapture(m));
            return;
        }

        // === 2) Draw rules only if NOT terminal ===
        boolean willDraw = isThreefoldNow() || isFiftyMoveRuleNow() || insufficientMaterialNow();
        Runnable after = willDraw ? null : then;

        animator.start(after, this::repaint);
        updateEval(state);

        if (willDraw) {
            SwingUtilities.invokeLater(() -> {
                String reason = isThreefoldNow() ? "threefold repetition"
                        : isFiftyMoveRuleNow() ? "50-move rule"
                        : "insufficient material";
                declareDraw(reason);
            });
        }

        AudioPlayer.playSound(GameState.isCapture(m));
    }


    void undoMove() {
        if (undo.isEmpty()) return;

        // stop AI if thinking
        if (aiRunning) {
            aiRunning = false;
            if (aiThread != null) aiThread.interrupt();
            progress.setVisible(false);
            progress.setIndeterminate(false);
            progress.setValue(0);
            progress.setString(null);
        }
        gameOver = false;


        decPosCount();

        state.unmake(undo.pop());
        selection.clear();
        animator.reset();
        updateEval(state);
        repaint();
        console.logInfo("Undo.");

        boolean aiTurn = state.whiteToMove() == aiPlaysWhite;
        if (aiTurn && !undo.isEmpty()) {
            state.unmake(undo.pop());
            decPosCount();
            updateEval(state);
            repaint();
            console.logInfo("Undo (AI).");
        }
    }

    private boolean handleTerminalNow() {
        // side to move is the opponent of the mover we just applied
        java.util.List<Integer> legal = MoveGen.generateAllLegal(state);
        if (!legal.isEmpty()) return false;

        boolean inCheck = com.jaceg18.Gameplay.Utility.Attacks.isInCheck(state, state.whiteToMove());
        if (inCheck) {
            // Checkmate: winner is the side that just moved
            boolean winnerWhite = !state.whiteToMove();
            declareMate(winnerWhite);
        } else {
            // Stalemate is a draw (but mate beats any draw)
            declareDraw("stalemate");
        }
        return true;
    }

    private void declareMate(boolean winnerWhite) {
        gameOver = true;
        // stop any thinking/animations
        if (aiRunning && aiThread != null) aiThread.interrupt();
        aiRunning = false;

        progress.setVisible(false);
        progress.setIndeterminate(false);
        progress.setValue(0);
        progress.setString(null);

        // If you allow AI-vs-AI, freeze both toggles
        aiPlaysWhite = false;
        aiPlaysBlack = false;

        console.logInfo("Checkmate — " + (winnerWhite ? "White" : "Black") + " wins.");
        repaint();
    }

    // ===== AI turn =====
    private void startAiTurn() {
        if (ai == null || gameOver) return;
        if (aiRunning) return;

        boolean aiTurn = state.whiteToMove() ? aiPlaysWhite : aiPlaysBlack;

        if (!aiTurn) return;



        aiRunning = true;

        GameState snap = state.copy();

        progress.setVisible(true);
        progress.setIndeterminate(true);
        ai.setProgressCallback(p -> SwingUtilities.invokeLater(() -> {
            progress.setIndeterminate(false);
            progress.setValue(p);
            progress.setString(p + "%");
        }));

        aiThread = new Thread(() -> {
            try {
                int best = ai.pickMove(snap);

                SwingUtilities.invokeLater(() -> {
                    progress.setVisible(false);
                    progress.setIndeterminate(false);
                    progress.setValue(0);
                    progress.setString(null);


                    if (gameOver) return;
                    boolean stillAiTurn = state.whiteToMove() ? aiPlaysWhite : aiPlaysBlack;
                    if (!stillAiTurn) return;


                    if (best != -1 && MoveGen.generateAllLegal(state).contains(best)) {
                        playAnimated(best, this::startAiTurn);
                    }
                });
            } finally {
                aiRunning = false;
            }
        }, "AI");

        aiThread.setDaemon(true);
        aiThread.start();
    }


    // ===== Helpers =====
    private boolean isOwnPiece(int sq) {
        long own = state.whiteToMove() ? state.whitePieces() : state.blackPieces();
        return ((own >>> sq) & 1L) != 0L;
    }
    int squareSize() { return Math.min(getWidth(), getHeight() - progress.getPreferredSize().height) / 8; }
    int boardSq(int vr, int vc) { int br = whiteAtBottom ? (7 - vr) : vr; int bc = whiteAtBottom ? vc : (7 - vc); return (br << 3) | bc; }
    int[] viewRC(int sq) { int br = sq >>> 3, bc = sq & 7; int vr = whiteAtBottom ? (7 - br) : br; int vc = whiteAtBottom ? bc : (7 - bc); return new int[]{vr, vc}; }

    private void bind(KeyStroke ks, Runnable r) {
        String name = "a" + (actionSeq++);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        getActionMap().put(name, new AbstractAction() { public void actionPerformed(ActionEvent e) { r.run(); }});
    }
    void adjustDepth(int d) { if (ai != null) { ai.setMaxDepth(Math.max(1, ai.getMaxDepth() + d)); console.logInfo("Max depth: " + ai.getMaxDepth()); } }

    // call once at startup and whenever you reset the board
    private void initGameCounters() {
        posCounts.clear();
        addPosCount(); // count the initial position
        gameOver = false;
    }


    private void declareDraw(String reason) {
        gameOver = true;

        // stop any thinking/animations
        if (aiRunning && aiThread != null) aiThread.interrupt();
        aiRunning = false;
        progress.setVisible(false);
        progress.setIndeterminate(false);
        progress.setValue(0);
        progress.setString(null);

        if (aiPlaysBlack && aiPlaysWhite){
            aiPlaysWhite = false; aiPlaysBlack = false;
            }

        console.logInfo("Game drawn: " + reason);
        repaint();
    }


    private void addPosCount() {
        long key = Zobrist.compute(state);
        posCounts.merge(key, 1, Integer::sum);
    }

    private void decPosCount() {
        long key = Zobrist.compute(state);
        posCounts.computeIfPresent(key, (k,v) -> (v > 1) ? v - 1 : null);
    }

    private boolean isThreefoldNow() {
        long key = Zobrist.compute(state);
        return posCounts.getOrDefault(key, 0) >= 3;
    }

    private boolean isFiftyMoveRuleNow() {
        return state.halfmoveClock() >= 100; // 100 plies = 50 moves
    }

    private boolean insufficientMaterialNow() {
        // quick & practical: K vs K / K+B vs K / K+N vs K / K+N vs K+N (same color bishops optional)
        long wP=state.pawns(true), bP=state.pawns(false);
        if ((wP|bP) != 0) return false;

        long wN=state.knights(true), bN=state.knights(false);
        long wB=state.bishops(true), bB=state.bishops(false);
        long wR=state.rooks(true)|state.queens(true), bR=state.rooks(false)|state.queens(false);
        if ((wR|bR) != 0) return false;

        int minors = Long.bitCount(wN|wB) + Long.bitCount(bN|bB);
        if (minors == 0) return true;           // K vs K
        if (minors == 1) return true;           // K+B vs K or K+N vs K
        if (minors == 2 && (wN!=0 && bN!=0) && wB==0 && bB==0) return true; // K+N vs K+N
        return false;
    }

}
