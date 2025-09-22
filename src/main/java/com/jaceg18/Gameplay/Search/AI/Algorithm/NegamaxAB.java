package com.jaceg18.Gameplay.Search.AI.Algorithm;

import com.jaceg18.Gameplay.Search.AI.Evaluation.EvaluationStrategy;
import com.jaceg18.Gameplay.Search.AI.RepetitionTracker;
import com.jaceg18.Gameplay.Search.AI.SearchConstants;
import com.jaceg18.Gameplay.Search.AI.SearchMetrics;
import com.jaceg18.Gameplay.TB.FenUtil;
import com.jaceg18.Gameplay.TB.TablebaseClient;
import com.jaceg18.Gameplay.UI.ChessBoardPanel;
import com.jaceg18.Gameplay.Utility.Attacks;
import com.jaceg18.Gameplay.Utility.GameState;
import com.jaceg18.Gameplay.Utility.MoveGen;
import com.jaceg18.Gameplay.Zobrist;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;


public final class NegamaxAB implements SearchAlgorithm {

    private final TranspositionTable tt;
    private final MoveOrderer orderer;
    private final RepetitionTracker rep;
    private final EvaluationStrategy eval;
    private final SearchMetrics metrics;

    private final IntSupplier maxDepthSupplier;

    private final GameState.Undo[] undo = new GameState.Undo[SearchConstants.MAX_PLY];

    private final TranspositionTable.ProbeOut[] probeOutByPly =
            new TranspositionTable.ProbeOut[SearchConstants.MAX_PLY];

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
        for (int i = 0; i < undo.length; i++) undo[i] = new GameState.Undo();
        for (int i = 0; i < probeOutByPly.length; i++) probeOutByPly[i] = new TranspositionTable.ProbeOut();
    }


    @Override
    public int computeBestMove(GameState root, IntConsumer progress) {
        return computeBestMove(root, null, progress);
    }

    public int computeBestMove(GameState root, List<Integer> history, IntConsumer progress) {
        if (progress != null) progress.accept(0);

        seedRepetition(root, history);

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
                        return tbMove;
                    }
                }
            }
        } catch (Throwable t) {
            ChessBoardPanel.console.logWarn("TB probe failed (searching): " + t.getMessage());
        }


        int bestMove = -1, bestScore = -SearchConstants.INF;
        long t0 = System.nanoTime();

        int maxDepth = maxDepthSupplier.getAsInt();

        int prevScore = 0;

        for (int depth = 1; depth <= maxDepth; depth++) {
            long ds = System.nanoTime();
            long ns = metrics.nodes;

            List<Integer> moves = MoveGen.generateAllLegal(root);
            if (moves.isEmpty()) {
                bestScore = Attacks.isInCheck(root, root.whiteToMove()) ? (-SearchConstants.MATE) : 0;
                bestMove = -1;
                logIter(depth, bestScore, metrics.nodes - ns, (System.nanoTime() - ds) / 1_000_000, -1);
                break;
            }

            long rootKey = rep.get(currentRootPly());
            var pout = new TranspositionTable.ProbeOut();
            int hashMove = tt.probe(rootKey, depth, -SearchConstants.INF, +SearchConstants.INF, pout) ? pout.move : 0;

            moves.sort((a, b) -> {
                if (a == hashMove && b != hashMove) return -1;
                if (b == hashMove && a != hashMove) return 1;
                boolean ac = GameState.isCapture(a), bc = GameState.isCapture(b);
                return ac == bc ? 0 : (ac ? -1 : 1);
            });

            final int WINDOW = 50;
            int alpha0 = Math.max(-SearchConstants.INF, prevScore - WINDOW);
            int beta0 = Math.min(+SearchConstants.INF, prevScore + WINDOW);

            RootResult rr = searchRootOnce(root, moves, depth, alpha0, beta0, maxDepth, progress);

            if (rr.failedLow || rr.failedHigh) {
                rr = searchRootOnce(root, moves, depth, -SearchConstants.INF, +SearchConstants.INF, maxDepth, progress);
            }

            bestMove = rr.bestMove;
            bestScore = rr.bestScore;
            prevScore = bestScore;

            tt.store(rootKey, depth, TranspositionTable.EXACT, Util.toTTScore(bestScore, 0), bestMove, tt.age());

            if (progress != null) progress.accept((depth * 100) / maxDepth);

            if (Util.isMateScore(bestScore) && bestScore > 0) {
                int mi = mateMovesFromScore(bestScore);
                String side = root.whiteToMove() ? "White" : "Black";
                ChessBoardPanel.console.logInfo("Forced mate in " + mi + " (" + side + ")");
                break;
            }

            logIter(depth, bestScore, metrics.nodes - ns, (System.nanoTime() - ds) / 1_000_000, bestMove);
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

    private RootResult searchRootOnce(GameState root,
                                      List<Integer> moves,
                                      int depth,
                                      int alphaInit,
                                      int betaInit,
                                      int maxDepth,
                                      IntConsumer progress) {
        int alpha = alphaInit, beta = betaInit;
        int iterBestMove = moves.get(0);
        int iterBestScore = -SearchConstants.INF;

        int done = 0, total = Math.max(1, moves.size());

        final int rootPly = currentRootPly();
        for (int m : moves) {
            GameState.Undo u = undo[rootPly];
            boolean irreversible =
                    GameState.isCapture(m) || GameState.moverKind(m) == 0;

            root.makeInPlace(m, u);
            int childPly = rootPly + 1;
            rep.set(childPly, Zobrist.compute(root));

            int savedFloor = rep.floor();
            if (irreversible) rep.setFloor(childPly);

            int sc = -negamax(root, depth - 1, -beta, -alpha, childPly);

            if (irreversible) rep.setFloor(savedFloor);
            root.unmake(u);

            if (sc > iterBestScore) {
                iterBestScore = sc;
                iterBestMove  = m;
            }
            if (iterBestScore > alpha) alpha = iterBestScore;

            done++;
            if (progress != null) {
                int coarse = (depth - 1) * 100 / Math.max(1, maxDepth);
                int fine   = (int)((done * (100.0 / maxDepth)) / total);
                progress.accept(Math.min(99, coarse + fine));
            }

            if (Util.isMateScore(sc) && sc > 0) {
                int mi = mateMovesFromScore(sc);
                ChessBoardPanel.console.logInfo("Mate in " + mi + " found at depth " + depth);
                iterBestScore = sc;
                iterBestMove  = m;
                break;
            }

            if (alpha >= beta) {
                break;
            }
        }

        boolean failedLow  = (iterBestScore <= alphaInit && alphaInit > -SearchConstants.INF);
        boolean failedHigh = (iterBestScore >= betaInit  && betaInit  <  SearchConstants.INF);

        return new RootResult(iterBestMove, iterBestScore, failedLow, failedHigh);
    }

    private static final class RootResult {
        final int bestMove;
        final int bestScore;
        final boolean failedLow, failedHigh;
        RootResult(int bm, int bs, boolean fl, boolean fh) {
            this.bestMove = bm; this.bestScore = bs; this.failedLow = fl; this.failedHigh = fh;
        }
    }

    private static int mateMovesFromScore(int sc) {
        int matePly = (sc > 0) ? (SearchConstants.MATE - sc) : (SearchConstants.MATE + sc);
        return (matePly + 1) / 2;
    }

    private int findMoveByUci(GameState s, String uci) {
        var legal = MoveGen.generateAllLegal(s);
        for (int m : legal) {
            if (uci.equalsIgnoreCase(Util.uci(m))) return m;
        }
        return -1;
    }

    private int negamax(GameState s, int depth, int alpha, int beta, int ply){
        metrics.nodes++;

        if (alpha < -SearchConstants.MATE + SearchConstants.MAX_PLY) alpha = -SearchConstants.MATE + SearchConstants.MAX_PLY;
        if (beta  >  SearchConstants.MATE - SearchConstants.MAX_PLY) beta  =  SearchConstants.MATE - SearchConstants.MAX_PLY;
        if (alpha >= beta) return alpha;

        long key = rep.get(ply);
        if (key == 0L) { key = Zobrist.compute(s); rep.set(ply, key); }

        if (rep.isThreefold(ply)) {
            int standPat = eval.evalSTM(s);
            if (standPat > 0) return +SearchConstants.CONTEMPT;
            if (standPat < 0) return 0;
            return 0;
        }

        final TranspositionTable.ProbeOut out = probeOutByPly[ply];
        if (tt.probe(key, depth, alpha, beta, out)) {
            return Util.fromTTScore(out.score, ply);
        }

        if (depth == 0 || ply >= SearchConstants.MAX_PLY) {
            return eval.evalSTM(s);
        }

        List<Integer> moves = MoveGen.generateAllLegal(s);
        if (moves.isEmpty()){
            int score = Attacks.isInCheck(s, s.whiteToMove())
                    ? (-SearchConstants.MATE + ply)
                    : 0;
            tt.store(key, depth, TranspositionTable.EXACT, Util.toTTScore(score, ply), 0, tt.age());
            return score;
        }

        int a0 = alpha, best = -SearchConstants.INF, bestMove = 0;
        int hashMove = out.move;
        boolean inCheck = Attacks.isInCheck(s, s.whiteToMove());

        orderer.order(s, ply, hashMove, inCheck, moves, depth);

        int moveNum = 0;
        for (int m : moves){
            moveNum++;
            var u = undo[ply];

            boolean isCapture = GameState.isCapture(m);
            boolean isPromo   = GameState.promoKind(m) >= 0;
            boolean isPawnMv  = GameState.moverKind(m) == 0;
            boolean irreversible = isCapture || isPawnMv;

            s.makeInPlace(m, u);
            int nextPly = ply + 1;
            rep.set(nextPly, Zobrist.compute(s));

            int savedFloor = rep.floor();
            if (irreversible) rep.setFloor(nextPly);

            int sc;
            boolean isTactical = isCapture || isPromo;

            if (moveNum == 1){
                sc = -negamax(s, depth-1, -beta, -alpha, nextPly);
            } else {
                int d = depth - 1;
                if (!inCheck && !isTactical && d >= 3 && moveNum > 3){
                    int r = 1 + (moveNum > 10 ? 1 : 0);
                    sc = -negamax(s, d - r, -alpha - 1, -alpha, nextPly);
                    if (sc > alpha){
                        sc = -negamax(s, d, -alpha - 1, -alpha, nextPly);
                        if (sc > alpha && sc < beta){
                            sc = -negamax(s, d, -beta, -alpha, nextPly);
                        }
                    }
                } else {
                    sc = -negamax(s, depth-1, -alpha - 1, -alpha, nextPly);
                    if (sc > alpha && sc < beta){
                        sc = -negamax(s, depth-1, -beta, -alpha, nextPly);
                    }
                }
            }

            if (irreversible) rep.setFloor(savedFloor);
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



    private int seededRootPlies = 0;

    private int currentRootPly() {
        return Math.max(0, seededRootPlies - 1);
    }


    private void seedRepetition(GameState root, List<Integer> history) {
        rep.clearFrom(0);
        GameState cur = root.clone();

        int ply = 0;
        rep.set(ply, Zobrist.compute(cur));

        int lastIrrevPly = 0;
        if (history != null) {
            for (int mv : history) {
                boolean irreversible =
                        GameState.isCapture(mv) || GameState.moverKind(mv) == 0;

                cur.makeInPlace(mv,null);
                rep.set(++ply, Zobrist.compute(cur));
                if (irreversible) lastIrrevPly = ply;
            }
        }
        rep.setFloor(lastIrrevPly);
    }
}
