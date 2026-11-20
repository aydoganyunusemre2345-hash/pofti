package com.pofti.app;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class PoftiNotif extends NotificationListenerService {
  private TextToSpeech tts;
  private boolean ok = false;
  private String lastKey = "";
  private long lastT = 0;

  @Override public void onCreate() {
    super.onCreate();
    tts = new TextToSpeech(this, st -> { if (st == TextToSpeech.SUCCESS) { tts.setLanguage(new Locale("tr", "TR")); ok = true; } });
  }

  @Override public void onDestroy() { if (tts != null) tts.shutdown(); super.onDestroy(); }

  @Override public void onNotificationPosted(StatusBarNotification sbn) {
    if (!ok || !getSharedPreferences("pofti_prefs", MODE_PRIVATE).getBoolean("notif", false)) return;
    String pkg = sbn.getPackageName();
    if (pkg.equals(getPackageName())) return;
    if (!(pkg.startsWith("com.whatsapp") || pkg.equals("org.telegram.messenger") || pkg.contains("messaging") || pkg.contains("mms"))) return;
    Notification n = sbn.getNotification();
    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
    Bundle e = n.extras;
    CharSequence ti = e.getCharSequence(Notification.EXTRA_TITLE), tx = e.getCharSequence(Notification.EXTRA_TEXT);
    if (ti == null || tx == null) return;
    long now = System.currentTimeMillis();
    String key = ti + "|" + tx;
    if (key.equals(lastKey) && now - lastT < 5000) return;
    lastKey = key; lastT = now;
    String app = pkg.startsWith("com.whatsapp") ? "WhatsApp" : pkg.contains("telegram") ? "Telegram" : "Mesaj";
    tts.speak(app + "'tan " + ti + " yazdı: " + tx, TextToSpeech.QUEUE_ADD, null, "n" + now);
  }
}
