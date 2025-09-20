package com.jaceg18.Gameplay;

import com.jaceg18.Gameplay.Utility.BitUtility;
import com.jaceg18.Gameplay.Utility.GameState;

/**
 * Zobrist hashing that works with GameState's public getters.
 * - Hashes piece-square occupancy via getters (no direct WP/WN fields).
 * - Hashes castling rights (0..15), side-to-move.
 * - Hashes EP *file* only if an EP capture is legal this ply.
 */
public final class Zobrist {
    private Zobrist() {}


    private static final long[][] PSQ = new long[12][64];
    // castling 0..15
    private static final long[] CASTLING = new long[16];
    // ep file 0..7
    private static final long[] EP_FILE = new long[8];
    // side to move
    private static final long SIDE = rnd();

    static {
        for (int p = 0; p < 12; p++)
            for (int sq = 0; sq < 64; sq++)
                PSQ[p][sq] = rnd();
        for (int i = 0; i < 16; i++) CASTLING[i] = rnd();
        for (int f = 0; f < 8; f++) EP_FILE[f] = rnd();
    }

    private static long seed = 0x9e3779b97f4a7c15L;

    private static long rnd() {
        long z = (seed += 0x9e3779b97f4a7c15L);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static int bsf(long bb) {
        return Long.numberOfTrailingZeros(bb);
    }

    private static void xorPieces(long bb, int pieceIdx, long[] acc) {
        while (bb != 0) {
            long lsb = bb & -bb;
            int sq = bsf(lsb);
            acc[0] ^= PSQ[pieceIdx][sq];
            bb ^= lsb;
        }
    }

    public static long compute(GameState s) {
        long[] kbox = new long[1];

        // white pieces
        xorPieces(s.pawns(true), 0, kbox);
        xorPieces(s.knights(true), 1, kbox);
        xorPieces(s.bishops(true), 2, kbox);
        xorPieces(s.rooks(true), 3, kbox);
        xorPieces(s.queens(true), 4, kbox);
        xorPieces(s.king(true), 5, kbox);

        // black pieces
        xorPieces(s.pawns(false), 6, kbox);
        xorPieces(s.knights(false), 7, kbox);
        xorPieces(s.bishops(false), 8, kbox);
        xorPieces(s.rooks(false), 9, kbox);
        xorPieces(s.queens(false), 10, kbox);
        xorPieces(s.king(false), 11, kbox);

        long k = kbox[0];

        k ^= CASTLING[s.castlingRights() & 15];

        int ep = s.epSquare();
        if (ep >= 0 && epCaptureIsLegalThisPly(s, ep)) {
            k ^= EP_FILE[ep & 7];
        }

        if (s.whiteToMove()) k ^= SIDE;

        return k;
    }

    private static boolean epCaptureIsLegalThisPly(GameState s, int epSquare) {
        long epBB = 1L << epSquare;
        if (s.whiteToMove()) {

            long fromNW = (epBB >>> 7) & ~BitUtility.FILE_H;
            long fromNE = (epBB >>> 9) & ~BitUtility.FILE_A;
            long src = fromNW | fromNE;
            return (src & s.pawns(true)) != 0L;
        } else {

            long fromSW = (epBB << 7) & ~BitUtility.FILE_A;
            long fromSE = (epBB << 9) & ~BitUtility.FILE_H;
            long src = fromSW | fromSE;
            return (src & s.pawns(false)) != 0L;
        }
    }
}
