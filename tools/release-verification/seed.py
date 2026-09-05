import json, sys, zipfile
from pathlib import Path
import xml.etree.ElementTree as E
p=Path('verification')
values={
 'percent_update-book.epub':('int','37'),
 'epub_chapter_update-book.epub':('int','0'),
 'epub_scroll_update-book.epub':('int','370'),
 'library_title_update-book.epub':('string','Custom title'),
 'library_author_update-book.epub':('string','Custom author'),
 'library_metadata_custom_update-book.epub':('boolean','true'),
 'library_shelves_json':('string','{"Favorites":["update-book.epub"]}'),
 'annotations_a38a7859':('string','[{"id":"sentinel","chapter":0,"start":0,"end":5,"quote":"Saved highlight","created_ms":12345,"note":"Saved note"}]'),
 'reading_stats_day_notes_json':('string','{"2026-09-05":"Saved daily note"}'),
 'reading_stats_book_day_notes_json':('string','{"2026-09-05":{"update-book.epub":"Saved memory"}}'),
 'reading_stats_day_books_json':('string','{"2026-09-05":{"update-book.epub":60000}}'),
 'reading_stats_total_ms':('long','60000'),
 'reader_theme':('int','2'),
 'epub_font':('int','145'),
 'google_last_backup_ms':('long','12345'),
}
if len(sys.argv)>1:
 root=E.parse(p/'updated-prefs.xml').getroot();actual={x.get('name'):(x.tag,x.text if x.tag=='string' else x.get('value')) for x in root}
 for k,v in values.items(): assert actual.get(k)==v,(k,actual.get(k),v)
 assert (p/'update-book.epub').read_bytes()==(p/'updated-book.epub').read_bytes()
 assert Path('app/src/main/assets/fonts/pyidaungsu_native.ttf').read_bytes()==(p/'updated-font.ttf').read_bytes()
 print('All seeded preferences, EPUB bytes and custom font bytes preserved')
else:
 root=E.parse(p/'old-prefs.xml').getroot()
 for x in list(root):
  if x.get('name') in values: root.remove(x)
 for k,(t,v) in values.items():
  n=E.SubElement(root,t,{'name':k})
  if t=='string':n.text=v
  else:n.set('value',v)
 E.ElementTree(root).write(p/'seed-prefs.xml',encoding='utf-8',xml_declaration=True)
 with zipfile.ZipFile(p/'update-book.epub','w') as z:
  z.writestr('mimetype','application/epub+zip')
  z.writestr('META-INF/container.xml','<container><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>')
  z.writestr('book.opf','<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Update test</dc:title></metadata><manifest><item id="c" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>')
  z.writestr('chapter.xhtml','<html><body><p>Retain this book during an update.</p></body></html>')
