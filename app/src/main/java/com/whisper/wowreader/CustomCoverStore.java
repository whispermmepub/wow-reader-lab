package com.whisper.wowreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

final class CustomCoverStore {
    private CustomCoverStore() {}
    static File importUri(Context context, Uri uri, String stableKey) throws Exception {
        if(context==null||uri==null)throw new Exception("Cover image is unavailable");
        File scratch=File.createTempFile("wow-cover-",".img",context.getCacheDir());
        try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(scratch)){
            if(in==null)throw new Exception("Unable to open cover image");copy(in,out);out.getFD().sync();
        }
        try{return normalize(context,scratch,stableKey);}finally{scratch.delete();}
    }
    static File importUrl(Context context,String url,String stableKey)throws Exception{
        HttpURLConnection c=openUrl(url);File scratch=File.createTempFile("wow-cover-web-",".img",context.getCacheDir());
        try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(scratch)){copy(in,out);out.getFD().sync();}
        finally{c.disconnect();}
        try{return normalize(context,scratch,stableKey);}finally{scratch.delete();}
    }
    static Bitmap downloadBitmap(String url,int targetW,int targetH)throws Exception{
        HttpURLConnection c=openUrl(url);File tmp=File.createTempFile("wow-cover-preview-",".img");
        try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){copy(in,out);}
        finally{c.disconnect();}
        try{return decodeSampled(tmp,targetW,targetH);}finally{tmp.delete();}
    }
    private static HttpURLConnection openUrl(String value)throws Exception{
        if(value==null||value.trim().isEmpty())throw new Exception("Cover URL is unavailable");String u=value.startsWith("http://")?"https://"+value.substring(7):value;
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","WoWReader/experimental");
        int code=c.getResponseCode();if(code<200||code>=300){c.disconnect();throw new Exception("Cover download failed ("+code+")");}return c;
    }
    private static File normalize(Context context,File source,String stableKey)throws Exception{
        BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getAbsolutePath(),b);if(b.outWidth<=0||b.outHeight<=0)throw new Exception("Unsupported or corrupt image");
        int sample=1;while(b.outWidth/sample>1800||b.outHeight/sample>1800)sample*=2;BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=sample;o.inPreferredConfig=Bitmap.Config.RGB_565;
        Bitmap bitmap=BitmapFactory.decodeFile(source.getAbsolutePath(),o);if(bitmap==null)throw new Exception("Unable to decode image");Bitmap oriented=orient(source,bitmap);if(oriented!=bitmap)bitmap.recycle();
        int w=oriented.getWidth(),h=oriented.getHeight();float scale=Math.min(1f,1000f/Math.max(w,h));Bitmap finalBitmap=oriented;
        if(scale<.999f){finalBitmap=Bitmap.createScaledBitmap(oriented,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);if(finalBitmap!=oriented)oriented.recycle();}
        File dir=new File(context.getFilesDir(),"custom_covers");if(!dir.exists()&&!dir.mkdirs()){finalBitmap.recycle();throw new Exception("Unable to create cover folder");}
        File dest=new File(dir,key(stableKey)+".jpg"),tmp=new File(dir,dest.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){if(!finalBitmap.compress(Bitmap.CompressFormat.JPEG,86,out))throw new Exception("Unable to save cover");out.getFD().sync();}finally{finalBitmap.recycle();}
        if(dest.exists()&&!dest.delete()){tmp.delete();throw new Exception("Unable to replace cover");}if(!tmp.renameTo(dest)){tmp.delete();throw new Exception("Unable to finalize cover");}return dest;
    }
    private static Bitmap orient(File source,Bitmap bitmap){try{ExifInterface e=new ExifInterface(source.getAbsolutePath());int x=e.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);Matrix m=new Matrix();
        if(x==ExifInterface.ORIENTATION_ROTATE_90)m.postRotate(90);else if(x==ExifInterface.ORIENTATION_ROTATE_180)m.postRotate(180);else if(x==ExifInterface.ORIENTATION_ROTATE_270)m.postRotate(270);else if(x==ExifInterface.ORIENTATION_FLIP_HORIZONTAL)m.preScale(-1,1);else if(x==ExifInterface.ORIENTATION_FLIP_VERTICAL)m.preScale(1,-1);
        if(!m.isIdentity())return Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);}catch(Exception ignored){}return bitmap;}
    static Bitmap decodeSampled(File file,int tw,int th){if(file==null||!file.isFile())return null;BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),b);int s=1;tw=Math.max(80,tw);th=Math.max(120,th);while(b.outWidth/s>tw*2||b.outHeight/s>th*2)s*=2;BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=s;o.inPreferredConfig=Bitmap.Config.RGB_565;return BitmapFactory.decodeFile(file.getAbsolutePath(),o);}
    static void delete(File f){if(f!=null&&f.isFile())f.delete();}
    private static String key(String v)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] d=md.digest((v==null?"book":v).getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(int i=0;i<12;i++)b.append(String.format(Locale.US,"%02x",d[i]));return b.toString();}
    private static void copy(InputStream in,FileOutputStream out)throws Exception{byte[] b=new byte[64*1024];int n;long total=0;while((n=in.read(b))>0){total+=n;if(total>50L*1024L*1024L)throw new Exception("Cover image is too large");out.write(b,0,n);}}
}
