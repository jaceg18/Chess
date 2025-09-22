package com.jaceg18.Gameplay.TB;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

public final class TablebaseClient {

    public static final class TbResult {
        public final String bestUci;
        public final Integer dtm;
        public final Integer dtz;
        public final String category;
        public final boolean checkmate;
        public final boolean stalemate;

        TbResult(String bestUci, Integer dtm, Integer dtz, String category, boolean checkmate, boolean stalemate) {
            this.bestUci = bestUci;
            this.dtm = dtm;
            this.dtz = dtz;
            this.category = category;
            this.checkmate = checkmate;
            this.stalemate = stalemate;
        }

        public boolean hasMove() { return bestUci != null && !bestUci.isEmpty(); }
        public boolean isMateAvailable() { return dtm != null && dtm != 0; }
    }

    private static final String ENDPOINT = "https://tablebase.lichess.ovh/standard?fen=";
    private static final int TIMEOUT_MS = 1200;
    private static final ConcurrentHashMap<Long, TbResult> CACHE = new ConcurrentHashMap<>();

    private TablebaseClient() {}

    public static TbResult probe(long zobrist, String fen) {
        TbResult cached = CACHE.get(zobrist);
        if (cached != null) return cached;
        String fenParam = fen.replace(' ', '_');
        String urlStr = ENDPOINT + fenParam;

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) return null;

            StringBuilder sb = new StringBuilder(512);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line; while ((line = br.readLine()) != null) sb.append(line);
            }
            String json = sb.toString();

            TbResult out = parse(json);
            if (out != null) CACHE.putIfAbsent(zobrist, out);
            return out;
        } catch (Exception ignore) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static TbResult parse(String json) {
        String category = strField(json, "\"category\":\"");
        Integer dtm = intField(json, "\"dtm\":");
        Integer dtz = intField(json, "\"dtz\":");
        String bestUci = null;
        int mIdx = json.indexOf("\"moves\"");
        if (mIdx >= 0) {
            int uciIdx = json.indexOf("\"uci\":\"", mIdx);
            if (uciIdx >= 0) bestUci = untilQuote(json, uciIdx + 7);
        }

        boolean checkmate = boolField(json, "\"checkmate\":");
        boolean stalemate = boolField(json, "\"stalemate\":");
        if (category == null && bestUci == null && dtm == null && dtz == null) return null;

        return new TbResult(bestUci, dtm, dtz, category == null ? "unknown" : category, checkmate, stalemate);
    }

    private static String strField(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return null;
        return untilQuote(json, i + key.length());
    }

    private static String untilQuote(String s, int from) {
        int end = s.indexOf('"', from);
        return end < 0 ? null : s.substring(from, end);
    }

    private static Integer intField(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return null;
        int j = i + key.length();
        int k = j;
        while (k < json.length()) {
            char c = json.charAt(k);
            if ((c >= '0' && c <= '9') || c == '-') { k++; continue; }
            if (c == 'n') {
                return null;
            }
            break;
        }
        try {
            return Integer.parseInt(json.substring(j, k));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean boolField(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return false;
        int j = i + key.length();
        return json.regionMatches(true, j, "true", 0, 4);
    }
}
