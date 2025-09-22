package com.jaceg18.Gameplay.Search.AI;
public final class RepetitionTracker {
    private final long[] repStack = new long[SearchConstants.MAX_PLY + 64]; // a little headroom
    private int floorPly = 0;

    public long get(int ply) { return repStack[ply]; }
    public void set(int ply, long key) { repStack[ply] = key; }

    public void clearFrom(int ply) {
        for (int i = ply; i < repStack.length; i++) repStack[i] = 0L;
        if (ply < floorPly) floorPly = ply;
    }

    public void setFloor(int ply) { floorPly = ply; }
    public int floor() { return floorPly; }

    public boolean isThreefold(int ply) {
        long key = repStack[ply];
        if (key == 0L) return false;
        int freq = 0;
        for (int i = floorPly; i <= ply; i++) {
            if (repStack[i] == key) {
                freq++;
                System.out.printf("REP HIT at ply=%d freq=%d%n", i, freq);
            }
        }
        return freq >= 3;
    }

}