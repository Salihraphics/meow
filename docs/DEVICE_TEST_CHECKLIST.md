# Device test checklist: viewer, stylus, answer marking, keys, dashboard and library

These flows have JVM tests (Robolectric), but Android's PDF renderer, ML Kit OCR, the pen on a
real screen and real books can only be checked on a device. Use a book with an answer key (the
"55 Günde TYT Matematik VDK 2024" PDF that showed the bugs) and, if possible, a scanned PDF.

Install: `adb install -r MEOW-…-arm64.apk` (or the universal APK). Existing library, drawings and
keys must still be there after updating.

## 0. Scrolling, zoom and stylus (this update; test first)
Use a long PDF (300+ pages) and one with landscape and portrait pages mixed.
- [ ] Slow and fast scrolling, flings, stopping a fling with a tap: the page never jumps to
      another page; no shaking while scrolling.
- [ ] Two quick taps to stop scrolling don't zoom. A deliberate double-tap zooms to 200%
      around the tap, and back.
- [ ] Pinch in and out at different places (top, bottom, near a page break, at 50%): the
      spot between the fingers stays under them; no jump or jolt when the fingers lift.
- [ ] Two fingers moving together pan the page; zoom then scroll; scroll sideways at 200%+.
- [ ] Hold the tablet with a thumb resting on the screen edge and scroll with the other hand:
      it scrolls and never zooms.
- [ ] Rotate the device and rotate the page (90°) while zoomed; close and reopen the PDF: same
      place, ink and notes on the same printed spots.
- [ ] Stylus: write several lines with the palm resting on the screen: every stroke appears,
      the page doesn't move, the palm leaves no mark. Note whether the device reports pressure
      (lines thicker when pressed): record the stylus model either way.
- [ ] After the stylus has written once, one finger scrolls (even with the pen tool). "Parmakla
      çiz" on: the finger writes again. A two-finger pinch while writing leaves no ink.
- [ ] Stylus eraser end (if it has one) erases with the pen tool selected.
- [ ] Diagonal lines and circles look smooth at 100% and 300%; ink appears right under the tip.
- [ ] "Şekil düzelt" on: a shaky straight line, a circle, an oval, a rectangle, a triangle, a
      one-stroke arrow become clean shapes on the same spot; normal handwriting (letters, "o",
      "e", loops), ticks and scribbles stay as written. Undo brings back the hand-drawn stroke,
      undo again removes it. Off: everything stays freehand.
- [ ] Write on a sticky note with the stylus, with and without shape correction.

## 1. Answers go to the right question (the reported bug)
- [ ] Open a page with 4 questions (two columns). Choose the pen. Circle **C in question 4**.
      Expect "Soru 4: C işaretlendi", not "Soru 1".
- [ ] On the same page circle an option in questions 1, 2 and 3; each note names its own question.
- [ ] Circle question 1 again with a different option; the later one counts (check in step 2).
- [ ] Zoom to 200%, pan, circle an option; the question is still right. Rotate the device to
      landscape and back; the circles stay on their options and nothing is lost.
- [ ] Turn the page view with "Sağa döndür" and circle an option: the question is still right.
- [ ] Draw a big loop, a line of working, or a note: nothing is recorded, it stays a drawing.
- [ ] Circle two options at once: MEOW asks "hangi şıkkı işaretledin?". Choose one; "Cevap değil"
      leaves it a drawing.
- [ ] If a page's question numbers aren't text (pictures), MEOW asks "Bu işaret hangi soru için?"
      instead of guessing. Note which pages do this.

## 2. Deferred checking
- [ ] While circling, correctness is never shown; the bar says "N cevap işaretlendi".
- [ ] Erase one circle (eraser) or undo it: the drawing goes, the answer stays (count unchanged).
- [ ] Right after a circle, "Geri al" on the "Soru N: X işaretlendi" note removes that answer.
- [ ] Tap "N cevap işaretlendi": "Cevaplarım" lists the answers; "Sil" clears one.
- [ ] Leave the viewer and come back (or close the app): the count is still there.
- [ ] Tap "Testi kontrol et": correct / wrong / blank counts match the book's key; mistakes list
      "Senin cevabın … Doğrusu …"; "Sayfaya git" jumps to the page. The bar disappears.
- [ ] With an unconfirmed key, checking says to confirm the key first and keeps the answers.
- [ ] If asked which test the pages belong to, pick it; checking then completes.
- [ ] Settings → "Cevabı hemen göster" on: each circle shows right/wrong immediately. Turn it off again.

## 3. Finding the key
- [ ] After import the library card shows "Cevap anahtarı aranıyor…", then a result.
- [ ] For the 55 Günde book: after updating, MEOW searches it again by itself (the card shows
      "Cevap anahtarı aranıyor…"; this version reads tables differently). Does it find the key? If not, open "Tanılama raporu" and Share/Copy it to the developer, with the
      PDF page numbers of the key and a screenshot of one key page.
- [ ] "Anahtar hangi sayfalarda?" → enter the key's pages → "Oku". Note how many tests were found
      and whether letters are right ("Kaynağı gör" shows the page next to the answers).
- [ ] "Ayrı anahtar PDF'i ekle" with a separate key PDF, if you have one.
- [ ] Books with "CEVAP: X" under questions, answers in "Çözümler" pages, or a filled optical
      form: answers are found, and tapping one shows where it was read ("Bu cevabın bulunduğu
      yerler"). Where two sources disagree the answer is red and "Onayla" waits for your choice.
- [ ] The PDF with "CEVAP: X" on its solution pages: after updating, MEOW searches it again by
      itself. Are the answers found, each under the right question and test? Tap two or three
      and check "Bu cevabın bulunduğu yerler" points at the right solution. If not, share the
      "Tanılama raporu" (its "soru numarasız" count says whether labels were seen but not placed)
      with the page numbers of one solution page.
- [ ] A page holding the solutions of two tests: the answers below the second test's header go
      to that test, not to the first.
- [ ] A scanned key longer than 12 pages, or scanned pages with a printed running header: all
      the key's tests are found (the search takes longer).
- [ ] A blank "YANITLAR" optical form is not taken as a key.
- [ ] Uncertain answers are red-outlined; conflicts block "Onayla" until resolved.
- [ ] "Onayla", then "Yeniden ara": the confirmed tests don't change.
- [ ] Without confirming, correct one answer, then "Yeniden ara": your correction is still there.
- [ ] Read a key with "Anahtar hangi sayfalarda?", then "Yeniden ara": that key is still there,
      and not twice.
- [ ] A scanned PDF: the search takes longer (OCR) but the app stays responsive; cancel by
      removing the PDF from the library.

## 4. Study workspace
- [ ] Tablet (and phone in landscape): the PDF fills the left side; **no toolbar, chip or card
      appears over the PDF or along its bottom**, at 50 %, 100 % and 400 % and in every rotation.
- [ ] Tablet: every tool is in the right panel: Gezin, Kalem, Fosforlu kalem, Silgi, Geri al,
      Yinele, pen colours and widths, highlighter colours and widths, Not ekle, the timer with the
      cat, page / zoom / rotation controls. The panel scrolls when the screen is short.
- [ ] Phone portrait: the tool rail at the right edge works one-handed; "Çalışma paneli" opens
      the drawer with timer, notes, zoom and rotation; back or the scrim closes it.
- [ ] Zoom: − goes down to 50 % (two pages visible), + up to 400 %; pinch and double-tap still work.
- [ ] Rotation: "Sağa döndür" / "Sola döndür" turn pages; "Orijinal yön" resets. Ink, highlights
      and notes stay on the same printed words after turning, zooming, scrolling to another page
      and coming back. Leave the PDF and reopen it: same turn, everything still aligned.
- [ ] Draw and highlight on a turned page, then reset the turn: the marks are where you made them.
- [ ] Highlighter: yellow and green, three widths; the text stays readable under it. The eraser
      removes it; undo/redo work. Highlighting an option never records an answer.
- [ ] Notes: "Not ekle" places an empty note straight away (no dialog). With the pen (or a
      stylus) write on it: the writing stays on the note. Move it with ✥ and resize it from the
      corner: the writing moves and scales with it. "Aa" adds typed text on the same note.
      The eraser on the note erases its writing; × deletes the note; "Geri al" brings it back.
- [ ] Writing on a note never draws on the page under it and never records an answer, even over
      an option. Reopen the PDF: notes in the same place with the same writing and text.
- [ ] With a stylus: pen and highlighter draw; a palm resting on the screen (note what happens).
- [ ] Timer: Başlat, Duraklat, Devam et, Sıfırla; − / + change the length (25 by default).
      The countdown keeps correct time with the screen off and with the app in the background
      for a few minutes. Let a short (5 min) session finish while the app is open: it says
      Tamamlandı and the cat celebrates; "Bugün … çalıştın · 1 oturum tamamlandı".
- [ ] Start the timer, swipe MEOW away from recents (or force-stop), reopen: the timer is paused
      at about where you left it, not completed, and nothing extra was counted.
- [ ] End a session early after a few minutes: a gentle message with the minutes. The cat says
      different things on repeated taps, falls asleep after a few idle minutes, and wakes on tap.

## 5. Fullscreen, screen timeout, timer buttons
- [ ] Open a PDF on a phone and on a tablet, in portrait and landscape: no status bar (clock,
      icons) and no navigation bar; the top bar and the panel reach the edges with no empty strip.
      On a phone with a camera notch, nothing important sits under the notch.
- [ ] Swipe from the top or bottom edge: the bars appear for a moment over the app, then hide by
      themselves. Back works (gesture or the briefly shown back button).
- [ ] Bars stay hidden after: opening and closing "Sayfaya git", a note's text editor (with the
      keyboard), the answer dialogs, the phone's study panel drawer; turning the device; pulling
      down the notification shade; leaving the app and coming back; closing and reopening the PDF.
- [ ] Leave the PDF (back to the library, the answer key, settings): the bars are back as normal.
- [ ] Set the device's screen timeout to 30 s. In a PDF, read without touching for 2–3 minutes:
      the screen stays on (while reading, after writing a note, and with the timer running).
      Back in the library, the screen turns off after 30 s again. The system setting is unchanged.
- [ ] Timer −/+: a tap changes 5 minutes. Hold +: after a short moment it keeps going, a little
      faster, and stops at 180 dk; hold −: stops at 5 dk. Releasing stops at once, with no extra
      step. Sliding the finger off the button while holding also stops it. TalkBack: "Süreyi 5
      dakika uzat/kısalt" are buttons and a double tap changes 5 minutes.

## 6. Look and feel (light and dark, phone and tablet)
- [ ] Settings → Tema: switch Sistem / Açık / Koyu; every screen follows at once (home, library,
      PDF workspace, answer key, dialogs).
- [ ] Dark: secondary text, card edges, switch and field borders are easy to see; dialogs stand
      apart from the screen behind them; nothing is pure white glare.
- [ ] Light: cream background, off-white cards with a thin edge, no washed-out grey text.
- [ ] Workspace panel: the selected tool is a filled tile in both themes; under pen or
      highlighter, "Renk" and "Kalınlık" are labelled; a black pen's thickness dots are visible
      in dark mode. Cards scroll smoothly; nothing is clipped on a short landscape tablet.
- [ ] The cat looks like the app icon's cat at every size (home, panel, empty library, review).
      Tap it: it reacts; leave it a few minutes: it falls asleep; tap: it wakes with half-open
      eyes. The speech bubble appears above it without pushing the timer.
- [ ] Timer: digits don't jitter while counting; the main button changes immediately on start,
      pause and reset.
- [ ] Notes: on a small note, the handles sit on the corners without hiding the writing; the
      selected note is clearly outlined.
- [ ] Larger text (Android font size at maximum): labels wrap instead of being cut off in the
      panel, dialogs and home.

## 6a. Home dashboard
- [ ] Tablet: "Çalışmaya devam et", pinned and recent visible without scrolling; the
      countdown top-right; the cat is a small avatar beside the greeting (tap: it reacts).
- [ ] Fresh install: Home shows "İlk PDF'ini ekle", "Sınav hedefi" and "Henüz kayıtlı çalışma
      yok"; no numbers that you didn't create.
- [ ] "PDF ekle" (card or quick action) opens the library and the system picker straight away;
      backing out of the picker leaves you in the library.
- [ ] After reading a PDF to some page and going back: "Çalışmaya devam et" shows that PDF and
      "Sayfa N / M"; "Devam et" opens it on that page.
- [ ] Pin a PDF in the library: it appears under "Sabitlenenler" on Home. Open two others: they
      appear under "Son açılanlar" with folder and "Bugün".
- [ ] Write a typed note and a pen-only note: both appear under "Notlarım" ("El yazısı not");
      tapping one opens that PDF at the note's page. An empty note you never wrote on is not
      listed.
- [ ] Run the timer to the end once and reset one early: "Bu hafta" shows the time and
      "Oturum 2 · 1 tamamlandı". Check a test: "Kontrol edilen test 1" with its counts.
- [ ] Tablet landscape and portrait, phone portrait and landscape: nothing clipped, countdown
      in the top corner on tablets.

## 6b. Exam countdown
- [ ] "Hedef belirle" → YKS → Kaydet: TYT is counted down in the 2×2 grid, the seconds tick,
      "Tahmini tarih" is shown, AYT/YDT is listed below.
- [ ] Change TYT's date and time ("Tarihi değiştir"): the chip turns to "Senin girdiğin tarih";
      the day picked is the day shown (check once with the phone in a time zone west of UTC).
      "Tahmine dön" restores 19 Haziran 2027 10:15.
- [ ] Switch to LGS, then "Hedef yok": the card goes back to "Sınav hedefi". The same dialog is in
      Ayarlar → "Sınav hedefi".
- [ ] Turn "Geri sayımı ana sayfada göster" off: Home shows "Geri sayım kapalı · Aç".
- [ ] Set a date today a few minutes ahead: "Sınav bugün" and the countdown reaches 00 at the
      start time, then shows the completed state (YKS: moves on to AYT/YDT). Never a negative
      number.
- [ ] Leave the app on Home for a few minutes, switch to another app and back, lock and unlock:
      the countdown is right again at once (it never drifts).
- [ ] Change the phone's time zone or date: the countdown follows the new clock.

## 6c. Library organisation
- [ ] Create folders, including one with the same name in other letter case ("fizik" when
      "Fizik" exists): refused with "Bu adda bir klasör zaten var."
- [ ] Move a PDF into a folder and back to "Klasörsüz" from its ⋮ menu; "Yeni klasör" inside
      the move dialog creates and moves in one step.
- [ ] Rename a folder; delete a folder with PDFs: the dialog says they move to Klasörsüz;
      "Vazgeç" changes nothing; deleting keeps every PDF, with its drawings and answers.
- [ ] Search "ozet" finds "ÖZET…"; sort by Ad / Eklenme / Son açılan, close and reopen the app:
      the sort is remembered. Pinned PDFs stay on top.
- [ ] Empty folder, no search results, no pinned: each shows its own short message.

## 7. Your data after updating
- [ ] Update over the previous version (`adb install -r`, do not uninstall): the library, last
      pages, drawings, marked answers, confirmed keys and corrections are all still there.
- [ ] After this update every PDF is in "Klasörsüz", nothing is pinned, sticky notes and
      handwriting on them are intact, and no PDF was duplicated (Android app info → storage roughly
      unchanged).

## 8. Nothing else broke
- [ ] Reading, scrolling, zooming, go-to-page, and last-read page work as before.
- [ ] Pen, eraser, colours, undo/redo work on pages with and without questions.
- [ ] Tablet (or phone in landscape): the side panel, the answer section and dialogs fit.
- [ ] Phone: the answer strip under the top bar ("Soru N: X işaretlendi · Geri al", "Testi kontrol
      et") pushes the pages down and covers nothing.

Report anything unexpected with the page number and a screenshot.
