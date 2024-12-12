package com.example.ai_aac;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CaregiverMessage {
    private List<Pictogram> pictograms;
    private String generatedResponse;
    private String timestamp;

    public CaregiverMessage(List<Pictogram> pictograms, String generatedResponse, String timestamp) {
        this.pictograms = pictograms;
        this.generatedResponse = generatedResponse;
        this.timestamp = timestamp;
    }

    public List<Pictogram> getPictograms() {
        return pictograms;
    }

    public String getGeneratedResponse() {
        return generatedResponse;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getFormattedTimestamp() {
        try {
            long millis = Long.parseLong(timestamp);
            Date date = new Date(millis);
            SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss", Locale.getDefault());
            return formatter.format(date);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return "Invalid timestamp";
        }
    }
}

