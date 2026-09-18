package com.whisper.wowreader;

public final class DriveRestorePlannerTest {
    private static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
    private static void eq(Object a,Object b,String m){if(a==null?b!=null:!a.equals(b))throw new AssertionError(m+" expected="+b+" actual="+a);}

    public static void main(String[] args){
        String hash="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        DriveRestorePlanner.BookObject epub=DriveRestorePlanner.parseBookObject("wow_book_"+hash+".epub");
        ok(epub!=null,"valid EPUB object must parse");
        eq(epub.hash,hash,"hash");
        eq(epub.extension,"epub","EPUB extension");

        DriveRestorePlanner.BookObject pdf=DriveRestorePlanner.parseBookObject("wow_book_"+hash.toUpperCase()+".pdf");
        ok(pdf!=null,"uppercase hash must parse");
        eq(pdf.hash,hash,"hash normalized lowercase");
        eq(pdf.extension,"pdf","PDF extension");

        ok(DriveRestorePlanner.parseBookObject("wow_cover_"+hash+".jpg")==null,"cover is not a book");
        ok(DriveRestorePlanner.parseBookObject("wow_book_short.epub")==null,"short hash rejected");
        ok(DriveRestorePlanner.parseBookObject("random.epub")==null,"unrelated file rejected");

        eq(DriveRestorePlanner.safeLocalName("မြန်မာ စာအုပ်.epub",epub),"မြန်မာ စာအုပ်.epub","Unicode filename preserved");
        eq(DriveRestorePlanner.safeLocalName("../outside.epub",epub),"outside.epub","path traversal stripped");
        eq(DriveRestorePlanner.safeLocalName("wrong.pdf",epub),hash+".epub","wrong extension falls back safely");
        eq(DriveRestorePlanner.safeLocalName("",epub),hash+".epub","empty name uses hash fallback");

        System.out.println("DRIVE_RESTORE_PLANNER_TEST_PASS");
    }
}
