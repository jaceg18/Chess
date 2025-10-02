package com.jaceg18.Gameplay.UI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ModernButton extends JButton {
    public enum ButtonStyle {
        PRIMARY, SECONDARY, ACCENT, SUCCESS, WARNING, DANGER
    }
    
    private ButtonStyle style = ButtonStyle.PRIMARY;
    private boolean isHovered = false;
    private boolean isPressed = false;
    private Color baseColor;
    private Color hoverColor;
    private Color pressedColor;
    private Color textColor;


    public ModernButton(String text) {
        this(text, ButtonStyle.PRIMARY);
    }
    
    public ModernButton(String text, ButtonStyle style) {
        super(text);
        this.style = style;
        initializeButton();
        setupColors();
        setupMouseListeners();
    }
    
    private void initializeButton() {
        setFont(ModernTheme.getUIFontBold(13));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(100, 36));
    }
    
    private void setupColors() {
        switch (style) {
            case PRIMARY -> {
                baseColor = ModernTheme.PRIMARY;
                hoverColor = ModernTheme.PRIMARY_DARK;
                pressedColor = new Color(40, 53, 147);
                textColor = ModernTheme.ON_PRIMARY;
            }
            case SECONDARY -> {
                baseColor = ModernTheme.CONSOLE_BORDER;
                hoverColor = new Color(189, 189, 189);
                pressedColor = new Color(158, 158, 158);
                textColor = ModernTheme.TEXT_PRIMARY;
            }
            case ACCENT -> {
                baseColor = ModernTheme.ACCENT;
                hoverColor = new Color(230, 74, 25);
                pressedColor = new Color(191, 54, 12);
                textColor = Color.WHITE;
            }
            case SUCCESS -> {
                baseColor = ModernTheme.SUCCESS;
                hoverColor = new Color(67, 160, 71);
                pressedColor = new Color(46, 125, 50);
                textColor = Color.WHITE;
            }
            case WARNING -> {
                baseColor = ModernTheme.WARNING;
                hoverColor = new Color(251, 140, 0);
                pressedColor = new Color(230, 126, 34);
                textColor = Color.WHITE;
            }
            case DANGER -> {
                baseColor = ModernTheme.ERROR;
                hoverColor = new Color(229, 57, 53);
                pressedColor = new Color(198, 40, 40);
                textColor = Color.WHITE;
            }
        }
        setForeground(textColor);
    }
    
    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                isPressed = true;
                repaint();
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color currentColor = baseColor;
        if (isPressed) {
            currentColor = pressedColor;
        } else if (isHovered) {
            currentColor = hoverColor;
        }
        g2d.setColor(currentColor);
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 
                ModernTheme.CORNER_RADIUS, ModernTheme.CORNER_RADIUS);
        if (isHovered && !isPressed) {
            g2d.setColor(ModernTheme.withAlpha(Color.BLACK, 20));
            g2d.fillRoundRect(2, 2, getWidth(), getHeight(), 
                    ModernTheme.CORNER_RADIUS, ModernTheme.CORNER_RADIUS);
            g2d.setColor(currentColor);
            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 
                    ModernTheme.CORNER_RADIUS, ModernTheme.CORNER_RADIUS);
        }
        
        g2d.dispose();
        super.paintComponent(g);
    }
    
    public void setButtonStyle(ButtonStyle style) {
        this.style = style;
        setupColors();
        repaint();
    }
    
    public ButtonStyle getButtonStyle() {
        return style;
    }
}
