package com.whisper.wowreader;

import java.nio.charset.StandardCharsets;

final class DriveAppPropertyPolicy {
    private DriveAppPropertyPolicy() {}
    static final int MAX_BYTES = 124;

    static boolean fits(String key,String value) {
        String k=key==null?"":key, v=value==null?"":value;
        return (k+v).getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    static boolean validContentHash(String hash) {
        if(hash==null || !hash.matches("[0-9a-fA-F]{64}")) return false;
        return fits("contentHash",hash);
    }
}
