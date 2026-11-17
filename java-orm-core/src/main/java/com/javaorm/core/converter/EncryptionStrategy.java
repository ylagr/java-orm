package com.javaorm.core.converter;

/**
 * 列级敏感数据加解密策略接口。
 */
public interface EncryptionStrategy {

    /**
     * 将明文数据加密为数据库存储密文。
     *
     * @param propertyName 属性名
     * @param plaintext    明文对象（如 String、Number 等）
     * @return 适合持久化的密文（通常为 String 或 byte[]）
     */
    Object encrypt(String propertyName, Object plaintext);

    /**
     * 将数据库读取的密文解密还原为明文。
     *
     * @param propertyName 属性名
     * @param ciphertext   数据库存储的密文
     * @return 解密还原后的明文对象
     */
    Object decrypt(String propertyName, Object ciphertext);
}
