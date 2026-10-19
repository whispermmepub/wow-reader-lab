# WoW Reader v2.20.1 / v65 — update candidate

v64 / v2.20.0 ကို ထုတ်ပြီးသားဖြစ်ကြောင်း ပိုင်ရှင်က အတည်ပြုထားသည်။ ဤ v65 သည် နောက် update အတွက် candidate ဖြစ်ပြီး Play Store သို့ မတင်ရသေးပါ။

## ပြင်ထားသည်များ

- ဖုန်းတစ်လုံးမှ ဖျက်ထားသောစာအုပ်ကို ဖုန်းဟောင်းမှ ပြန် upload လုပ်မိနိုင်သည့် လမ်းကြောင်းကို ကာကွယ်ထားသည်။
- Drive `appDataFolder` တွင် SHA-256 တစ်ခုချင်းစီအတွက် `wow_deleted_v1_<hash>` အမည်ဖြင့် metadata-only deletion marker ထိန်းထားသည်။ Marker အရင်တင်ပြီးမှ စာအုပ်နှင့် cover ကို ရှင်းသည်။ Marker ဖန်တီးမှု/cleanup မအောင်မြင်လျှင် deletion ကို အောင်မြင်ကြောင်း မမှတ်ဘဲ retry လုပ်နိုင်သည်။
- Book/cover upload၊ resumable upload နှင့် restore များတွင် deletion ကို စစ်သည်။ Upload နှင့် restore ပြီးစီးချိန်တွင်လည်း ထပ်စစ်သည်။ Network/queue database error ကို အလွတ်စာရင်းဟု မယူဘဲ sync error အဖြစ် ရပ်သည်။
- SQLite tombstone ကို sync ပြီးလျှင် မဖျက်တော့ဘဲ dirty flag ကိုသာ ရှင်းသည်။ Snapshot merge တွင် tombstone ကို စာအုပ် metadata မတိုင်မီ merge လုပ်သည်။ Tombstone table မပါသော legacy snapshot ကို ဆက်ဖတ်နိုင်သည်။
- Legacy ZIP restore နှင့် opt-in legacy smart sync တို့တွင်လည်း tombstone ကို လေးစားသည်။ Normal incremental sync သည် ပြောင်းထားသည့် hash များကိုသာ စစ်ပြီး library တစ်ခုလုံးကို မ scan ပါ။
- Discussion group: `https://t.me/+oKgpGSA_lKc4YTU1` သို့ ပြောင်းသည်။ Books channel `https://t.me/TheBookR` ကို မပြောင်းပါ။

## Data ကာကွယ်မှုနှင့် ကန့်သတ်ချက်

Remote deletion တွေ့လျှင် အခြားဖုန်းရှိ local စာအုပ်၊ progress၊ history များကို အလိုအလျောက် မဖျက်ပါ။ Cloud upload queue နှင့် upload checkpoint ကို ရပ်ပြီး local-only အဖြစ် ထိန်းထားသည်။ Restore လုပ်နေစဉ် deletion ဖြစ်သည့် race တွင် တင်ပြီးသား local bytes ကိုလည်း အလိုအလျောက် မဖျက်ဘဲ local-only အဖြစ် ထိန်းသည်။

လောလောဆယ် delete-wins စည်းမျဉ်းဖြစ်သည်။ ဖျက်ထားသော EPUB/PDF bytes အတိအကျကို နောက်တစ်ခါ import လုပ်လျှင် local ဖတ်နိုင်သော်လည်း cloud သို့ အလိုအလျောက် ပြန်မတင်ပါ။ Explicit cloud undelete/re-add feature ကို ဤ update တွင် မထည့်ထားပါ။ Marker ကို သက်တမ်းကုန်သတ်မှတ်၍ အလိုအလျောက် မဖျက်ပါ။

v64 နှင့် ပိုဟောင်းသော app များသည် marker ကို မသိသောကြောင့် အဟောင်း client များကိုယ်တိုင် ပြန်တင်ခြင်း/restore လုပ်ခြင်းကို မတားနိုင်ပါ။ Updated client က ထို bytes ကို live cloud book ဟု မယူပါ။ စက်အားလုံး update လုပ်ရန် လိုသည်။ ဤပြင်ဆင်မှုမတိုင်မီ ဖျက်ပြီး tombstone ပျောက်သွားသော deletion ဟောင်းများကို ပြန်သိနိုင်ခြင်း မရှိပါ။

Drive သည် marker/upload/install တို့အားလုံးကို ခြုံသည့် transaction မပေးသဖြင့် pre/post checks အလွန်နောက်ကပ်ပြီး ရောက်လာသော deletion ကို လုံးဝ atomic ဟု မအာမခံပါ။ Persistent marker သည် နောက်ဆက်တွဲ upload/restore အတွက် authority ဖြစ်သည်။

Package `com.whisper.wowreader`၊ မူလ signing identity၊ minSdk 23 နှင့် targetSdk 36 မပြောင်းပါ။ App data ရှင်းခြင်းနှင့် destructive schema migration မပါပါ။

## Regression စမ်းသပ်ခြင်း

```bash
bash tools/run-contract-tests.sh
```

မူလ Java contract suite ၆ ခုအပြင် deletion guard assertion ၅၈ ခုနှင့် Discussion/Books link စစ်ဆေးမှု ပါသည်။ Fake remote ဖြင့် publish/delete/ack အစီအစဉ်၊ retry၊ stale device၊ resumed upload၊ cover၊ upload/restore race၊ lookup failure နှင့် small-delta query အရေအတွက်ကို စစ်သည်။ ဤစမ်းသပ်မှုသည် live Drive သို့မဟုတ် ဖုန်းနှစ်လုံး စမ်းသပ်ခြင်း အစားမဟုတ်ပါ။

`SyncDeletionInstrumentation.java` သည် သီးခြား cache SQLite database များဖြင့် tombstone retention၊ queue/checkpoint suppression၊ progress/history retention၊ snapshot merge နှင့် legacy snapshot compatibility ကို စစ်ရန် ထည့်ထားသည်။ App ၏ တကယ့် database ကို မပြောင်းပါ။ Device/emulator မချိတ်ထားသဖြင့် runtime စမ်းသပ်မှု မလုပ်ရသေးပါ။ Debug app နှင့် debug test APK ကို သီးခြား test device တွင် install ပြီးနောက်:

```bash
adb shell am instrument -w -r -e syncDeletionOnly true com.whisper.wowreader.test/com.whisper.wowreader.RegressionInstrumentation
```

## Production မတင်မီ

1. Play Console ရှိ track အားလုံးတွင် versionCode 65 မသုံးရသေးကြောင်း စစ်ပါ။ v64 ထုတ်ပြီးသားဟူသည့် owner information အပေါ် မူတည်ပြီး 65 ရွေးထားခြင်းဖြစ်သည်။ Existing app Console ကို မစစ်နိုင်သေးပါ။
2. v64 → v65 ကို uninstall မလုပ်ဘဲ update လုပ်ပြီး စာအုပ်၊ notes၊ progress၊ account၊ cover နှင့် fonts ကျန်ရှိမှု စစ်ပါ။
3. ဖုန်း A မှ delete → sync ပြီးမှ stale ဖုန်း B ကို reconnect လုပ်၍ dirty book/cover နှင့် resumed upload မှ ပြန်မတင်မှု စစ်ပါ။ Local copy ကို ထိန်းထားမည့် စည်းမျဉ်းကို သတိပြုပါ။
4. Fresh install + Google sign-in + incremental/legacy restore နှစ်မျိုးဖြင့် ဖျက်ထားသောစာအုပ်ကို မဆွဲယူမှု စစ်ပါ။
5. Network interruption၊ retry၊ concurrent delete/upload နှင့် စာအုပ်အများအပြားရှိသော library ကို ဖုန်းပေါ် စစ်ပါ။
6. APK နှင့် AAB signature/build စစ်ဆေးမှုသည် အမှန်တကယ် Google sign-in/Drive sync အောင်မြင်မှုကို မအစားထိုးပါ။
