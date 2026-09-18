package com.whisper.wowreader;

import java.util.ArrayList;
import java.util.List;

/** Immutable, SQL-backed library query. Keeps 100k+ filtering/sorting out of Java heap. */
final class LibraryQuerySpec {
    final String search;
    final String author;
    final String status;
    final String shelf;
    final String sort;

    LibraryQuerySpec(String search, String author, String status, String shelf, String sort) {
        this.search = clean(search);
        this.author = clean(author);
        this.status = clean(status);
        this.shelf = clean(shelf);
        this.sort = clean(sort);
    }

    String whereSql() {
        List<String> parts = new ArrayList<>();
        parts.add("1=1");
        if (!search.isEmpty()) parts.add("(LOWER(title) LIKE ? OR LOWER(author) LIKE ? OR LOWER(file_name) LIKE ?)");
        if (!author.isEmpty()) parts.add("author=?");
        if ("reading".equals(status)) parts.add("progress>0 AND progress<100");
        else if ("unread".equals(status)) parts.add("progress=0");
        else if ("finished".equals(status)) parts.add("progress>=100");
        if (!shelf.isEmpty()) parts.add("EXISTS(SELECT 1 FROM shelf_books sb WHERE sb.file_name=books.file_name AND sb.shelf_name=?)");
        return join(parts, " AND ");
    }

    String[] args() {
        List<String> out = new ArrayList<>();
        if (!search.isEmpty()) {
            String q = "%" + search.toLowerCase(java.util.Locale.ROOT) + "%";
            out.add(q); out.add(q); out.add(q);
        }
        if (!author.isEmpty()) out.add(author);
        if (!shelf.isEmpty()) out.add(shelf);
        return out.toArray(new String[0]);
    }

    String orderSql() {
        if ("title_asc".equals(sort)) return "title COLLATE NOCASE ASC, file_name COLLATE NOCASE ASC";
        if ("title_desc".equals(sort)) return "title COLLATE NOCASE DESC, file_name COLLATE NOCASE DESC";
        if ("opened".equals(sort)) return "last_opened_at DESC, title COLLATE NOCASE ASC";
        return "added_at DESC, title COLLATE NOCASE ASC";
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static String join(List<String> values, String sep) {
        StringBuilder b = new StringBuilder();
        for (String v : values) { if (b.length() > 0) b.append(sep); b.append(v); }
        return b.toString();
    }
}
