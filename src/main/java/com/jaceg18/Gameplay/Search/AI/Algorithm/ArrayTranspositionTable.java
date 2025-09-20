package com.jaceg18.Gameplay.Search.AI.Algorithm;


public final class ArrayTranspositionTable implements TranspositionTable {
    private final Entry[] table;
    private final int mask;
    private int age = 0;

    public ArrayTranspositionTable(int size){
        int cap = 1;
        while (cap < size) cap <<= 1;
        table = new Entry[cap];
        for (int i=0;i<cap;i++) table[i] = new Entry();
        mask = cap - 1;
    }

    private int index(long key){ return (int)key & mask; }

    @Override public boolean probe(long key, int depth, int alpha, int beta, ProbeOut out){
        Entry e = table[index(key)];
        if (e.key != key){ out.move = 0; return false; }
        out.move = e.move;
        if (e.depth < depth) return false;
        switch (e.flag){
            case EXACT: out.score = e.score; return true;
            case LOWER: if (e.score >= beta)  { out.score = e.score; return true; } break;
            case UPPER: if (e.score <= alpha) { out.score = e.score; return true; } break;
        }
        return false;
    }

    @Override public void store(long key, int depth, int flag, int score, int move, int ageNow){
        int idx = index(key);
        Entry e = table[idx];
        if (e.key == 0L || e.key == key || depth > e.depth || ageNow > e.age){
            e.key = key; e.depth = depth; e.flag = flag; e.score = score;
            if (move != 0) e.move = move;
            e.age = ageNow;
        }
    }

    @Override public void bumpAge(){ age++; }
    @Override public int age(){ return age; }
}
