# Data, security and UX design

## Logical database

Use UUIDs, timestamps, schema version and foreign keys. Store large assets outside SQLite with transactional staging and recovery journal.

  ---------------------------------------------------------------------------------------------------------------------------
  Entity                              Key fields / relationships
  ----------------------------------- ---------------------------------------------------------------------------------------
  Folder                              id, parent_id, name

  Content                             id, folder_id, type, title, protection_id, deleted_at

  Page                                id, content_id, order, dimensions, template, revision

  CanvasObject                        id, page_id, type, bounds, transform, layer_id, payload_ref

  Layer                               id, page_id, order, opacity, blend, locked

  Stroke                              id, object_id, sample_blob, brush, timestamp

  RasterTile                          layer_id, x, y, level, asset_id

  Asset                               id, hash, path, mime, size, encryption_scope

  Revision                            id, content_id, parent_revision, operation_log, snapshot_ref

  Anchor                              id, content_id, revision_id, page_id, rect or EPUB locator

  Annotation                          id, anchor_id, type, payload

  ReadingState                        content_id, locator, progress, listening_locator

  Tag / ContentTag                    id, name / content_id, tag_id

  Link                                source_anchor, target_anchor, type

  Chunk                               id, anchor_id, text_ref, index_version, protection_scope

  Embedding                           chunk_id, model_id, vector_ref

  Conversation / Message              id, scope / conversation_id, role, body_ref, model_id

  Citation                            message_id, anchor_id, source_revision

  AIAction                            id, proposal, approval_state, revision_id

  MathSolution                        id, anchor_id, AST, steps, assumptions, verification_status

  Calculation                         id, mode, expression, result

  Recording / Transcript              id, asset_id, content_id / recording_id, start_ms, end_ms, text

  Deck / Card / Review                subject, source_anchor / deck_id, prompt, answer, due_at / card_id, rating, timestamp

  Quiz / Question / Attempt           subject / quiz_id, answer, rubric / quiz_id, score, feedback

  StudySession                        subject, duration, goal, timestamp

  Model                               id, architecture, quantization, hash, licence, path, capabilities

  Protection                          id, wrapped_key, policy

  Backup / Entitlement                manifest, version / product_id, validation_state
  ---------------------------------------------------------------------------------------------------------------------------

Index folder/content foreign keys, page order, object spatial bounds, due cards, anchors and FTS text. Partition protected indexes by authorization scope. Avoid cross-scope deduplication leaks. EPUB anchors use stable locators and quote context; PDF anchors use page coordinates; notebook anchors use object IDs and revisions. Migrations and backup restore must be tested.

## Security

Threat model: stolen device, malicious imports, prompt injection, leaked cache, corrupted backup, unauthorized AI access. Use Android Keystore-wrapped random content keys and authenticated encryption; portable backups use reviewed password KDF and authenticated manifest. Lock clears decrypted caches and active inference context; protect thumbnails, embeddings, transcripts and signatures. Default to no cloud backup of sensitive data. No plaintext temporary files outside protected scope. Validate archive paths, size limits and native parsers. Restrict network to explicit downloads/billing/update operations. No remote content telemetry. Explain forgotten password recovery limits and flash-storage deletion limits. User approval for AI changes, transactional rollback and audit history.

## UI/UX

Premium neutral surfaces, configurable accent, light/dark/sepia, consistent typography and icons. Phone: adaptive navigation and compact floating tools. Tablet: library sidebar, tabs, split-screen reader/notebook and AI panel. Screens: onboarding/model setup, home/library, notebook, drawing studio, reader, scanner, PDF editor, calculator, study dashboard, flashcards/quiz, AI chat, knowledge graph, audio, settings, backup and billing. Toolbars customizable; stylus-only mode, finger pan/zoom, explicit circle-to-solve mode to avoid accidental solving. Show recognition preview, verification state, source links and AI change preview. Accessible labels, keyboard focus, large touch targets, reduced motion and scalable text. Always show offline capability and missing model status; never block core notes on setup.
