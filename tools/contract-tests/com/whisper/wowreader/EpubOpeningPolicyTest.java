package com.whisper.wowreader;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;

/** Plain-main contract checks; run with EpubOpeningPolicy and EpubUtil sources only. */
public final class EpubOpeningPolicyTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static File file(File root, String name, String text) throws Exception {
        File f = new File(root, name); try (FileWriter w = new FileWriter(f)) { w.write(text); } return f;
    }
    public static void main(String[] args) throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "epub-policy-contract-" + System.nanoTime());
        check(root.mkdirs(), "temporary root");
        File empty = file(root, "empty.xhtml", "<html><head><title>metadata</title></head><body> &nbsp; </body></html>");
        check(!EpubOpeningPolicy.hasReadableContent(empty), "empty XHTML excluded");
        check(EpubOpeningPolicy.hasReadableContent(file(root, "image.xhtml", "<html><body><img src='x'/></body></html>")), "image retained");
        check(EpubOpeningPolicy.hasReadableContent(file(root, "my.xhtml", "<body>မြန်မာ &#x1000; &#4096;</body>")), "Unicode/entities retained");
        check(EpubOpeningPolicy.hasReadableContent(file(root, "bad.xhtml", "<html><body>unfinished Myanmar မြန်မာ")), "malformed renderable retained");
        StringBuilder large = new StringBuilder(240000); large.append("<html><body>");
        while (large.length() < 100000) large.append(' ');
        large.append("visible only in the middle");
        while (large.length() < 220000) large.append(' ');
        large.append("</body></html>");
        File middle = file(root, "large.xhtml", large.toString());
        check(EpubOpeningPolicy.probe(middle).length <= 128 * 1024, "probe bounded");
        check(EpubOpeningPolicy.hasReadableContent(middle), "middle content retained after full scan");
        File opf = file(root, "package.opf", "opf"); File chapter = file(root, "chapter.xhtml", "<body>one</body>");
        EpubOpeningPolicy.Cached c = new EpubOpeningPolicy.Cached(); c.candidatePaths.add("chapter.xhtml"); c.paths.add("chapter.xhtml"); c.paths.add("chapter.xhtml"); c.titles.add("one"); c.titles.add("one");
        EpubOpeningPolicy.write(root, "package.opf", opf, c, Arrays.asList(opf));
        EpubOpeningPolicy.Cached hit = EpubOpeningPolicy.read(root, "package.opf", opf, Arrays.asList(chapter), Arrays.asList(opf));
        check(hit != null && hit.paths.size() == 2 && hit.paths.get(0).equals(hit.paths.get(1)), "cache preserves duplicate order");
        try (FileWriter w = new FileWriter(chapter, true)) { w.write("changed"); }
        check(EpubOpeningPolicy.read(root, "package.opf", opf, Arrays.asList(chapter), Arrays.asList(opf)) == null, "candidate mutation invalidates cache");
        check(EpubOpeningPolicy.cacheFile(root).isFile(), "cache exists");
        check(EpubOpeningPolicy.hasReadableContent(new File(root, "../chapter.xhtml")) == false, "outside path is not readable");
        System.out.println("EpubOpeningPolicyTest: PASS");
    }
}
