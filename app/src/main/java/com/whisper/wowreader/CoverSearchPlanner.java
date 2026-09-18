package com.whisper.wowreader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class CoverSearchPlanner {
    private CoverSearchPlanner() {}

    static List<String> googleQueries(String title,String author,String isbn) {
        String t=clean(title), a=clean(author), i=clean(isbn);
        LinkedHashSet<String> q=new LinkedHashSet<>();
        if(!i.isEmpty()) q.add("isbn:"+i);
        if(!t.isEmpty()&&!a.isEmpty()) q.add(t+" "+a);
        if(!t.isEmpty()) q.add(t);
        if(!t.isEmpty()) q.add("intitle:"+t);
        if(t.isEmpty()&&!a.isEmpty()) q.add(a);
        return new ArrayList<>(q);
    }

    static String openLibraryQuery(String title,String author,String isbn) {
        String t=clean(title), a=clean(author), i=clean(isbn);
        if(!i.isEmpty()) return "isbn:"+i;
        if(!t.isEmpty()&&!a.isEmpty()) return t+" "+a;
        if(!t.isEmpty()) return t;
        return a;
    }

    static String webImageQuery(String title,String author,String isbn) {
        String t=clean(title), a=clean(author), i=clean(isbn);
        if(!t.isEmpty()&&!a.isEmpty()) return t+" "+a+" book cover";
        if(!t.isEmpty()) return t+" book cover";
        if(!i.isEmpty()) return i+" book cover";
        if(!a.isEmpty()) return a+" book cover";
        return "book cover";
    }

    static String clean(String s){ return s==null?"":s.trim(); }
}
