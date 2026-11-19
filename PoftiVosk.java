package com.pofti.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import org.vosk.Model;
import org.vosk.android.StorageService;

public class PoftiVosk {
  private static Model model;
  private static boolean loading = false;

  public static synchronized void get(Context c, StorageService.Callback<Model> ok, StorageService.Callback<IOException> err) {
    if (model != null) { ok.onComplete(model); return; }
    if (loading) { new Handler(Looper.getMainLooper()).postDelayed(() -> get(c, ok, err), 500); return; }
    loading = true;
    StorageService.unpack(c.getApplicationContext(), "model-tr", "model", m -> { model = m; loading = false; ok.onComplete(m); }, e -> { loading = false; err.onComplete(e); });
  }
}
