package com.jaceg18.Gameplay.Utility;

public class Attacks {


    public static boolean isInCheck(GameState s, boolean whiteKing) {
        long kingBB = s.king(whiteKing);
        if (kingBB == 0) return true;
        int ksq = Long.numberOfTrailingZeros(kingBB);
        return isSquareAttackedBy(s, ksq, !whiteKing);
    }

    public static boolean isSquareAttackedBy(GameState s, int sq, boolean byWhite) {
        long occ = s.allPieces();
        long byP = s.pawns(byWhite);
        long byN = s.knights(byWhite);
        long byB = s.bishops(byWhite);
        long byR = s.rooks(byWhite);
        long byQ = s.queens(byWhite);
        long byK = s.king(byWhite);
        long pawnAtk = byWhite ? BitUtility.whitePawnAttacks(byP)
                : BitUtility.blackPawnAttacks(byP);
        if (((pawnAtk >>> sq) & 1L) != 0) return true;
        long knightAtk = 0L;
        for (long bb = byN; bb != 0; bb &= bb - 1) {
            int from = Long.numberOfTrailingZeros(bb);
            knightAtk |= BitUtility.squaresAKnightCouldAttackFrom(from);
        }
        if (((knightAtk >>> sq) & 1L) != 0) return true;
        long kingAtk = 0L;
        if (byK != 0) {
            int from = Long.numberOfTrailingZeros(byK);
            kingAtk = BitUtility.squaresAKingCouldAttackFrom(from);
            if (((kingAtk >>> sq) & 1L) != 0) return true;
        }
        long diagAtk = 0L;
        for (long bb = (byB | byQ); bb != 0; bb &= bb - 1) {
            int from = Long.numberOfTrailingZeros(bb);
            diagAtk |= BitUtility.squaresABishopCouldSlideTo(from, occ);
        }
        if (((diagAtk >>> sq) & 1L) != 0) return true;
        long orthoAtk = 0L;
        for (long bb = (byR | byQ); bb != 0; bb &= bb - 1) {
            int from = Long.numberOfTrailingZeros(bb);
            orthoAtk |= BitUtility.squaresARookCouldSlideTo(from, occ);
        }
        return ((orthoAtk >>> sq) & 1L) != 0;
    }
}
