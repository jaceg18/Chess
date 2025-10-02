package com.jaceg18.Gameplay.UI;

import com.jaceg18.Gameplay.Utility.GameState;

public interface ConsoleInterface {
    void logInfo(String message);
    void logWarn(String message);
    void logMove(GameState state, int move);
    void setEvalCp(int cp);
    void setTbInfo(String category, Integer dtm, Integer dtz, String bestUci);
    void clearTbInfo();

}
