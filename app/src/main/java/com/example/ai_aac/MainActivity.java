package com.example.ai_aac;

import static com.example.ai_aac.AddPictogramActivity.readFromFile;
import static com.example.ai_aac.AddPictogramActivity.writeToFile;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import ai.onnxruntime.genai.GenAIException;
import ai.onnxruntime.genai.Generator;
import ai.onnxruntime.genai.GeneratorParams;
import ai.onnxruntime.genai.Sequences;
import ai.onnxruntime.genai.TokenizerStream;
import ai.onnxruntime.genai.Model;
import ai.onnxruntime.genai.Tokenizer;

public class MainActivity extends AppCompatActivity implements Consumer<String> {

    private Model model;
    private Tokenizer tokenizer;
    private TextView generatedTV;
    private TextView progressText;
    private GridLayout gridLayout;
    private static final String TAG = "MainActivity";

    private TextToSpeech textToSpeech;
    private Stack<NavigationState> navigationStack = new Stack<>();
    private int maxLength = 200;
    private float lengthPenalty = 4.0f;
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private TokenizerStream currentStream;
    private List<String> buttonSequence = new ArrayList<>();
    LinearLayout sequenceLayout;
    private Map<String, Pictogram> pictogramMap;

    private Set<String> addedPictograms = new HashSet<>();

    private DatabaseHelper dbHelper;

    private SocketServerHelper socketHelper;

    private String lastRootButtonPressed = null;
    private boolean isGeneratingResponse = false;

    private static final String CACHE_FILE_NAME = "responseCache.json";

    private Map<String, String> responseCache = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.aac_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        socketHelper = new SocketServerHelper(new SocketServerHelper.ConnectionCallback() {
            @Override
            public void onMessageReceived(String message) {
                Log.d(TAG, "Received message: " + message);
                // Handle received messages if necessary
            }

            @Override
            public void onClientConnected(String clientAddress) {
                Log.d(TAG, "Client connected: " + clientAddress);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Caregiver connected!", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Socket error: " + errorMessage);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Socket error: " + errorMessage, Toast.LENGTH_SHORT).show());
            }
        });
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setPitch(1.2f); // Higher pitch
                textToSpeech.setSpeechRate(0.9f); // Slower speech

            }
            if (status != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(getResources().getConfiguration().locale);
            }
        });
        toolbar.setNavigationIcon(R.drawable.hamburgermenu); // Replace with your hamburger icon

        toolbar.setNavigationOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(this, v);
            popupMenu.inflate(R.menu.navigation_menu); // Ensure you have the correct menu file

            // Set up click listener for menu items
            popupMenu.setOnMenuItemClickListener(item -> {
                if(item.getItemId() == R.id.nav_add_pictogram) {
                    // Launch the AddPictogramActivity
                    Intent intent = new Intent(this, AddPictogramActivity.class);
                    startActivityForResult(intent, 1);
                    return true;
                } else if(item.getItemId() == R.id.nav_clear_json) {
                    // Clear the JSON file and reset the UI
                    deleteInternalPictogramKey();
                    Toast.makeText(this, "JSON and cache cleared!", Toast.LENGTH_SHORT).show();
                    resetGrid();
                    loadPictogramsFromJSON();
                    return true;
                } else if (item.getItemId() == R.id.nav_caregiver_mode) {
                    showIpInputDialog();
                    return true;
                } else if (item.getItemId() == R.id.nav_start_server) {
                    startServer();
                    return true;
                } else if (item.getItemId() == R.id.nav_server_credentials) {
                    showPairingPopup();
                    return true;
                }
                return false;
            });

            // Show the popup menu
            popupMenu.show();
        });



        gridLayout = findViewById(R.id.grid_layout);
        generatedTV = findViewById(R.id.aiMessage);
        sequenceLayout = findViewById(R.id.sequence_layout);
        progressText = findViewById(R.id.progressText);
        //deleteInternalPictogramKey();
        dbHelper = new DatabaseHelper(this, "messages.db", null, 1);
        copyStockImagesToInternalStorage();
        copyPictogramKeyToInternalStorage();
        loadPictogramsFromJSON();
        loadCacheFromFile();
        setupBackButton();

        try {
            downloadModels(getApplicationContext());
        } catch (GenAIException e) {
            Log.e(TAG, "Model download error: ", e);
        }
    }

    private void loadPictogramsFromJSON() {
        pictogramMap = new HashMap<>();
        try {
            JSONObject pictogramKey;
            File internalJsonFile = new File(getFilesDir(), "pictogramKey.json");

            // Load JSON from internal storage or assets
            if (!internalJsonFile.exists()) {
                pictogramKey = JSONHelper.loadJSONFromAsset(this, "AAC_Symbols/pictogramKey.json");

                // Copy stock JSON and associated images to internal storage
                writeToFile(internalJsonFile, pictogramKey.toString());
                copyStockImagesToInternalStorage();
            } else {
                pictogramKey = JSONHelper.loadJSONFromFile(internalJsonFile.getPath());
            }

            Log.d(TAG, "Loaded JSON file: " + pictogramKey);

            // Parse each JSON object as an independent Pictogram
            if (pictogramKey != null) {
                for (Iterator<String> it = pictogramKey.keys(); it.hasNext(); ) {
                    String key = it.next();
                    Pictogram pictogram = parsePictogram(pictogramKey.getJSONObject(key), key);
                    pictogramMap.put(key, pictogram);
                }
            }

            // Organize Pictograms based on Parent_Category
            for (Pictogram pictogram : pictogramMap.values()) {
                String parentCategory = pictogram.getParentCategory();

                if (parentCategory != null) {
                    Pictogram parentPictogram = pictogramMap.get(parentCategory);

                    if (parentPictogram == null) {
                        // If the parent category doesn't exist, create it
                        parentPictogram = new Pictogram(parentCategory, null, null, null, null, null);
                        pictogramMap.put(parentCategory, parentPictogram);
                        Log.d(TAG, "Created new parent category: " + parentCategory);
                    }

                    // Add the current pictogram to the parent category
                    parentPictogram.addChild(pictogram);
                    Log.d(TAG, "Added " + pictogram.getLabel() + " to parent: " + parentCategory);
                }
            }

            loadInitialPictograms();

        } catch (JSONException | IOException e) {
            Log.e(TAG, "Error loading or parsing JSON file", e);
        }
    }




    private Pictogram parsePictogram(JSONObject jsonObject, String label) throws JSONException {
        String imageFile = jsonObject.optString("Image_File", null);
        String parentCategory = jsonObject.optString("Parent_Category", null);
        String defaultResponse = jsonObject.optString("Default_Response", null);
        List<String> keywords = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (jsonObject.has("Suggestions")) {
            JSONArray suggestionsArray = jsonObject.optJSONArray("Suggestions");
            if (suggestionsArray != null) {
                for (int i = 0; i < suggestionsArray.length(); i++) {
                    suggestions.add(suggestionsArray.getString(i).trim());
                }
            }
        }
        if(jsonObject.has("Keywords")) {
            JSONArray keywordsArray = jsonObject.optJSONArray("Keywords");
            if (keywordsArray != null) {
                for (int i = 0; i < keywordsArray.length(); i++) {
                    keywords.add(keywordsArray.getString(i).trim());
                }
            }
        }

        Pictogram pictogram = new Pictogram(label, imageFile, parentCategory, suggestions, defaultResponse, keywords);

        // Log created pictograms for debugging
        Log.d(TAG, "Created pictogram: " + label + ", Image File: " + imageFile + ", Parent Category: " + parentCategory + ", Suggestions: " + suggestions);
        return pictogram;
    }







    private void generateResponse(String prompt) {
        if (isGeneratingResponse) {
            Log.d(TAG, "Response generation already in progress, ignoring request.");
            return;
        }

        // Check the cache
        if (responseCache.containsKey(prompt)) {
            String cachedResponse = responseCache.get(prompt);
            Log.d(TAG, "Cache hit for prompt: " + prompt);
            displayResponse(cachedResponse);
            narrateResponse(cachedResponse);
            broadcastGeneratedMessage(prompt, cachedResponse);
            return;
        }

        List<String> allKeywords = new ArrayList<>();
        for (String pictogramLabel : buttonSequence) {
            Pictogram pictogram = pictogramMap.get(pictogramLabel);
            if (pictogram != null && pictogram.getKeywords() != null) {
                allKeywords.addAll(pictogram.getKeywords());
            }
        }

        String keywordsString = String.join(", ", allKeywords);

        // Set the processing flag
        isGeneratingResponse = true;
        String formattedInput = "<system>You are a small child. Refer to yourself as 'I.' Using the keywords, translate the message in 12 words or less." +
                "Do not explain or add extra details.<|end|>\n" +
                "<user>" + prompt + "<|end|>\n<assistant|>";

        if (currentStream != null) {
            currentStream.close();
            currentStream = null;
        }

        executorService.execute(() -> {
            try {
                tokenizer.close();
                tokenizer = model.createTokenizer();

                synchronized (this) {
                    currentStream = tokenizer.createStream();
                }

                GeneratorParams params = model.createGeneratorParams();
                params.setSearchOption("length_penalty", lengthPenalty);
                params.setSearchOption("max_length", maxLength);

                Sequences encodedPrompt = tokenizer.encode(formattedInput);
                params.setInput(encodedPrompt);

                Generator generator = new Generator(model, params);

                StringBuilder responseBuilder = new StringBuilder();
                boolean delimiterFound = false;

                while (!generator.isDone() && !delimiterFound) {
                    generator.computeLogits();
                    generator.generateNextToken();
                    int token = generator.getLastTokenInSequence(0);

                    try {
                        String decodedToken = currentStream.decode(token);
                        responseBuilder.append(decodedToken);

                        // Stop further token generation if a delimiter is found
                        if (decodedToken.contains(".") || decodedToken.contains("?") || decodedToken.contains("!")) {
                            delimiterFound = true;
                        }

                    } catch (GenAIException e) {
                        Log.e(TAG, "Token decoding error: ", e);
                    }
                }

                String response = responseBuilder.toString();
                int delimiterIndex = Math.max(
                        Math.max(response.lastIndexOf('.'), response.lastIndexOf('?')),
                        response.lastIndexOf('!')
                );

                String finalResponse = delimiterIndex != -1 ? response.substring(0, delimiterIndex + 1) : response;
                broadcastGeneratedMessage(prompt, finalResponse);

                // Update the UI with the final response
                runOnUiThread(() -> {
                    displayResponse(finalResponse);
                    narrateResponse(finalResponse);
                });

                // Add response to cache and save to file
                responseCache.put(prompt, finalResponse);
                saveCacheToFile();
                isGeneratingResponse = false;

            } catch (GenAIException e) {
                Log.e(TAG, "Error during generation: ", e);
            } finally {
                synchronized (this) {
                    if (currentStream != null) {
                        currentStream.close();
                        currentStream = null;
                    }
                }
                isGeneratingResponse = false;
            }
        });
    }

    private void saveCacheToFile() {
        File cacheFile = new File(getFilesDir(), CACHE_FILE_NAME);

        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            JSONObject cacheJson = new JSONObject(responseCache);
            fos.write(cacheJson.toString().getBytes());
            Log.d(TAG, "Response cache saved to file.");
        } catch (IOException e) {
            Log.e(TAG, "Error saving response cache to file", e);
        }
    }

    private void loadCacheFromFile() {
        File cacheFile = new File(getFilesDir(), CACHE_FILE_NAME);

        if (!cacheFile.exists()) {
            Log.d(TAG, "No existing cache file found. Starting with empty cache.");
            return;
        }

        try (InputStream is = new FileInputStream(cacheFile)) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            String jsonString = new String(buffer);
            JSONObject cacheJson = new JSONObject(jsonString);

            responseCache.clear();
            for (Iterator<String> it = cacheJson.keys(); it.hasNext(); ) {
                String key = it.next();
                responseCache.put(key, cacheJson.getString(key));
            }
            Log.d(TAG, "Response cache loaded from file.");
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error loading response cache from file", e);
        }
    }



    private void displayResponse(String response) {
        runOnUiThread(() -> generatedTV.setText(response));
    }



    private void narrateResponse(String response) {
        if (textToSpeech != null && !textToSpeech.isSpeaking()) {
            textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            Log.e("TTS", "TextToSpeech not initialized or currently speaking");
        }
    }


    private void resetGrid() {
        gridLayout.removeAllViews();
        loadInitialPictograms(); // Reset to the top-level options
    }

    private void downloadModels(Context context) throws GenAIException {
        final String baseUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/";
        List<String> files = Arrays.asList(
                "added_tokens.json",
                "config.json",
                "configuration_phi3.py",
                "genai_config.json",
                "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx",
                "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx.data",
                "special_tokens_map.json",
                "tokenizer.json",
                "tokenizer.model",
                "tokenizer_config.json");

        ArrayList<Pair<String, String>> urlFilePairs = new ArrayList<>();
        for (String file : files) {
            if (!fileExists(context, file)) {
                urlFilePairs.add(new Pair<>(
                        baseUrl + file,
                        file));
            }
        }
        if (urlFilePairs.isEmpty()) {
            // Display a message using Toast
            Toast.makeText(this, "All files already exist. Skipping download.", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "All files already exist. Skipping download.");
            model = new Model(getFilesDir().getPath());
            tokenizer = model.createTokenizer();
            return;
        }

        Toast.makeText(this,
                "Downloading model for the app... Model Size greater than 2GB, please allow a few minutes to download.",
                Toast.LENGTH_SHORT).show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ModelDownloader.downloadModel(context, urlFilePairs, new ModelDownloader.DownloadCallback() {
                @Override
                public void onProgress(long lastBytesRead, long bytesRead, long bytesTotal) {
                    long lastPctDone = 100 * lastBytesRead / bytesTotal;
                    long pctDone = 100 * bytesRead / bytesTotal;
                    if (pctDone > lastPctDone) {
                        Log.d(TAG, "Downloading files: " + pctDone + "%");
                        runOnUiThread(() -> {
                            progressText.setText("Downloading: " + pctDone + "%");
                        });
                    }
                }
                @Override
                public void onDownloadComplete() {
                    Log.d(TAG, "All downloads completed.");

                    // Last download completed, create SimpleGenAI
                    try {
                        model = new Model(getFilesDir().getPath());
                        tokenizer = model.createTokenizer();
                        runOnUiThread(() -> {
                            Toast.makeText(context, "All downloads completed", Toast.LENGTH_SHORT).show();
                            progressText.setVisibility(View.INVISIBLE);
                        });
                    } catch (GenAIException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }

                }
            });
        });
        executor.shutdown();
    }

    private boolean fileExists(Context context, String fileName) {
        return new File(context.getFilesDir(), fileName).exists();
    }

    private class ModelDownloadCallback implements ModelDownloader.DownloadCallback {
        @Override
        public void onProgress(long lastBytesRead, long bytesRead, long bytesTotal) {
            int progress = (int) (100 * bytesRead / bytesTotal);
            runOnUiThread(() -> progressText.setText("Downloading: " + progress + "%"));
        }

        @Override
        public void onDownloadComplete() {
            try {
                model = new Model(getFilesDir().getPath());
                tokenizer = model.createTokenizer();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Model download complete.", Toast.LENGTH_SHORT).show());
            } catch (GenAIException e) {
                Log.e(TAG, "Error initializing model after download: ", e);
            }
        }
    }

    private void loadInitialPictograms() {
        Pictogram rootPictogram = pictogramMap.get("Actions");
        if (rootPictogram != null) {
            Log.d(TAG, "Found 'Actions' category: " + rootPictogram);
            displayPictograms(rootPictogram.getChildren());
        } else {
            Log.e(TAG, "'Actions' category not found in pictogramMap.");
        }
    }



    private void handlePictogramSelection(String label, Pictogram pictogram) {
        Log.d(TAG, "Selected item: " + label);

        if (label.equals(lastRootButtonPressed)) {
            Log.d(TAG, "Root-level button pressed again, ignoring.");
            return; // Ignore if it's the same button pressed consecutively
        }

        lastRootButtonPressed = label;
        buttonSequence.add(lastRootButtonPressed);
        updateSequenceDisplay(pictogram);

        List<Pictogram> pictogramsToDisplay = new ArrayList<>();
        for (String suggestion : pictogram.getSuggestions()) {
            Pictogram parentPictogram = pictogramMap.get(suggestion);
            if (parentPictogram != null) {
                pictogramsToDisplay.addAll(parentPictogram.getChildren());
            }
        }

        // Push a new NavigationState onto the stack
        navigationStack.push(new NavigationState(new ArrayList<>(buttonSequence), pictogram, pictogramsToDisplay));

        if (!pictogramsToDisplay.isEmpty()) {
            generatedTV.setText(pictogram.getDefaultResponse());
            displayPictograms(pictogramsToDisplay);
        } else {
            String prompt = String.join(" ", buttonSequence);
            generateResponse(prompt);
            buttonSequence.clear(); // Clear sequence after generating response
        }
    }



    // This method should display the final list of pictograms (with images) in the UI
    private void displayPictograms(List<Pictogram> pictograms) {
        gridLayout.removeAllViews();  // Clear previous views immediately
        for (Pictogram pictogram : pictograms) {
            createPictogramButton(pictogram); // Add new buttons for current level
        }
    }

    private void updateSequenceDisplay(Pictogram pictogram) {
        // Find the sequence layout for displaying the pictogram sequence
        LinearLayout sequenceLayout = findViewById(R.id.sequence_layout);

        // Check if the pictogram is already in the sequence
        if (addedPictograms.contains(pictogram.getLabel())) {
            Log.d(TAG, "Pictogram already in sequence: " + pictogram.getLabel());
            return; // Do nothing if the pictogram is already in the sequence
        }

        // Add the pictogram label to the set to keep track
        addedPictograms.add(pictogram.getLabel());

        // Create a smaller button for each selected pictogram
        Button sequenceButton = new Button(this);
        sequenceButton.setText(pictogram.getLabel());
        sequenceButton.setTextSize(12);
        sequenceButton.setTextColor(getResources().getColor(R.color.black));
        sequenceButton.setEnabled(false); // Disable interaction for sequence display

        // Load the image from internal storage and set it as the button drawable
        String imageFileName = pictogram.getImageFile();
        if (imageFileName != null) {
            File internalImageFile = new File(getFilesDir(), imageFileName);
            if (internalImageFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(internalImageFile.getAbsolutePath());
                if (bitmap != null) {
                    Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                    sequenceButton.setBackgroundResource(R.drawable.button_background);
                    sequenceButton.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
                } else {
                    Log.e(TAG, "Bitmap from internal storage is null for file: " + imageFileName);
                }
            } else {
                Log.e(TAG, "Image file not found in internal storage: " + imageFileName);
            }
        }

        // Add the button to the sequence layout
        sequenceLayout.addView(sequenceButton);
    }




    private void createPictogramButton(Pictogram pictogram) {
        Button button = new Button(this);
        button.setText(pictogram.getLabel());
        button.setTextColor(getResources().getColor(R.color.black));

        // Load and set the icon from assets, if available
        String imageFileName = pictogram.getImageFile();
        if (imageFileName != null) {
            try {
                // Load the image from internal storage
                File imageFile = new File(getFilesDir(), imageFileName);
                if (imageFile.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                    if (bitmap != null) {
                        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
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


        // Set button click to handle pictogram selection based on suggestions
        button.setOnClickListener(v -> {
            Log.d(TAG, "Button pressed: " + pictogram.getLabel());
            handlePictogramSelection(pictogram.getLabel(), pictogram);
        });

        gridLayout.addView(button);
    }

    private void setupBackButton() {
        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> {
            generatedTV.setText(""); // Clear the response text

            if (navigationStack.size() > 1) { // Ensure stack has more than one state
                // Pop current state
                navigationStack.pop();
                NavigationState previousState = navigationStack.peek();

                // Restore button sequence and previous level
                buttonSequence = new ArrayList<>(previousState.buttonSequence);
                displayPictograms(previousState.pictogramsToDisplay);

                // Update the sequence layout visually
                sequenceLayout.removeAllViews();
                addedPictograms.clear();
                for (String label : buttonSequence) {
                    updateSequenceDisplay(pictogramMap.get(label));
                }
                Log.d(TAG, "Back button pressed. Restored state: " + previousState);

            } else { // Reset if only one level in stack
                loadInitialPictograms();
                buttonSequence.clear();
                navigationStack.clear();
                sequenceLayout.removeAllViews(); // Clear sequence display
                addedPictograms.clear();
                lastRootButtonPressed = null;
                Log.d(TAG, "Back to root level.");
            }
        });
    }

    @Override
    public void accept(String token) {
        runOnUiThread(() -> generatedTV.append(token));
    }

    @Override
    protected void onDestroy() {
        if (tokenizer != null) tokenizer.close();
        if (model != null) model.close();
        if (socketHelper != null) {
            socketHelper.stopServer();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            // Reload pictograms after adding a new one
            resetGrid();
            loadPictogramsFromJSON();
        }
    }

    private void copyPictogramKeyToInternalStorage() {
        File destFile = new File(getFilesDir(), "pictogramKey.json");
        if (!destFile.exists()) {
            try {
                InputStream inputStream = getAssets().open("AAC_Symbols/pictogramKey.json");
                FileOutputStream outputStream = new FileOutputStream(destFile);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }

                inputStream.close();
                outputStream.close();
                Log.d(TAG, "pictogramKey.json copied to internal storage.");
            } catch (IOException e) {
                Log.e(TAG, "Error copying pictogramKey.json to internal storage", e);
            }
        } else {
            Log.d(TAG, "pictogramKey.json already exists in internal storage.");
        }
    }

    private void copyStockImagesToInternalStorage() {
        AssetManager assetManager = getAssets();
        try {
            // Manually specify the path of each image
            String[] assetFiles = assetManager.list("AAC_Symbols");
            if (assetFiles != null) {
                for (String fileName : assetFiles) {
                    if (fileName.endsWith(".png")) { // Process only .png files
                        Log.d(TAG, "Found stock image in assets: " + fileName);
                        File internalImageFile = new File(getFilesDir(), fileName);

                        // Copy the file if it doesn't already exist in internal storage
                        if (!internalImageFile.exists()) {
                            try (InputStream inputStream = assetManager.open("AAC_Symbols/" + fileName);
                                 FileOutputStream outputStream = new FileOutputStream(internalImageFile)) {

                                byte[] buffer = new byte[1024];
                                int length;
                                while ((length = inputStream.read(buffer)) > 0) {
                                    outputStream.write(buffer, 0, length);
                                }

                                Log.d(TAG, "Copied stock image to internal storage: " + fileName);
                            } catch (IOException e) {
                                Log.e(TAG, "Error copying image: " + fileName, e);
                            }
                        } else {
                            Log.d(TAG, "Stock image already exists in internal storage: " + fileName);
                        }
                    }
                }
            } else {
                Log.e(TAG, "AAC_Symbols directory not found in assets.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error accessing AAC_Symbols assets", e);
        }
    }



    private void deleteInternalPictogramKey() {
        File jsonFile = new File(getFilesDir(), "pictogramKey.json");
        File responseCacheFile = new File(getFilesDir(), CACHE_FILE_NAME);
        if (jsonFile.exists()) {
            boolean deleted = jsonFile.delete();
            boolean deletedCache = responseCacheFile.delete();
            if (deleted && deletedCache) {
                Log.d(TAG, "Internal pictogramKey.json and cache deleted successfully.");
                Toast.makeText(this, "File deleted successfully.", Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Failed to delete internal pictogramKey.json.");
                Toast.makeText(this, "Failed to delete file.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d(TAG, "Internal pictogramKey.json does not exist.");
            Toast.makeText(this, "File does not exist.", Toast.LENGTH_SHORT).show();
        }
    }


    void loadPictograms() {
        try {
            File jsonFile = new File(getFilesDir(), "pictogramKey.json");
            if (jsonFile.exists()) {
                String jsonData = readFromFile(jsonFile);
                JSONObject jsonObject = new JSONObject(jsonData);

                // Iterate through JSON and load pictograms
                for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    JSONObject pictogramData = jsonObject.getJSONObject(key);

                    // Process each pictogram and load it into the UI
                    String imageFile = pictogramData.getString("Image_File");
                    String parentCategory = pictogramData.getString("Parent_Category");
                    JSONArray keywords = pictogramData.getJSONArray("Keywords");

                    // Add the pictogram to the UI or process as needed
                    Log.d(TAG, "Loaded pictogram: " + key);
                }
            } else {
                Log.e(TAG, "pictogramKey.json does not exist in internal storage.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading pictograms", e);
        }
    }

    private void broadcastGeneratedMessage(String pictogramString, String generatedResponse) {
        // Save the message to the local database
        String timestamp = String.valueOf(System.currentTimeMillis());
        dbHelper.insertMessage(pictogramString, generatedResponse, timestamp);

        // Construct the message JSON
        JSONObject messageJson = new JSONObject();
        try {
            messageJson.put("Pictogram_String", pictogramString);
            messageJson.put("Generated_Response", generatedResponse);
            messageJson.put("Time_Created", timestamp);
        } catch (JSONException e) {
            Log.e(TAG, "Error constructing message JSON", e);
            return;
        }

        // Send the message to all connected devices
        socketHelper.broadcastMessage(messageJson.toString());
    }

    private void startServer() {
        new Thread(() -> {
            socketHelper.startServer();

            // Retrieve the local IP address
            String localIp = NetworkUtils.getWifiIpAddress(this);
            if (localIp == null) {
                localIp = NetworkUtils.getLocalIpAddress();
            }

            if (localIp != null) {
                String message = "Server started! IP: " + localIp + " Port: " + SocketServerHelper.PORT;
                Log.d(TAG, message);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
            } else {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Unable to determine IP address.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showIpInputDialog() {
        // Create a dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter IP Address");

        // Set up the input field
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("e.g., 192.168.1.100");
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", (dialog, which) -> {
            String ipAddress = input.getText().toString().trim();
            if (!ipAddress.isEmpty()) {
                // Start CaregiverModeActivity with the entered IP
                Intent intent = new Intent(MainActivity.this, CaregiverModeActivity.class);
                intent.putExtra("server_ip", ipAddress); // Pass the IP to the activity
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "IP Address cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showPairingPopup() {
        // Retrieve the device's IP address
        String ipAddress = NetworkUtils.getWifiIpAddress(this); // true for IPv4
        int port = 12345; // Use your server's port

        // Create a message with connection details
        String pairingInfo = "Server IP: " + ipAddress + "\nPort: " + port;

        // Display in an AlertDialog
        new AlertDialog.Builder(this)
                .setTitle("Pair Caretaker Device")
                .setMessage(pairingInfo)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }



}
