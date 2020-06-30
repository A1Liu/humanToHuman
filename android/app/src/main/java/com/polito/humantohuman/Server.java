package com.polito.humantohuman;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import com.android.volley.*;
import com.android.volley.toolbox.*;
import com.polito.humantohuman.utils.Polyfill;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Server extends Service {
  private static final String SEND_CHANNEL_ID = "HumanToHuman Sending";
  public static Polyfill.Supplier<ArrayList<Database.Row>> supplier;
  public static Listener<JSONObject> listener;
  public static final SimpleDateFormat format =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ");
  public static RequestQueue requestQueue;

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    System.err.println("bluetooth server service started");
//    NotificationChannel serviceChannel =
//        new NotificationChannel(SEND_CHANNEL_ID, "Foreground Service Channel",
//                                NotificationManager.IMPORTANCE_DEFAULT);
//
//    getSystemService(NotificationManager.class)
//        .createNotificationChannel(serviceChannel);
//
//    PendingIntent pendingIntent = PendingIntent.getActivity(
//        this, 0, new Intent(this, ScanActivity.class), 0);
//
//    Notification notification =
//        new NotificationCompat.Builder(this, SEND_CHANNEL_ID)
//            .setContentTitle("Human To Human")
//            .setContentText("Sending data to server...")
//            .setSmallIcon(R.drawable.ic_stat_name)
//            .setContentIntent(pendingIntent)
//            .build();
//
//    startForeground(3, notification);

    Handler handler = new Handler();
    Runnable runner = new Runnable() {
      @Override
      public void run() {
        ArrayList<Database.Row> rows = supplier.get();
        if (rows == null) {
          handler.postDelayed(this, 3000);
          return;
        }
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST, AppLogic.getServerURL() + "/addConnections",
            serializeRows(rows),
            (response)
                -> {
              listener.onFinish(response, null);
              handler.postDelayed(this, 3000);
            },
            (error) -> {
              listener.onFinish(null, error);
              handler.postDelayed(this, 3000);
            });

        requestQueue.add(request);
      }
    };
    handler.postDelayed(runner, 3000);

    return Service.START_STICKY;
  }

  public interface Listener<T> {
    void onFinish(T data, VolleyError error);
  }

  public static void initializeServer(Context ctx) {
    if (requestQueue != null)
      return;
    requestQueue = Volley.newRequestQueue(ctx);
    requestQueue.start();
  }

  public static JSONObject serializeRows(ArrayList<Database.Row> rows) {
      try {
          JSONArray jsonArray = new JSONArray();
          for (Database.Row row : rows) {
              JSONObject jsonObject = new JSONObject();
              jsonObject.put("other", row.id);
              jsonObject.put("time", format.format(row.date));
              jsonObject.put("power", row.power);
              jsonObject.put("rssi", row.rssi);
              jsonArray.put(jsonObject);
          }
          JSONObject jsonObject = new JSONObject();

          jsonObject.put("id", AppLogic.getBluetoothID());
          jsonObject.put("connections", jsonArray);
          return jsonObject;
      } catch (JSONException e) {
          throw new RuntimeException(e);
      }
  }

  public static void getId(Listener<Long> l) {
      StringRequest req = new StringRequest(Request.Method.POST, AppLogic.getServerURL()+"/addUser",
              (response) -> l.onFinish(Long.parseLong(response), null),
              (error) -> l.onFinish(null, error));

      requestQueue.add(req);
  }
}
