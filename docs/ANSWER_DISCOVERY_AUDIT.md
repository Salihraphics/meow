# Answer discovery audit

Audit of the offline answer-key pipeline at commit `b2e9f99`, before changes. No real PDF that
failed on a device was available in the development environment (including the PDF with
"CEVAP: X" labels on solution pages), so every finding below is confirmed only against code and
synthetic PDFs written with PdfBox in tests (`DiscoveryAuditTest`, two repository tests). Findings
marked **hypothesis** are plausible from the code but not reproduced.

## 1. The pipeline, stage by stage

| # | Stage | Input → output | Implementation |
|---|---|---|---|
| 1 | Page classification | `PageText` per page → readable / unreadable / textless | `AnswerKeyAnalyzer.analyze`: `characterCount < MIN_TEXT_CHARS (25)` = textless; `isReadable` (word shapes) = unreadable |
| 2 | Native text | PDF → words with boxes (page fractions, crop box, rotation applied) → lines | `PdfBoxTextSource` (`PDFTextStripper.writeString`), `groupIntoLines` (splits at gaps > 0.08) |
| 3 | Candidate pages | all pages → key pages, solution pages, question-page paths | `AnswerKeyParser.findKeySectionPages` (heading / continuation / dense), `findSolutionPages`, `mapQuestionPages` (headers in the top 25%, carried forward) |
| 4 | OCR | textless pages → OCR `PageText` | `AnswerKeyAnalyzer.analyze`: only when no key section was found; stage 1 = text headings without answers + textless pages among the last quarter, at most 12; stage 2 = last text pages if nothing found; margins of textless pages if still nothing. `MlKitPageOcr` (1400 px full page, 1200 px margins, no other preprocessing) |
| 5 | Patterns | lines → (number, letter) pairs | `prepare` (column vs. row reading), `KeyTableReader` (tables, matrices), `answerPairs` (coverage ≥ 0.55/0.7, steps 0..3 ≥ 75 %), `answerLabel` ("CEVAP: X"), `BubbleSheetReader` |
| 6 | Association | pairs → question numbers and tests | key sections: `parseKeySection` (headers, restarts at line start); tables: page path; margins: page path if numbers match the page's questions; labels: `questionLabels` + nearest label above in the column, test = page path |
| 7 | Combine | per-finder tests → merged tests | `combine` / `identity` (section + number, else label) / `merge` (agreeing → one answer, disagreeing → `CONFLICT` with provenance) |
| 8 | Review | `KeyTest`s → corrections, confirm | `AnswerKeyScreen`, `AnswerKeyViewModel`; `confirm()` refuses while conflicts remain |
| 9 | Saving | outcome → Room | `AnswerKeyRepository.saveOutcome` / `saveKeyPagesOutcome` / `saveKeySourceOutcome` / `insertDetected` |
| 10 | Rescan | analyzer version, content hash | `AnswerKeyAnalysisScheduler.start`: re-runs when the hash differs, the version is older, or the run was interrupted; `saveOutcome` deletes this source's `DETECTED` non-manual tests and inserts the new ones |

Extraction confidence and association confidence are separate here. Extraction confidence is per
answer: `Confidence` HIGH/LOW, with `AnswerNote` (OCR guess, image reading, conflict).
Association is per test: its name (`TestRef`) and its question pages. An unnamed test, or one
whose pages are unknown, is an unresolved association. It is never guessed: checking a test asks
the student which test the pages belong to.

## 2. Failure matrix

"Before" is the result at `b2e9f99`. Each failing row has a test named in the last column.

| Case | Expected | Before | Test |
|---|---|---|---|
| Dedicated answer table at the end | tests with answers, linked to question pages | ✅ works | `AnswerDiscoveryTest.endOfBookAnswerTable`, `PublisherKeyLayoutsTest` |
| Scanned / image-only key at the end (≤ 12 pages, heading in the OCR'd pages) | read with OCR | ✅ works | `AnswerKeyAnalyzerTest`, `AnswerKeyRecoveryTest` |
| Long scanned key (20 pages, heading on its first page) | all 20 tests | ❌ **0 tests**: OCR reads the last 12 pages only; the heading page is never read, so nothing counts as a key | `m11c_aLongScannedKeyIsReadToItsEnd` |
| "CEVAP: X" under questions (numbers "N.") | each to its question | ✅ works | `AnswerDiscoveryTest.cevapLabels…` |
| "CEVAP: X" right-aligned on a solution page | each to its question | ❌ **0 answers**: the label's x puts it in "column 1"; all question numbers are in column 0 | `m3a_…` |
| "CEVAP:" and the letter printed apart | read | ❌ **0 answers**: they arrive as two lines (gap > 0.08); the pattern needs both on one line | `m3b_…` |
| Solution at the top of a page | read | ❌ **first answer lost**: question numbers in the top 10 % ("header zone") are ignored | `m3c_…` |
| "1.Verilen…" (number fused to text) | read | ❌ **0 answers**: the number isn't a separate word, so it isn't a question number | `m3d_…` |
| Student points at solution pages | labels read | ❌ **0 answers**: pointed-at pages are excluded from the label finder | `m3e_…` |
| A page with solutions for two tests | each answer in its own test | ❌ **wrong question and test**: the mid-page "TEST 2" header is ignored and the decreasing numbers (4 → 1) are dropped as "steps", so TEST 2's answers go to **TEST 1 question 4**, as a false conflict | `m7_…` |
| Solutions naming no test, after the last test's pages | unnamed, student assigns | ❌ **wrong test**: the last question page's test ("TEST 3") is carried onto the solutions | `c_solutionsWithoutATestName…` |
| "Test 1" and "Deneme 1" in one key | two tests | ❌ **merged, 4 false conflicts**, and neither linked to its pages: identity uses only the number | `c_differentKinds…` |
| Headers / footers / margins | page's own key; other sections unassigned | ✅ works | `AnswerDiscoveryTest.footer…` |
| Compact "1.C 2.A 3.D", "1.C2.A3.D" | read | ✅ works | `PublisherKeyLayoutsTest.answersPrintedWithoutSpaces` |
| Numbering restarting at 1, separate lines | new test | ✅ works | `AnswerKeyParserTest.numberingThatRestarts…` |
| Numbering restarting at 1 within one line | two tests | ❌ **one test, 4 false conflicts** | `m6_…` |
| Blank optical sheet "YANITLAR" | no key | ✅ works | `AnswerDiscoveryTest.aBlankOpticalForm…` |
| Missing numbers, incomplete key | gaps reported, not filled | ✅ works | `AnswerDiscoveryTest.missingQuestionNumbers…` |
| Conflicting answers from different methods | conflict with provenance | ✅ works | `AnswerDiscoveryTest.agreeingSources…` |
| Mixed: key partly text, partly scanned | whole key | ❌ **partial**: OCR only runs when *no* key section was found in text | `m11a_…` |
| Mixed: scanned page with a text header layer | OCR'd | ❌ **0 tests**: 49 characters of header text make it a "text page", never OCR'd | `m11b_…` |
| Corrected (not yet confirmed) answer, then rescan | correction kept | ❌ **correction erased**: the rescan deletes `DETECTED` tests even when they hold manual answers | `AnswerKeyRepositoryTest.aRescanKeepsAnswersTheStudentCorrected…` |
| Key read from pointed-at pages, then rescan | kept | ❌ **erased**: the automatic rescan deletes it and doesn't know those pages | `AnswerKeyRepositoryTest.aRescanKeepsTheKeyReadFromPages…` |
| Confirmed key, then rescan | kept | ✅ works | `AnswerKeyRepositoryTest.aRescanAddsNewTestsButKeeps…` |

## 3. The three failure categories

**A. No results.** Confirmed causes:
- Answer labels never matched: letter apart from "CEVAP:" (m3b).
- Labels found but not placed: right-aligned labels (m3a); fused numbers (m3d); pointed-at pages excluded (m3e).
- Scanned pages never OCR'd: a text header layer (m11b); the key's heading beyond the 12-page budget (m11c).

The diagnostic report already shows "soru numarasız" (unplaced labels) and the OCR count, so a
real report would tell these apart.

Hypotheses, not reproduced:
- Solution numbers in formats other than "N." / "N)" ("Soru 1", "1. Soru", a boxed "1" with no
  full stop).
- Labels in other wording ("Doğru seçenek C'dir" works; "Yanıt C şıkkı" works; "Cevabı C" does
  not).
- Scanned solution pages in the middle of the book: they're neither in the last quarter nor
  under a text heading, so they're only OCR'd if the student points at them.

**B. Partial results.**
- Confirmed: question numbers in the top 10 % ignored (m3c); OCR gated on "no key section yet"
  (m11a); OCR budget too short for long scanned keys (m11c).
- Hypothesis: OCR misreads of letters lowering coverage below the line thresholds.

**C. Incorrect associations.** Confirmed causes:
- The test taken from the page, not from the header above the answer (m7).
- Decreasing numbers dropped and their answers given to the previous question (m7).
- The test name carried from question pages onto solutions (c-inherit).
- Test identity ignoring the kind of test (c-kinds).
- A restart inside a line merged into one test (m6).

Each of these also creates **false conflicts**, the confusing confirmations the student has to
resolve.

## 4. Proposed design: the smallest change for the verified causes

Keep the finders, the provenance and the combine step (they work). Fix association and the OCR
plan, and stop rescans from erasing the student's work:

1. **Labelled answers** (`parseLabelled`, `questionLabels`):
   - read labels on whole printed rows (`KeyTableReader.visualRows`), so a letter printed apart
     from "CEVAP:" is still read;
   - use columns only when the page has question numbers in two columns;
   - accept numbers in the header zone (but not test/section headers) and numbers fused to text;
   - give each label the test of the nearest test header above it on the page, with numbering
     increasing within that test (a header starts a new run);
   - on solution pages, take the test only from headers in the solutions section, never from
     question pages;
   - read labels on pointed-at pages too.
2. **Test identity** (`identity`, `matches`): include the kind of test ("test", "deneme", "konu
   tarama"…), so "Deneme 1" is never "Test 1".
3. **Key-section lines**: a restart to 1–2 inside a line starts a new test, like a restart at a
   line start.
4. **OCR plan** (`AnswerKeyAnalyzer.analyze`):
   - pages with sparse text (< 200 characters and no answers) are OCR candidates, and their OCR
     text is added to (not replacing) the native text;
   - stage 1 runs whatever the text found (cheap: it's empty for fully digital books);
   - OCR follows scanned runs: while OCR'd pages give answer lines, their scanned neighbours are
     read too (up to 60 pages in all).
5. **Rescans** (`AnswerKeyRepository`):
   - a detected test holding answers the student corrected is kept like a confirmed one;
   - pages the student pointed at are remembered in the analysis clues, and their tests survive
     rescans.

   Annotations, solving sessions, reading positions and confirmed keys are untouched by rescans
   already: they live in other tables, and the code paths were checked.
6. **Analyzer version 5**, so books whose key isn't confirmed are read again.

Not in scope, kept for evidence from real PDFs:
- new label wordings and solution-number formats;
- full OCR of mid-book scanned solution pages;
- OCR preprocessing (binarisation, deskew).

## 5. After the change

See the "Results" section at the end of this file.

## Results

Every failing row of §2 now passes its test, on synthetic PDFs and scripted OCR only. The real
PDFs that failed on a device were not available, so this doesn't show that they work now:
that needs the device checks in `DEVICE_TEST_CHECKLIST.md` §3.

| Case | After | Change |
|---|---|---|
| Long scanned key (m11c) | ✅ 20 tests | OCR follows scanned runs (≤ 60 pages) |
| Mixed text + scanned key (m11a) | ✅ 4 tests | scanned neighbours of a text key are OCR'd; OCR no longer gated |
| Scan with a text header (m11b) | ✅ 2 tests | margins-only text (< 200 chars) counts as scanned; its text is kept beside the OCR |
| Right-aligned labels (m3a) | ✅ | columns only on two-column pages |
| Letter apart from "CEVAP:" (m3b) | ✅ | labels read on whole printed rows |
| Solution at the top of a page (m3c) | ✅ | numbers accepted in the top band (not headers) |
| "1.Verilen" (m3d) | ✅ | fused numbers |
| Pointed-at solution pages (m3e) | ✅ | labels read on all pages |
| Two tests' solutions on one page (m7) | ✅ no conflict | header above the answer names it; one run per header |
| Unnamed solutions (c-inherit) | ✅ unnamed | solution pages don't inherit the question pages' test |
| "Test 1" vs "Deneme 1" (c-kinds) | ✅ 2 tests | identity and page linking include the test's kind |
| Restart within a line (m6) | ✅ 2 tests | a restart to 1–2 inside a line starts a new test |
| Corrected answer, then rescan | ✅ kept | detected tests with manual answers survive |
| Pointed-at key, then rescan | ✅ kept, not duplicated | pages remembered in clues (`f=`) |

Changed:
- `AnswerKeyParser`: `questionLabels`, `twoColumns`, `pageHeaders`, `parseLabelled`, `wordAt`,
  `findSolutionPages` (visual rows), `parseKeySection` (restart inside a line), `identity`,
  `matches`, `kindOf`.
- `AnswerKeyAnalyzer.analyze` (OCR plan), `merged`, `isMarginsOnly`; constants
  `MAX_FOLLOWED_OCR_PAGES` (60), `MIN_FOLLOWED_ANSWERS` (4), `SPARSE_TEXT_CHARS` (200),
  `MARGIN_BAND` (0.12).
- `AnswerKeyRepository`: `saveOutcome`, `saveKeyPagesOutcome`, `markStarted`, `markFailed`,
  `insertDetected`, `isProtected`; `ANALYZER_VERSION` 5. `AnalysisClues.forcedPages`. No schema
  change (the clues column already exists).

Tests: 306, all passing (292 before, plus the 12 in `DiscoveryAuditTest` and 2 in
`AnswerKeyRepositoryTest`). The m11b fixture's OCR includes the printed key heading, as a real
OCR read would.

Remaining risks (hypotheses, not reproduced):
- A number in the top band like "12." that isn't a question (a page number printed that way)
  could take a label meant for the question above it on the previous page; such a label was
  unplaced before.
- Numbered steps at the left edge of a solution ("3.Adım") now count as fused numbers when they
  increase; the run filter drops only decreasing ones.
- A key naming tests "Konu Testi 1" whose question pages say "Test 1" is no longer linked to
  those pages (different kinds). The answers are still found; the student assigns the pages.
- Following scanned runs can OCR up to 60 pages: time on a real phone is not measured.
- Margins-only detection relies on header/footer text sitting in the top or bottom 12%.
- Still out of scope: other label wordings ("Cevabı C"), other solution numbering ("Soru 1"),
  scanned solution pages in the middle of a book, OCR preprocessing.
