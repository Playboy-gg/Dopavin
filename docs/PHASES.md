# PHASES — लैग सुधार की योजना और स्थिति

## चरण 1 — गहरा विश्लेषण ✅ पूरा
- बिल्ड कॉन्फ़िग, CI, manifest, ViewModel के flows, PlayerConnection, पोज़िशन polling, glass रेंडरिंग, इमेज लोडर, डाउनलोड इंजन, मुख्य स्क्रीनों की जाँच।
- नतीजा: `docs/MEMORY.md` की तालिका।

## चरण 2 — सुधार लागू करना ✅ पूरा (कोड में)
- [x] strong skipping चालू
- [x] डाउनलोड progress throttle
- [x] Home की sort `remember` में
- [x] ArtworkLoader: gate + disk cache + बड़ा memory cache + maxres 404 याद
- [x] debug build non-debuggable (टॉगल के साथ)

## चरण 3 — बिल्ड और डिवाइस पर सत्यापन ⏳ बाकी (आपके हाथ में)
1. `./gradlew testDebugUnitTest` (25 टेस्ट क्लास)
2. `./gradlew assembleDebug`
   - अगर `experimentalStrongSkipping` पर कंपाइलर ऐतराज़ करे, तो `app/build.gradle` की उस `freeCompilerArgs` पंक्ति को हटाकर बाकी चार सुधार रखें।
3. फ़ोन पर जाँचें:
   - Home पर तेज़ scroll, फिर Search, फिर Library
   - कई गाने एक साथ डाउनलोड करते हुए UI का चलना ("Download all")
   - Now Playing खोलना/बंद करना और mini player खींचना
   - Lyrics स्क्रीन पर word-by-word highlight
   - ऐप बंद करके दोबारा खोलने पर कवर तेज़ी से आना (disk cache)
4. असली माप: Developer options → Profile HWUI rendering या `adb shell dumpsys gfxinfo com.dopavin` (jank %)।

## चरण 4 — वैकल्पिक, माप के बाद ⏸
- Macrobenchmark से Baseline Profile बनाना।
- R8 (`minifyEnabled true`) के साथ NewPipeExtractor के keep rules।
- `PlayerConnection` की queue-rebuild घटाना; `missing` को केवल स्थायी विफलता तक सीमित करना।
- `MusicViewModel` को बाँटना।

## चरण 5 — UI सुधारों की फ़ोन पर जाँच ⏳ बाकी
- Now Playing कवर पूरा दिखे (कटा हुआ नहीं, नीचे fade/blur नहीं) और उसके चारों ओर नरम गोल shadow हो।
- कवर को स्वाइप करके अगला/पिछला गाना देखें, और mini player से खोलना/बंद करना (उड़ने वाला कवर)।
- Queue में पंक्ति को देर तक दबाकर खींचें: चारों ओर चौकोर किनारा न हो; नीचे/ऊपर किनारे पर सूची अपने-आप scroll हो; pill title/artist वाले हिस्से तक न जाए; छोड़ने पर सही जगह बैठे।
- लंबी queue (20+ गाने) में पहली पंक्ति को आखिर तक खींचकर देखें।

## चरण 6 — Queue generation की फ़ोन पर जाँच ⏳ बाकी
- कोई streamed गाना चलाएँ, autoplay चालू रखें: queue में लगातार लगभग 10 अगले गाने रहें (4 से कम न बचें)।
- queue खोलें: हर पंक्ति में artwork और अवधि हो, restart के बाद भी।
- autoplay से जुड़े गानों में आपके downloaded गाने न मिलें (ऑफ़लाइन मोड छोड़कर)।
- Now Playing में कवर स्वाइप करें: पड़ोसी streamed गाने का कवर खाली न दिखे।
- एक ही गाना दो बार न दिखे।
- `./gradlew testDebugUnitTest` दोबारा चलाएँ (SmartQueue के tests)।

## चरण 7 — Lyrics प्राथमिकता की जाँच ⏳ बाकी
- `./gradlew testDebugUnitTest` (LyricsRaceTest, LyricsFallbackTest, LyricsProvidersTest)।
- ऐसा गाना खोलें जिसका word-synced मौजूद हो (Better/LrcRed/Portato/LyricsPlus): शब्द-दर-शब्द highlight दिखे, भले LRCLIB पहले जवाब दे।
- केवल line-synced वाला गाना: अधिकतम ~6 s में line-synced दिखे।
- केवल plain वाला गाना: plain दिखे, पर पहले synced खोजा जाए।
- लिरिक्स स्क्रीन पर provider बदलकर देखें; अगर देरी ज़्यादा लगे तो `RaceConfig.wordGraceMs` (6 s) घटाएँ।

## v1.2 — नई सुविधाओं की सूची ⏳ उपयोगकर्ता के चुनाव का इंतज़ार
सुझाव (उपयोगकर्ता चुनेगा):
1. Lyrics का transliteration (गुरमुखी/देवनागरी/Roman), ऑफ़लाइन नियम-आधारित।
2. Queue में पहले से मौजूद गाना जोड़ने पर चेतावनी (दोहराव की सुरक्षा)।
3. सेटिंग: autoplay में downloaded गाने मिलाना (डिफ़ॉल्ट बंद)।
4. Artwork की अस्थायी विफलता पर अपने-आप दोबारा कोशिश (`ArtworkLoader.missing` की कमी)।
5. प्रदर्शन का दूसरा दौर: Baseline Profile, queue rebuild घटाना (फ़ोन पर माप के बाद)।
6. Release की तैयारी: README संस्करण/Status, CI में signed release APK (GitHub secrets से)।
चुनाव के बाद हर चुनी सुविधा का अलग चरण यहाँ जोड़ा जाएगा।

## v1.2 — पहली त्रुटि: Lyrics title सफ़ाई ✅ कोड में पूरी, फ़ोन पर जाँच बाकी
- [x] raw और सफ़ाई वाला query अलग-अलग भेजे जाते हैं
- [x] label-uploader पर title के अंत का गायक
- [x] देवनागरी/गुरमुखी की मात्राएँ
- [x] UI में तकनीकी error नहीं
- [x] पुराना "नहीं मिला" cache अमान्य
- [ ] फ़ोन पर: "(Official Video)" वाले और label-uploader वाले गानों में लिरिक्स आएँ; Hindi/Punjabi लिपि के title; बंद इंटरनेट पर सरल संदेश

## v1.2 — crash: foreground service ✅ कोड में, फ़ोन पर जाँच बाकी
- [x] `SafeNotificationProvider` और listener
- [ ] फ़ोन पर: सेटिंग में "headphones जुड़ने पर चलाओ" चालू; गाना चलाएँ; Bluetooth बंद करें; ऐप background में; Bluetooth जोड़ें → crash नहीं, notification दिखे, गाना चले
- [ ] `./gradlew assembleDebug` में कोई compile त्रुटि न हो (नया `PlaybackNotification.kt`)

## v1.2 — Home: देश/भाषा और स्वागत-कार्ड ✅ कोड में, फ़ोन पर जाँच बाकी
- [x] `RegionProfile` + 12 tests; `RegionDetector`; NewPipe को देश
- [x] श्रेणियाँ फ़ोन/सुनने के अनुसार; नई Regional कला; `cat_regional`
- [x] Home cache देश बदलने पर रीसेट
- [x] स्वागत-कार्ड (`WelcomeStore`), चुने कलाकार = शुरुआती बीज, reset पर दोबारा
- [ ] फ़ोन पर: Trending/Charts आपके देश के आएँ; फ़ोन की भाषा तमिल/तेलुगु आदि करके Home की tiles देखें; सिम बदलकर या विमान मोड में भी Home चले
- [ ] फ़ोन पर: नया इंस्टॉल (या Settings → reset personalization): कार्ड दिखे, 3 कलाकार चुनकर Done → "More from ..." पट्टियाँ आएँ; Skip पर कार्ड गायब और दोबारा न आए
- [ ] `./gradlew assembleDebug` और `testDebugUnitTest`

## v1.2 — Onboarding और Contributors ✅ कोड में, फ़ोन पर जाँच बाकी
- [x] `OnboardingScreen` (5 पन्ने), VM में `onboarding`, MusicApp में overlay, Home का पुराना कार्ड हटाया
- [x] `Contributors` डेटा + 4 tests, `ContributorsPage`, Settings में नई श्रेणी
- [ ] फ़ोन पर: नया इंस्टॉल → onboarding दिखे; Skip, Next, Back, बिंदु; कलाकार चुनकर Done → Home पर "More from ..." पट्टियाँ; ऐप दोबारा खोलने पर onboarding न आए
- [ ] फ़ोन पर: पुराने संस्करण से अपडेट (history सहित) → onboarding बिल्कुल न दिखे
- [ ] फ़ोन पर: onboarding के ऊपर से नीचे का Home छुआ न जा सके; Reduce Motion चालू हो तो बिना animation
- [ ] फ़ोन पर: Settings → Contributors → नाम पर टैप → GitHub प्रोफ़ाइल खुले; "All contributors" और "Want to contribute?" भी
- [ ] `./gradlew assembleDebug` में कोई त्रुटि न हो (नई फ़ाइलें: OnboardingScreen, ContributorsPage, Contributors)
- [ ] Contributors में और लोगों के GitHub नाम `Contributors.people` में जोड़ना (केवल मालिक अभी)

## v1.2 — Contributors: तस्वीर और Sandeepbedia ✅ कोड में, फ़ोन पर जाँच बाकी
- [x] `avatarUrl`, `ContributorRole.Designer`, नया सदस्य, `Avatar` (ArtworkLoader), PRIVACY.md, ArtworkLoader का retry
- [ ] फ़ोन पर: Settings → Contributors: दोनों की GitHub तस्वीर दिखे; इंटरनेट बंद हो तो पहला अक्षर; नाम पर टैप → प्रोफ़ाइल खुले
- [ ] फ़ोन पर: इंटरनेट बंद करके पन्ना खोलें, फिर चालू करके दोबारा खोलें (≈1 मिनट बाद) → तस्वीर आ जाए
- [ ] `./gradlew assembleDebug` में कोई त्रुटि न हो (`ContributorsPage` में नए imports: Bitmap, Uri, Image, produceState...)

## v1.2 — Update dialog ✅ कोड में, फ़ोन पर जाँच बाकी
- [x] `UpdateDialog`, VM की `updateDialog` स्थिति, skip-version, पुराना toast हटाया
- [ ] फ़ोन पर: GitHub पर नया release (v1.2 से ऊँचा tag) रखकर, ऐप खोलें: dialog आए; notes और आकार दिखें
- [ ] "Update now" → प्रतिशत दिखे → "Install update" → Android का installer खुले
- [ ] "Later" → dialog हटे; "Skip this version" → उसी release पर फिर न आए; और नया release आए तो फिर आए
- [ ] Settings → Updates से हाथ से जाँच → dialog न खुले; "Check automatically" बंद हो तो dialog न आए
- [ ] `./gradlew assembleDebug` में कोई त्रुटि न हो

## v1.2 — release दस्तावेज़ ✅ पूरे
- [x] CHANGELOG (1.2), RELEASE_NOTES_v1_2, README (बैज, Status, फ़ीचर, Privacy, Contributors), CONTRIBUTING
- [ ] (आपका) बिल्ड + tests हरे → फ़ोन-जाँचें → branch/PR/CI → merge → tag v1.2 और GitHub Release (APK + notes)
