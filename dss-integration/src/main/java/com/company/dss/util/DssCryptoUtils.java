package com.company.dss.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import lombok.experimental.UtilityClass;

/**
 * Crypto DSS V8 : RSA (clé publique plateforme) + AES-CBC pour le mot de passe MQ.
 */
@UtilityClass
public class DssCryptoUtils {

    public record AesCredentials(String secretKey, String secretVector) {
    }

    public static AesCredentials generateAesCredentials() {
        String secretKey = UUID.randomUUID().toString().replace("-", "");
        String secretVector = UUID.randomUUID().toString().replace("-", "").substring(16);
        return new AesCredentials(secretKey, secretVector);
    }

    public static String encryptForPlatform(String plaintext, String platformPublicKeyBase64) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(platformPublicKeyBase64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);

            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
            int blockSize = 245;
            byte[] result = new byte[0];
            for (int i = 0; i < data.length; i += blockSize) {
                int end = Math.min(i + blockSize, data.length);
                byte[] chunk = cipher.doFinal(data, i, end - i);
                result = concat(result, chunk);
            }
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Échec chiffrement RSA DSS : " + ex.getMessage(), ex);
        }
    }

    public static String decryptAesCbcHex(String hexCiphertext, String aesKey, String aesVector) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec iv = new IvParameterSpec(aesVector.getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
            byte[] decrypted = cipher.doFinal(hexToBytes(hexCiphertext));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Échec déchiffrement AES MQ : " + ex.getMessage(), ex);
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len / 2; i++) {
            int high = Integer.parseInt(hex.substring(i * 2, i * 2 + 1), 16);
            int low = Integer.parseInt(hex.substring(i * 2 + 1, i * 2 + 2), 16);
            result[i] = (byte) (high * 16 + low);
        }
        return result;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] out = new byte[left.length + right.length];
        System.arraycopy(left, 0, out, 0, left.length);
        System.arraycopy(right, 0, out, left.length, right.length);
        return out;
    }
}
