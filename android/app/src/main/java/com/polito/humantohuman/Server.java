package com.polito.humantohuman;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import com.android.volley.*;
import com.android.volley.toolbox.*;
import com.polito.humantohuman.utils.Polyfill;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Server extends Service {
  private static final String SEND_CHANNEL_ID = "HumanToHumanSending";
  private static final String SEND_CHANNEL_NAME = "HumanToHuman Sending";
  private static final int SEND_FOREGROUND_ID = 3;
  public static Polyfill.Supplier<ArrayList<Database.Row>> supplier;
  public static Listener<JSONObject> listener;
  public static RequestQueue requestQueue;

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Polyfill.startForeground(this, SEND_FOREGROUND_ID, SEND_CHANNEL_ID,
                               SEND_CHANNEL_NAME);
    } else {
      startForeground(SEND_FOREGROUND_ID, new Notification());
    }

    Handler handler = new Handler();

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (AppLogic.getAppState() == AppLogic.APPSTATE_NO_EXPERIMENT ||
            AppLogic.getAppState() == AppLogic.APPSTATE_LOGGING_IN)
          return;

        if (!AppLogic.shouldUpload()) {
          handler.postDelayed(this, 1000 * 60);
          return;
        }

        ArrayList<Database.Row> rows = supplier.get();
        if (rows == null) {
          if (AppLogic.getAppState() !=
              AppLogic.APPSTATE_EXPERIMENT_RUNNING_NOT_COLLECTING)
            handler.postDelayed(this, 1000 * 60);
          return;
        }

        System.err.println("Sending data to server...");
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST, AppLogic.getServerURL() + "/addConnections",
            serializeRows(rows),
            (response)
                -> {
              listener.onFinish(response, null);
              handler.postDelayed(this, 1000 * 60);
            },
            (error) -> {
              listener.onFinish(null, error);
              handler.postDelayed(this, 1000 * 60);
            });

        requestQueue.add(request);
      }
    };

    handler.postDelayed(runnable, 0);

    return Service.START_NOT_STICKY;
  }

  public interface Listener<T> { void onFinish(T data, VolleyError error); }
  public interface IDTokenListener {
    void onFinish(Long id, String token, Exception error);
  }

  public static void initializeServer(Context ctx) {
    if (requestQueue != null)
      return;
    requestQueue = Volley.newRequestQueue(ctx);
    requestQueue.start();
  }

  public static JSONObject serializeRows(ArrayList<Database.Row> rows) {
    SimpleDateFormat format =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.US);
    format.setTimeZone(TimeZone.getDefault());

    try {
      JSONArray jsonArray = new JSONArray();
      for (Database.Row row : rows) {
        jsonArray.put(new JSONObject()
                          .put("other", row.id)
                          .put("time", format.format(row.date))
                          .put("power", row.power)
                          .put("rssi", row.rssi));
      }

      return new JSONObject()
          .put("id", AppLogic.getBluetoothID())
          .put("token", AppLogic.getToken())
          .put("connections", jsonArray);
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  public static void getId(IDTokenListener l) {
    JsonObjectRequest req = new JsonObjectRequest(
        Request.Method.POST, AppLogic.getServerURL() + "/addUser",
        new JSONObject(), (response) -> {
          try {
            l.onFinish(response.getLong("id"), response.getString("token"),
                       null);
          } catch (JSONException e) {
            l.onFinish(null, null, e);
          }
        }, (error) -> l.onFinish(null, null, error));

    requestQueue.add(req);
  }

  public static void getPrivacyPolicy(Listener<String> l) {
    StringRequest req = new StringRequest(Request.Method.GET,
                                          AppLogic.getServerURL() + "/policy",
                                          (response)
                                              -> l.onFinish(response, null),
                                          (error) -> l.onFinish(null, error));
    requestQueue.add(req);
  }

  public static void getDescription(Listener<String> l) {
    StringRequest req = new StringRequest(
        Request.Method.GET, AppLogic.getServerURL() + "/description",
        (response)
            -> l.onFinish(response, null),
        (error) -> l.onFinish(null, error));
    requestQueue.add(req);
  }
}
