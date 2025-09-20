package com.jaceg18.Gameplay.UI;


import java.util.List;

class SelectionModel {
    int selected = -1; List<Integer> legal = List.of();
    boolean hasSelection() { return selected != -1; }
    void select(int sq) { selected = sq; }
    void clear() { selected = -1; legal = List.of(); }
}