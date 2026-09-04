from pathlib import Path

ROOT = Path('.')
reader_path = ROOT / 'app/src/main/java/com/whisper/wowreader/BookReaderActivity.java'
main_path = ROOT / 'app/src/main/java/com/whisper/wowreader/MainActivity.java'
gradle_path = ROOT / 'app/build.gradle'

reader = reader_path.read_text()
main = main_path.read_text()
gradle = gradle_path.read_text()


def replace_between(text, start_marker, end_marker, replacement):
    a = text.index(start_marker)
    b = text.index(end_marker, a)
    return text[:a] + replacement + text[b:]

# v37 identity
gradle = gradle.replace('versionCode 36', 'versionCode 37', 1)
gradle = gradle.replace("versionName '2.16.6-lab-v36'", "versionName '2.16.7-lab-v37'", 1)

# Use exactly the same canonical text-node stream for selection offsets that the
# annotation renderer uses. This avoids Range.toString() offsets including body
# script/style nodes that the renderer later ignores.
new_capture = r'''    private void captureCurrentSelection(int action, ActionMode mode) {
        if (webView == null || isPdf) {
            if (mode != null) mode.finish();
            return;
        }
        String js = "(function(){try{" +
                "var sel=window.getSelection&&window.getSelection();if(!sel||sel.rangeCount===0||sel.isCollapsed)return null;" +
                "var range=sel.getRangeAt(0),root=document.getElementById('wow-page-flow')||document.body;" +
                "if(!root||!root.contains(range.commonAncestorContainer))return null;" +
                "function nodes(){var out=[],w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){var p=n.parentElement;if(!p)return NodeFilter.FILTER_REJECT;var tag=p.tagName;if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT')return NodeFilter.FILTER_REJECT;return n.nodeValue&&n.nodeValue.length?NodeFilter.FILTER_ACCEPT:NodeFilter.FILTER_REJECT;}});var n;while(n=w.nextNode())out.push(n);return out;}" +
                "var ns=nodes(),pos=0,start=-1,end=-1;for(var i=0;i<ns.length;i++){var n=ns[i],len=n.nodeValue.length;if(n===range.startContainer)start=pos+Math.max(0,Math.min(len,range.startOffset));if(n===range.endContainer)end=pos+Math.max(0,Math.min(len,range.endOffset));pos+=len;}" +
                "var text=range.toString();if(start<0||end<start){var full='';for(var j=0;j<ns.length;j++)full+=ns[j].nodeValue||'';var raw=0;try{var pre=document.createRange();pre.selectNodeContents(root);pre.setEnd(range.startContainer,range.startOffset);raw=pre.toString().length;}catch(x){}var best=-1,dist=1e18,from=0,at;while(text&&(at=full.indexOf(text,from))>=0){var d=Math.abs(at-raw);if(d<dist){dist=d;best=at;}from=at+1;}if(best>=0){start=best;end=best+text.length;}}" +
                "var lm=text.match(/^\\s+/),rm=text.match(/\\s+$/),lead=lm?lm[0].length:0,trail=rm?rm[0].length:0;start+=lead;end-=trail;text=text.trim();" +
                "if(!text||start<0||end<=start)return null;return JSON.stringify({text:text,start:start,end:end});" +
                "}catch(e){return null;}})()";
        try {
            webView.evaluateJavascript(js, result -> {
                SelectionData data = parseSelectionResult(result);
                if (mode != null) mode.finish();
                clearWebSelection();
                if (data == null || data.text == null || data.text.trim().isEmpty() || data.end <= data.start) {
                    Toast.makeText(this, "Select some text first", Toast.LENGTH_SHORT).show();
                    return;
                }
                data.text = data.text.trim();
                if (action == SEL_HIGHLIGHT) showHighlightColorDialog(data);
                else if (action == SEL_NOTE) showNoteEditor(data);
                else if (action == SEL_TRANSLATE) showTranslateDialog(data.text);
                else if (action == SEL_COPY) copySelectedText(data.text);
            });
        } catch (Exception e) {
            if (mode != null) mode.finish();
        }
    }

'''
reader = replace_between(reader, '    private void captureCurrentSelection(int action, ActionMode mode) {', '    private SelectionData parseSelectionResult', new_capture)

new_watcher = r'''    private void installSelectionWatcher() {
        if (webView == null || isPdf) return;
        String js = "(function(){try{" +
                "if(window.__wowSelectionWatcher)return;window.__wowSelectionWatcher=true;var timer=0;" +
                "document.addEventListener('selectionchange',function(){clearTimeout(timer);timer=setTimeout(function(){try{" +
                "var sel=window.getSelection&&window.getSelection();if(!sel||sel.rangeCount===0||sel.isCollapsed){WoW.onSelection('',0,0);return;}" +
                "var range=sel.getRangeAt(0),root=document.getElementById('wow-page-flow')||document.body;if(!root||!root.contains(range.commonAncestorContainer)){WoW.onSelection('',0,0);return;}" +
                "function nodes(){var out=[],w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){var p=n.parentElement;if(!p)return NodeFilter.FILTER_REJECT;var tag=p.tagName;if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT')return NodeFilter.FILTER_REJECT;return n.nodeValue&&n.nodeValue.length?NodeFilter.FILTER_ACCEPT:NodeFilter.FILTER_REJECT;}});var n;while(n=w.nextNode())out.push(n);return out;}" +
                "var ns=nodes(),pos=0,start=-1,end=-1;for(var i=0;i<ns.length;i++){var n=ns[i],len=n.nodeValue.length;if(n===range.startContainer)start=pos+Math.max(0,Math.min(len,range.startOffset));if(n===range.endContainer)end=pos+Math.max(0,Math.min(len,range.endOffset));pos+=len;}" +
                "var text=range.toString();if(start<0||end<start){var full='';for(var j=0;j<ns.length;j++)full+=ns[j].nodeValue||'';var at=full.indexOf(text);if(at>=0){start=at;end=at+text.length;}}" +
                "var lm=text.match(/^\\s+/),rm=text.match(/\\s+$/),lead=lm?lm[0].length:0,trail=rm?rm[0].length:0;start+=lead;end-=trail;text=text.trim();" +
                "if(!text||start<0||end<=start){WoW.onSelection('',0,0);return;}WoW.onSelection(text,start,end);" +
                "}catch(e){WoW.onSelection('',0,0);}},180);});" +
                "}catch(e){}})();";
        try { webView.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

'''
reader = replace_between(reader, '    private void installSelectionWatcher() {', '    private void onWebSelection', new_watcher)

# Critical annotation fix: never remove already-highlighted text from the canonical
# offset stream while applying the next annotation. Also verify saved offsets against
# the stored quote and relocate to the nearest exact quote when older offsets drifted.
old_nodes = '''                "function nodes(){var out=[],w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){var p=n.parentElement;if(!p)return NodeFilter.FILTER_REJECT;var tag=p.tagName;" +\n                "if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT'||p.closest('span.wow-annotation'))return NodeFilter.FILTER_REJECT;return n.nodeValue&&n.nodeValue.length?NodeFilter.FILTER_ACCEPT:NodeFilter.FILTER_REJECT;}});var n;while(n=w.nextNode())out.push(n);return out;}" +\n                "function apply(a){var ns=nodes(),pos=0,parts=[];for(var i=0;i<ns.length;i++){var n=ns[i],len=n.nodeValue.length,lo=Math.max(a.start-pos,0),hi=Math.min(a.end-pos,len);if(hi>lo)parts.push({n:n,lo:lo,hi:hi});pos+=len;if(pos>=a.end)break;}" +'''
new_nodes = '''                "function nodes(){var out=[],w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){var p=n.parentElement;if(!p)return NodeFilter.FILTER_REJECT;var tag=p.tagName;" +\n                "if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT')return NodeFilter.FILTER_REJECT;return n.nodeValue&&n.nodeValue.length?NodeFilter.FILTER_ACCEPT:NodeFilter.FILTER_REJECT;}});var n;while(n=w.nextNode())out.push(n);return out;}" +\n                "function resolved(a,ns){var full='';for(var z=0;z<ns.length;z++)full+=ns[z].nodeValue||'';var s=Math.max(0,Math.min(full.length,a.start||0)),e=Math.max(s,Math.min(full.length,a.end||s)),q=(a.quote||'').trim();if(q&&full.slice(s,e)!==q){var best=-1,dist=1e18,from=0,at;while((at=full.indexOf(q,from))>=0){var d=Math.abs(at-s);if(d<dist){dist=d;best=at;}from=at+1;}if(best>=0){s=best;e=best+q.length;}}return [s,e];}" +\n                "function apply(a){var ns=nodes(),rr=resolved(a,ns),targetStart=rr[0],targetEnd=rr[1],pos=0,parts=[];for(var i=0;i<ns.length;i++){var n=ns[i],len=n.nodeValue.length,lo=Math.max(targetStart-pos,0),hi=Math.min(targetEnd-pos,len);if(hi>lo)parts.push({n:n,lo:lo,hi:hi});pos+=len;if(pos>=targetEnd)break;}" +'''
if old_nodes not in reader:
    raise SystemExit('annotation node/apply block not found')
reader = reader.replace(old_nodes, new_nodes, 1)

# Multi-book picker: allow selecting many EPUB/PDF files in one pass.
if 'import android.content.ClipData;' not in main:
    main = main.replace('import android.content.Intent;\n', 'import android.content.Intent;\nimport android.content.ClipData;\n', 1)
old_choose = '    private void chooseBook(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/epub+zip","application/pdf"}); startActivityForResult(i,REQ_IMPORT); }'
new_choose = '    private void chooseBook(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/epub+zip","application/pdf"}); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i,REQ_IMPORT); }'
if old_choose not in main:
    raise SystemExit('chooseBook block not found')
main = main.replace(old_choose, new_choose, 1)

old_result = '''    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(googleDrive!=null&&googleDrive.handleActivityResult(requestCode,resultCode,data))return;
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==REQ_IMPORT){importBook(uri,false);return;}
        try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){}
        if(requestCode==REQ_BACKUP)backupLibrary(uri);else if(requestCode==REQ_RESTORE)restoreLibrary(uri);
    }
'''
new_result = '''    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(googleDrive!=null&&googleDrive.handleActivityResult(requestCode,resultCode,data))return;
        if(resultCode!=RESULT_OK||data==null)return;
        if(requestCode==REQ_IMPORT){
            ArrayList<Uri> selected=new ArrayList<>();
            ClipData clip=data.getClipData();
            if(clip!=null){for(int i=0;i<clip.getItemCount();i++){Uri u=clip.getItemAt(i).getUri();if(u!=null&&!selected.contains(u))selected.add(u);}}
            else if(data.getData()!=null)selected.add(data.getData());
            if(selected.isEmpty())return;
            int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            for(Uri u:selected){try{getContentResolver().takePersistableUriPermission(u,flags);}catch(Exception ignored){} importBook(u,false);}
            if(selected.size()>1)Toast.makeText(this,"Importing "+selected.size()+" books…",Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri=data.getData();if(uri==null)return;
        try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){}
        if(requestCode==REQ_BACKUP)backupLibrary(uri);else if(requestCode==REQ_RESTORE)restoreLibrary(uri);
    }
'''
if old_result not in main:
    raise SystemExit('onActivityResult block not found')
main = main.replace(old_result, new_result, 1)

reader_path.write_text(reader)
main_path.write_text(main)
gradle_path.write_text(gradle)
print('v37 annotation + multi-import patch applied')
