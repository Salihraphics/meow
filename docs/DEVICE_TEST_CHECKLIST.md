# Device test checklist: answer marking and keys

These flows have JVM tests (Robolectric), but Android's PDF renderer, ML Kit OCR, the pen on a
real screen and real books can only be checked on a device. Use a book with an answer key (the
"55 Günde TYT Matematik VDK 2024" PDF that showed the bugs) and, if possible, a scanned PDF.

Install: `adb install -r MEOW-…-arm64.apk` (or the universal APK). Existing library, drawings and
keys must still be there after updating.

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

## 5. Your data after updating
- [ ] Update over the previous version (`adb install -r`, do not uninstall): the library, last
      pages, drawings, marked answers, confirmed keys and corrections are all still there.

## 6. Nothing else broke
- [ ] Reading, scrolling, zooming, go-to-page, and last-read page work as before.
- [ ] Pen, eraser, colours, undo/redo work on pages with and without questions.
- [ ] Tablet (or phone in landscape): the side panel, the answer section and dialogs fit.
- [ ] Phone: the answer strip under the top bar ("Soru N: X işaretlendi · Geri al", "Testi kontrol
      et") pushes the pages down and covers nothing.

Report anything unexpected with the page number and a screenshot.
