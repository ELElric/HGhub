package com.ridu.plan.data;

/**
 * 内置的仓库只读凭据。以异或分片保存，避免在 APK 中出现明文。
 */
public final class TokenVault {
    private static final int[] KEY = {
            44, 225, 236, 172, 128, 38, 107, 171, 16, 74, 60, 77, 146, 114, 199, 27,
            232, 237, 200
    };

    private static final int[] DATA = {
            235, 25, 171, 105, 131, 90, 13, 37, 4, 161, 120, 79, 237, 165, 251, 140,
            21, 208, 230, 202, 39, 252, 122, 184, 91, 52, 100, 47, 148, 102, 58, 151,
            185, 227, 201, 35, 249, 133, 234, 37, 207, 102, 140, 84, 55, 82, 57, 130,
            75, 37, 151, 189, 252, 213, 62, 244, 221, 254, 112, 194, 28, 138, 82, 83,
            71, 19, 171, 99, 5, 187, 183, 255, 139, 63, 166, 249, 240, 114, 214, 105,
            249, 38, 57, 100, 43, 129, 110, 30, 175, 158, 194, 210, 17
    };

    private static String cached;

    private TokenVault() {
    }

    public static synchronized String get() {
        if (cached != null) return cached;
        byte[] reversed = new byte[DATA.length];
        for (int i = 0; i < DATA.length; i++) {
            reversed[i] = (byte) ((DATA[i] ^ 0x5A) & 0xFF);
        }
        StringBuilder sb = new StringBuilder(DATA.length);
        for (int i = 0; i < DATA.length; i++) {
            int cipher = reversed[DATA.length - 1 - i] & 0xFF;
            sb.append((char) (cipher ^ KEY[i % KEY.length]));
        }
        cached = sb.toString();
        return cached;
    }
}
