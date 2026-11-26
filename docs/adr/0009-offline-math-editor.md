# ADR 0009 · Offline math editor and LaTeX preview

**Status** Provisional  
**Ticket** OW-0702 / [KAN-35](https://omninumeris.atlassian.net/browse/KAN-35)  
**Traceability** FR-MAT-02

## Decision

Make the math editor available from the normal workspace on phones and tablets. Keep the draft and selection in saveable UI state. Text entry, system paste and math keys edit the same draft. Fraction, power and square-root keys insert templates at the current selection. Cursor and delete keys preserve the surrounding text.

Extend the pure Kotlin `MathParser.parse(String)` contract for the supported LaTeX subset. It returns the shared `MathInput` AST or a typed rejection with a source position. The editor preserves the draft when parsing fails. Callers can pass the same source through the existing deterministic engine after user review.

Render the syntax tree using bundled Compose components and local system fonts. Fraction bars, radical bars and superscripts follow expression grouping. The preview exposes the resulting structure to accessibility services. Deep trees have a separate preview limit and retain their editable source. Rendering requires no external renderer, scripting engine or network assets.

## Supported syntax

| Entry | Examples |
| --- | --- |
| Numbers, the variable `x` and a single equality | `2.5`, `x+3=7` |
| Arithmetic, signs and parentheses | `2+3×4`, `-(2+3)/4` |
| Braced fractions and square roots | `\frac{1}{2}`, `\sqrt{49}` |
| Plain and braced powers | `2^3`, `2^{3^{2}}` |
| LaTeX multiplication and division | `2\times3`, `2\cdot3`, `6\div3` |

Use explicit multiplication in the editor. Unsupported commands include indexed roots, functions such as `\sin`, formatting commands, macro definitions and matrix environments. Fraction and root arguments require braces. The parser accepts expressions and single equations. Mathematical solving and domain validation remain the caller's responsibility.

## Verification

The pure Kotlin parser tests compare LaTeX and typed ASTs and check original offsets for malformed expressions. The editor unit tests exercise caret placement, selected text, mid-expression insertion and Unicode-aware deletion. The Android test exercises the normal workspace entry, fraction keypad inputs, real clipboard paste, nested preview and draft retention after an unsupported command.

On 2026-11-25, three parser tests, including 80 seeded equivalence cases, and three editor unit tests passed. The UI test passed on Android 10 in airplane mode. The app architecture checks, unit tests, lint, and debug, release and instrumentation builds passed. Installing the debug APK on Samsung preserved all 22 existing canvas strokes. The tablet was locked during the UI smoke test.

- [OMNI baseline requirements](https://omninumeris.atlassian.net/wiki/spaces/OMNI/pages/655361)
- [Exact math and OCR contract](0005-exact-math-and-ocr-evaluation.md)
