package com.jaceg18.Gameplay.Search.AI.Evaluation;

import com.jaceg18.Gameplay.Utility.Attacks;
import com.jaceg18.Gameplay.Utility.GameState;

public final class Eval {

    private static final int P = 100, N = 320, B = 330, R = 500, Q = 900;

    private static final int TEMPO = 10;
    private static final int BISHOP_PAIR = 40;

    private static final int DRAW_SCORE = 0;
    private static final int FIFTY_MOVE_SOFT_START = 80;
    private static final int FIFTY_MOVE_LIMIT = 100;


    private static final int CASTLING_RIGHT = 30;

    private static final int CASTLED_BONUS_OPEN = 75;
    private static final int UNCASTLED_OPEN_PEN = 50;

    private static final int PRECASTLE_KING_MOVE_PEN = 60;
    private static final int KING_WANDER_PEN = 20;

    private static final int DOUBLED_PEN = 12;
    private static final int ISOLATED_PEN = 10;
    private static final int[] PP_BONUS = {0, 0, 12, 20, 36, 60, 100, 0};

    private static final int PAWN_CENTER = 8;
    private static final int KNIGHT_CENTER = 10;

    private static final int KING_CENTER_OPEN_PEN = 15;
    private static final int KING_CENTER_END_BON = 8;

    private static final int SHIELD_PAWN_BON = 12;
    private static final int SHIELD_PAWN_FAR_BON = 6;
    private static final int OPEN_FILE_NEAR_KING_PEN = 12;

    private static final int KING_RING_ATTACK = 6;
    private static final int CHECK_BONUS = 30;

    private static final int PH_N = 1, PH_B = 1, PH_R = 2, PH_Q = 4, PH_MAX = 24;

    private static final long[] FILE = new long[8];
    private static final long CENTER4 = mask("d4", "e4", "d5", "e5");
    private static final long CENTER_KING = maskRect(2, 2, 5, 5);

    private static final long[] IN_FRONT_W = new long[64];
    private static final long[] IN_FRONT_B = new long[64];

    static {
        for (int f=0; f<8; f++) FILE[f] = 0x0101010101010101L << f;
        for (int sq=0; sq<64; sq++){
            long m = 1L << sq;
            long north = 0L, south = 0L;
            long x = m;
            while ((x <<= 8) != 0) north |= x;
            x = m;
            while ((x >>>= 8) != 0) south |= x;
            IN_FRONT_W[sq] = north;
            IN_FRONT_B[sq] = south;
        }
    }

    private Eval() {}

    public static int evaluate(GameState s) {
        long WP = s.pawns(true),   BP = s.pawns(false);
        long WN = s.knights(true), BN = s.knights(false);
        long WB = s.bishops(true), BB = s.bishops(false);
        long WR = s.rooks(true),   BR = s.rooks(false);
        long WQ = s.queens(true),  BQ = s.queens(false);
        long WK = s.king(true),    BK = s.king(false);
        int wP = bc(WP), bP = bc(BP);
        int wN = bc(WN), bN = bc(BN);
        int wB = bc(WB), bB = bc(BB);
        int wR = bc(WR), bR = bc(BR);
        int wQ = bc(WQ), bQ = bc(BQ);

        int mat =  P*(wP-bP) + N*(wN-bN) + B*(wB-bB) + R*(wR-bR) + Q*(wQ-bQ);
        int bp = ((wB >= 2) ? BISHOP_PAIR : 0) - ((bB >= 2) ? BISHOP_PAIR : 0);
        int cr = s.castlingRights();

        int maxPhaseRaw = PH_N*4 + PH_B*4 + PH_R*4 + PH_Q*2;
        int phaseRaw    = PH_N*(wN+bN) + PH_B*(wB+bB) + PH_R*(wR+bR) + PH_Q*(wQ+bQ);
        int phase       = (phaseRaw * PH_MAX + maxPhaseRaw/2) / maxPhaseRaw;
        int endgame     = PH_MAX - phase;

        int crWhiteOpen = ((cr & 0b0001) != 0 ? CASTLING_RIGHT : 0)
                + ((cr & 0b0010) != 0 ? CASTLING_RIGHT : 0);
        int crBlackOpen = ((cr & 0b0100) != 0 ? CASTLING_RIGHT : 0)
                + ((cr & 0b1000) != 0 ? CASTLING_RIGHT : 0);
        int crScore     = scale(crWhiteOpen, 0, phase, endgame)
                - scale(crBlackOpen, 0, phase, endgame);

        int pst = 0;
        pst += pawnStructure(WP, BP, true);
        pst -= pawnStructure(BP, WP, false);
        pst += PAWN_CENTER * bc(WP & CENTER4) - PAWN_CENTER * bc(BP & CENTER4);
        pst += KNIGHT_CENTER * bc(WN & CENTER4) - KNIGHT_CENTER * bc(BN & CENTER4);

        int kOpen = 0, kEnd = 0;
        kOpen -= KING_CENTER_OPEN_PEN * bc(WK & CENTER_KING);
        kOpen += KING_CENTER_OPEN_PEN * bc(BK & CENTER_KING);
        kEnd  += KING_CENTER_END_BON  * bc(WK & CENTER_KING);
        kEnd  -= KING_CENTER_END_BON  * bc(BK & CENTER_KING);
        int kingScaled = scale(kOpen, kEnd, phase, endgame);

        int tempo = s.whiteToMove() ? TEMPO : -TEMPO;

        int safetyOpen = 0;
        int wKSq = (WK != 0) ? Long.numberOfTrailingZeros(WK) : -1;
        int bKSq = (BK != 0) ? Long.numberOfTrailingZeros(BK) : -1;

        if (phase > 0) {
            boolean wCastled = (wKSq == sq("g1") || wKSq == sq("c1"));
            boolean bCastled = (bKSq == sq("g8") || bKSq == sq("c8"));

            if (wCastled) safetyOpen += CASTLED_BONUS_OPEN;
            else if (wKSq == sq("e1")) safetyOpen -= UNCASTLED_OPEN_PEN;

            if (bCastled) safetyOpen -= CASTLED_BONUS_OPEN;
            else if (bKSq == sq("e8")) safetyOpen += UNCASTLED_OPEN_PEN;

            boolean wHasCastleRights = (cr & 0b0011) != 0;
            boolean bHasCastleRights = (cr & 0b1100) != 0;

            if (!wCastled && wKSq >= 0) {
                if (wHasCastleRights && wKSq != sq("e1")) safetyOpen -= PRECASTLE_KING_MOVE_PEN;
                if (wKSq != sq("e1") && wKSq != sq("c1") && wKSq != sq("g1")) safetyOpen -= KING_WANDER_PEN;
            }
            if (!bCastled && bKSq >= 0) {
                if (bHasCastleRights && bKSq != sq("e8")) safetyOpen += PRECASTLE_KING_MOVE_PEN;
                if (bKSq != sq("e8") && bKSq != sq("c8") && bKSq != sq("g8")) safetyOpen += KING_WANDER_PEN;
            }

            safetyOpen += pawnShieldWhite(WP, wKSq);
            safetyOpen -= pawnShieldBlack(BP, bKSq);

            safetyOpen -= openFilesNearKing(WP, wKSq, true)  * OPEN_FILE_NEAR_KING_PEN;
            safetyOpen += openFilesNearKing(BP, bKSq, false) * OPEN_FILE_NEAR_KING_PEN;

            safetyOpen += kingRingPressureWhite(s, bKSq) * KING_RING_ATTACK;
            safetyOpen -= kingRingPressureBlack(s, wKSq) * KING_RING_ATTACK;
            if (wKSq >= 0 && Attacks.isInCheck(s, true))  safetyOpen -= CHECK_BONUS;
            if (bKSq >= 0 && Attacks.isInCheck(s, false)) safetyOpen += CHECK_BONUS;
        }
        int safetyScaled = scale(safetyOpen, 0, phase, endgame);
        int posScaled = scale(pst, pst/2, phase, endgame) + kingScaled + safetyScaled;

        int raw = mat + bp + crScore + posScaled + tempo;

        boolean fiftyExempt = hasLikelyMateWithoutPawnMove(WP, BP, WR, BR, WQ, BQ, WN, BN, WB, BB);
        raw = scaleByFiftyMoveClock(raw, s.halfmoveClock(), fiftyExempt);

        if (isInsufficientMaterial(WP, BP, WN, BN, WB, BB, WR, BR, WQ, BQ)) {
            return DRAW_SCORE;
        }

        raw = compressMinorOnlyIfPawnless(raw, WP, BP, WR, BR, WQ, BQ, WN, BN, WB, BB);
        return raw;
    }
    private static boolean hasLikelyMateWithoutPawnMove(
            long WP, long BP,
            long WR, long BR, long WQ, long BQ,
            long WN, long BN, long WB, long BB) {

        if ((WR | BR | WQ | BQ) != 0) return true;

        boolean noPawns = (WP | BP) == 0;
        if (noPawns) {
            int wMin = Long.bitCount(WN) + Long.bitCount(WB);
            int bMin = Long.bitCount(BN) + Long.bitCount(BB);
            if ((wMin >= 2 && bMin == 0) || (bMin >= 2 && wMin == 0)) return true;
        }
        return false;
    }


    private static int scaleByFiftyMoveClock(int eval, int hmc, boolean exempt) {
        if (exempt) return eval;
        if (hmc >= FIFTY_MOVE_LIMIT) return DRAW_SCORE;
        if (hmc <= FIFTY_MOVE_SOFT_START) return eval;
        int window = FIFTY_MOVE_LIMIT - FIFTY_MOVE_SOFT_START;
        int remain = FIFTY_MOVE_LIMIT - hmc;
        int num = eval * remain;
        int den = window;
        return (num >= 0) ? (num + den/2) / den : -(( -num + den/2) / den);
    }




    private static boolean isInsufficientMaterial(long WP, long BP,
                                                  long WN, long BN,
                                                  long WB, long BB,
                                                  long WR, long BR,
                                                  long WQ, long BQ) {

        if ( (WP | BP | WR | BR | WQ | BQ) != 0 ) return false;

        int wMin = bc(WN) + bc(WB);
        int bMin = bc(BN) + bc(BB);


        if (wMin == 0 && bMin == 0) return true;


        if ((wMin == 1 && bMin == 0) || (wMin == 0 && bMin == 1)) return true;


        if ((wMin == 2 && bc(WN) == 2 && bMin == 0) ||
                (bMin == 2 && bc(BN) == 2 && wMin == 0)) return true;


        return wMin == 1 && bMin == 1 && bc(WB) == 1 && bc(BB) == 1;
    }

    private static int compressMinorOnlyIfPawnless(int eval,
                                                   long WP, long BP,
                                                   long WR, long BR, long WQ, long BQ,
                                                   long WN, long BN, long WB, long BB) {
        boolean noPawns  = (WP | BP) == 0;
        boolean noMajors = (WR | BR | WQ | BQ) == 0;
        if (!noPawns || !noMajors) return eval;

        int wMin = bc(WN) + bc(WB);
        int bMin = bc(BN) + bc(BB);

        if ((wMin >= 2 && bMin == 0) || (bMin >= 2 && wMin == 0)) return eval;

        int minors = wMin + bMin;

        int denom = (minors <= 2) ? 2 : 3;

        return (eval >= 0) ? (eval + denom/2) / denom : -(( -eval + denom/2) / denom);
    }



    private static int pawnStructure(long myPawns, long oppPawns, boolean white) {
        int score = 0;
        for (int f=0; f<8; f++){
            int n = Long.bitCount(myPawns & FILE[f]);
            if (n > 1) score -= DOUBLED_PEN * (n - 1);
        }
        long myOnA = projectToFiles(myPawns, 0), myOnB = projectToFiles(myPawns, 1),
                myOnC = projectToFiles(myPawns, 2), myOnD = projectToFiles(myPawns, 3),
                myOnE = projectToFiles(myPawns, 4), myOnF = projectToFiles(myPawns, 5),
                myOnG = projectToFiles(myPawns, 6), myOnH = projectToFiles(myPawns, 7);
        long iso = 0L;
        long pawns = myPawns;
        while (pawns != 0){
            long p = pawns & -pawns; pawns ^= p;
            int sq = Long.numberOfTrailingZeros(p), f = sq & 7;
            boolean hasNeighbor =
                    (f>0 && (FILE[f-1] & (switch(f-1){case 0->myOnA;case 1->myOnB;case 2->myOnC;case 3->myOnD;case 4->myOnE;case 5->myOnF;case 6->myOnG;default->0L;}))!=0) ||
                            (f<7 && (FILE[f+1] & (switch(f+1){case 0->myOnA;case 1->myOnB;case 2->myOnC;case 3->myOnD;case 4->myOnE;case 5->myOnF;case 6->myOnG;default->0L;}))!=0);
            if (!hasNeighbor) iso |= p;
        }
        score -= ISOLATED_PEN * Long.bitCount(iso);
        long passed = 0L;
        long tmp = myPawns;
        while (tmp != 0){
            long p = tmp & -tmp; tmp ^= p;
            int sq = Long.numberOfTrailingZeros(p), f = sq & 7, r = sq >>> 3;
            long front = (white ? IN_FRONT_W[sq] : IN_FRONT_B[sq]);
            long block = (FILE[f] | (f>0?FILE[f-1]:0L) | (f<7?FILE[f+1]:0L)) & front & oppPawns;
            if (block == 0) passed |= p;
            int rr = white ? r : (7 - r);
            if ((p & passed) != 0) score += PP_BONUS[rr];
        }
        return score;
    }


    private static int pawnShieldWhite(long WP, int kSq){
        if (kSq < 0) return 0;
        int f = kSq & 7;
        int score = 0;
        for (int df=-1; df<=1; df++){
            int ff = f + df; if (ff < 0 || ff > 7) continue;
            long fMask = FILE[ff];
            long r2 = fMask & 0x000000000000FF00L;
            long r3 = fMask & 0x0000000000FF0000L;
            if ((WP & r2) != 0) score += SHIELD_PAWN_BON;
            if ((WP & r3) != 0) score += SHIELD_PAWN_FAR_BON;
        }
        return score;
    }

    private static int pawnShieldBlack(long BP, int kSq){
        if (kSq < 0) return 0;
        int f = kSq & 7;
        int score = 0;
        for (int df=-1; df<=1; df++){
            int ff = f + df; if (ff < 0 || ff > 7) continue;
            long fMask = FILE[ff];
            long r7 = fMask & 0x00FF000000000000L;
            long r6 = fMask & 0x0000FF0000000000L;
            if ((BP & r7) != 0) score += SHIELD_PAWN_BON;
            if ((BP & r6) != 0) score += SHIELD_PAWN_FAR_BON;
        }
        return score;
    }

    private static int openFilesNearKing(long myPawns, int kSq, boolean white){
        if (kSq < 0) return 0;
        int f = kSq & 7;
        long frontMask = white ? IN_FRONT_W[kSq] : IN_FRONT_B[kSq];
        int openCnt = 0;
        for (int df = -1; df <= 1; df++){
            int ff = f + df; if (ff < 0 || ff > 7) continue;
            long laneForward = FILE[ff] & frontMask;
            if ((myPawns & laneForward) == 0) openCnt++;
        }
        return openCnt;
    }


    private static int kingRingPressureWhite(GameState s, int blackKingSq){
        if (blackKingSq < 0) return 0;
        long ring = kingRingMask(blackKingSq);
        int score = 0;
        long m = ring;
        while (m != 0){
            int sq = Long.numberOfTrailingZeros(m);
            m &= m-1;
            if (Attacks.isSquareAttackedBy(s, sq, true)) score++;
        }
        return score;
    }

    private static int kingRingPressureBlack(GameState s, int whiteKingSq){
        if (whiteKingSq < 0) return 0;
        long ring = kingRingMask(whiteKingSq);
        int score = 0;
        long m = ring;
        while (m != 0){
            int sq = Long.numberOfTrailingZeros(m);
            m &= m-1;
            if (Attacks.isSquareAttackedBy(s, sq, false)) score++;
        }
        return score;
    }

    private static long kingRingMask(int sq){
        long attacks = 0L;
        int f = sq & 7, r = sq >>> 3;
        for (int dr=-1; dr<=1; dr++){
            for (int df=-1; df<=1; df++){
                if (dr==0 && df==0) continue;
                int ff = f+df, rr = r+dr;
                if (ff>=0 && ff<8 && rr>=0 && rr<8){
                    attacks |= 1L << (rr*8+ff);
                }
            }
        }
        return attacks;
    }

    private static int bc(long x){ return Long.bitCount(x); }

    private static int scale(int openScore, int endScore, int opening, int endgame){
        int num = openScore * opening + endScore * endgame;
        return (num >= 0) ? (num + PH_MAX/2) / PH_MAX : -(( -num + PH_MAX/2) / PH_MAX);
    }

    private static long mask(String... squares){
        long m=0; for (String s : squares) m |= 1L << ((s.charAt(1)-'1')*8 + (s.charAt(0)-'a'));
        return m;
    }
    private static long maskRect(int f1,int r1,int f2,int r2){
        long m=0; for(int r=r1;r<=r2;r++) for(int f=f1;f<=f2;f++) m|=1L<<(r*8+f); return m;
    }

    public static int sq(String algebraic){
        return ((algebraic.charAt(1)-'1')*8 + (algebraic.charAt(0)-'a'));
    }

    private static long projectToFiles(long pawns, int file){
        return (pawns & FILE[file]) != 0 ? FILE[file] : 0L;
    }
}
