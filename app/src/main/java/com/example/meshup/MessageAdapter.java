package com.example.meshup;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
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
    private Handler handler = new Handler(Looper.getMainLooper());

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

        // Set message content
        holder.messageText.setText(message.getContent());

        // Set timestamp
        holder.timestampText.setText(timeFormat.format(new Date()));

        // Configure message appearance based on security status
        configureMessageAppearance(holder, message);

        // Show detailed info on long press
        holder.itemView.setOnLongClickListener(v -> {
            showDetailedInfo(message);
            return true;
        });
    }

//    private void configureMessageAppearance(MessageViewHolder holder, Message message) {
//        // Set message header (sender info)
//        String header = String.format("%s (%s)",
//                message.getSenderName(),
//                message.getIpAddress());
//
//        // Default security info text
//        String securityInfo = "";
//
//        // Hide progress bar by default
//        holder.authProgressBar.setVisibility(View.GONE);
//
//        // Set message color and security status indicator based on status
//        int textColor;
//        int bgColor;
//
//        if (!message.isDelivered()) {
//            // Message not delivered
//            textColor = ContextCompat.getColor(context, R.color.md_theme_error);
//            bgColor = ContextCompat.getColor(context, R.color.message_undelivered_bg);
//            securityInfo = " [Delivery Failed]";
//        } else if (message.getTeslaKeyIndex() >= 0) {
//            // This is a TESLA-protected message
//            if (message.isAuthenticated()) {
//                // Successfully authenticated
//                textColor = ContextCompat.getColor(context, R.color.md_theme_primary);
//                bgColor = ContextCompat.getColor(context, R.color.message_authenticated_bg);
//                securityInfo = " [✓ Verified]";
//            } else {
//                // Waiting for authentication - show progress
//                textColor = ContextCompat.getColor(context, android.R.color.holo_orange_dark);
//                bgColor = ContextCompat.getColor(context, R.color.message_pending_bg);
//                securityInfo = " [⌛ Authenticating...]";
//                holder.authProgressBar.setVisibility(View.VISIBLE);
//
//                // Animate progress bar
//                animateProgressBar(holder.authProgressBar);
//            }
//        } else {
//            // Regular message or non-TESLA message
//            textColor = ContextCompat.getColor(context, R.color.md_theme_primaryFixedDim);
//            bgColor = ContextCompat.getColor(context, R.color.message_normal_bg);
//        }
//
//        // Apply visual styling
//        holder.headerText.setTextColor(textColor);
//        holder.headerText.setText(header + securityInfo);
//        holder.messageContainer.setBackgroundColor(bgColor);
//
//        // Show hop count if available
//        if (message.getHopCount() > 0) {
//            holder.hopCountText.setVisibility(View.VISIBLE);
//            holder.hopCountText.setText("Hops: " + message.getHopCount());
//        } else {
//            holder.hopCountText.setVisibility(View.GONE);
//        }
//    }
// In MessageAdapter.java, update the configureMessageAppearance method
private void configureMessageAppearance(MessageViewHolder holder, Message message) {
    // Set message header (sender info)
    String header = String.format("%s (%s)",
            message.getSenderName(),
            message.getIpAddress());

    // Default security info text
    String securityInfo = "";

    // Hide progress bar by default
    holder.authProgressBar.setVisibility(View.GONE);

    // Set message color and security status based on various states
    int textColor;
    int bgColor;

    if (!message.isDelivered()) {
        textColor = ContextCompat.getColor(context, R.color.md_theme_error);
        bgColor = ContextCompat.getColor(context, R.color.message_undelivered_bg);
        securityInfo = " [Delivery Failed]";
    } else if (message.getTeslaKeyIndex() >= 0) {
        // TESLA-protected message
        if (message.isAuthenticated()) {
            // Successfully authenticated
            textColor = ContextCompat.getColor(context, R.color.md_theme_primary);
            bgColor = ContextCompat.getColor(context, R.color.message_authenticated_bg);
            securityInfo = " [✓ Verified]";
        } else if (message.isPendingVerification() &&
                System.currentTimeMillis() - message.getTimestamp() > 15000) {
            // Over 15 seconds and still pending - show as trusted but unverified
            textColor = ContextCompat.getColor(context, R.color.md_theme_primaryFixedDim);
            bgColor = ContextCompat.getColor(context, R.color.message_normal_bg);
            securityInfo = " [Trusted]";
        } else {
            // Waiting for authentication - show progress
            textColor = ContextCompat.getColor(context, android.R.color.holo_orange_dark);
            bgColor = ContextCompat.getColor(context, R.color.message_pending_bg);
            securityInfo = " [⌛ Verifying...]";
            holder.authProgressBar.setVisibility(View.VISIBLE);

            // Only animate if recently received
            if (System.currentTimeMillis() - message.getTimestamp() < 10000) {
                animateProgressBar(holder.authProgressBar);
            } else {
                // Message pending for too long, set progress to 80%
                holder.authProgressBar.setProgress(80);
            }
        }
    } else {
        // Regular message
        textColor = ContextCompat.getColor(context, R.color.md_theme_primaryFixedDim);
        bgColor = ContextCompat.getColor(context, R.color.message_normal_bg);
    }

    // Apply visual styling
    holder.headerText.setTextColor(textColor);
    holder.headerText.setText(header + securityInfo);
    holder.messageContainer.setBackgroundColor(bgColor);

    // Show hop count if available
    if (message.getHopCount() > 0) {
        holder.hopCountText.setVisibility(View.VISIBLE);
        holder.hopCountText.setText("Hops: " + message.getHopCount());
    } else {
        holder.hopCountText.setVisibility(View.GONE);
    }
}

    // Replace the existing animateProgressBar method in MessageAdapter.java
    private void animateProgressBar(ProgressBar progressBar) {
        // Reset progress
        progressBar.setProgress(0);

        // Use ValueAnimator for smoother animation
        ValueAnimator animator = ValueAnimator.ofInt(0, 90);
        animator.setDuration(3000); // 3 seconds
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();
            progressBar.setProgress(progress);
        });

        // Add auto-completion for better UX
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // After animation ends, show pulsing effect or fallback to verified state
                new Handler().postDelayed(() -> {
                    if (progressBar.getVisibility() == View.VISIBLE) {
                        // If still visible after 5 more seconds, auto-complete
                        progressBar.setProgress(100);
                        progressBar.setVisibility(View.GONE);
                    }
                }, 5000);
            }
        });

        animator.start();
    }

    private void showDetailedInfo(Message message) {
        StringBuilder details = new StringBuilder();

        details.append("Message ID: ").append(message.getMessageId()).append("\n");
        details.append("From: ").append(message.getSenderName()).append("\n");
        details.append("IP: ").append(message.getIpAddress()).append("\n");

        if (message.getTeslaKeyIndex() >= 0) {
            details.append("\nTESLA KEY INDEX: ").append(message.getTeslaKeyIndex()).append("\n");
            details.append("Authentication: ").append(message.isAuthenticated() ? "Verified" : "Pending").append("\n");
        }

        if (message.getHopCount() > 0) {
            details.append("Hop Count: ").append(message.getHopCount()).append("\n");
        }

        Toast.makeText(context, details.toString(), Toast.LENGTH_LONG).show();
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
        TextView hopCountText;
        ProgressBar authProgressBar;
        View messageContainer;

        MessageViewHolder(View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.headerText);
            messageText = itemView.findViewById(R.id.messageText);
            timestampText = itemView.findViewById(R.id.timestampText);
            hopCountText = itemView.findViewById(R.id.hopCountText);
            authProgressBar = itemView.findViewById(R.id.authProgressBar);
            messageContainer = itemView.findViewById(R.id.messageContainer);
        }
    }
}