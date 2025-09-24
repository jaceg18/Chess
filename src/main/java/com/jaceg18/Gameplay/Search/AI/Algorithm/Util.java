package com.jaceg18.Gameplay.Search.AI.Algorithm;



import com.jaceg18.Gameplay.Search.AI.SearchConstants;
import com.jaceg18.Gameplay.Utility.GameState;

public final class Util {
    static boolean isQuiet(int m){
        return !GameState.isCapture(m) && GameState.promoKind(m) < 0;
    }
    static int histIdx(int m){ return (GameState.from(m) << 6) | GameState.to(m); }

    static long rate(long nodes,long ms){ return ms>0 ? (nodes*1000L)/ms : 0; }

    public static boolean isMateScore(int sc){
        return Math.abs(sc) >= SearchConstants.MATE - SearchConstants.MAX_PLY;
    }
    public static int toTTScore(int sc, int ply){
        if (sc >=  SearchConstants.MATE - SearchConstants.MAX_PLY) return sc + ply;
        if (sc <= -SearchConstants.MATE + SearchConstants.MAX_PLY) return sc - ply;
        return sc;
    }
    public static int fromTTScore(int sc, int ply){
        if (sc >=  SearchConstants.MATE - SearchConstants.MAX_PLY) return sc - ply;
        if (sc <= -SearchConstants.MATE + SearchConstants.MAX_PLY) return sc + ply;
        return sc;
    }
    public static String uci(int m){
        if (m <= 0) return "(none)";
        String s = sq(GameState.from(m)) + sq(GameState.to(m));
        int pk = GameState.promoKind(m); if (pk >= 0) s += "nbrq".charAt(pk);
        return s;
    }
    static String sq(int i){ return ""+(char)('a'+(i&7))+(char)('1'+(i>>>3)); }

    public static String scoreStr(int sc){
        if (isMateScore(sc)){
            int matePly = (sc > 0) ? (SearchConstants.MATE - sc) : (SearchConstants.MATE + sc);
            int mateMoves = (matePly + 1) / 2;
            return (sc > 0) ? ("#"+mateMoves) : ("#-"+mateMoves);
        }
        return String.format("%+d", sc);
    }
}
