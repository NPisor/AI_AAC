package com.example.ai_aac;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AddPictogramActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private View currentCapturedImageButton;
    private EditText itemNameInput, parentCategoryInput, keywordsInput;

    private TextView cardLabel;
    private Bitmap capturedImage;
    private File photoFile;

    private static final String TAG = "AddPictogramActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_pictogram);

        itemNameInput = findViewById(R.id.item_name_input);
        parentCategoryInput = findViewById(R.id.parent_category_input);
        keywordsInput = findViewById(R.id.keyword_input);
        Button captureImageButton = findViewById(R.id.capture_image_button);
        Button saveButton = findViewById(R.id.save_pictogram_button);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Launch Camera to Capture Image
        captureImageButton.setOnClickListener(v -> {
            checkPermissions(); // Ensure permissions are checked
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                photoFile = createImageFile(); // Attempt to create the file
                if (photoFile != null) {
                    Log.d(TAG, "photoFile created successfully: " + photoFile.getAbsolutePath());
                    Uri photoURI = FileProvider.getUriForFile(this, "com.example.ai_aac.fileprovider", photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    Log.d(TAG, "photoURI: " + photoURI.toString());
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                } else {
                    Log.e(TAG, "photoFile is null. Unable to proceed.");
                    Toast.makeText(this, "Failed to create file for image capture.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "No camera activity found to handle the intent.");
                Toast.makeText(this, "No camera app available.", Toast.LENGTH_SHORT).show();
            }
        });


        // Save Pictogram Logic
        saveButton.setOnClickListener(v -> {
            String itemName = itemNameInput.getText().toString().trim();
            String parentCategory = parentCategoryInput.getText().toString().trim();
            String[] keywords = keywordsInput.getText().toString().split("\\s*,\\s*");

            if (itemName.isEmpty() || parentCategory.isEmpty() || capturedImage == null) {
                Toast.makeText(this, "Please fill in all fields and capture an image", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save the image and add the pictogram to JSON
            try {
                String imageFileName = saveImageToInternalStorage(itemName);
                addPictogramToJSON(itemName, parentCategory, imageFileName, keywords);
            } catch (IOException e) {
                Log.e(TAG, "Error saving pictogram", e);
                Toast.makeText(this, "Failed to save pictogram", Toast.LENGTH_SHORT).show();
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            if (photoFile != null && photoFile.exists()) {
                Log.d(TAG, "photoFile path: " + photoFile.getAbsolutePath());
                Bitmap fullImage = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                if (fullImage != null) {
                    BackgroundRemovalHelper.removeBackground(fullImage, new BackgroundRemovalHelper.BackgroundRemovalCallback() {
                        @Override
                        public void onSuccess(Bitmap isolatedBitmap) {
                            runOnUiThread(() -> {
                                Bitmap scaledBitmap = Bitmap.createScaledBitmap(isolatedBitmap, 250, 250, true);
                                capturedImage = scaledBitmap;

                                // Replace the existing button with the new one
                                createCapturedImageButton(scaledBitmap);
                            });
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "Error removing background", e);
                            Toast.makeText(AddPictogramActivity.this, "Failed to process the image. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Log.e(TAG, "Failed to decode the image.");
                    Toast.makeText(this, "Failed to process the image. Please try again.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "photoFile is null or does not exist.");
                Toast.makeText(this, "Failed to retrieve the image. Please try again.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e(TAG, "onActivityResult received unexpected resultCode: " + resultCode);
        }
    }



    private File createImageFile() {
        File storageDir = getExternalFilesDir(null); // App-specific external directory
        if (storageDir != null && (storageDir.exists() || storageDir.mkdirs())) {
            File imageFile = new File(storageDir, "captured_image.jpg");
            Log.d(TAG, "Image file created: " + imageFile.getAbsolutePath());
            return imageFile;
        } else {
            Log.e(TAG, "Failed to create storage directory.");
        }
        return null;
    }





    private String saveImageToInternalStorage(String itemName) throws IOException {
        File imageFile = new File(getFilesDir(), itemName + ".png");
        FileOutputStream outputStream = new FileOutputStream(imageFile);
        capturedImage.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        outputStream.flush();
        outputStream.close();
        return imageFile.getName();
    }

    private void addPictogramToJSON(String itemName, String parentCategory, String imageFileName, String[] keywords) {
        try {
            File jsonFile = new File(getFilesDir(), "pictogramKey.json");
            JSONObject jsonObject;

            if (jsonFile.exists()) {
                jsonObject = new JSONObject(readFromFile(jsonFile));
            } else {
                jsonObject = new JSONObject();
            }

            JSONObject newPictogram = new JSONObject();
            newPictogram.put("Image_File", imageFileName);
            newPictogram.put("Parent_Category", parentCategory);
            JSONArray keywordsJsonArray = new JSONArray();
            for (String keyword : keywords) {
                if (!keyword.isEmpty()) { // Avoid adding empty keywords
                    keywordsJsonArray.put(keyword);
                }
            }
            newPictogram.put("Keywords", keywordsJsonArray);
            jsonObject.put(itemName, newPictogram);
            writeToFile(jsonFile, jsonObject.toString());

            Toast.makeText(this, "Pictogram added successfully!", Toast.LENGTH_SHORT).show();
            finish(); // Close the activity
        } catch (Exception e) {
            Log.e(TAG, "Error updating JSON", e);
            Toast.makeText(this, "Failed to add pictogram", Toast.LENGTH_SHORT).show();
        }
    }

    public static String readFromFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
        }
        reader.close();
        return content.toString();
    }


    public static void writeToFile(File file, String content) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(content);
        writer.close();
    }

    private static final int PERMISSION_REQUEST_CODE = 100;

    private void checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // Check for camera permission
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.CAMERA);
        }

        // Check for storage or media permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // Android 9 or below
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        } else { // Android 10-12
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        // Request permissions if needed
        if (!permissionsNeeded.isEmpty()) {
            requestPermissions(permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permissions are required for this functionality.", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save photoFile path
        if (photoFile != null) {
            outState.putString("photoFilePath", photoFile.getAbsolutePath());
        }

        // Save capturedImage
        if (capturedImage != null) {
            outState.putParcelable("capturedImage", capturedImage);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // Restore photoFile path
        String photoFilePath = savedInstanceState.getString("photoFilePath");
        if (photoFilePath != null) {
            photoFile = new File(photoFilePath);
            Log.d(TAG, "photoFile restored: " + photoFile.getAbsolutePath());
        } else {
            Log.d(TAG, "photoFile was not saved.");
        }

        // Restore capturedImage if available
        capturedImage = savedInstanceState.getParcelable("capturedImage");
        if (capturedImage != null) {
            Log.d(TAG, "capturedImage restored.");
            // Recreate the button with the restored image
            createCapturedImageButton(capturedImage);
        } else {
            Log.d(TAG, "No capturedImage found in savedInstanceState.");
        }
    }

    private void createCapturedImageButton(Bitmap image) {
        // Remove the previous button if it exists
        if (currentCapturedImageButton != null) {
            LinearLayout parentLayout = findViewById(R.id.add_pictogram_layout);
            parentLayout.removeView(currentCapturedImageButton);
            currentCapturedImageButton = null;
        }

        // Inflate the custom layout for the button
        View buttonView = getLayoutInflater().inflate(R.layout.icon_button, null);
        buttonView.setBackgroundResource(R.drawable.button_background);

        // Access the ImageView and set the isolated image
        ImageView cardImage = buttonView.findViewById(R.id.card_image);
        if (image != null) {
            cardImage.setImageBitmap(image);
            LinearLayout.LayoutParams imageParams = (LinearLayout.LayoutParams) cardImage.getLayoutParams();
            imageParams.width = (int) (cardImage.getLayoutParams().width * 1.5); // 50% larger
            imageParams.height = (int) (cardImage.getLayoutParams().height * 1.5);
        }

        // Access the TextView and set the initial text
        cardLabel = buttonView.findViewById(R.id.card_label);
        cardLabel.setText(itemNameInput.getText().toString()); // Use the current text from itemNameInput
        cardLabel.setTextColor(getResources().getColor(R.color.black));

        // Add a TextWatcher to update the TextView when the input changes
        itemNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                cardLabel.setText(s); // Update the label with the input text
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action needed
            }
        });

        // Define LayoutParams for the button
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(3, 3, 3, 3); // Optional margins for spacing

        // Add the custom button to the parent layout
        LinearLayout parentLayout = findViewById(R.id.add_pictogram_layout);
        if (parentLayout != null) {
            int index = parentLayout.indexOfChild(findViewById(R.id.capture_image_button)); // Add before capture button
            if (index >= 0) {
                parentLayout.addView(buttonView, index, params);
                Log.d(TAG, "Custom button with image and text added at index: " + index);
            } else {
                Log.e(TAG, "capture_image_button not found in parent layout");
            }
        } else {
            Log.e(TAG, "Parent layout (add_pictogram_layout) not found");
        }

        // Save the reference to the current button
        currentCapturedImageButton = buttonView;
    }

}
