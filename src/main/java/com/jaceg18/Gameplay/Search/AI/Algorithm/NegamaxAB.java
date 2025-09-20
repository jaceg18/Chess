package com.jaceg18.Gameplay.Search.AI.Algorithm;

import com.jaceg18.Gameplay.Search.AI.RepetitionTracker;
import com.jaceg18.Gameplay.Search.AI.SearchConstants;
import com.jaceg18.Gameplay.Search.AI.SearchMetrics;
import com.jaceg18.Gameplay.Search.AI.Evaluation.EvaluationStrategy;
import com.jaceg18.Gameplay.TB.FenUtil;
import com.jaceg18.Gameplay.TB.TablebaseClient;
import com.jaceg18.Gameplay.UI.ChessBoardPanel;
import com.jaceg18.Gameplay.Utility.Attacks;
import com.jaceg18.Gameplay.Utility.GameState;
import com.jaceg18.Gameplay.Utility.MoveGen;
import com.jaceg18.Gameplay.Zobrist;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class NegamaxAB implements SearchAlgorithm {

    private final TranspositionTable tt;
    private final MoveOrderer orderer;
    private final RepetitionTracker rep;
    private final EvaluationStrategy eval;
    private final SearchMetrics metrics;
    private final SearchConstants C = new SearchConstants();

    private final IntSupplier maxDepthSupplier;

    private final GameState.Undo[] undo = new GameState.Undo[SearchConstants.MAX_PLY];

    public NegamaxAB(
            SearchConstants ignored,
            TranspositionTable tt,
            MoveOrderer orderer,
            RepetitionTracker rep,
            EvaluationStrategy eval,
            SearchMetrics metrics,
            IntSupplier maxDepthSupplier,
            java.util.function.IntConsumer maxDepthSetter
    ) {
        this.tt = tt;
        this.orderer = orderer;
        this.rep = rep;
        this.eval = eval;
        this.metrics = metrics;
        this.maxDepthSupplier = maxDepthSupplier;
        for (int i=0;i<undo.length;i++) undo[i] = new GameState.Undo();
    }


    // --- helpers in NegamaxAB ---
    private static boolean isWinningMate(int score) { return Util.isMateScore(score) && score > 0; }
    private int matePlies(int score) { return SearchConstants.MATE - Math.abs(score); }

    private String mateForSide(int score, boolean rootWhiteToMove) {
        boolean forSideToMove = score > 0;              // + = winning for side to move at root
        boolean whiteWins = forSideToMove ? rootWhiteToMove : !rootWhiteToMove;
        return whiteWins ? "White" : "Black";
    }

    @Override
    public int computeBestMove(GameState root, IntConsumer progress) {
        if (progress != null) progress.accept(0);

        // init repetition key for root
        rep.clearFrom(0);
        rep.set(0, Zobrist.compute(root));

        metrics.reset();
        tt.bumpAge();

        try {
            int pieces = Long.bitCount(root.allPieces());
            if (pieces <= 7) {
                String fen = FenUtil.toFEN(root);
                long z = Zobrist.compute(root);
                var tb = TablebaseClient.probe(z, fen);
                if (tb != null && tb.hasMove()) {
                    int tbMove = findMoveByUci(root, tb.bestUci);
                    if (tbMove != -1) {
                        if (tb.isMateAvailable()) {
                            int mi = Math.abs(tb.dtm);
                            String side = root.whiteToMove() ? "White" : "Black";
                            ChessBoardPanel.console.logInfo("TB: Mate in " + mi + " (" + side + "), Best=" + tb.bestUci);
                        } else {
                            String dtzTxt = (tb.dtz != null) ? (", DTZ " + tb.dtz) : "";
                            ChessBoardPanel.console.logInfo("TB: " + tb.category + dtzTxt + ", Best=" + tb.bestUci);
                        }
                        if (progress != null) progress.accept(100);
                        return tbMove; // perfect move; skip search
                    }
                }
            }
        } catch (Throwable t) {
            // Fail quietly and just search
            ChessBoardPanel.console.logWarn("TB probe failed (searching): " + t.getMessage());
        }
        // ================== end optional TB block ========================

        int bestMove = -1, bestScore = -SearchConstants.INF;
        long t0 = System.nanoTime();

        int maxDepth = maxDepthSupplier.getAsInt();
        outer:
        for (int depth = 1; depth <= maxDepth; depth++){
            long ds = System.nanoTime();
            long ns = metrics.nodes;

            // Root moves
            java.util.List<Integer> moves = new java.util.ArrayList<>(MoveGen.generateAllLegal(root));
            if (moves.isEmpty()){
                bestScore = Attacks.isInCheck(root, root.whiteToMove()) ? (-SearchConstants.MATE) : 0;
                bestMove = -1;
                logIter(depth, bestScore, metrics.nodes - ns, (System.nanoTime()-ds)/1_000_000, -1);
                break;
            }

            // hash move for root
            long rootKey = rep.get(0);
            var pout = new TranspositionTable.ProbeOut();
            int hashMove = tt.probe(rootKey, depth, -SearchConstants.INF, +SearchConstants.INF, pout) ? pout.move : 0;

            // order root: hash first, captures first
            moves.sort((a, b) -> {
                if (a == hashMove && b != hashMove) return -1;
                if (b == hashMove && a != hashMove) return  1;
                boolean ac = GameState.isCapture(a), bc = GameState.isCapture(b);
                return ac == bc ? 0 : (ac ? -1 : 1);
            });

            int alpha = -SearchConstants.INF;
            int iterBestMove = moves.getFirst();
            int iterBestScore = -SearchConstants.INF;

            int done = 0, total = Math.max(1, moves.size());

            for (int m : moves){
                GameState.Undo u = undo[0];
                root.makeInPlace(m, u);
                rep.set(1, Zobrist.compute(root));

                int sc = -negamax(root, depth-1, -SearchConstants.INF, +SearchConstants.INF, 1);

                root.unmake(u);

                if (sc > iterBestScore){
                    iterBestScore = sc;
                    iterBestMove  = m;
                }
                if (iterBestScore > alpha) alpha = iterBestScore;

                // progress
                done++;
                if (progress != null) {
                    int coarse = (depth - 1) * 100 / Math.max(1, maxDepth);
                    int fine   = (int)((done * (100.0 / maxDepth)) / total);
                    progress.accept(Math.min(99, coarse + fine));
                }

                // Early break only if it's a WINNING mate for side-to-move
                if (Util.isMateScore(sc) && sc > 0) {
                    int mi = mateMovesFromScore(sc);
                    ChessBoardPanel.console.logInfo("Mate in " + mi + " found at depth " + depth);
                    iterBestScore = sc;
                    iterBestMove  = m;
                    break;
                }
            }

            bestMove  = iterBestMove;
            bestScore = iterBestScore;

            tt.store(rootKey, depth, TranspositionTable.EXACT, Util.toTTScore(bestScore, 0), bestMove, tt.age());

            if (progress != null) progress.accept((depth * 100) / maxDepth);

            if (Util.isMateScore(bestScore) && bestScore > 0) {
                int mi = mateMovesFromScore(bestScore);
                String side = root.whiteToMove() ? "White" : "Black";
                ChessBoardPanel.console.logInfo("Forced mate in " + mi + " (" + side + ")");
                break outer;
            }
        }

        long totalMs = (System.nanoTime()-t0)/1_000_000;

        if (Util.isMateScore(bestScore)) {
            int mi = mateMovesFromScore(bestScore);
            String side = root.whiteToMove() ? "White" : "Black";
            ChessBoardPanel.console.logInfo("Mate in " + mi + " (" + side + "), Best=" + Util.uci(bestMove));
        } else {
            ChessBoardPanel.console.logInfo("Best=" + Util.uci(bestMove) + ", Score=" + Util.scoreStr(bestScore)
                    + ", Time(Sec)=" + (double) totalMs/1000L);
        }

        if (progress != null) progress.accept(100);
        return bestMove;
    }

    // Compute "mate in N moves" from your score convention
    private static int mateMovesFromScore(int sc) {
        int matePly = (sc > 0) ? (SearchConstants.MATE - sc) : (SearchConstants.MATE + sc);
        return (matePly + 1) / 2;
    }


    // NEW helper: find a legal move that matches a UCI string (e.g., "e2e4", "e7e8q")
    private int findMoveByUci(GameState s, String uci) {
        var legal = MoveGen.generateAllLegal(s);
        for (int m : legal) {
            if (uci.equalsIgnoreCase(Util.uci(m))) return m;
        }
        return -1;
    }
    private int negamax(GameState s, int depth, int alpha, int beta, int ply){
        metrics.nodes++;

        // Mate-band stabilization
        if (alpha < -SearchConstants.MATE + SearchConstants.MAX_PLY) alpha = -SearchConstants.MATE + SearchConstants.MAX_PLY;
        if (beta  >  SearchConstants.MATE - SearchConstants.MAX_PLY) beta  =  SearchConstants.MATE - SearchConstants.MAX_PLY;
        if (alpha >= beta) return alpha;

        // ensure key
        long key = rep.get(ply);
        if (key == 0L) { key = com.jaceg18.Gameplay.Zobrist.compute(s); rep.set(ply, key); }

        // repetition draw
        if (rep.isThreefold(ply)) {
            System.out.println("Returned 0 score for 3 rep draw");
            return 0;
        }

        // TT
        var out = new TranspositionTable.ProbeOut();
        if (tt.probe(key, depth, alpha, beta, out)) {
            return Util.fromTTScore(out.score, ply);
        }

        // leaf
        if (depth == 0 || ply >= SearchConstants.MAX_PLY) {
            // *** TB LEAF DISABLED to avoid tons of network calls ***
            // return eval
            return eval.evalSTM(s);
        }

        // moves
        java.util.List<Integer> moves = new java.util.ArrayList<>(com.jaceg18.Gameplay.Utility.MoveGen.generateAllLegal(s));
        if (moves.isEmpty()){
            int score = com.jaceg18.Gameplay.Utility.Attacks.isInCheck(s, s.whiteToMove())
                    ? (-SearchConstants.MATE + ply)
                    : 0;
            tt.store(key, depth, TranspositionTable.EXACT, Util.toTTScore(score, ply), 0, tt.age());
            return score;
        }

        int a0 = alpha, best = -SearchConstants.INF, bestMove = 0;
        int hashMove = out.move;
        boolean inCheck = com.jaceg18.Gameplay.Utility.Attacks.isInCheck(s, s.whiteToMove());

        orderer.order(s, ply, hashMove, inCheck, moves, depth);

        int moveNum = 0;
        for (int m : moves){
            moveNum++;
            var u = undo[ply];
            s.makeInPlace(m, u);
            rep.set(ply+1, com.jaceg18.Gameplay.Zobrist.compute(s));

            int sc;
            boolean isTactical = com.jaceg18.Gameplay.Utility.GameState.isCapture(m)
                    || com.jaceg18.Gameplay.Utility.GameState.promoKind(m) >= 0;

            // PVS + LMR
            if (moveNum == 1){
                sc = -negamax(s, depth-1, -beta, -alpha, ply+1);
            } else {
                int d = depth - 1;
                if (!inCheck && !isTactical && d >= 3 && moveNum > 3){
                    int r = 1 + (moveNum > 10 ? 1 : 0);
                    sc = -negamax(s, d - r, -alpha - 1, -alpha, ply+1);
                    if (sc > alpha){
                        sc = -negamax(s, d, -alpha - 1, -alpha, ply+1);
                        if (sc > alpha && sc < beta){
                            sc = -negamax(s, d, -beta, -alpha, ply+1);
                        }
                    }
                } else {
                    sc = -negamax(s, depth-1, -alpha - 1, -alpha, ply+1);
                    if (sc > alpha && sc < beta){
                        sc = -negamax(s, depth-1, -beta, -alpha, ply+1);
                    }
                }
            }

            s.unmake(u);

            if (sc > best){ best = sc; bestMove = m; }
            if (best > alpha){
                alpha = best;
                if (alpha >= beta){
                    orderer.onCutoff(s, ply, m, depth);
                    break;
                }
            }
        }

        int flag = (best <= a0) ? TranspositionTable.UPPER : (best >= beta ? TranspositionTable.LOWER : TranspositionTable.EXACT);
        tt.store(key, depth, flag, Util.toTTScore(best, ply), bestMove, tt.age());
        return best;
    }



    private void logIter(int depth, int score, long nodes, long ms, int best) {
        System.out.printf("Depth %2d: score=%s  nodes=%,d  time=%d ms  nps=%,d  best=%s%n",
                depth, Util.scoreStr(score), nodes, ms, Util.rate(nodes, ms), Util.uci(best));
    }
}
