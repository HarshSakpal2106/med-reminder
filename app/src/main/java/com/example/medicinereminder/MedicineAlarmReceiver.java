package com.example.medicinereminder;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

public class MedicineAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "medicine_reminder_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        int medicineId = intent.getIntExtra("medicineId", -1);
        String medicineName = intent.getStringExtra("medicineName");
        String medicineDosage = intent.getStringExtra("medicineDosage");
        String medicineTime = intent.getStringExtra("medicineTime");

        if (medicineId == -1) return;

        // Format time for display
        String displayTime = formatTime(medicineTime);

        // Create full screen intent to wake device
        Intent fullScreenIntent = new Intent(context, MainActivity.class);
        fullScreenIntent.putExtra("medicineId", medicineId);
        fullScreenIntent.putExtra("medicineName", medicineName);
        fullScreenIntent.putExtra("show_alert", true);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(context, medicineId * 10,
                fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Medicine Alert!")
                .setContentText("Time to take " + medicineName + " (" + medicineDosage + ")")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("It is time to take your medicine:\n" + medicineName + " (" + medicineDosage + ")\nScheduled at " + displayTime))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true);

        // Add Yes action
        Intent yesIntent = new Intent(context, NotificationActionReceiver.class);
        yesIntent.putExtra("medicineId", medicineId);
        yesIntent.putExtra("response", "yes");
        yesIntent.putExtra("action", "yes");
        PendingIntent yesPendingIntent = PendingIntent.getBroadcast(
                context,
                medicineId * 10 + 1,
                yesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.addAction(android.R.drawable.ic_menu_save, "Yes", yesPendingIntent);

        // Add No action
        Intent noIntent = new Intent(context, NotificationActionReceiver.class);
        noIntent.putExtra("medicineId", medicineId);
        noIntent.putExtra("response", "no");
        noIntent.putExtra("action", "no");
        PendingIntent noPendingIntent = PendingIntent.getBroadcast(
                context,
                medicineId * 10 + 2,
                noIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "No", noPendingIntent);

        // Show notification
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(medicineId, builder.build());
    }

    private String formatTime(String time24) {
        String[] parts = time24.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        String ampm = hour >= 12 ? "PM" : "AM";
        int hour12 = hour % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format("%d:%02d %s", hour12, minute, ampm);
    }
}
