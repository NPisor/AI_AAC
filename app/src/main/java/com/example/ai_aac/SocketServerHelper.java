package com.example.ai_aac;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketServerHelper {
    public static final int PORT = 12345;
    private static final String TAG = "SocketServerHelper";

    private ServerSocket serverSocket;
    private final List<Socket> clientSockets = new CopyOnWriteArrayList<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ConnectionCallback connectionCallback;

    public interface ConnectionCallback {
        void onMessageReceived(String message);
        void onClientConnected(String clientAddress);
        void onError(String errorMessage);
    }

    public SocketServerHelper(ConnectionCallback connectionCallback) {
        this.connectionCallback = connectionCallback;
    }

    // ===== Start Server =====
    public void startServer() {
        executorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.d(TAG, "Server started on port: " + PORT);

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    clientSockets.add(clientSocket);
                    Log.d(TAG, "Client connected: " + clientSocket.getInetAddress());
                    if (connectionCallback != null) {
                        connectionCallback.onClientConnected(clientSocket.getInetAddress().toString());
                    }
                    handleClientConnection(clientSocket);
                }
            } catch (IOException e) {
                Log.e(TAG, "Server stopped or error occurred: ", e);
                if (connectionCallback != null) {
                    connectionCallback.onError("Server error: " + e.getMessage());
                }
            }
        });
    }

    private void handleClientConnection(Socket clientSocket) {
        executorService.execute(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d(TAG, "Message from client: " + line);
                    if (connectionCallback != null) {
                        connectionCallback.onMessageReceived(line);
                    }

                    // Send acknowledgment
                    try {
                        JSONObject responseJson = new JSONObject();
                        responseJson.put("Acknowledgment", true);
                        responseJson.put("Received_Request", line);
                        writer.println(responseJson.toString());
                        Log.d(TAG, "Acknowledgment sent: " + responseJson);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error constructing acknowledgment JSON", e);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error handling client: ", e);
            } finally {
                disconnectClient(clientSocket);
            }
        });
    }

    // ===== Client-Side Logic =====
    public Socket connectToServer(String serverIp, int port) {
        final Socket[] clientSocket = {null};
        executorService.execute(() -> {
            try {
                clientSocket[0] = new Socket(serverIp, port);
                Log.d(TAG, "Connected to server at " + serverIp + ":" + port);
                listenForMessages(clientSocket[0]);
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to server: ", e);
                if (connectionCallback != null) {
                    connectionCallback.onError("Connection error: " + e.getMessage());
                }
            }
        });
        return clientSocket[0];
    }

    public void listenForMessages(Socket socket) {
        executorService.execute(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String message;
                while ((message = reader.readLine()) != null) {
                    Log.d(TAG, "Message received from server: " + message);
                    if (connectionCallback != null) {
                        connectionCallback.onMessageReceived(message);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error receiving message from server: ", e);
                if (connectionCallback != null) {
                    connectionCallback.onError("Connection lost: " + e.getMessage());
                }
            }
        });
    }

    // ===== Broadcasting Messages =====
    public void broadcastMessage(String message) {
        executorService.execute(() -> {
            for (Socket client : clientSockets) {
                if (client.isConnected()) {
                    try {
                        PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
                        writer.println(message); // Automatically adds a newline
                        Log.d(TAG, "Message broadcasted to client: " + message);
                    } catch (IOException e) {
                        Log.e(TAG, "Error sending message to client: ", e);
                    }
                } else {
                    Log.d(TAG, "Skipping disconnected client.");
                    clientSockets.remove(client); // Clean up disconnected clients
                }
            }
        });
    }

    // ===== Stopping and Disconnecting =====
    public void stopServer() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
                Log.d(TAG, "Server stopped.");
            }
            for (Socket client : clientSockets) {
                disconnectClient(client);
            }
            clientSockets.clear();
        } catch (IOException e) {
            Log.e(TAG, "Error stopping server: ", e);
        }
    }

    public void disconnectClient(Socket clientSocket) {
        try {
            if (clientSocket != null) {
                clientSocket.close();
                Log.d(TAG, "Client disconnected: " + clientSocket.getInetAddress());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error disconnecting client: ", e);
        }
    }
}
