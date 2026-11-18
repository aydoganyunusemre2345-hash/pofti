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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Locale;

public class PoftiWake extends Service {
  public static volatile String pending = null;
  private boolean running = false;
  private long lastWake = 0;
  private SpeechRecognizer sr;
  private final Handler h = new Handler(Looper.getMainLooper());

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
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.createNotificationChannel(new NotificationChannel("pofti_wake", "Pofti dinliyor", NotificationManager.IMPORTANCE_LOW));
    Notification n = new Notification.Builder(this, "pofti_wake").setContentTitle("Pofti seni dinliyor").setContentText("Hey Pofti de").setSmallIcon(android.R.drawable.ic_btn_speak_now).build();
    if (Build.VERSION.SDK_INT >= 30) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE); else startForeground(7, n);
    if (!running) { running = true; h.post(this::listen); }
    return START_STICKY;
  }

  @Override public void onDestroy() {
    running = false; h.removeCallbacksAndMessages(null);
    if (sr != null) { sr.destroy(); sr = null; }
    super.onDestroy();
  }

  private void listen() {
    if (!running) return;
    if (sr != null) { sr.destroy(); sr = null; }
    if (MainActivity.front) { h.postDelayed(this::listen, 1500); return; }
    sr = recognizer(this);
    sr.setRecognitionListener(new RecognitionListener() {
      public void onReadyForSpeech(Bundle b) {}
      public void onBeginningOfSpeech() {}
      public void onRmsChanged(float v) {}
      public void onBufferReceived(byte[] b) {}
      public void onEndOfSpeech() {}
      public void onEvent(int t, Bundle b) {}
      public void onError(int e) { h.postDelayed(PoftiWake.this::listen, 700); }
      public void onPartialResults(Bundle b) { check(b); }
      public void onResults(Bundle b) { check(b); h.postDelayed(PoftiWake.this::listen, 300); }
    });
    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
    i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    sr.startListening(i);
  }

  private void check(Bundle b) {
    ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    if (l == null || System.currentTimeMillis() - lastWake < 5000) return;
    for (String s : l) {
      String t = s.toLowerCase(new Locale("tr", "TR"));
      if (t.contains("pofti") || t.contains("bofti") || t.contains("pofi")) {
        lastWake = System.currentTimeMillis(); pending = t;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try { startActivity(i); } catch (Exception e) {}
        return;
      }
    }
  }
}
