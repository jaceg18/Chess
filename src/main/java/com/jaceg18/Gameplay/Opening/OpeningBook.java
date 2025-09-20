package com.jaceg18.Gameplay.Opening;



import com.jaceg18.Gameplay.Utility.GameState;
import com.jaceg18.Gameplay.Utility.MoveGen;
import com.jaceg18.Gameplay.Zobrist;

import java.io.*;
import java.util.*;



public final class OpeningBook {
    private final Map<Long, Int2IntMap> book = new HashMap<>();
    private final Random rng = new Random(0xC0FFEE);

    public static OpeningBook load(String path) throws IOException {
        OpeningBook ob = new OpeningBook();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) ob.ingestLine(line);
        }
        return ob;
    }


    public int pick(GameState s) {
        Int2IntMap m = book.get(Zobrist.compute(s));
        if (m == null || m.size() == 0) return 0;
        int total = m.totalWeight;
        int r = (total <= 1) ? 1 : 1 + rng.nextInt(total);
        int acc = 0;
        for (var e : m.entries) {
            acc += e.weight;
            if (acc >= r) return e.move;
        }
        return m.entries.getFirst().move;
    }


    private void ingestLine(String line) {
        if (line == null) return;
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return;

        List<String> toks = new ArrayList<>();
        for (String t : line.split("\\s+")) {
            if (t.isEmpty()) continue;

            t = t.replaceAll("[\\u200B\\u00A0]", "");

            if (t.matches("\\d+\\.\\.\\..*")) t = t.substring(t.indexOf("...")+3);
            else if (t.matches("\\d+\\..*")) t = t.substring(t.indexOf('.')+1);
            t = t.replace("+","").replace("#",""); // ignore check/mate markers
            if (!t.isEmpty()) toks.add(t);
        }
        if (toks.isEmpty()) return;

        GameState s = new GameState();

        for (String sanLike : toks) {
            int move = resolveTokenToMove(s, sanLike);
            if (move == 0) break;
            long key = Zobrist.compute(s);
            add(key, move, 1);
            s.make(move);
        }
    }


    private int resolveTokenToMove(GameState s, String tok) {
        if (tok == null || tok.isEmpty()) return 0;
        tok = tok.trim();

        if (tok.matches("^[a-h][1-8][a-h][1-8][nbrqNBRQ]?$")) {
            final int[] found = {0};
            String finalTok = tok;
            MoveGen.generate(s, s.whiteToMove(), (int m) -> {
                String u = toUci(m);
                if (u.equalsIgnoreCase(finalTok)) found[0] = m;
            });
            return found[0];
        }

        final String wanted = normalizeSAN(tok);
        final int[] match = {0};
        MoveGen.generate(s, s.whiteToMove(), m -> {
            if (match[0] != 0) return;
            String san = toSimpleSAN(s, m);
            if (wanted.equals(san)) match[0] = m;
        });
        return match[0];
    }

    private static String toUci(int m) {
        String s = sq(GameState.from(m)) + sq(GameState.to(m));
        int pk = GameState.promoKind(m);
        if (pk >= 0) s += "nbrq".charAt(pk);
        return s;
    }
    private static String sq(int i){ return ""+(char)('a'+(i&7))+(char)('1'+(i>>>3)); }


    private static String normalizeSAN(String t) {
        t = t.replace("x","x")
                .replace("O-O-O","O-O-O").replace("0-0-0","O-O-O")
                .replace("O-O","O-O").replace("0-0","O-O")
                .replace("+","").replace("#","")
                .replace("e.p.","").trim();

        if (t.matches("^[a-h][a-h][1-8]$")) t = (""+t.charAt(0)+'x'+t.substring(1));

        return t;
    }

    private static String toSimpleSAN(GameState s, int m){
        if (GameState.isCastle(m)) {
            int f = GameState.from(m)&7, t = GameState.to(m)&7;
            return (t>f) ? "O-O" : "O-O-O";
        }
        int from = GameState.from(m), to = GameState.to(m);
        int kind = GameState.moverKind(m);
        boolean capture = GameState.isCapture(m);

        StringBuilder sb = new StringBuilder();
        if (kind>0) sb.append(" NBRQK".charAt(kind));


        if (kind>0) {
            boolean needFile=false, needRank=false;
            final int pieceKind = kind;
            final int toSq = to;
            final int fromSq = from;
            final boolean stm = s.whiteToMove();
            final boolean[] same = {false};
            final boolean[] sameFile = {false};
            final boolean[] sameRank = {false};

            MoveGen.generate(s, stm, (int mm)->{
                if (mm==m) return;
                if (GameState.moverKind(mm)!=pieceKind) return;
                if (GameState.to(mm)!=toSq) return;
                // ensure legal source actually from a different square
                if (GameState.from(mm)==fromSq) return;
                same[0]=true;
                if ((GameState.from(mm)&7)==(fromSq&7)) sameFile[0]=true;
                if ((GameState.from(mm)>>3)==(fromSq>>3)) sameRank[0]=true;
            });
            if (same[0]) {

                if (!sameFile[0]) needFile=true;
                else if (!sameRank[0]) needRank=true;
                else { needFile=true; needRank=true; }
            }
            if (needFile) sb.append((char)('a'+(from&7)));
            if (needRank) sb.append((char)('1'+(from>>>3)));
        } else {

            if (capture) sb.append((char)('a'+(from&7)));
        }

        if (capture) sb.append('x');
        sb.append(sq(to));

        int promo = GameState.promoKind(m);
        if (promo >= 0) sb.append("=NBRQ".charAt(promo+1));

        return sb.toString();
    }

    private void add(long key, int move, int w){
        Int2IntMap set = book.computeIfAbsent(key, k -> new Int2IntMap());
        set.add(move, w);
    }


    private static final class Int2IntMap {
        final ArrayList<Entry> entries = new ArrayList<>(4);
        int totalWeight = 0;
        void add(int move, int w){
            for (Entry e : entries) if (e.move==move){ e.weight+=w; totalWeight+=w; return; }
            entries.add(new Entry(move, w)); totalWeight += w;
        }
        int size(){ return entries.size(); }
        static final class Entry { final int move; int weight; Entry(int m,int w){ move=m; weight=w; } }
    }
}
