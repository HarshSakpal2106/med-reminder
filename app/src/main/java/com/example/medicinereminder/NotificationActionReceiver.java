package com.example.medicinereminder;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class NotificationActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int medicineId = intent.getIntExtra("medicineId", -1);
        String response = intent.getStringExtra("response");
        String action = intent.getStringExtra("action");

        if (medicineId == -1 || response == null) return;

        // Cancel notification
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(medicineId);

        // Open app and pass response
        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.putExtra("medicineId", medicineId);
        appIntent.putExtra("response", response);
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(appIntent);

        // Show toast
        String message = action.equals("yes") ? "✓ Medicine marked as taken!" : "✕ Medicine marked as missed";
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}