package com.company.dss.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import lombok.experimental.UtilityClass;

/**
 * Calcul de la signature DSS V8 — 5 chaînages MD5 (doc officielle section 3.1.2).
 */
@UtilityClass
public class DssSignatureUtils {

    public static String computeSignature(String username, String password, String realm, String randomKey) {
        String temp1 = md5(password);
        String temp2 = md5(username + temp1);
        String temp3 = md5(temp2);
        String temp4 = md5(username + ":" + realm + ":" + temp3);
        return md5(temp4 + ":" + randomKey);
    }

    public static String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 non disponible", e);
        }
    }
}
