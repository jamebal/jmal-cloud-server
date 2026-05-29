# Rust Upload CLI Design

## Scope

Build a Rust command-line client for this repository's JmalCloud server upload workflows. The first release focuses on netdisk file and folder upload APIs used by the web uploader:

- Authenticated upload: `/api/checkExist`, `/api/upload`, `/api/merge`, `/api/upload-folder`, `/api/new_folder`
- Public share-folder upload: `/api/public/checkExist`, `/api/public/upload`, `/api/public/merge`, `/api/public/upload-folder`
- Login: `/api/login`, `/api/public/verify-totp`
- Access-token authentication via the `access-token` header

The CLI will not implement OSS presigned upload APIs or markdown/image-specialized upload APIs in this release.

## User Experience

The binary name is `jmalcloud`. The primary command is:

```bash
jmalcloud upload <local-path> --server http://127.0.0.1:8088 --remote /Documents
```

Authentication can be supplied in two ways:

```bash
jmalcloud login --server http://127.0.0.1:8088 --username admin
jmalcloud upload ./file.zip --server http://127.0.0.1:8088 --remote / --username admin
```

or:

```bash
jmalcloud upload ./file.zip --server http://127.0.0.1:8088 --remote / --access-token <token>
```

The `--server` value is required unless `JMAL_CLOUD_SERVER` is set. The CLI must not silently use a hard-coded default server. When neither the flag nor the environment variable is present, it exits non-zero with a message telling the user to pass `--server` or set `JMAL_CLOUD_SERVER`.

The login command prompts for password and, when the server returns `mfaToken`, prompts for a TOTP code and completes `/api/public/verify-totp`. Tokens are stored in a config file under the user's config directory with mode `0600` on Unix. CLI flags and environment variables can override stored values:

- `JMAL_CLOUD_SERVER`
- `JMAL_CLOUD_USERNAME`
- `JMAL_CLOUD_ACCESS_TOKEN`
- `JMAL_CLOUD_JMAL_TOKEN`

Upload supports files and folders. Folder upload first creates directory entries through `/api/upload-folder` or `/api/public/upload-folder`, then uploads all contained files with their relative paths.

## Architecture

Create a new Rust crate at `cli/jmal-cloud-cli`. It is independent of the Java build and can be built from the repository root with `cargo test --manifest-path cli/jmal-cloud-cli/Cargo.toml`.

The crate is split into focused modules:

- `main.rs`: CLI argument parsing and command dispatch.
- `config.rs`: config-file load/store and environment override.
- `auth.rs`: login, TOTP verification, and auth header construction.
- `api.rs`: typed HTTP client for JmalCloud response envelopes and upload endpoints.
- `upload.rs`: upload orchestration for files, folders, chunk checks, chunk upload, merge, retries, and progress.
- `path.rs`: remote/local path normalization and relative path generation.
- `identifier.rs`: resumable upload identifier generation compatible with the existing uploader.
- `errors.rs`: shared error type and readable messages.

Use `clap` for CLI parsing, `reqwest` with multipart and blocking APIs for HTTP, `serde` for JSON envelopes, `rpassword` for password/TOTP prompts, `indicatif` for progress, `dirs` for config locations, and `walkdir` for folder traversal.

## Upload Protocol

The CLI mirrors the web uploader fields expected by `UploadApiParamDTO`.

Before uploading a batch, call `checkExist` with `filenames` and the destination metadata. If the server reports `exist: true`, the CLI fails by default and supports `--overwrite` to continue.

For each file:

1. Build `identifier` the same way the existing uploader does, not as a file-content MD5. The formula is `"<file_size>-<relative_path_sanitized>"`, where `relative_path_sanitized` is the upload relative path with every character outside `[0-9A-Za-z_-]` removed. This mirrors `simple-uploader.js` default `file.size + '-' + relativePath.replace(/[^0-9a-zA-Z_-]/g, '')` and matches this repository's `upload.sh` behavior for single-file uploads.
2. Compute `totalChunks = ceil(totalSize / chunkSize)`.
3. Call `GET /api/upload` or `GET /api/public/upload` with the chunk metadata to retrieve `pass` and `resume`.
4. If `pass` is true, treat the file as uploaded.
5. Upload missing chunks as multipart `POST` requests with the same metadata and `file=@chunk`.
6. If a response returns `merge: true`, call `POST /api/merge` or `/api/public/merge`.

Small files are represented as one chunk where `currentChunkSize == totalSize`, matching the server's direct-write path.

## Public Share Upload

Public upload is enabled with:

```bash
jmalcloud upload ./dir --server http://host --remote / --share-id <id> --share-token <token>
```

The CLI uses the `/api/public/...` endpoints and sends `shareId` and `share-token` headers. The server derives username, userId, currentDirectory, and permission metadata from the share target.

## Error Handling

Every JmalCloud response envelope must have `code == 0`; otherwise the CLI exits non-zero and prints the server `message`. HTTP/network errors include the method, endpoint, and retry count. Upload retry is bounded and only retries transient request failures; server validation failures are not retried.

The CLI never logs passwords, TOTP codes, access tokens, `jmal-token`, or refresh cookies. Verbose mode may print endpoint names and file paths, but not secret header values.

## Packaging

Add a GitHub Actions workflow that builds release binaries for:

- `x86_64-unknown-linux-gnu`
- `aarch64-unknown-linux-gnu`
- `x86_64-apple-darwin`
- `aarch64-apple-darwin`

Artifacts are archived as `jmalcloud-<target>.tar.gz` and attached to GitHub releases. Add `cli/jmal-cloud-cli/install.sh`, which detects OS and architecture, downloads the matching archive from GitHub releases, and installs `jmalcloud` into `/usr/local/bin` or `$HOME/.local/bin`.

## Testing

Unit tests cover path normalization, config precedence, response envelope parsing, identifier generation, chunk planning, and public/authenticated endpoint selection.

HTTP tests use `wiremock` to verify:

- login without MFA stores `jmal-token`, username, and userId;
- login with MFA calls `/api/public/verify-totp`;
- access-token upload uses the `access-token` header;
- chunk resume skips chunks returned by `resume`;
- folder upload creates remote folders before files;
- public share upload uses `/api/public/...` and share headers.

Manual integration against a local server is optional because the default server profile may require local database and frontend resources. When feasible, run the server on port `8088`, initialize or use an existing user, and upload a small file plus a multi-chunk file.
