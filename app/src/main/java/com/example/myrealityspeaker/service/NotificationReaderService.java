
package com.example.myrealityspeaker.service;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationReaderService extends NotificationListenerService {

    private static final String TAG = "MyRealitySpeaker";

    // Aplicación que queremos escuchar: Nequi Colombia.
    private static final String TARGET_PACKAGE = "com.nequi.MobileApp";

    // Velocidad de lectura:
    // 1.0 = velocidad normal.
    // 0.85 = un poco más lento.
    // 0.75 = bastante más lento.
    private static final float SPEECH_RATE = 0.85f;

    // Tono normal de voz.
    private static final float SPEECH_PITCH = 1.0f;

    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    // Detecta cantidades precedidas por el símbolo de moneda.
    // Ejemplos: $100, $50.000, $1,000,000.
    private static final Pattern MONEY_PATTERN = Pattern.compile(
            "\\$\\s*([0-9][0-9.,]*)"
    );

    // Detecta palabras escritas completamente en mayúsculas.
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(
            "(?<![\\p{L}])([A-ZÁÉÍÓÚÜÑ]{2,})(?![\\p{L}])"
    );

    @Override
    public void onCreate() {
        super.onCreate();

        textToSpeech = new TextToSpeech(
                getApplicationContext(),
                status -> {

                    if (status == TextToSpeech.SUCCESS) {

                        configureVoice();

                    } else {
                        Log.e(TAG, "No se pudo inicializar TextToSpeech");
                    }
                }
        );
    }

    /**
     * Configura el idioma, la voz y la velocidad.
     */
    private void configureVoice() {

        // Prioridad 1: Español de Colombia.
        Locale colombianSpanish = Locale.forLanguageTag("es-CO");

        int result = textToSpeech.setLanguage(colombianSpanish);

        // Prioridad 2: Español latinoamericano.
        if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {

            Locale latinAmericanSpanish =
                    Locale.forLanguageTag("es-419");

            result = textToSpeech.setLanguage(latinAmericanSpanish);
        }

        // Prioridad 3: Español de España.
        if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {

            Locale spanish = new Locale("es", "ES");

            result = textToSpeech.setLanguage(spanish);
        }

        // Validar que el idioma esté disponible.
        if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {

            Log.e(TAG, "No hay un idioma español compatible instalado");
            return;
        }

        // Intentar seleccionar una voz colombiana disponible.
        Set<Voice> voices = textToSpeech.getVoices();

        if (voices != null) {

            for (Voice voice : voices) {

                Locale voiceLocale = voice.getLocale();

                if ("es".equals(voiceLocale.getLanguage()) &&
                        "CO".equals(voiceLocale.getCountry())) {

                    textToSpeech.setVoice(voice);

                    Log.i(TAG, "Voz colombiana seleccionada");
                    break;
                }
            }
        }

        // Aplicar velocidad y tono.
        textToSpeech.setSpeechRate(SPEECH_RATE);
        textToSpeech.setPitch(SPEECH_PITCH);

        ttsReady = true;

        Log.i(TAG, "TextToSpeech configurado correctamente");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();

        Log.i(TAG, "Escucha de notificaciones conectada");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

        // Ignorar notificaciones de otras aplicaciones.
        if (!TARGET_PACKAGE.equals(sbn.getPackageName())) {
            return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        if (extras == null) {
            return;
        }

        // Obtener título.
        CharSequence title = extras.getCharSequence(
                Notification.EXTRA_TITLE
        );

        // Obtener mensaje ampliado.
        CharSequence message = extras.getCharSequence(
                Notification.EXTRA_BIG_TEXT
        );

        // Si no existe, intentar obtener el mensaje normal.
        if (message == null || message.toString().trim().isEmpty()) {

            message = extras.getCharSequence(
                    Notification.EXTRA_TEXT
            );
        }

        // Construir el texto que se va a reproducir.
        StringBuilder speech = new StringBuilder();

        if (title != null && !title.toString().trim().isEmpty()) {
            speech.append(title.toString().trim());
        }

        if (message != null && !message.toString().trim().isEmpty()) {

            if (speech.length() > 0) {
                speech.append(". ");
            }

            speech.append(message.toString().trim());
        }

        String text = speech.toString().trim();

        if (text.isEmpty()) {
            return;
        }

        // Preparar el contenido antes de enviarlo al motor de voz.
        text = prepareText(text);

        SharedPreferences preferences = getSharedPreferences(
                "MyRealitySpeakerPrefs",
                MODE_PRIVATE
        );

        float speechRate = preferences.getFloat(
                "speech_rate",
                0.85f
        );

        textToSpeech.setSpeechRate(speechRate);

        // Reproducir la notificación.
        if (ttsReady && textToSpeech != null) {

            textToSpeech.speak(
                    text,
                    TextToSpeech.QUEUE_ADD,
                    null,
                    "notification-" + sbn.getKey()
            );
        }
    }

    /**
     * Normaliza el contenido de las notificaciones.
     */
    private String prepareText(String originalText) {

        String text = originalText;

        // 1. Convertir cantidades monetarias a pesos colombianos.
        text = normalizeCurrency(text);

        // 2. Normalizar palabras completamente en mayúsculas.
        text = normalizeUppercaseWords(text);

        return text;
    }

    /**
     * Convierte importes como $100 o $50.000
     * en cantidades expresadas en pesos colombianos.
     */
    private String normalizeCurrency(String text) {

        Matcher matcher = MONEY_PATTERN.matcher(text);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {

            String amount = matcher.group(1);

            // Eliminar separadores de miles.
            String digits = amount.replaceAll("[^0-9]", "");

            try {

                long value = Long.parseLong(digits);

                String spokenAmount = numberToSpanish(value);

                // Ajustar uno a un antes de "pesos".
                spokenAmount = spokenAmount.replaceAll(
                        "\\buno$", "un"
                );

                spokenAmount += " pesos colombianos";

                matcher.appendReplacement(
                        result,
                        Matcher.quoteReplacement(spokenAmount)
                );

            } catch (NumberFormatException e) {

                // Si el importe es demasiado grande,
                // conservar el texto original.
                matcher.appendReplacement(
                        result,
                        Matcher.quoteReplacement(matcher.group())
                );
            }
        }

        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Convierte números a palabras en español.
     */
    private String numberToSpanish(long number) {

        if (number == 0) {
            return "cero";
        }

        if (number < 0) {
            return "menos " + numberToSpanish(-number);
        }

        if (number <= 29) {

            String[] units = {
                    "cero", "uno", "dos", "tres", "cuatro",
                    "cinco", "seis", "siete", "ocho", "nueve",
                    "diez", "once", "doce", "trece", "catorce",
                    "quince", "dieciséis", "diecisiete",
                    "dieciocho", "diecinueve", "veinte",
                    "veintiuno", "veintidós", "veintitrés",
                    "veinticuatro", "veinticinco", "veintiséis",
                    "veintisiete", "veintiocho", "veintinueve"
            };

            return units[(int) number];
        }

        if (number < 100) {

            String[] tens = {
                    "", "", "veinte", "treinta", "cuarenta",
                    "cincuenta", "sesenta", "setenta",
                    "ochenta", "noventa"
            };

            int ten = (int) number / 10;
            int unit = (int) number % 10;

            return unit == 0
                    ? tens[ten]
                    : tens[ten] + " y " + numberToSpanish(unit);
        }

        if (number < 1000) {

            String[] hundreds = {
                    "", "ciento", "doscientos", "trescientos",
                    "cuatrocientos", "quinientos", "seiscientos",
                    "setecientos", "ochocientos", "novecientos"
            };

            if (number == 100) {
                return "cien";
            }

            int hundred = (int) number / 100;
            int remainder = (int) number % 100;

            return hundreds[hundred] +
                    (remainder == 0
                            ? ""
                            : " " + numberToSpanish(remainder));
        }

        if (number < 1_000_000) {

            long thousands = number / 1000;
            long remainder = number % 1000;

            String prefix = thousands == 1
                    ? "mil"
                    : numberToSpanish(thousands) + " mil";

            return prefix +
                    (remainder == 0
                            ? ""
                            : " " + numberToSpanish(remainder));
        }

        if (number < 1_000_000_000) {

            long millions = number / 1_000_000;
            long remainder = number % 1_000_000;

            String prefix = millions == 1
                    ? "un millón"
                    : numberToSpanish(millions) + " millones";

            return prefix +
                    (remainder == 0
                            ? ""
                            : " " + numberToSpanish(remainder));
        }

        // Evitar conversiones incorrectas para cantidades
        // superiores a 999.999.999.
        return String.valueOf(number);
    }

    /**
     * Convierte palabras en mayúsculas a formato normal.
     *
     * Ejemplo:
     * SANTAMARIA -> Santamaria
     * DAMIAN -> Damian
     */
    private String normalizeUppercaseWords(String text) {

        Matcher matcher = UPPERCASE_PATTERN.matcher(text);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {

            String word = matcher.group(1);

            String readable = word.substring(0, 1)
                    + word.substring(1).toLowerCase(
                    Locale.forLanguageTag("es-CO")
            );

            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(readable)
            );
        }

        matcher.appendTail(result);

        return result.toString();
    }

    @Override
    public void onDestroy() {

        if (textToSpeech != null) {

            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        super.onDestroy();
    }
}


/*
package com.example.myrealityspeaker.service;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public class NotificationReaderService
        extends NotificationListenerService {

    private static final String TAG = "MyRealitySpeaker";

    // Reemplaza este valor por el paquete real
    // de la aplicación que quieres escuchar.
    private static final String TARGET_PACKAGE = "com.nequi.MobileApp";

    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    @Override
    public void onCreate() {
        super.onCreate();

        textToSpeech = new TextToSpeech(
                getApplicationContext(),
                status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = textToSpeech.setLanguage(
                                new Locale("es", "CO")
                        );

                        ttsReady =
                                result != TextToSpeech.LANG_MISSING_DATA
                                        && result != TextToSpeech.LANG_NOT_SUPPORTED;

                        if (!ttsReady) {
                            Log.w(TAG,
                                    "Idioma español no disponible");
                        }
                    } else {
                        Log.e(TAG,
                                "No se pudo inicializar TextToSpeech");
                    }
                }
        );
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "Escucha de notificaciones conectada");
    }

    @Override
    public void onNotificationPosted(
            StatusBarNotification sbn) {

        // Ignorar notificaciones de otras aplicaciones.
        if (!TARGET_PACKAGE.equals(sbn.getPackageName())) {
            return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        if (extras == null) {
            return;
        }

        CharSequence title = extras.getCharSequence(
                Notification.EXTRA_TITLE);

        CharSequence message = extras.getCharSequence(
                Notification.EXTRA_BIG_TEXT);

        // Si no hay texto ampliado, intentar con el texto normal.
        if (message == null || message.toString().isBlank()) {
            message = extras.getCharSequence(
                    Notification.EXTRA_TEXT);
        }

        StringBuilder speech = new StringBuilder();

        if (title != null && !title.toString().isBlank()) {
            speech.append(title.toString().trim());
        }

        if (message != null && !message.toString().isBlank()) {
            if (speech.length() > 0) {
                speech.append(". ");
            }

            speech.append(message.toString().trim());
        }

        String text = speech.toString().trim();

        if (text.isBlank()) {
            return;
        }

        // No registrar el contenido de la notificación.
        if (ttsReady && textToSpeech != null) {
            textToSpeech.speak(
                    text,
                    TextToSpeech.QUEUE_ADD,
                    null,
                    "notification-" + sbn.getKey()
            );
        }
    }

    @Override
    public void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        super.onDestroy();
    }
}
*/
