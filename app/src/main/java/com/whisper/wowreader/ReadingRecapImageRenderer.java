package com.whisper.wowreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Generates bounded 1080px recap pages entirely off the UI thread. */
final class ReadingRecapImageRenderer {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1500;
    private static final int PER_PAGE = 12;
    private ReadingRecapImageRenderer() {}

    static List<File> render(Context context, ReadingRecapData.Summary summary, String mode,
                             String periodLabel) throws Exception {
        File dir = new File(context.getCacheDir(), "shared_images");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create share folder");
        File[] old = dir.listFiles((d,n) -> n.startsWith("wow-reading-recap-") && n.endsWith(".png"));
        if (old != null) for (File f : old) f.delete();

        int totalBooks = summary.finishedBooks.size();
        int pages = Math.max(1, (totalBooks + PER_PAGE - 1) / PER_PAGE);
        List<File> out = new ArrayList<>();
        for (int page = 0; page < pages; page++) {
            int from = page * PER_PAGE;
            int to = Math.min(totalBooks, from + PER_PAGE);
            Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawBackground(canvas);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            drawCentered(canvas, kicker(mode), 74, 34f, Color.rgb(190,198,224), p, false);
            drawCentered(canvas, title(mode, summary), 126, 58f, Color.WHITE, p, true);
            drawCentered(canvas, periodLabel == null ? "" : periodLabel, 174, 27f, Color.rgb(190,198,224), p, false);
            if (pages > 1) drawCentered(canvas, "Page " + (page + 1) + " / " + pages, 214, 24f, Color.rgb(190,198,224), p, false);

            int top = 260;
            if (to > from) drawCovers(context, canvas, summary.finishedBooks.subList(from, to), top);
            else drawCentered(canvas, "No finished books in this period yet", 650, 36f, Color.rgb(220,225,241), p, true);

            int statsY = 1265;
            drawMetric(canvas, 180, statsY, String.valueOf(totalBooks), "Finished", p);
            drawMetric(canvas, 540, statsY, String.valueOf(summary.activeDays), "Reading days", p);
            drawMetric(canvas, 900, statsY, duration(summary.readingMs), "Read time", p);
            drawCentered(canvas, "A little reading becomes a life of stories.", 1390, 27f, Color.rgb(218,221,239), p, false);
            drawCentered(canvas, "WoW Reader", 1450, 29f, Color.WHITE, p, true);

            File file = new File(dir, String.format(Locale.US, "wow-reading-recap-%02d.png", page + 1));
            try (FileOutputStream fos = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 92, fos)) throw new Exception("Cannot create recap image");
            } finally { bitmap.recycle(); }
            out.add(file);
        }
        return out;
    }

    private static void drawBackground(Canvas canvas) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0,0,WIDTH,HEIGHT,
                new int[]{Color.rgb(18,31,64),Color.rgb(32,48,88),Color.rgb(44,35,82)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0,0,WIDTH,HEIGHT), 48,48,bg);
    }

    private static void drawCovers(Context context, Canvas canvas, List<ReadingRecapData.FinishedBook> books, int top) {
        final int cols = 4, gap = 18, side = 58;
        int cellW = (WIDTH - side * 2 - gap * (cols - 1)) / cols;
        int coverH = 270;
        for (int i=0;i<books.size();i++) {
            int row=i/cols,col=i%cols;
            int left=side+col*(cellW+gap);
            int y=top+row*(coverH+24);
            Rect target=new Rect(left,y,left+cellW,y+coverH);
            Bitmap cover=loadCover(context,books.get(i).file,cellW,coverH);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            if(cover!=null){
                Rect src=centerCropSource(cover,cellW/(float)coverH);
                canvas.drawBitmap(cover,src,target,paint);
                cover.recycle();
            } else {
                paint.setColor(Color.rgb(54,68,103)); canvas.drawRoundRect(new RectF(target),18,18,paint);
                paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(44); paint.setTypeface(Typeface.DEFAULT_BOLD);
                String t=books.get(i).title; String letter=(t==null||t.trim().isEmpty())?"W":t.trim().substring(0,1);
                canvas.drawText(letter,target.centerX(),target.centerY()+15,paint);
            }
        }
    }

    private static Bitmap loadCover(Context context, File file, int w, int h) {
        try {
            Bitmap raw = null;
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".epub")) {
                File cache = new File(context.getFilesDir(), "cover_cache"); if (!cache.exists()) cache.mkdirs();
                EpubUtil.Summary s = EpubUtil.extractSummary(file, cache);
                if (s.cover != null && s.cover.isFile()) raw = BitmapFactory.decodeFile(s.cover.getAbsolutePath());
            } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) raw = pdfCover(file, Math.max(180,w));
            if (raw == null) return null;
            int maxW=Math.max(w*2,320),maxH=Math.max(h*2,460);
            if(raw.getWidth()>maxW*2||raw.getHeight()>maxH*2){
                float scale=Math.min(maxW/(float)raw.getWidth(),maxH/(float)raw.getHeight());
                Bitmap scaled=Bitmap.createScaledBitmap(raw,Math.max(1,Math.round(raw.getWidth()*scale)),Math.max(1,Math.round(raw.getHeight()*scale)),true);
                raw.recycle(); raw=scaled;
            }
            return raw;
        } catch(Exception ignored){ return null; }
    }

    private static Bitmap pdfCover(File file,int width){
        ParcelFileDescriptor pfd=null; PdfRenderer renderer=null; PdfRenderer.Page page=null;
        try{
            pfd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY); renderer=new PdfRenderer(pfd); if(renderer.getPageCount()==0)return null;
            page=renderer.openPage(0); int height=Math.max(1,Math.round(width*page.getHeight()/(float)page.getWidth()));
            Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); b.eraseColor(Color.WHITE); page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); return b;
        }catch(Exception e){return null;}finally{try{if(page!=null)page.close();}catch(Exception ignored){}try{if(renderer!=null)renderer.close();}catch(Exception ignored){}try{if(pfd!=null)pfd.close();}catch(Exception ignored){}}
    }

    private static Rect centerCropSource(Bitmap b,float targetRatio){
        int w=b.getWidth(),h=b.getHeight(); float ratio=w/(float)h;
        if(ratio>targetRatio){int nw=Math.round(h*targetRatio),x=(w-nw)/2;return new Rect(x,0,x+nw,h);}
        int nh=Math.round(w/targetRatio),y=(h-nh)/2;return new Rect(0,y,w,y+nh);
    }

    private static void drawMetric(Canvas c,int x,int y,String value,String label,Paint p){
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(42);p.setColor(Color.WHITE);c.drawText(value,x,y,p);
        p.setTypeface(Typeface.DEFAULT);p.setTextSize(23);p.setColor(Color.rgb(190,198,224));c.drawText(label,x,y+38,p);
    }
    private static void drawCentered(Canvas c,String text,float y,float size,int color,Paint p,boolean bold){
        p.setShader(null);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(size);p.setColor(color);p.setTypeface(Typeface.create(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(text==null?"":text,WIDTH/2f,y,p);
    }
    private static String kicker(String mode){return "week".equals(mode)?"MY WEEK IN BOOKS":"year".equals(mode)?"MY READING YEAR":"MY MONTH IN BOOKS";}
    private static String title(String mode,ReadingRecapData.Summary summary){
        if("week".equals(mode))return "Week in Books";
        java.util.Calendar c=java.util.Calendar.getInstance();c.setTimeInMillis(summary.startMs);
        if("year".equals(mode))return c.get(java.util.Calendar.YEAR)+" in Books";
        return new java.text.SimpleDateFormat("MMMM",Locale.ENGLISH).format(c.getTime())+" in Books";
    }
    private static String duration(long ms){
        long min=Math.max(0,ms)/60000L,h=min/60,m=min%60;
        return h>0?(h+"h "+m+"m"):(m+"m");
    }
}
