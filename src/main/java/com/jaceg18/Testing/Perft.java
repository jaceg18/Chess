package com.jaceg18.Testing;


import com.jaceg18.Gameplay.Utility.BitUtility;
import com.jaceg18.Gameplay.Utility.GameState;
import com.jaceg18.Gameplay.Utility.MoveGen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

public final class Perft {

    public static void main(String[] args) {
        int maxDepth = 6;

        GameState s = new GameState();
        printHeader();
        for (int d = 0; d <= maxDepth; d++) {
            PerftCounters c = runPerftWithBreakdown(s, d);
            printRow(d, c);
        }
    }

    static final class PerftCounters {
        long nodes;
        long captures;
        long ep;
        long castles;
        long promotions;
        long checks;
        long discoveryChecks;
        long doubleChecks;
        long checkmates;

        void add(PerftCounters other){
            nodes += other.nodes;
            captures += other.captures;
            ep += other.ep;
            castles += other.castles;
            promotions += other.promotions;
            checks += other.checks;
            discoveryChecks += other.discoveryChecks;
            doubleChecks += other.doubleChecks;
            checkmates += other.checkmates;
        }
    }

    static PerftCounters runPerftWithBreakdown(GameState s, int depth) {
        PerftCounters out = new PerftCounters();
        if (depth == 0) {
            out.nodes = 1;
            return out;
        }
        boolean side = s.whiteToMove();

        List<Integer> legal = generateLegalMoves(s, side);
        out.nodes = legal.size();
        if (depth == 1) {
            for (int m : legal) {
                if (GameState.isCapture(m)) out.captures++;
                if (GameState.isEP(m)) out.ep++;
                if (GameState.isCastle(m)) out.castles++;
                if (GameState.promoKind(m) >= 0) out.promotions++;

                GameState.Undo u = s.make(m);
                boolean moverWhite = !s.whiteToMove();
                int oppKingSq = Long.numberOfTrailingZeros(s.king(!moverWhite));
                int atkCount = countAttackersToSquare(s, oppKingSq, moverWhite);
                if (atkCount > 0) {
                    out.checks++;
                    boolean moverGivesCheck = movingPieceGivesCheck(s, m, moverWhite, oppKingSq);
                    if (atkCount >= 2) out.doubleChecks++;
                    else if (!moverGivesCheck) out.discoveryChecks++;
                }
                if (isSquareAttackedBy(s, oppKingSq, moverWhite)) {
                    List<Integer> reply = generateLegalMoves(s, !moverWhite);
                    if (reply.isEmpty()) out.checkmates++;
                }

                s.unmake(u);
            }
            return out;
        }
        out.nodes = 0;
        for (int m : legal) {
            GameState.Undo u = s.make(m);
            PerftCounters child = runPerftWithBreakdown(s, depth - 1);
            out.add(child);

            s.unmake(u);
        }
        return out;
    }

    static List<Integer> generateLegalMoves(GameState s, boolean whiteToMove) {
        List<Integer> legal = new ArrayList<>(64);
        MoveGen.generate(s, whiteToMove, (IntConsumer) m -> {
            GameState.Undo u = s.make(m);
            boolean moverIsWhite = !s.whiteToMove();
            boolean ok = !isInCheck(s, moverIsWhite);
            s.unmake(u);
            if (ok) legal.add(m);
        });
        return legal;
    }

    static boolean isInCheck(GameState s, boolean whiteKing) {
        long kingBB = s.king(whiteKing);
        if (kingBB == 0) return true;
        int ksq = Long.numberOfTrailingZeros(kingBB);
        return isSquareAttackedBy(s, ksq, !whiteKing);
    }

    static boolean isSquareAttackedBy(GameState s, int sq, boolean byWhite) {
        return countAttackersToSquare(s, sq, byWhite) > 0;
    }

    static int countAttackersToSquare(GameState s, int sq, boolean byWhite) {
        long occ = s.allPieces();
        long pawnAtk = byWhite ? BitUtility.whitePawnAttacks(s.pawns(true))
                : BitUtility.blackPawnAttacks(s.pawns(false));
        int count = (int) Long.bitCount(pawnAtk & (1L << sq));
        long nAtk = BitUtility.squaresAKnightCouldAttackFrom(sq);
        count += Long.bitCount(nAtk & (byWhite ? s.knights(true) : s.knights(false)));
        long kAtk = BitUtility.squaresAKingCouldAttackFrom(sq);
        count += Long.bitCount(kAtk & (byWhite ? s.king(true) : s.king(false)));
        long diagTargets = BitUtility.squaresABishopCouldSlideTo(sq, occ);
        long diagAttackers = diagTargets & (byWhite ? (s.bishops(true) | s.queens(true))
                : (s.bishops(false) | s.queens(false)));
        count += Long.bitCount(diagAttackers);
        long orthoTargets = BitUtility.squaresARookCouldSlideTo(sq, occ);
        long orthoAttackers = orthoTargets & (byWhite ? (s.rooks(true) | s.queens(true))
                : (s.rooks(false) | s.queens(false)));
        count += Long.bitCount(orthoAttackers);

        return count;
    }

    static boolean movingPieceGivesCheck(GameState s, int m, boolean moverWhite, int oppKingSq) {
        int to = GameState.to(m);
        long occ = s.allPieces();
        int kind = GameState.moverKind(m);

        switch (kind) {
            case 0: {
                long atk = moverWhite ? BitUtility.whitePawnAttacks(1L << to)
                        : BitUtility.blackPawnAttacks(1L << to);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
            case 1: {
                long atk = BitUtility.squaresAKnightCouldAttackFrom(to);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
            case 2: {
                long atk = BitUtility.squaresABishopCouldSlideTo(to, occ);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
            case 3: {
                long atk = BitUtility.squaresARookCouldSlideTo(to, occ);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
            case 4: {
                long atk = BitUtility.squaresAQueenCouldSlideTo(to, occ);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
            case 5: {
                long atk = BitUtility.squaresAKingCouldAttackFrom(to);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
        }
        return false;
    }

    static void printHeader() {
        System.out.println("Depth\tNodes\tCaptures\tE.p.\tCastles\tPromotions\tChecks\tDiscovery Checks\tDouble Checks\tCheckmates");
    }

    static void printRow(int depth, PerftCounters c) {
        System.out.println(
                depth + "\t" +
                        fmt(c.nodes) + "\t" +
                        fmt(c.captures) + "\t" +
                        fmt(c.ep) + "\t" +
                        fmt(c.castles) + "\t" +
                        fmt(c.promotions) + "\t" +
                        fmt(c.checks) + "\t" +
                        fmt(c.discoveryChecks) + "\t" +
                        fmt(c.doubleChecks) + "\t" +
                        fmt(c.checkmates)
        );
    }

    static String fmt(long n) {
        return String.format(Locale.US, "%,d", n);
    }
}
