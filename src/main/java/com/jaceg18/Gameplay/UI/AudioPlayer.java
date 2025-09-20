package com.jaceg18.Gameplay.UI;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class AudioPlayer {
    public static void playSound(boolean capture){
        try {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream( (!capture ) ?
                    new File("src/main/resources/move-self.wav") :
                    new File("src/main/resources/capture.wav"));
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }
}
