package com.pofti.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class PoftiService extends AccessibilityService {
  public static PoftiService inst;
  @Override protected void onServiceConnected() { inst = this; }
  @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
  @Override public void onInterrupt() {}
  @Override public boolean onUnbind(Intent i) { inst = null; return super.onUnbind(i); }

  public boolean run(String a, String t) {
    switch (a) {
      case "back": return performGlobalAction(GLOBAL_ACTION_BACK);
      case "home": return performGlobalAction(GLOBAL_ACTION_HOME);
      case "recents": return performGlobalAction(GLOBAL_ACTION_RECENTS);
      case "notifications": return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
      case "quick": return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
      case "up": return swipe(0.5f, 0.3f, 0.5f, 0.7f);
      case "down": return swipe(0.5f, 0.7f, 0.5f, 0.3f);
      case "click": return clickText(t);
    }
    return false;
  }

  private boolean swipe(float x1, float y1, float x2, float y2) {
    DisplayMetrics d = getResources().getDisplayMetrics();
    Path p = new Path();
    p.moveTo(d.widthPixels * x1, d.heightPixels * y1);
    p.lineTo(d.widthPixels * x2, d.heightPixels * y2);
    return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p, 0, 350)).build(), null, null);
  }

  private boolean clickText(String t) {
    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root == null) return false;
    List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByText(t);
    for (AccessibilityNodeInfo n : l) {
      AccessibilityNodeInfo c = n;
      while (c != null && !c.isClickable()) c = c.getParent();
      if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
    }
    return false;
  }
}
