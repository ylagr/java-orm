package com.javaorm.core.converter;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 开箱即用的 AES-CBC + Base64 加解密策略抽象基类。
 * 子类仅需实现 getKey() 与可选的 getIv()。
 */
public abstract class AesBase64EncryptionStrategy implements EncryptionStrategy {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    /**
     * 获取 16/24/32 字节密钥。
     */
    protected abstract byte[] getKey();

    /**
     * 获取 16 字节 IV 偏移向量（默认使用密钥前 16 字节）。
     */
    protected byte[] getIv() {
        byte[] key = getKey();
        byte[] iv = new byte[16];
        System.arraycopy(key, 0, iv, 0, Math.min(key.length, 16));
        return iv;
    }

    @Override
    public Object encrypt(String propertyName, Object plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            String text = plaintext.toString();
            SecretKeySpec keySpec = new SecretKeySpec(getKey(), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(getIv());
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed for property: " + propertyName, e);
        }
    }

    @Override
    public Object decrypt(String propertyName, Object ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            String cipherTextStr = ciphertext.toString();
            byte[] cipherBytes = Base64.getDecoder().decode(cipherTextStr);
            SecretKeySpec keySpec = new SecretKeySpec(getKey(), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(getIv());
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] original = cipher.doFinal(cipherBytes);
            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed for property: " + propertyName, e);
        }
    }
}
