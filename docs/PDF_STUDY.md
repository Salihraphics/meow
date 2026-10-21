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
  a new pass, and a new analyzer version re-reads books whose key isn't confirmed. A new pass
  replaces only what earlier passes detected: confirmed tests, typed tests, tests with answers
  the student corrected, and tests read from pages the student pointed at (remembered in the
  analysis clues) are kept, and detections repeating them aren't added. An interrupted run (app
  killed) is picked up again on the next start.
- Text first: PdfBox-Android reads each page's words with positions (page fractions of the crop
  box, top-left origin, page rotation applied, the same space the pen draws in). The last pages
  (the last quarter, at least 8) are read first, because that's where books put their keys.
- Text that can't be trusted counts as no text: pages under 25 characters (scans), and pages
  whose text is unreadable, e.g. fonts without a character map that give symbols or consonant
  soup (`AnswerKeyAnalyzer.isReadable`).
- OCR in stages: first up to 12 pages: pages titled "Cevap Anahtarı" whose answers weren't text
  (letters drawn as shapes or images; only while no key was found in the text), scanned pages
  next to a key found in text, and the last scanned pages. While a scanned page gives answers,
  the scanned pages next to it are read too, so a long scanned key is followed to its ends (at
  most 60 pages in all). Then, only if nothing at all was found, the last text pages. If still
  nothing, the top and bottom 14% of scanned pages (up to 400) for per-page answer strips. A
  mixed PDF (digital questions, scanned key) only OCRs its scans. A page whose only text is a
  short running header or page number (top and bottom 12%, under 200 characters, no answers)
  counts as scanned; its text is kept beside the OCR. Pages whose text already gave answers are
  never OCR'd.
- Each page is read two ways and the one giving more answers is kept: column by column as
  extracted, and as whole printed rows (`KeyTableReader.visualRows`). Table cells spaced wider
  than a word gap otherwise arrive as separate "lines" and the table is never seen; this was one
  of the reproduced causes of "text read on every page, no key found".
- Answer tables are read by position (`KeyTableReader`): letters are matched to the question
  number printed above them, tables side by side are split where numbering restarts and named by
  the headers above them, and tests printed as columns ("TEST 1 TEST 2 TEST 3" over rows of
  "1. C A E") become one test per column. Answers printed without spaces ("1.C2.A3.D", "1C2A3D")
  are split again.
- One discovery pipeline with independent finders whose results are combined (`AnswerKeyParser`,
  plain Kotlin; `combine`). Every answer keeps its provenance (finder, page, value). The same test
  found by several finders (same section and number or name, or an unnamed one whose question
  pages overlap a single named test) becomes one test: agreeing readings merge, disagreeing ones
  become a conflict listing each value and where it was read, for the student to settle.
  Nothing is ever inferred from the question content. The finders:
  - **Labelled answers** next to questions: "CEVAP: A", "Cevap C", "Doğru cevap: D", "Doğru
    seçenek E", "Yanıt: B" (capital letter required), read on whole printed rows, so a letter
    printed apart from "CEVAP:" still counts. Each goes to the nearest question number above it
    on the same page, in its column when the page has question numbers in two columns. Numbers
    are taken at the column's left edge (also in the top band, and fused to text: "1.Verilen"),
    increasing within a run, so numbered steps inside a solution don't count. A test header on
    the page ("TEST 2") starts a new run and names the answers below it. A label with no question
    number above it is not used (counted in the report). Pages the student points at are read
    for labels too.
  - **Worked solutions**: pages under a "Çözümler" / "… ÇÖZÜMLERİ" heading (and the pages that go
    on with labelled answers). Their explicit answers are read the same way; the test comes from
    the solutions heading ("TEST 3 ÇÖZÜMLERİ") or a test header above the answer, never from the
    question pages before the solutions (unnamed solutions stay unnamed, for the student to
    assign); solution pages are never counted as the test's question pages.
  - **Optical answer sheets**: rows of "n A B C D E" (from text or OCR) give where each bubble is;
    the rendered page image (`RenderedPageImages`, on the device) tells which is filled. One
    clearly darker bubble = the answer, marked "optik formdan okundu" for review; a blank form
    ("YANITLAR" with nothing filled) gives nothing, two marks give nothing. A row of identical
    letters under a row of numbers (a horizontal blank form) is not read as answers.
  - **Page margins** are the page's own key only when their numbers match questions printed on
    that page; otherwise (answers to another section) they keep a printed test name or stay
    unnamed, with no question pages, for the student to assign.
- Answer lines may skip a few numbers (a missing or image-printed answer) or repeat one: gaps are
  reported as missing questions and repeats as conflicts, never filled in.
- Key sections and tables (the original finders):
  - key sections: a heading ("Cevap Anahtarı", "Cevaplar", "Yanıtlar", "Test Cevapları",
    "Doğru Cevaplar", "Answer Key"…), pages that continue one, pages dense with numbered answers,
    including one-answer-per-line tables;
  - answers as "1. D", "1-D", "1) D", "1:D", "1/D", "1D", "1.(D)", "1.Ⓓ" and grids (a row of numbers
    over a row of letters, rows optionally labelled "Soru" / "Cevap"); one or two columns;
  - answer tables among the questions (typically closing each test), wherever they are;
  - days and units that group tests: "1. GÜN", "GÜN 12", "2. Ünite", "Bölüm 3". Books like
    "55 Günde…" repeat test numbers every day, so a key test is "1. GÜN · TEST 2" and is matched
    only to the pages under that day's "TEST 2" header. A day with no tests under it is one test;
  - test headers: "TEST 5", "5. Test", "Deneme Sınavı 2", "Konu Tarama 3", "Pekiştirme Testi",
    "Alıştırma"… Turkish letters are folded (İ/ı, Ş, Ğ, Ü, Ö, Ç), so OCR that drops the dots still
    matches. A restart at question 1 or 2 without a header starts a new, unnamed test, also in
    the middle of a line ("1.A 2.B 3.C 1.D 2.E"). A test's identity includes its kind, so
    "Deneme 1" and "Test 1" are different tests;
  - header/footer strips like "5.B 6.D 7.A" (at least two answers on the line).
- OCR noise: with an explicit separator, 8→B, 0/O/Q→D and l/I→1 are accepted, but the answer is
  marked "Emin değilim" (LOW confidence) for the student to check. Without a separator nothing is
  guessed. Answers are never invented: a question with no readable answer is listed as missing.
- Per test it reports missing questions, the same question with two different answers (both
  kept, marked as a conflict), too few answers, and out-of-order numbering.
- What the search saw is kept (`AnalysisClues`), and a short report per document
  (`AnalysisReports`, `files/keyreports`): per stage counts, then up to 15 pages that looked most
  like a key (and every page with a key heading) with their headings, test and day headers,
  answer lines, table rows, "1 A B C D E" bubble rows (answers marked by shapes, which text can't
  carry), and why near-miss lines were turned down. Excerpts are short and only of key-like
  lines. The review screen shows it under "Tanılama raporu", with Copy and Share; it never leaves
  the device otherwise.

**When the search finds nothing** the review screen says why in plain words ("Neye baktım?") and
offers, in this order:
1. "Anahtar hangi sayfalarda?": the student gives a page range; those pages are read as key
   pages whatever they look like, with OCR for pages whose text gives no answers.
2. "Ayrı anahtar PDF'i ekle": a separate answer-key PDF, picked with the system picker, copied
   privately (`files/keysources`, not into the library) and read whole. Its tests name the file.
3. "Elle gir": typing the key, the last resort, made quick by "Hızlı giriş" (one tap per answer,
   missing questions first).

**Storage** (Room v4, by auto-migrations 1→2→3→4; existing libraries, strokes and keys are kept):
`document_analysis` (with the clues), `key_tests`, `key_answers`, `key_sources`,
`answer_attempts`, `solving_sessions`. Each answer records its type (choice, number, text), origin
(detected / manual), confidence, note and source page; each test its source document (the book or
a key PDF). A new detection replaces only unconfirmed detections from the same source (or from
the pages the student pointed at). Confirmed and typed tests are never replaced, and a detection
repeating a confirmed test isn't added again.

**Review** (`feature/answerkey`, one screen: from the library card's key status or the viewer's ✓)
- Each test shows its answers as chips: found, "Emin değilim" (OCR guess or conflict), "Elle
  girildi" (typed by the student) and, after confirming, "Onaylı". "Kaynağı gör" shows the page
  the answers were read from (book or key PDF), so they can be checked without leaving.
- Tap an answer to change it: for a choice one tap on the letter saves it; numbers (with an
  optional tolerance) and written answers are typed. "Hızlı giriş" fills a test one tap per answer.
  "Kontrol gerekenler" shows only tests with missing, uncertain or conflicting answers.
- "Onayla" confirms the document's tests; it refuses while a conflict is unresolved. Only
  confirmed tests are used for grading.

## Solving and checking a test (multiple choice)

**Marking an answer.** With the pen, the student circles an option on the PDF. When the stroke
ends, the viewer reads the page's layout (`QuestionLayoutDetector`, from the PDF text, or OCR for
a scanned page) and `ChoiceRecognizer` decides what the mark means.
- The unit is an option group: labels A, B, C… printed close together (in a row, a column or a
  grid). Each group takes the question number printed nearest above it in its own column: "4.",
  "4)" or a bare "4" (boxed numbers; accepted only when two or more line up as a column of
  numbers). Headings like "1. GÜN" are not question numbers. A group with no number of its own,
  or whose number is ambiguous, has an unknown question.
- The question is always the one the circled label belongs to. There is no "region the mark
  falls in" and no fallback to question 1 (that was the device bug: a "1. GÜN" header became the
  only question and its region covered the page). A number must sit near its options (at most
  20% of the page width to their left), so a column whose numbers can't be read never borrows the
  other column's; a number two option groups both claim is asked about, not given to either.
- A closed loop around exactly one label of a numbered question → that answer. Around several
  labels, around the value next to a label, or a tick touching a label → "Soru N: hangi şıkkı
  işaretledin?". A known option of an unknown question → "Bu işaret hangi soru için?" with the
  numbers on the page first, then the test's. "Cevap değil" leaves it as a drawing. Anything
  else (notes, working out, big drawings) is just a drawing.
- Marks are handled one at a time in the order drawn, so a quick second circle on the same
  question is always the one that counts.

**Deferred checking (default).** The answer is saved in the document's open solving session and
the viewer only says "Soru 4: C işaretlendi", never right or wrong. A bar shows "N cevap
işaretlendi · Testi kontrol et". Checking (`SessionGrader`):
- takes the latest answer per question. Answers are kept apart from drawings: erasing or undoing
  a circle leaves its answer saved. An answer changes by circling another option; "Geri al" on
  the "Soru 4: C işaretlendi" note drops that answer (an earlier one counts again); the answer
  sheet ("N cevap işaretlendi" → "Cevaplarım") clears a question. The same stroke reported twice
  is one answer;
- refuses to grade against an unconfirmed key ("önce cevap anahtarını onaylaman gerekiyor"; the
  answers stay saved) or a guessed test: if a page's test isn't known, the student picks it once
  and the test's question pages are widened to include it;
- shows correct, incorrect and blank counts for the tests answered in, then the mistakes ("Soru 4 ·
  Senin cevabın B · Doğrusu D", with "Sayfaya git") and blanks, and stores each result. The next
  answer starts a new session. Sessions survive leaving the viewer and restarting the app.

**Immediate feedback** is an option in Settings ("Cevabı hemen göster", off by default): each
answer is graded as it's marked, and saved in the session the same way.

- Everything is in page fractions of the crop box (what the viewer shows), so zoom, pan, device
  rotation, page /Rotate and print bleed don't change the result.
- Numeric and written answers can be stored and graded (`AnswerGrader`: comma decimals, fractions,
  tolerance; written answers are matched exactly after normalising, otherwise "check it yourself").
  Reading handwritten numbers or text from the page is not implemented, and nothing claims to
  grade open-ended answers.

**Known limits**
- Option labels must be text "A)" / "A." / "(A)" / "Ⓐ", or bare letters A–D in a row. Questions
  whose options are pictures (or whose page text is unreadable and OCR misses them) produce no
  groups, and circling stays a drawing.
- A test whose pages aren't known yet is asked once when checking.

**Reading the report** when a book's key isn't found: "Anahtar başlıklı sayfalar: -" means no
heading was seen as text (it may be an image); "sayı satırı" without "harf satırı" means numbers
were read but the letters weren't text; "balon" rows mean answers are marked by filled shapes;
"reddedildi" lines show what almost matched.

**What the JVM tests cannot cover**: Android's `PdfRenderer` and ML Kit OCR do not run under
Robolectric, and no tests ran on a physical device. The text pipeline is tested on real PDFs
generated in the tests (PdfBox, Turkish font, rotated pages, print bleed, two columns); OCR is
tested through the `PageOcr` interface with scripted readers. The book reported from device
testing ("55 Günde TYT Matematik VDK 2024") was not available here, so its exact failure wasn't
reproduced; the layouts above are what such books commonly use. See
[DEVICE_TEST_CHECKLIST.md](DEVICE_TEST_CHECKLIST.md).

## Next

- Timed solving, per-question time, and the mistake notebook fed by checked sessions.
- Showing ✓/✗ next to each circle after checking.
