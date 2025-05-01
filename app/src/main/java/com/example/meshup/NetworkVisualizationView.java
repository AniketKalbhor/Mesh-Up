package com.example.meshup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Custom view for visualizing the mesh network topology
 */
public class NetworkVisualizationView extends View {
    private static final String TAG = "NetworkVizView";

    // Node properties
    private static final float NODE_RADIUS = 40;
    private static final int SELF_COLOR = Color.parseColor("#4CAF50"); // Green
    private static final int CONNECTED_COLOR = Color.parseColor("#2196F3"); // Blue
    private static final int INDIRECT_COLOR = Color.parseColor("#FF9800"); // Orange
    private static final int INACTIVE_COLOR = Color.parseColor("#9E9E9E"); // Gray

    // Line properties
    private static final int DIRECT_LINE_COLOR = Color.parseColor("#42A5F5"); // Light Blue
    private static final int INDIRECT_LINE_COLOR = Color.parseColor("#FFB74D"); // Light Orange
    private static final float LINE_WIDTH = 5;

    // Data
    private Map<String, Node> nodes = new HashMap<>();
    private List<Connection> connections = new ArrayList<>();
    private String selfId;

    // Drawing objects
    private Paint nodePaint;
    private Paint textPaint;
    private Paint linePaint;
    private Paint backgroundPaint;
    private Path arrowPath;

    public NetworkVisualizationView(Context context) {
        super(context);
        init();
    }

    public NetworkVisualizationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Initialize paints
        nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nodePaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(26);
        textPaint.setTextAlign(Paint.Align.CENTER);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(LINE_WIDTH);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.parseColor("#212121")); // Dark background

        arrowPath = new Path();
    }

    /**
     * Set self device ID for highlighting
     */
    public void setSelfId(String deviceId) {
        this.selfId = deviceId;
        invalidate();
    }

    /**
     * Update the network visualization with the current devices
     */
    public void updateNetwork(Map<String, Long> deviceLastSeenTime, Set<String> directConnections) {
        nodes.clear();
        connections.clear();

        // Layout nodes in a circle
        int nodeCount = deviceLastSeenTime.size() + (deviceLastSeenTime.containsKey(selfId) ? 0 : 1);
        float radius = Math.min(getWidth(), getHeight()) * 0.4f; // 40% of the smaller dimension
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        // Add self node first at the center
        if (selfId != null) {
            nodes.put(selfId, new Node(selfId, "Me", centerX, centerY, SELF_COLOR));
        }

        // Layout other nodes in a circle around self
        int i = 0;
        for (String deviceId : deviceLastSeenTime.keySet()) {
            if (deviceId.equals(selfId)) continue;

            // Determine if device is active based on last seen time
            long lastSeen = deviceLastSeenTime.get(deviceId);
            boolean isActive = System.currentTimeMillis() - lastSeen < 60000; // 1 minute timeout

            // Calculate position in circle
            float angle = (float) (2 * Math.PI * i / (nodeCount - 1));
            float x = centerX + radius * (float) Math.cos(angle);
            float y = centerY + radius * (float) Math.sin(angle);

            // Determine color based on connectivity
            int color = isActive ?
                    (directConnections.contains(deviceId) ? CONNECTED_COLOR : INDIRECT_COLOR) :
                    INACTIVE_COLOR;

            // Add node
            String label = "Node " + (i + 1);
            nodes.put(deviceId, new Node(deviceId, label, x, y, color));
            i++;
        }

        // Add connections
        for (String deviceId : deviceLastSeenTime.keySet()) {
            // Connect self to all direct connections
            if (directConnections.contains(deviceId) && selfId != null) {
                connections.add(new Connection(selfId, deviceId, true));
            }

            // For demonstration, add random indirect connections
            // In a real app, these would be determined by routing discovery
            Random random = new Random(deviceId.hashCode());
            for (String otherDeviceId : deviceLastSeenTime.keySet()) {
                if (!deviceId.equals(otherDeviceId) && random.nextFloat() < 0.3f) {
                    connections.add(new Connection(deviceId, otherDeviceId, false));
                }
            }
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        // Draw connections
        for (Connection connection : connections) {
            Node source = nodes.get(connection.sourceId);
            Node target = nodes.get(connection.targetId);

            if (source != null && target != null) {
                // Set color based on connection type
                linePaint.setColor(connection.isDirect ? DIRECT_LINE_COLOR : INDIRECT_LINE_COLOR);

                // Draw line
                canvas.drawLine(source.x, source.y, target.x, target.y, linePaint);

                // Draw arrow
                drawArrow(canvas, source.x, source.y, target.x, target.y);
            }
        }

        // Draw nodes
        for (Node node : nodes.values()) {
            // Draw circle
            nodePaint.setColor(node.color);
            canvas.drawCircle(node.x, node.y, NODE_RADIUS, nodePaint);

            // Draw label
            canvas.drawText(node.label, node.x, node.y + textPaint.getTextSize() / 3, textPaint);
        }
    }

    private void drawArrow(Canvas canvas, float fromX, float fromY, float toX, float toY) {
        float deltaX = toX - fromX;
        float deltaY = toY - fromY;
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        // Calculate the point on the line where the arrow should be drawn
        float ratio = (distance - NODE_RADIUS) / distance;
        float arrowX = fromX + deltaX * ratio;
        float arrowY = fromY + deltaY * ratio;

        // Calculate the arrow points
        float angle = (float) Math.atan2(deltaY, deltaX);
        float arrowSize = 20;

        float x1 = arrowX - arrowSize * (float) Math.cos(angle - Math.PI / 6);
        float y1 = arrowY - arrowSize * (float) Math.sin(angle - Math.PI / 6);

        float x2 = arrowX - arrowSize * (float) Math.cos(angle + Math.PI / 6);
        float y2 = arrowY - arrowSize * (float) Math.sin(angle + Math.PI / 6);

        // Draw arrow
        arrowPath.reset();
        arrowPath.moveTo(arrowX, arrowY);
        arrowPath.lineTo(x1, y1);
        arrowPath.lineTo(x2, y2);
        arrowPath.close();

        nodePaint.setColor(linePaint.getColor());
        canvas.drawPath(arrowPath, nodePaint);
    }

    private static class Node {
        final String id;
        final String label;
        final float x;
        final float y;
        final int color;

        Node(String id, String label, float x, float y, int color) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    private static class Connection {
        final String sourceId;
        final String targetId;
        final boolean isDirect;

        Connection(String sourceId, String targetId, boolean isDirect) {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.isDirect = isDirect;
        }
    }
}