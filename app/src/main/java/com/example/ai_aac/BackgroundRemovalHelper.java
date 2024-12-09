package com.example.ai_aac;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

public class BackgroundRemovalHelper {

    public static void removeBackground(Bitmap originalBitmap, BackgroundRemovalCallback callback) {
        // Check if the first row has non-white pixels
        if (isFirstRowNonWhite(originalBitmap)) {
            // Run ML Kit's segmentation if the first row is not white
            isolateImageWithMLKit(originalBitmap, new BackgroundRemovalCallback() {
                @Override
                public void onSuccess(Bitmap isolatedBitmap) {
                    // Remove white background and crop the isolated image
                    Bitmap resultBitmap = removeWhiteBackgroundAndCrop(isolatedBitmap);
                    callback.onSuccess(resultBitmap);
                }

                @Override
                public void onFailure(Exception e) {
                    callback.onFailure(e);
                }
            });
        } else {
            // Directly remove white background and crop
            Bitmap resultBitmap = removeWhiteBackgroundAndCrop(originalBitmap);
            callback.onSuccess(resultBitmap);
        }
    }

    private static boolean isFirstRowNonWhite(Bitmap bitmap) {
        int width = bitmap.getWidth();
        for (int x = 0; x < width; x++) {
            int pixel = bitmap.getPixel(x, 0);
            if (!isWhite(pixel)) {
                return true; // Found a non-white pixel
            }
        }
        return false;
    }

    private static boolean isWhite(int color) {
        int threshold = 240; // Threshold for detecting "white" pixels
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return r > threshold && g > threshold && b > threshold;
    }

    private static void isolateImageWithMLKit(Bitmap originalBitmap, BackgroundRemovalCallback callback) {
        InputImage image = InputImage.fromBitmap(originalBitmap, 0);
        SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder().enableForegroundBitmap().build();
        SubjectSegmenter segmenter = SubjectSegmentation.getClient(options);

        segmenter.process(image)
                .addOnSuccessListener(new OnSuccessListener<SubjectSegmentationResult>() {
                    @Override
                    public void onSuccess(SubjectSegmentationResult result) {
                        Bitmap isolatedBitmap = result.getForegroundBitmap();
                        callback.onSuccess(isolatedBitmap);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        callback.onFailure(e);
                    }
                });
    }

    private static Bitmap removeWhiteBackgroundAndCrop(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int threshold = 240;

        int top = height, bottom = 0;
        int left = width, right = 0;

        // Arrays to track leftmost and rightmost subject pixels for each line
        int[] leftmost = new int[height];
        int[] rightmost = new int[height];

        // Initialize arrays
        for (int i = 0; i < height; i++) {
            leftmost[i] = width; // Max possible value
            rightmost[i] = -1;   // Min possible value
        }

        // Identify subject and update bounds
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                if (r <= threshold || g <= threshold || b <= threshold) {
                    result.setPixel(x, y, pixel);

                    // Update leftmost and rightmost for this line
                    if (x < leftmost[y]) leftmost[y] = x;
                    if (x > rightmost[y]) rightmost[y] = x;

                    // Update top and bottom
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                } else {
                    result.setPixel(x, y, Color.TRANSPARENT);
                }
            }
        }

        // Calculate overall left and right bounds from all lines
        for (int y = top; y <= bottom; y++) {
            if (leftmost[y] < left) left = leftmost[y];
            if (rightmost[y] > right) right = rightmost[y];
        }

        // Calculate subject dimensions
        int subjectWidth = right - left + 1;
        int subjectHeight = bottom - top + 1;

        // Apply padding (e.g., 20% of subject size)
        int paddingX = (int) (subjectWidth * 0.1); // 20% padding horizontally
        int paddingY = (int) (subjectHeight * 0.1); // 20% padding vertically

        // Adjust bounds with padding
        int paddedLeft = Math.max(0, left - paddingX);
        int paddedTop = Math.max(0, top - paddingY);
        int paddedRight = Math.min(width - 1, right + paddingX);
        int paddedBottom = Math.min(height - 1, bottom + paddingY);

        // Calculate the midpoint of the padded area
        int paddedWidth = paddedRight - paddedLeft + 1;
        int paddedHeight = paddedBottom - paddedTop + 1;
        int midpointX = paddedLeft + paddedWidth / 2;
        int midpointY = paddedTop + paddedHeight / 2;

        // Recalculate cropping bounds to center the subject
        int finalWidth = Math.min(paddedWidth, width); // Ensure it doesn't exceed image width
        int finalHeight = Math.min(paddedHeight, height); // Ensure it doesn't exceed image height
        int cropLeft = Math.max(0, midpointX - finalWidth / 2);
        int cropTop = Math.max(0, midpointY - finalHeight / 2);
        int cropRight = Math.min(width - 1, cropLeft + finalWidth - 1);
        int cropBottom = Math.min(height - 1, cropTop + finalHeight - 1);

        // Crop the image centered around the subject with padding
        return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropRight - cropLeft + 1, cropBottom - cropTop + 1);
    }





    public interface BackgroundRemovalCallback {
        void onSuccess(Bitmap result);
        void onFailure(Exception e);
    }
}
