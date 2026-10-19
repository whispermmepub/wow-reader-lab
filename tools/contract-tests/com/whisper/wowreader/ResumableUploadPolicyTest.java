package com.whisper.wowreader;
public final class ResumableUploadPolicyTest {
    public static void main(String[] args) {
        same(0,ResumableUploadPolicy.acknowledgedOffset(null,9000));
        same(0,ResumableUploadPolicy.acknowledgedOffset("",9000));
        same(43,ResumableUploadPolicy.acknowledgedOffset("bytes=0-42",9000));
        same(4096,ResumableUploadPolicy.acknowledgedOffset("bytes=0-4095",9000));
        same(9000,ResumableUploadPolicy.acknowledgedOffset("bytes=0-8999",9000));
        invalid("bytes=4-4095",9000);invalid("bytes=0-9000",9000);invalid("bytes=0--1",9000);
        invalid("bytes=0-9223372036854775807",9000);invalid("Range: bytes=0-10",9000);
        same(1,ResumableUploadPolicy.stalledAcknowledgements(4096,0,0));
        same(2,ResumableUploadPolicy.stalledAcknowledgements(0,0,1));
        same(0,ResumableUploadPolicy.stalledAcknowledgements(0,4096,2));
        same(3,ResumableUploadPolicy.MAX_STALLED_ACKS);
        System.out.println("RESUMABLE_UPLOAD_POLICY_TEST_PASS 13");
    }
    private static void invalid(String range,long total){try{ResumableUploadPolicy.acknowledgedOffset(range,total);throw new AssertionError("Accepted invalid acknowledgement: "+range);}catch(IllegalArgumentException expected){}}
    private static void same(long expected,long actual){if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
}
