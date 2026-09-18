package com.whisper.wowreader;

public final class DriveAppPropertyPolicyTest {
    private static void ok(boolean v,String m){ if(!v) throw new AssertionError(m); }
    public static void main(String[] args){
        String hash="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        ok(DriveAppPropertyPolicy.validContentHash(hash),"64-char SHA-256 must fit");
        String burmese="မြန်မာစာအုပ်နာမည်အရှည်ကြီးအရှည်ကြီးအရှည်ကြီးအရှည်ကြီးအရှည်ကြီး.epub";
        ok(!DriveAppPropertyPolicy.fits("originalName",burmese),"long Myanmar filename must be rejected from Drive properties");
        ok(DriveAppPropertyPolicy.fits("contentHash",hash),"contentHash property must stay within 124 bytes");
        System.out.println("DRIVE_APP_PROPERTY_POLICY_TEST_PASS");
    }
}
