# Accounts and sync

How optional accounts and cloud sync will fit into MEOW. Nothing here may make an account
necessary: every feature must keep working signed out.

## Current state

- No account system. `AccountRepository` has a single implementation,
  `LocalOnlyAccountRepository`, which always reports `SignedOut` and `isSignInAvailable = false`.
- Settings has a "Hesap" section that says Google sign-in is coming ("Yakında"). It is not a
  button, so nothing pretends to work.
- All data stays on the device (DataStore today; Room for study data later). The only copy that
  leaves the device is Android's own backup, if the user has it enabled.

## Sign-in (planned)

- **API:** Android Credential Manager (`androidx.credentials`) with the Sign in with Google
  library (`com.google.android.libraries.identity.googleid`). This is Google's current
  recommendation; check it again before implementing, because these APIs change.
- **Scopes:** basic profile only (stable account ID, display name, email). MEOW never sees or
  stores a Google password, and asks for no Drive, Gmail or contacts access.
- **Configuration needed:** a Google Cloud project with an OAuth client (a Web client ID plus the
  app's signing certificate SHA-1). The client ID is not secret, but it must come from the build
  configuration, not be hard-coded. No service-account keys or private keys go in the repository.
- **Backend:** sign-in alone needs none. The ID token proves identity only to a server that
  verifies it, so a backend becomes necessary only for sync.
- **What signing in changes:** the app shows who is signed in. It does **not** upload anything.

## Sync (planned, separate opt-in)

Sync needs a backend, so it is a deliberate later decision: a managed service such as Firebase,
or a small server of our own. Before building it, decide the following and write the decision
here.

| Topic | Decision to make |
| --- | --- |
| What syncs | Profile, tests, attempts, mistakes, annotations. PDFs themselves are large and copyrighted, so likely only metadata plus an optional file upload the user explicitly chooses. |
| Conflicts | Per-record last-writer-wins using `updatedAt`, except for append-only data (attempts), which merges. Annotation strokes merge by stroke ID. |
| First sign-in on a second device | Ask whether to merge or keep separate. Never overwrite silently. |
| Signing out | Local data stays on the device. Offer an explicit option to remove it. |
| Account deletion | Deletes server copies within a stated time frame; local data is untouched unless the user asks. In-app and documented. |
| Privacy | A short, plain-language screen before sync is enabled: what leaves the device, where it is stored, how to delete it. |
| Backup and recovery | Local export/import (a file the user owns) works without an account. Sync is never the only backup. |

## Data-model rules that keep this possible

Follow these when adding Room entities, so accounts and sync can be added without rewriting:

- Primary keys are random UUIDs generated on the device, never auto-increment integers.
- Every syncable row has `createdAt`, `updatedAt` (UTC epoch millis) and a `deletedAt` tombstone
  instead of being hard-deleted.
- No `accountId` column on study data. An account is a property of the installation, not of each
  row, so signing in or out never rewrites study data.
- Keep binary files (PDFs, rendered pages) outside the database, referenced by ID.
