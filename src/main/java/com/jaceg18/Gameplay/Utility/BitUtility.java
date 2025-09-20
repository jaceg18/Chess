package com.jaceg18.Gameplay.Utility;

import java.util.Locale;

/**
 * Bitboard utilities with plain-English method names.
 *
 * Mapping: A1 is bit 0 (LSB), H8 is bit 63 (MSB).
 *   file = sq & 7   (0..7 -> a..h)
 *   rank = sq >>> 3 (0..7 -> 1..8 minus 1)
 */
public final class BitUtility {

    private BitUtility() {}

    // One-bit mask for each square (index 0..63)
    public static final long[] ONE_AT_SQUARE = new long[64];

    // Rank/file masks
    public static final long[] RANK_MASK = new long[8];
    public static final long[] FILE_MASK = new long[8];

    // Edges (useful for shifts)
    public static final long FILE_A = 0x0101010101010101L;
    public static final long FILE_H = 0x8080808080808080L;
    public static final long RANK_1 = 0x00000000000000FFL;
    public static final long RANK_8 = 0xFF00000000000000L;

    // Attack tables (no occupancy needed)
    public static final long[] KNIGHT_ATTACKS = new long[64];
    public static final long[] KING_ATTACKS   = new long[64];
    public static final long[] WHITE_PAWN_ATTACKS = new long[64];
    public static final long[] BLACK_PAWN_ATTACKS = new long[64];

    static {
        // one-bit masks
        for (int sq = 0; sq < 64; sq++) ONE_AT_SQUARE[sq] = 1L << sq;

        // ranks
        for (int r = 0; r < 8; r++) {
            long mask = 0L;
            for (int f = 0; f < 8; f++) mask |= oneFor(f, r);
            RANK_MASK[r] = mask;
        }

        // files
        for (int f = 0; f < 8; f++) {
            long mask = 0L;
            for (int r = 0; r < 8; r++) mask |= oneFor(f, r);
            FILE_MASK[f] = mask;
        }

        // king / knight / pawn attack tables
        for (int sq = 0; sq < 64; sq++) {
            KING_ATTACKS[sq]   = kingFrom(sq);
            KNIGHT_ATTACKS[sq] = knightFrom(sq);
            WHITE_PAWN_ATTACKS[sq] = whitePawnAttacksFrom(sq);
            BLACK_PAWN_ATTACKS[sq] = blackPawnAttacksFrom(sq);
        }
    }

    // ---------- mapping helpers ----------
    public static int squareIndexOf(String algebraic) {
        // "a1".."h8"
        if (algebraic == null || algebraic.length() != 2) throw new IllegalArgumentException("bad square: " + algebraic);
        algebraic = algebraic.toLowerCase(Locale.ROOT);
        int file = algebraic.charAt(0) - 'a';           // 0..7
        int rank = algebraic.charAt(1) - '1';           // 0..7
        if (file < 0 || file > 7 || rank < 0 || rank > 7) throw new IllegalArgumentException("bad square: " + algebraic);
        return (rank << 3) | file;
    }


    public static long maskFor(String algebraicSquare) {
        return ONE_AT_SQUARE[squareIndexOf(algebraicSquare)];
    }

    public static int fileIndex(int sq) { return sq & 7; }       // 0..7 -> a..h
    public static int rankIndex(int sq) { return sq >>> 3; }     // 0..7 -> 1..8 minus 1

    private static long oneFor(int file, int rank) { return 1L << (rank * 8 + file); }

    // ---------- simple shifts (masked so they don't wrap) ----------
    // Think: "move all bits one square north/south/east/west"
    public static long oneStepNorth(long bb) { return (bb << 8); }
    public static long oneStepSouth(long bb) { return (bb >>> 8); }

    public static long oneStepEast(long bb)  { return (bb << 1) & ~FILE_A; } // east = to higher files; mask to prevent wrap from a->b confusion (we mask opposite edge)
    public static long oneStepWest(long bb)  { return (bb >>> 1) & ~FILE_H; }

    public static long oneStepNorthEast(long bb) { return (bb << 9) & ~FILE_A; }
    public static long oneStepNorthWest(long bb) { return (bb << 7) & ~FILE_H; }
    public static long oneStepSouthEast(long bb) { return (bb >>> 7) & ~FILE_A; }
    public static long oneStepSouthWest(long bb) { return (bb >>> 9) & ~FILE_H; }

    // ---------- attacks for single pieces (no occupancy) ----------
    public static long squaresAKingCouldAttackFrom(int sq)   { return KING_ATTACKS[sq]; }
    public static long squaresAKnightCouldAttackFrom(int sq) { return KNIGHT_ATTACKS[sq]; }
    public static long squaresAWhitePawnCouldAttackFrom(int sq) { return WHITE_PAWN_ATTACKS[sq]; }
    public static long squaresABlackPawnCouldAttackFrom(int sq) { return BLACK_PAWN_ATTACKS[sq]; }

    // ---------- sliding attacks (need occupancy) ----------
    // These compute "ray" moves until blocked by occupancy. They include empty squares up to the blocker,
    // and include the blocker square itself (so you can later intersect with enemy pieces to get captures).
    public static long squaresARookCouldSlideTo(int sq, long occupancy) {
        return slideNorth(sq, occupancy) | slideSouth(sq, occupancy) | slideEast(sq, occupancy) | slideWest(sq, occupancy);
    }

    public static long squaresABishopCouldSlideTo(int sq, long occupancy) {
        return slideNE(sq, occupancy) | slideNW(sq, occupancy) | slideSE(sq, occupancy) | slideSW(sq, occupancy);
    }

    public static long squaresAQueenCouldSlideTo(int sq, long occupancy) {
        return squaresARookCouldSlideTo(sq, occupancy) | squaresABishopCouldSlideTo(sq, occupancy);
    }

    public static long whitePawnAttacks(long whitePawns) {
        return oneStepNorthEast(whitePawns) | oneStepNorthWest(whitePawns);
    }
    public static long blackPawnAttacks(long blackPawns) {
        return oneStepSouthEast(blackPawns) | oneStepSouthWest(blackPawns);
    }

    // ---------- internal helpers for attack tables ----------
    private static long kingFrom(int sq) {
        long bb = ONE_AT_SQUARE[sq];
        return oneStepNorth(bb) | oneStepSouth(bb) | oneStepEast(bb) | oneStepWest(bb)
                | oneStepNorthEast(bb) | oneStepNorthWest(bb) | oneStepSouthEast(bb) | oneStepSouthWest(bb);
    }

    private static long knightFrom(int sq) {
        long bb = ONE_AT_SQUARE[sq];
        long n1 = (bb << 17) & ~FILE_A;
        long n2 = (bb << 15) & ~FILE_H;
        long n3 = (bb << 10) & ~(FILE_A | FILE_B());
        long n4 = (bb << 6)  & ~(FILE_H | FILE_G());
        long n5 = (bb >>> 17) & ~FILE_H;
        long n6 = (bb >>> 15) & ~FILE_A;
        long n7 = (bb >>> 10) & ~(FILE_H | FILE_G());
        long n8 = (bb >>> 6)  & ~(FILE_A | FILE_B());
        return n1|n2|n3|n4|n5|n6|n7|n8;
    }

    private static long whitePawnAttacksFrom(int sq) {
        long bb = ONE_AT_SQUARE[sq];
        return oneStepNorthEast(bb) | oneStepNorthWest(bb);
    }

    private static long blackPawnAttacksFrom(int sq) {
        long bb = ONE_AT_SQUARE[sq];
        return oneStepSouthEast(bb) | oneStepSouthWest(bb);
    }

    // file masks for “one-away from edge” checks
    private static long FILE_B() { return FILE_MASK[1]; }
    private static long FILE_G() { return FILE_MASK[6]; }

    // ---------- sliding in each direction (stop at first blocker) ----------
    private static long slideNorth(int sq, long occ) {
        long attacks = 0L, blockerMask;
        int r = rankIndex(sq);
        for (int rr = r + 1; rr < 8; rr++) {
            int s = (rr << 3) | fileIndex(sq);
            attacks |= ONE_AT_SQUARE[s];
            blockerMask = ONE_AT_SQUARE[s];
            if ((occ & blockerMask) != 0) break;
        }
        return attacks;
    }

    private static long slideSouth(int sq, long occ) {
        long attacks = 0L, blockerMask;
        int r = rankIndex(sq);
        for (int rr = r - 1; rr >= 0; rr--) {
            int s = (rr << 3) | fileIndex(sq);
            attacks |= ONE_AT_SQUARE[s];
            blockerMask = ONE_AT_SQUARE[s];
            if ((occ & blockerMask) != 0) break;
        }
        return attacks;
    }

    private static long slideEast(int sq, long occ) {
        long attacks = 0L, blockerMask;
        int f = fileIndex(sq);
        for (int ff = f + 1; ff < 8; ff++) {
            int s = (rankIndex(sq) << 3) | ff;
            attacks |= ONE_AT_SQUARE[s];
            blockerMask = ONE_AT_SQUARE[s];
            if ((occ & blockerMask) != 0) break;
        }
        return attacks;
    }

    private static long slideWest(int sq, long occ) {
        long attacks = 0L, blockerMask;
        int f = fileIndex(sq);
        for (int ff = f - 1; ff >= 0; ff--) {
            int s = (rankIndex(sq) << 3) | ff;
            attacks |= ONE_AT_SQUARE[s];
            blockerMask = ONE_AT_SQUARE[s];
            if ((occ & blockerMask) != 0) break;
        }
        return attacks;
    }

    private static long slideNE(int sq, long occ) {
        long attacks = 0L;
        int f = fileIndex(sq), r = rankIndex(sq);
        int ff = f + 1, rr = r + 1;
        while (ff < 8 && rr < 8) {
            int s = (rr << 3) | ff;
            attacks |= ONE_AT_SQUARE[s];
            if ((occ & ONE_AT_SQUARE[s]) != 0) break;
            ff++; rr++;
        }
        return attacks;
    }

    private static long slideNW(int sq, long occ) {
        long attacks = 0L;
        int f = fileIndex(sq), r = rankIndex(sq);
        int ff = f - 1, rr = r + 1;
        while (ff >= 0 && rr < 8) {
            int s = (rr << 3) | ff;
            attacks |= ONE_AT_SQUARE[s];
            if ((occ & ONE_AT_SQUARE[s]) != 0) break;
            ff--; rr++;
        }
        return attacks;
    }

    private static long slideSE(int sq, long occ) {
        long attacks = 0L;
        int f = fileIndex(sq), r = rankIndex(sq);
        int ff = f + 1, rr = r - 1;
        while (ff < 8 && rr >= 0) {
            int s = (rr << 3) | ff;
            attacks |= ONE_AT_SQUARE[s];
            if ((occ & ONE_AT_SQUARE[s]) != 0) break;
            ff++; rr--;
        }
        return attacks;
    }

    private static long slideSW(int sq, long occ) {
        long attacks = 0L;
        int f = fileIndex(sq), r = rankIndex(sq);
        int ff = f - 1, rr = r - 1;
        while (ff >= 0 && rr >= 0) {
            int s = (rr << 3) | ff;
            attacks |= ONE_AT_SQUARE[s];
            if ((occ & ONE_AT_SQUARE[s]) != 0) break;
            ff--; rr--;
        }
        return attacks;
    }

    // ---------- convenience masks ----------
    public static long maskForRank(int rank1to8) {
        if (rank1to8 < 1 || rank1to8 > 8) throw new IllegalArgumentException();
        return RANK_MASK[rank1to8 - 1];
    }

}
