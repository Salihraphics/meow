# PDF study workflow

## What exists

**Import** (`data/library`)
- The system document picker (`ACTION_OPEN_DOCUMENT`, `application/pdf`); no storage permission.
- The picked file is streamed into `files/library/incoming/`, hashed (SHA-256), and checked:
  empty → header (`%PDF-`) → already in library (same hash) → `PdfRenderer` can open it
  (password-protected and damaged files are told apart). Only then is it moved to
  `files/library/documents/<uuid>.pdf`. Failures and cancellation delete the temporary file;
  leftovers from a killed process are cleared at startup.
- MEOW keeps its own copy instead of a persisted URI grant: grants can be lost when a provider
  changes, and a copy means removing an entry can never affect the student's original file. The
  cost is storage space, shown in the viewer's document info.
- Metadata is in Room (`pdf_documents`): UUID id, display name, original file name, size, page
  count, content hash, import time, last opened, last page, `updatedAt`, `deletedAt` tombstone.

**Viewer** (`feature/viewer`)
- `PdfRenderer` behind `PdfRenderSession`, confined to a single thread (the renderer is not
  thread-safe). The session is opened once per screen in the ViewModel and closed in `onCleared`.
- Pages are a `LazyColumn`; only pages near the screen are rendered. Bitmaps are rendered at
  display width (rounded to 128px steps, capped at 2048px) and kept in a byte-bounded LRU cache
  (1/8 of the heap, at most 96 MB).
- Zoom 100–400%: pinch (previewed with a layer scale, then re-rendered sharp), double-tap, and
  +/− buttons. Above roughly 2048px of page width, pages are upscaled rather than re-rendered;
  tiled rendering would fix that if it matters in practice.
- The reading position is saved a moment after scrolling stops, and on leaving the screen.

## Annotation

**Storage** (`data/annotation`, Room table `annotation_strokes`, database v2 by auto-migration)
- One row per stroke: UUID, document, page, tool, colour (ARGB), width (fraction of page width),
  points (little-endian float32 x,y pairs as page fractions 0..1), `createdAt`, `updatedAt`,
  `deletedAt`. The imported PDF file is never touched.
- Erasing, undo and redo only set or clear `deletedAt`; nothing is rewritten. Removing a document
  from the library tombstones its strokes too.
- Writes go through one ordered queue on the application scope (`LocalAnnotationRepository`), so
  a stroke drawn just before leaving the viewer or backgrounding the app is still saved, and an
  undo can never overtake the write it undoes. Reading a page waits for pending writes.

**Session** (`feature/annotation/AnnotationSession`, owned by the viewer ViewModel)
- Strokes per page, loaded the first time a page appears; tool, colour and width; chronological
  undo/redo for the open document, capped at 100 steps. Undo or redo on a page that is off screen
  scrolls to it. Kept across rotation because the ViewModel is.

**Drawing** (`PageAnnotationLayer`, in each page's overlay slot)
- Touch points are converted to page fractions and clamped to the page, so a stroke belongs to
  the page it started on. Points closer than 0.15% of the page width are skipped.
- Strokes are drawn scaled to the page's current on-screen size, so zoom, rotation and the phone /
  tablet layouts need no special handling.
- The eraser removes whole strokes within 14dp of the finger, measured on screen so it feels the
  same at every zoom.
- Input: with Gezin (navigate) selected, the viewer behaves exactly as before. With the pen or
  eraser, one finger or a stylus draws; two fingers pan and zoom. Which pointers draw is decided
  in `DrawingInputPolicy`, the place to add stylus-only drawing and palm rejection.

**Next for annotation**: highlighter, partial (pixel) erasing, pressure-sensitive width, and
stylus-only mode.

**Question-level annotations** will need no data change: a question is a page plus a rectangle
in the same page fractions, and its annotations are the page's strokes inside that rectangle.

## Answer keys

Everything runs on the device. No PDF, page or answer leaves it.

**Finding the key** (`analysis/`, run by `AnswerKeyAnalysisScheduler` after import and at app start)
- One document at a time, in the background, cancelled if the document is removed. Results are
  stored per content hash and analyzer version, so a document is read once; "Yeniden ara" forces
  a new pass. An interrupted run (app killed) is picked up again on the next start.
- Text first: PdfBox-Android reads each page's words with positions (page fractions, top-left
  origin, page rotation applied). The last pages (the last quarter, at least 8) are read first,
  because that's where books put their keys; reading stops promptly on cancellation.
- OCR only when needed: pages with fewer than 25 characters of text count as scanned. If no key
  section was found in the text and such pages exist, ML Kit's bundled Latin recogniser reads up
  to 12 of the last scanned pages in full, then (if still nothing) only the top and bottom 14% of
  up to 400 scanned pages, where per-page answer strips are printed.
- The parser (`AnswerKeyParser`, plain Kotlin) finds:
  - key sections: a heading ("Cevap Anahtarı", "Cevaplar", "Yanıtlar", "Test Cevapları",
    "Doğru Cevaplar", "Answer Key"…), pages that continue one, or pages dense with numbered
    answers;
  - answers as "1. D", "1-D", "1) D", "1:D", "1/D", "1D" and grids (a row of numbers over a row of
    letters); one or two columns;
  - test headers: "TEST 5", "5. Test", "Deneme Sınavı 2", "Konu Tarama 3", "Ünite 1",
    "Alıştırma"… Turkish letters are folded (İ/ı, Ş, Ğ, Ü, Ö, Ç), so OCR that drops the dots still
    matches. A restart at question 1 without a header starts a new, unnamed test;
  - header/footer strips like "5.B 6.D 7.A" (at least two answers on the line).
- OCR noise: with an explicit separator, 8→B, 0/O/Q→D and l/I→1 are accepted, but the answer is
  marked "Emin değilim" (LOW confidence) for the student to check. Without a separator nothing is
  guessed. Answers are never invented: a question with no readable answer is listed as missing.
- Per test it reports missing questions, the same question with two different answers (both
  kept, marked as a conflict), too few answers, and out-of-order numbering.
- Question pages: "TEST N" headers near the top of ordinary pages give each test its page range,
  used later to know which test a marked question belongs to.

**Storage** (Room, database v3 by auto-migration from v2, so existing libraries and strokes are
kept): `document_analysis`, `key_tests`, `key_answers`, `answer_attempts`. Each answer records
its type (choice, number, text), origin (detected / manual), confidence, note and source page.
Every test records which document its answers came from (`sourceDocumentId`): today always the
same PDF; a key in a separate PDF can be linked later without a schema change.

**Review** (`feature/answerkey`, from the library card's key status or the viewer's ✓ button)
- Each test shows its answers as chips: found, "Emin değilim" (OCR guess or conflict), "Elle
  girildi" (typed by the student) and, after confirming, "Onaylı".
- Tap an answer to change it (choice A–E, a number with an optional tolerance, or written text),
  or delete it; add missing answers, add or rename tests and set their question pages.
- "Onayla" confirms the document's tests; it refuses while a conflict is unresolved. Only
  confirmed tests are used for grading. A new analysis never replaces confirmed tests.
- If nothing was found the screen says so plainly and offers manual entry.

## Answer checking (multiple choice)

- With a pen, the student circles an option on the PDF. When the stroke ends, the viewer finds
  the question region and option labels on that page (`QuestionLayoutDetector`, from the PDF
  text, or OCR for a scanned page), then `ChoiceRecognizer` decides:
  - a closed loop around exactly one option label → that option;
  - around several labels, around the value next to a label, or a tick touching a label → MEOW
    asks "Soru N: hangi şıkkı işaretledin?" with every option (the likely ones first) and "Cevap
    değil";
  - anything else (notes, working out, big drawings) is just a drawing.
- `AnswerChecker` picks the test from the page ranges and grades against the confirmed key. If the
  key isn't confirmed, the page belongs to no known test, or the question has no answer, MEOW says
  so and offers to open the key; it never grades against an unconfirmed key.
- The cat reacts (correct / incorrect / unsure), and each graded answer is saved as an attempt
  (question, choice, result, whether it was recognised or chosen by the student, the stroke and
  its region). The stroke itself stays an ordinary annotation; the PDF is never modified.
- Everything is in page fractions, so zoom, rotation and screen size don't change the result.
- Numeric and written answers can be stored and graded (`AnswerGrader`: comma decimals, fractions,
  tolerance; written answers are matched exactly after normalising, otherwise "check it yourself").
  Reading handwritten numbers or text from the page is not implemented, and nothing claims to
  grade open-ended answers.

**Known limits**
- Option labels must be printed as "A)" / "A." and question numbers as "12." / "12)" at the start
  of a line. Other layouts produce no regions, and circling simply stays a drawing.
- Erasing or undoing a graded circle keeps its attempt.

**What the JVM tests cannot cover**: Android's `PdfRenderer` and ML Kit OCR do not run under
Robolectric. The text pipeline is tested on real PDFs generated in the tests (PdfBox, Turkish
font, including rotated pages and two columns); OCR is tested through the `PageOcr` interface
with fake readers. Real scanned books and OCR accuracy need checking on a device.

## Next

- Question-by-question solving with timer and results (correct / wrong / blank / time), feeding
  the mistake notebook.
- Keys in a separate PDF, linked to a book.
