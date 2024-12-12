package com.example.ai_aac;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.Button;

import java.io.File;
import java.util.HashMap;

public class PictogramManager {
    private static final PictogramManager instance = new PictogramManager();
    private HashMap<String, Pictogram> pictogramMap = new HashMap<>();

    // Private constructor to prevent instantiation
    private PictogramManager() {}

    // Static method to get the single instance of this class
    public static PictogramManager getInstance() {
        return instance;
    }

    // Getter for pictogramMap
    public HashMap<String, Pictogram> getPictogramMap() {
        return pictogramMap;
    }

    // Setter for pictogramMap
    public void setPictogramMap(HashMap<String, Pictogram> map) {
        this.pictogramMap = map;
    }

    public Button createPictogramButton(Context context, Pictogram pictogram) {
        Button button = new Button(context);
        button.setText(pictogram.getLabel());
        button.setTextColor(context.getResources().getColor(R.color.black));

        // Load and set the icon from assets, if available
        String imageFileName = pictogram.getImageFile();
        if (imageFileName != null) {
            try {
                // Load the image from internal storage
                File imageFile = new File(context.getFilesDir(), imageFileName);
                if (imageFile.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                    if (bitmap != null) {
                        Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
                        button.setBackgroundResource(R.drawable.button_background);
                        button.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
                    } else {
                        Log.e(TAG, "Failed to decode bitmap from file: " + imageFile.getAbsolutePath());
                    }
                } else {
                    Log.e(TAG, "Image file not found in internal storage: " + imageFile.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading image from internal storage: " + imageFileName, e);
            }
        }
        return button;
    }
}



