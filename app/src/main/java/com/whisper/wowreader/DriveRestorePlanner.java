package com.whisper.wowreader;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parser/naming rules for streaming Drive restore. */
final class DriveRestorePlanner {
    private static final Pattern BOOK=Pattern.compile("^wow_book_([0-9a-fA-F]{64})\\.(epub|pdf)$");
    private static final Pattern COVER=Pattern.compile("^wow_cover_([0-9a-fA-F]{64})\\.jpg$");
    private DriveRestorePlanner(){}

    static final class BookObject {
        final String hash, extension;
        BookObject(String hash,String extension){this.hash=hash;this.extension=extension;}
    }

    static BookObject parseBookObject(String name){
        if(name==null)return null;
        Matcher m=BOOK.matcher(name.trim());
        if(!m.matches())return null;
        return new BookObject(m.group(1).toLowerCase(Locale.ROOT),m.group(2).toLowerCase(Locale.ROOT));
    }

    static String parseCoverHash(String name){
        if(name==null)return "";
        Matcher m=COVER.matcher(name.trim());
        return m.matches()?m.group(1).toLowerCase(Locale.ROOT):"";
    }

    static String chooseRemoteId(String current,String restored){
        String next=restored==null?"":restored.trim();
        if(!next.isEmpty())return next;
        return current==null?"":current.trim();
    }

    static String safeLocalName(String preferred,BookObject object){
        if(object==null)return "";
        String fallback=object.hash+"."+object.extension;
        if(preferred==null)return fallback;
        String n=preferred.trim();
        int slash=Math.max(n.lastIndexOf('/'),n.lastIndexOf('\\'));
        if(slash>=0)n=n.substring(slash+1);
        if(n.isEmpty()||".".equals(n)||"..".equals(n)||n.indexOf('\0')>=0)return fallback;
        String lower=n.toLowerCase(Locale.ROOT);
        if(!lower.endsWith("."+object.extension))return fallback;
        return n;
    }
}
