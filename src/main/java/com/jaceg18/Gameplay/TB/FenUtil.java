package com.jaceg18.Gameplay.TB;

import com.jaceg18.Gameplay.Utility.GameState;

public final class FenUtil {
    private FenUtil() {}

    public static String toFEN(GameState s) {
        StringBuilder board = new StringBuilder();
        for (int r = 7; r >= 0; r--) {
            int empty = 0;
            for (int f = 0; f < 8; f++) {
                int sq = (r << 3) | f;
                char pc = pieceAt(s, sq);
                if (pc == 0) { empty++; }
                else {
                    if (empty > 0) { board.append(empty); empty = 0; }
                    board.append(pc);
                }
            }
            if (empty > 0) board.append(empty);
            if (r > 0) board.append('/');
        }

        String stm = s.whiteToMove() ? "w" : "b";
        String cr  = castling(s.castlingRights());
        String ep  = (s.epSquare() >= 0) ? sqName(s.epSquare()) : "-";

        return board + " " + stm + " " + cr + " " + ep + " " + s.halfmoveClock() + " " + s.fullmoveNumber;
    }

    private static String castling(int rights) {
        StringBuilder sb = new StringBuilder();
        if ((rights & 0b0001) != 0) sb.append('K');
        if ((rights & 0b0010) != 0) sb.append('Q');
        if ((rights & 0b0100) != 0) sb.append('k');
        if ((rights & 0b1000) != 0) sb.append('q');
        return sb.isEmpty() ? "-" : sb.toString();
    }

    private static String sqName(int sq) {
        int f = sq & 7, r = sq >>> 3;
        return "" + (char)('a' + f) + (char)('1' + r);
    }

    private static char pieceAt(GameState s, int sq) {
        long m = 1L << sq;
        if ((s.pawns(true)   & m) != 0) return 'P';
        if ((s.knights(true) & m) != 0) return 'N';
        if ((s.bishops(true) & m) != 0) return 'B';
        if ((s.rooks(true)   & m) != 0) return 'R';
        if ((s.queens(true)  & m) != 0) return 'Q';
        if ((s.king(true)    & m) != 0) return 'K';

        if ((s.pawns(false)  & m) != 0) return 'p';
        if ((s.knights(false)& m) != 0) return 'n';
        if ((s.bishops(false)& m) != 0) return 'b';
        if ((s.rooks(false)  & m) != 0) return 'r';
        if ((s.queens(false) & m) != 0) return 'q';
        if ((s.king(false)   & m) != 0) return 'k';
        return 0;
    }
}
