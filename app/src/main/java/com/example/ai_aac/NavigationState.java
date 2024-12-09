package com.example.ai_aac;

import java.util.List;

public class NavigationState {
    List<String> buttonSequence;
    Pictogram currentPictogram;
    List<Pictogram> pictogramsToDisplay;

    NavigationState(List<String> buttonSequence, Pictogram currentPictogram, List<Pictogram> pictogramsToDisplay) {
        this.buttonSequence = buttonSequence;
        this.currentPictogram = currentPictogram;
        this.pictogramsToDisplay = pictogramsToDisplay;
    }

    @Override
    public String toString() {
        return "NavigationState{" +
                "buttonSequence=" + buttonSequence +
                ", currentPictogram=" + (currentPictogram != null ? currentPictogram.getLabel() : "null") +
                ", pictogramsToDisplay=" + pictogramsToDisplay +
                '}';
    }
}


