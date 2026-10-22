package com.whisper.wowreader;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={24,35,36},manifest=Config.NONE)
public class CloudMergePolicyTest {
    private static final String GROUP="epub_location_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef_";
    private SharedPreferences prefs;
    @Before public void setup(){prefs=RuntimeEnvironment.getApplication().getSharedPreferences("cloud-test",0);prefs.edit().clear().commit();}
    @Test public void newerRemoteLocationCanMoveBackwardsButRemainsAtomic() throws Exception {
        prefs.edit().putInt(GROUP+"chapter",9).putInt(GROUP+"permille",800).putString(GROUP+"anchor","9:800").putLong(GROUP+"updated_ms",1000).commit();
        assertTrue(CloudMergePolicy.mergePreferences(location(2,100,2000),prefs));
        assertEquals(2,prefs.getInt(GROUP+"chapter",-1));
        assertEquals(100,prefs.getInt(GROUP+"permille",-1));
        assertEquals("2:100",prefs.getString(GROUP+"anchor",""));
        assertEquals(2000,prefs.getLong(GROUP+"updated_ms",0));
    }
    @Test public void staleRemoteCannotCreateHybridChapterAndOffset() throws Exception {
        prefs.edit().putInt(GROUP+"chapter",4).putInt(GROUP+"permille",750).putLong(GROUP+"updated_ms",3000).commit();
        assertFalse(CloudMergePolicy.mergePreferences(location(8,25,2000),prefs));
        assertEquals(4,prefs.getInt(GROUP+"chapter",-1));
        assertEquals(750,prefs.getInt(GROUP+"permille",-1));
        assertFalse(prefs.contains(GROUP+"anchor"));
    }
    @Test public void incompleteOrInvalidRemoteLocationIsNotPartiallyImported() throws Exception {
        JSONObject incomplete=new JSONObject().put(GROUP+"updated_ms",item("l",3000L)).put(GROUP+"chapter",item("i",3));
        assertFalse(CloudMergePolicy.mergePreferences(incomplete,prefs));
        assertFalse(prefs.contains(GROUP+"chapter"));
        assertFalse(CloudMergePolicy.mergePreferences(location(4,1001,4000),prefs));
        assertTrue(prefs.getAll().isEmpty());
    }
    @Test public void privateOperationalAndDeliveryStateNeverImports() throws Exception {
        JSONObject remote=new JSONObject().put("google_account_email",item("s","other@example.test"))
                .put("google_last_backup_ms",item("l",99L)).put("sync_updated_ms",item("l",100L))
                .put("reading_reminder_last_delivered_day",item("s","2026-10-19"))
                .put("reading_reminder_scheduled_due_ms",item("l",100L))
                .put("reading_reminder_last_error",item("s","Remote error"));
        assertFalse(CloudMergePolicy.mergePreferences(remote,prefs));
        assertTrue(prefs.getAll().isEmpty());
    }
    @Test public void legacyMergeRemainsConservativeAndAdditive() throws Exception {
        prefs.edit().putInt("epub_chapter_book.epub",3).putInt("percent_book.epub",40)
                .putString("reading_stats_days_json","{\"2026-10-18\":1000}").commit();
        JSONObject remote=new JSONObject().put("epub_chapter_book.epub",item("i",7)).put("percent_book.epub",item("i",55))
                .put("reading_stats_days_json",item("s","{\"2026-10-18\":900,\"2026-10-19\":500}"));
        assertTrue(CloudMergePolicy.mergePreferences(remote,prefs));
        assertEquals(3,prefs.getInt("epub_chapter_book.epub",0));
        assertEquals(55,prefs.getInt("percent_book.epub",0));
        JSONObject days=new JSONObject(prefs.getString("reading_stats_days_json","{}"));
        assertEquals(1000,days.getLong("2026-10-18"));assertEquals(500,days.getLong("2026-10-19"));
    }
    private static JSONObject location(int chapter,int progress,long when) throws Exception {
        return new JSONObject().put(GROUP+"chapter",item("i",chapter)).put(GROUP+"permille",item("i",progress))
                .put(GROUP+"anchor",item("s","untrusted anchor should be rebuilt")).put(GROUP+"updated_ms",item("l",when));
    }
    private static JSONObject item(String type,Object value) throws Exception{return new JSONObject().put("t",type).put("v",value);}
}
