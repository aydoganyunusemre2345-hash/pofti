package com.pofti.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import java.util.Calendar;

public class PoftiWidget extends AppWidgetProvider {
  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
    for (int id : ids) m.updateAppWidget(id, build(c));
  }

  static boolean sleeping(Context c) {
    boolean pref = c.getSharedPreferences("pofti_prefs", Context.MODE_PRIVATE).getBoolean("sleep", false);
    int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    return pref || h >= 23 || h < 7;
  }

  static RemoteViews build(Context c) {
    RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.pofti_widget);
    boolean s = sleeping(c);
    v.setViewVisibility(R.id.pw_awake, s ? View.GONE : View.VISIBLE);
    v.setViewVisibility(R.id.pw_sleep, s ? View.VISIBLE : View.GONE);
    Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    v.setOnClickPendingIntent(R.id.pw_root, PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    return v;
  }

  public static void refresh(Context c) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    for (int id : m.getAppWidgetIds(new ComponentName(c, PoftiWidget.class))) m.updateAppWidget(id, build(c));
  }
}
