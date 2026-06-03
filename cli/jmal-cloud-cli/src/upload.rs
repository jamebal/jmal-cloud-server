use crate::api::{FolderParams, JmalApiClient, UploadFileParams};
use crate::auth::AuthHeaders;
use crate::errors::{CliError, Result};
use crate::identifier::identifier_for;
use crate::paths::{containing_root, normalize_remote_dir, path_to_upload_string, relative_path};
use indicatif::{ProgressBar, ProgressStyle};
use std::collections::HashSet;
use std::fs::{self, File};
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use tempfile::NamedTempFile;
use walkdir::WalkDir;

#[derive(Debug, Clone)]
pub struct UploadOptions {
    pub local_path: PathBuf,
    pub remote: String,
    pub username: Option<String>,
    pub user_id: Option<String>,
    pub folder: Option<String>,
    pub file_id: Option<String>,
    pub chunk_size: u64,
    pub overwrite: bool,
    pub retries: u32,
    pub verbose: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ChunkPlan {
    pub number: u32,
    pub start: u64,
    pub size: u64,
}

pub fn plan_chunks(total_size: u64, chunk_size: u64) -> Vec<ChunkPlan> {
    let chunk_size = chunk_size.max(1);
    if total_size == 0 {
        return vec![ChunkPlan {
            number: 1,
            start: 0,
            size: 0,
        }];
    }
    let mut chunks = Vec::new();
    let mut start = 0;
    let mut number = 1;
    while start < total_size {
        let size = (total_size - start).min(chunk_size);
        chunks.push(ChunkPlan {
            number,
            start,
            size,
        });
        start += size;
        number += 1;
    }
    chunks
}

pub struct Uploader {
    api: JmalApiClient,
    auth: AuthHeaders,
}

impl Uploader {
    pub fn new(api: JmalApiClient, auth: AuthHeaders) -> Self {
        Self { api, auth }
    }

    pub fn upload_path(&self, mut options: UploadOptions) -> Result<()> {
        options.remote = normalize_remote_dir(&options.remote);
        if !self.auth.is_public() {
            self.ensure_user(&mut options)?;
        }
        if options.local_path.is_dir() {
            self.upload_directory(&options)
        } else if options.local_path.is_file() {
            self.upload_files(
                &options,
                &[options.local_path.clone()],
                &containing_root(&options.local_path),
            )
        } else {
            Err(CliError::InvalidInput(format!(
                "path does not exist: {}",
                options.local_path.display()
            )))
        }
    }

    fn ensure_user(&self, options: &mut UploadOptions) -> Result<()> {
        if options.username.is_some() && options.user_id.is_some() {
            return Ok(());
        }
        let info = self.api.user_info(&self.auth)?;
        if options.username.is_none() {
            options.username = info.username;
        }
        if options.user_id.is_none() {
            options.user_id = info.id;
        }
        if options.username.is_none() {
            return Err(CliError::MissingUsername);
        }
        if options.user_id.is_none() {
            return Err(CliError::MissingUserId);
        }
        Ok(())
    }

    fn upload_directory(&self, options: &UploadOptions) -> Result<()> {
        let root = options.local_path.clone();
        let root_parent = root
            .parent()
            .unwrap_or_else(|| Path::new("."))
            .to_path_buf();
        let root_name = root
            .file_name()
            .ok_or_else(|| CliError::InvalidInput("folder has no filename".into()))?
            .to_string_lossy()
            .to_string();
        let exists = self.api.check_exist(
            &self.auth,
            &[root_name],
            &options.remote,
            options.username.as_deref(),
            options.user_id.as_deref(),
            options.file_id.as_deref(),
        )?;
        if exists.exist && !options.overwrite {
            return Err(CliError::InvalidInput(
                "remote file or folder already exists; pass --overwrite to continue".into(),
            ));
        }
        let mut files = Vec::new();
        let mut dirs = vec![root.clone()];
        for entry in WalkDir::new(&root).sort_by_file_name() {
            let entry = entry.map_err(|err| CliError::InvalidInput(err.to_string()))?;
            let path = entry.path();
            if path == root {
                continue;
            }
            if entry.file_type().is_dir() {
                dirs.push(path.to_path_buf());
            } else if entry.file_type().is_file() {
                files.push(path.to_path_buf());
            }
        }
        for dir in dirs {
            self.create_remote_folder(options, &root_parent, &dir)?;
        }
        self.upload_files_without_batch_check(options, &files, &root_parent)
    }

    fn upload_files(&self, options: &UploadOptions, files: &[PathBuf], root: &Path) -> Result<()> {
        self.upload_files_inner(options, files, root, true)
    }

    fn upload_files_without_batch_check(
        &self,
        options: &UploadOptions,
        files: &[PathBuf],
        root: &Path,
    ) -> Result<()> {
        self.upload_files_inner(options, files, root, false)
    }

    fn upload_files_inner(
        &self,
        options: &UploadOptions,
        files: &[PathBuf],
        root: &Path,
        check_batch: bool,
    ) -> Result<()> {
        let filenames = batch_filenames(files, root)?;
        if check_batch && !filenames.is_empty() {
            let exists = self.api.check_exist(
                &self.auth,
                &filenames,
                &options.remote,
                options.username.as_deref(),
                options.user_id.as_deref(),
                options.file_id.as_deref(),
            )?;
            if exists.exist && !options.overwrite {
                return Err(CliError::InvalidInput(
                    "remote file or folder already exists; pass --overwrite to continue".into(),
                ));
            }
        }
        let progress = ProgressBar::new(total_upload_size(files)?);
        progress.set_style(upload_progress_style());
        for file in files {
            progress.set_message(file.display().to_string());
            self.upload_one_file(options, root, file, &progress)?;
        }
        progress.finish_and_clear();
        Ok(())
    }

    fn create_remote_folder(&self, options: &UploadOptions, root: &Path, dir: &Path) -> Result<()> {
        let relative = relative_path(root, dir)?;
        let remote_name = dir
            .file_name()
            .ok_or_else(|| CliError::InvalidInput("folder has no filename".into()))?
            .to_string_lossy()
            .to_string();
        let parent = dir.parent().unwrap_or(root);
        let folder_path = if parent == root {
            String::new()
        } else {
            relative_path(root, parent)?
        };
        let params = FolderParams {
            folder_path,
            filename: remote_name,
            current_directory: options.remote.clone(),
            username: options.username.clone(),
            user_id: options.user_id.clone(),
            folder: options.folder.clone(),
            file_id: options.file_id.clone(),
        };
        if options.verbose {
            eprintln!("create folder {relative}");
        }
        self.api.upload_folder(&self.auth, &params)
    }

    fn upload_one_file(
        &self,
        options: &UploadOptions,
        root: &Path,
        file: &Path,
        progress: &ProgressBar,
    ) -> Result<()> {
        let metadata = fs::metadata(file)?;
        let total_size = metadata.len();
        let relative = if root == file.parent().unwrap_or(root) {
            file.file_name()
                .ok_or_else(|| CliError::InvalidInput("file has no filename".into()))?
                .to_string_lossy()
                .to_string()
        } else {
            relative_path(root, file)?
        };
        let filename = file
            .file_name()
            .ok_or_else(|| CliError::InvalidInput("file has no filename".into()))?
            .to_string_lossy()
            .to_string();
        let chunks = plan_chunks(total_size, options.chunk_size);
        let base_params = UploadFileParams {
            chunk_number: 1,
            chunk_size: options.chunk_size,
            current_chunk_size: chunks.first().map(|chunk| chunk.size).unwrap_or(0),
            total_size,
            identifier: identifier_for(total_size, &relative),
            filename,
            relative_path: relative,
            total_chunks: chunks.len() as u32,
            is_folder: false,
            current_directory: options.remote.clone(),
            username: options.username.clone(),
            user_id: options.user_id.clone(),
            folder: options.folder.clone(),
            file_id: options.file_id.clone(),
            last_modified: last_modified_millis(&metadata),
        };
        let test = self.api.test_chunk(&self.auth, &base_params)?;
        if test.pass {
            progress.inc(total_size);
            return Ok(());
        }
        let resume: HashSet<u32> = test.resume.into_iter().collect();
        progress.inc(resumed_bytes(&chunks, &resume));
        let mut needs_merge = false;
        for chunk in chunks {
            if resume.contains(&chunk.number) {
                continue;
            }
            let mut params = base_params.clone();
            params.chunk_number = chunk.number;
            params.current_chunk_size = chunk.size;
            let chunk_file = write_temp_chunk(file, chunk.start, chunk.size)?;
            let response = retry(options.retries, || {
                self.api
                    .upload_chunk(&self.auth, &params, chunk_file.path())
            })?;
            if !response.upload {
                return Err(CliError::InvalidInput(format!(
                    "server rejected chunk {} for {}",
                    chunk.number,
                    file.display()
                )));
            }
            if response.merge {
                needs_merge = true;
            }
            progress.inc(chunk.size);
        }
        if needs_merge {
            let _ = self.api.merge(&self.auth, &base_params)?;
        }
        Ok(())
    }
}

fn retry<T, F>(retries: u32, mut action: F) -> Result<T>
where
    F: FnMut() -> Result<T>,
{
    let mut attempts = 0;
    loop {
        match action() {
            Ok(value) => return Ok(value),
            Err(err) if attempts < retries => {
                attempts += 1;
                if matches!(err, CliError::Server { .. } | CliError::InvalidInput(_)) {
                    return Err(err);
                }
            }
            Err(err) => return Err(err),
        }
    }
}

fn total_upload_size(files: &[PathBuf]) -> Result<u64> {
    files.iter().try_fold(0u64, |total, file| {
        Ok(total.saturating_add(fs::metadata(file)?.len()))
    })
}

fn upload_progress_style() -> ProgressStyle {
    ProgressStyle::with_template("{bytes}/{total_bytes} {upload_rate} ETA {upload_eta} {wide_msg}")
        .map(|style| {
            style
                .with_key(
                    "upload_rate",
                    |state: &indicatif::ProgressState, writer: &mut dyn std::fmt::Write| {
                        let _ = writer.write_str(&format_upload_rate(state.per_sec()));
                    },
                )
                .with_key(
                    "upload_eta",
                    |state: &indicatif::ProgressState, writer: &mut dyn std::fmt::Write| {
                        let _ = writer.write_str(&format_upload_eta(state.eta()));
                    },
                )
        })
        .unwrap_or_else(|_| ProgressStyle::default_bar())
}

fn format_upload_rate(bytes_per_second: f64) -> String {
    let bytes_per_second = if bytes_per_second.is_finite() && bytes_per_second > 0.0 {
        bytes_per_second
    } else {
        0.0
    };
    const KB: f64 = 1024.0;
    const MB: f64 = KB * 1024.0;
    const GB: f64 = MB * 1024.0;

    if bytes_per_second < KB {
        format!("{:.0} B/s", bytes_per_second.floor())
    } else if bytes_per_second < MB {
        format!("{:.2} KB/s", bytes_per_second / KB)
    } else if bytes_per_second < GB {
        format!("{:.2} MB/s", bytes_per_second / MB)
    } else {
        format!("{:.2} GB/s", bytes_per_second / GB)
    }
}

fn format_upload_eta(duration: std::time::Duration) -> String {
    let seconds = duration.as_secs();
    if seconds < 60 {
        format!("{seconds}s")
    } else if seconds < 3600 {
        format!("{:02}m{:02}s", seconds / 60, seconds % 60)
    } else {
        format!("{:02}h{:02}m", seconds / 3600, (seconds % 3600) / 60)
    }
}

fn resumed_bytes(chunks: &[ChunkPlan], resume: &HashSet<u32>) -> u64 {
    chunks
        .iter()
        .filter(|chunk| resume.contains(&chunk.number))
        .map(|chunk| chunk.size)
        .sum()
}

fn write_temp_chunk(file: &Path, start: u64, size: u64) -> Result<NamedTempFile> {
    let mut source = File::open(file)?;
    let mut sink = NamedTempFile::new()?;
    source.seek(SeekFrom::Start(start))?;
    let mut chunk_reader = source.take(size);
    std::io::copy(&mut chunk_reader, &mut sink)?;
    sink.flush()?;
    Ok(sink)
}

fn batch_filenames(files: &[PathBuf], root: &Path) -> Result<Vec<String>> {
    let mut names = Vec::new();
    for file in files {
        if root.is_dir() {
            names.push(relative_path(root, file)?);
        } else if let Some(name) = file.file_name() {
            names.push(name.to_string_lossy().to_string());
        }
    }
    Ok(names)
}

fn last_modified_millis(metadata: &fs::Metadata) -> Option<u64> {
    metadata
        .modified()
        .ok()
        .and_then(|time| time.duration_since(std::time::UNIX_EPOCH).ok())
        .map(|duration| duration.as_millis() as u64)
}

#[allow(dead_code)]
pub fn upload_relative_for_path(root: &Path, file: &Path) -> Result<String> {
    if root == file.parent().unwrap_or(root) {
        Ok(file
            .file_name()
            .ok_or_else(|| CliError::InvalidInput("file has no filename".into()))?
            .to_string_lossy()
            .to_string())
    } else {
        relative_path(root, file)
    }
}

#[allow(dead_code)]
pub fn folder_name(path: &Path) -> String {
    path_to_upload_string(path)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::api::JmalApiClient;
    use crate::auth::AuthHeaders;
    use httpmock::prelude::*;
    use serde_json::json;
    use std::io::Write;

    #[test]
    fn chunk_planning_handles_boundaries() {
        assert_eq!(
            plan_chunks(0, 4),
            vec![ChunkPlan {
                number: 1,
                start: 0,
                size: 0
            }]
        );
        assert_eq!(
            plan_chunks(1, 4),
            vec![ChunkPlan {
                number: 1,
                start: 0,
                size: 1
            }]
        );
        assert_eq!(
            plan_chunks(4, 4),
            vec![ChunkPlan {
                number: 1,
                start: 0,
                size: 4
            }]
        );
        assert_eq!(
            plan_chunks(5, 4),
            vec![
                ChunkPlan {
                    number: 1,
                    start: 0,
                    size: 4
                },
                ChunkPlan {
                    number: 2,
                    start: 4,
                    size: 1
                },
            ]
        );
    }

    #[test]
    fn upload_rate_uses_byte_units() {
        assert_eq!(format_upload_rate(0.0), "0 B/s");
        assert_eq!(format_upload_rate(999.4), "999 B/s");
        assert_eq!(format_upload_rate(1024.0), "1.00 KB/s");
        assert_eq!(format_upload_rate(1024.0 * 1024.0 * 1.5), "1.50 MB/s");
        assert_eq!(
            format_upload_rate(1024.0 * 1024.0 * 1024.0 * 2.0),
            "2.00 GB/s"
        );
    }

    #[test]
    fn upload_eta_uses_compact_time_units() {
        assert_eq!(format_upload_eta(std::time::Duration::from_secs(0)), "0s");
        assert_eq!(format_upload_eta(std::time::Duration::from_secs(12)), "12s");
        assert_eq!(
            format_upload_eta(std::time::Duration::from_secs(80)),
            "01m20s"
        );
        assert_eq!(
            format_upload_eta(std::time::Duration::from_secs(3900)),
            "01h05m"
        );
    }

    #[test]
    fn resumed_chunks_count_as_uploaded_bytes() {
        let chunks = plan_chunks(5, 3);
        let resume: HashSet<u32> = [1].into_iter().collect();

        assert_eq!(resumed_bytes(&chunks, &resume), 3);
    }

    #[test]
    fn resume_list_skips_already_uploaded_chunks() {
        let server = MockServer::start();
        let check = server.mock(|when, then| {
            when.method(POST).path("/api/checkExist");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "exist": false,
                    "upload": true
                }
            }));
        });
        let test = server.mock(|when, then| {
            when.method(GET).path("/api/upload");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "pass": false,
                    "resume": [1],
                    "upload": true
                }
            }));
        });
        let upload = server.mock(|when, then| {
            when.method(POST).path("/api/upload");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "upload": true,
                    "merge": true
                }
            }));
        });
        let merge = server.mock(|when, then| {
            when.method(POST).path("/api/merge");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "upload": true
                }
            }));
        });
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("hello.txt");
        std::fs::write(&file, b"hello").unwrap();
        let uploader = Uploader::new(
            JmalApiClient::new(&server.base_url()).unwrap(),
            AuthHeaders::access_token("access".into()),
        );
        uploader
            .upload_path(UploadOptions {
                local_path: file,
                remote: "/".into(),
                username: Some("admin".into()),
                user_id: Some("u1".into()),
                folder: None,
                file_id: None,
                chunk_size: 3,
                overwrite: false,
                retries: 0,
                verbose: false,
            })
            .unwrap();
        check.assert();
        test.assert();
        upload.assert_hits(1);
        merge.assert();
    }

    #[test]
    fn access_token_upload_resolves_missing_user_info() {
        let server = MockServer::start();
        let user_info = server.mock(|when, then| {
            when.method(GET)
                .path("/api/user/info")
                .header("access-token", "access");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "id": "u1",
                    "username": "admin"
                }
            }));
        });
        server.mock(|when, then| {
            when.method(POST).path("/api/checkExist");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "exist": false,
                    "upload": true
                }
            }));
        });
        server.mock(|when, then| {
            when.method(GET).path("/api/upload");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "pass": false,
                    "resume": [],
                    "upload": true
                }
            }));
        });
        server.mock(|when, then| {
            when.method(POST).path("/api/upload");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "upload": true
                }
            }));
        });
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("hello.txt");
        std::fs::write(&file, b"hi").unwrap();
        let uploader = Uploader::new(
            JmalApiClient::new(&server.base_url()).unwrap(),
            AuthHeaders::access_token("access".into()),
        );
        uploader
            .upload_path(UploadOptions {
                local_path: file,
                remote: "/".into(),
                username: None,
                user_id: None,
                folder: None,
                file_id: None,
                chunk_size: 3,
                overwrite: false,
                retries: 0,
                verbose: false,
            })
            .unwrap();
        user_info.assert();
    }

    #[test]
    fn public_directory_upload_creates_public_folders() {
        let server = MockServer::start();
        let create_folder = server.mock(|when, then| {
            when.method(POST)
                .path("/api/public/upload-folder")
                .header("shareId", "sid")
                .header("share-token", "stok");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": null
            }));
        });
        server.mock(|when, then| {
            when.method(POST).path("/api/public/checkExist");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "exist": false,
                    "upload": true
                }
            }));
        });
        server.mock(|when, then| {
            when.method(GET).path("/api/public/upload");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "pass": false,
                    "resume": [],
                    "upload": true
                }
            }));
        });
        server.mock(|when, then| {
            when.method(POST).path("/api/public/upload");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "upload": true
                }
            }));
        });
        let parent = tempfile::tempdir().unwrap();
        let dir = parent.path().join("folder");
        std::fs::create_dir(&dir).unwrap();
        let file = dir.join("hello.txt");
        std::fs::File::create(&file)
            .unwrap()
            .write_all(b"hi")
            .unwrap();
        let uploader = Uploader::new(
            JmalApiClient::new(&server.base_url()).unwrap(),
            AuthHeaders::public_share("sid".into(), "stok".into()),
        );
        uploader
            .upload_path(UploadOptions {
                local_path: dir.clone(),
                remote: "/".into(),
                username: None,
                user_id: None,
                folder: None,
                file_id: Some("share-folder".into()),
                chunk_size: 3,
                overwrite: false,
                retries: 0,
                verbose: false,
            })
            .unwrap();
        create_folder.assert();
    }
}
