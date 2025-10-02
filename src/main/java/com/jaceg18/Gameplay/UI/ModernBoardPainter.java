package com.jaceg18.Gameplay.UI;

import com.jaceg18.Gameplay.Utility.Attacks;
import com.jaceg18.Gameplay.Utility.GameState;

import java.awt.*;

public class ModernBoardPainter {
    private int hoverSq = -1;
    private int lastFromSq = -1;
    private int lastToSq = -1;
    private final ChessBoardPanel host;
    
    public ModernBoardPainter(ChessBoardPanel host) {
        this.host = host;
    }
    
    public void drawBoard(Graphics2D g, int squareSize, boolean whiteBottom) {
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                boolean isLight = ((rank + file) & 1) == 0;
                
                int x = file * squareSize;
                int y = rank * squareSize;
                Color baseColor = isLight ? ModernTheme.LIGHT_SQ : ModernTheme.DARK_SQ;
                Color edgeColor = isLight ? ModernTheme.LIGHT_EDGE : ModernTheme.DARK_EDGE;
                
                Paint gradient = ModernTheme.createGradient(x, y, squareSize, squareSize, baseColor, edgeColor);
                g.setPaint(gradient);
                g.fillRect(x, y, squareSize, squareSize);
                if (!isLight) {
                    g.setColor(ModernTheme.withAlpha(Color.BLACK, 15));
                    g.fillRect(x, y, squareSize, 2);
                    g.fillRect(x, y, 2, squareSize);
                }
            }
        }
        g.setStroke(ModernTheme.THICK_STROKE);
        g.setColor(ModernTheme.BOARD_BORDER);
        g.drawRect(0, 0, squareSize * 8, squareSize * 8);
        g.setStroke(ModernTheme.THIN_STROKE);
        g.setColor(ModernTheme.withAlpha(ModernTheme.BOARD_BORDER, 60));
        g.drawRect(-1, -1, squareSize * 8 + 2, squareSize * 8 + 2);
    }
    
    public void drawCoordinates(Graphics2D g, int squareSize, boolean whiteBottom) {
        g.setFont(ModernTheme.getCoordFont(squareSize));
        g.setColor(ModernTheme.COORD);
        FontMetrics fm = g.getFontMetrics();
        
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                int boardRank = whiteBottom ? (7 - rank) : rank;
                int boardFile = whiteBottom ? file : (7 - file);
                
                String fileName = "abcdefgh".substring(boardFile, boardFile + 1);
                String rankName = String.valueOf(boardRank + 1);
                
                int x = file * squareSize;
                int y = rank * squareSize;
                if (rank == 7) {
                    g.drawString(fileName, x + 6, y + squareSize - 6);
                }
                if (file == 0) {
                    g.drawString(rankName, x + 6, y + fm.getAscent() + 4);
                }
            }
        }
    }
    
    public void drawCheck(Graphics2D g, int squareSize, boolean whiteBottom, GameState state) {
        boolean whiteInCheck = Attacks.isInCheck(state, true);
        boolean blackInCheck = Attacks.isInCheck(state, false);
        
        if (!whiteInCheck && !blackInCheck) return;
        
        long kingMask = whiteInCheck ? state.king(true) : state.king(false);
        if (kingMask == 0) return;
        
        int kingSq = Long.numberOfTrailingZeros(kingMask);
        int[] rc = host.viewRC(kingSq);
        
        int x = rc[1] * squareSize;
        int y = rc[0] * squareSize;
        long time = System.currentTimeMillis();
        int alpha = (int) (100 + 40 * Math.sin(time * 0.008));
        
        g.setColor(ModernTheme.withAlpha(ModernTheme.CHECK, alpha));
        g.fillRoundRect(x + 4, y + 4, squareSize - 8, squareSize - 8, 8, 8);
        g.setStroke(ModernTheme.THICK_STROKE);
        g.setColor(ModernTheme.CHECK);
        g.drawRoundRect(x + 4, y + 4, squareSize - 8, squareSize - 8, 8, 8);
    }
    
    public void drawSelection(Graphics2D g, int squareSize, boolean whiteBottom, SelectionModel selection) {
        if (!selection.hasSelection()) return;
        
        int selectedSq = selection.selected;
        int[] rc = host.viewRC(selectedSq);
        
        int x = rc[1] * squareSize;
        int y = rc[0] * squareSize;
        g.setColor(ModernTheme.SELECT);
        g.fillRoundRect(x + 2, y + 2, squareSize - 4, squareSize - 4, 6, 6);
        g.setStroke(ModernTheme.THICK_STROKE);
        g.setColor(ModernTheme.withAlpha(ModernTheme.SELECT, 200));
        g.drawRoundRect(x + 2, y + 2, squareSize - 4, squareSize - 4, 6, 6);
        drawLegalMoves(g, squareSize, whiteBottom, selection);
    }
    
    private void drawLegalMoves(Graphics2D g, int squareSize, boolean whiteBottom, SelectionModel selection) {
        for (int move : selection.legal) {
            int toSq = GameState.to(move);
            int[] rc = host.viewRC(toSq);
            
            int x = rc[1] * squareSize + squareSize / 2;
            int y = rc[0] * squareSize + squareSize / 2;
            
            boolean isCapture = GameState.isCapture(move);
            int radius = isCapture ? squareSize / 3 : squareSize / 6;
            if (isCapture) {
                g.setStroke(ModernTheme.THICK_STROKE);
                g.setColor(ModernTheme.LEGAL);
                g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
            } else {
                g.setColor(ModernTheme.LEGAL);
                g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            }
        }
    }
    
    public void drawHover(Graphics2D g, int squareSize, boolean whiteBottom) {
        if (hoverSq < 0) return;
        
        int[] rc = host.viewRC(hoverSq);
        int x = rc[1] * squareSize;
        int y = rc[0] * squareSize;
        g.setColor(ModernTheme.HOVER);
        g.fillRoundRect(x + 1, y + 1, squareSize - 2, squareSize - 2, 4, 4);
    }
    
    public void drawLastMove(Graphics2D g, int squareSize, boolean whiteBottom) {
        if (lastFromSq < 0 || lastToSq < 0) return;
        int[] fromRC = host.viewRC(lastFromSq);
        int fromX = fromRC[1] * squareSize;
        int fromY = fromRC[0] * squareSize;
        
        g.setColor(ModernTheme.LAST_MOVE);
        g.fillRoundRect(fromX + 3, fromY + 3, squareSize - 6, squareSize - 6, 6, 6);
        int[] toRC = host.viewRC(lastToSq);
        int toX = toRC[1] * squareSize;
        int toY = toRC[0] * squareSize;
        
        g.setColor(ModernTheme.LAST_MOVE);
        g.fillRoundRect(toX + 3, toY + 3, squareSize - 6, squareSize - 6, 6, 6);
        drawMoveArrow(g, fromX + squareSize/2, fromY + squareSize/2, 
                     toX + squareSize/2, toY + squareSize/2);
    }
    
    private void drawMoveArrow(Graphics2D g, int fromX, int fromY, int toX, int toY) {
        g.setStroke(ModernTheme.THICK_STROKE);
        g.setColor(ModernTheme.withAlpha(ModernTheme.ACCENT, 120));
        double dx = toX - fromX;
        double dy = toY - fromY;
        double length = Math.sqrt(dx * dx + dy * dy);
        
        if (length < 10) return;
        dx /= length;
        dy /= length;
        int startX = (int) (fromX + dx * 20);
        int startY = (int) (fromY + dy * 20);
        int endX = (int) (toX - dx * 20);
        int endY = (int) (toY - dy * 20);
        g.drawLine(startX, startY, endX, endY);
        double arrowLength = 12;
        double arrowAngle = Math.PI / 6;
        
        int arrowX1 = (int) (endX - arrowLength * Math.cos(Math.atan2(dy, dx) - arrowAngle));
        int arrowY1 = (int) (endY - arrowLength * Math.sin(Math.atan2(dy, dx) - arrowAngle));
        int arrowX2 = (int) (endX - arrowLength * Math.cos(Math.atan2(dy, dx) + arrowAngle));
        int arrowY2 = (int) (endY - arrowLength * Math.sin(Math.atan2(dy, dx) + arrowAngle));
        
        g.drawLine(endX, endY, arrowX1, arrowY1);
        g.drawLine(endX, endY, arrowX2, arrowY2);
    }
    
    public void drawPieces(Graphics2D g, int squareSize, boolean whiteBottom, GameState state, 
                          PieceSprites sprites, MoveAnimator animator) {
        for (int square = 0; square < 64; square++) {
            if (animator.isAnimating() && square == animator.draggingFrom) {
                continue;
            }
            
            long mask = 1L << square;
            int piece = -1;
            boolean white = false;
            if ((state.pawns(true) & mask) != 0) { piece = 0; white = true; }
            else if ((state.pawns(false) & mask) != 0) { piece = 0; white = false; }
            else if ((state.knights(true) & mask) != 0) { piece = 1; white = true; }
            else if ((state.knights(false) & mask) != 0) { piece = 1; white = false; }
            else if ((state.bishops(true) & mask) != 0) { piece = 2; white = true; }
            else if ((state.bishops(false) & mask) != 0) { piece = 2; white = false; }
            else if ((state.rooks(true) & mask) != 0) { piece = 3; white = true; }
            else if ((state.rooks(false) & mask) != 0) { piece = 3; white = false; }
            else if ((state.queens(true) & mask) != 0) { piece = 4; white = true; }
            else if ((state.queens(false) & mask) != 0) { piece = 4; white = false; }
            else if ((state.king(true) & mask) != 0) { piece = 5; white = true; }
            else if ((state.king(false) & mask) != 0) { piece = 5; white = false; }
            
            if (piece >= 0) {
                int[] rc = host.viewRC(square);
                int x = rc[1] * squareSize;
                int y = rc[0] * squareSize;
                g.setColor(ModernTheme.withAlpha(Color.BLACK, 30));
                g.fillOval(x + squareSize/8, y + squareSize - squareSize/8, 
                          squareSize - squareSize/4, squareSize/8);
                Image pieceImage = sprites.imageAt(state, square, squareSize);
                if (pieceImage != null) {
                    g.drawImage(pieceImage, x, y, null);
                }
            }
        }
    }
    
    public void setHoverSquare(int square) {
        this.hoverSq = square;
    }
    
    public void setLastMove(int fromSq, int toSq) {
        this.lastFromSq = fromSq;
        this.lastToSq = toSq;
    }
    
    public void clearLastMove() {
        this.lastFromSq = -1;
        this.lastToSq = -1;
    }
}
