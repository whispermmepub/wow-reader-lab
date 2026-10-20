package com.whisper.wowreader;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,35,36},manifest=Config.NONE,shadows={PdfPreviewRendererTest.RendererShadow.class,PdfPreviewRendererTest.PageShadow.class})
public class PdfPreviewRendererTest {
    private PdfPreviewRenderer renderer;
    private File file;
    @Before public void setup()throws Exception{
        RendererShadow.opens=RendererShadow.closes=PageShadow.closes=0;
        PageShadow.width=600;PageShadow.height=900;PageShadow.fail=false;
        renderer=new PdfPreviewRenderer();file=File.createTempFile("preview-pdf-",".pdf");Files.write(file.toPath(),new byte[]{1});
    }
    @After public void cleanup(){renderer.close();file.delete();}
    @Test public void repeatedPagesReuseOneRendererAndCloseEachPage()throws Exception{
        renderer.render(file,0,0xffffffff);renderer.render(file,1,0xffffffff);
        assertEquals(1,RendererShadow.opens);assertEquals(2,PageShadow.closes);assertEquals(0,RendererShadow.closes);
        renderer.close();renderer.close();assertEquals(1,RendererShadow.closes);
    }
    @Test public void fileIdentityChangeReopensRenderer()throws Exception{
        renderer.render(file,0,0);Files.write(file.toPath(),new byte[]{1,2});renderer.render(file,1,0);
        assertEquals(2,RendererShadow.opens);assertEquals(1,RendererShadow.closes);
    }
    @Test public void portraitAndLandscapeThumbnailsKeepAspectRatio()throws Exception{
        Bitmap portrait=renderer.render(file,0,0xff112233);
        assertEquals(373,portrait.getWidth());assertEquals(560,portrait.getHeight());assertEquals(0xff112233,portrait.getPixel(0,0));
        PageShadow.width=1200;PageShadow.height=300;Bitmap landscape=renderer.render(file,1,0);
        assertEquals(400,landscape.getWidth());assertEquals(100,landscape.getHeight());
    }
    @Test public void failedRenderClosesPageAndRendererAndCanRetry()throws Exception{
        PageShadow.fail=true;try{renderer.render(file,0,0);fail("expected render failure");}catch(IOException expected){}
        assertEquals(1,PageShadow.closes);assertEquals(1,RendererShadow.closes);
        PageShadow.fail=false;renderer.render(file,0,0);assertEquals(2,RendererShadow.opens);
    }
    @Test public void invalidPageClosesRenderer()throws Exception{
        try{renderer.render(file,5,0);fail("expected range failure");}catch(IOException expected){}
        assertEquals(1,RendererShadow.closes);assertEquals(0,PageShadow.closes);
    }
    @Implements(PdfRenderer.class)
    public static class RendererShadow {
        static int opens,closes;
        @Implementation protected void __constructor__(ParcelFileDescriptor fd){opens++;}
        @Implementation protected int getPageCount(){return 3;}
        @Implementation protected PdfRenderer.Page openPage(int index){return Shadow.newInstanceOf(PdfRenderer.Page.class);}
        @Implementation protected void close(){closes++;}
    }
    @Implements(PdfRenderer.Page.class)
    public static class PageShadow {
        static int width,height,closes;static boolean fail;
        @Implementation protected int getWidth(){return width;}
        @Implementation protected int getHeight(){return height;}
        @Implementation protected void render(Bitmap image,Rect clip,Matrix matrix,int mode){if(fail)throw new IllegalStateException("fixture error");}
        @Implementation protected void close(){closes++;}
    }
}
