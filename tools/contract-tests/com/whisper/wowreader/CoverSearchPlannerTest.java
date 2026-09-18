package com.whisper.wowreader;

import java.util.List;

public final class CoverSearchPlannerTest {
    private static void ok(boolean v,String m){ if(!v) throw new AssertionError(m); }
    public static void main(String[] args){
        List<String> mm=CoverSearchPlanner.googleQueries("မရဏကင်းတမန်","ကျော်သူရ", "");
        ok(mm.size()>=3,"Myanmar title must get broad fallback queries");
        ok(mm.get(0).contains("မရဏကင်းတမန်"),"first broad query must preserve Myanmar text");
        List<String> en=CoverSearchPlanner.googleQueries("The Little Prince","Antoine de Saint-Exupéry","");
        ok(en.contains("The Little Prince"),"English title-only fallback required");
        ok(CoverSearchPlanner.openLibraryQuery("The Little Prince","Antoine de Saint-Exupéry","").contains("The Little Prince"),"Open Library query required");
        ok(CoverSearchPlanner.googleQueries("","","9780156012195").get(0).startsWith("isbn:"),"ISBN should be highest precision query");
        ok(CoverSearchPlanner.webImageQuery("မရဏကင်းတမန်","ကျော်သူရ","").contains("မရဏကင်းတမန်"),"Google Images fallback must preserve Myanmar title");
        ok(CoverSearchPlanner.webImageQuery("The Little Prince","Antoine de Saint-Exupéry","").contains("book cover"),"web fallback should request a cover");
        System.out.println("COVER_SEARCH_PLANNER_TEST_PASS");
    }
}
