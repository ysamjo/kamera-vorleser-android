package de.stegmann.kameravorleser;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.Image;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.view.ScaleGestureDetector;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.button.MaterialButton;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import static de.stegmann.kameravorleser.TextRules.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONException;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private static final long ANALYSIS_INTERVAL_MS = 180;
    private static final int MAX_REMEMBERED_LINES = 500;
    private static final String KEY_DIALOGUE_HISTORY = "dialogueHistoryV2";
    private static final int MAX_QUEUED_UTTERANCES = 2;
    private static final float STORY_TOP_RATIO = 0.74f;
    private static final float STORY_LEFT_RATIO = 0.21f;
    private static final float STORY_RIGHT_RATIO = 0.79f;
    private static final float SPEAKER_TOP_RATIO = 0.70f;
    private static final float SPEAKER_BOTTOM_RATIO = 0.81f;
    private static final long SPEAKER_HOLD_MS = 2500L;
    private static final int REQUIRED_STABLE_FRAMES = 2;
    private static final long STABLE_WINDOW_MS = 1100L;
    private static final String NARRATOR = "Erzähler";
    private static final String PREFS_NAME = "KameraVorleserPrefs";
    private static final String KEY_SOUNDS_ENABLED = "soundsEnabled";

    // OCR & Text-Erkennungs-Schwellwerte
    private static final int MIN_SPEAKER_NAME_LENGTH = 2;
    private static final int MAX_SPEAKER_NAME_LENGTH = 28;
    private static final int MAX_SPEAKER_NAME_WORDS = 3;

    // UI-Region-Schwellwerte
    private static final float SPEAKER_MAX_CENTER_X = 0.62f; // Speaker-Namen maximal bei 62% horizontal
    private static final float SPEAKER_MAX_WIDTH = 0.36f; // Speaker-Namen maximal 36% Bildbreite
    private static final float STORY_GAP_MAX_HEIGHT_RATIO = 0.14f; // Max Abstand Speaker->Story: 14% Bildhöhe
    private static final float STORY_GAP_MAX_HORIZONTAL_RATIO = 0.25f; // Max horizontaler Abstand: 25%

    // Blaue Dialog-Erkennung (YUV-Farb-Schwellwerte)
    private static final int BLUE_MIN_VALUE = 70; // Mindest-Blau-Wert
    private static final int BLUE_OVER_RED_THRESHOLD = 24; // Blau muss mindestens 24 mehr als Rot sein
    private static final int BLUE_OVER_GREEN_THRESHOLD = 8; // Blau muss mindestens 8 mehr als Grün sein
    private static final float BLUE_PIXEL_RATIO = 0.36f; // Mindestens 36% der Pixel müssen blau sein
    private static final int BLUE_MIN_SAMPLES = 15; // Mindestens 15 Pixel müssen gesampelt werden
    private static final float BLUE_REGION_WIDTH_RATIO = 0.18f; // Breite der Sampling-Region
    private static final float BLUE_REGION_HEIGHT_RATIO = 0.10f; // Höhe der Sampling-Region

    // TTS/Voice-Schwellwerte
    // Extreme Tonhöhen/Tempi verzerren installierte Offline-Stimmen.
    private static final int VIBRATION_DURATION_MS = 35; // Vibration bei neuem Text: 35ms

    private PreviewView previewView;
    private TextView statusText;
    private TextView speakerText;
    private TextView recognizedText;
    private TextView confidenceText;
    private MaterialButton pauseButton;
    private MaterialButton zoomButton;
    private Camera camera;

    private final AtomicBoolean ocrBusy = new AtomicBoolean(false);
    private final SpeechQueue speechQueue = new SpeechQueue();
    private final DialogueHistory dialogueHistory = new DialogueHistory(MAX_REMEMBERED_LINES);
    private final List<Voice> germanVoices = new ArrayList<>();
    private ExecutorService cameraExecutor;
    private TextRecognizer textRecognizer;
    private TextToSpeech textToSpeech;
    private volatile boolean ttsReady = false;
    private volatile boolean paused = false;
    private volatile boolean foreground = false;
    private volatile boolean destroyed = false;
    private final AtomicInteger analysisGeneration = new AtomicInteger();
    private volatile long lastAnalysisAt = 0L;
    private String currentSpeaker = NARRATOR;
    private long lastSpeakerSeenAt = 0L;
    private String pendingStoryKey = "";
    private int pendingStoryHits = 0;
    private long pendingStorySeenAt = 0L;
    private float speechRate = 1.15f;
    private String cachedSpeaker = "";
    private float cachedPitch = 1.0f;
    private float cachedRate = 1.0f;
    private float volume = 0.8f;
    private Vibrator vibrator;

    private String previousSpeaker = "";

    // Dialog & Satzfluss-Tracking (Wachstum, Klick-Erweiterung, Duplikatvermeidung)
    private String lastSpokenVisible = "";
    private String lastSpokenNormalized = "";
    private String lastSpokenSpeaker = "";
    private long lastSpokenAt = 0L;

    // Sound feedback
    private boolean soundsEnabled = true;
    private ToneGenerator toneGenerator;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    showStatus("Kamerazugriff wird benötigt", false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-Edge & Immersive Statusleiste im Querformat
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        hideSystemBars();

        setContentView(R.layout.activity_main);

        // Window Insets Listener: Verhindert, dass HUD-Elemente jemals über Statusleiste,
        // Kamera-Notch (DisplayCutout) oder Navigationsleiste ragen
        View mainRoot = findViewById(R.id.mainRoot);
        ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );

            View topPanel = findViewById(R.id.topPanel);
            if (topPanel != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) topPanel.getLayoutParams();
                lp.topMargin = insets.top + dpToPx(8);
                lp.leftMargin = insets.left + dpToPx(12);
                lp.rightMargin = insets.right + dpToPx(12);
                topPanel.setLayoutParams(lp);
            }

            View dialogPanel = findViewById(R.id.dialogPanel);
            if (dialogPanel != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) dialogPanel.getLayoutParams();
                lp.leftMargin = insets.left + dpToPx(16);
                dialogPanel.setLayoutParams(lp);
            }

            View controlPanel = findViewById(R.id.controlPanel);
            if (controlPanel != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) controlPanel.getLayoutParams();
                lp.rightMargin = insets.right + dpToPx(14);
                controlPanel.setLayoutParams(lp);
            }

            return windowInsets;
        });

        previewView = findViewById(R.id.previewView);
        statusText = findViewById(R.id.statusText);
        speakerText = findViewById(R.id.speakerText);
        recognizedText = findViewById(R.id.recognizedText);
        confidenceText = findViewById(R.id.confidenceText);
        pauseButton = findViewById(R.id.pauseButton);
        zoomButton = findViewById(R.id.zoomButton);
        zoomButton.setOnClickListener(view -> toggleZoom());
        setupPinchToZoom();
        SeekBar speedSeekBar = findViewById(R.id.speedSeekBar);
        SeekBar volumeSeekBar = findViewById(R.id.volumeSeekBar);
        Button repeatButton = findViewById(R.id.repeatButton);
        MaterialButton soundsToggleButton = findViewById(R.id.soundsToggleButton);

        cameraExecutor = Executors.newSingleThreadExecutor();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        textToSpeech = new TextToSpeech(this, this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // Load sound preference
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadDialogueHistory(prefs);
        soundsEnabled = prefs.getBoolean(KEY_SOUNDS_ENABLED, true);
        speechRate = prefs.getFloat("speechRate", 1.15f);
        volume = prefs.getFloat("volume", 0.8f);
        if (Float.isNaN(speechRate) || Float.isInfinite(speechRate)) speechRate = 1.15f;
        if (Float.isNaN(volume) || Float.isInfinite(volume)) volume = 0.8f;
        speechRate = Math.max(0.75f, Math.min(1.75f, speechRate));
        volume = Math.max(0f, Math.min(1f, volume));
        speedSeekBar.setProgress(Math.round((speechRate - 0.75f) * 100));
        volumeSeekBar.setProgress(Math.round(volume * 100));

        // Initialize tone generator for audio feedback
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 50);

        // Update sounds toggle button state
        updateSoundsToggleButton(soundsToggleButton);
        soundsToggleButton.setOnClickListener(view -> {
            toggleSounds();
            updateSoundsToggleButton(soundsToggleButton);
        });

        pauseButton.setOnClickListener(view -> {
            paused = !paused;
            invalidateAnalysis();
            pauseButton.setText(paused ? "WEITER" : "PAUSE");
            pauseButton.setIconResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);
            if (paused) {
                stopSpeech();
                showStatus("Pausiert", false);
            } else {
                showStatus("Suche Text …", true);
            }
        });

        repeatButton.setOnClickListener(view -> {
            invalidateAnalysis();
            stopSpeech();
            // Nur die sichtbare Zeile erneut zulassen, die Historie bleibt erhalten.
            dialogueHistory.allowRepeat(currentSpeaker, recognizedText.getText().toString());
            resetLastSpoken();
            showStatus("Aktueller Dialog kann erneut gelesen werden", true);
        });

        speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;

                speechRate = 0.75f + (progress / 100f);
                prefs.edit().putFloat("speechRate", speechRate).apply();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                volume = progress / 100f;
                prefs.edit().putFloat("volume", volume).apply();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    public void onInit(int status) {
        // Auch ein synchroner Init-Callback muss die Zuweisung des TTS-Objekts abwarten.
        new android.os.Handler(getMainLooper()).post(() -> initializeSpeech(status));
    }

    private void initializeSpeech(int status) {
        if (destroyed) return;
        if (status != TextToSpeech.SUCCESS) {
            showStatus("Sprachausgabe ist nicht verfügbar", false);
            return;
        }

        int languageResult = textToSpeech.setLanguage(Locale.GERMANY);
        ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA
                && languageResult != TextToSpeech.LANG_NOT_SUPPORTED;
        if (ttsReady) {
            loadGermanVoices();
            ttsReady = !germanVoices.isEmpty()
                    && textToSpeech.setVoice(germanVoices.get(0)) == TextToSpeech.SUCCESS;
        }
        textToSpeech.setSpeechRate(speechRate);
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {
                runOnUiThread(() -> {
                    if (!destroyed && foreground && !paused && speechQueue.contains(id)) {
                        showStatus("Lese vor …", true);
                    }
                });
            }
            @Override public void onDone(String id) { finishSpeech(id, false); }
            @Override public void onError(String id) { finishSpeech(id, true); }
            @Override public void onStop(String id, boolean interrupted) { finishSpeech(id, true); }
        });

        if (!ttsReady) showStatus("Deutsche Offline-Stimme fehlt – bitte in Android installieren", false);
    }

    private void stopSpeech() {
        // Unterbrochene Zeilen sind nicht vollständig vorgelesen und dürfen erneut starten.
        for (String key : speechQueue.drain()) {
            dialogueHistory.finish(key, false);
            if (key.equals(DialogueHistory.key(lastSpokenSpeaker, lastSpokenNormalized))) {
                resetLastSpoken();
            }
        }
        // Vor stop() entwerten: Engines dürfen sofort oder später zurückrufen.
        if (textToSpeech != null) textToSpeech.stop();
    }

    private void resetLastSpoken() {
        lastSpokenVisible = "";
        lastSpokenNormalized = "";
        lastSpokenSpeaker = "";
        lastSpokenAt = 0L;
    }

    private void finishSpeech(String id, boolean failed) {
        new android.os.Handler(getMainLooper()).post(() -> {
            String key = speechQueue.finish(id);
            if (key == null || destroyed) return;
            if (dialogueHistory.finish(key, !failed)) saveDialogueHistory();
            if (failed) {
                if (key.equals(DialogueHistory.key(lastSpokenSpeaker, lastSpokenNormalized))) {
                    resetLastSpoken();
                }
            }
            if (foreground && !paused && speechQueue.size() == 0) {
                showStatus(failed ? "Sprachausgabe fehlgeschlagen – versuche erneut" : "Suche Text …", !failed);
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                if (destroyed) return;
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                camera = provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                );
                if (camera != null) {
                    camera.getCameraInfo().getZoomState().observe(this, zoomState -> {
                        if (zoomState != null && zoomButton != null) {
                            float ratio = zoomState.getZoomRatio();
                            zoomButton.setText(String.format(Locale.US, "%.1fx", ratio));
                        }
                    });
                }
                showStatus("Suche Text …", true);
            } catch (Exception error) {
                showStatus("Kamera konnte nicht gestartet werden", false);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void invalidateAnalysis() {
        analysisGeneration.incrementAndGet();
        pendingStoryKey = "";
        pendingStoryHits = 0;
        lastAnalysisAt = 0L;
    }

    private boolean acceptsFrame(int generation) {
        return !destroyed && foreground && !paused && generation == analysisGeneration.get();
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        int generation = analysisGeneration.get();
        long now = android.os.SystemClock.elapsedRealtime();
        if (!acceptsFrame(generation) || now - lastAnalysisAt < ANALYSIS_INTERVAL_MS
                || !ocrBusy.compareAndSet(false, true)) {
            imageProxy.close();
            return;
        }
        lastAnalysisAt = now;
        // Ein einziger Abschluss besitzt Frame und Busy-Flag, auch bei synchronen Fehlern.
        FrameRelease release = new FrameRelease(() -> {
            try { imageProxy.close(); }
            finally {
                ocrBusy.set(false);
                if (destroyed) textRecognizer.close();
            }
        });
        try {
            if (!acceptsFrame(generation) || imageProxy.getImage() == null) {
                release.close();
                return;
            }
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), rotation);
            int width = rotation == 90 || rotation == 270 ? imageProxy.getHeight() : imageProxy.getWidth();
            int height = rotation == 90 || rotation == 270 ? imageProxy.getWidth() : imageProxy.getHeight();
            textRecognizer.process(image).addOnCompleteListener(ContextCompat.getMainExecutor(this), task -> {
                try {
                    if (!acceptsFrame(generation)) return;
                    if (task.isSuccessful()) {
                        handleRecognizedText(task.getResult(), imageProxy, rotation, width, height);
                    } else {
                        showStatus("Texterkennung fehlgeschlagen – versuche erneut", false);
                    }
                } catch (RuntimeException error) {
                    showStatus("Text konnte nicht verarbeitet werden – versuche erneut", false);
                } finally {
                    release.close();
                }
            });
        } catch (RuntimeException error) {
            release.close();
            runOnUiThread(() -> {
                if (acceptsFrame(generation)) showStatus("Texterkennung fehlgeschlagen – versuche erneut", false);
            });
        }
    }

    private void handleRecognizedText(
            Text result,
            ImageProxy imageProxy,
            int rotation,
            int frameWidth,
            int frameHeight
    ) {
        if (paused) return;

        long now = System.currentTimeMillis();
        if (result.getText().isBlank()) {
            clearPendingStoryIfStale(now);
            runOnUiThread(() -> confidenceText.setVisibility(android.view.View.GONE));
            return;
        }

        List<OcrLine> storyLines = new ArrayList<>();

        // OCR-Konfidenz berechnen (Durchschnitt aller TextLines)
        float totalConfidence = 0f;
        int confidenceCount = 0;
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String visible = line.getText().trim().replaceAll("\\s+", " ");
                Rect box = line.getBoundingBox();
                boolean normalStory = isInStoryRegion(box, frameWidth, frameHeight);
                boolean blueStory = isBlueBackedLine(imageProxy, box, rotation);
                String normalized = normalize(visible);
                if ((!normalStory && !blueStory)
                        || normalized.length() < 3
                        || isActionLabel(visible)
                        || isNonStoryNotification(normalized)) {
                    continue;
                }
                storyLines.add(new OcrLine(visible, normalized, box));
                // Konfidenz aus der TextLine sammeln (TextBlock hat keine getConfidence() Methode)
                Float lineConfidence = line.getConfidence();
                if (lineConfidence != null) {
                    totalConfidence += lineConfidence;
                    confidenceCount++;
                }
            }
        }

        // OCR-Konfidenz anzeigen
        if (confidenceCount > 0) {
            float avgConfidence = totalConfidence / confidenceCount;
            int percent = Math.round(avgConfidence * 100);
            runOnUiThread(() -> {
                confidenceText.setText("OCR: " + percent + "%");
                confidenceText.setVisibility(android.view.View.VISIBLE);
            });
        }

        String detectedSpeaker = detectSpeaker(result, storyLines, frameWidth, frameHeight);
        if (detectedSpeaker != null) {
            if (!normalize(currentSpeaker).equals(normalize(detectedSpeaker))) invalidateAnalysis();
            currentSpeaker = detectedSpeaker;
            lastSpeakerSeenAt = now;
        } else if (now - lastSpeakerSeenAt > SPEAKER_HOLD_MS) {
            if (!currentSpeaker.equals(NARRATOR)) invalidateAnalysis();
            currentSpeaker = NARRATOR;
        }

        String speakerKey = normalize(currentSpeaker);
        storyLines.removeIf(line -> line.normalized.equals(speakerKey));

        if (storyLines.isEmpty()) {
            clearPendingStoryIfStale(now);
            speakerText.setText("BEREIT");
            recognizedText.setText("Warte auf Pokopia-Dialog …");
            runOnUiThread(() -> confidenceText.setVisibility(android.view.View.GONE));
            return;
        }

        String visibleStory = DialogueText.withoutSpeaker(
                assembleStoryText(storyLines), currentSpeaker);
        if (visibleStory.isBlank()) {
            clearPendingStoryIfStale(now);
            return;
        }
        if (detectedSpeaker == null && !currentSpeaker.equals(NARRATOR)
                && !DialogueText.sameDialogue(recognizedText.getText().toString(), visibleStory)) {
            currentSpeaker = NARRATOR;
            invalidateAnalysis();
        }
        String normalizedStory = normalize(visibleStory);

        VoiceProfile visibleProfile = voiceProfileFor(normalize(currentSpeaker));
        speakerText.setText(
                currentSpeaker.toUpperCase(Locale.GERMAN)
                        + "  ·  "
                        + voiceProfileLabel(visibleProfile)
        );
        recognizedText.setText(visibleStory);

        if (isNonStoryNotification(normalizedStory)) {
            pendingStoryKey = "";
            pendingStoryHits = 0;
            speakerText.setText("SYSTEMMELDUNG");
            showStatus("Ignoriert", true);
            return;
        }

        if (!looksLikeGermanStory(visibleStory) || !isStableStory(visibleStory, now)) {
            showStatus("Prüfe Dialog …", true);
            return;
        }

        if (speechQueue.size() > 0 && !normalize(currentSpeaker).equals(cachedSpeaker)) stopSpeech();
        if (dialogueHistory.wasSpoken(currentSpeaker, normalizedStory)) return;

        // Klick-Erweiterung: Wenn der Text nach einem Klick um einen neuen Satzteil gewachsen ist
        boolean isExtension = !lastSpokenNormalized.isEmpty()
                && normalize(currentSpeaker).equals(lastSpokenSpeaker)
                && (now - lastSpokenAt < 10000L)
                && normalizedStory.startsWith(lastSpokenNormalized)
                && normalizedStory.length() > lastSpokenNormalized.length() + 3;

        if (isExtension) {
            boolean ttsSpeaking = speechQueue.size() > 0;
            if (ttsSpeaking) {
                // Vorheriges unvollständiges Vorlesen abbrechen und vollen, runden Gesamtsatz sprechen
                stopSpeech();
                if (!speak(visibleStory, currentSpeaker, normalizedStory)) return;
            } else {
                String trimmedLast = lastSpokenVisible != null ? lastSpokenVisible.trim() : "";
                char lastChar = !trimmedLast.isEmpty() ? trimmedLast.charAt(trimmedLast.length() - 1) : ' ';
                if (lastChar == '.' || lastChar == '!' || lastChar == '?' || lastChar == '…' || lastChar == ':') {
                    String suffix = extractExtensionSuffix(visibleStory, lastSpokenVisible);
                    if (!suffix.isBlank()) {
                        if (!speak(suffix, currentSpeaker, normalizedStory)) return;
                    }
                } else {
                    // Satz war unvollständig getrennt -> vollen Satz flüssig sprechen
                    if (!speak(visibleStory, currentSpeaker, normalizedStory)) return;
                }
            }
            lastSpokenVisible = visibleStory;
            lastSpokenNormalized = normalizedStory;
            lastSpokenSpeaker = normalize(currentSpeaker);
            lastSpokenAt = now;
            showStatus("Lese vor …", true);
            return;
        }

        // Neuen Dialog vollständig und flüssig sprechen
        if (ttsReady && (speechQueue.size() < MAX_QUEUED_UTTERANCES
                || !normalize(currentSpeaker).equals(cachedSpeaker))) {
            if (!speak(visibleStory, currentSpeaker, normalizedStory)) return;
            lastSpokenVisible = visibleStory;
            lastSpokenNormalized = normalizedStory;
            lastSpokenSpeaker = normalize(currentSpeaker);
            lastSpokenAt = now;
            showStatus("Lese vor …", true);
        }
    }

    private boolean speak(String text, String speaker, String dialogueKey) {
        if (!ttsReady || paused || !foreground || destroyed) return false;
        // Aussprache-Wörterbuch anwenden
        String corrected = PronunciationDictionary.apply(text);

        // Emotionale Variation basierend auf Satzzeichen
        float emotionPitchMod = 1.0f;
        float emotionRateMod = 1.0f;
        if (corrected.contains("!")) {
            emotionPitchMod = 1.08f;
            emotionRateMod = 1.06f;
        } else if (corrected.contains("?")) {
            emotionPitchMod = 1.05f;
            emotionRateMod = 0.96f;
        } else if (corrected.endsWith("...") || corrected.contains(" ... ")) {
            emotionRateMod = 0.92f;
        }

        // Natürliche Pausen einfügen
        String withPauses = PronunciationDictionary.addNaturalPauses(corrected);

        if (withPauses.length() > TextToSpeech.getMaxSpeechInputLength()) {
            showStatus("Dialog ist für die Sprachausgabe zu lang", false);
            return false;
        }

        // Neue Figur: veraltete Warteschlangen-Einträge dürfen nicht die neue Stimme erben.
        if (speechQueue.size() > 0 && !normalize(speaker).equals(cachedSpeaker)) {
            stopSpeech();
        }
        applyVoiceForSpeaker(speaker, emotionPitchMod, emotionRateMod);
        if (!ttsReady) return false;

        // Audio-Feedback bei neuem Text
        playNewTextSound();

        // Vibrations-Feedback bei neuem Text
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(VIBRATION_DURATION_MS);
            }
        }

        // Lautstärke über Bundle setzen
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);

        boolean accepted = speechQueue.submit(DialogueHistory.key(speaker, dialogueKey), id -> textToSpeech.speak(
                withPauses, TextToSpeech.QUEUE_ADD, params, id) == TextToSpeech.SUCCESS);
        if (!accepted) {
            showStatus("Sprachausgabe fehlgeschlagen – versuche erneut", false);
            return false;
        }
        dialogueHistory.begin(DialogueHistory.key(speaker, dialogueKey));
        return true;
    }

    private String detectSpeaker(
            Text result,
            List<OcrLine> storyLines,
            int frameWidth,
            int frameHeight
    ) {
        OcrLine reference = null;
        for (OcrLine line : storyLines) {
            if (line.box == null) continue;
            if (reference == null
                    || line.visible.length() > reference.visible.length()
                    || line.box.width() > reference.box.width() * 1.25f) {
                reference = line;
            }
        }

        if (reference != null && reference.box != null) {
            String relative = detectSpeakerNearStory(result, reference, frameWidth, frameHeight);
            if (relative != null) {
                String relativeKey = normalize(relative);
                boolean isStoryLine = false;
                for (OcrLine storyLine : storyLines) {
                    if (storyLine.normalized.equals(relativeKey)) {
                        isStoryLine = true;
                        break;
                    }
                }
                if (!isStoryLine) return relative;
            }
        }

        String best = null;
        int bestTop = Integer.MIN_VALUE;
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                String candidate = line.getText().trim().replaceAll("\\s+", " ");
                if (!isSpeakerLabel(box, frameWidth, frameHeight, candidate)) continue;
                if (box != null && box.top > bestTop) {
                    bestTop = box.top;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private String detectSpeakerNearStory(
            Text result,
            OcrLine storyReference,
            int frameWidth,
            int frameHeight
    ) {
        String best = null;
        int smallestGap = Integer.MAX_VALUE;
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                String candidate = line.getText().trim().replaceAll("\\s+", " ");
                if (box == null || !isShortSpeakerName(candidate, box, frameWidth)) continue;

                int gap = storyReference.box.top - box.bottom;
                int horizontalDistance = Math.abs(box.left - storyReference.box.left);
                if (gap < -storyReference.box.height()
                        || gap > frameHeight * STORY_GAP_MAX_HEIGHT_RATIO
                        || horizontalDistance > frameWidth * STORY_GAP_MAX_HORIZONTAL_RATIO) {
                    continue;
                }
                if (normalize(candidate).equals(storyReference.normalized)) continue;
                if (gap < smallestGap) {
                    smallestGap = gap;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private boolean isShortSpeakerName(String text, Rect box, int frameWidth) {
        String normalized = normalize(text);
        if (normalized.length() < MIN_SPEAKER_NAME_LENGTH
                || normalized.length() > MAX_SPEAKER_NAME_LENGTH
                || isActionLabel(text)) return false;
        int words = normalized.split("\\s+").length;
        return words <= MAX_SPEAKER_NAME_WORDS && box.width() < frameWidth * SPEAKER_MAX_WIDTH;
    }

    private boolean isSpeakerLabel(Rect box, int frameWidth, int frameHeight, String text) {
        if (box == null || text.length() < MIN_SPEAKER_NAME_LENGTH
                || text.length() > MAX_SPEAKER_NAME_LENGTH) return false;
        int words = normalize(text).split("\\s+").length;
        float centerY = box.centerY() / (float) frameHeight;
        float centerX = box.centerX() / (float) frameWidth;
        float width = box.width() / (float) frameWidth;
        return words <= MAX_SPEAKER_NAME_WORDS
                && centerY >= SPEAKER_TOP_RATIO
                && centerY < SPEAKER_BOTTOM_RATIO
                && centerX < SPEAKER_MAX_CENTER_X
                && width < SPEAKER_MAX_WIDTH
                && !isActionLabel(text);
    }

    private boolean isInStoryRegion(Rect box, int frameWidth, int frameHeight) {
        if (box == null) return false;
        float centerX = box.centerX() / (float) frameWidth;
        return box.centerY() >= frameHeight * STORY_TOP_RATIO
                && centerX >= STORY_LEFT_RATIO
                && centerX <= STORY_RIGHT_RATIO;
    }







    private boolean isStableStory(String text, long now) {
        String key = normalize(text);
        if (key.isEmpty()) return false;

        // 1. Wachstums-Erkennung (Typewriter-Effekt / Streaming-Text):
        // Wenn der neue Text mit dem bisherigen beginnt und länger ist, tippt das Spiel noch!
        if (!pendingStoryKey.isBlank()
                && key.startsWith(pendingStoryKey)
                && key.length() > pendingStoryKey.length()) {
            pendingStoryKey = key;
            pendingStoryHits = 1;
            pendingStorySeenAt = now;
            // Solange der Text aktiv wächst, noch nicht als stabil werten
            return false;
        }

        // 2. Stabilitätsprüfung: Bleibt der Text über REQUIRED_STABLE_FRAMES unverändert?
        boolean same = !pendingStoryKey.isBlank()
                && now - pendingStorySeenAt <= STABLE_WINDOW_MS
                && similarStoryText(pendingStoryKey, key);
        if (same) {
            pendingStoryHits++;
        } else {
            pendingStoryKey = key;
            pendingStoryHits = 1;
        }
        pendingStorySeenAt = now;
        return pendingStoryHits >= REQUIRED_STABLE_FRAMES;
    }



    private void clearPendingStoryIfStale(long now) {
        if (now - pendingStorySeenAt <= 700L) return;
        pendingStoryKey = "";
        pendingStoryHits = 0;
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private boolean isBlueBackedLine(ImageProxy imageProxy, Rect box, int rotation) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null || box == null || mediaImage.getPlanes().length < 3) return false;

        Image.Plane yPlane = mediaImage.getPlanes()[0];
        Image.Plane uPlane = mediaImage.getPlanes()[1];
        Image.Plane vPlane = mediaImage.getPlanes()[2];
        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();
        int rawWidth = imageProxy.getWidth();
        int rawHeight = imageProxy.getHeight();
        int uprightWidth = rotation == 90 || rotation == 270 ? rawHeight : rawWidth;
        int uprightHeight = rotation == 90 || rotation == 270 ? rawWidth : rawHeight;

        int blue = 0;
        int sampled = 0;
        int halfWidth = Math.max(box.width() / 2 + box.height(), (int) (uprightWidth * BLUE_REGION_WIDTH_RATIO));
        int halfHeight = Math.max(box.height() * 2, (int) (uprightHeight * BLUE_REGION_HEIGHT_RATIO));
        int left = box.centerX() - halfWidth;
        int right = box.centerX() + halfWidth;
        int top = box.centerY() - halfHeight;
        int bottom = box.centerY() + halfHeight;

        for (int row = 0; row < 5; row++) {
            int uprightY = top + Math.max(1, bottom - top) * row / 4;
            for (int column = 0; column < 9; column++) {
                int uprightX = left + Math.max(1, right - left) * column / 8;
                int[] raw = toRawCoordinates(uprightX, uprightY, rotation, rawWidth, rawHeight);
                int rawX = raw[0];
                int rawY = raw[1];
                if (rawX < 0 || rawY < 0 || rawX >= rawWidth || rawY >= rawHeight) continue;

                int yIndex = rawY * yPlane.getRowStride() + rawX * yPlane.getPixelStride();
                int uvX = rawX / 2;
                int uvY = rawY / 2;
                int uIndex = uvY * uPlane.getRowStride() + uvX * uPlane.getPixelStride();
                int vIndex = uvY * vPlane.getRowStride() + uvX * vPlane.getPixelStride();
                if (yIndex >= yBuffer.limit() || uIndex >= uBuffer.limit() || vIndex >= vBuffer.limit()) {
                    continue;
                }

                int y = (yBuffer.get(yIndex) & 0xff) - 16;
                int u = (uBuffer.get(uIndex) & 0xff) - 128;
                int v = (vBuffer.get(vIndex) & 0xff) - 128;
                int red = clamp((298 * y + 409 * v + 128) >> 8);
                int green = clamp((298 * y - 100 * u - 208 * v + 128) >> 8);
                int blueValue = clamp((298 * y + 516 * u + 128) >> 8);
                sampled++;
                if (blueValue > BLUE_MIN_VALUE && blueValue > red + BLUE_OVER_RED_THRESHOLD && blueValue > green + BLUE_OVER_GREEN_THRESHOLD) blue++;
            }
        }
        return sampled >= BLUE_MIN_SAMPLES && blue >= sampled * BLUE_PIXEL_RATIO;
    }

    private int[] toRawCoordinates(
            int uprightX,
            int uprightY,
            int rotation,
            int rawWidth,
            int rawHeight
    ) {
        switch (rotation) {
            case 90:
                return new int[]{uprightY, rawHeight - 1 - uprightX};
            case 180:
                return new int[]{rawWidth - 1 - uprightX, rawHeight - 1 - uprightY};
            case 270:
                return new int[]{rawWidth - 1 - uprightY, uprightX};
            default:
                return new int[]{uprightX, uprightY};
        }
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void loadGermanVoices() {
        List<Voice> allGerman = new ArrayList<>();
        if (textToSpeech.getVoices() == null) return;
        for (Voice voice : textToSpeech.getVoices()) {
            Locale locale = voice.getLocale();
            if (locale == null || !"de".equals(locale.getLanguage())
                    || voice.isNetworkConnectionRequired()
                    || (voice.getFeatures() != null && voice.getFeatures().contains(
                            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))) continue;
            allGerman.add(voice);
        }
        if (allGerman.stream().anyMatch(voice -> "DE".equalsIgnoreCase(voice.getLocale().getCountry()))) {
            allGerman.removeIf(voice -> !"DE".equalsIgnoreCase(voice.getLocale().getCountry()));
        }
        // Stimmen derselben Sprachregion nach Qualität und Latenz sortieren.
        allGerman.sort(Comparator
                .comparingInt((Voice voice) -> "DE".equalsIgnoreCase(voice.getLocale().getCountry()) ? 0 : 1)
                .thenComparing(Comparator.comparingInt(Voice::getQuality).reversed())
                .thenComparingInt(Voice::getLatency)
                .thenComparing(Voice::getName));
        germanVoices.clear();
        germanVoices.addAll(allGerman);
    }

    private void applyVoiceForSpeaker(String speaker, float emotionPitchMod, float emotionRateMod) {
        String key = normalize(speaker);
        VoiceProfile profile = voiceProfileFor(key);

        // Check for speaker change and play sound
        if (!key.equals(previousSpeaker) && !previousSpeaker.isEmpty()) {
            playSpeakerChangeSound();
        }
        previousSpeaker = key;

        float targetPitch = SpeakerVoice.pitch(key, profile.pitch, emotionPitchMod);
        float targetRate = SpeakerVoice.rate(key, profile.rateFactor, speechRate, emotionRateMod);

        // Voice-Caching: Nur ändern wenn wirklich nötig
        boolean needsUpdate = !cachedSpeaker.equals(key)
                || Float.compare(cachedPitch, targetPitch) != 0
                || Float.compare(cachedRate, targetRate) != 0;

        if (needsUpdate) {
            if (!germanVoices.isEmpty()) {
                int voiceIndex = Math.floorMod(profile.voiceSlot, germanVoices.size());
                if (textToSpeech.setVoice(germanVoices.get(voiceIndex)) != TextToSpeech.SUCCESS) {
                    ttsReady = false;
                    showStatus("Offline-Stimme konnte nicht geladen werden", false);
                    return;
                }
            }

            if (textToSpeech.setPitch(targetPitch) != TextToSpeech.SUCCESS
                    || textToSpeech.setSpeechRate(targetRate) != TextToSpeech.SUCCESS) {
                ttsReady = false;
                showStatus("Stimmprofil konnte nicht eingestellt werden", false);
                return;
            }

            cachedSpeaker = key;
            cachedPitch = targetPitch;
            cachedRate = targetRate;
        }
    }

    private float getSpeakerRate(String speakerKey, VoiceProfile profile) {
        return SpeakerVoice.rate(speakerKey, profile.rateFactor, speechRate, 1f);
    }

    private VoiceProfile voiceProfileFor(String speaker) {
        if (speaker.isBlank() || speaker.equals(normalize(NARRATOR))) {
            return new VoiceProfile(0, 0.98f, 0.96f);
        }

        if (matchesAny(speaker, "professor", "tangoloss", "tangrowth")) {
            return new VoiceProfile(0, 0.80f, 0.90f); // warm, erfahren
        }
        if (matchesAny(speaker, "peakychu")) {
            return new VoiceProfile(1, 1.12f, 0.90f); // klein, sanft und etwas müde
        }
        if (matchesAny(speaker, "chef dente")) {
            return new VoiceProfile(0, 0.90f, 0.98f); // herzlich und genussfreudig
        }
        if (matchesAny(speaker, "tinkmaster")) {
            return new VoiceProfile(2, 0.98f, 1.06f); // selbstbewusst und tatkräftig
        }
        if (matchesAny(speaker, "dj rotom")) {
            return new VoiceProfile(3, 1.02f, 1.13f); // besonders lebhaft-elektronisch
        }
        if (matchesAny(speaker, "schiggy", "squirtle")) {
            return new VoiceProfile(2, 1.16f, 1.06f); // fröhlich und aufgeweckt
        }
        if (matchesAny(speaker, "bisasam", "bulbasaur")) {
            return new VoiceProfile(1, 1.07f, 0.99f); // jung, ruhig und freundlich
        }
        if (matchesAny(speaker, "glumanda", "charmander")) {
            return new VoiceProfile(3, 1.12f, 1.07f); // warm und energiegeladen
        }
        if (matchesAny(speaker, "plinfa", "piplup")) {
            return new VoiceProfile(2, 1.21f, 1.03f); // klein und stolz
        }
        if (matchesAny(speaker, "felori", "sprigatito")) {
            return new VoiceProfile(1, 1.14f, 0.97f); // weich und gelassen
        }
        if (matchesAny(speaker, "pikachu", "pichu")) {
            return new VoiceProfile(3, 1.28f, 1.12f); // sehr hell, energetisch und ikonisch
        }
        if (matchesAny(speaker, "mooslaxo", "mosslax")) {
            return new VoiceProfile(0, 0.68f, 0.76f); // besonders schläfrig, träge und weich
        }
        if (matchesAny(speaker, "mooslaxo", "mosslax", "relaxo", "dragoran", "lapras",
                "bisaflor", "turtok", "chelterrar", "altaria", "wailord")) {
            return new VoiceProfile(0, 0.76f, 0.88f); // groß, weich und gemütlich
        }
        if (matchesAny(speaker, "onix", "stahlos", "meistagrif", "stalobor", "geowaz",
                "rihorn", "rizeros", "despotar", "volcanion", "groudon")) {
            return new VoiceProfile(0, 0.66f, 0.86f); // massiv und tief
        }
        if (matchesAny(speaker, "mewtu", "mewtwo", "entei", "raikou", "zapdos", "lavados",
                "ho oh", "lugia", "rayquaza", "dialga", "palkia")) {
            return new VoiceProfile(0, 0.72f, 0.88f); // ruhig und autoritär
        }
        if (matchesAny(speaker, "suicune", "mew", "celebi", "guardevoir", "galagladi",
                "vulnona", "milotic", "arktos")) {
            return new VoiceProfile(1, 1.00f, 0.92f); // ruhig und elegant
        }
        if (matchesAny(speaker, "driftlon", "drifloon", "nebulak", "alpollo", "gengar",
                "traunfugil", "zwirrlicht", "lichtel", "mimigma", "kryppuk")) {
            return new VoiceProfile(4, 0.82f, 0.86f); // geisterhaft - tiefer und mysteriöser
        }
        if (matchesAny(speaker, "rotom", "porygon", "magnetilo", "magneton", "metagross",
                "genesect", "golbit", "golgantes")) {
            return new VoiceProfile(3, 0.88f, 1.12f); // technisch - robotischer und präziser
        }
        if (matchesAny(speaker, "sichlor", "scyther", "praktibalk", "timburr", "rotomurf",
                "drilbur", "machollo", "maschock", "machomei", "panzaeron")) {
            return new VoiceProfile(1, 0.84f, 0.98f); // rau und tatkräftig
        }
        if (matchesAny(speaker, "mauzi", "meowth", "karpador", "magikarp", "unratütox",
                "trubbish", "schlaraffel", "greedent", "kleptifux")) {
            return new VoiceProfile(2, 1.10f, 1.08f); // frech und komisch
        }
        if (matchesAny(speaker, "farbeagle", "smeargle", "meister farbeagle", "smearguru")) {
            return new VoiceProfile(2, 1.02f, 0.96f); // kreativ und gelassen
        }
        if (matchesAny(speaker, "evoli", "eevee", "togepi", "emolga", "dedenne",
                "pummeluff", "wuffels", "hoppspross", "knospi", "mobai", "riolu")) {
            return new VoiceProfile(2, 1.18f, 1.06f); // klein, jung und lebhaft
        }
        if (matchesAny(speaker, "meikro", "rookidee", "staralili", "taubsi", "habitak",
                "waumpel", "raupy", "hornliu", "wommel")) {
            return new VoiceProfile(3, 1.30f, 1.14f); // sehr klein, piepsig und flink
        }

        // Auch noch nicht bekannte Figuren behalten anhand ihres Namens dauerhaft
        // ein klar unterscheidbares, Pokémon-typisches Profil.
        switch (Math.floorMod(speaker.hashCode(), 5)) {
            case 0:
                return new VoiceProfile(0, 0.65f, 0.84f); // sehr tief und langsam
            case 1:
                return new VoiceProfile(1, 0.82f, 0.92f); // tief und gemütlich
            case 2:
                return new VoiceProfile(2, 1.05f, 1.02f); // neutral-freundlich
            case 3:
                return new VoiceProfile(3, 1.20f, 1.11f); // hell und lebhaft
            default:
                return new VoiceProfile(4, 1.28f, 1.16f); // sehr hell und quirlig
        }
    }

    private float effectiveRate(VoiceProfile profile) {
        return getSpeakerRate(normalize(currentSpeaker), profile);
    }

    private String voiceProfileLabel(VoiceProfile profile) {
        float pitch = SpeakerVoice.pitch(normalize(currentSpeaker), profile.pitch, 1f);
        if (pitch <= 0.88f) return "SEHR TIEF";
        if (pitch <= 0.96f) return "TIEF";
        if (pitch >= 1.20f) return "SEHR HELL";
        if (pitch >= 1.08f) return "HELL";
        if (effectiveRate(profile) >= speechRate * 1.08f) return "FLOTT";
        return "SANFT";
    }

    private boolean matchesAny(String speaker, String... names) {
        for (String name : names) {
            if (speaker.equals(name)
                    || speaker.startsWith(name + " ")
                    || speaker.endsWith(" " + name)) {
                return true;
            }
        }
        return false;
    }

    private static final class OcrLine {
        final String visible;
        final String normalized;
        final Rect box;

        OcrLine(String visible, String normalized, Rect box) {
            this.visible = visible;
            this.normalized = normalized;
            this.box = box;
        }
    }

    private static final class VoiceProfile {
        final int voiceSlot;
        final float pitch;
        final float rateFactor;

        VoiceProfile(int voiceSlot, float pitch, float rateFactor) {
            this.voiceSlot = voiceSlot;
            this.pitch = pitch;
            this.rateFactor = rateFactor;
        }
    }

    private String assembleStoryText(List<OcrLine> lines) {
        List<StoryTextAssembler.Line> input = new ArrayList<>();
        if (lines != null) for (OcrLine line : lines) {
            if (line != null && line.box != null) {
                input.add(new StoryTextAssembler.Line(line.visible, line.box.left,
                        line.box.top, line.box.right, line.box.bottom));
            }
        }
        return StoryTextAssembler.assemble(input);
    }

    private String extractExtensionSuffix(String fullVisible, String prefixVisible) {
        String fullNorm = normalize(fullVisible);
        String prefixNorm = normalize(prefixVisible);
        if (!fullNorm.startsWith(prefixNorm)) {
            return fullVisible;
        }
        String[] fullWords = fullVisible.trim().split("\\s+");
        String[] prefixWords = prefixVisible.trim().split("\\s+");
        int prefixCount = prefixWords.length;
        if (prefixCount < fullWords.length) {
            StringBuilder suffix = new StringBuilder();
            for (int i = prefixCount; i < fullWords.length; i++) {
                if (suffix.length() > 0) suffix.append(" ");
                suffix.append(fullWords[i]);
            }
            return suffix.toString();
        }
        return "";
    }







    private void loadDialogueHistory(SharedPreferences prefs) {
        List<String> saved = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_DIALOGUE_HISTORY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String key = array.optString(i, "");
                if (!key.isEmpty()) saved.add(key);
            }
        } catch (JSONException ignored) {
            // Beschädigte Einstellungen dürfen die Kamera nicht blockieren.
        }
        dialogueHistory.restore(saved);
    }

    private void saveDialogueHistory() {
        JSONArray array = new JSONArray();
        for (String key : dialogueHistory.snapshot()) array.put(key);
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_DIALOGUE_HISTORY, array.toString()).apply();
    }

    private void showStatus(String message, boolean good) {
        statusText.setText("●  " + message);
        statusText.setTextColor(ContextCompat.getColor(this, good ? R.color.leaf : R.color.coral));
    }

    // === Sound Feedback ===

    private void playNewTextSound() {
        if (!soundsEnabled || toneGenerator == null) return;
        // Short pleasant chime: C6 note (1047 Hz) for 80ms
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE, 80);
    }

    private void playSpeakerChangeSound() {
        if (!soundsEnabled || toneGenerator == null) return;
        // Short whoosh/blip: two-tone sound
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
    }

    private void toggleSounds() {
        soundsEnabled = !soundsEnabled;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SOUNDS_ENABLED, soundsEnabled).apply();
    }

    private void updateSoundsToggleButton(MaterialButton button) {
        if (soundsEnabled) {
            button.setText("EIN");
            button.setIconResource(R.drawable.ic_volume);
            button.setBackgroundTintList(getColorStateList(R.color.gold));
        } else {
            button.setText("AUS");
            button.setIconResource(R.drawable.ic_volume_off);
            button.setBackgroundTintList(getColorStateList(R.color.navy_light));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        foreground = false;
        invalidateAnalysis();
        stopSpeech();
        super.onPause();
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.statusBars());
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void toggleZoom() {
        if (camera == null) return;
        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        if (zoomState == null) return;
        float currentRatio = zoomState.getZoomRatio();
        float maxZoom = zoomState.getMaxZoomRatio();
        float targetRatio;
        if (currentRatio < 1.8f) {
            targetRatio = Math.min(2.0f, maxZoom);
        } else {
            targetRatio = 1.0f;
        }
        camera.getCameraControl().setZoomRatio(targetRatio);
    }

    private void setupPinchToZoom() {
        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        if (camera == null) return true;
                        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                        if (zoomState == null) return true;
                        float currentRatio = zoomState.getZoomRatio();
                        float delta = detector.getScaleFactor();
                        float newRatio = Math.max(zoomState.getMinZoomRatio(),
                                Math.min(zoomState.getMaxZoomRatio(), currentRatio * delta));
                        camera.getCameraControl().setZoomRatio(newRatio);
                        return true;
                    }
                });
        previewView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        foreground = false;
        invalidateAnalysis();
        super.onDestroy();
        if (textToSpeech != null) {
            stopSpeech();
            textToSpeech.shutdown();
        }
        if (textRecognizer != null && !ocrBusy.get()) textRecognizer.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }
}
