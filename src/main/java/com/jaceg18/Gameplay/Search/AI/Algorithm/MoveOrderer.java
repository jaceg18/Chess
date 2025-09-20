package com.jaceg18.Gameplay.Search.AI.Algorithm;

import com.jaceg18.Gameplay.Utility.GameState;

import java.util.List;

public interface MoveOrderer {
    void onCutoff(GameState s, int ply, int move, int depth);
    void order(GameState s, int ply, int hashMove, boolean inCheck, List<Integer> moves, int depth);
}