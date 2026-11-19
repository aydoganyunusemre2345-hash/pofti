package com.pofti.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

public class PoftiWake extends Service {
  public static volatile String pending = null;
  private static PoftiWake inst;
  private static final Pattern WAKE = Pattern.compile("\\b[pb][oö][fv]\\w*");
  private boolean running = false, loading = false;
  private long lastWake = 0;
  private SpeechService ss;
  private final Handler h = new Handler(Looper.getMainLooper());

  public static void release() { final PoftiWake w = inst; if (w != null) w.h.post(w::stopSs); }

  public static SpeechRecognizer recognizer(Context c) {
    try {
      String d = android.provider.Settings.Secure.getString(c.getContentResolver(), "voice_recognition_service");
      if (d != null && d.contains("com.pofti.app")) {
        ComponentName cn = new ComponentName("com.google.android.googlequicksearchbox", "com.google.android.voicesearch.serviceapi.GoogleRecognitionService");
        c.getPackageManager().getServiceInfo(cn, 0);
        return SpeechRecognizer.createSpeechRecognizer(c, cn);
      }
    } catch (Exception e) {}
    return SpeechRecognizer.createSpeechRecognizer(c);
  }

  @Override public IBinder onBind(Intent i) { return null; }

  @Override public int onStartCommand(Intent i, int f, int id) {
    inst = this;
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.createNotificationChannel(new NotificationChannel("pofti_wake", "Pofti dinliyor", NotificationManager.IMPORTANCE_LOW));
    Notification n = new Notification.Builder(this, "pofti_wake").setContentTitle("Pofti seni dinliyor").setContentText("Hey Pofti de").setSmallIcon(android.R.drawable.ic_btn_speak_now).build();
    if (Build.VERSION.SDK_INT >= 30) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE); else startForeground(7, n);
    if (!running) { running = true; h.post(this::tick); }
    return START_STICKY;
  }

  @Override public void onDestroy() {
    running = false; inst = null; h.removeCallbacksAndMessages(null); stopSs();
    super.onDestroy();
  }

  private void stopSs() { if (ss != null) { try { ss.stop(); ss.shutdown(); } catch (Exception e) {} ss = null; } }

  private void tick() {
    if (!running) return;
    if (MainActivity.front) stopSs(); else if (ss == null) startVosk();
    h.postDelayed(this::tick, 1500);
  }

  private void startVosk() {
    if (loading) return;
    loading = true;
    PoftiVosk.get(this, m -> {
      loading = false;
      if (!running || ss != null || MainActivity.front) return;
      try {
        ss = new SpeechService(new Recognizer(m, 16000.0f), 16000.0f);
        ss.startListening(new RecognitionListener() {
          public void onPartialResult(String x) { check(x, "partial"); }
          public void onResult(String x) { check(x, "text"); }
          public void onFinalResult(String x) { check(x, "text"); }
          public void onError(Exception e) { stopSs(); }
          public void onTimeout() {}
        });
      } catch (Exception e) { stopSs(); }
    }, e -> loading = false);
  }

  private void check(String x, String key) {
    String t;
    try { t = new JSONObject(x).optString(key, "").toLowerCase(new Locale("tr", "TR")); } catch (Exception e) { return; }
    if (t.isEmpty() || System.currentTimeMillis() - lastWake < 5000 || !WAKE.matcher(t).find()) return;
    lastWake = System.currentTimeMillis(); pending = t;
    stopSs();
    Intent i = new Intent(this, OverlayActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    try { startActivity(i); } catch (Exception e) {}
  }
}
