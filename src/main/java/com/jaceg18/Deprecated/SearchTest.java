package com.jaceg18.Deprecated;

import com.jaceg18.Gameplay.Opening.OpeningBook;
import com.jaceg18.Gameplay.Search.AI.AiProvider;
import com.jaceg18.Gameplay.Search.AI.Evaluation.Eval;
import com.jaceg18.Gameplay.Utility.Attacks;
import com.jaceg18.Gameplay.Utility.GameState;
import com.jaceg18.Gameplay.Utility.MoveGen;
import com.jaceg18.Gameplay.Zobrist;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

@Deprecated
public class SearchTest implements AiProvider {

    private static final int INF = 1_000_000_000;
    private static final int MAX_PLY = 128;

    private static final int MATE = 30_000;
    private static final int WIN  = 10_000;

    private final int[][] killers = new int[MAX_PLY][2];
    private final int[][] history = new int[2][64 * 64];

    private static boolean quiet(int m){
        return !GameState.isCapture(m) && GameState.promoKind(m) < 0;
    }
    private static int histIdx(int m){
        return (GameState.from(m) << 6) | GameState.to(m);
    }
    private void storeKiller(int ply, int m){
        if (killers[ply][0] != m){
            killers[ply][1] = killers[ply][0];
            killers[ply][0] = m;
        }
    }

    private IntConsumer progress = null;
    @Override public void setProgressCallback(java.util.function.IntConsumer cb){ this.progress = cb; }
    private void report(int pct){ if (progress != null) progress.accept(Math.max(0, Math.min(100, pct))); }

    private OpeningBook book;
    public void setOpeningBook(OpeningBook b){ book = b; }

    private int maxDepth;
    public SearchTest(int d){ maxDepth = d; }
    @Override public void setMaxDepth(int d){ maxDepth = d; }
    @Override public int getMaxDepth(){ return maxDepth; }

    private long nodes;
    private final GameState.Undo[] undo = new GameState.Undo[MAX_PLY];
    { for (int i=0;i<undo.length;i++) undo[i] = new GameState.Undo(); }

    private final TT tt = new TT(1 << 20);
    private int ttAge = 0;

    private final long[] repStack = new long[MAX_PLY];

    @Override public int pickMove(GameState s){ return computeBestMove(s); }

    public int computeBestMove(GameState root){
        report(0);
        if (book != null){
            int bm = book.pick(root);
            if (bm != 0){ System.out.println("[book] " + uci(bm)); report(100); return bm; }
        }
        int bestMove = -1, bestScore = -INF;
        nodes = 0;
        ttAge++;
        long t0 = System.nanoTime();
        System.out.println("=== Search start (negamax+ab+TT) ===");
        repStack[0] = Zobrist.compute(root);

        for (int depth = 1; depth <= maxDepth; depth++){
            long ds = System.nanoTime();
            long ns = nodes;
            List<Integer> moves = new ArrayList<>(MoveGen.generateAllLegal(root));
            if (moves.isEmpty()){
                bestScore = Attacks.isInCheck(root, root.whiteToMove()) ? (-MATE + 0) : 0;
                bestMove = -1;
                long ms = (System.nanoTime()-ds)/1_000_000;
                long nd = nodes - ns;
                System.out.printf("Depth %2d: score=%s  nodes=%,d  time=%d ms  nps=%,d%n",
                        depth, scoreStr(bestScore), nd, ms, rate(nd, ms));
                break;
            }
            long rootKey = repStack[0];
            TT.TTOut out = new TT.TTOut();
            int hashMove;
            if (tt.probe(rootKey, depth, -INF, +INF, out)) hashMove = out.move;
            else {
                hashMove = 0;
            }

            moves.sort((a, b) -> {
                if (a == hashMove && b != hashMove) return -1;
                if (b == hashMove && a != hashMove) return  1;
                boolean ac = GameState.isCapture(a), bc = GameState.isCapture(b);
                return ac == bc ? 0 : (ac ? -1 : 1);
            });

            int alpha = -INF;
            int iterBestMove = moves.get(0);
            int iterBestScore = -INF;

            int done = 0, total = Math.max(1, moves.size());

            for (int m : moves){
                GameState.Undo u = undo[0];
                root.makeInPlace(m, u);
                repStack[1] = Zobrist.compute(root);

                int sc = -negamax(root, depth-1, -INF, +INF, 1);

                root.unmake(u);

                if (sc > iterBestScore){
                    iterBestScore = sc;
                    iterBestMove = m;
                }
                if (iterBestScore > alpha) alpha = iterBestScore;

                done++;
                int coarse = (depth - 1) * 100 / Math.max(1, maxDepth);
                int fine   = (int)((done * (100.0 / maxDepth)) / total);
                report(Math.min(99, coarse + fine));
            }

            bestMove = iterBestMove;
            bestScore = iterBestScore;
            tt.store(rootKey, depth, TT.EXACT, toTTScore(bestScore, 0), bestMove, ttAge);

            long ms = (System.nanoTime()-ds)/1_000_000, nd = nodes - ns;
            System.out.printf("Depth %2d: score=%s  nodes=%,d  time=%d ms  nps=%,d  best=%s%n",
                    depth, scoreStr(bestScore), nd, ms, rate(nd, ms), uci(bestMove));

            report((depth * 100) / Math.max(1, maxDepth));
            if (isMateScore(bestScore)) break;
        }

        long totalMs = (System.nanoTime()-t0)/1_000_000;
        System.out.printf("=== Done: best=%s  score=%s  totalNodes=%,d  totalTime=%d ms  nps=%,d ===%n",
                uci(bestMove), scoreStr(bestScore), nodes, totalMs, rate(nodes, totalMs));
        report(100);
        return bestMove;
    }


    private int negamax(GameState s, int depth, int alpha, int beta, int ply){
        nodes++;
        if (alpha < -MATE + MAX_PLY) alpha = -MATE + MAX_PLY;
        if (beta  >  MATE - MAX_PLY) beta  =  MATE - MAX_PLY;
        if (alpha >= beta) return alpha;
        long key = repStack[ply] != 0L ? repStack[ply] : (repStack[ply] = Zobrist.compute(s));
        int freq = 0;
        for (int i = 0; i <= ply; i++){
            if (repStack[i] == key) freq++;
        }
        if (freq >= 3){
            int drawScore = 0;
            return drawScore;
        }
        TT.TTOut out = new TT.TTOut();
        if (tt.probe(key, depth, alpha, beta, out)) {
            return fromTTScore(out.score, ply);
        }

        if (depth == 0 || ply >= MAX_PLY) {
            return evalSTM(s);
        }
        List<Integer> moves = new ArrayList<>(MoveGen.generateAllLegal(s));
        if (moves.isEmpty()){
            int score = Attacks.isInCheck(s, s.whiteToMove())
                    ? (-MATE + ply)
                    : 0;
            tt.store(key, depth, TT.EXACT, toTTScore(score, ply), 0, ttAge);
            return score;
        }

        final int a0 = alpha;
        int best = -INF, bestMove = 0;
        final int hashMove = out.move;
        final boolean inCheck = Attacks.isInCheck(s, s.whiteToMove());

        moves.sort((a, b) -> {
            if (a == hashMove && b != hashMove) return -1;
            if (b == hashMove && a != hashMove) return  1;
            boolean ac = GameState.isCapture(a), bc = GameState.isCapture(b);
            if (ac != bc) return ac ? -1 : 1;
            if (!ac){
                int pa = 0, pb = 0;
                if (a == killers[ply][0]) pa += 3_000; else if (a == killers[ply][1]) pa += 2_000;
                if (b == killers[ply][0]) pb += 3_000; else if (b == killers[ply][1]) pb += 2_000;
                int side = s.whiteToMove()?0:1;
                pa += history[side][histIdx(a)];
                pb += history[side][histIdx(b)];
                return Integer.compare(pb, pa);
            }
            return 0;
        });

        int moveNum = 0;
        for (int m : moves){
            moveNum++;
            GameState.Undo u = undo[ply];
            s.makeInPlace(m, u);
            repStack[ply+1] = Zobrist.compute(s);

            int sc;
            boolean isCapture = GameState.isCapture(m) || GameState.promoKind(m) >= 0;
            if (moveNum == 1){
                sc = -negamax(s, depth-1, -beta, -alpha, ply+1);
            } else {
                int d = depth - 1;
                if (!inCheck && !isCapture && d >= 3 && moveNum > 3){
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
                    if (quiet(m)){
                        storeKiller(ply, m);
                        int side = s.whiteToMove()?1:0;
                        history[side][histIdx(m)] += depth * depth;
                    }
                    break;
                }
            }
        }

        int flag = (best <= a0) ? TT.UPPER : (best >= beta ? TT.LOWER : TT.EXACT);
        tt.store(key, depth, flag, toTTScore(best, ply), bestMove, ttAge);
        return best;
    }

    private static boolean isMateScore(int sc){
        return Math.abs(sc) >= MATE - MAX_PLY;
    }

    private static int toTTScore(int sc, int ply){
        if (sc >=  MATE - MAX_PLY) return sc + ply;
        if (sc <= -MATE + MAX_PLY) return sc - ply;
        return sc;
    }

    private static int fromTTScore(int sc, int ply){
        if (sc >=  MATE - MAX_PLY) return sc - ply;
        if (sc <= -MATE + MAX_PLY) return sc + ply;
        return sc;
    }

    private static String scoreStr(int sc){
        if (isMateScore(sc)){
            int matePly = (sc > 0) ? (MATE - sc) : (MATE + sc);
            int mateMoves = (matePly + 1) / 2;
            return (sc > 0) ? ("#"+mateMoves) : ("#-"+mateMoves);
        }
        return String.format("%+d", sc);
    }

    private static int evalSTM(GameState s){
        int whitePOV = Eval.evaluate(s);
        return s.whiteToMove() ? whitePOV
                : -whitePOV;
    }

    private static long rate(long nodes,long ms){ return ms>0 ? (nodes*1000L)/ms : 0; }

    private static String uci(int m){
        if (m <= 0) return "(none)";
        String s = sq(GameState.from(m)) + sq(GameState.to(m));
        int pk = GameState.promoKind(m); if (pk >= 0) s += "nbrq".charAt(pk);
        return s;
    }
    private static String sq(int i){ return ""+(char)('a'+(i&7))+(char)('1'+(i>>>3)); }

    static final class TT {
        static final int EXACT = 0, LOWER = 1, UPPER = 2;

        static final class Entry {
            long key;
            int move;
            int score;
            int depth;
            int flag;
            int age;
        }

        static final class TTOut { public int move; public int score; }

        private final Entry[] table;
        private final int mask;

        TT(int size){
            int cap = 1;
            while (cap < size) cap <<= 1;
            table = new Entry[cap];
            for (int i=0;i<table.length;i++) table[i] = new Entry();
            mask = cap - 1;
        }

        private int index(long key){ return (int)key & mask; }

        boolean probe(long key, int depth, int alpha, int beta, TTOut out){
            Entry e = table[index(key)];
            if (e.key != key){ out.move = 0; return false; }
            out.move = e.move;
            if (e.depth < depth) return false;
            switch (e.flag){
                case EXACT: out.score = e.score; return true;
                case LOWER: if (e.score >= beta)  { out.score = e.score; return true; } break;
                case UPPER: if (e.score <= alpha) { out.score = e.score; return true; } break;
            }
            return false;
        }

        void store(long key, int depth, int flag, int score, int move, int age){
            int idx = index(key);
            Entry e = table[idx];
            if (e.key == 0L || e.key == key || depth > e.depth || age > e.age){
                e.key = key;
                e.depth = depth;
                e.flag = flag;
                e.score = score;
                if (move != 0) e.move = move;
                e.age = age;
            }
        }
    }
}
