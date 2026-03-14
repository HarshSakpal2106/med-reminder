package com.example.medicinereminder;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "TalkBack";

    private TextToSpeech textToSpeech;
    private SharedPreferences sharedPreferences;
    private SwitchCompat talkBackSwitch;
    private boolean isTtsReady = false;

    private static final String PREF_NAME    = "MedReminderPrefs";
    private static final String KEY_TALKBACK = "talkback_enabled";
    private static final String KEY_FIRST_RUN = "is_first_run";

    // Medicine names mapped to each <include> id
    private static final int[] MED_IDS = {
        R.id.medMetoprolol, R.id.medAspirin, R.id.medLisinopril,
        R.id.medMetformin,  R.id.medVitaminD3, R.id.medAtorvastatin,
        R.id.medFurosemide, R.id.medMelatonin
    };
    private static final String[] MED_NAMES = {
        "Metoprolol", "Aspirin", "Lisinopril",
        "Metformin", "Vitamin D 3", "Atorvastatin",
        "Furosemide", "Melatonin"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Init TTS
        textToSpeech = new TextToSpeech(this, this);

        // Wire up the toggle switch
        talkBackSwitch = findViewById(R.id.switch_talkback);
        if (talkBackSwitch != null) {
            boolean currentState = sharedPreferences.getBoolean(KEY_TALKBACK, true);
            talkBackSwitch.setChecked(currentState);

            talkBackSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                // Save setting
                sharedPreferences.edit().putBoolean(KEY_TALKBACK, isChecked).apply();

                if (isChecked) {
                    // Show toast AND speak confirmation
                    Toast.makeText(this, "🔊 Voice Alerts: ON", Toast.LENGTH_SHORT).show();
                    forceSpeak("Voice Alerts is now enabled");
                } else {
                    Toast.makeText(this, "🔇 Voice Alerts: OFF", Toast.LENGTH_SHORT).show();
                }
            });
        }

        checkFirstRun();
        setupMedicineClickListeners();
        setupMedicineData();
        setupDateTime(); // Real-time greeting and date
    }

    /** Sets greeting (Good Morning/Afternoon/Evening) and today's date dynamically. */
    private void setupDateTime() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);

        // Pick greeting based on time of day
        String greeting;
        if (hour >= 5 && hour < 12) {
            greeting = "Good Morning 👋";
        } else if (hour >= 12 && hour < 17) {
            greeting = "Good Afternoon ☀️";
        } else if (hour >= 17 && hour < 21) {
            greeting = "Good Evening 🌆";
        } else {
            greeting = "Good Night 🌙";
        }

        // Format today's date e.g. "Saturday, 14 Mar 2026"
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEEE, dd MMM yyyy", java.util.Locale.getDefault());
        String dateStr = sdf.format(cal.getTime());

        TextView tvGreeting = findViewById(R.id.tvGreeting);
        TextView tvDate     = findViewById(R.id.tvDate);

        if (tvGreeting != null) tvGreeting.setText(greeting);
        if (tvDate     != null) tvDate.setText(dateStr);
    }


    // Called when TTS engine is ready
    @Override
    public void onInit(int status) {
        Log.d(TAG, "onInit called with status: " + status);
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);
            Log.d(TAG, "setLanguage result: " + result);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "TTS: Language not supported on this device", Toast.LENGTH_LONG).show();
            } else {
                isTtsReady = true;
                Log.d(TAG, "TTS ready!");
            }
        } else {
            Toast.makeText(this, "TTS engine failed to start (status=" + status + ")", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Speaks text ONLY if TalkBack switch is ON.
     * Shows a Toast hint if switch is off.
     */
    public void speakText(String text) {
        boolean enabled = sharedPreferences.getBoolean(KEY_TALKBACK, true);
        Log.d(TAG, "speakText called. enabled=" + enabled + " ttsReady=" + isTtsReady + " text=" + text);

        if (!enabled) {
            Toast.makeText(this, "Voice Alerts is OFF · Enable the switch to hear medicine names", Toast.LENGTH_SHORT).show();
            return;
        }
        forceSpeak(text);
    }

    /**
     * Speaks text regardless of the switch state (used for toggle feedback).
     */
    private void forceSpeak(String text) {
        if (!isTtsReady || textToSpeech == null) {
            Toast.makeText(this, "Voice engine is loading, try again in a moment", Toast.LENGTH_SHORT).show();
            return;
        }
        int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id");
        Log.d(TAG, "speak() result: " + result);
        if (result == TextToSpeech.ERROR) {
            Toast.makeText(this, "TTS speak() returned error", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkFirstRun() {
        if (!sharedPreferences.getBoolean(KEY_FIRST_RUN, true)) return;

        new AlertDialog.Builder(this)
            .setTitle("Enable Voice Assistance?")
            .setMessage("Tap any medicine name and the app will read it aloud for you.")
            .setPositiveButton("Yes, Enable", (d, w) -> {
                if (talkBackSwitch != null) talkBackSwitch.setChecked(true);
                sharedPreferences.edit()
                    .putBoolean(KEY_TALKBACK, true)
                    .putBoolean(KEY_FIRST_RUN, false)
                    .apply();
            })
            .setNegativeButton("No, Thanks", (d, w) -> {
                if (talkBackSwitch != null) talkBackSwitch.setChecked(false);
                sharedPreferences.edit()
                    .putBoolean(KEY_TALKBACK, false)
                    .putBoolean(KEY_FIRST_RUN, false)
                    .apply();
            })
            .setCancelable(false)
            .show();
    }

    private void setupMedicineClickListeners() {
        for (int i = 0; i < MED_IDS.length; i++) {
            final String name = MED_NAMES[i];

            View cardRoot = findViewById(MED_IDS[i]);
            if (cardRoot == null) {
                Log.w(TAG, "Card root null for: " + name);
                continue;
            }

            // Make the entire card clickable
            cardRoot.setClickable(true);
            cardRoot.setFocusable(true);
            cardRoot.setOnClickListener(v -> speakText(name));

            // Also put it directly on the TextView so tapping text word fires it
            TextView medNameView = cardRoot.findViewById(R.id.medName);
            if (medNameView != null) {
                medNameView.setClickable(true);
                medNameView.setOnClickListener(v -> speakText(name));
            }
        }
    }

    /**
     * Sets the correct name, description, time and status for each medicine card.
     * All <include> rows share the same template with hardcoded defaults,
     * so we must override each one here.
     */
    private void setupMedicineData() {
        // Format: { name, description, time, statusIcon, statusBg, statusColor }
        // statusBg references drawable names, statusColor is hex
        Object[][] data = {
            {"Metoprolol",   "25mg · Heart Rate",       "8:00 AM",  "✓", R.drawable.bg_circle_taken,   android.graphics.Color.parseColor("#2E7D47")},
            {"Aspirin",      "81mg · Blood Thinner",    "9:00 AM",  "✓", R.drawable.bg_circle_taken,   android.graphics.Color.parseColor("#2E7D47")},
            {"Lisinopril",   "10mg · Blood Pressure",   "12:00 PM", "✓", R.drawable.bg_circle_taken,   android.graphics.Color.parseColor("#2E7D47")},
            {"Metformin",    "500mg · Blood Sugar",     "8:30 AM",  "✓", R.drawable.bg_circle_taken,   android.graphics.Color.parseColor("#2E7D47")},
            {"Vitamin D3",   "1000 IU · Supplement",    "6:00 AM",  "✓", R.drawable.bg_circle_taken,   android.graphics.Color.parseColor("#2E7D47")},
            {"Atorvastatin", "20mg · Cholesterol",      "9:00 PM",  "✕", R.drawable.bg_circle_missed,  android.graphics.Color.parseColor("#C04030")},
            {"Furosemide",   "40mg · Diuretic",         "6:00 PM",  "○", R.drawable.bg_circle_pending, android.graphics.Color.parseColor("#CCCCCC")},
            {"Melatonin",    "3mg · Sleep Aid",         "9:00 PM",  "○", R.drawable.bg_circle_pending, android.graphics.Color.parseColor("#CCCCCC")},
        };

        for (int i = 0; i < MED_IDS.length; i++) {
            View card = findViewById(MED_IDS[i]);
            if (card == null) continue;

            String name    = (String) data[i][0];
            String desc    = (String) data[i][1];
            String time    = (String) data[i][2];
            String icon    = (String) data[i][3];
            int    bgRes   = (int)    data[i][4];
            int    color   = (int)    data[i][5];

            TextView tvName   = card.findViewById(R.id.medName);
            TextView tvDesc   = card.findViewById(R.id.medDesc);
            TextView tvTime   = card.findViewById(R.id.medTime);
            TextView tvStatus = card.findViewById(R.id.medStatus);

            if (tvName   != null) tvName.setText(name);
            if (tvDesc   != null) tvDesc.setText(desc);
            if (tvTime   != null) tvTime.setText(time);
            if (tvStatus != null) {
                tvStatus.setText(icon);
                tvStatus.setTextColor(color);
                tvStatus.setBackgroundResource(bgRes);
            }
        }
    }


    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
