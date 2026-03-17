package com.example.medicinereminder;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "MedReminder";
    private static final String CHANNEL_ID = "medicine_reminder_channel";

    // --- WebView ---
    private WebView webView;
    private ActivityResultLauncher<String> mGetContent;

    // --- TalkBack TTS ---
    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;
    private static final String PREF_NAME     = "MedReminderPrefs";
    private static final String KEY_TALKBACK  = "talkback_enabled";
    private static final String KEY_FIRST_RUN = "is_first_run";
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            android.app.KeyguardManager keyguardManager = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) keyguardManager.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Init TTS
        textToSpeech = new TextToSpeech(this, this);

        // Ask on first run if user wants TalkBack
        checkFirstRun();

        // --- WebView setup ---
        webView = findViewById(R.id.webView);

        mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) processImageWithMLKit(uri);
                });

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleFullScreenAlert(getIntent());
            }
        });
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        webView.loadUrl("file:///android_asset/index.html");

        createNotificationChannel();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack();
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });

        handleNotificationIntent(getIntent());
    }

    // ─── TTS Callbacks ───────────────────────────────────────────────────────

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS language not supported");
            } else {
                isTtsReady = true;
                Log.d(TAG, "TTS ready");
            }
        } else {
            Log.e(TAG, "TTS init failed");
        }
    }

    /** Speaks text if TalkBack is enabled. */
    public void speakText(String text) {
        boolean enabled = sharedPreferences.getBoolean(KEY_TALKBACK, true);
        if (!enabled || !isTtsReady || textToSpeech == null) return;
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id");
    }

    private void checkFirstRun() {
        if (!sharedPreferences.getBoolean(KEY_FIRST_RUN, true)) return;
        showProfileSetupDialog(false);
    }

    private void showProfileSetupDialog(boolean isEditMode) {
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final android.widget.EditText nameInput = new android.widget.EditText(this);
        nameInput.setHint("Patient Name (e.g. John Doe)");
        nameInput.setText(sharedPreferences.getString("patient_name", ""));
        layout.addView(nameInput);

        final android.widget.EditText ageInput = new android.widget.EditText(this);
        ageInput.setHint("Age");
        ageInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        ageInput.setText(sharedPreferences.getString("patient_age", ""));
        layout.addView(ageInput);

        final android.widget.EditText bloodInput = new android.widget.EditText(this);
        bloodInput.setHint("Blood Group (e.g. O+)");
        bloodInput.setText(sharedPreferences.getString("patient_blood", ""));
        layout.addView(bloodInput);

        final android.widget.EditText docNameInput = new android.widget.EditText(this);
        docNameInput.setHint("Doctor's Name");
        docNameInput.setText(sharedPreferences.getString("doc_name", ""));
        layout.addView(docNameInput);

        final android.widget.EditText docPhoneInput = new android.widget.EditText(this);
        docPhoneInput.setHint("Doctor's Phone Number");
        docPhoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        docPhoneInput.setText(sharedPreferences.getString("doc_phone", ""));
        layout.addView(docPhoneInput);

        final android.widget.Switch talkBackSwitch = new android.widget.Switch(this);
        talkBackSwitch.setText("Enable Voice Alerts (TalkBack)");
        talkBackSwitch.setChecked(sharedPreferences.getBoolean(KEY_TALKBACK, true));
        talkBackSwitch.setPadding(0, 30, 0, 10);
        layout.addView(talkBackSwitch);

        scrollView.addView(layout);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(isEditMode ? "Edit Profile" : "Welcome! Setup Profile")
            .setView(scrollView)
            .setPositiveButton("Save", null) // Overridden below
            .setCancelable(isEditMode) // Force setup on first run
            .create();
            
        if (isEditMode) {
             dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", (d, w) -> dialog.dismiss());
        }

        dialog.setOnShowListener(dialogInterface -> {
            android.widget.Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String name = nameInput.getText().toString().trim();
                String age = ageInput.getText().toString().trim();
                String blood = bloodInput.getText().toString().trim();
                String dName = docNameInput.getText().toString().trim();
                String dPhone = docPhoneInput.getText().toString().trim();

                if (name.isEmpty() || age.isEmpty() || blood.isEmpty() || dName.isEmpty() || dPhone.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please fill in all details", Toast.LENGTH_SHORT).show();
                    return; // Prevent dialog close
                }

                sharedPreferences.edit()
                    .putString("patient_name", name)
                    .putString("patient_age", age)
                    .putString("patient_blood", blood)
                    .putString("doc_name", dName)
                    .putString("doc_phone", dPhone)
                    .putBoolean(KEY_TALKBACK, talkBackSwitch.isChecked())
                    .putBoolean(KEY_FIRST_RUN, false)
                    .apply();
                
                // Refresh WebView to show new data
                if (webView != null) webView.evaluateJavascript("javascript:loadPatientData()", null);
                Toast.makeText(MainActivity.this, "Profile Saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });
        
        dialog.show();
    }

    // ─── ML Kit OCR ──────────────────────────────────────────────────────────

    private void processImageWithMLKit(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String safeText = JSONObject.quote(visionText.getText());
                    runOnUiThread(() ->
                        webView.evaluateJavascript("javascript:handleOCRResult(" + safeText + ")", null));
                })
                .addOnFailureListener(e ->
                    Toast.makeText(this, "Failed to read prescription", Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── Notification handling ────────────────────────────────────────────────

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
        handleFullScreenAlert(intent);
    }

    private void handleFullScreenAlert(Intent intent) {
        if (intent != null && intent.getBooleanExtra("show_alert", false)) {
            final int medicineId = intent.getIntExtra("medicineId", -1);
            if (medicineId != -1) {
                // Delay slightly to ensure WebView is ready if just launched
                webView.postDelayed(() -> webView.evaluateJavascript(
                    "javascript:showAlert(" + medicineId + ")", null), 1000);
                intent.removeExtra("show_alert");
            }
        }
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent != null && intent.hasExtra("medicineId")) {
            int medicineId = intent.getIntExtra("medicineId", -1);
            String response = intent.getStringExtra("response");
            if (medicineId != -1 && response != null) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.cancel(medicineId);
                String message = response.equals("yes") ? "✓ Medicine marked as taken!" : "✕ Medicine marked as missed";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                webView.post(() -> webView.evaluateJavascript(
                    "javascript:handleNotificationResponse(" + medicineId + ", '" + response + "')", null));
            }
        }
    }

    // ─── JavaScript Bridge ────────────────────────────────────────────────────

    public class WebAppInterface {
        Context context;
        WebAppInterface(Context c) { context = c; }

        @JavascriptInterface
        public void startPrescriptionScan() {
            runOnUiThread(() -> mGetContent.launch("image/*"));
        }

        @JavascriptInterface
        public void showToast(String message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public String getPatientData() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("name", sharedPreferences.getString("patient_name", "Patient"));
                obj.put("age", sharedPreferences.getString("patient_age", "--"));
                obj.put("blood", sharedPreferences.getString("patient_blood", "--"));
                obj.put("docName", sharedPreferences.getString("doc_name", "Your Doctor"));
                obj.put("docPhone", sharedPreferences.getString("doc_phone", ""));
                return obj.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void openEditProfile() {
            runOnUiThread(() -> showProfileSetupDialog(true));
        }

        @JavascriptInterface
        public void callDoctor() {
            String phone = sharedPreferences.getString("doc_phone", "");
            if (phone.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(context, "No doctor phone number saved!", Toast.LENGTH_SHORT).show());
                return;
            }
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phone));
            context.startActivity(intent);
        }

        /** Called from JS when user taps a medicine name — speaks it aloud via TTS. */
        @JavascriptInterface
        public void speakMedicineName(String name) {
            runOnUiThread(() -> speakText(name));
        }

        /** Called from JS to stop any active TTS immediately. */
        @JavascriptInterface
        public void stopSpeaking() {
            if (textToSpeech != null && isTtsReady) {
                textToSpeech.stop();
            }
        }

        /** Called from JS to toggle TalkBack on/off and announce the change. */
        @JavascriptInterface
        public void setTalkBack(boolean enabled) {
            sharedPreferences.edit().putBoolean(KEY_TALKBACK, enabled).apply();
            String msg = enabled ? "Voice Alerts enabled" : "Voice Alerts disabled";
            runOnUiThread(() -> {
                Toast.makeText(context, enabled ? "🔊 " + msg : "🔇 " + msg, Toast.LENGTH_SHORT).show();
                if (enabled && isTtsReady) {
                    textToSpeech.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "tts_toggle");
                }
            });
        }

        /** Returns current TalkBack state to JavaScript. */
        @JavascriptInterface
        public boolean isTalkBackEnabled() {
            return sharedPreferences.getBoolean(KEY_TALKBACK, true);
        }

        @JavascriptInterface
        public void showNotification(String medicineName, String dosage, String time, int medicineId) {
            Intent yesIntent = new Intent(context, MainActivity.class);
            yesIntent.putExtra("medicineId", medicineId);
            yesIntent.putExtra("response", "yes");
            yesIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent yesPendingIntent = PendingIntent.getActivity(context, medicineId * 10 + 1, yesIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent noIntent = new Intent(context, MainActivity.class);
            noIntent.putExtra("medicineId", medicineId);
            noIntent.putExtra("response", "no");
            noIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent noPendingIntent = PendingIntent.getActivity(context, medicineId * 10 + 2, noIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Medicine Alert!")
                .setContentText("Time to take " + medicineName + " (" + dosage + ")")
                .setStyle(new NotificationCompat.BigTextStyle()
                    .bigText("Time to take your medicine:\n" + medicineName + " (" + dosage + ")\nScheduled at " + time))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_save, "Yes", yesPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "No", noPendingIntent);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(medicineId, builder.build());

            // Also speak the alert via TTS
            speakText("Time to take " + medicineName);
        }

        @JavascriptInterface
        public void updateMedicineAlarms(String medicinesJson) {
            try {
                JSONArray medicines = new JSONArray(medicinesJson);
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                for (int i = 0; i < medicines.length(); i++) {
                    JSONObject medicine = medicines.getJSONObject(i);
                    int id = medicine.getInt("id");
                    String time = medicine.getString("time");
                    String status = medicine.getString("status");
                    if (status.equals("pending")) {
                        String[] timeParts = time.split(":");
                        int hour = Integer.parseInt(timeParts[0]);
                        int minute = Integer.parseInt(timeParts[1]);
                        Calendar calendar = Calendar.getInstance();
                        calendar.set(Calendar.HOUR_OF_DAY, hour);
                        calendar.set(Calendar.MINUTE, minute);
                        calendar.set(Calendar.SECOND, 0);
                        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                            calendar.add(Calendar.DAY_OF_YEAR, 1);
                        }
                        Intent intent = new Intent(context, MedicineAlarmReceiver.class);
                        intent.putExtra("medicineId", id);
                        intent.putExtra("medicineName", medicine.getString("name"));
                        intent.putExtra("medicineDosage", medicine.getString("dosage"));
                        intent.putExtra("medicineTime", time);
                        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                        } else {
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                        }
                    } else {
                        Intent intent = new Intent(context, MedicineAlarmReceiver.class);
                        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        alarmManager.cancel(pendingIntent);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Medicine Reminders", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for medicine schedules");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        super.onDestroy();
    }
}
