package com.jaceg18;


import com.jaceg18.Gameplay.Opening.OpeningBook;
import com.jaceg18.Gameplay.Search.AI.AiFactory;
import com.jaceg18.Gameplay.Search.AI.SearchConstants;
import com.jaceg18.Gameplay.Search.SearchEngine;
import com.jaceg18.Gameplay.UI.ChessBoardPanel;
import com.jaceg18.Gameplay.UI.EngineAdapter;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Chess");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            ChessBoardPanel board = new ChessBoardPanel();


            SearchEngine engine = AiFactory.balanced();
            engine.setMaxDepth(SearchConstants.MAX_DEPTH);
            try {
                OpeningBook ob = OpeningBook.load("src/main/resources/openings.txt");
                engine.setOpeningBook(ob);
            } catch (IOException e) {
                ChessBoardPanel.console.logWarn("Opening book not found. Using search only.");
            }


            board.setAi(new EngineAdapter(engine));

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, board, ChessBoardPanel.console);
            split.setBorder(null);
            split.setContinuousLayout(true);
            split.setOneTouchExpandable(true);
            split.setResizeWeight(0.80);

            frame.getContentPane().add(split, BorderLayout.CENTER);
            frame.pack();


            int consolePref = ChessBoardPanel.console.getPreferredSize().width;
            split.setDividerLocation(Math.max(board.getPreferredSize().width, ChessBoardPanel.BOARD_PX));

            split.setDividerLocation(frame.getWidth() - consolePref - split.getDividerSize());

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
