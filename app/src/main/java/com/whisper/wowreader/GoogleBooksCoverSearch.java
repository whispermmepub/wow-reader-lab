package com.whisper.wowreader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GoogleBooksCoverSearch {
    static final class Result {
        String title="";
        String author="";
        String imageUrl="";
        String source="";
    }

    static List<Result> search(String title,String author,String isbn)throws Exception {
        if(CoverSearchPlanner.clean(title).isEmpty() &&
                CoverSearchPlanner.clean(author).isEmpty() &&
                CoverSearchPlanner.clean(isbn).isEmpty())
            throw new Exception("Enter a title, author or ISBN");

        LinkedHashMap<String,Result> merged=new LinkedHashMap<>();
        Exception googleError=null, openLibraryError=null;

        // Google Books: do not require strict intitle/inauthor matching.
        // Broad free-text queries are much better for translated titles and Myanmar metadata.
        try {
            List<String> queries=CoverSearchPlanner.googleQueries(title,author,isbn);
            int calls=0;
            for(String q:queries) {
                if(calls>=3 || merged.size()>=50) break;
                addGoogle(merged,q);
                calls++;
            }
        } catch(Exception e) { googleError=e; }

        // Open Library broadens coverage and supplies covers by stable Cover ID/ISBN.
        try {
            addOpenLibrary(merged,CoverSearchPlanner.openLibraryQuery(title,author,isbn));
        } catch(Exception e) { openLibraryError=e; }

        if(merged.isEmpty()) {
            if(googleError!=null && openLibraryError!=null)
                throw new Exception("Cover search failed. Check internet and try a different/original title.");
            return new ArrayList<>();
        }
        return new ArrayList<>(merged.values());
    }

    private static void addGoogle(Map<String,Result> out,String query)throws Exception {
        if(query==null||query.trim().isEmpty()) return;
        String u="https://www.googleapis.com/books/v1/volumes?q="+
                URLEncoder.encode(query,"UTF-8")+"&maxResults=40&printType=books";
        JSONObject root=getJson(u,"WoWReader/2.19");
        JSONArray items=root.optJSONArray("items");
        if(items==null) return;
        for(int i=0;i<items.length() && out.size()<70;i++) {
            JSONObject item=items.optJSONObject(i); if(item==null) continue;
            JSONObject v=item.optJSONObject("volumeInfo"); if(v==null) continue;
            JSONObject imgs=v.optJSONObject("imageLinks"); if(imgs==null) continue;
            String image=first(imgs,"extraLarge","large","medium","small","thumbnail","smallThumbnail");
            if(image.isEmpty()) continue;
            if(image.startsWith("http://")) image="https://"+image.substring(7);
            Result r=new Result();
            r.title=v.optString("title","");
            JSONArray aa=v.optJSONArray("authors");
            if(aa!=null&&aa.length()>0) r.author=aa.optString(0,"");
            r.imageUrl=image;
            r.source="Google Books";
            out.putIfAbsent(normalizeImageKey(image),r);
        }
    }

    private static void addOpenLibrary(Map<String,Result> out,String query)throws Exception {
        if(query==null||query.trim().isEmpty()) return;
        String u="https://openlibrary.org/search.json?q="+URLEncoder.encode(query,"UTF-8")+
                "&fields=title,author_name,cover_i,isbn,key&limit=40";
        JSONObject root=getJson(u,"WoWReader/2.19 (book cover search)");
        JSONArray docs=root.optJSONArray("docs");
        if(docs==null) return;
        for(int i=0;i<docs.length() && out.size()<90;i++) {
            JSONObject d=docs.optJSONObject(i); if(d==null) continue;
            String image="";
            long coverId=d.optLong("cover_i",0L);
            if(coverId>0) {
                image="https://covers.openlibrary.org/b/id/"+coverId+"-L.jpg?default=false";
            } else {
                JSONArray isbns=d.optJSONArray("isbn");
                if(isbns!=null&&isbns.length()>0) {
                    String iv=isbns.optString(0,"").trim();
                    if(!iv.isEmpty()) image="https://covers.openlibrary.org/b/isbn/"+
                            URLEncoder.encode(iv,"UTF-8")+"-L.jpg?default=false";
                }
            }
            if(image.isEmpty()) continue;
            Result r=new Result();
            r.title=d.optString("title","");
            JSONArray authors=d.optJSONArray("author_name");
            if(authors!=null&&authors.length()>0) r.author=authors.optString(0,"");
            r.imageUrl=image;
            r.source="Open Library";
            out.putIfAbsent(normalizeImageKey(image),r);
        }
    }

    private static JSONObject getJson(String url,String userAgent)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent",userAgent);
        int code=c.getResponseCode();
        if(code<200||code>=300) {
            InputStream err=c.getErrorStream();
            if(err!=null) try{while(err.read()!=-1){} }finally{err.close();}
            c.disconnect();
            throw new Exception("Book cover search failed ("+code+")");
        }
        byte[] data;
        try(InputStream in=c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream()){
            byte[] buf=new byte[16384]; int n;
            while((n=in.read(buf))>0) {
                if(b.size()+n>4*1024*1024) throw new Exception("Cover search response is too large");
                b.write(buf,0,n);
            }
            data=b.toByteArray();
        } finally { c.disconnect(); }
        return new JSONObject(new String(data,StandardCharsets.UTF_8));
    }

    private static String first(JSONObject o,String...keys){
        for(String k:keys){
            String v=o.optString(k,"");
            if(v!=null&&!v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private static String normalizeImageKey(String s) {
        if(s==null) return "";
        return s.replace("http://","https://").replace("&zoom=1","").trim();
    }
}
