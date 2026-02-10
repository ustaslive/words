# Crossword Diversity Problem Overview (No Solution Document)

## Purpose

This document records the problem space around crossword variety and repeated words.
It intentionally contains **constraints, observations, open questions, and requirements only**.
It does **not** contain implementation proposals.

## Product Goal (as stated)

- Keep the game interesting by reducing repeated word sets between games.
- Prevent frequent recurrence of the same words (example pattern: similar recurring pairs like `TONE` / `NOTE`).
- Preserve smooth UX: no visible freezes when generating a new crossword.

## Current Runtime Context

- Platform: Android app (Kotlin/Compose).
- Dictionary sources:
  - `app/src/main/assets/words.txt`
  - `app/src/main/assets/forbidden_words.txt`
- Dictionary is edited regularly (both additions and removals in allowed and forbidden lists).
- Dictionary mutability is a core requirement, not an edge case.

## Current Selection Parameters and Rules

From the current game logic and lab script context:

- Seed selection modes:
  - `random_word`
  - `least_similar` (low overlap behavior)
  - `random_letters`
- `maxLetterSetSize` default: `9`.
- Effective seed length range is derived from `maxLetterSetSize`.
- Crossword acceptance constraints include:
  - `MIN_CROSSWORD_WORD_LENGTH = 4`
  - `MIN_CROSSWORD_WORD_COUNT = 9`
  - `MAX_CROSSWORD_GENERATION_ATTEMPTS = 100`

## Hard Constraints from Discussion

1. A single universal approach is required for seed handling.
- The logic must work for any seed source.
- It must remain compatible if new seed-generation methods are added later.
- Separate algorithms for different seed sources are explicitly undesirable.

2. Heavy dependence on prebuilt static dependency/index files is problematic.
- Because dictionaries are frequently edited, static artifacts (IDs, dependency graphs, stats snapshots) become stale.
- Manual rebuild steps are considered unacceptable.
- If artifacts exist, regeneration must be fully automated and reliable.

3. Fresh install behavior matters.
- The game should work immediately on a clean install.
- Runtime memory footprint should stay low.
- Generation should remain responsive.

## Data Snapshot and Observations (Current Lab Evidence)

Observed from current assets and `lab/crossword_repeatability/my10000.txt`:

- `words.txt`: `7102` lines.
- `forbidden_words.txt`: `45` effective entries.
- Dictionary after filtering forbidden: `7094` words.

From stats file (`least_similar`, `max_letter_set_size=9`, `runs=10000`):

- Words seen at least once: `5172`.
- Words not seen: `1922`.

Additional classification under the current word-seed constraints (`max=9`, seed lengths 8..9 in this context):

- Missing words not buildable from any eligible seed: `1336`.
- Missing words buildable only from low-mini seed sets (below acceptance threshold): `247`.
- Missing words buildable from accepted seed sets but still unseen in the run: `339`.

Interpretation of this evidence:

- Non-coverage is not only a randomness issue.
- A large portion of unseen words may be structurally excluded under current constraints and seed-space behavior.

## Open Questions That Must Be Resolved

1. Reachability under random-letter seeds
- Which currently unseen words become reachable when seed source is random letters?
- Which words remain unreachable even then?

2. Coverage baseline definition
- What is the expected minimum long-run coverage of dictionary words?
- Should coverage target apply globally, per session, or per rolling window?

3. Diversity measurement definition
- What exact metric defines "too repetitive"?
- Per-word repetition, set-overlap repetition, recency overlap, or combined metric?

4. Acceptance threshold impact
- How much does `MIN_CROSSWORD_WORD_COUNT = 9` reduce practical vocabulary coverage?
- Which part of the missing-word set is caused by this threshold vs seed generation itself?

5. Dictionary change impact
- After dictionary edits, which analytics/statistics become invalid immediately?
- What must be recomputed, when, and where?

## User-Originated Problem Notes to Preserve

- Frequent dictionary edits are normal and expected.
- Any dependency on static word IDs and derived files must account for dictionary churn.
- Seed generation is extensible; future modes may be added.
- A universal seed-evaluation process is required across all current and future seed sources.
- The possibility of leveraging letter composition from currently unseen/unreachable words was raised as a direction to analyze (recorded here as a requirement input, not a chosen solution).

## Non-Functional Requirements (Problem Framing)

- Generation latency must stay low enough to avoid visible UI blocking.
- Memory usage should remain minimal, especially for first-run/fresh-install scenarios.
- Behavior must remain deterministic enough for debugging and reproducible analysis in the lab.

## Explicit Scope Boundary for This Document

- This file is a problem statement and constraint registry.
- It intentionally excludes concrete algorithmic designs and implementation steps.
