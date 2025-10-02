package com.jaceg18.Gameplay.Search.AI.Algorithm;

import com.jaceg18.Gameplay.Search.AI.Evaluation.EvaluationStrategy;
import com.jaceg18.Gameplay.Search.AI.SearchConstants;
import com.jaceg18.Gameplay.Search.AI.SearchMetrics;
import com.jaceg18.Gameplay.TB.FenUtil;
import com.jaceg18.Gameplay.TB.TablebaseClient;
import com.jaceg18.Gameplay.UI.ChessBoardPanel;
import com.jaceg18.Gameplay.Utility.Attacks;
import com.jaceg18.Gameplay.Utility.GameState;
import com.jaceg18.Gameplay.Utility.MoveGen;
import com.jaceg18.Gameplay.Zobrist;
import jdk.jfr.Experimental;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongToIntFunction;

public final class NegamaxAB implements SearchAlgorithm {

    private final TranspositionTable tt;
    private final MoveOrderer orderer;
    private final EvaluationStrategy eval;
    private final SearchMetrics metrics;
    private final IntSupplier maxDepthSupplier;

    @Experimental private static final int TAKE_DRAW_IF_WORSE = 120;
    @Experimental private static final int REP_TAKE_BONUS     = 40;
    @Experimental private static final int REP_AVOID_PENALTY  = 200;
    @Experimental private static final int TWOFOLD_PENALTY    = 120;

    @Experimental private static final int FUT_MARGIN       = 120;
    @Experimental private static final int FUT_CHILD_MARGIN = 90;
    @Experimental private static final int LMP_D1_LIMIT     = 8;
    @Experimental private static final int LMP_D2_LIMIT     = 12;

    private final GameState.Undo[] undo = new GameState.Undo[SearchConstants.MAX_PLY];
    private final TranspositionTable.ProbeOut[] probeOutByPly = new TranspositionTable.ProbeOut[SearchConstants.MAX_PLY];

    private final long[] pathKeys = new long[SearchConstants.MAX_PLY];
    private int floorPly = 0;



    private long moveDeadlineNanos = Long.MAX_VALUE;
    private boolean timeLimited = false;


    private static final class TimeUp extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
    private static final TimeUp TIME_UP = new TimeUp();

    private void checkTime() {
        if (timeLimited && System.nanoTime() >= moveDeadlineNanos) throw TIME_UP;
    }

    public NegamaxAB(
            SearchConstants ignored,
            TranspositionTable tt,
            MoveOrderer orderer,
            EvaluationStrategy eval,
            SearchMetrics metrics,
            IntSupplier maxDepthSupplier,
            IntConsumer maxDepthSetter
    ) {
        this.tt = tt;
        this.orderer = orderer;
        this.eval = eval;
        this.metrics = metrics;
        this.maxDepthSupplier = maxDepthSupplier;
        for (int i = 0; i < undo.length; i++) undo[i] = new GameState.Undo();
        for (int i = 0; i < probeOutByPly.length; i++) probeOutByPly[i] = new TranspositionTable.ProbeOut();
    }

    @Override
    public int computeBestMove(GameState root, IntConsumer progress) {
       timeLimited = false;
       moveDeadlineNanos = Long.MAX_VALUE;
       return computeBestMove(root, null, progress);

    }

    @Override
    public int computeBestMove(GameState root, int timeMs, IntConsumer progress) {

      timeLimited = timeMs > 0;
      moveDeadlineNanos = System.nanoTime() + Math.max(1, timeMs) * 1_000_000L;
      try {
          return computeBestMove(root, null, progress);
      } finally {
          timeLimited = false;
          moveDeadlineNanos = Long.MAX_VALUE;
      }
    }

    private LongToIntFunction repBase = k -> 0;

    @Override
    public void setRepetitionCounter(LongToIntFunction f) {
        this.repBase = (f != null) ? f : (k -> 0);
    }

    private int repetitionScoreForUs(int standForUs) {
        return (standForUs <= -TAKE_DRAW_IF_WORSE) ? +REP_TAKE_BONUS : -REP_AVOID_PENALTY;
    }

    public int computeBestMove(GameState root, List<Integer> history, IntConsumer progress) {
        if (progress != null) progress.accept(0);
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

        pathKeys[0] = Zobrist.compute(root);
        floorPly = 0;

        int baseAtRoot = repBase.applyAsInt(pathKeys[0]);
        if (baseAtRoot >= 3) {
            ChessBoardPanel.console.logInfo("Draw by threefold (root).");
            if (progress != null) progress.accept(100);
            return -1;
        }

        try {
            for (int depth = 1; depth <= maxDepth; depth++) {
                checkTime();

                long ds = System.nanoTime();
                long ns = metrics.nodes;

                List<Integer> moves = MoveGen.generateAllLegal(root);
                if (moves.isEmpty()) {
                    bestScore = Attacks.isInCheck(root, root.whiteToMove()) ? (-SearchConstants.MATE) : 0;
                    bestMove = -1;
                    logIter(depth, bestScore, metrics.nodes - ns, (System.nanoTime() - ds) / 1_000_000, -1);
                    break;
                }

                long rootKey = pathKeys[0];
                var pout = new TranspositionTable.ProbeOut();
                int hashMove = tt.probe(rootKey, depth, -SearchConstants.INF, +SearchConstants.INF, pout) ? pout.move : 0;
                boolean inCheckRoot = Attacks.isInCheck(root, root.whiteToMove());
                orderer.order(root, 0, hashMove, inCheckRoot, moves, depth);
                final int WINDOW = 50;
                int alpha0 = Math.max(-SearchConstants.INF, prevScore - WINDOW);
                int beta0  = Math.min(+SearchConstants.INF, prevScore + WINDOW);

                checkTime();

                RootResult rr = searchRootOnce(root, moves, depth, alpha0, beta0, maxDepth, progress);
                if (rr.failedLow || rr.failedHigh) {
                    checkTime();
                    int W2 = 200;
                    int a2 = Math.max(-SearchConstants.INF, prevScore - W2);
                    int b2 = Math.min(+SearchConstants.INF, prevScore + W2);
                    rr = searchRootOnce(root, moves, depth, a2, b2, maxDepth, progress);
                    if (rr.failedLow || rr.failedHigh) {
                        checkTime();
                        rr = searchRootOnce(root, moves, depth, -SearchConstants.INF, +SearchConstants.INF, maxDepth, progress);
                    }
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
        } catch (TimeUp ignore) {
        }

        long totalMs = (System.nanoTime() - t0) / 1_000_000;

        if (Util.isMateScore(bestScore)) {
            int mi = mateMovesFromScore(bestScore);
            String side = root.whiteToMove() ? "White" : "Black";
            ChessBoardPanel.console.logInfo("Mate in " + mi + " (" + side + "), Best=" + Util.uci(bestMove));
        } else {
            ChessBoardPanel.console.logInfo("Best=" + Util.uci(bestMove) + ", Score=" + Util.scoreStr(bestScore)
                    + ", Time(Sec)=" + (double) totalMs / 1000L);
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
        final int ROOT_AVOID_REP_MARGIN = 80;
        int alpha = alphaInit;
        int iterBestMove = moves.getFirst();
        int iterBestScore = -SearchConstants.INF;

        int done = 0, total = Math.max(1, moves.size());
        final int rootPly = 0;
        final long rootKey = pathKeys[0];
        boolean iterBestIsRep = false;

        for (int m : moves) {
            checkTime();

            GameState.Undo u = undo[rootPly];
            boolean isCapture = GameState.isCapture(m);
            boolean isPawnMv  = GameState.moverKind(m) == 0;
            boolean irreversible = isCapture || isPawnMv;

            root.makeInPlace(m, u);
            int childPly = rootPly + 1;
            pathKeys[childPly] = Zobrist.compute(root);

            int savedFloor = floorPly;
            if (irreversible) floorPly = childPly;

            boolean childIsRep3 = isThreefold(childPly);
            boolean childIsImmediateBack = false;
            if (!irreversible && depth <= 2) {
                var replies = MoveGen.generateAllLegal(root);
                GameState.Undo u2 = undo[childPly];
                for (int r : replies) {
                    root.makeInPlace(r, u2);
                    long k = Zobrist.compute(root);
                    root.unmake(u2);
                    if (k == rootKey) { childIsImmediateBack = true; break; }
                }
            }

            boolean childIsRepSoft = childIsRep3 || childIsImmediateBack;

            int sc;
            if (childIsRep3) {
                int standOpp   = eval.evalSTM(root);
                int standForUs = -standOpp;
                sc = repetitionScoreForUs(standForUs);
            } else {
                sc = -negamax(root, depth - 1, -betaInit, -alpha, childPly);
                if (childIsImmediateBack && !irreversible) sc -= TWOFOLD_PENALTY;
            }

            if (irreversible) floorPly = savedFloor;
            root.unmake(u);

            if (sc > iterBestScore ||
                    (sc == iterBestScore && (iterBestIsRep && !childIsRepSoft)) ||
                    (!childIsRepSoft && iterBestIsRep && sc + ROOT_AVOID_REP_MARGIN > iterBestScore)) {
                iterBestScore = sc;
                iterBestMove  = m;
                iterBestIsRep = childIsRepSoft;
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
            if (alpha >= betaInit) break;
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
            this.bestMove = bm;
            this.bestScore = bs;
            this.failedLow = fl;
            this.failedHigh = fh;
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

    private int negamax(GameState s, int depth, int alpha, int beta, int ply) {
        metrics.nodes++;
        if ((metrics.nodes & 0x3FF) == 0) checkTime();

        if (alpha < -SearchConstants.MATE + SearchConstants.MAX_PLY)
            alpha = -SearchConstants.MATE + SearchConstants.MAX_PLY;
        if (beta > SearchConstants.MATE - SearchConstants.MAX_PLY)
            beta = SearchConstants.MATE - SearchConstants.MAX_PLY;
        if (alpha >= beta) return alpha;

        long key = Zobrist.compute(s);
        pathKeys[ply] = key;

        if (isThreefold(ply)) {
            int stand = eval.evalSTM(s);
            int rep   = repetitionScoreForUs(stand);
            tt.store(key, depth, TranspositionTable.EXACT, Util.toTTScore(rep, ply), 0, tt.age());
            return rep;
        }

        final TranspositionTable.ProbeOut out = probeOutByPly[ply];
        if (tt.probe(key, depth, alpha, beta, out)) {
            return Util.fromTTScore(out.score, ply);
        }
        boolean inCheck = Attacks.isInCheck(s, s.whiteToMove());
        if (!inCheck && depth == 1) {
            int stand = eval.evalSTM(s);
            if (stand + FUT_MARGIN <= alpha) {
                return stand;
            }
        }

        if (depth == 0 || ply >= SearchConstants.MAX_PLY) {
            return eval.evalSTM(s);
        }

        List<Integer> moves = MoveGen.generateAllLegal(s);
        if (moves.isEmpty()) {
            int score = Attacks.isInCheck(s, s.whiteToMove())
                    ? (-SearchConstants.MATE + ply)
                    : 0;
            tt.store(key, depth, TranspositionTable.EXACT, Util.toTTScore(score, ply), 0, tt.age());
            return score;
        }

        int a0 = alpha, best = -SearchConstants.INF, bestMove = 0;
        int hashMove = out.move;

        orderer.order(s, ply, hashMove, inCheck, moves, depth);

        int moveNum = 0;
        int standOnce = Integer.MIN_VALUE;
        for (int m : moves) {
            moveNum++;

            boolean isCapture = GameState.isCapture(m);
            boolean isPromo   = GameState.promoKind(m) >= 0;
            boolean isPawnMv  = GameState.moverKind(m) == 0;
            boolean irreversible = isCapture || isPawnMv;
            boolean isTactical = isCapture || isPromo;
            if (!inCheck && !isTactical) {
                if ((depth == 1 && moveNum > LMP_D1_LIMIT) || (depth == 2 && moveNum > LMP_D2_LIMIT)) {
                    continue;
                }
                if (depth <= 2) {
                    if (standOnce == Integer.MIN_VALUE) standOnce = eval.evalSTM(s);
                    if (standOnce + FUT_CHILD_MARGIN <= alpha) {
                        continue;
                    }
                }
            }

            var u = undo[ply];
            s.makeInPlace(m, u);
            int nextPly = ply + 1;
            pathKeys[nextPly] = Zobrist.compute(s);

            int savedFloor = floorPly;
            if (irreversible) floorPly = nextPly;

            int sc;
            boolean childIsRep = false;

            if (isThreefold(nextPly)) {
                int standOpp   = eval.evalSTM(s);
                int standForUs = -standOpp;
                int rep        = repetitionScoreForUs(standForUs);
                tt.store(pathKeys[nextPly], depth - 1, TranspositionTable.EXACT, Util.toTTScore(rep, nextPly), 0, tt.age());
                sc = rep;
                childIsRep = true;
            } else if (moveNum == 1) {
                sc = -negamax(s, depth - 1, -beta, -alpha, nextPly);
            } else {
                int d = depth - 1;
                if (!inCheck && !isTactical && d >= 3 && moveNum > 3) {
                    int r = 1 + (moveNum > 10 ? 1 : 0);
                    sc = -negamax(s, d - r, -alpha - 1, -alpha, nextPly);
                    if (sc > alpha) {
                        sc = -negamax(s, d, -alpha - 1, -alpha, nextPly);
                        if (sc > alpha && sc < beta) {
                            sc = -negamax(s, d, -beta, -alpha, nextPly);
                        }
                    }
                } else {
                    sc = -negamax(s, depth - 1, -alpha - 1, -alpha, nextPly);
                    if (sc > alpha && sc < beta) {
                        sc = -negamax(s, depth - 1, -beta, -alpha, nextPly);
                    }
                }
            }

            if (!childIsRep && repeatsOnPathAny(nextPly) && !irreversible) {
                sc -= TWOFOLD_PENALTY;
            }

            if (irreversible) floorPly = savedFloor;
            s.unmake(u);

            if (sc > best) {
                best = sc;
                bestMove = m;
            }
            if (best > alpha) {
                alpha = best;
                if (alpha >= beta) {
                    orderer.onCutoff(s, ply, m, depth);
                    break;
                }
            }
        }

        int flag = (best <= a0) ? TranspositionTable.UPPER : (best >= beta ? TranspositionTable.LOWER : TranspositionTable.EXACT);
        tt.store(key, depth, flag, Util.toTTScore(best, ply), bestMove, tt.age());
        return best;
    }



    private boolean repeatsOnPathAny(int ply) {
        long key = pathKeys[ply];
        int start = Math.max(floorPly, 1);
        for (int i = start; i < ply; i++) {
            if (pathKeys[i] == key) return true;
        }
        return false;
    }

    private boolean isThreefold(int ply) {
        long key = pathKeys[ply];
        int start = Math.max(floorPly, 1);
        int occInPath = 0;
        for (int i = start; i < ply; i++) if (pathKeys[i] == key) occInPath++;
        return repBase.applyAsInt(key) + occInPath + 1 >= 3;
    }

    private void logIter(int depth, int score, long nodes, long ms, int best) {
        System.out.printf("Depth %2d: score=%s  nodes=%,d  time=%d ms  nps=%,d  best=%s%n",
                depth, Util.scoreStr(score), nodes, ms, Util.rate(nodes, ms), Util.uci(best));
    }
}