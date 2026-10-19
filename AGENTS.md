# AGENTS.md — ScanViet Engineering Rules

## 1. Project goal

Build ScanViet, a maintainable native Android document-scanning application using Kotlin.

Core features:
- Scan single-page and multi-page documents.
- Preview and manage scanned pages.
- Export and share valid PDF files.
- Recognize Vietnamese text locally on-device.
- Allow users to edit, copy, and save recognized text.
- Persist document metadata and settings locally.
- Support offline use for core functionality after required scanner resources are available.
- Keep the app usable without registration or an app-owned backend.

## 2. Architecture

Use:
- Clean Architecture principles, applied pragmatically.
- MVVM for presentation.
- Repository Pattern for data access.
- Jetpack Compose and Material 3.
- Room for structured local metadata.
- DataStore for application settings.
- Kotlin Coroutines and Flow where appropriate.

Dependency direction:
- Presentation depends on Domain.
- Data implements Domain repository interfaces.
- Domain must not depend on Compose, Android UI, Room, or concrete SDK implementations.
- Scanner, OCR, PDF export, and advertising integrations must be isolated behind clear interfaces.

Start with one Gradle app module and well-separated packages. Do not introduce additional modules, backend services, or complex frameworks without a documented reason.

## 3. Data and privacy

- Keep scanned files in app-private storage by default.
- Store metadata and OCR text in Room only where needed.
- Do not store PDF binary content in Room.
- Use Android Storage Access Framework for user-selected exports and imports where appropriate.
- Never upload document images, PDFs, or OCR text to an app-owned server.
- Do not log document content, OCR results, personal document names, or sensitive paths.
- Handle file deletion and database consistency explicitly.
- Do not promise complete offline behavior until verified on a clean device.
- Document third-party SDK data practices and permissions.

## 4. Code quality

- Use clear names, small classes, and single-responsibility components.
- Keep UI composables free from database and OCR business logic.
- Use immutable UI state and explicit loading, success, empty, and error states.
- Handle cancellation, exceptions, invalid URIs, missing files, and permission failures.
- Avoid duplicated logic and unexplained constants.
- Do not add dependencies without explaining the need and checking compatibility.
- Do not change unrelated files when implementing a feature.
- Do not silently replace existing architecture or remove functionality.

## 5. Required workflow for every task

Before editing:
1. Inspect relevant files and current project structure.
2. Identify dependencies and compatibility constraints.
3. Describe the planned changes and acceptance criteria.
4. List files expected to be added or modified.

After editing:
1. Run the relevant Gradle build and tests.
2. Fix errors caused by the changes.
3. Report the actual commands run and their results.
4. List files changed and explain important decisions.
5. State any unverified behavior or remaining limitations.
6. Do not claim a test passed if it was not executed.

## 6. Testing requirements

- Add unit tests for domain logic and document-management rules.
- Add repository tests for persistence and file consistency.
- Add UI tests for critical navigation and document actions where feasible.
- Test scan cancellation, multi-page PDF export, invalid files, OCR failures, and deletion.
- Verify offline behavior after required scanner resources are available.
- Never use only a successful compilation as proof that a feature works.

## 7. Scope control

Implement only the current requested phase.
Do not add authentication, cloud sync, subscriptions, advertising, analytics, AI APIs, or unrelated features unless explicitly requested.
Keep the application buildable after each phase.

## 8. Completion report

For each task, report:
- Implemented functionality
- Files changed
- Dependencies added or changed
- Build and test commands executed
- Actual results
- Known limitations
- Recommended next task