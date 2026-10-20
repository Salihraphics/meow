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

## Next: annotation

The page list already has the hook: `PdfPagesView(pageOverlay = { pageIndex -> ... })` draws in
each page's bounds and scrolls and zooms with it.

Plan:
- Strokes stored per document and page in **page-normalised coordinates** (0..1), so they survive
  zoom, rotation and different render sizes. Each stroke: UUID, tool (pen / highlighter /
  eraser), colour, width, points with optional pressure, `createdAt`, `deletedAt`.
- Input: `PointerType.Stylus` draws immediately; with a finger, one finger draws only in drawing
  mode, and two fingers always scroll and zoom. Palm rejection: ignore touch while a stylus is down.
- Room table `annotation_strokes`, saved as each stroke ends; undo and redo in memory.

## After that: tests from PDFs

- Student marks a page range as a test and enters the answer key; no automatic segmentation yet.
- Question-by-question solving with answer choices, timer, and results (correct / wrong / blank /
  time), feeding the mistake notebook.
