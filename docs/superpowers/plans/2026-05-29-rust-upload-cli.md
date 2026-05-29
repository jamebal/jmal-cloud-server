# Rust Upload CLI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `jmalcloud` Rust CLI for authenticated and public-share file/folder uploads to this server.

**Architecture:** Add an independent Rust crate under `cli/jmal-cloud-cli` with a typed HTTP client, auth/config handling, uploader-compatible identifier generation, upload orchestration, and packaging assets. The implementation mirrors the existing Vue `simple-uploader.js` flow and `upload.sh` API fields.

**Tech Stack:** Rust 2021, `clap`, `reqwest` blocking multipart client, `serde`, `thiserror`, `rpassword`, `dirs`, `walkdir`, `indicatif`, `wiremock`, GitHub Actions.

---

## File Structure

- Create `cli/jmal-cloud-cli/Cargo.toml`: package metadata, binary target named `jmalcloud`, dependencies and dev-dependencies.
- Create `cli/jmal-cloud-cli/src/main.rs`: CLI parsing and command dispatch.
- Create `cli/jmal-cloud-cli/src/lib.rs`: module exports for tests and binary.
- Create `cli/jmal-cloud-cli/src/config.rs`: config-file model, env override, server resolution, Unix permissions.
- Create `cli/jmal-cloud-cli/src/errors.rs`: shared `CliError` and `Result` alias.
- Create `cli/jmal-cloud-cli/src/models.rs`: JmalCloud response envelopes and DTOs.
- Create `cli/jmal-cloud-cli/src/auth.rs`: login, TOTP, user info resolution, auth header model.
- Create `cli/jmal-cloud-cli/src/api.rs`: blocking HTTP client for all upload endpoints.
- Create `cli/jmal-cloud-cli/src/identifier.rs`: `simple-uploader.js` compatible identifier generation.
- Create `cli/jmal-cloud-cli/src/paths.rs`: URL base normalization, remote path normalization, relative path building.
- Create `cli/jmal-cloud-cli/src/upload.rs`: folder walk, chunk planning, resume checks, multipart chunk upload, merge, progress.
- Create `cli/jmal-cloud-cli/install.sh`: one-command Linux/macOS installer.
- Create `.github/workflows/jmalcloud-cli-release.yml`: multi-target build and release artifacts.
- Modify `README.md`: short CLI section with install and usage.

## Task 1: Scaffold Crate And CLI Shell

**Files:**
- Create: `cli/jmal-cloud-cli/Cargo.toml`
- Create: `cli/jmal-cloud-cli/src/main.rs`
- Create: `cli/jmal-cloud-cli/src/lib.rs`
- Create: `cli/jmal-cloud-cli/src/errors.rs`

- [ ] **Step 1: Write failing CLI metadata tests**

Create `cli/jmal-cloud-cli/src/main.rs` with a `Cli` parser and add tests that expect the command name to be `jmalcloud`, with `login` and `upload` subcommands.

```rust
#[test]
fn command_name_is_jmalcloud() {
    use clap::CommandFactory;
    assert_eq!(Cli::command().get_name(), "jmalcloud");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml command_name_is_jmalcloud`

Expected: FAIL because the crate and parser do not exist yet.

- [ ] **Step 3: Implement minimal crate shell**

Add `Cargo.toml` with `[[bin]] name = "jmalcloud"`, create `Cli`, `Commands::Login`, `Commands::Upload`, and a `CliError` enum. `upload` must include `path`, `--server`, `--remote`, `--username`, `--access-token`, `--jmal-token`, `--chunk-size`, `--overwrite`, `--share-id`, `--share-token`, `--retries`, and `--verbose`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml command_name_is_jmalcloud`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add cli/jmal-cloud-cli && git commit -m "feat(cli): scaffold rust upload cli"`

## Task 2: Config And Server Resolution

**Files:**
- Create: `cli/jmal-cloud-cli/src/config.rs`
- Modify: `cli/jmal-cloud-cli/src/lib.rs`
- Modify: `cli/jmal-cloud-cli/src/main.rs`

- [ ] **Step 1: Write failing config tests**

Add tests for `resolve_server(Some(flag), env)` returning the flag, `resolve_server(None, env)` using `JMAL_CLOUD_SERVER`, and missing server returning an error whose message mentions `--server` and `JMAL_CLOUD_SERVER`.

```rust
#[test]
fn missing_server_reports_flag_and_env() {
    let err = resolve_server(None, &EnvConfig::default()).unwrap_err().to_string();
    assert!(err.contains("--server"));
    assert!(err.contains("JMAL_CLOUD_SERVER"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml missing_server_reports_flag_and_env`

Expected: FAIL because config resolution is not implemented.

- [ ] **Step 3: Implement config resolution**

Implement `EnvConfig::from_env`, `StoredConfig`, `EffectiveConfig`, `resolve_server`, `load_config`, and `save_config`. Use `dirs::config_dir()/jmalcloud/config.json`. On Unix, set the file mode to `0600` after saving.

- [ ] **Step 4: Run config tests**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml config`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add cli/jmal-cloud-cli && git commit -m "feat(cli): add config and server resolution"`

## Task 3: Response Models And Auth

**Files:**
- Create: `cli/jmal-cloud-cli/src/models.rs`
- Create: `cli/jmal-cloud-cli/src/auth.rs`
- Modify: `cli/jmal-cloud-cli/src/lib.rs`
- Modify: `cli/jmal-cloud-cli/src/main.rs`

- [ ] **Step 1: Write failing auth tests**

Use `wiremock` to test:

- `/api/login` returning `{ "code": 0, "data": { "jmal-token": "tok", "userId": "u1" } }` stores username and token.
- `/api/login` returning `{ "code": 0, "data": { "mfaToken": "mfa" } }` followed by `/api/public/verify-totp` returns the final token.
- server response `{ "code": -1, "message": "login.error" }` becomes an error.

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml auth`

Expected: FAIL because auth code is missing.

- [ ] **Step 3: Implement auth client**

Implement `JmalResponse<T>`, `LoginData`, `UserInfo`, `AuthSession`, `AuthHeaders`, and login methods using JSON requests. For `jmal-token` auth, send both `jmal-token` and `name`; for access-token auth, send `access-token`.

- [ ] **Step 4: Run auth tests**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml auth`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add cli/jmal-cloud-cli && git commit -m "feat(cli): add login and auth headers"`

## Task 4: Existing-Uploader Identifier And Paths

**Files:**
- Create: `cli/jmal-cloud-cli/src/identifier.rs`
- Create: `cli/jmal-cloud-cli/src/paths.rs`
- Modify: `cli/jmal-cloud-cli/src/lib.rs`

- [ ] **Step 1: Write failing identifier/path tests**

Add tests for:

```rust
assert_eq!(identifier_for(12, "a b/中文.txt"), "12-abtxt");
assert_eq!(identifier_for(5, "dir/file_name-1.txt"), "5-dirfile_name-1txt");
assert_eq!(normalize_remote_dir("Documents"), "/Documents");
assert_eq!(normalize_remote_dir("/Documents/"), "/Documents/");
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml identifier paths`

Expected: FAIL because modules are missing.

- [ ] **Step 3: Implement identifier and path helpers**

Implement `identifier_for(size, relative_path)` with the same sanitization as `simple-uploader.js`: keep only ASCII letters, digits, `_`, and `-`. Implement remote directory normalization without inventing a server default.

- [ ] **Step 4: Run tests**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml identifier paths`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add cli/jmal-cloud-cli && git commit -m "feat(cli): match uploader identifier semantics"`

## Task 5: Upload API Client

**Files:**
- Create: `cli/jmal-cloud-cli/src/api.rs`
- Modify: `cli/jmal-cloud-cli/src/models.rs`
- Modify: `cli/jmal-cloud-cli/src/lib.rs`

- [ ] **Step 1: Write failing API tests**

Use `wiremock` to verify:

- authenticated `check_exist` posts to `/api/checkExist`;
- public `check_exist` posts to `/api/public/checkExist` and sends `shareId` plus `share-token`;
- `test_chunk` uses `GET /api/upload`;
- `upload_chunk` sends multipart field `file`;
- `merge` posts to `/api/merge`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml api`

Expected: FAIL because API client is missing.

- [ ] **Step 3: Implement API client**

Implement `JmalApiClient` with `check_exist`, `test_chunk`, `upload_chunk`, `merge`, `upload_folder`, `new_folder`, and `user_info`. Normalize `--server` so both `http://host` and `http://host/api` produce correct endpoint URLs.

- [ ] **Step 4: Run API tests**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml api`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add cli/jmal-cloud-cli && git commit -m "feat(cli): add jmalcloud upload api client"`

## Task 6: Upload Orchestration

**Files:**
- Create: `cli/jmal-cloud-cli/src/upload.rs`
- Modify: `cli/jmal-cloud-cli/src/main.rs`
- Modify: `cli/jmal-cloud-cli/src/lib.rs`

- [ ] **Step 1: Write failing upload tests**

Add tests for:

- chunk planning for `0`, `1`, `chunk_size`, and `chunk_size + 1` byte files;
- resume list `[1, 3]` uploads only chunk `2`;
- folder traversal creates folders before uploading nested files;
- access-token-only upload resolves username and userId through `/api/user/info`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml upload`

Expected: FAIL because orchestration is missing.

- [ ] **Step 3: Implement uploader**

Implement `UploadOptions`, `UploadPlan`, `ChunkPlan`, and `Uploader`. For ordinary uploads, require or resolve `username` and `userId`; for public share uploads, omit them unless supplied because the server derives them from share headers. Use `checkExist` before a batch and fail on existing files unless `--overwrite` is passed. Upload chunks with bounded retry and call merge when a chunk response returns `merge: true`.

- [ ] **Step 4: Run upload tests**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml upload`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add cli/jmal-cloud-cli && git commit -m "feat(cli): implement resumable file uploads"`

## Task 7: Installer, Release Workflow, And Docs

**Files:**
- Create: `cli/jmal-cloud-cli/install.sh`
- Create: `.github/workflows/jmalcloud-cli-release.yml`
- Modify: `README.md`

- [ ] **Step 1: Write failing script/workflow checks**

Run before files exist:

```bash
bash -n cli/jmal-cloud-cli/install.sh
cargo metadata --manifest-path cli/jmal-cloud-cli/Cargo.toml --no-deps
```

Expected: installer check FAIL because the script does not exist.

- [ ] **Step 2: Implement packaging files**

Add an installer that maps `Darwin/x86_64`, `Darwin/arm64`, `Linux/x86_64`, and `Linux/aarch64` to release archives named `jmalcloud-<target>.tar.gz`. Add GitHub Actions to build the four targets, archive the binary as `jmalcloud`, upload artifacts, and attach archives on release events.

- [ ] **Step 3: Add README usage**

Document:

```bash
jmalcloud login --server http://127.0.0.1:8088 --username admin
jmalcloud upload ./file.txt --server http://127.0.0.1:8088 --remote /
JMAL_CLOUD_SERVER=http://127.0.0.1:8088 jmalcloud upload ./dir --remote /
jmalcloud upload ./file.txt --server http://127.0.0.1:8088 --remote / --access-token <token> --username admin
```

- [ ] **Step 4: Run packaging checks**

Run:

```bash
bash -n cli/jmal-cloud-cli/install.sh
cargo metadata --manifest-path cli/jmal-cloud-cli/Cargo.toml --no-deps
```

Expected: both PASS.

- [ ] **Step 5: Commit**

Run: `git add cli/jmal-cloud-cli/install.sh .github/workflows/jmalcloud-cli-release.yml README.md && git commit -m "chore(cli): add installer and release workflow"`

## Task 8: Final Verification

**Files:**
- All CLI files

- [ ] **Step 1: Format**

Run: `cargo fmt --manifest-path cli/jmal-cloud-cli/Cargo.toml -- --check`

Expected: PASS.

- [ ] **Step 2: Lint**

Run: `cargo clippy --manifest-path cli/jmal-cloud-cli/Cargo.toml --all-targets -- -D warnings`

Expected: PASS.

- [ ] **Step 3: Test**

Run: `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml`

Expected: PASS.

- [ ] **Step 4: Build release binary**

Run: `cargo build --manifest-path cli/jmal-cloud-cli/Cargo.toml --release`

Expected: PASS and binary exists at `cli/jmal-cloud-cli/target/release/jmalcloud`.

- [ ] **Step 5: Optional local server smoke test**

If the Spring Boot server can start locally with available dependencies and data, run it on port `8088`, initialize or use an existing user, then upload one small file and one multi-chunk file with `target/release/jmalcloud`. If server startup is blocked by missing external services or local data, record that blocker and rely on Rust HTTP tests.
