package com.pofti.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;
import java.util.Locale;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

@CapacitorPlugin(name = "PoftiNative", permissions = { @Permission(strings = { Manifest.permission.RECORD_AUDIO }, alias = "mic"), @Permission(strings = { Manifest.permission.READ_CONTACTS }, alias = "contacts") })
public class PoftiPlugin extends Plugin {
  private TextToSpeech tts;
  private boolean ttsOk = false, torchOn = false;
  private PluginCall speakCall;
  private SpeechService ss;
  private PluginCall listenCall;
  private String pendingData = "";

  @Override public void load() {
    PoftiVosk.get(getContext(), m -> {}, e -> {});
    tts = new TextToSpeech(getContext(), st -> {
      if (st == TextToSpeech.SUCCESS) {
        tts.setLanguage(new Locale("tr", "TR"));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
          public void onStart(String id) {}
          public void onDone(String id) { finishSpeak(false); }
          public void onError(String id) { finishSpeak(false); }
        });
        ttsOk = true;
      }
    });
  }

  private synchronized void finishSpeak(boolean interrupted) {
    if (speakCall != null) {
      JSObject o = new JSObject();
      o.put("interrupted", interrupted);
      speakCall.resolve(o);
      speakCall = null;
    }
  }

  @PluginMethod public void speak(PluginCall call) {
    finishSpeak(true);
    if (!ttsOk) { call.resolve(); return; }
    speakCall = call;
    Bundle p = new Bundle();
    p.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, call.getFloat("volume", 1f));
    tts.setSpeechRate(call.getFloat("rate", 1f));
    tts.setPitch(call.getFloat("pitch", 1.1f));
    String vn = call.getString("voice", "");
    if (vn != null && !vn.isEmpty()) { try { for (android.speech.tts.Voice x : tts.getVoices()) if (x.getName().equals(vn)) { tts.setVoice(x); break; } } catch (Exception e) {} }
    tts.speak(call.getString("text", ""), TextToSpeech.QUEUE_FLUSH, p, "p" + System.nanoTime());
  }

  @PluginMethod public void listen(PluginCall call) {
    if (getPermissionState("mic") != PermissionState.GRANTED) { requestPermissionForAlias("mic", call, "micCb"); return; }
    startListen(call);
  }

  @PermissionCallback private void micCb(PluginCall call) {
    if (getPermissionState("mic") == PermissionState.GRANTED) startListen(call); else call.reject("izin");
  }

  private void stopSs() { if (ss != null) { try { ss.stop(); ss.shutdown(); } catch (Exception e) {} ss = null; } }

  private void finishListen(PluginCall call, String text, int err) {
    if (listenCall != call) return;
    listenCall = null; stopSs();
    JSObject o = new JSObject(); o.put("text", text); if (err != 0) o.put("error", err);
    call.resolve(o);
  }

  private String voskText(String h) { try { return new org.json.JSONObject(h).optString("text", "").trim(); } catch (Exception e) { return ""; } }

  private void startListen(PluginCall call) {
    getActivity().runOnUiThread(() -> {
      cancelListen();
      listenCall = call;
      PoftiVosk.get(getContext(), m -> {
        if (listenCall != call) return;
        try {
          ss = new SpeechService(new Recognizer(m, 16000.0f), 16000.0f);
          ss.startListening(new RecognitionListener() {
            public void onPartialResult(String h) {}
            public void onResult(String h) { String t = voskText(h); if (!t.isEmpty()) finishListen(call, t, 0); }
            public void onFinalResult(String h) { String t = voskText(h); if (!t.isEmpty()) finishListen(call, t, 0); }
            public void onError(Exception e) { finishListen(call, "", 5); }
            public void onTimeout() { finishListen(call, "", 6); }
          }, 9000);
        } catch (Exception e) { finishListen(call, "", 5); }
      }, e -> finishListen(call, "", 21));
    });
  }

  private boolean start(Intent i) {
    try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getContext().startActivity(i); return true; } catch (Exception e) { return false; }
  }

  @PluginMethod public void openUri(PluginCall c) {
    JSObject o = new JSObject(); o.put("ok", start(new Intent(Intent.ACTION_VIEW, Uri.parse(c.getString("uri", ""))))); c.resolve(o);
  }

  @PluginMethod public void openSettings(PluginCall c) {
    Intent i = new Intent(c.getString("action", "android.settings.SETTINGS")); String d = c.getString("data"); if (d != null) i.setData(Uri.parse(d)); JSObject o = new JSObject(); o.put("ok", start(i)); c.resolve(o);
  }

  @PluginMethod public void openApp(PluginCall c) {
    String name = c.getString("name", "").toLowerCase(new Locale("tr", "TR"));
    PackageManager pm = getContext().getPackageManager();
    Intent main = new Intent(Intent.ACTION_MAIN, null);
    main.addCategory(Intent.CATEGORY_LAUNCHER);
    JSObject o = new JSObject();
    o.put("opened", "");
    for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
      String label = ri.loadLabel(pm).toString();
      if (label.toLowerCase(new Locale("tr", "TR")).contains(name)) {
        Intent i = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
        if (i != null && start(i)) { o.put("opened", label); break; }
      }
    }
    c.resolve(o);
  }

  @PluginMethod public void setVolume(PluginCall c) {
    AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * c.getInt("percent", 50) / 100f), AudioManager.FLAG_SHOW_UI);
    c.resolve();
  }

  @PluginMethod public void torch(PluginCall c) {
    try {
      CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
      torchOn = c.getBoolean("on", !torchOn);
      cm.setTorchMode(cm.getCameraIdList()[0], torchOn);
      c.resolve();
    } catch (Exception e) { c.reject("fener"); }
  }

  private void cancelListen() {
    PluginCall c = listenCall; listenCall = null;
    if (c != null) { stopSs(); JSObject o = new JSObject(); o.put("text", ""); o.put("cancelled", true); c.resolve(o); }
  }

  @PluginMethod public void stopListen(PluginCall c) { getActivity().runOnUiThread(() -> { cancelListen(); c.resolve(); }); }

  @PluginMethod public void wakeStart(PluginCall c) {
    try { ContextCompat.startForegroundService(getContext(), new Intent(getContext(), PoftiWake.class)); c.resolve(); } catch (Exception e) { c.reject("servis"); }
  }

  @PluginMethod public void wakeStop(PluginCall c) { getContext().stopService(new Intent(getContext(), PoftiWake.class)); c.resolve(); }

  @PluginMethod public void takeWake(PluginCall c) {
    JSObject o = new JSObject(); o.put("text", PoftiWake.pending == null ? "" : PoftiWake.pending); PoftiWake.pending = null; c.resolve(o);
  }

  @PluginMethod public void permStatus(PluginCall c) {
    Context x = getContext(); String p = x.getPackageName(); JSObject o = new JSObject();
    o.put("mic", ContextCompat.checkSelfPermission(x, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    o.put("access", PoftiService.inst != null);
    o.put("overlay", android.provider.Settings.canDrawOverlays(x));
    o.put("battery", ((android.os.PowerManager) x.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(p));
    o.put("notif", androidx.core.app.NotificationManagerCompat.from(x).areNotificationsEnabled());
    String a = null;
    try { a = android.provider.Settings.Secure.getString(x.getContentResolver(), "assistant"); } catch (Exception e) {}
    o.put("assist", a != null && a.contains(p));
    o.put("wakesvc", PoftiWake.on);
    o.put("contacts", ContextCompat.checkSelfPermission(x, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED);
    o.put("notiflisten", androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(x).contains(p));
    c.resolve(o);
  }

  @PluginMethod public void micAsk(PluginCall c) {
    if (getPermissionState("mic") == PermissionState.GRANTED) { c.resolve(); return; }
    requestPermissionForAlias("mic", c, "micAskCb");
  }

  @PermissionCallback private void micAskCb(PluginCall c) { c.resolve(); }

  @PluginMethod public void voices(PluginCall c) {
    com.getcapacitor.JSArray a = new com.getcapacitor.JSArray();
    try { if (ttsOk) for (android.speech.tts.Voice x : tts.getVoices()) if ("tr".equals(x.getLocale().getLanguage())) a.put(x.getName()); } catch (Exception e) {}
    JSObject o = new JSObject(); o.put("list", a); c.resolve(o);
  }

  @PluginMethod public void heard(PluginCall c) {
    JSObject o = new JSObject(); o.put("text", getContext().getSharedPreferences("pofti_log", Context.MODE_PRIVATE).getString("h", "")); c.resolve(o);
  }

  @PluginMethod public void timer(PluginCall c) {
    Intent i = new Intent(android.provider.AlarmClock.ACTION_SET_TIMER).putExtra(android.provider.AlarmClock.EXTRA_LENGTH, c.getInt("seconds", 60)).putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, c.getString("label", "Pofti")).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true);
    JSObject o = new JSObject(); o.put("ok", start(i)); c.resolve(o);
  }

  @PluginMethod public void alarm(PluginCall c) {
    Intent i = new Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_HOUR, c.getInt("hour", 7)).putExtra(android.provider.AlarmClock.EXTRA_MINUTES, c.getInt("minute", 0)).putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, c.getString("label", "Pofti")).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true);
    JSObject o = new JSObject(); o.put("ok", start(i)); c.resolve(o);
  }

  @PluginMethod public void media(PluginCall c) {
    String a = c.getString("a", "");
    int k = a.equals("next") ? android.view.KeyEvent.KEYCODE_MEDIA_NEXT : a.equals("prev") ? android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS : a.equals("pause") ? android.view.KeyEvent.KEYCODE_MEDIA_PAUSE : a.equals("play") ? android.view.KeyEvent.KEYCODE_MEDIA_PLAY : android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
    AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, k));
    am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, k));
    c.resolve();
  }

  @PluginMethod public void volumeStep(PluginCall c) {
    AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, c.getBoolean("up", true) ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
    c.resolve();
  }

  @PluginMethod public void setPref(PluginCall c) {
    getContext().getSharedPreferences("pofti_prefs", Context.MODE_PRIVATE).edit().putBoolean(c.getString("key", "x"), c.getBoolean("value", false)).apply();
    if ("sleep".equals(c.getString("key", ""))) PoftiWidget.refresh(getContext());
    c.resolve();
  }

  @PluginMethod public void contactsAsk(PluginCall c) {
    if (getPermissionState("contacts") == PermissionState.GRANTED) { c.resolve(); return; }
    requestPermissionForAlias("contacts", c, "contactsAskCb");
  }

  @PermissionCallback private void contactsAskCb(PluginCall c) { c.resolve(); }

  @PluginMethod public void contact(PluginCall c) {
    if (getPermissionState("contacts") != PermissionState.GRANTED) { requestPermissionForAlias("contacts", c, "contactFlow"); return; }
    findContact(c);
  }

  @PermissionCallback private void contactFlow(PluginCall c) {
    if (getPermissionState("contacts") == PermissionState.GRANTED) findContact(c); else c.reject("izin");
  }

  private void findContact(PluginCall c) {
    JSObject o = new JSObject(); o.put("number", "");
    android.net.Uri u = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
    try (android.database.Cursor cur = getContext().getContentResolver().query(u, new String[] { android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER }, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", new String[] { "%" + c.getString("name", "") + "%" }, null)) {
      if (cur != null && cur.moveToFirst()) { o.put("name", cur.getString(0)); o.put("number", cur.getString(1)); }
    } catch (Exception e) {}
    c.resolve(o);
  }

  @PluginMethod public void widgetAdd(PluginCall c) {
    boolean ok = false;
    try {
      android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(getContext());
      if (android.os.Build.VERSION.SDK_INT >= 26 && m.isRequestPinAppWidgetSupported()) ok = m.requestPinAppWidget(new android.content.ComponentName(getContext(), PoftiWidget.class), null, null);
    } catch (Exception e) {}
    JSObject o = new JSObject(); o.put("ok", ok); c.resolve(o);
  }

  @PluginMethod public void backupSave(PluginCall c) {
    pendingData = c.getString("data", "");
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "pofti-yedek.json");
    startActivityForResult(c, i, "backupSaveCb");
  }

  @ActivityCallback private void backupSaveCb(PluginCall c, androidx.activity.result.ActivityResult r) {
    JSObject o = new JSObject(); o.put("ok", false);
    if (r.getResultCode() == android.app.Activity.RESULT_OK && r.getData() != null && r.getData().getData() != null) {
      try (java.io.OutputStream os = getContext().getContentResolver().openOutputStream(r.getData().getData())) {
        os.write(pendingData.getBytes("UTF-8")); o.put("ok", true);
      } catch (Exception e) {}
    }
    c.resolve(o);
  }

  @PluginMethod public void backupLoad(PluginCall c) {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
    startActivityForResult(c, i, "backupLoadCb");
  }

  @ActivityCallback private void backupLoadCb(PluginCall c, androidx.activity.result.ActivityResult r) {
    JSObject o = new JSObject(); o.put("text", "");
    if (r.getResultCode() == android.app.Activity.RESULT_OK && r.getData() != null && r.getData().getData() != null) {
      try (java.io.InputStream is = getContext().getContentResolver().openInputStream(r.getData().getData())) {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] b = new byte[4096]; int n;
        while ((n = is.read(b)) > 0) bo.write(b, 0, n);
        o.put("text", bo.toString("UTF-8"));
      } catch (Exception e) {}
    }
    c.resolve(o);
  }

  @PluginMethod public void closeOverlay(PluginCall c) { getActivity().runOnUiThread(() -> getActivity().finish()); c.resolve(); }

  @PluginMethod public void accessOn(PluginCall c) {
    JSObject o = new JSObject(); o.put("on", PoftiService.inst != null); c.resolve(o);
  }

  @PluginMethod public void action(PluginCall c) {
    JSObject o = new JSObject();
    PoftiService s = PoftiService.inst;
    o.put("ok", s != null && s.run(c.getString("a", ""), c.getString("text", "")));
    c.resolve(o);
  }
}
