package com.example.accessibilityhighlightservice;


import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class AccessibilityHighlightService extends AccessibilityService {
    private WindowManager windowManager;
    private List<AccessibilityNodeInfo> clickableNodesInPage = new ArrayList<>();
    private Stack<View> activeViews = new Stack<>();
    private String previouslyOpenApp = "com.android.launcher";

    protected void onServiceConnected() {
        Log.i("DEBUG_LOG", "Service connected");
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED |
                AccessibilityEvent.TYPE_VIEW_FOCUSED |
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        this.setServiceInfo(info);
        if (Settings.canDrawOverlays(this)) {
            Log.i("DEBUG_LOG", "Overlay Permission Present");
            removeOverlays();
            windowManager = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
            clickableNodesInPage.clear();
        } else {
            try {
                requestOverlayPermission();
            } catch (Exception e) {
                Log.e("DEBUG_LOG", e.toString());
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.i("DEBUG_LOG", "onAccessibilityEvent has been called");
        try {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {

                AccessibilityNodeInfo rootNode = event.getSource();

                if (rootNode != null) {

                    //Remove highlights when apps are switched
                    String currentApp = rootNode.getPackageName().toString();
                    Log.i("DEBUG_LOG current app", currentApp);
                    if (!currentApp.equals(previouslyOpenApp)) {
                        removeOverlays();
                        previouslyOpenApp = currentApp;
                    }

                    traverseAndHighlightClickableElements(rootNode);
                    rootNode.recycle();
                }
                Log.i("DEBUG_LOG", clickableNodesInPage.toString());
            }
        } catch (Exception e) {
            Log.i("DEBUG_LOG", "Error while fetching root node");
            Log.e("DEBUG_LOG", e.toString());
        }
    }

    private void traverseAndHighlightClickableElements(AccessibilityNodeInfo node) {
        if (node == null) {
            return;
        }

        // Check if the node is clickable or long clickable
        // If required, can also highlight focusable, editable, etc. nodes
        if (node.isClickable() || node.isLongClickable()) {
            Log.i("DEBUG_LOG", "Node is clickable");
            try {
                overlayNode(node);
                String nodeString = node.toString();
                Log.i("DEBUG_LOG", nodeString);
            } catch (Exception e) {
                Log.e("DEBUG_LOG", "Error while calling highlighting service");
                Log.e("DEBUG_LOG", e.toString());
            }
        }

        // Checking child nodes for clickability
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            if (childNode != null) {
                traverseAndHighlightClickableElements(childNode);
                childNode.recycle();
            }
        }
    }


    public void overlayNode(AccessibilityNodeInfo nodeInfo) {
        Log.i("DEBUG_LOG", "highlighting function called");
        Rect bounds = new Rect();
        nodeInfo.getBoundsInScreen(bounds);

        // Create a yellow rectangle highlight
        GradientDrawable rectangle = new GradientDrawable();
        rectangle.setColor(0xFFFFD700);

        //translucency
        rectangle.setAlpha(100);

        rectangle.setShape(GradientDrawable.RECTANGLE);

        View overlayView = new View(this);
        overlayView.setBackground(rectangle);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                bounds.width(),
                bounds.height(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.START | Gravity.TOP;
        params.x = bounds.left;
        params.y = bounds.top - bounds.height() / 2;
        windowManager.addView(overlayView, params);

        //Store active views to remove them when screen changes
        activeViews.push(overlayView);
    }

    private void removeOverlays() {
        Log.i("DEBUG_LOG", "removing overlays");
        while (!activeViews.empty()) {
            windowManager.removeView(activeViews.lastElement());
            activeViews.pop();
        }
    }


    @Override
    public void onInterrupt() {
        Log.i("DEBUG_LOG", "onInterrupt has been called");
        Log.i("DEBUG_LOG", "removing old overlays");
        removeOverlays();
        this.disableSelf();
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
