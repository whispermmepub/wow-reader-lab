package com.whisper.wowreader;

public final class WholeBookPageModelTest {
    private static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
    public static void main(String[] args){
        WholeBookPageModel m=new WholeBookPageModel();
        m.reset(3);
        ok(!m.isComplete(),"new model must be incomplete");
        m.setChapterCount(0,10);
        m.setChapterCount(1,20);
        ok(m.wholePageIfKnown(1,7)==17,"chapter 2 page 7 must be whole-book page 17");
        ok(m.totalPagesIfComplete()==-1,"total must not lie before scan completes");
        m.setChapterCount(2,5);
        ok(m.isComplete(),"all three chapter counts must complete model");
        ok(m.totalPagesIfComplete()==35,"whole book total must be exact sum");
        ok(m.wholePageIfKnown(2,5)==35,"last page must equal total");
        m.reset(100_000);
        m.setChapterCount(0,1);
        ok(m.wholePageIfKnown(0,1)==1,"100k chapter model must stay bounded and correct");
        ok(m.totalPagesIfComplete()==-1,"100k incomplete model must not invent a total");
        System.out.println("WHOLE_BOOK_PAGE_MODEL_TEST_PASS");
    }
}
