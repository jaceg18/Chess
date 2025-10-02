package com.jaceg18.Gameplay.UI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class OpeningPanel extends JPanel {
    private JLabel openingNameLabel;
    private JLabel openingDescLabel;
    private String currentOpening = "";
    private boolean isVisible = false;
    
    public OpeningPanel() {
        initializeComponents();
        setupLayout();
        setupStyling();
        setupAnimations();
    }
    
    private void initializeComponents() {
        openingNameLabel = new JLabel("No opening detected");
        openingDescLabel = new JLabel("Game in progress");
        
        openingNameLabel.setFont(ModernTheme.getUIFontBold(16));
        openingDescLabel.setFont(ModernTheme.getUIFont(12));
        
        openingNameLabel.setForeground(ModernTheme.TEXT_PRIMARY);
        openingDescLabel.setForeground(ModernTheme.TEXT_SECONDARY);
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout(8, 4));
        setBorder(new EmptyBorder(12, 16, 12, 16));
        
        JPanel textPanel = new JPanel(new BorderLayout(4, 2));
        textPanel.setOpaque(false);
        textPanel.add(openingNameLabel, BorderLayout.NORTH);
        textPanel.add(openingDescLabel, BorderLayout.CENTER);
        JLabel iconLabel = new JLabel("📚");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        
        add(iconLabel, BorderLayout.WEST);
        add(textPanel, BorderLayout.CENTER);
        JButton closeButton = createCloseButton();
        add(closeButton, BorderLayout.EAST);
    }
    
    private JButton createCloseButton() {
        JButton closeBtn = new JButton("×");
        closeBtn.setFont(ModernTheme.getUIFont(16));
        closeBtn.setForeground(ModernTheme.TEXT_SECONDARY);
        closeBtn.setBackground(new Color(0, 0, 0, 0));
        closeBtn.setBorder(null);
        closeBtn.setFocusPainted(false);
        closeBtn.setPreferredSize(new Dimension(24, 24));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        closeBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeBtn.setForeground(ModernTheme.ERROR);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                closeBtn.setForeground(ModernTheme.TEXT_SECONDARY);
            }
        });
        
        closeBtn.addActionListener(e -> hidePanel());
        return closeBtn;
    }
    
    private void setupStyling() {
        setBackground(ModernTheme.SURFACE);
        setPreferredSize(new Dimension(0, 60));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
    }
    
    private void setupAnimations() {
        setVisible(false);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Paint gradient = ModernTheme.createGradient(0, 0, getWidth(), getHeight(), 
                ModernTheme.SURFACE, ModernTheme.withAlpha(ModernTheme.PRIMARY, 5));
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.setColor(ModernTheme.CONSOLE_BORDER);
        g2d.fillRect(0, getHeight() - 1, getWidth(), 1);
        
        g2d.dispose();
    }
    
    public void displayOpening(String openingName) {
        if (openingName == null || openingName.trim().isEmpty()) {
            return;
        }
        
        currentOpening = openingName;
        openingNameLabel.setText(openingName);
        String description = getOpeningDescription(openingName);
        openingDescLabel.setText(description);
        
        showPanel();
    }
    
    private String getOpeningDescription(String openingName) {
        String lower = openingName.toLowerCase();
        if (lower.contains("italian")) return "Classical attacking opening";
        if (lower.contains("ruy lopez") || lower.contains("spanish")) return "One of the oldest chess openings";
        if (lower.contains("sicilian")) return "Sharp, counterattacking defense";
        if (lower.contains("french")) return "Solid, positional defense";
        if (lower.contains("caro-kann")) return "Reliable, strategic defense";
        if (lower.contains("queen's gambit")) return "Classical queen's pawn opening";
        if (lower.contains("king's indian")) return "Dynamic, hypermodern defense";
        if (lower.contains("nimzo-indian")) return "Positional, piece-focused defense";
        if (lower.contains("english")) return "Flexible flank opening";
        if (lower.contains("alekhine")) return "Provocative knight defense";
        return "Opening in progress";
    }
    
    private void showPanel() {
        if (!isVisible) {
            setVisible(true);
            isVisible = true;
            Timer timer = new Timer(10, null);
            final int[] alpha = {0};
            timer.addActionListener(e -> {
                alpha[0] += 15;
                if (alpha[0] >= 255) {
                    alpha[0] = 255;
                    timer.stop();
                }
                repaint();
            });
            timer.start();
        }
    }
    
    private void hidePanel() {
        if (isVisible) {
            setVisible(false);
            isVisible = false;
        }
    }
    
    public void clearOpening() {
        openingNameLabel.setText("No opening detected");
        openingDescLabel.setText("Game in progress");
        hidePanel();
    }
    
    public boolean hasOpening() {
        return isVisible && !currentOpening.isEmpty();
    }
}
