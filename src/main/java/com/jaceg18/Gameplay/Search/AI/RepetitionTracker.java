package com.jaceg18.Gameplay.Search.AI;

public final class RepetitionTracker {
    private final long[] repStack = new long[SearchConstants.MAX_PLY];
    private int floorPly = 0;

    public long get(int ply) {
        return repStack[ply];
    }

    public void set(int ply, long key) {
        repStack[ply] = key;
    }

    public void clearFrom(int ply) {
        for (int i = ply; i < repStack.length; i++) repStack[i] = 0;
        if (ply < floorPly) floorPly = ply;
    }

    public void setFloor(int ply) { floorPly = ply; }

    public boolean isThreefold(int ply) {
        long key = repStack[ply];
        int freq = 0;
        for (int i = floorPly; i <= ply; i++) if (repStack[i] == key) freq++;
        return freq >= 3;
    }
}
