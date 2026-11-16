# CHANGELOG: patch 43 -> 43-v2 (Chaty Home FILE02, source-only)

Date: 2026-11-15. Base: `Chat-y/chat-application` @ `3c67bf51e87c007e37e2195ba714ced988ee87f6` + ordered patches 01-40.

## Supersession

**43-v2 SUPERSEDES the original patch 43** (`43-Chaty-Home-FILE02-source-only-52733ee4.patch`,
sha256 `71d04f87df8f414aaebe76781946fe3f686b964ba9656cccb45dfbdf9e15cf44`).
Do NOT apply both. Apply chain is: 01-40 in manifest order, then 43-v2.
The excluded `ChatFolderChips.kt` hunk ships separately as REVIEW-ONLY and is NOT part of the apply
chain; it stays withheld from the merge vehicle until the unified R5-03 change.

## Artifacts

| File | sha256 | Role |
|---|---|---|
| `43-v2-Chaty-Home-FILE02-source-only-c1f5b544.patch` | `c1f5b544ea183d13816378cb453c785a67313bb43925b30f32ca6031d9f93c17` | Apply after 01-40. ChatsHomeScreen.kt edits + new files HomeSessionTriage.kt, HomeAppearanceResetPreview.kt. No ChatFolderChips.kt change. |
| `43R2-ChatFolderChips-REVIEW-ONLY-R4-03-R4-07-ea02015c.patch` | `ea02015c6440a907057791bc7b7d8d220dbd42609cd0d4815c3e16e97670f6fc` | REVIEW-ONLY. The excluded ChatFolderChips.kt hunk (onReorder comment, R4-03 move earlier/later controls, R4-07 manual-membership line). Do not apply. |
| `43-v2-removed-hunk-note.md` | see parent report | What the hunk did and the dependency audit. |

## What changed vs original 43

Only the removal of the `ChatFolderChips.kt` file diff. Everything else is byte-identical:
the split is exact — 43-v2 lines 1-94 + review-only hunk + 43-v2 lines 95-191 reassemble to the
original 43 byte-for-byte (cmp PASS). `grep -c ChatFolderChips` on 43-v2: 0.

## Verified (fresh clone of the pin, git apply only)

1. Fresh detached checkout at `3c67bf51e87c007e37e2195ba714ced988ee87f6`; patches 01-40 applied in
   manifest order, each preceded by `git apply --check`: PASS. Bundle sha256s match MANIFEST.txt.
2. 43-v2 `git apply --check --whitespace=error` after 01-40: PASS. Applied: PASS.
   `git diff --check` on the result: no whitespace errors.
3. ChatFolderChips.kt byte-identical before and after 43-v2 (sha256
   `6247166705b5ca207e00330bde085c8f2dda6ea38374062cddac033dbef94d49` both times; cmp PASS).
4. Review-only hunk `git apply --check --whitespace=error` on its own after 01-40: PASS.
5. Review-only hunk `git apply --check` after 01-40 + 43-v2 (stacked order): PASS.
6. Equivalence: tree(01-40 + 43-v2 + review-only) is byte-identical to tree(01-40 + original 43)
   across the whole repo (recursive diff excluding .git: no differences).
7. Dependency audit on the applied 01-40 + 43-v2 tree: ChatsHomeScreen.kt has no reference to
   onReorder / Move earlier / Move later / Manual membership; only caller of
   `homeAdjacentFolderOrder` was the excluded hunk; no compile-logical break (see removed-hunk note).

## Unverified (unchanged source-only restriction)

No compile, build, type check, test run, emulator, visual render or runtime verification was
performed. Patch application is not compilation or correctness verification. The unused
`homeAdjacentFolderOrder` helper in HomeSessionTriage.kt is expected to produce at most an
unused-symbol warning when the integrator compiles; that was reasoned, not compiler-confirmed.
