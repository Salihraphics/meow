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

## After that: tests from PDFs

- Student marks a page range as a test and enters the answer key; no automatic segmentation yet.
- Question-by-question solving with answer choices, timer, and results (correct / wrong / blank /
  time), feeding the mistake notebook.
