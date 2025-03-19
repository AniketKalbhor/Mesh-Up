package com.example.meshup;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private List<Message> messages;
    private Context context;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public MessageAdapter(Context context) {
        this.context = context;
        this.messages = new ArrayList<>();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.message_item, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);

        // Set message header (sender info)
        String header = String.format("%s (%s)",
                message.getSenderName(),
                message.getIpAddress());
        holder.headerText.setText(header);

        // Set message content
        holder.messageText.setText(message.getContent());

        // Set timestamp
        holder.timestampText.setText(timeFormat.format(new Date(message.getTimestamp())));

        // Set message color based on delivery and authentication status
        int textColor;
        if (!message.isDelivered()) {
            textColor = ContextCompat.getColor(context, R.color.md_theme_error);
        } else if (message.isAuthenticated()) {
            textColor = ContextCompat.getColor(context, R.color.md_theme_primaryFixedDim);
        } else {
            // Message delivered but not authenticated yet
            textColor = ContextCompat.getColor(context, R.color.md_theme_secondary);
        }
        holder.headerText.setTextColor(textColor);

        // Set authentication icon visibility
        if (holder.authIcon != null) {
            if (message.isAuthenticated()) {
                holder.authIcon.setVisibility(View.VISIBLE);
                holder.authIcon.setImageResource(R.drawable.ic_verified); // Ensure ic_verified exists in drawable
            } else if (message.isDelivered()) {
                holder.authIcon.setVisibility(View.VISIBLE);
                holder.authIcon.setImageResource(R.drawable.ic_pending); // Ensure ic_pending exists in drawable
            } else {
                holder.authIcon.setVisibility(View.GONE);
            }
        }

        // Show MAC address on long press
        holder.itemView.setOnLongClickListener(v -> {
            String status = "MAC: " + message.getMacAddress();

            // Add authentication status to info
            if (message.isAuthenticated()) {
                status += " | Verified ✓";
            } else if (message.isDelivered()) {
                status += " | Pending Verification...";
            } else {
                status += " | Delivery Failed";
            }

            Toast.makeText(context, status, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateMessageDeliveryStatus(String messageId, boolean delivered) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getMessageId().equals(messageId)) {
                messages.get(i).setDelivered(delivered);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void updateMessageAuthenticationStatus(String messageId, boolean authenticated) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getMessageId().equals(messageId)) {
                messages.get(i).setAuthenticated(authenticated);
                notifyItemChanged(i);
                break;
            }
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView headerText;
        TextView messageText;
        TextView timestampText;
        ImageView authIcon;

        MessageViewHolder(View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.headerText);
            messageText = itemView.findViewById(R.id.messageText);
            timestampText = itemView.findViewById(R.id.timestampText);
            authIcon = itemView.findViewById(R.id.authIcon); // Ensure message_item.xml has an ImageView with id authIcon
        }
    }
}
