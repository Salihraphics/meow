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
- [ ] Uncertain answers are red-outlined; conflicts block "Onayla" until resolved.
- [ ] "Onayla", then "Yeniden ara": the confirmed tests don't change.
- [ ] A scanned PDF: the search takes longer (OCR) but the app stays responsive; cancel by
      removing the PDF from the library.

## 4. Nothing else broke
- [ ] Reading, scrolling, zooming, go-to-page, and last-read page work as before.
- [ ] Pen, eraser, colours, undo/redo work on pages with and without questions.
- [ ] Tablet (or phone in landscape): the side panel, the answer bar and dialogs fit.

Report anything unexpected with the page number and a screenshot.
