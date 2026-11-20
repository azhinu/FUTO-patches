package futo.enhancements;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
/** Secrets are excluded from FUTO exports and encrypted with a non-exportable Android Keystore key. */
public final class Secrets {
    private static final String ALIAS="futo.enhancements.credentials.v1";
    private static synchronized javax.crypto.SecretKey key() throws GeneralSecurityException,java.io.IOException {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if(!ks.containsAlias(ALIAS)) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            g.generateKey();
        }
        return (javax.crypto.SecretKey)ks.getKey(ALIAS,null);
    }
    public static void put(String name,String value) throws GeneralSecurityException,java.io.IOException {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key());
        String encoded=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);
        Config.prefs().edit().putString("secret_"+name,encoded).apply();
    }
    public static String get(String name) throws GeneralSecurityException,java.io.IOException {
        String v=Config.prefs().getString("secret_"+name,""); if(v.isEmpty()) return "";
        try {
            String[] parts=v.split(":",2);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
            return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
        } catch(IllegalArgumentException | ArrayIndexOutOfBoundsException e) { throw new GeneralSecurityException("Could not decrypt credentials"); }
    }
}
