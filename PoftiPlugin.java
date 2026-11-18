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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;
import java.util.Locale;

@CapacitorPlugin(name = "PoftiNative", permissions = { @Permission(strings = { Manifest.permission.RECORD_AUDIO }, alias = "mic") })
public class PoftiPlugin extends Plugin {
  private TextToSpeech tts;
  private boolean ttsOk = false, torchOn = false;
  private PluginCall speakCall;
  private SpeechRecognizer sr;
  private PluginCall listenCall;

  @Override public void load() {
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
    tts.setPitch(1.1f);
    tts.speak(call.getString("text", ""), TextToSpeech.QUEUE_FLUSH, p, "p" + System.nanoTime());
  }

  @PluginMethod public void listen(PluginCall call) {
    if (getPermissionState("mic") != PermissionState.GRANTED) { requestPermissionForAlias("mic", call, "micCb"); return; }
    startListen(call);
  }

  @PermissionCallback private void micCb(PluginCall call) {
    if (getPermissionState("mic") == PermissionState.GRANTED) startListen(call); else call.reject("izin");
  }

  private void startListen(PluginCall call) {
    getActivity().runOnUiThread(() -> {
      cancelListen();
      listenCall = call;
      if (!SpeechRecognizer.isRecognitionAvailable(getContext())) { call.reject("servis yok"); return; }
      if (sr != null) sr.destroy();
      sr = PoftiWake.recognizer(getContext());
      sr.setRecognitionListener(new RecognitionListener() {
        public void onReadyForSpeech(Bundle b) {}
        public void onBeginningOfSpeech() {}
        public void onRmsChanged(float v) {}
        public void onBufferReceived(byte[] b) {}
        public void onEndOfSpeech() {}
        public void onPartialResults(Bundle b) {}
        public void onEvent(int t, Bundle b) {}
        public void onError(int e) { if (listenCall != call) return; listenCall = null; JSObject o = new JSObject(); o.put("text", ""); o.put("error", e); call.resolve(o); }
        public void onResults(Bundle b) {
          if (listenCall != call) return;
          listenCall = null;
          ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
          JSObject o = new JSObject();
          o.put("text", l != null && !l.isEmpty() ? l.get(0) : "");
          call.resolve(o);
        }
      });
      Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
      sr.startListening(i);
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
    if (c != null) { try { if (sr != null) sr.cancel(); } catch (Exception e) {} JSObject o = new JSObject(); o.put("text", ""); o.put("cancelled", true); c.resolve(o); }
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
    c.resolve(o);
  }

  @PluginMethod public void micAsk(PluginCall c) {
    if (getPermissionState("mic") == PermissionState.GRANTED) { c.resolve(); return; }
    requestPermissionForAlias("mic", c, "micAskCb");
  }

  @PermissionCallback private void micAskCb(PluginCall c) { c.resolve(); }

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
