package com.pofti.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
  public static volatile boolean on = false;
  private static PoftiWake inst;
  private static final Pattern WAKE = Pattern.compile("\\b[pb][oö][fv]\\w*");
  private boolean running = false, loading = false;
  private long lastWake = 0;
  private SpeechService ss;
  private final Handler h = new Handler(Looper.getMainLooper());

  private static String norm(String s) {
    StringBuilder b = new StringBuilder();
    for (char c : s.toLowerCase(new Locale("tr", "TR")).toCharArray()) {
      if (!Character.isLetter(c)) continue;
      if (c == 'ğ') continue;
      if (c == 'b') c = 'p'; else if (c == 'v') c = 'f'; else if (c == 'ö' || c == 'u' || c == 'ü') c = 'o'; else if (c == 'd') c = 't'; else if (c == 'ı') c = 'i'; else if (c == 'ş') c = 's';
      b.append(c);
    }
    return b.toString();
  }

  private static int lev(String a, String b) {
    int[][] d = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) d[i][0] = i;
    for (int j = 0; j <= b.length(); j++) d[0][j] = j;
    for (int i = 1; i <= a.length(); i++)
      for (int j = 1; j <= b.length(); j++)
        d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
    return d[a.length()][b.length()];
  }

  public static boolean isWake(String t) { return isWake(t, true); }

  public static boolean isWake(String t, boolean fin) {
    for (String w : t.trim().split("\\s+")) {
      String n = norm(w);
      if (n.length() >= 3 && (n.startsWith("pof") || lev(n, "pofti") <= 1)) return true;
    }
    String j = norm(t);
    if (j.length() <= 12)
      for (int len = 4; len <= 6; len++)
        for (int i = 0; i + len <= j.length(); i++)
          if (lev(j.substring(i, i + len), "pofti") <= 1) return true;
    String[] w = t.trim().split("\\s+");
    if (fin && w.length == 1 && lev(norm(w[0]), "hey") <= 1) return true;
    for (int i = 0; i + 1 < w.length; i++)
      if (lev(norm(w[i]), "hey") <= 1) { String n = norm(w[i + 1]); if (n.length() >= 3 && lev(n, "pofti") <= 3) return true; }
    return false;
  }

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
    inst = this; on = true;
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.createNotificationChannel(new NotificationChannel("pofti_wake", "Pofti dinliyor", NotificationManager.IMPORTANCE_LOW));
    Notification n = new Notification.Builder(this, "pofti_wake").setContentTitle("Pofti seni dinliyor").setContentText("Hey Pofti de").setSmallIcon(android.R.drawable.ic_btn_speak_now).build();
    if (Build.VERSION.SDK_INT >= 30) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE); else startForeground(7, n);
    if (!running) { running = true; h.post(this::tick); }
    return START_STICKY;
  }

  @Override public void onDestroy() {
    running = false; on = false; inst = null; h.removeCallbacksAndMessages(null); stopSs();
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

  private void logHeard(String t) {
    SharedPreferences p = getSharedPreferences("pofti_log", MODE_PRIVATE);
    String old = p.getString("h", "");
    String[] l = old.isEmpty() ? new String[0] : old.split("\n");
    StringBuilder sb = new StringBuilder();
    for (int i = Math.max(0, l.length - 7); i < l.length; i++) sb.append(l[i]).append("\n");
    sb.append(new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date())).append("  ").append(t);
    p.edit().putString("h", sb.toString()).apply();
  }

  private void check(String x, String key) {
    String t;
    try { t = new JSONObject(x).optString(key, "").toLowerCase(new Locale("tr", "TR")); } catch (Exception e) { return; }
    if (t.isEmpty()) return;
    if (key.equals("text")) logHeard(t);
    if (System.currentTimeMillis() - lastWake < 5000 || !isWake(t, key.equals("text"))) return;
    lastWake = System.currentTimeMillis(); pending = t;
    stopSs();
    Intent i = new Intent(this, OverlayActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    try { startActivity(i); } catch (Exception e) {}
  }
}
