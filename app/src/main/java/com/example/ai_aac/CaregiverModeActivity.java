package com.example.ai_aac;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class CaregiverModeActivity extends AppCompatActivity {

    private static final String TAG = "CaregiverModeActivity";

    private ListView messageListView;
    private ArrayAdapter<String> messageAdapter;
    private List<String> messages;
    private SocketServerHelper socketHelper;
    private DatabaseHelper dbHelper;

    private Socket clientSocket;
    private String serverIp;
    private final int serverPort = 12345; // Match the port used on the child's server

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.caregiver_layout_main);

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Initialize UI components
        messageListView = findViewById(R.id.message_list_view);
        messages = new ArrayList<>();
        messageAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, messages);
        messageListView.setAdapter(messageAdapter);

        // Initialize database helper
        dbHelper = new DatabaseHelper(this, "messages.db", null, 1);

        // Load existing messages from the database
        loadMessagesFromDatabase();

        Intent intent = getIntent();
        serverIp = intent.getStringExtra("server_ip");

        // Initialize socket helper with callback
        socketHelper = new SocketServerHelper(new SocketServerHelper.ConnectionCallback() {
            @Override
            public void onMessageReceived(String message) {
                Log.d(TAG, "Message received: " + message);
                handleIncomingMessage(message);
            }

            @Override
            public void onClientConnected(String clientAddress) {
                Log.d(TAG, "Connected to server: " + clientAddress);
                runOnUiThread(() ->
                        Toast.makeText(CaregiverModeActivity.this, "Connected to server!", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Socket error: " + errorMessage);
                runOnUiThread(() ->
                        Toast.makeText(CaregiverModeActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show());
            }
        });

        // Start the socket client to connect to the child device
        startSocketConnection();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (socketHelper != null) {
            socketHelper.disconnectClient(clientSocket);
        }
    }

    private void startSocketConnection() {
        new Thread(() -> {
            try {
                clientSocket = socketHelper.connectToServer(serverIp, serverPort);
            } catch (Exception e) {
                Log.e(TAG, "Error starting socket connection: ", e);
                runOnUiThread(() ->
                        Toast.makeText(CaregiverModeActivity.this, "Failed to connect: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void requestMissingMessages() {
        // Fetch the latest timestamp as a string
        String latestTimestamp = dbHelper.getLatestTimestamp();

        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("Request_Missing_Messages", true);
            requestJson.put("Latest_Timestamp", latestTimestamp);
            socketHelper.broadcastMessage(requestJson.toString());
            Log.d(TAG, "Message sent: " + requestJson.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error constructing missing message request JSON", e);
        }
    }

    private void handleIncomingMessage(String message) {
        try {
            JSONObject messageJson = new JSONObject(message);

            if (messageJson.has("Pictogram_String") && messageJson.has("Generated_Response") && messageJson.has("Time_Created")) {
                // Parse the received message
                String pictogramString = messageJson.getString("Pictogram_String");
                String generatedResponse = messageJson.getString("Generated_Response");
                String timestamp = messageJson.getString("Time_Created");

                // Save to the database
                dbHelper.insertMessage(pictogramString, generatedResponse, timestamp);

                // Add to the UI
                runOnUiThread(() -> addMessageToUI(pictogramString, generatedResponse, timestamp));
            } else if (messageJson.has("Acknowledgment")) {
                // Handle acknowledgment messages
                Log.d(TAG, "Acknowledgment received: " + messageJson.toString());
            }
        } catch (JSONException e) {
            Log.e(TAG, "Received non-JSON message or invalid format: " + message, e);
            // Handle non-JSON plain text messages
            if (message.startsWith("Acknowledged:")) {
                Log.d(TAG, "Acknowledged message: " + message);
            }
        }
    }

    private void loadMessagesFromDatabase() {
        Cursor cursor = dbHelper.getAllMessages();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                @SuppressLint("Range") String pictogramString = cursor.getString(cursor.getColumnIndex("pictogram"));
                @SuppressLint("Range") String generatedResponse = cursor.getString(cursor.getColumnIndex("response"));
                @SuppressLint("Range") String timestamp = cursor.getString(cursor.getColumnIndex("timestamp"));

                // Format message for display
                String displayMessage = formatMessage(pictogramString, generatedResponse, timestamp);
                messages.add(displayMessage);
            }
            cursor.close();
        }
        messageAdapter.notifyDataSetChanged();
    }

    private void addMessageToUI(String pictogramString, String response, String timestamp) {
        String displayMessage = formatMessage(pictogramString, response, timestamp);
        messages.add(displayMessage);
        messageAdapter.notifyDataSetChanged();
    }

    private String formatMessage(String pictogramString, String response, String timestamp) {
        return "Time: " + timestamp + "\n" +
                "Pictograms: " + pictogramString + "\n" +
                "Response: " + response;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_caregiver_mode, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            requestMissingMessages();
            return true;
        } else if (item.getItemId() == R.id.action_reconnect) {
            startSocketConnection();
            return true;
        } else if (item.getItemId() == R.id.action_exit) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
