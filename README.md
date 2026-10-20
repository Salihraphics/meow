<div align="center">
  <img src="./meow-app_20261020_223635_0000.svg" alt="MEOW app icon" width="140" />  <h1>MEOW</h1>
  <p><strong>Study smarter. Learn from every mistake.</strong></p>  <p>A smarter way to study. A little more human, a little less complicated.</p>  <p>
    MEOW is an Android study companion designed to turn PDF question banks into an interactive, organized, and personal study experience.
  </p>  <p>
    Import your PDFs, solve questions, track your progress, and learn from your mistakes — all in one place.
  </p>  <p>
    <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform: Android" />
    <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Language: Kotlin" />
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="UI: Jetpack Compose" />
    <img src="https://img.shields.io/badge/Storage-Local--First-9FE870" alt="Storage: Local First" />
    <img src="https://img.shields.io/badge/Status-In%20Development-orange" alt="Status: In Development" />
  </p>
</div>---

✨ What is MEOW?

Studying shouldn't mean jumping between PDF readers, answer keys, timers, and separate mistake notebooks.

MEOW aims to bring the entire workflow together in one focused experience — from opening a question to reviewing what went wrong.

🐾 Features

- 📚 PDF Study Library — Import and organize your own question-bank PDFs.
- ✍️ Interactive Question Solving — Draw, write calculations, annotate diagrams, and select answers.
- 🎯 Answer Checking — Check answers against answer keys, with manual verification when necessary.
- 📓 Mistake Notebook — Keep incorrect and unanswered questions ready for another attempt.
- ⏱️ Study Tracking — Track results, completion time, and progress.
- 🧠 AI-Assisted Explanations — Optional AI support for understanding difficult questions.
- 💾 Automatic Saving — Preserve progress and resume study sessions.
- 🐈 A Study Companion With Personality — A curious black-cat mascot makes the experience feel welcoming without sacrificing usability.

«Note: MEOW is under development. The features above describe the intended product and may not yet be implemented.»

🧰 Built With

Technology| Purpose
Kotlin| Application development
Jetpack Compose| Native Android UI
Jetpack DataStore| On-device preferences
Android PdfRenderer| PDF viewing
Room| Local database (PDF library today; questions, tests and attempts planned)
On-device OCR| Text recognition — planned

The technical implementation may evolve as development progresses.

🎨 Design Philosophy

- Simple by design. Keep the interface focused and intuitive.
- Useful over complicated. Every feature should solve a real problem.
- Local-first where practical. Keep personal study data on-device whenever possible.
- Honest automation. Uncertain answers and extracted content should be reviewable.
- Progress without pressure. Help students improve without unnecessary distractions.
- Built to last. Prioritize reliability, maintainability, and thoughtful development.

🔒 Privacy

MEOW aims to keep personal study materials, annotations, and progress on-device wherever practical.

Future online and AI-powered features should clearly explain what information is shared with external services. API credentials and personal data must be handled carefully.

Today, MEOW makes no network requests. The optional onboarding answers (exam, subjects, level, session length and a name or nickname) are stored only on the device, and can be changed or erased at any time in Settings. No account is required.

🚧 Development Status

MEOW is an early-stage personal project built gradually with a long-term vision.

The goal is not to rush a release, but to create a reliable and genuinely useful study companion — one thoughtful improvement at a time.

What works today:

- First-launch welcome and an eight-screen onboarding flow (exam, subjects, level, session length, name), every question optional and skippable.
- Preferences saved on-device; onboarding is shown once, and can be revisited or reset from Settings.
- Home screen with the student's profile, and Settings for profile, theme (system / light / dark) and cat animations.
- The MEOW cat, drawn in code with nine moods and a reaction model ready for future study events. Ambient animation respects the in-app toggle and the system "remove animations" setting.
- PDF library: import PDFs with the system file picker (no storage permission), with checks for non-PDF, empty, password-protected and damaged files, and duplicate detection. Rename and remove; MEOW keeps its own copy, so removing never touches the original file.
- PDF viewer: real page rendering, vertical scrolling, pinch / double-tap / button zoom, page indicator, go-to-page, and resuming at the last-read page. On wide screens a side panel shows document info and navigation.
- Phone and tablet layouts from one codebase: two-pane onboarding on tablets and phones in landscape, two-column home and settings on wider screens, portrait and landscape.

No account is needed. Google sign-in is planned but not implemented; Settings says so rather than showing a button that does nothing. See [docs/ACCOUNTS_AND_SYNC.md](docs/ACCOUNTS_AND_SYNC.md).

Annotation, question solving, answer checking, the mistake notebook and statistics are not implemented yet; the app labels them "Yakında". See [docs/PDF_STUDY.md](docs/PDF_STUDY.md).

🛠️ Building

Requirements: JDK 17 or newer and the Android SDK (platform 35). Open the project in Android Studio, or point `local.properties` at your SDK (`sdk.dir=/path/to/android-sdk`) and run:

```
./gradlew assembleDebug          # build the app
./gradlew testDebugUnitTest      # unit, Robolectric UI and screenshot tests (JVM only, no emulator)
./gradlew recordRoborazziDebug   # render every screen to app/build/outputs/roborazzi/
```

To see onboarding again during development, use Settings → "Karşılama ekranlarını yeniden göster", or clear the app data (`adb shell pm clear com.salihraphics.meow`).

📄 License

MEOW is currently published without an open-source license. All rights are reserved by the copyright holder.

You may view the source code on GitHub, but no general permission is granted to reuse, modify, redistribute, or incorporate it into other projects.

For permission requests, collaboration, or commercial licensing inquiries, please contact the repository owner.

Third-party dependencies, libraries, and assets remain subject to their respective licenses and terms.

---

<div align="center">
  <strong>MEOW 🐈‍⬛</strong>
  <p><em>One question at a time. One mistake at a time. One step forward.</em></p>
</div>
