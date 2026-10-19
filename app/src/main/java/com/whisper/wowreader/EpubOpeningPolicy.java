package com.whisper.wowreader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java opening helpers: bounded probes and a small, validated metadata cache. */
final class EpubOpeningPolicy {
    static final int CACHE_VERSION = 2;
    static final int PROBE_BYTES = 64 * 1024;
    static final int MAX_CACHE_RECORDS = 512;
    private EpubOpeningPolicy() {}

    static byte[] probe(File file) throws IOException {
        if (file == null || !file.isFile()) return new byte[0];
        long length = file.length();
        int first = (int) Math.min(PROBE_BYTES, length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(first + PROBE_BYTES);
        try (InputStream in = new FileInputStream(file)) {
            byte[] b = new byte[8192]; int left = first, n;
            while (left > 0 && (n = in.read(b, 0, Math.min(left, b.length))) > 0) { out.write(b, 0, n); left -= n; }
        }
        if (length > PROBE_BYTES * 2L) {
            try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
                in.seek(length - PROBE_BYTES);
                byte[] b = new byte[8192]; int left = PROBE_BYTES, n;
                while (left > 0 && (n = in.read(b, 0, Math.min(left, b.length))) > 0) { out.write(b, 0, n); left -= n; }
            }
        }
        return out.toByteArray();
    }

    static boolean hasReadableContent(File file) {
        try {
            if (file == null || !file.isFile() || file.length() == 0) return false;
            String raw = new String(probe(file), StandardCharsets.UTF_8);
            // The probe is intentionally bounded. A blank prefix and suffix are
            // inconclusive: content may be in the middle of a large chapter.
            if (hasReadableMarkup(raw)) return true;
            return hasReadableContentStreaming(file);
        } catch (Exception ignored) {
            // Do not discard a chapter merely because malformed input defeated the
            // detector; the renderer remains the authority for such documents.
            return true;
        }
    }

    private static boolean hasReadableMarkup(String raw) {
        String visible = raw.replaceAll("(?is)<!--.*?-->", " ")
                .replaceAll("(?is)<head\\b[^>]*>.*?</head\\s*>", " ")
                .replaceAll("(?is)<script\\b[^>]*>.*?</script\\s*>", " ")
                .replaceAll("(?is)<style\\b[^>]*>.*?</style\\s*>", " ")
                .replaceAll("(?is)<[^>]*>", " ");
        visible = decodeEntities(visible).replace('\u00a0', ' ');
        if (!visible.replaceAll("[\\p{Punct}\\p{javaWhitespace}]+", "").isEmpty()) return true;
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.matches("(?s).*<(img|image|svg|math|table|video|audio|object|embed|canvas)(\\s|>|/).*") ) return true;
        // An incomplete but renderable document is retained. A complete XHTML
        // shell with an empty body remains eligible for the full scan below.
        boolean hasBody = lower.matches("(?s).*<body\\b[^>]*>.*");
        boolean closedBody = lower.matches("(?s).*?</body\\s*>.*");
        return !hasBody || !closedBody;
    }

    private static boolean hasReadableContentStreaming(File file) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder tag = new StringBuilder(256), entity = new StringBuilder(32);
            boolean inTag = false, inComment = false, inEntity = false;
            int commentTail = 0, rawDepth = 0, ch;
            while ((ch = reader.read()) != -1) {
                char c = (char) ch;
                if (inComment) {
                    if (commentTail == 0 && c == '-') commentTail = 1;
                    else if (commentTail == 1 && c == '-') commentTail = 2;
                    else if (commentTail == 2 && c == '>') { inComment = false; commentTail = 0; }
                    else commentTail = c == '-' ? 1 : 0;
                    continue;
                }
                if (inTag) {
                    if (tag.length() < 8192) tag.append(c);
                    if (c == '>') {
                        String t = tag.toString().toLowerCase(Locale.ROOT).trim();
                        if (t.startsWith("!--")) inComment = true;
                        else {
                            boolean closing = t.startsWith("/");
                            int begin = closing ? 1 : 0;
                            while (begin < t.length() && Character.isWhitespace(t.charAt(begin))) begin++;
                            int end = begin;
                            while (end < t.length() && Character.isLetterOrDigit(t.charAt(end))) end++;
                            String name = t.substring(begin, end);
                            if (!closing && (name.equals("img") || name.equals("image") || name.equals("svg") || name.equals("math") || name.equals("table") || name.equals("video") || name.equals("audio") || name.equals("object") || name.equals("embed") || name.equals("canvas"))) return true;
                            if (name.equals("head") || name.equals("script") || name.equals("style")) {
                                if (closing) rawDepth = Math.max(0, rawDepth - 1); else rawDepth++;
                            }
                        }
                        inTag = false; tag.setLength(0);
                    }
                    continue;
                }
                if (c == '<') {
                    inTag = true; tag.setLength(0); continue;
                }
                if (rawDepth > 0) continue;
                if (inEntity) {
                    if (entity.length() < 32) entity.append(c);
                    if (c == ';') {
                        String e = entity.toString();
                        if (e.matches("&(#([0-9]+|[xX][0-9a-fA-F]+));")) return true;
                        inEntity = false; entity.setLength(0);
                    } else if (entity.length() >= 32) { inEntity = false; entity.setLength(0); }
                    continue;
                }
                if (c == '&') { inEntity = true; entity.setLength(0); entity.append(c); continue; }
                if (isReadableCharacter(c)) return true;
            }
            return false;
        }
    }

    private static boolean isReadableCharacter(char c) {
        if (Character.isWhitespace(c) || Character.isISOControl(c)) return false;
        int type = Character.getType(c);
        return type != Character.CONNECTOR_PUNCTUATION && type != Character.DASH_PUNCTUATION
                && type != Character.START_PUNCTUATION && type != Character.END_PUNCTUATION
                && type != Character.INITIAL_QUOTE_PUNCTUATION && type != Character.FINAL_QUOTE_PUNCTUATION
                && type != Character.OTHER_PUNCTUATION && type != Character.MATH_SYMBOL
                && type != Character.CURRENCY_SYMBOL && type != Character.MODIFIER_SYMBOL
                && type != Character.OTHER_SYMBOL && type != Character.FORMAT;
    }

    private static String decodeEntities(String s) {
        Matcher m = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);|&nbsp;", Pattern.CASE_INSENSITIVE).matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String v = m.group();
            String replacement = " ";
            try {
                if (!v.equalsIgnoreCase("&nbsp;")) {
                    String n = m.group(1); int radix = n.charAt(0) == 'x' || n.charAt(0) == 'X' ? 16 : 10;
                    int cp = Integer.parseInt(n.substring(radix == 16 ? 1 : 0), radix);
                    replacement = new String(Character.toChars(cp));
                }
            } catch (Exception ignored) {}
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out); return out.toString();
    }

    static String title(File file, int number) {
        try {
            String raw = new String(probe(file), StandardCharsets.UTF_8);
            String value = firstTagText(raw, "title");
            if (!meaningful(value)) for (String tag : new String[]{"h1", "h2", "h3", "h4"}) {
                value = firstTagText(raw, tag); if (meaningful(value) && value.length() <= 160) break;
            }
            if (!meaningful(value)) value = filename(file == null ? null : file.getName());
            return meaningful(value) ? clean(value) : "Chapter " + number;
        } catch (Exception ignored) { return "Chapter " + number; }
    }
    static String heading(File file) {
        try { String raw = new String(probe(file), StandardCharsets.UTF_8); for (String tag : new String[]{"h1","h2","h3","h4"}) { String v=firstTagText(raw,tag); if(meaningful(v)&&v.length()<=180)return clean(v); } } catch(Exception ignored) {} return null;
    }
    private static String firstTagText(String raw,String tag){Matcher m=Pattern.compile("(?is)<"+tag+"\\b[^>]*>(.*?)</"+tag+"\\s*>").matcher(raw);return m.find()?clean(m.group(1).replaceAll("(?is)<[^>]+>"," ")):null;}
    private static String filename(String n){if(n==null||n.isEmpty())return null;int d=n.lastIndexOf('.');String s=clean((d>0?n.substring(0,d):n).replace('_',' ').replace('-',' '));return meaningful(s)&&!s.matches("(?i)(chapter|section|part|page|text|content|item|file)\\s*\\d+")?s:null;}
    private static boolean meaningful(String s){if(s==null)return false;String x=clean(s);return x!=null&&!x.isEmpty()&&!x.matches("(?i)(unknown|untitled|undefined|null|none|no title|title|chapter|section|part)\\s*\\d*");}
    private static String clean(String s){return s==null?null:s.replaceAll("&nbsp;"," ").replaceAll("\\s+"," ").trim();}

    static final class Cached {
        String opfPath,title,author;
        final List<String> candidatePaths=new ArrayList<>(),paths=new ArrayList<>(),titles=new ArrayList<>(),tocTitles=new ArrayList<>(),tocFragments=new ArrayList<>();
        final List<Integer> tocIndices=new ArrayList<>();
    }
    static File cacheFile(File root){return new File(root,".wow_epub_info_v"+CACHE_VERSION);}
    static Cached read(File root,String opfPath,File opf,List<File> current){return read(root,opfPath,opf,current,Collections.singletonList(opf));}
    static Cached read(File root,String opfPath,File opf,List<File> current,List<File> dependencies){
        if(root==null||opf==null||current==null||current.size()>MAX_CACHE_RECORDS)return null; File f=cacheFile(root); if(!f.isFile())return null;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            if(!("v="+CACHE_VERSION).equals(r.readLine())||!opfPath.equals(r.readLine()))return null;
            int d=Integer.parseInt(r.readLine()); if(d<0||d>MAX_CACHE_RECORDS||dependencies.size()!=d)return null;
            for(int i=0;i<d;i++){String[] p=fields(r.readLine());if(p.length<2)return null;File x=safe(root,unescape(p[0]));if(x==null||!x.equals(dependencies.get(i))||!signature(x).equals(p[1]))return null;}
            int n=Integer.parseInt(r.readLine());if(n!=current.size()||n>MAX_CACHE_RECORDS)return null;
            Cached c=new Cached(); c.opfPath=opfPath;
            for(int i=0;i<n;i++){String[] p=fields(r.readLine());if(p.length<2)return null;File x=safe(root,unescape(p[0]));if(x==null||!x.equals(current.get(i))||!signature(x).equals(p[1]))return null;c.candidatePaths.add(p[0]);}
            c.title=unescape(r.readLine());c.author=unescape(r.readLine());int selected=Integer.parseInt(r.readLine());if(selected<0||selected>MAX_CACHE_RECORDS)return null;
            for(int i=0;i<selected;i++){String[] p=fields(r.readLine());if(p.length<3)return null;File x=safe(root,unescape(p[0]));if(x==null||!signature(x).equals(p[1]))return null;c.paths.add(unescape(p[0]));c.titles.add(unescape(p[2]));}
            int tc=Integer.parseInt(r.readLine());if(tc<0||tc>MAX_CACHE_RECORDS)return null;for(int i=0;i<tc;i++){String[] p=fields(r.readLine());if(p.length<3)return null;try{c.tocIndices.add(Integer.parseInt(p[0]));}catch(Exception e){return null;}c.tocTitles.add(unescape(p[1]));c.tocFragments.add(unescape(p[2]));}
            for(Integer idx:c.tocIndices)if(idx==null||idx<0||idx>=selected)return null;
            return c;
        }catch(Exception ignored){return null;}
    }
    static void write(File root,String opfPath,File opf,Cached c){write(root,opfPath,opf,c,Collections.singletonList(opf));}
    static void write(File root,String opfPath,File opf,Cached c,List<File> dependencies){
        if(root==null||opf==null||c==null||c.candidatePaths.size()>MAX_CACHE_RECORDS||c.paths.size()>MAX_CACHE_RECORDS)return;File f=cacheFile(root),tmp=new File(root,f.getName()+".tmp");
        try(Writer w=new OutputStreamWriter(new FileOutputStream(tmp),StandardCharsets.UTF_8)){
            w.write("v="+CACHE_VERSION+"\n"+opfPath+"\n"+dependencies.size()+"\n");for(File dep:dependencies)w.write(escape(rel(root,dep))+"\t"+signature(dep)+"\n");
            w.write(c.candidatePaths.size()+"\n");for(String path:c.candidatePaths){File x=safe(root,path);w.write(escape(path)+"\t"+signature(x)+"\n");}
            w.write(escape(c.title)+"\n"+escape(c.author)+"\n"+c.paths.size()+"\n");for(int i=0;i<c.paths.size();i++){File x=safe(root,c.paths.get(i));w.write(escape(c.paths.get(i))+"\t"+signature(x)+"\t"+escape(i<c.titles.size()?c.titles.get(i):null)+"\n");}
            w.write(c.tocTitles.size()+"\n");for(int i=0;i<c.tocTitles.size();i++){int idx=i<c.tocIndices.size()&&c.tocIndices.get(i)!=null?c.tocIndices.get(i):0;w.write(idx+"\t"+escape(c.tocTitles.get(i))+"\t"+escape(i<c.tocFragments.size()?c.tocFragments.get(i):null)+"\n");}
        }catch(Exception e){tmp.delete();return;}if(!tmp.renameTo(f)){f.delete();tmp.renameTo(f);}
    }
    private static String[] fields(String line){return line==null?new String[0]:line.split("\\t",-1);}
    private static String rel(File root,File f){try{return root.getCanonicalFile().toURI().relativize(f.getCanonicalFile().toURI()).getPath();}catch(Exception e){return "";}}
    private static String signature(File f){return f==null?"-":f.length()+":"+f.lastModified();}
    private static String escape(String s){if(s==null)return "";byte[] b=s.getBytes(StandardCharsets.UTF_8);StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format(Locale.ROOT,"%02x",v&255));return x.toString();}
    private static String unescape(String s){if(s==null||s.isEmpty())return null;try{byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return new String(b,StandardCharsets.UTF_8);}catch(Exception e){return null;}}
    private static String[] split(String line,int ignored){if(line==null)return null;String[] p=line.split("\\t",-1);return p.length>=3?p:new String[]{line.split("\\t",-1)[0],line.split("\\t",-1).length>1?line.split("\\t",-1)[1]:"",""};}
    private static File safe(File root, String rel) {
        if (rel == null || rel.startsWith("/") || rel.indexOf('\\') >= 0) return null;
        String[] parts = rel.split("/");
        for (String part : parts) if ("..".equals(part)) return null;
        try { File r=root.getCanonicalFile(), x=new File(r,rel).getCanonicalFile(); return x.getPath().startsWith(r.getPath()+File.separator) ? x : null; }
        catch (Exception e) { return null; }
    }
}
