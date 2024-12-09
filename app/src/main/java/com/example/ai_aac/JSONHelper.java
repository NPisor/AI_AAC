package com.example.ai_aac;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class JSONHelper {

    public static JSONObject loadJSONFromAsset(Context context, String fileName) {
        String json;
        try {
            AssetManager manager = context.getAssets();
            InputStream is = manager.open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
            return new JSONObject(json);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static JSONObject loadJSONFromFile(String filePath) {
        String json;
        try {
            InputStream is = Files.newInputStream(Paths.get(filePath));
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
            return new JSONObject(json);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
