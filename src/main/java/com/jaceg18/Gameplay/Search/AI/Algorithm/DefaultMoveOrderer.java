package com.jaceg18.Gameplay.Search.AI.Algorithm;


import com.jaceg18.Gameplay.Search.AI.SearchConstants;
import com.jaceg18.Gameplay.Utility.GameState;

import java.util.List;

public final class DefaultMoveOrderer implements MoveOrderer {
    private final int[][] killers = new int[SearchConstants.MAX_PLY][2];
    private final int[][] history = new int[2][64*64];

    @Override
    public void onCutoff(GameState s, int ply, int m, int depth){
        if (Util.isQuiet(m)) {

            if (killers[ply][0] != m){
                killers[ply][1] = killers[ply][0];
                killers[ply][0] = m;
            }

            int side = s.whiteToMove()?1:0;
            history[side][Util.histIdx(m)] += depth * depth;
        }
    }


    @Override
    public void order(GameState s, int ply, int hashMove, boolean inCheck, List<Integer> moves, int depth){
        moves.sort((a, b) -> {
            if (a == hashMove && b != hashMove) return -1;
            if (b == hashMove && a != hashMove) return  1;
            boolean ac = GameState.isCapture(a), bc = GameState.isCapture(b);
            if (ac != bc) return ac ? -1 : 1;
            if (!ac){
                int pa = 0, pb = 0;
                if (a == killers[ply][0]) pa += 3_000; else if (a == killers[ply][1]) pa += 2_000;
                if (b == killers[ply][0]) pb += 3_000; else if (b == killers[ply][1]) pb += 2_000;
                int side = s.whiteToMove()?0:1;
                pa += history[side][Util.histIdx(a)];
                pb += history[side][Util.histIdx(b)];
                return Integer.compare(pb, pa);
            }
            return 0;
        });
    }
}