package com.example.ai_aac;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class CaregiverModeActivity extends AppCompatActivity {

    private static final String TAG = "CaregiverModeActivity";

    private ListView messageListView;
    private DatabaseHelper dbHelper;
    private PictogramManager pictogramManager;
    private SocketServerHelper socketHelper;
    private Socket clientSocket;

    private String serverIp;
    private final int serverPort = 12345; // Match the port used on the child's server

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.caregiver_layout_main);

        // Initialize Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Initialize components
        messageListView = findViewById(R.id.message_list_view);
        dbHelper = new DatabaseHelper(this, "messages.db", null, 1);
        pictogramManager = PictogramManager.getInstance();

        // Set up the ListView with custom adapter
        setupListView();

        Intent intent = getIntent();
        serverIp = intent.getStringExtra("server_ip");

        // Initialize socket helper
        socketHelper = new SocketServerHelper(new SocketServerHelper.ConnectionCallback() {
            @Override
            public void onMessageReceived(String message) {
                Log.d(TAG, "Message received: " + message);
                handleIncomingMessage(message);
            }

            @Override
            public void onClientConnected(String clientAddress) {
                Log.d(TAG, "Connected to server: " + clientAddress);
                runOnUiThread(() -> Toast.makeText(CaregiverModeActivity.this, "Connected to server!", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Socket error: " + errorMessage);
                runOnUiThread(() -> Toast.makeText(CaregiverModeActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show());
            }
        });

        // Start socket connection
        startSocketConnection();
    }

    private void setupListView() {
        List<CaregiverMessage> messageList = new ArrayList<>();
        CaregiverMessageAdapter messageAdapter = new CaregiverMessageAdapter(this, R.layout.caregiver_list_item, messageList);
        messageListView.setAdapter(messageAdapter);

        // Load messages from the database
        loadMessagesFromDatabase(messageAdapter);
    }

    private void startSocketConnection() {
        new Thread(() -> {
            try {
                clientSocket = socketHelper.connectToServer(serverIp, serverPort);
            } catch (Exception e) {
                Log.e(TAG, "Error starting socket connection: ", e);
                runOnUiThread(() -> Toast.makeText(CaregiverModeActivity.this, "Failed to connect: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void handleIncomingMessage(String message) {
        try {
            JSONObject messageJson = new JSONObject(message);

            if (messageJson.has("Pictogram_String") && messageJson.has("Generated_Response") && messageJson.has("Time_Created")) {
                String pictogramString = messageJson.getString("Pictogram_String");
                String generatedResponse = messageJson.getString("Generated_Response");
                String timestamp = messageJson.getString("Time_Created");

                // Check for exact match or split keywords
                List<String> keywords = new ArrayList<>();
                if (pictogramManager.getPictogramMap().containsKey(pictogramString)) {
                    keywords.add(pictogramString);
                } else {
                    keywords.addAll(Arrays.asList(pictogramString.split(" ")));
                }

                dbHelper.insertMessage(String.join(", ", keywords), generatedResponse, timestamp);

                List<Pictogram> pictogramsInMessage = new ArrayList<>();
                for (String keyword : keywords) {
                    if (pictogramManager.getPictogramMap().containsKey(keyword.trim())) {
                        pictogramsInMessage.add(pictogramManager.getPictogramMap().get(keyword.trim()));
                    } else {
                        Log.e(TAG, "Pictogram not found for keyword: " + keyword);
                    }
                }

                // Update UI
                runOnUiThread(() -> {
                    CaregiverMessage messageEntry = new CaregiverMessage(pictogramsInMessage, generatedResponse, timestamp);
                    ((CaregiverMessageAdapter) messageListView.getAdapter()).add(messageEntry);
                });
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing incoming message: " + message, e);
        }
    }

    private void loadMessagesFromDatabase(CaregiverMessageAdapter adapter) {
        Cursor cursor = dbHelper.getAllMessages();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                @SuppressLint("Range") String pictogramString = cursor.getString(cursor.getColumnIndex("pictogram"));
                @SuppressLint("Range") String generatedResponse = cursor.getString(cursor.getColumnIndex("response"));
                @SuppressLint("Range") String timestamp = cursor.getString(cursor.getColumnIndex("timestamp"));

                // Process pictograms
                List<Pictogram> pictogramsInMessage = new ArrayList<>();
                for (String keyword : pictogramString.split(", ")) {
                    if (pictogramManager.getPictogramMap().containsKey(keyword.trim())) {
                        pictogramsInMessage.add(pictogramManager.getPictogramMap().get(keyword.trim()));
                    }
                }

                adapter.add(new CaregiverMessage(pictogramsInMessage, generatedResponse, timestamp));
            }
            cursor.close();
        }
    }
}
