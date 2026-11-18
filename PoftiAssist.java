package com.pofti.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

public class PoftiAssist extends VoiceInteractionService {

  public static class SessSvc extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession(Bundle args) { return new Sess(this); }
  }

  public static class Sess extends VoiceInteractionSession {
    public Sess(Context c) { super(c); }
    @Override public void onShow(Bundle args, int flags) {
      super.onShow(args, flags);
      PoftiWake.pending = "hey pofti";
      Intent i = new Intent(getContext(), MainActivity.class);
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
      startAssistantActivity(i);
      hide();
    }
  }

  public static class Recog extends RecognitionService {
    private SpeechRecognizer g;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onStartListening(Intent intent, Callback cb) {
      h.post(() -> {
        if (g != null) g.destroy();
        g = PoftiWake.recognizer(this);
        g.setRecognitionListener(new RecognitionListener() {
          public void onReadyForSpeech(Bundle b) { try { cb.readyForSpeech(b); } catch (Exception e) {} }
          public void onBeginningOfSpeech() { try { cb.beginningOfSpeech(); } catch (Exception e) {} }
          public void onRmsChanged(float v) { try { cb.rmsChanged(v); } catch (Exception e) {} }
          public void onBufferReceived(byte[] b) { try { cb.bufferReceived(b); } catch (Exception e) {} }
          public void onEndOfSpeech() { try { cb.endOfSpeech(); } catch (Exception e) {} }
          public void onError(int e) { try { cb.error(e); } catch (Exception x) {} }
          public void onResults(Bundle b) { try { cb.results(b); } catch (Exception e) {} }
          public void onPartialResults(Bundle b) { try { cb.partialResults(b); } catch (Exception e) {} }
          public void onEvent(int t, Bundle b) {}
        });
        g.startListening(intent);
      });
    }
    @Override protected void onStopListening(Callback cb) { h.post(() -> { if (g != null) g.stopListening(); }); }
    @Override protected void onCancel(Callback cb) { h.post(() -> { if (g != null) g.cancel(); }); }
    @Override public void onDestroy() { h.post(() -> { if (g != null) { g.destroy(); g = null; } }); super.onDestroy(); }
  }
}
