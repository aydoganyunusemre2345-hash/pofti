package com.pofti.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override public void onCreate(Bundle b) {
    registerPlugin(PoftiPlugin.class);
    super.onCreate(b);
  }
}
