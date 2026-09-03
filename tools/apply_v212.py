from pathlib import Path

P = Path('app/src/main/java/com/whisper/wowreader/MainActivity.java')
s = P.read_text(encoding='utf-8')

def repl(old, new, count=1):
    global s
    n = s.count(old)
    if n < count:
        raise SystemExit(f'anchor missing {n} < {count}: {old[:120]!r}')
    s = s.replace(old, new, count)

# Fields and startup.
repl('    private TextView sortButton;\n    private String sortMode = "added";\n',
     '    private TextView sortButton;\n    private TextView authorButton;\n    private TextView accountButton;\n    private String sortMode = "added";\n    private String authorFilter = "";\n    private GoogleDriveSync googleDrive;\n    private GoogleDriveSync.Profile googleProfile;\n    private boolean googleSyncBusy = false;\n    private long lastAutoSyncAttemptMs = 0L;\n')
repl('        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);\n        gridMode = prefs.getBoolean("library_grid", true);\n',
     '        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);\n        googleDrive = new GoogleDriveSync(this);\n        restoreStoredGoogleProfile();\n        gridMode = prefs.getBoolean("library_grid", true);\n')
repl('    @Override protected void onResume() { super.onResume(); if (libraryRecycler != null) refreshLibrary(); }\n',
     '    @Override protected void onResume() {\n        super.onResume();\n        if (libraryRecycler != null) refreshLibrary();\n        maybeAutoGoogleSync();\n    }\n')

# Library search + author filter + author button label.
old_refresh = '''        visibleBooks.clear();
        for (File f : all) {
            String cachedTitle = cachedLibraryTitle(f).toLowerCase(Locale.ROOT);
            String fileTitle = stripExtension(f.getName()).toLowerCase(Locale.ROOT);
            if (searchQuery.isEmpty() || cachedTitle.contains(searchQuery) || fileTitle.contains(searchQuery))
                visibleBooks.add(f);
        }
        if (libraryAdapter != null) libraryAdapter.submit(visibleBooks);
        if (countView != null) countView.setText(visibleBooks.size() + (visibleBooks.size() == 1 ? " book" : " books"));
        if (sortButton != null) sortButton.setText(sortButtonLabel());

        if (isAlphabeticalSort()) warmSortMetadataIfNeeded(all);
'''
new_refresh = '''        visibleBooks.clear();
        for (File f : all) {
            String cachedTitle = cachedLibraryTitle(f).toLowerCase(Locale.ROOT);
            String fileTitle = stripExtension(f.getName()).toLowerCase(Locale.ROOT);
            String author = cachedLibraryAuthor(f);
            String authorLower = author.toLowerCase(Locale.ROOT);
            if (!authorFilter.isEmpty() && !authorFilter.equals(author)) continue;
            if (searchQuery.isEmpty() || cachedTitle.contains(searchQuery) || fileTitle.contains(searchQuery) || authorLower.contains(searchQuery))
                visibleBooks.add(f);
        }
        if (libraryAdapter != null) libraryAdapter.submit(visibleBooks);
        if (countView != null) {
            String suffix = visibleBooks.size() == 1 ? " book" : " books";
            countView.setText(visibleBooks.size() + suffix + (authorFilter.isEmpty() ? "" : " · " + authorFilter));
        }
        if (sortButton != null) sortButton.setText(sortButtonLabel());
        if (authorButton != null) authorButton.setText(authorButtonLabel());

        warmSortMetadataIfNeeded(all);
'''
repl(old_refresh, new_refresh)

repl('    private String cachedLibraryTitle(File file) {\n        String fallback = stripExtension(file.getName());\n        String value = prefs.getString("library_title_" + file.getName(), fallback);\n        return value == null || value.trim().isEmpty() ? fallback : value.trim();\n    }\n',
'''    private String cachedLibraryTitle(File file) {
        String fallback = stripExtension(file.getName());
        String value = prefs.getString("library_title_" + file.getName(), fallback);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String cachedLibraryAuthor(File file) {
        String value = prefs.getString("library_author_" + file.getName(), "");
        return value == null ? "" : value.trim();
    }
''')

# Warm title and author metadata together.
old_warm = '''        boolean missing = false;
        for (File f : files) {
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".epub") &&
                    !prefs.contains("library_title_" + f.getName())) {
                missing = true;
                break;
            }
        }
'''
new_warm = '''        boolean missing = false;
        for (File f : files) {
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".epub") &&
                    (!prefs.contains("library_title_" + f.getName()) ||
                     !prefs.contains("library_author_" + f.getName()))) {
                missing = true;
                break;
            }
        }
'''
repl(old_warm, new_warm)
old_warm_loop = '''                if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".epub") ||
                        prefs.contains("library_title_" + f.getName())) continue;
                String title = stripExtension(f.getName());
                try {
                    EpubUtil.Summary summary = EpubUtil.extractSummary(f, coverCacheDir);
                    if (summary.title != null && !summary.title.trim().isEmpty()) title = summary.title.trim();
                } catch (Exception ignored) {}
                edit.putString("library_title_" + f.getName(), title);
                changed = true;
'''
new_warm_loop = '''                if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".epub")) continue;
                if (prefs.contains("library_title_" + f.getName()) && prefs.contains("library_author_" + f.getName())) continue;
                String title = stripExtension(f.getName());
                String author = "";
                try {
                    EpubUtil.Summary summary = EpubUtil.extractSummary(f, coverCacheDir);
                    if (summary.title != null && !summary.title.trim().isEmpty()) title = summary.title.trim();
                    if (summary.author != null && !summary.author.trim().isEmpty()) author = summary.author.trim();
                } catch (Exception ignored) {}
                edit.putString("library_title_" + f.getName(), title);
                edit.putString("library_author_" + f.getName(), author);
                changed = true;
'''
repl(old_warm_loop, new_warm_loop)
repl('                if (shouldRefresh && isAlphabeticalSort()) refreshLibrary();\n',
     '                if (shouldRefresh) refreshLibrary();\n')

# Account button replaces the old dedicated backup button; manual backup remains in account menu.
old_backup = '''        TextView backup = iconButton("⇅");
        backup.setTextSize(18);
        backup.setContentDescription("Backup and restore");
        backup.setOnClickListener(v -> showCloudMenu());
        brandRow.addView(backup, new LinearLayout.LayoutParams(dp(44), dp(44)));

        viewModeButton = iconButton(gridMode ? "☷" : "▦");
'''
new_backup = '''        accountButton = iconButton("G");
        accountButton.setTextSize(15);
        accountButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        accountButton.setContentDescription("Google account and sync");
        accountButton.setOnClickListener(v -> showAccountMenu());
        updateAccountButton();
        brandRow.addView(accountButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        viewModeButton = iconButton(gridMode ? "☷" : "▦");
'''
repl(old_backup, new_backup)

# Add Authors shelf selector next to Sort.
old_sort_add = '''        row.addView(sortButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        return row;
    }

    private String sortButtonLabel() {
'''
new_sort_add = '''        authorButton = new TextView(this);
        authorButton.setText(authorButtonLabel());
        authorButton.setTextSize(11.5f);
        authorButton.setTextColor(Color.rgb(67, 68, 190));
        authorButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        authorButton.setGravity(Gravity.CENTER);
        authorButton.setPadding(dp(10), 0, dp(10), 0);
        authorButton.setSingleLine(true);
        authorButton.setMaxWidth(dp(126));
        authorButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
        authorButton.setBackground(roundRect(Color.argb(220, 255, 255, 255), dp(19), dp(1), Color.argb(72, 126, 126, 210)));
        authorButton.setOnClickListener(v -> showAuthorsDialog());
        LinearLayout.LayoutParams authorLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        authorLp.rightMargin = dp(7);
        row.addView(authorButton, authorLp);

        row.addView(sortButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        return row;
    }

    private String authorButtonLabel() {
        return authorFilter.isEmpty() ? "Authors  ▾" : authorFilter + "  ×";
    }

    private void showAuthorsDialog() {
        File[] files = libraryDir.listFiles(file -> file.isFile() && isBook(file.getName()));
        if (files == null) files = new File[0];
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (File f : files) {
            String author = cachedLibraryAuthor(f);
            if (author.isEmpty()) continue;
            counts.put(author, counts.getOrDefault(author, 0) + 1);
        }
        java.util.List<String> authors = new java.util.ArrayList<>(counts.keySet());
        authors.sort((a, b) -> {
            int ga = titleScriptGroup(a), gb = titleScriptGroup(b);
            if (ga != gb) return Integer.compare(ga, gb);
            return ga == 0 ? myanmarCollator.compare(a, b) : englishCollator.compare(a, b);
        });
        String[] labels = new String[authors.size() + 1];
        labels[0] = "All authors · " + files.length + " books";
        for (int i = 0; i < authors.size(); i++) {
            String name = authors.get(i);
            labels[i + 1] = name + " · " + counts.get(name) + (counts.get(name) == 1 ? " book" : " books");
        }
        new AlertDialog.Builder(this)
                .setTitle("Authors")
                .setItems(labels, (dialog, which) -> {
                    authorFilter = which == 0 ? "" : authors.get(which - 1);
                    refreshLibrary();
                })
                .setNegativeButton("Cancel", null)
                .show();
        warmSortMetadataIfNeeded(files);
    }

    private String sortButtonLabel() {
'''
repl(old_sort_add, new_sort_add)

# Cache author and make author line tappable.
old_visual = '''    private void loadBookVisual(File file,ImageView cover,TextView titleView,TextView metaView){
        new Thread(()->{ String title=stripExtension(file.getName()),author=""; Bitmap bitmap=null; try{ if(file.getName().toLowerCase(Locale.ROOT).endsWith(".epub")){ EpubUtil.Summary s=EpubUtil.extractSummary(file,coverCacheDir); if(s.title!=null&&!s.title.isEmpty()) title=s.title; if(s.author!=null) author=s.author; if(s.cover!=null&&s.cover.isFile()) bitmap=BitmapFactory.decodeFile(s.cover.getAbsolutePath()); } else bitmap=renderPdfCover(file); }catch(Exception ignored){}
            prefs.edit().putString("library_title_" + file.getName(), title).apply();
            String ft=title,fa=author; Bitmap fb=bitmap; int progress=prefs.getInt("percent_"+file.getName(),0); runOnUiThread(()->{ if(fb!=null) cover.setImageBitmap(fb); titleView.setText(ft); applyBookTitleTypeface(titleView); String type=file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")?"PDF":"EPUB"; metaView.setText(fa.isEmpty()?type+" · "+progress+"%":fa+" · "+progress+"%"); }); }).start();
    }
'''
new_visual = '''    private void loadBookVisual(File file,ImageView cover,TextView titleView,TextView metaView){
        new Thread(()->{ String title=stripExtension(file.getName()),author=cachedLibraryAuthor(file); Bitmap bitmap=null; try{ if(file.getName().toLowerCase(Locale.ROOT).endsWith(".epub")){ EpubUtil.Summary s=EpubUtil.extractSummary(file,coverCacheDir); if(s.title!=null&&!s.title.isEmpty()) title=s.title; if(s.author!=null&&!s.author.trim().isEmpty()) author=s.author.trim(); if(s.cover!=null&&s.cover.isFile()) bitmap=BitmapFactory.decodeFile(s.cover.getAbsolutePath()); } else bitmap=renderPdfCover(file); }catch(Exception ignored){}
            prefs.edit().putString("library_title_" + file.getName(), title).putString("library_author_" + file.getName(), author).apply();
            String ft=title,fa=author; Bitmap fb=bitmap; int progress=prefs.getInt("percent_"+file.getName(),0); runOnUiThread(()->{ if(fb!=null) cover.setImageBitmap(fb); titleView.setText(ft); applyBookTitleTypeface(titleView); String type=file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")?"PDF":"EPUB"; metaView.setText(fa.isEmpty()?type+" · "+progress+"%":fa+" · "+progress+"%"); if(!fa.isEmpty()){ if(pyidaungsuTypeface!=null) metaView.setTypeface(pyidaungsuTypeface); metaView.setClickable(true); metaView.setOnClickListener(v->{authorFilter=fa;refreshLibrary();}); } }); }).start();
    }
'''
repl(old_visual, new_visual)

# Import author metadata and mark cloud state dirty.
old_import_summary = '''                String displayTitle=stripExtension(out.getName());
                if(out.getName().toLowerCase(Locale.ROOT).endsWith(".epub")){
                    try{
                        EpubUtil.Summary summary=EpubUtil.extractSummary(out,coverCacheDir);
                        if(summary.title!=null&&!summary.title.trim().isEmpty())displayTitle=summary.title.trim();
                    }catch(Exception ignored){}
                }
                prefs.edit()
                        .putLong("added_at_"+out.getName(),System.currentTimeMillis())
                        .putString("library_title_"+out.getName(),displayTitle)
                        .apply();
'''
new_import_summary = '''                String displayTitle=stripExtension(out.getName());
                String displayAuthor="";
                if(out.getName().toLowerCase(Locale.ROOT).endsWith(".epub")){
                    try{
                        EpubUtil.Summary summary=EpubUtil.extractSummary(out,coverCacheDir);
                        if(summary.title!=null&&!summary.title.trim().isEmpty())displayTitle=summary.title.trim();
                        if(summary.author!=null&&!summary.author.trim().isEmpty())displayAuthor=summary.author.trim();
                    }catch(Exception ignored){}
                }
                prefs.edit()
                        .putLong("added_at_"+out.getName(),System.currentTimeMillis())
                        .putString("library_title_"+out.getName(),displayTitle)
                        .putString("library_author_"+out.getName(),displayAuthor)
                        .putLong("sync_updated_ms",System.currentTimeMillis())
                        .apply();
'''
repl(old_import_summary, new_import_summary)
repl('                    refreshLibrary();\n                });\n',
     '                    refreshLibrary();\n                    maybeAutoGoogleSync();\n                });\n', 1)

# Delete clears author cache and dirties sync state.
repl('.remove("library_title_"+file.getName()).remove("added_at_"+file.getName()).remove("last_opened_"+file.getName()).apply();refreshLibrary();',
     '.remove("library_title_"+file.getName()).remove("library_author_"+file.getName()).remove("added_at_"+file.getName()).remove("last_opened_"+file.getName()).putLong("sync_updated_ms",System.currentTimeMillis()).apply();refreshLibrary();maybeAutoGoogleSync();')

# Google account and Drive sync UX before old manual cloud menu.
anchor = '    private void showCloudMenu(){new AlertDialog.Builder(this).setTitle("Backup & restore")'
if anchor not in s:
    raise SystemExit('cloud menu anchor missing')
account_methods = r'''    private void restoreStoredGoogleProfile(){
        if(!prefs.getBoolean("google_sync_connected",false)) return;
        googleProfile=new GoogleDriveSync.Profile();
        googleProfile.name=prefs.getString("google_account_name","Google account");
        googleProfile.email=prefs.getString("google_account_email","");
        googleProfile.picture=prefs.getString("google_account_picture","");
    }

    private void updateAccountButton(){
        if(accountButton==null)return;
        boolean connected=prefs!=null&&prefs.getBoolean("google_sync_connected",false);
        String name=googleProfile==null?prefs.getString("google_account_name",""):googleProfile.name;
        String initial="G";
        if(connected&&name!=null&&!name.trim().isEmpty())initial=name.trim().substring(0,1).toUpperCase(Locale.ROOT);
        accountButton.setText(initial);
        accountButton.setTextColor(connected?Color.WHITE:Color.rgb(67,68,190));
        accountButton.setBackground(connected
                ?gradientRoundRect(new int[]{Color.rgb(91,76,220),Color.rgb(70,112,235)},dp(22))
                :roundRect(Color.argb(188,255,255,255),dp(22),dp(1),Color.argb(80,210,214,222)));
        accountButton.setContentDescription(connected?"Google account connected":"Connect Google account");
    }

    private void showAccountMenu(){
        boolean connected=prefs.getBoolean("google_sync_connected",false);
        if(!connected){
            new AlertDialog.Builder(this)
                    .setTitle("Account & backup")
                    .setMessage("Connect a Google account to privately sync books, notes, highlights and reading progress to your Drive.")
                    .setItems(new String[]{"Connect Google account","Manual folder backup","Manual folder restore"},(d,w)->{
                        if(w==0)connectGoogleAccount(true); else openManualCloudPicker(w==1);
                    }).show();
            return;
        }
        String name=prefs.getString("google_account_name","Google account");
        String email=prefs.getString("google_account_email","");
        boolean auto=prefs.getBoolean("google_sync_enabled",true);
        String[] items={"Sync now","Restore from Google Drive","Auto sync: "+(auto?"On":"Off"),"Switch Google account","Disconnect Google account","Manual folder backup","Manual folder restore"};
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage((email.isEmpty()?"":email+"\n")+"WoW Reader data is stored privately in this account's Google Drive app data.")
                .setItems(items,(d,w)->{
                    if(w==0)performGoogleBackup(true);
                    else if(w==1)confirmGoogleRestore();
                    else if(w==2){prefs.edit().putBoolean("google_sync_enabled",!auto).apply();Toast.makeText(this,"Auto sync "+(!auto?"on":"off"),Toast.LENGTH_SHORT).show();}
                    else if(w==3)connectGoogleAccount(true);
                    else if(w==4)disconnectGoogleAccount();
                    else openManualCloudPicker(w==5);
                }).show();
    }

    private void connectGoogleAccount(boolean chooseAccount){
        if(googleDrive==null)googleDrive=new GoogleDriveSync(this);
        googleDrive.authorize(chooseAccount,new GoogleDriveSync.AuthCallback(){
            @Override public void onReady(GoogleDriveSync.Profile profile){
                googleProfile=profile;
                prefs.edit()
                        .putBoolean("google_sync_connected",true)
                        .putBoolean("google_sync_enabled",true)
                        .putString("google_account_name",profile.name==null?"Google account":profile.name)
                        .putString("google_account_email",profile.email==null?"":profile.email)
                        .putString("google_account_picture",profile.picture==null?"":profile.picture)
                        .apply();
                updateAccountButton();
                GoogleDriveSync.hasBackup(MainActivity.this,profile.accessToken,found->{
                    File[] local=libraryDir.listFiles(file->file.isFile()&&isBook(file.getName()));
                    boolean empty=local==null||local.length==0;
                    if(found&&empty){
                        new AlertDialog.Builder(MainActivity.this).setTitle("Restore your library?")
                                .setMessage("A WoW Reader backup was found in this Google Drive. Restore your books, notes and highlights to this device?")
                                .setNegativeButton("Not now",null).setPositiveButton("Restore",(d,w)->performGoogleRestore()).show();
                    }else{
                        new AlertDialog.Builder(MainActivity.this).setTitle("Google Drive connected")
                                .setMessage("Auto sync is on. Back up this device now?")
                                .setNegativeButton("Later",null).setPositiveButton("Back up now",(d,w)->performGoogleBackup(true)).show();
                    }
                });
            }
            @Override public void onError(String message){Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
        });
    }

    private void rememberGoogleProfile(GoogleDriveSync.Profile profile){
        googleProfile=profile;
        prefs.edit().putBoolean("google_sync_connected",true)
                .putString("google_account_name",profile.name==null?"Google account":profile.name)
                .putString("google_account_email",profile.email==null?"":profile.email)
                .putString("google_account_picture",profile.picture==null?"":profile.picture).apply();
        updateAccountButton();
    }

    private File readerFontsDir(){File d=new File(getFilesDir(),"reader_fonts");if(!d.exists())d.mkdirs();return d;}

    private void performGoogleBackup(boolean showToast){
        if(googleSyncBusy)return;
        googleSyncBusy=true;
        googleDrive.authorize(false,new GoogleDriveSync.AuthCallback(){
            @Override public void onReady(GoogleDriveSync.Profile profile){
                rememberGoogleProfile(profile);
                GoogleDriveSync.backup(MainActivity.this,profile.accessToken,libraryDir,readerFontsDir(),prefs,new GoogleDriveSync.SyncCallback(){
                    @Override public void onSuccess(String message){googleSyncBusy=false;if(showToast)Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
                    @Override public void onError(String message){googleSyncBusy=false;if(showToast)Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
                });
            }
            @Override public void onError(String message){googleSyncBusy=false;if(showToast)Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
        });
    }

    private void confirmGoogleRestore(){
        new AlertDialog.Builder(this).setTitle("Restore from Google Drive?")
                .setMessage("Books with the same stored name will be replaced. Notes, highlights, reading progress and reader settings from the backup will be restored.")
                .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->performGoogleRestore()).show();
    }

    private void performGoogleRestore(){
        if(googleSyncBusy)return;
        googleSyncBusy=true;
        googleDrive.authorize(false,new GoogleDriveSync.AuthCallback(){
            @Override public void onReady(GoogleDriveSync.Profile profile){
                rememberGoogleProfile(profile);
                GoogleDriveSync.restore(MainActivity.this,profile.accessToken,libraryDir,readerFontsDir(),prefs,new GoogleDriveSync.SyncCallback(){
                    @Override public void onSuccess(String message){googleSyncBusy=false;authorFilter="";refreshLibrary();Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
                    @Override public void onError(String message){googleSyncBusy=false;Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
                });
            }
            @Override public void onError(String message){googleSyncBusy=false;Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
        });
    }

    private void maybeAutoGoogleSync(){
        if(prefs==null||googleDrive==null||googleSyncBusy)return;
        if(!prefs.getBoolean("google_sync_connected",false)||!prefs.getBoolean("google_sync_enabled",true))return;
        long changed=prefs.getLong("sync_updated_ms",0L),backed=prefs.getLong("google_last_backup_ms",0L),now=System.currentTimeMillis();
        if(changed<=backed||now-lastAutoSyncAttemptMs<45000L)return;
        lastAutoSyncAttemptMs=now;
        performGoogleBackup(false);
    }

    private void disconnectGoogleAccount(){
        GoogleDriveSync.Profile profile=googleProfile;
        Runnable clear=()->runOnUiThread(()->{
            googleProfile=null;
            prefs.edit().remove("google_sync_connected").remove("google_sync_enabled").remove("google_account_name").remove("google_account_email").remove("google_account_picture").apply();
            updateAccountButton();
            Toast.makeText(this,"Google account disconnected",Toast.LENGTH_SHORT).show();
        });
        if(googleDrive!=null)googleDrive.revoke(profile,clear);else clear.run();
    }

    private void openManualCloudPicker(boolean backup){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,backup?REQ_BACKUP:REQ_RESTORE);
    }

'''
s = s.replace(anchor, account_methods + anchor, 1)

# onActivityResult: Google authorization has no data URI, so it must be handled first.
old_result = '''    @SuppressLint("WrongConstant")
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(requestCode==REQ_IMPORT){importBook(uri,false);return;}try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){}if(requestCode==REQ_BACKUP)backupLibrary(uri);else if(requestCode==REQ_RESTORE)restoreLibrary(uri);}
'''
new_result = '''    @SuppressLint("WrongConstant")
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(googleDrive!=null&&googleDrive.handleActivityResult(requestCode,resultCode,data))return;
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==REQ_IMPORT){importBook(uri,false);return;}
        try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){}
        if(requestCode==REQ_BACKUP)backupLibrary(uri);else if(requestCode==REQ_RESTORE)restoreLibrary(uri);
    }
'''
repl(old_result, new_result)

P.write_text(s, encoding='utf-8')

# PDF reading progress should also mark cloud state dirty.
R = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
r = R.read_text(encoding='utf-8')
old = '''            prefs.edit()
                    .putInt("pdf_page_" + bookFile.getName(), currentPdfPage)
                    .putInt("percent_" + bookFile.getName(), percent)
                    .apply();
'''
new = '''            prefs.edit()
                    .putInt("pdf_page_" + bookFile.getName(), currentPdfPage)
                    .putInt("percent_" + bookFile.getName(), percent)
                    .putLong("sync_updated_ms", System.currentTimeMillis())
                    .apply();
'''
if old not in r:
    raise SystemExit('PDF progress anchor missing')
r = r.replace(old, new, 1)
R.write_text(r, encoding='utf-8')

print('Applied WoW Reader v2.12 author shelves and Google Drive sync UX')
