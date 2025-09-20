package com.jaceg18.Gameplay.Search.AI.Algorithm;

public interface TranspositionTable {
    int EXACT = 0, LOWER = 1, UPPER = 2;

    final class Entry {
        public long key;
        public int move, score, depth, flag, age;
    }
    final class ProbeOut { public int move; public int score; public int depth; public int flag; }

    boolean probe(long key, int depth, int alpha, int beta, ProbeOut out);
    void store(long key, int depth, int flag, int score, int move, int age);
    void bumpAge(); int age();
}