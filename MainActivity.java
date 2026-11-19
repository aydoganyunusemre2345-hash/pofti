package com.pofti.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  public static boolean front = false;
  @Override public void onCreate(Bundle b) {
    registerPlugin(PoftiPlugin.class);
    super.onCreate(b);
  }
  @Override public void onResume() { super.onResume(); front = true; PoftiWake.release(); }
  @Override public void onPause() { super.onPause(); front = false; }
}
