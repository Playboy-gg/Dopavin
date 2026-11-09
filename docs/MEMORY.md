# MEMORY — Dopavin (प्रोजेक्ट की याददाश्त)

इस फ़ाइल में वह सब है जो अगली बार काम शुरू करते समय याद रहना चाहिए। हर सत्र के बाद अपडेट करें।

## प्रोजेक्ट एक नज़र में
- Dopavin: Android म्यूज़िक प्लेयर (Kotlin 1.9.24, Compose 1.7.5, Compose compiler 1.5.14, Media3 1.3.1, NewPipeExtractor 0.26.5)।
- लगभग 39,000 पंक्तियाँ Kotlin। कोई Room/Hilt/Coil/Navigation नहीं — सब कुछ हाथ से लिखा है।
- बिल्ड मुख्यतः फ़ोन पर CodeAssist से होता है; CI केवल `assembleDebug` बनाता है। यानी **जो APK चलता है वह अक्सर debug APK है**।
- Kotlin 2.x पर न जाएँ (README की चेतावनी: CodeAssist अलग Compose compiler plugin resolve नहीं कर पाता)।
- Liquid-glass UI, Reduce Motion, 48dp targets, सारे strings `strings*.xml` में — ये नियम तोड़ना नहीं है।

## लैग सुधार (6 नवंबर 2026) — क्या मिला और क्या बदला
लक्ष्य: कोई फ़ीचर या लुक कम किए बिना लैग हटाना।

| # | कारण | जगह | सुधार |
|---|------|-----|-------|
| 1 | हर UI state क्लास में `List<...>` है जिसे Compose "unstable" मानता है। strong skipping बंद थी, इसलिए कहीं भी बदलाव होने पर पूरी स्क्रीनें दोबारा बनती थीं (MusicApp सीधे 29 StateFlow सुनता है) | `app/build.gradle` | `experimentalStrongSkipping=true` |
| 2 | डाउनलोड हर 32 KB पढ़ने पर progress भेजता था (तेज़ नेट पर सैकड़ों बार प्रति सेकंड), हर बार पूरा pipeline और root recompose | `DownloadCenter.kt` | progress अधिकतम हर 200 ms और कम से कम 1% बदलने पर |
| 3 | Home में पूरी लाइब्रेरी की sort LazyColumn के अंदर, main thread पर, हर play/pause पर | `HomeScreen.kt` | `remember(visible.songs)` |
| 4 | इमेज लोडर: Dispatchers.IO पर बेहिसाब समानांतर लोड, कोई disk cache नहीं, छोटा memory cache, हर बार 404 वाला maxres अनुरोध | `ArtworkLoader.kt` | 4 की सीमा (Semaphore), disk cache (64 MB, cacheDir), memory cache heap का 1/8, maxres 404 याद रखना |
| 5 | debug APK `debuggable=true` था, जो Compose/ART को काफ़ी धीमा करता है | `app/build.gradle` | debug अब non-debuggable। ब्रेकपॉइंट चाहिए तो `-PdopavinDebuggable=true` |

## जानबूझकर नहीं बदला
- Glass की गुणवत्ता/blur/lens: पहले से स्केल और record-budget के साथ ट्यून्ड है; लुक बदलना "compromise" होता।
- `minifyEnabled`: R8 के लिए NewPipeExtractor के keep rules चाहिए, जिन्हें बिना डिवाइस पर टेस्ट किए जोड़ना जोखिम है।
- Baseline profile: सही प्रोफ़ाइल केवल असली डिवाइस पर Macrobenchmark से बनती है।

## खुले अवलोकन (छोटे, अभी ठीक नहीं किए)
- `PlayerConnection.refresh` हर timeline बदलाव पर पूरी queue (अधिकतम 2000) दोबारा map करता है।
- `ArtworkLoader.missing` में अस्थायी नेटवर्क विफलता भी हमेशा के लिए दर्ज हो जाती है (ऐप दोबारा खुलने तक कवर नहीं आता)।
- `LyricsSheet` में `focusEntry` हर फ़्रेम पर O(n) खोज करता है (लाइनें कम होने से अभी सस्ता है)।
- `MusicViewModel.kt` 100 KB से बड़ा है; आगे चलकर बाँटना बेहतर होगा।
- `rememberArtworkTint` MainActivity और MiniPlayer दोनों में चलता है (cache से सस्ता, पर दोहरा)।

## सत्यापन की स्थिति
- पहले बिल्ड में `ArtworkLoader.kt` पर त्रुटि आई थी (`MAX_PARALLEL_LOADS must be initialized`, const को object के ऊपर ले जाकर ठीक किया)। उसके बाद का बिल्ड अभी दोबारा चलाना बाकी है।
- शुरुआती काम static विश्लेषण था। इस वातावरण में Android SDK नहीं था, इसलिए **बिल्ड और डिवाइस टेस्ट नहीं हुए**। देखें `docs/PHASES.md`, चरण 3।

## UI सुधार (6 नवंबर 2026, स्क्रीनशॉट से)
- Now Playing कवर के नीचे आयताकार धब्बा: कवर के नीचे का fade पारदर्शी था और platform shadow उसमें से झाँकती थी। **अंतिम निर्णय (उपयोगकर्ता का):** Now Playing का कवर पूरा रहे, नीचे fade/blur नहीं। इसलिए मुख्य कवर, पड़ोसी कवर और उड़ने वाले कवर (`ArtworkFlight`) से `meltIntoPage` हटा दिया और सामान्य गोल `.shadow()` लौटा दिया। (बीच में एक गलत कोशिश थी जिसमें `Modifier.shadow` की डिफ़ॉल्ट `clip=true` ने कवर 72% पर काट दिया था; सीख: कस्टम shape वाली shadow कभी कवर की सामग्री न काटे।) `meltIntoPage` अब केवल `CoverHero` (artist/album/playlist पेज) में है।
- Queue में drag की pill के चारों ओर चौकोर किनारा: `graphicsLayer` की `shadowElevation` की डिफ़ॉल्ट shape आयत है। glass वाली pill पर वह हटाई (glass अपनी गोल shadow बनाता है), बिना glass वाली पर गोल shape दी।
- Queue drag नीचे/ऊपर पहुँचने पर: पहले कोई सीमा या auto-scroll नहीं था। अब pill queue के पहले-आखिरी slot और दिखने वाले हिस्से में रहती है, किनारे पर पहुँचने पर सूची अपने-आप scroll होती है (गति किनारे से दूरी के अनुसार), और पकड़ी हुई पंक्ति `LocalPinnableContainer` से pin रहती है ताकि scroll में drag न टूटे।

## Queue generation सुधार (6 नवंबर 2026)
समस्याएँ: queue में कभी कम कभी ज़्यादा गाने, कभी artwork गायब, कभी downloaded गाने मिल जाना, एक ही गाना दो बार।

| समस्या | जड़ | सुधार |
|--------|-----|-------|
| artwork/अवधि गायब | queue का lookup `songsById` केवल library + इस session की online memory से था। streamed गाना या restart के बाद restore हुई queue में वह नहीं मिलता। memory की saved प्रति भी HashMap के किसी भी 300 गाने रखती थी | `QueueItem` में `artworkUri` और `durationMs` (MediaMetadata से) जोड़े; पंक्ति और पड़ोसी-कवर इन्हीं से fallback लेते हैं। `toMediaItem` अब अवधि भी भरता है। metadata cache अब LinkedHashMap है और सबसे नए 300 रखता है |
| queue की लंबाई अनियमित | autoplay 8 माँगता था पर repeat/artist-सीमा/खाली related सूची से 0-8 मिलते थे; 25 s cooldown | लक्ष्य: अगले ~10 गाने। trigger पर बचे गानों के हिसाब से `need` (कम से कम 5) माँगा जाता है; कम मिले तो दूसरा चरण: seed का YouTube radio (30) + एक-कलाकार सीमा 2 से 4। सफल top-up के बाद अगला 5 s में, खाली नतीजे पर पूरा cooldown |
| downloaded गाने मिल जाना | autoplay में "fit" वाले library गाने (वही कलाकार/पसंदीदा/liked) हमेशा जुड़ते थे; radio में उसी कलाकार के सारे downloaded गाने; mood में top/liked कलाकारों के | अब ये तभी जुड़ते हैं जब streamed से कुछ नहीं मिला (ऑफ़लाइन या online playback बंद)। Personal Mix जानबूझकर वैसा ही रखा (वह पसंद पर आधारित है) |
| वही गाना दोबारा | अलग uploads की लंबाई में 4 s से ज़्यादा फ़र्क़ होने पर अलग माने जाते थे | `SmartQueue` में सहनशीलता 10 s |

ध्यान: गाना manually जोड़ने (`addToQueue`) या दोबारा playlist खोलने पर दोहराव रोकने का कोई नियम नहीं है; वह उपयोगकर्ता की अपनी क्रिया मानी गई है।

## Lyrics खोज और प्राथमिकता (6 नवंबर 2026)
आदेश: **1. Word synced, 2. Line synced, 3. Plain text** (`lyricsRank` in `LyricsRace.kt`: 3/2/1)।

| समस्या | जड़ | सुधार |
|--------|-----|-------|
| line-synced पहले आ जाए तो वही जीतता था | `raceLyrics` केवल plain को upgrade करता था; पहला synced (line भी) तुरंत जीत | अब line-synced तभी रुकता है जब कोई word-capable provider बाकी न हो; वरना उसकी प्रतीक्षा (अधिकतम `wordGraceMs`=6 s, दौड़ की शुरुआत से)। word-synced आते ही जीतता है |
| बाद की queries का फ़ायदा नहीं | `LyricsFallback` पहली जीत पर रुक जाता था (कई बार raw title से सिर्फ़ plain/line मिलता, जबकि साफ़ किए title से word-synced मिलता) | कम प्राथमिकता की जीत के बाद अगली `UPGRADE_QUERIES`=2 queries केवल उन providers से जो बेहतर दे सकते हैं (अधिकतम 8 s, हर चरण 4 s)। word मिलते ही समाप्त |
| LRCLIB plain पर रुक जाता था | exact `get` या पहली `search` में सिर्फ़ plain मिलने पर तुरंत लौट आता था | plain को रोककर रखता है और दूसरी lookups में synced खोजता है |
| पुराना कमज़ोर नतीजा 90 दिन तक cache में | सभी प्रकार का TTL 90 दिन | word 90 दिन, line 14 दिन, plain 3 दिन; समय पूरा होने पर दोबारा खोज, और नई खोज बदतर निकले या विफल हो तो पुराना नतीजा बना रहता है (`readStale`) |

नए tests: `LyricsRaceTest` (4), `LyricsFallbackTest` (3)।
ध्यान: providers की `capabilities` (`LyricsProviders.capabilities`) ही तय करती हैं कि कौन word-synced दे सकता है; किसी provider की क्षमता बदले तो वहीं सुधारें।

## v1.2 की तैयारी (7 नवंबर 2026)
- आधार: उपयोगकर्ता की भेजी `Dopavin-lagfix-5.zip`, `CHANGELOG.md` और `RELEASE_NOTES_v1_1.md`।
- `app/build.gradle`: versionCode 2 → 3, versionName 1.1 → 1.2।
- `CHANGELOG.md`: उपयोगकर्ता की फ़ाइल आधार; "Unreleased (after 1.1)" अब "1.2 (versionCode 3) - in progress", और ऊपर के सारे सुधार (performance, player/queue, lyrics) जोड़े।
  (पहले प्रोजेक्ट की CHANGELOG में "gradient-free UI", "notification में Repeat/Close", "Trending 15 गाने" और "कवर नीचे fade" वाले अनुच्छेद थे जो उपयोगकर्ता की फ़ाइल में नहीं हैं; आधार वही फ़ाइल रखी, इन्हें दोबारा नहीं जोड़ा। "कवर नीचे fade" अब गलत भी है।)
- release notes: `docs/releases/RELEASE_NOTES_v1_1.md` (इतिहास) और `docs/releases/RELEASE_NOTES_v1_2.md` (मसौदा, अब तक के सुधार)।
- README अभी भी v1.1 दिखाता है (बैज और Status); release के समय बदलना है।
- `.gitignore` में keystore फ़ाइलें जोड़ी गईं।

## Lyrics: title सफ़ाई का दोष (7 नवंबर 2026) — v1.2 की पहली त्रुटि
लक्षण: title की सफ़ाई के बाद भी कई गानों के लिरिक्स नहीं आते थे।

| जड़ | असर | सुधार |
|-----|-----|-------|
| `LyricsQueryPlan.build` का dedupe `LyricsMatcher.normalise` से होता था, जो brackets हटा देता है | "Song (Official Video)" (raw) और "Song" (सफ़ाई वाला) एक ही query गिने जाते थे, इसलिए "सफ़ाई वाला + कलाकार" query कभी भेजा ही नहीं गया; सिर्फ़ noisy text और एक सख़्त (strict) बिना-कलाकार query जाती थी | dedupe अब असली text (छोटे अक्षर + spacing) से |
| `normalise` में `\p{M}` नहीं था | देवनागरी/गुरमुखी की मात्राएँ अलग हो जाती थीं, शब्द टूट जाते थे (कलाकार का मिलान भी) | `[^\p{L}\p{M}\p{N}]+` |
| कलाकार की जगह label/channel ("T-Series", "Brown Town Music") और गायक title के अंत में | provider का title "California Love" है, हमारा "CALIFORNIA LOVE Cheema Y": न कोई query मिलती, न सख़्त सीमा (0.75) पार होती | label-जैसे uploader पर title के आख़िरी 2/1 शब्द कलाकार मानकर और हटाकर (strict) भी queries (`looksLikeLabel`)। साधारण कलाकार वाले गानों पर ये अतिरिक्त queries नहीं बनतीं |
| पुराने "नहीं मिला" cache | ग़लत plan के नतीजे 7 दिन तक रुके रहते | `FALLBACK_GEN` 1 → 2 |
| UI में "(HTTP 503)" जैसा तकनीकी पाठ | `LyricsSheet` असफलता पर `errorDetail` भी दिखाता था | अब केवल सरल वाक्य; विवरण `Log.w("Lyrics", ...)` में |

सत्यापन: Kotlin 1.9.24 कंपाइलर (GitHub से) और एक छोटे JUnit-जैसे runner से `LyricsQueryPlanTest` और `TrackNamesTest` चलाए: 29 में से 29 पास (6 नए)। बाकी tests (coroutines/Android पर निर्भर) यहाँ नहीं चले।
ध्यान: नीचे का पूरा `assembleDebug` बिल्ड अभी आपकी तरफ़ से बाकी है।

## Crash: ForegroundServiceStartNotAllowedException (7 नवंबर 2026)
रिपोर्ट: Xiaomi 22041219PI, Android 14, v1.2, thread main; `MediaNotificationManager.startForeground` से `PlaybackService` शुरू नहीं हो सका।
- **जड़:** `PlaybackService.deviceCallback` ("headphones दोबारा जुड़ने पर चलाओ", `resumeOnConnect`) ऐप के background में `player.play()` बुलाता है। Android 12+ तब foreground service शुरू करने नहीं देता। notification का कवर बाद में लोड होता है और उसका callback `startForegroundService` बुलाता है, और यह रास्ता Media3 में बिना `try/catch` है।
- **सत्यापन (स्रोत से):** Media3 1.3.1, 1.4.1, 1.5.1, 1.6.1 और 1.7.1 की `MediaNotificationManager.startForeground` में कोई बचाव नहीं; `MediaSessionService.onUpdateNotificationInternal` केवल सीधे रास्ते पर exception पकड़ता है। इसलिए संस्करण बढ़ाने से यह ठीक नहीं होता।
- **सुधार:** `SafeNotificationProvider` (`PlaybackNotification.kt`) डिफ़ॉल्ट provider को लपेटता है; बाद के हर notification-update का callback `try` में चलता है। अस्वीकृति पर सामान्य notification दिखता है और playback चलता रहता है। `PlaybackService` में `setMediaNotificationProvider(...)` और `setListener(...)` (केवल log)।
- **सीमा:** ऐसे में गाना foreground service के बिना चलता है; Android कुछ देर बाद उसे रोक सकता है। असली foreground दर्जा background से माँगना मुमकिन नहीं।
- **जाँच बाकी:** `resumeOnConnect` चालू करके Bluetooth बंद करें (गाना रुके), ऐप background में रखें, फिर Bluetooth जोड़ें: crash न हो और notification दिखे।
- यह कोड मैं यहाँ compile नहीं कर सका (Media3 की jar उपलब्ध नहीं); API नाम Media3 1.3.1 के स्रोत से मिलाए गए हैं।

## Home: देश/भाषा अपने-आप + पहली बार का स्वागत-कार्ड (7 नवंबर 2026)
**शर्त (उपयोगकर्ता की):** ऐप उपयोगकर्ता से न देश पूछेगा, न भाषा। सब फ़ोन के संकेतों और व्यवहार से।

समस्या: (1) Home की 6 में से 3 श्रेणियाँ (Punjabi, Bollywood, Romantic Hindi) सबके लिए तय थीं; (2) `NewPipe.init(HttpDownloader())` का डिफ़ॉल्ट en-GB/GB है (v0.26.5 के स्रोत से पक्का), इसलिए "top songs this week" हर किसी के लिए ब्रिटेन के थे; विदेश और दक्षिण भारत वालों को ग़लत सामग्री।

| हिस्सा | फ़ाइल | क्या करता है |
|--------|-------|--------------|
| पहचान | `RegionDetector.kt` (Android), `RegionProfile.kt` (शुद्ध) | देश: SIM → नेटवर्क → फ़ोन का region; भाषाएँ: फ़ोन की भाषा-सूची। कोई अनुमति नहीं |
| NewPipe | `OnlineRepository.init` | `Localization("en", देश)` और `ContentCountry(देश)`; भाषा जानबूझकर अंग्रेज़ी (extractor का पाठ-विश्लेषण सबसे भरोसेमंद) |
| श्रेणियाँ | `HomeCategories.plan` | Charts पहले, Lofi आख़िर, बीच में अधिकतम 4। फ़ोन/सुनी हुई भाषा हो तो वही आगे (अंग्रेज़ी सीधे "स्पष्ट" भाषा नहीं गिनी जाती) और देश की डिफ़ॉल्ट भाषाएँ नहीं जुड़तीं; कुछ स्पष्ट न हो तो देश की डिफ़ॉल्ट (भारत: पंजाबी, हिंदी, अंग्रेज़ी = पहले जैसे 6 tiles; अमेरिका: Charts, English, Lofi) |
| सीखना | `ScriptLanguages` | पिछले 60 सुने गानों के नाम की लिपि (देवनागरी, गुरमुखी, तमिल, तेलुगु...) से भाषा; कम से कम 3 गाने पर भाषा "सीखी" मानी जाती है |
| Cache | `HomeCache.resetFor(key)` | देश/भाषा बदलने पर पुराना Home cache हटता है |
| स्वागत-कार्ड | `WelcomeStore`, `HomeScreen.WelcomeArtistsCard`, `MusicViewModel.finishWelcome` | इतिहास खाली हो तो Home पर "Start with your favourite artists": स्थानीय charts के कलाकार (≥6 हों तब), चुनना/Skip; चुने नाम `fetchHome` में कलाकार-पट्टियों के बीज बनते हैं; "Reset personalization" से कार्ड फिर लौट सकता है |

ईमानदार सीमाएँ:
- फ़ोन का देश पसंद नहीं बताता (विदेश में बैठा भारतीय पहले दिन उस देश के charts देखेगा); असली सुधार व्यवहार से सीखना और निजी पट्टियाँ (radio, related, "More from") हैं।
- दक्षिण भारत में देश भी IN है; अंग्रेज़ी वाले फ़ोन पर शुरू में पंजाबी/हिंदी tiles ही दिखेंगे, जब तक फ़ोन की भाषा-सूची में दक्षिणी भाषा न हो या सुनने से लिपि न दिखे। अधिकतर गानों के नाम अंग्रेज़ी अक्षरों में होते हैं, इसलिए लिपि वाला संकेत हर किसी पर काम नहीं करेगा।
- YouTube Music देश संकेत (`gl`) को कितना मानता है, यह यहाँ से जाँचा नहीं जा सका; फ़ोन पर देखें।
- सत्यापन: `RegionProfileTest` (12 tests) Kotlin 1.9.24 कंपाइलर से यहाँ चलाए, सब पास। Android/Compose वाला हिस्सा (HomeScreen, ViewModel, WelcomeStore, RegionDetector, OnlineRepository) यहाँ compile नहीं हो सका।

## Onboarding और Contributors (8 नवंबर 2026)
उपयोगकर्ता ने कहा: onboarding पूरा करो (पहले केवल Home पर एक कार्ड था) और Settings में Contributors बनाओ जहाँ से GitHub प्रोफ़ाइल खुले।

**Onboarding** (`ui/screens/OnboardingScreen.kt`, `MusicApp` के root पर overlay):
- पन्ने: 0 स्वागत (लोगो), 1 खोजें/चलाएँ, 2 डाउनलोड/ऑफ़लाइन, 3 लिरिक्स, 4 "Start with your favourite artists" (केवल तब जब स्थानीय charts से ≥6 कलाकार लोड हो गए हों; वरना पन्ना 3 पर ही "Start listening")।
- हर पन्ने पर Skip, पिछला पन्ना (Back), पन्ने के बिंदु (TalkBack में "Page x of y"), Crossfade (Reduce Motion में अवधि 0)। देश/भाषा नहीं पूछी जाती (उपयोगकर्ता की शर्त)।
- कौन देखेगा: `!welcomeDone && history खाली` (`_onboarding` VM में, शुरू में ही `historyStore` से तय, ताकि अपडेट करने वाले पुराने उपयोगकर्ता को झलक भी न दिखे)। पूरा होने पर `finishWelcome(picked)`; "Reset personalization" के बाद onboarding दोबारा नहीं आता।
- overlay पूरे ऐप पर स्पर्श रोकता है (`pointerInput { detectTapGestures {} }`), वरना नीचे का Home छू जाता।
- पुराना Home कार्ड (`WelcomeArtistsCard`) हटा दिया; `HomeUi.welcomeArtists` अब onboarding को कलाकार देता है।
- Notification की अनुमति पहले जैसी: पहला गाना चलने पर (MainActivity), onboarding में नहीं।

**Contributors** (`data/Contributors.kt`, `ui/screens/ContributorsPage.kt`, `SettingsScreen` में नई `SettingsCategory.Contributors`, "App" समूह में Updates और About के बीच):
- सूची `Contributors.people` में; नया नाम जोड़ने के लिए एक पंक्ति (`Contributor(name, githubLogin, role)`)। अभी केवल रिपॉज़िटरी का मालिक `playboy-gg` है (अन्य नाम मुझे पता नहीं थे, गढ़े नहीं)।
- नाम पर टैप: `https://github.com/<login>` ब्राउज़र में (`openUrl`, About वाला साझा)। login की जाँच (`Contributors.profileUrl`): अवैध नाम कभी नहीं खुलता।
- पन्ने पर दो और पंक्तियाँ: "All contributors on GitHub" (`/graphs/contributors`, जो अपने-आप सबको दिखाता है) और "Want to contribute?" (रिपॉज़िटरी)।
- कोई तस्वीर डाउनलोड नहीं होती (नाम का पहला अक्षर); ऐप कुछ fetch नहीं करता, इसलिए PRIVACY.md में बदलाव नहीं चाहिए।
- Settings की खोज में भी शामिल।

सत्यापन: `ContributorsTest` (4) यहाँ चलाया, पास। Compose वाला हिस्सा (Onboarding, ContributorsPage, Settings, MusicApp) यहाँ compile नहीं हो सका।

बिल्ड सुधार: `OnboardingScreen.kt` में `ArtworkSize` का import `com.dopavin.ui.components` से था; सही `com.dopavin.data.ArtworkSize` है (दो त्रुटियाँ)।

## Contributors: तस्वीर और नया सदस्य (9 नवंबर 2026)
- **नया सदस्य:** `Sandeepbedia` (UI/UX designer) `Contributors.people` में; नई भूमिका `ContributorRole.Designer` (string `contributor_role_designer`)। नाम में फ़िलहाल login ही दिखता है (असली नाम मुझे पता नहीं; चाहें तो `Contributor(name = ...)` में बदलें)।
- **GitHub profile picture:** `Contributors.avatarUrl(login, px)` → `https://github.com/<login>.png?size=..` (login की जाँच के बाद)। `ContributorsPage.Avatar` इसे `ArtworkLoader` से लाता है (disk cache सहित); लोड होने तक या ऑफ़लाइन होने पर नाम का पहला अक्षर। तस्वीर केवल पन्ना खुले रहने पर डाउनलोड होती है।
- **निजता:** पहले मैंने जानबूझकर तस्वीर नहीं रखी थी (ऐप कुछ fetch नहीं करता था); अब `PRIVACY.md` में GitHub वाली नई पंक्ति और Effective तारीख 2026-11-09। इसी बहाने एक छूटी बात भी जोड़ी: NewPipe को दिया जाने वाला दो अक्षर का देश-कोड (`gl`), जो अब YouTube Music को जाता है।
- **`ArtworkLoader` सुधार:** नेटवर्क की गड़बड़ी (IOException, web तस्वीर) पर तस्वीर अब पूरे session के लिए "missing" नहीं ठहरती; 60 सेकंड बाद दोबारा कोशिश (`retryAfter`)। (यह पुरानी खुली कमी थी।)
- सत्यापन: `ContributorsTest` में नए 3 tests; सभी शुद्ध tests मिलाकर 48 पास।

## Update dialog (9 नवंबर 2026)
समस्या: अपडेट आने पर ऐप में dialog नहीं आता था। कोड में जानबूझकर लिखा था "No dialog: a quiet message ..." यानी सिर्फ़ एक छोटा toast ("A new version is available. See Settings → Updates") जो आसानी से छूट जाता है।

पहले से मौजूद: `UpdateCheck` (GitHub से जाँच), `UpdateInstaller` (डाउनलोड, signature जाँच, install), Settings → Updates पन्ना (`AvailableBlock`), `autoUpdateCheck` (दिन में अधिकतम दो बार, `UPDATE_CHECK_INTERVAL_MS`=12 घंटे)।

नया: `ui/screens/UpdateDialog.kt` (मौजूदा `GlassDialog` से, ताकि बाकी dialogs जैसा दिखे)।
- कब: **केवल स्वचालित जाँच** में नया release मिलने पर (`runUpdateCheck(manual=false)`) और उस release का tag "skip" न किया गया हो। हाथ से की गई जाँच (Settings → Updates) पर नहीं (वहाँ पन्ना सब दिखा देता है)। onboarding के ऊपर नहीं।
- सामग्री: संस्करण, installed संस्करण, आकार, release notes (`UpdateCheck.plainNotes`), त्रुटि (यदि डाउनलोड विफल)।
- बटन: एक मुख्य बटन चरण के अनुसार बदलता है (Update now → "Downloading… %" (बंद) → Install update; असफल पर Try again; APK न हो तो Open release page)। "Later": `dismissUpdateDialog()` (अगली स्वचालित जाँच तक छुपा); "Skip this version": `skipUpdateVersion()` → prefs `update_check`/`skipped_tag` में tag; नया release फिर पूछेगा। Skip केवल डाउनलोड शुरू होने से पहले दिखता है।
- पुराना toast (`update_available_toast`) हटाया।
- स्थिति: `MusicViewModel._updateDialog` / `updateDialog`; `MusicActions` में `dismissUpdateDialog()`, `skipUpdateVersion()`।
- सीमा: जाँच दिन में दो बार तक ही होती है, इसलिए dialog भी उतनी ही बार आ सकता है। "Check automatically" बंद हो तो dialog नहीं आता (Settings में बंद करने का विकल्प पहले जैसा)।
- Compose वाला हिस्सा यहाँ compile नहीं हुआ।

बिल्ड सुधार (assembleRelease): `UpdatesPage.sizeText` को `internal` करने से `ExtrasScreens.kt` के private `sizeText` से टकराव हुआ (6 त्रुटियाँ)। `UpdatesPage` का फ़ंक्शन फिर `private` है; `UpdateDialog` का अपना `updateSizeText` है। सीख: एक ही package (`ui.screens`) में सहायक फ़ंक्शन का नाम दोहराना नहीं; नया नाम रखने से पहले `grep -rn "fun <नाम>("` से जाँचें। नई घोषणाओं के नाम जाँच लिए गए हैं, और टकराव नहीं मिला।

.gitignore: `keystore.properties`, `*.jks`, `*.keystore` पहले से सूची में थे (मेरा पहले का दावा कि वे नहीं थे गलत था, मैंने फ़ाइल का केवल शुरुआती हिस्सा देखा था); मेरा जोड़ा हुआ दोहराव हटा दिया। push से पहले: `git status` में `dopavin-release.jks` और `keystore.properties` दिखने नहीं चाहिए।

## v1.2 release की तैयारी (9 नवंबर 2026)
उपयोगकर्ता के कहने पर: README, CHANGELOG और सब कुछ v1.2 के लिए अद्यतन।
- `CHANGELOG.md`: शीर्षक "1.2 (versionCode 3)" ("in progress" हटा)। 1.1 वाली प्रविष्टि में तारीख नहीं है, इसलिए यहाँ भी नहीं।
- `docs/releases/RELEASE_NOTES_v1_2.md`: अंतिम रूप, 1.1 जैसे ढाँचे में (Home, New here, Updates, Player and queue, Lyrics, Smoother and steadier, About the project)। इसे GitHub Release के विवरण में चिपकाना है: ऐप का Update dialog उसी पाठ को "What's new" में दिखाएगा (`UpdateCheck.plainNotes`), इसलिए इसमें लिंक या जटिल markdown नहीं रखा।
- `README.md`: version बैज 1.1 → 1.2 और Status; Playback (queue का auto-scroll, autoplay का नया बर्ताव), Discover (देश के charts, फ़ोन की भाषा, onboarding), Lyrics (प्राथमिकता), Personalisation (Update dialog, Contributors), Privacy (देश-कोड और Contributors की तस्वीरें; `INTERNET` की पंक्ति), Credits में "Contributors" सूची।
- `CONTRIBUTING.md`: "Being listed as a contributor" खंड (नया नाम कहाँ जोड़ना है)।
- `app/build.gradle`: versionCode 3, versionName 1.2 (पहले ही हो चुका था)।
- `PRIVACY.md`: Effective 2026-11-09 (पहले ही हो चुका था)।
- README में जानबूझकर "Gradient-free surfaces everywhere" वाली पंक्ति नहीं छुई (मुझे इसकी सत्यता का कोड-प्रमाण नहीं मिला था: कवर के placeholder में अब भी gradient-brush दिखा था)। उपयोगकर्ता खुद जाँच ले।

**push/release से पहले अब भी बाकी (उपयोगकर्ता के हाथ में):**
1. `./gradlew testDebugUnitTest assembleDebug assembleRelease` तीनों हरे।
2. `docs/PHASES.md` की फ़ोन-जाँचें (कम से कम onboarding, update dialog, Home की श्रेणियाँ, लिरिक्स, headphones-crash)।
3. branch पर push → PR → CI हरा → merge; `dopavin-release.jks` / `keystore.properties` कभी नहीं।
4. tag `v1.2` पर GitHub Release, signed APK `Dopavin-1.2.apk` और RELEASE_NOTES_v1_2.md का पाठ विवरण में।
