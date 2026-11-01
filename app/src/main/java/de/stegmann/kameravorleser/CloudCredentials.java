package de.stegmann.kameravorleser;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Keine Schlüssel im Repository, Klartext-Preferences oder Android-Backup. */
final class CloudCredentials {
    private static final String ALIAS = "pokopia-cloud-tts-v1";
    private final AtomicFile file;
    CloudCredentials(Context context) {
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "cloud-tts.bin"));
    }

    synchronized String[] read() throws Exception {
        byte[] all;
        try { all = file.readFully(); }
        catch (java.io.FileNotFoundException missing) { return new String[]{"", ""}; }
        if (all.length < 29) throw new java.io.IOException("Zugangsspeicher beschädigt");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
        JSONObject data = new JSONObject(new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8));
        return new String[]{data.optString("fish", ""), data.optString("google", "")};
    }

    synchronized void write(String fish, String google) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(new JSONObject().put("fish", fish).put("google", google)
                .toString().getBytes(StandardCharsets.UTF_8));
        FileOutputStream stream = file.startWrite();
        try {
            stream.write(cipher.getIV());
            stream.write(encrypted);
            file.finishWrite(stream);
        } catch (Exception error) {
            file.failWrite(stream);
            throw error;
        }
    }

    synchronized void clear() { file.delete(); }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
