use crate::errors::{CliError, Result};
use std::path::{Path, PathBuf};

pub fn normalize_remote_dir(remote: &str) -> String {
    if remote.trim().is_empty() || remote == "/" {
        return "/".to_string();
    }
    if remote.starts_with('/') {
        remote.to_string()
    } else {
        format!("/{remote}")
    }
}

pub fn normalize_api_base(server: &str) -> Result<String> {
    let trimmed = server.trim().trim_end_matches('/');
    if trimmed.is_empty() {
        return Err(CliError::MissingServer);
    }
    let _ = url::Url::parse(trimmed)
        .map_err(|err| CliError::InvalidInput(format!("invalid server URL: {err}")))?;
    if trimmed.ends_with("/api") {
        Ok(trimmed.to_string())
    } else {
        Ok(format!("{trimmed}/api"))
    }
}

pub fn relative_path(root: &Path, file: &Path) -> Result<String> {
    let relative = file
        .strip_prefix(root)
        .map_err(|err| CliError::InvalidInput(format!("failed to compute relative path: {err}")))?;
    Ok(path_to_upload_string(relative))
}

pub fn path_to_upload_string(path: &Path) -> String {
    path.components()
        .map(|component| component.as_os_str().to_string_lossy())
        .collect::<Vec<_>>()
        .join("/")
}

pub fn containing_root(path: &Path) -> PathBuf {
    if path.is_dir() {
        path.to_path_buf()
    } else {
        path.parent()
            .unwrap_or_else(|| Path::new("."))
            .to_path_buf()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_remote_dir() {
        assert_eq!(normalize_remote_dir("Documents"), "/Documents");
        assert_eq!(normalize_remote_dir("/Documents/"), "/Documents/");
        assert_eq!(normalize_remote_dir("/"), "/");
    }

    #[test]
    fn normalizes_api_base() {
        assert_eq!(
            normalize_api_base("http://127.0.0.1:8088").unwrap(),
            "http://127.0.0.1:8088/api"
        );
        assert_eq!(
            normalize_api_base("http://127.0.0.1:8088/api/").unwrap(),
            "http://127.0.0.1:8088/api"
        );
    }
}
