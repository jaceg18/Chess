package com.jaceg18.Testing;


import com.jaceg18.Gameplay.Utility.BitUtility;
import com.jaceg18.Gameplay.Utility.GameState;
import com.jaceg18.Gameplay.Utility.MoveGen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

public final class Perft {

    // ---------- Public entry ----------
    public static void main(String[] args) {
        int maxDepth = 6;

        GameState s = new GameState(); // start position
        printHeader();
        for (int d = 0; d <= maxDepth; d++) {
            PerftCounters c = runPerftWithBreakdown(s, d);
            printRow(d, c);
        }
    }

    // ---------- Counters ----------
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

    // ---------- Driver with breakdown ----------
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
            // leaf: count categories based on immediate consequences of each legal move
            for (int m : legal) {
                // pre-categorize by flags
                if (GameState.isCapture(m)) out.captures++;
                if (GameState.isEP(m)) out.ep++;
                if (GameState.isCastle(m)) out.castles++;
                if (GameState.promoKind(m) >= 0) out.promotions++;

                GameState.Undo u = s.make(m);
                // After make(): side flips; now it's opponent to move.
                boolean moverWhite = !s.whiteToMove();
                int oppKingSq = Long.numberOfTrailingZeros(s.king(!moverWhite));

                // checks / discovery / double
                int atkCount = countAttackersToSquare(s, oppKingSq, moverWhite);
                if (atkCount > 0) {
                    out.checks++;
                    boolean moverGivesCheck = movingPieceGivesCheck(s, m, moverWhite, oppKingSq);
                    if (atkCount >= 2) out.doubleChecks++;
                    else if (!moverGivesCheck) out.discoveryChecks++; // single check, not by mover => discovered
                }

                // checkmate? (opponent has no legal moves and is in check)
                if (isSquareAttackedBy(s, oppKingSq, moverWhite)) {
                    List<Integer> reply = generateLegalMoves(s, !moverWhite);
                    if (reply.isEmpty()) out.checkmates++;
                }

                s.unmake(u);
            }
            return out;
        }

        // depth >= 2
        out.nodes = 0;
        for (int m : legal) {
            GameState.Undo u = s.make(m);

            // flags at ply 1 (we only count “immediate” categories once per root move at the leaf layer),
            // but to match classic perft breakdowns, we also accumulate them at deeper depths by recursion.
            PerftCounters child = runPerftWithBreakdown(s, depth - 1);
            out.add(child);

            s.unmake(u);
        }
        return out;
    }

    // ---------- Legal generation via pseudo-legal + filter ----------
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

    // ---------- Checks / attacks ----------
    static boolean isInCheck(GameState s, boolean whiteKing) {
        long kingBB = s.king(whiteKing);
        if (kingBB == 0) return true;
        int ksq = Long.numberOfTrailingZeros(kingBB);
        return isSquareAttackedBy(s, ksq, !whiteKing);
    }

    /** Is square 'sq' attacked by side 'byWhite'? */
    static boolean isSquareAttackedBy(GameState s, int sq, boolean byWhite) {
        return countAttackersToSquare(s, sq, byWhite) > 0;
    }

    /** Count attackers to 'sq' from side 'byWhite'. */
    static int countAttackersToSquare(GameState s, int sq, boolean byWhite) {
        long occ = s.allPieces();

        // pawns
        long pawnAtk = byWhite ? BitUtility.whitePawnAttacks(s.pawns(true))
                : BitUtility.blackPawnAttacks(s.pawns(false));
        int count = (int) Long.bitCount(pawnAtk & (1L << sq));

        // knights
        long nAtk = BitUtility.squaresAKnightCouldAttackFrom(sq); // from the square’s perspective
        count += Long.bitCount(nAtk & (byWhite ? s.knights(true) : s.knights(false)));

        // king (adjacent)
        long kAtk = BitUtility.squaresAKingCouldAttackFrom(sq);
        count += Long.bitCount(kAtk & (byWhite ? s.king(true) : s.king(false)));

        // bishops/queens (diagonals) — first blocker test via slide-from-king
        long diagTargets = BitUtility.squaresABishopCouldSlideTo(sq, occ);
        long diagAttackers = diagTargets & (byWhite ? (s.bishops(true) | s.queens(true))
                : (s.bishops(false) | s.queens(false)));
        count += Long.bitCount(diagAttackers);

        // rooks/queens (orthogonals)
        long orthoTargets = BitUtility.squaresARookCouldSlideTo(sq, occ);
        long orthoAttackers = orthoTargets & (byWhite ? (s.rooks(true) | s.queens(true))
                : (s.rooks(false) | s.queens(false)));
        count += Long.bitCount(orthoAttackers);

        return count;
    }

    /** Did the moving piece itself give (at least one) check after the move? */
    static boolean movingPieceGivesCheck(GameState s, int m, boolean moverWhite, int oppKingSq) {
        int to = GameState.to(m);
        long occ = s.allPieces();
        int kind = GameState.moverKind(m); // 0=P,1=N,2=B,3=R,4=Q,5=K

        switch (kind) {
            case 0: { // pawn
                long atk = moverWhite ? BitUtility.whitePawnAttacks(1L << to)
                        : BitUtility.blackPawnAttacks(1L << to);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
            case 1: { // knight
                long atk = BitUtility.squaresAKnightCouldAttackFrom(to);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
            case 2: { // bishop
                long atk = BitUtility.squaresABishopCouldSlideTo(to, occ);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
            case 3: { // rook
                long atk = BitUtility.squaresARookCouldSlideTo(to, occ);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
            case 4: { // queen
                long atk = BitUtility.squaresAQueenCouldSlideTo(to, occ);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
            case 5: { // king
                long atk = BitUtility.squaresAKingCouldAttackFrom(to);
                return ((atk >>> oppKingSq) & 1L) != 0L;
            }
        }
        return false;
    }

    // ---------- Table printing ----------
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
