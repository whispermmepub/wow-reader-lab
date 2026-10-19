# WoW Reader — v70 owner-accepted / v64 last confirmed live

## Latest owner authorization — v70 source publication

The owner accepted v70 / 2.21.4 and explicitly lifted the prior no-push instruction, authorizing its source publication to `whispermmepub/wow-reader-lab`. The owner will upload to Google Play personally; do not upload there. Earlier no-push/no-AAB statements below are historical.

The production-signed `WoW-Reader-v2.21.4-v70-PlayStore-production.aab` has been built and verified: package `com.whisper.wowreader`, versionCode 70, targetSdk 36, non-debuggable, valid AAB signature, certificate matching the accepted APK and production certificate, and successful bundletool validation. A source rebuild reproduced the accepted APK byte-for-byte. AAB SHA-256: `bb4886aebda51b554eb3d8b943a1cb6b31df0e9c0e045e85e7a98f328db791ad`. Evidence and binaries remain outside Git under `/workspace/wow-reader-v70-update/`; never commit signing secrets. Confirm versionCode 70 is unused across Play tracks before uploading. Automated verification is not a guarantee of Play acceptance or all-device behavior; remaining device QA below still applies.

## နောက်ဆုံး — v70 / 2.21.4 adaptive preview update

ပိုင်ရှင်က v69 ကောင်းလာပြီဟု ပြောပြီး seek preview ပိုကြီးရန်၊ None animation၊ storage permissions နှင့် reminder မပြခြင်း မေးထားသည်။ ဤပြင်ဆင်ချက်သည် v69 source ပေါ်မှ ဆက်လုပ်ထားပြီး ယခင်အောင်မြင်သော fixes ကို revert မလုပ်ထားပါ။ GitHub commit/push၊ Play upload၊ AAB generation မလုပ်ထားပါ။

- Preview preferred size **148×210dp → 264×374dp**။ Root/system-bar/cutout safe bounds နှင့် seek bar အထက် space အတွင်း uniform shrink လုပ်သည်။ Height သည် usable root 62% ထက် မကျော်ပါ။ Small/landscape/multi-window၊ padding၊ anchor movement၊ off-screen anchor fallback၊ listener cleanup tests ပါသည်။ EPUB physical pagination viewport/textZoom/engine နှင့် read-only progress behavior မပြောင်းပါ။ Panel resize သည် EPUB view/generation ကို မပယ်ပါ။ Preview chapter 2MiB limit ကို policy constant တစ်ခုတည်းသို့ စုထားပြီး v69 actual limit မပြောင်းပါ။
- Contract suites PASS; **251 host/Robolectric tests, 0 failures/errors/skips**; actual JavaScript Desktop Chromium **30/30 cases PASS**။ assembleRelease/lintRelease/testDebugUnitTest/compileDebugAndroidTestJavaWithJavac PASS။ Lint fatal/error 0, warnings 175; ယခင် Firebase Kotlin metadata diagnostic ရှိနေဆဲ။ Instrumentation compile သာဖြစ်သည်။
- APK package/version/non-debuggable၊ **v69 နှင့် တိုက်ရိုက် signing certificate match**၊ ZIP 16-KiB alignment၊ checksum၊ git diff --check verified။ APK `/workspace/wow-reader-v70-update/WoW-Reader-v2.21.4-v70-test-update.apk` (15,996,612 bytes); SHA256 `ae42dd08e1fc8e885ee91607bf0f82701f52e33a4df3cdd99ff385bccdc84e94`။ Evidence `/workspace/wow-reader-v70-update/VERIFICATION.json`; build script/log `/workspace/release-private/build_v70_apk.py`, `/workspace/release-private/build-v70-apk.log`။
- **None** သည် page/chapter transition effects မပါဘဲ direct page change ဖြစ်သည်; loading spinner/loading-card fade/system/UI animations အားလုံးပိတ်ခြင်း မဟုတ်ပါ။ Chapter loading/readiness လိုသေးသည်။
- **Storage**: broad READ/WRITE_EXTERNAL_STORAGE မလိုပါ။ MainActivity ACTION_OPEN_DOCUMENT မှ ရွေးထားသည့် URI ကို app-private library သို့ copy သွင်းသည်; manual backup/restore သည် ACTION_OPEN_DOCUMENT_TREE grant အသုံးပြုသည်။ Manifest တွင် legacy/all-files access မထည့်ပါ။ Official source: https://developer.android.com/training/data-storage/shared/documents-files ။ Provider write support/revoked grant/offline/missing space နှင့် Android/data, Android/obb picker restrictions ရှိနိုင်သည်; all-device verified မဟုတ်ပါ။
- **Reminder စစ်ချက်သာ၊ behavior မပြင်ထားပါ**: Daily reading reminder default OFF; Only remind me if I haven't read today default ON။ ထို checkbox ON ဖြစ်လျှင် device-local today reading >=1ms နှင့် notification suppress လုပ်သည်။ Android13+ POST_NOTIFICATIONS denied ဖြစ်လျှင် alarm မစီစဉ်ဘဲ cancel; permission deny callback က enabled=false ထားသည်။ Receiver တွင် global notification block၊ same-local-day duplicate ကိုပါ skip လုပ်သည်။ reading_reminders channel blocked/DND/OEM restrictions ကြောင့် notify call ဖြစ်သော်လည်း မမြင်ရနိုင်သည်။ Streak option သည် wording ကိုပြောင်းခြင်းသာ; streak မရှိရုံဖြင့် ordinary reminder မတားပါ။ Selected time အချိန်လွန်/တူနေပြီဆို next day; default20:00 device local time။ setAndAllowWhileIdle သည် inexact ဖြစ်၍ နောက်ကျနိုင်သည် (https://developer.android.com/develop/background-work/services/alarms)။
- **ဖုန်းဘက် လိုအပ်သည့် evidence**: reminder settings screenshot (enabled, onlyUnread, selected time), မျှော်လင့်သည့်နေ့ စာဖတ်ပြီး/မပြီး၊ phone model + Android version၊ device local time/timezone၊ app/channel notification permission status။ မရှင်းသေးလျှင် battery/autostart/force-stop status နှင့် scheduled_due/last_delivered_day/last_error prefs diagnostics လိုသည်။ အကြောင်းရင်းတစ်ခုတည်း အတည်မပြုရသေး၊ notification ဖုန်းပေါ် delivery မစမ်းရသေး။

ပိုင်ရှင်ကို **uninstall/clear data မလုပ်ဘဲ v69 ပေါ် update အဖြစ်** စမ်းရန် ပို့ပါ။ Actual phone/EPUB/layout/latency/notification QA မရှိသေးသဖြင့် ဖုန်းတိုင်းသေချာဟု မဆိုရ။ အောက်ရှိ v69 security/sync/OEM gates မပြောင်းပါ။

## နောက်ဆုံး — v69 / 2.21.3 စမ်းသပ် update

**ဤအပိုင်းသည် အောက်ရှိ v68/v66 မှတ်တမ်းများထက် နောက်ကျသည်။** ပိုင်ရှင်က v68 တွင် formatting/preview စသည်တို့ ကောင်းလာပြီဟု ပြောပြီး **None animation အခန်းကူး effect** နှင့် **စာအုပ်ကြီး chapter boundary delay** နှစ်ချက် ဆက်ပြင်ခိုင်းသည်။ Video: `/home/ubuntu/upload/1000017459.mp4`။ GitHub push/commit၊ Play upload၊ AAB အသစ် မလုပ်ထားပါ။ v64 သည် live ဖြစ်နေဆဲ။

### ပြင်ထားမှု

- Warm incoming WebView ကို promote/Previous-end restore မပြီးမီ alpha 1 ဖြင့် ပြခဲ့ပြီး၊ `confirmStableChapterReveal` က ပြန်ဖျောက်ကာ 70 ms အကြိမ်ကြိမ်နှင့် frame နှစ်ခု ထပ်စောင့်နေမှု ဖယ်ထားသည်။ 82% viewport အောက်ရှိ legitimate flow width ကို reject လုပ်နိုင်သည့် native probe ကိုလည်း ဖယ်ထားသည်။ JS engine-ready အမှန်ရပြီး `postVisualStateCallback` မှ compositor commit ရမှ **တိုက်ရိုက်လဲပြ** သည်။ Callback ပျောက်သော OEM အတွက် 180 ms bounded fallback ရှိပြီး engine-ready generation မကိုက်လျှင် မစပါ။
- Cold boundary လည်း spare WebView ထဲ target chapter ဖွင့်ပြီး adopt လုပ်၍ outgoing page ကို alpha 1 ဖြင့် ဆက်ပြသည်။ Incoming သည် alpha .01 ဖြင့် attached/visible ဖြစ်နေပြီး rendered အဆင်သင့်မှ ပြသည်။ Screenshot/fade မလိုပါ။ Old spare DOM ကို chapter အသစ်အဖြစ် မမှားယူရန် expected negative warm-generation guard ထည့်ထားသည်။
- None/Slide ရွေးခြင်းကို typography persistence နှင့် ခွဲထား၍ warm/in-flight adjacent chapter cache ကို မပယ်တော့ပါ။ None ရွေးချိန်နှင့် chapter load တိုင်းတွင် queued native Slide callbacks ကို revision token ဖြင့် invalidate လုပ်သည်။ End-action အပါအဝင် owner/chapter-generation/token guard မကိုက်လျှင် current chapter ကို unlock/report မလုပ်နိုင်ပါ။
- Reader-owned page flow တွင် transition/animation/transform ကို inline-important ဖြင့် ချိန်၍ publisher CSS မှ wrapper ကို animate မလုပ်နိုင်စေသည်။ မူလ EPUB prose/layout နှင့် global typography rules မပြောင်းထားပါ။
- Pagination full scan သည် 160-node timer slices အစား **1024-node ceiling / 12 ms budget** ဖြင့် MessageChannel task yields သုံးသည်။ မရှိ/disabled runtime တွင် timer fallback ရှိသည်။ Completion/failure/cancellation တွင် ports ပိတ်သည်။ Text/media မကျော်၊ node cap မထည့်၊ page map/အဆုံးစာမျက်နှာ ထိန်းထားသည်။

### စစ်ဆေးပြီး

- Contract suites PASS။ Host/Robolectric **236 tests / 0 failure/error/skip**။ New cold handoff၊ compositor callback/fallback၊ engine-ready mismatch၊ stale owner/generation/slide token၊ None preference cache preservation ပါဝင်သည်။ API23/28/35/36 reader matrix ဖြစ်သည်။
- Actual exported JavaScript **30/30 Desktop Chromium regression cases** PASS။ Existing EPUB authored HTML/XHTML alignment/margins/global fonts၊ preview-related engine behavior၊ Previous-end restoration၊ None + publisher-important animation rule၊ stale/cold spare promotion၊ missing MessageChannel၊ long-chapter yield/last page၊ cancellation/error ပါဝင်သည်။
- `assembleRelease`, `lintRelease`, `testDebugUnitTest`, `compileDebugAndroidTestJavaWithJavac` PASS။ Lint fatal/error 0; warnings ရှိနေဆဲ။ Firebase Kotlin metadata diagnostic ရှိနေဆဲဖြစ်သည်။ Instrumentation compile သာလုပ်ထားပြီး device ပေါ် မ run ပါ။
- Signing သည် **လက်ရှိ v68 APK နှင့် တိုက်ရိုက်နှိုင်းယှဉ်၍ တူသည်**။ VersionCode69/versionName2.21.3၊ package၊ non-debuggable နှင့် 16-KiB ZIP alignment verified။ `git diff --check` PASS။
- Same synthetic 26,000-text-node / 130-page document၊ scheduler ပိုင်းသာပြောင်း၍ Desktop Chromium 3 runs median **v68 1360 ms → v69 745 ms**။ နှစ်မျိုးလုံး 130 pages နှင့် final sentinel reachability တူသည်။ **ပိုင်ရှင်ဖုန်း/EPUB speed guarantee မဟုတ်ပါ**။

### ပို့ရန်နှင့် QA ကန့်သတ်ချက်

- `/workspace/wow-reader-v69-update/WoW-Reader-v2.21.3-v69-test-update.apk` — signed test update; v68 ကို မဖျက်ဘဲ update အဖြစ် စမ်းရန်။ Uninstall/clear data မလုပ်ရ။
- `/workspace/wow-reader-v69-update/VERIFICATION.json` — checksum၊ certificate match၊ exact test counts၊ lint နှင့် benchmark runs။
- Build script `/workspace/release-private/build_v69_apk.py`; log `/workspace/release-private/build-v69-apk.log`။
- Source tests: `ChapterVisualCommitTest.java`, `ChapterTransitionIntegrationTest.java`, `tools/test-reader-engine.py`, `tools/test-reader-publisher-layout.py`။

**ကျန် QA:** ADB device မရှိ၊ မူရင်း reproduction EPUB မရသေး။ Owner ဖုန်းတွင် None ဖြင့် forward/backward boundary၊ Previous-end ပထမ frame၊ cold/warm large/image-heavy chapters၊ Slide→None ပြောင်းပြီးချက်ချင်းကူးမှု၊ seek preview၊ cross-book typography၊ resume/upgrade data retention နှင့် memory/latency ကို စမ်းရန် ကျန်သည်။ v68 ၏ preview >2 MiB chapter limit နှင့် အောက်မှတ်တမ်းရှိ security/sync/OEM gates မပြောင်းထားပါ။ “ဖုန်းတိုင်းအကုန်ပြေလည်ပြီ/instant ဖြစ်ပြီ” ဟု မဆိုရ။ Video automated analysis ၏ selected-option timestamp အချို့ မှားနေသဖြင့် evidence အဖြစ် မကိုးကားရ; ကိုယ်တိုင်ဖတ်ထားသော 21 s နှင့် 38 s frames တွင် None selected ဖြစ်သည်၊ 22.5 s frame တွင် Slide selected ဖြစ်သည်။ Keyframes `/tmp/v69-frames/`။

---


## နောက်ဆုံး — v68 စမ်းသပ် candidate / v67 ကို ပိုင်ရှင်က reject လုပ်ထားသည်

**ဤအပိုင်းသည် အောက်ရှိ v66/source-only မှတ်တမ်းအားလုံးထက် နောက်ကျသည်။** Play Store live သည် v64 ဖြစ်နေဆဲ။ v67 တွင် မူလ EPUB alignment ပျက်ခြင်းနှင့် seek preview ပျက်ခြင်းကို ပိုင်ရှင်က video ဖြင့် ပြထားသည်။ ယခု **v2.21.2 / versionCode 68** သည် စမ်းသပ် candidate သာဖြစ်သည်။ GitHub commit/push၊ Play upload နှင့် AAB အသစ် မလုပ်ထားပါ။

### ယခု ပြင်ထားမှု

- Publisher ၏ centre/left/right၊ prose margins၊ padding၊ width၊ indentation ကို အမြဲဖျက်နေသော typography rules ဖယ်ထားသည်။ **Original EPUB** နှင့် **Reader settings** ကို explicit modes အဖြစ် ခွဲထားသည်။ Font profile နှင့် mode သည် စာအုပ်အားလုံးအတွက် global တစ်ခုတည်း ဖြစ်ပြီး per-book override ကို ပြန်မထည့်ထားပါ။ ရှိပြီးသား customized font/size/weight/line-spacing ကို upgrade က မဖျက်ပါ။ Legacy forced alignment ကို publisher alignment သို့ migration လုပ်သည်။ မပြင်ထားသည့် fresh profile သည် Original EPUB mode ဖြင့် စသည်။
- Column wrappers ကြောင့် ပျက်သည့် `body > child` CSS rules ကို specificity မတိုးစေသော `:where` ဖြင့် ပြန်ချိတ်ပြီး Scroll mode တွင် ပြန်ဖယ်သည်။ Body class/id၊ attribute၊ simple pseudo selectors နှင့် imported CSS ကို ထောက်ပံ့သည်။ အလွန်ဟောင်းသော WebView တွင် `:where` မရှိလျှင် descendant fallback သာဖြစ်၍ authored direct-child semantics အပြည့်အစုံ တူသည်ဟု မဆိုရပါ။ Original mode သို့ ပြန်ရွေးလျှင် inline typography၊ class/style attribute မရှိခဲ့မှုနှင့် reader-added language ကို ပြန်ထိန်းသည်။
- Incoming view ၏ animation frame ရပ်နေလျှင် 48 ms timer fallback ဖြင့် layout ဆက်လုပ်သည်။ JavaScript failure ကို bridge သို့ ပြန်တင်သည်။ Render-ready flag နှင့် timeout failure ကို ခွဲထားပြီး watchdog ကို success ဟု မသတ်မှတ်တော့ပါ။ Error တွင် Retry/Contents recovery ရှိသည်။ Instrumentation က ready generation ကိုပါ စစ်ရသည်။
- Page-map scan ၏ 24,000-node cap ဖယ်ထားပြီး 160 nodes / 12 ms chunks ဖြင့် timer yield လုပ်သည်။ Chapter/mode epoch ပြောင်းလျှင် scan အဟောင်း ရပ်သည်။ စာရှည်အဆုံးပိုင်း page မပျောက်ရန် test ရှိသည်။ ဤသည် instant loading သို့မဟုတ် OEM speed guarantee မဟုတ်ပါ။
- Video 170 s frame တွင် preview ၏ data-URL/base64 tail နှင့် `net::ERR_INVALID_URL` ကို တိုက်ရိုက်မြင်ရသည်။ Preview ကို data URL မသုံးသော **private local HTML file**၊ encoded chapter-directory base၊ UTF-8၊ safe publication resources ဖြင့် ပြောင်းထားသည်။ Main reader နှင့် တူသော page engine၊ physical viewport၊ textZoom ကို scale ချပြသည်။ Same-chapter drag ကို cancel/restart မလုပ်ဘဲ latest target ကို ready ပြီးမှ ချိန်သည်။ Bridge က main reading progress မရေးပါ။ Hide/reconfigure/destroy တွင် WebView နှင့် staged preview files ကို ရှင်းသည်။ Preview သည် read-only ဖြစ်သည်။

### နောက်ဆုံး အတည်ပြုထားမှု

- Contract suites အားလုံး PASS။ `testDebugUnitTest` — **188 tests, 0 failures/errors/skips**။ Reader lifecycle matrix တွင် API23/28/35/36၊ preview tests တွင် API23/35/36 ပါဝင်သည်။ ဤသည် host/Robolectric tests ဖြစ်ပြီး Android ဖုန်းပေါ် စမ်းထားခြင်း မဟုတ်ပါ။
- Actual exported reader JavaScript ကို Desktop Chromium တွင် **25/25** PASS။ HTML/XHTML original alignment/indent/margins၊ CSS cascade/attribute/pseudo selectors၊ mode round-trip၊ global custom font + authored alignment၊ missing rAF/fonts၊ stale callback၊ in-flight handoff၊ 26,000 text nodes အဆုံးစာမျက်နှာနှင့် async failure/cancellation ပါဝင်သည်။
- `lintRelease` task PASS၊ fatal/error 0; warnings ရှိနေဆဲဖြစ်၍ warning-free ဟု မပြောရ။ Firebase Kotlin metadata diagnostic ရှိနေဆဲဖြစ်သည်။ `compileDebugAndroidTestJavaWithJavac` PASS ဖြစ်သော်လည်း instrumentation မ run ထားပါ။
- APK package/version၊ non-debuggable၊ production/v67 certificate match နှင့် ZIP 16-KiB alignment စစ်ပြီးသည်။ `git diff --check` PASS။

### ဖိုင်များနှင့် နောက်တစ်ဆင့်

- Test APK: `/workspace/wow-reader-v68-update/WoW-Reader-v2.21.2-v68-test-update.apk`
- Verification: `/workspace/wow-reader-v68-update/VERIFICATION.json`
- Build: `/workspace/release-private/build_v68_apk.py`; log `/workspace/release-private/build-v68-apk.log`
- Browser reports: `app/build/reports/reader-engine-regression-v68.json`, `app/build/reports/reader-publisher-regression-v68.json`.
- Owner video: `/home/ubuntu/upload/1000017456.mp4`; exact preview error frame `/tmp/wow-v67-video-frames/error-03.png`. Research evidence `/tmp/wow-v68-external-evidence.md` links official Cantook Original-formatting, W3C EPUB and Readium preference documentation; no claim about Google/Cantook proprietary internals or switching our engine to Readium.

**မပြီးသေးသော QA:** မူရင်း reproduction EPUB files မရသေး၊ ADB device/emulator/KVM မရှိ။ Owner ၏ Xiaomi/Vivo/Oppo/HONOR ပေါ် မူလ EPUB alignment၊ cold/warm ရှေ့နောက်အခန်းကူးမှု၊ seek target preview နှင့် release၊ fonts across chapters/books၊ resume/bookmarks/rotation၊ upgrade/data retention နှင့် latency/memory ကို စစ်ရန် ကျန်သည်။ Preview chapter သည် 2 MiB ထက်ကြီးလျှင် ဖြတ်ပြခြင်းမဟုတ်ဘဲ explicitly unavailable ဖြစ်သည်။ ထို limit ကြောင့် owner စာအုပ်နှင့် ကိုက်မကိုက် file လိုအပ်သည်။ Existing main reader untrusted-EPUB security/sync/device gates အောက်တွင် ရှိနေဆဲဖြစ်သည်။ **“အကုန်ပြေလည်ပြီး၊ Cantook/Play Books နှင့် တန်းတူမြန်ပြီ” ဟု မပြောရ။** App ကို uninstall/clear data မလုပ်ဘဲ update အဖြစ် စမ်းရန်သာပို့ပြီး install rejected ဖြစ်လျှင် installed/Play signing cert ကို နှိုင်းယှဉ်ရမည်။

---


## နောက်ဆုံး source-only ပြင်ဆင်မှု — 2026-10-20

**ဤအပိုင်းသည် အောက်ရှိ v66 build မှတ်တမ်းထက် နောက်ကျသည်။** v64 သည် live ဖြစ်နေဆဲ၊ ယခင်ပို့ထားသော v66 APK/AAB များထဲတွင် ယခု source fixes မပါသေးပါ။ Version/signing မပြောင်း၊ APK/AAB အသစ် မထုတ်၊ commit/push/Play upload မလုပ်ထားပါ။ ယခု source အတွက် ယခင် v66 VERIFICATION.json ကို အထောက်အထားမယူရပါ။

### ပြင်ထားသည့်အချက်များ

- အခန်းကူးနေစဉ် `chapterLoading` ကြောင့် အသင့်ရှိသော preload ကို လက်မခံတော့သည့် guard ပြင်ထားသည်။ Preload သည် active reader နှင့် တူညီသော typography၊ fonts၊ pagination engine ကို အပြည့်အစုံသုံးသည်။
- မပြီးသေးသော adjacent preload ကို 900 ms စောင့်ပြီး ပယ်/ပြန်ဖွင့်တော့မည်မဟုတ်။ ရှိပြီးသား WebView ကို လွှဲယူကာ readiness ကို ဆက်စောင့်သည်။ ပြီးခဲ့သောအခန်း၏ အဆုံးနေရာ/locator ကို ready နှင့် in-flight handoff နှစ်မျိုးလုံးတွင် ပြန်ချိန်သည်။
- 420 ms chapter debounce ကြောင့် boundary request လွတ်သွားခြင်း ဖယ်ရှားထားသည်။ Callback များကို WebView owner၊ chapter generation၊ expected URL ဖြင့် စစ်သည်။ PixelCopy deferral 250 ms ကန့်သတ်ထားပြီး font readiness မပြီးလျှင်လည်း အဆုံးမရှိ မစောင့်တော့ပါ။ Chapter callback ပျောက်လျှင် 3 s တွင် style retry၊ 7 s တွင် သတိပေးပြီး controls ပြန်လွှတ်သည်။ နောက်ဆုံးနည်းသည် rendering အောင်မြင်ကြောင်း သတ်မှတ်ခြင်း မဟုတ်ပါ။
- Typography သည် **စာအုပ်အားလုံးအတွက် global profile တစ်ခုတည်း** ဖြစ်သည်။ Per-book chooser/load/save ကို reader လမ်းကြောင်းမှ ဖယ်ထားပြီး ဟောင်းသော per-book data ကို မဖျက်ထားပါ။ `GlobalTypographySettings` က font size/family/weight၊ line spacing၊ side margin၊ alignment၊ auto spacing ကို မူလ `epub_*` keys များဖြင့် သိမ်းသည်။ v19 defaults marker ကြောင့် ရှိပြီးသော settings မပျက်တော့ပါ။
- `ReaderTypographyCss` က publisher ၏ px/pt/% နှင့် inline-important prose size/margins ကို တစ်သမတ်တည်း ချိန်သည်။ TextZoom သာ user font scale ဖြစ်သည်။ Headings၊ emphasis၊ sub/sup၊ code နှင့် media ကို ထိန်းထားသည်။ ရွေးထားသော font မှ Publisher သို့ ပြန်ပြောင်းလျှင် မူလ inline font-family ကို ပြန်ရသည်။
- Whole-book counter သည် reader နှင့် တူညီသော engine ကို သုံးသည်။ Chapter/page/style interaction က counter ကို ရပ်စေပြီး idle အချိန်မှ ပြန်လုပ်သည်။ Low-RAM devices တွင် တတိယ WebView မဖန်တီးတော့ပါ။

### နောက်ဆုံး စမ်းသပ်မှုရလဒ်

- `bash tools/run-contract-tests.sh` — အားလုံး PASS။
- `:app:testDebugUnitTest` — **161 tests၊ 0 failures/errors/skips၊ 11 suites**။ အသစ်ထည့်ထားသော transition integration tests တွင် API 23/28/35/36 စစ်ထားသည်။
- `:app:lintRelease` — task အောင်၊ **170 warnings၊ fatal/error issue 0**။ Firebase dependency ၏ Kotlin metadata version diagnostic သည် log တွင် ရှိနေဆဲဖြစ်သည်။ Lint warning-free ဟု မဆိုရပါ။
- `python3 tools/test-reader-engine.py --json app/build/reports/reader-engine-regression.json` — **12/12 PASS**။ Unit test မှ export ထုတ်သော actual reader/promote scripts ကို local Chromium တွင် run ထားသည်။ Publisher CSS နှစ်မျိုး၏ typography/page-count တူညီမှု၊ pending font၊ ready/in-flight Previous-end restoration၊ stale font callback၊ selected font/weight နှင့် Publisher restoration၊ blank နှင့် SVG-only chapters ပါဝင်သည်။ **ဖုန်း WebView latency benchmark မဟုတ်ပါ။**
- `git diff --check` — PASS။ `adb devices` — device/emulator မရှိ။ ယခု session အတွင်း APK/AAB အသစ် မရှိကြောင်း စစ်ထားသည်။

ရလဒ်တည်နေရာများ — `/tmp/wow-reader-final-validation.log`, `/tmp/wow-reader-contract-tests.log`, `/tmp/wow-reader-browser-tests.log`, `app/build/reports/reader-engine-regression.json`, `app/build/test-results/testDebugUnitTest/`, `app/build/reports/lint-results-release.xml`။

### ဖုန်းအစစ်တွင် စစ်ရန် ကျန်ရှိမှု

Xiaomi/Vivo/Oppo/HONOR တွင် ရှေ့နောက် chapter boundary အကြိမ်များစွာ ကူးခြင်း၊ cold/warm နှစ်မျိုး၊ Pages/Scroll၊ None/Slide၊ long/image-heavy chapters၊ font/margin/alignment ကို စာအုပ်ပြောင်းပြီး ပြန်ဖွင့်ကြည့်ခြင်း၊ update/data preservation နှင့် memory/latency တိုင်းတာခြင်း ကျန်သည်။ Xiaomi position issue နှင့် Vivo slow-open issue အတွက် actual EPUB files မရသေးပါ။ Play Books/Cantook နှင့် တန်းတူမြန်ကြောင်း၊ ဖုန်းတိုင်းပြဿနာကင်းကြောင်း မဆိုရပါ။ APK/AAB အသစ်မထုတ်မီ ပိုင်ရှင်၏ တောင်းဆိုမှုကို စောင့်ရမည်။


## Owner constraints

- Live Play Store release confirmed by owner: **v2.20.0 / versionCode 64**.
- Current local candidate: **v2.21.0 / versionCode 66**. v65 was local-only and is superseded by this candidate, with its deletion fixes retained.
- **Do not commit/push to GitHub or upload to Play Store without the owner requesting it.** Nothing has been committed, pushed or uploaded in this work.
- Preserve production signing identity. Signing kit, passwords and private build scripts live outside Git under `/workspace/release-private`; never print or include secrets in source/artifacts.
- Owner wants both signed APK for phone testing and signed AAB for eventual Play submission. Neither is a declaration of production readiness.

## Workspace and deliverables

- Repo: `/workspace/wow-reader-lab`, branch `main`, `whispermmepub/wow-reader-lab`.
- Package `com.whisper.wowreader`, minSdk23, compile/target36; app source uses Java17; host tests including Android16 require Java21.
- Candidate artifacts: `/workspace/wow-reader-v66-update/`.
- Owner-facing feature/limitations/QA guide: `/workspace/wow-reader-v66-update/UPDATE-README.md`.
- Build/signature/test summary: `/workspace/wow-reader-v66-update/VERIFICATION.json`.
- Build entry point: `/workspace/release-private/build_v66.py`; logs `build-v66-final.log`. Treat a successful final build plus current checksums as authoritative, not an older log.
- Source snapshot before v66 changes: `/workspace/wow-reader-v65-source-before-next-update.tar.gz`.

## What changed

Home maximum six Continue Reading books and refresh rules; deleted-book filtering in reading-calendar book lists without deleting historical reading time; durable EPUB locators and timestamped atomic cross-device chapter+offset merging; explicit global/per-book typography and independent custom text/background colors; bounded EPUB metadata/readability/cache with blank-spine regression coverage; PDF bitmap/text-cache limits; opt-in daily reading reminder with permission/reboot/time-zone handling; slider PDF thumbnail and bounded EPUB chapter preview.

**Latest owner clarification:** page turns within an EPUB chapter are fine; the lag is specifically the boundary from one chapter to the next, on all phones. Chapter-transition changes prioritize one ready preload, skip unnecessary capture/animation for None, avoid duplicate style injection, pause speculative whole-book scans during reading/loading, and disable the third counter WebView on low-RAM devices. `WoWReaderTiming` debug logs have no content/user data. Hardware timing remains unmeasured.

Sync now binds the selected Google account, reconciles remote metadata before publishing, preserves local paths/covers/checkpoints/pending changes, retains tombstones without deleting another device's local bytes, does not republish unchanged full state on idle polls, uses Android background thread priority, and defers at book/chunk boundaries while Home is hidden. Resume probes Google for actual acknowledged offsets; missing Range means zero acknowledged, not "all bytes sent". Book and cover share one transport, with bounded stalled-ack handling and no success before server confirmation.

State backups use a frozen SQLite file before ZIP compression rather than compressing a changing live database. Snapshot merge errors fail closed.

## Validation commands

```sh
bash tools/run-contract-tests.sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
ANDROID_HOME=/workspace/release-private/android-sdk \
/workspace/release-private/tools/gradle-8.11.1/bin/gradle \
  --no-daemon --max-workers=4 :app:testDebugUnitTest :app:lintRelease
python3 /workspace/release-private/build_v66.py
```

Robolectric4.17 host matrix includes API23/28/35/36 where configured. Android16 requires Java21 plus the test-only `jdk.internal.access` export in app/build.gradle. No test failures/skips are accepted by final verification. Contract tests are logic tests, not a 100,000-book phone benchmark. Instrumentation APK compiles, but there is no attached Android device/emulator.

## Production gates / remaining work

1. Actual Xiaomi EPUB reproduction fixture and actual Vivo slow-opening book are still requested; no fixture has been received. User video is `/home/ubuntu/upload/1000017443.mp4` (preview interaction reference).
2. Real Xiaomi15 / VivoY30 Android12 / OppoA58 / HONORX6 device QA, v64-to-v66 upgrade, resume/bookmark/font/rotation, chapter-transition latency/memory and preview lifecycle.
3. Two-device Google Drive account/permission/restore/offline-delete/race tests and notification/OEM battery tests. New phone still needs explicit Restore to download book bytes.
4. Metadata still uses state SQLite snapshot/ZIP when local state changes; NOT a fully incremental metadata journal. Durable WorkManager sync, network constraints, exponential backoff/jitter, per-record versioning and atomic server conditional writes remain future work. Existing modified-time recheck is not an atomic concurrency guarantee.
5. Legacy reader WebViews have broad file/JavaScript access; untrusted EPUB isolation/security audit is a release gate. Accessibility/lint warnings remain. Do not claim all phones are lag-free.
6. APK/AAB cert matches supplied production kit/v64 AAB. Installed Play app may use a different Play App Signing key; if APK update is rejected, do not uninstall/clear data—compare installed/Play app-signing cert first. Check versionCode66 availability across Play tracks before any upload.

The six original contract suites and all v65 tombstone protections remain. Do not revert valid v65 code when addressing v66 findings. `docs/V65_DELETION_FIX.md` documents earlier deletion behavior.

## Final review follow-up

A second focused review found and fixed: Home's fixed 20-row prefilter (now SQL eligible paging until six valid files, with background query/path validation and generation-guarded UI rendering); persisted custom colors not applied by color helpers (now independently normalized/applied); 180ms preload cancellation causing duplicate chapter loads (now preserves preload through 900ms readiness timeout with early handoff); prefix/suffix-only EPUB readability losing middle-only content (now streaming full-scan fallback); filename+size reader identity collision (now background content SHA-256 cached per open for location/bookmarks, legacy migration, and newer database-restore location timestamps reconciled).

Typography 'implicit scope' audit suspicion was rejected after checking the explicit enabled flag and save call sites; no unnecessary change was made. Added ReaderLocationPolicyContentTest and RecentBooksPagingTest; contract runner now has unique per-run temp directories. Final build log/checksums/VERIFICATION.json must postdate these edits. Real-device/security/sync-architecture gates above still apply; do not call the candidate production-ready.
