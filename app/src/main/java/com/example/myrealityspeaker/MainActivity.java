
package com.example.myrealityspeaker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.myrealityspeaker.service.NotificationReaderService;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "MyRealitySpeakerPrefs";
    private static final String PREF_SPEECH_RATE = "speech_rate";

    private static final float DEFAULT_SPEECH_RATE = 0.85f;

    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    private TextView tvServiceStatus;
    private TextView tvServiceDescription;
    private TextView tvSpeechRate;

    private SeekBar seekSpeechRate;

    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        );

        initializeViews();
        initializeSpeech();
        initializeListeners();
        loadSpeechRate();
    }

    private void initializeViews() {

        tvServiceStatus = findViewById(R.id.tvServiceStatus);
        tvServiceDescription = findViewById(R.id.tvServiceDescription);
        tvSpeechRate = findViewById(R.id.tvSpeechRate);

        seekSpeechRate = findViewById(R.id.seekSpeechRate);
    }

    private void initializeListeners() {

        Button btnTestVoice = findViewById(R.id.btnTestVoice);

        Button btnNotificationAccess =
                findViewById(R.id.btnNotificationAccess);

        btnNotificationAccess.setOnClickListener(v -> {

            Intent intent = new Intent(
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            );

            startActivity(intent);
        });

        btnTestVoice.setOnClickListener(v -> testVoice());

        seekSpeechRate.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {

                        float rate = 0.50f + (progress * 0.01f);

                        tvSpeechRate.setText(
                                String.format(Locale.US, "%.2fx", rate)
                        );

                        if (fromUser) {
                            saveSpeechRate(rate);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                }
        );
    }

    private void loadSpeechRate() {

        float savedRate = preferences.getFloat(
                PREF_SPEECH_RATE,
                DEFAULT_SPEECH_RATE
        );

        int progress = Math.round(
                (savedRate - 0.50f) * 100
        );

        seekSpeechRate.setProgress(
                Math.max(0, Math.min(50, progress))
        );

        tvSpeechRate.setText(
                String.format(Locale.US, "%.2fx", savedRate)
        );
    }

    private void saveSpeechRate(float rate) {

        preferences.edit()
                .putFloat(PREF_SPEECH_RATE, rate)
                .apply();
    }

    private void initializeSpeech() {

        textToSpeech = new TextToSpeech(
                this,
                status -> {

                    if (status == TextToSpeech.SUCCESS) {

                        int result = textToSpeech.setLanguage(
                                Locale.forLanguageTag("es-CO")
                        );

                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                                result == TextToSpeech.LANG_NOT_SUPPORTED) {

                            result = textToSpeech.setLanguage(
                                    Locale.forLanguageTag("es-419")
                            );
                        }

                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                                result == TextToSpeech.LANG_NOT_SUPPORTED) {

                            result = textToSpeech.setLanguage(
                                    new Locale("es", "ES")
                            );
                        }

                        ttsReady =
                                result != TextToSpeech.LANG_MISSING_DATA &&
                                        result != TextToSpeech.LANG_NOT_SUPPORTED;

                        if (ttsReady) {
                            textToSpeech.setSpeechRate(
                                    getSavedSpeechRate()
                            );
                        }
                    }
                }
        );
    }

    private float getSavedSpeechRate() {

        return preferences.getFloat(
                PREF_SPEECH_RATE,
                DEFAULT_SPEECH_RATE
        );
    }

    private void testVoice() {

        if (textToSpeech == null || !ttsReady) {
            tvServiceDescription.setText(
                    "El motor de voz todavía se está inicializando."
            );
            return;
        }

        float rate = getSavedSpeechRate();

        textToSpeech.setSpeechRate(rate);

        textToSpeech.speak(
                "Hola. My Reality Speaker está funcionando correctamente.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "test-voice"
        );
    }

    private void updateNotificationStatus() {

        String enabledListeners = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );

        ComponentName serviceComponent = new ComponentName(
                this,
                NotificationReaderService.class
        );

        String componentName =
                serviceComponent.flattenToString();

        boolean isEnabled = enabledListeners != null &&
                enabledListeners.contains(componentName);

        if (isEnabled) {

            tvServiceStatus.setText("ACTIVO");

            tvServiceDescription.setText(
                    "Acceso concedido. MyRealitySpeaker puede escuchar " +
                            "las notificaciones de Nequi."
            );

        } else {

            tvServiceStatus.setText("INACTIVO");

            tvServiceDescription.setText(
                    "Debes conceder acceso a las notificaciones " +
                            "para comenzar a utilizar el servicio."
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (tvServiceStatus != null) {
            updateNotificationStatus();
        }

        // Recuperar la velocidad guardada.
        if (preferences != null && seekSpeechRate != null) {
            loadSpeechRate();
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



/*package com.example.myrealityspeaker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("AvisoPorVoz");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);

        TextView description = new TextView(this);
        description.setText(
                "Lee en voz alta las notificaciones " +
                        "de la aplicación seleccionada."
        );
        description.setTextSize(16);
        description.setGravity(Gravity.CENTER);

        Button accessButton = new Button(this);
        accessButton.setText("Activar acceso a notificaciones");
        accessButton.setOnClickListener(v ->
                startActivity(new Intent(
                        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                ))
        );

        Button testButton = new Button(this);
        testButton.setText("Probar voz");
        testButton.setOnClickListener(v -> {
            if (textToSpeech != null) {
                textToSpeech.speak(
                        "Aviso por voz funcionando correctamente",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "test-voice"
                );
            }
        });

        layout.addView(title);
        layout.addView(description);
        layout.addView(accessButton);
        layout.addView(testButton);

        setContentView(layout);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(
                        new Locale("es", "CO")
                );
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        super.onDestroy();
    }
}*/


/*
package com.example.myrealityspeaker;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}*/
