package com.example.ai_aac;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

public class CaregiverMessageAdapter extends ArrayAdapter<CaregiverMessage> {
    private Context context;
    private int resource;

    public CaregiverMessageAdapter(@NonNull Context context, int resource, @NonNull List<CaregiverMessage> messages) {
        super(context, resource, messages);
        this.context = context;
        this.resource = resource;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(resource, parent, false);
        }

        // Get the current message
        CaregiverMessage message = getItem(position);
        if (message == null) return convertView;

        // Populate the timestamp
        TextView timestampText = convertView.findViewById(R.id.timestamp_text);
        timestampText.setText(message.getFormattedTimestamp());

        // Populate the generated response
        TextView responseText = convertView.findViewById(R.id.generated_response_text);
        responseText.setText(message.getGeneratedResponse());

        // Populate the pictogram sequence
        LinearLayout pictogramSequenceLayout = convertView.findViewById(R.id.pictogram_sequence_layout);
        pictogramSequenceLayout.removeAllViews();

        for (Pictogram pictogram : message.getPictograms()) {
            // Create a button for each pictogram
            Button button = PictogramManager.getInstance().createPictogramButton(context, pictogram);
            button.setEnabled(false); // Make the button non-interactive
            button.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            pictogramSequenceLayout.addView(button);
        }

        return convertView;
    }
}
