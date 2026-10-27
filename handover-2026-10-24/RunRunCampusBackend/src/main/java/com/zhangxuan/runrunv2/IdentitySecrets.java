package com.zhangxuan.runrunv2;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IdentitySecrets {
    private final byte[] encryptionKey;
    private final byte[] hashKey;
    private final SecureRandom random = new SecureRandom();
    public IdentitySecrets(@Value("${rrc.identity.encryption-key}") String encryption,
                           @Value("${rrc.identity.hash-key}") String hash) {
        try {
            encryptionKey = Base64.getDecoder().decode(encryption);
            hashKey = Base64.getDecoder().decode(hash);
            if (encryptionKey.length != 32 || hashKey.length < 32 || java.util.Arrays.equals(encryptionKey, hashKey)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) { throw new IllegalStateException("Independent identity keys must be configured (base64, 32 bytes)"); }
    }
    String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    String hash(String purpose, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((purpose + "\0" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Identity digest unavailable"); }
    }
    String encrypt(String appId, String openid) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(appId.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(openid.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) { throw new IllegalStateException("Identity encryption unavailable"); }
    }
}
