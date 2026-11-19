package com.pofti.app;

import android.graphics.Color;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class OverlayActivity extends BridgeActivity {
  @Override public void onCreate(Bundle b) {
    registerPlugin(PoftiPlugin.class);
    super.onCreate(b);
    getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
    getBridge().getWebView().loadUrl("https://localhost/?overlay=1");
  }
  @Override public void onResume() { super.onResume(); MainActivity.front = true; PoftiWake.release(); }
  @Override public void onPause() { super.onPause(); MainActivity.front = false; }
}
