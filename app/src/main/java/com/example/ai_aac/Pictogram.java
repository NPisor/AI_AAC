package com.example.ai_aac;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Pictogram {
    private String label;
    private String imageFile;
    private String parentCategory;
    private List<Pictogram> children;
    private List<String> suggestions;
    private List<String> keywords;
    private String defaultResponse;

    public Pictogram(String label, String imageFile, String parentCategory, List<String> suggestions, String defaultResponse, List<String> keywords) {
        this.label = label;
        this.imageFile = imageFile;
        this.parentCategory = parentCategory;
        this.suggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>();
        this.children = new ArrayList<>();
        this.defaultResponse = defaultResponse;
        this.keywords = keywords;
    }

    public void addChild(Pictogram child) {
        children.add(child);
        Log.d("Pictogram", "Added child " + child.getLabel() + " to " + this.label);
    }

    public List<Pictogram> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public String getLabel() {
        return label;
    }

    public String getImageFile() {
        return imageFile;
    }

    public String getParentCategory() {
        return parentCategory;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public String getDefaultResponse() {
        return defaultResponse;
    }

    public List<String> getKeywords() {
        return keywords;
    }
}
