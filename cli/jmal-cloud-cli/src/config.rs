use crate::errors::{CliError, Result};
use serde::{Deserialize, Serialize};
use std::env;
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct EnvConfig {
    pub server: Option<String>,
    pub username: Option<String>,
    pub access_token: Option<String>,
    pub jmal_token: Option<String>,
}

impl EnvConfig {
    pub fn from_env() -> Self {
        Self {
            server: env::var("JMAL_CLOUD_SERVER").ok(),
            username: env::var("JMAL_CLOUD_USERNAME").ok(),
            access_token: env::var("JMAL_CLOUD_ACCESS_TOKEN").ok(),
            jmal_token: env::var("JMAL_CLOUD_JMAL_TOKEN").ok(),
        }
    }
}

#[derive(Debug, Default, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct StoredConfig {
    pub server: Option<String>,
    pub username: Option<String>,
    pub user_id: Option<String>,
    pub access_token: Option<String>,
    pub jmal_token: Option<String>,
}

pub fn config_path() -> Result<PathBuf> {
    let mut dir = dirs::config_dir().ok_or(CliError::ConfigDirUnavailable)?;
    dir.push("jmalcloud");
    Ok(dir.join("config.json"))
}

pub fn load_config() -> Result<StoredConfig> {
    let path = config_path()?;
    if !path.exists() {
        return Ok(StoredConfig::default());
    }
    let text = fs::read_to_string(path)?;
    Ok(serde_json::from_str(&text)?)
}

pub fn save_config(config: &StoredConfig) -> Result<()> {
    let path = config_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(&path, serde_json::to_vec_pretty(config)?)?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&path, fs::Permissions::from_mode(0o600))?;
    }
    Ok(())
}

pub fn resolve_server(flag: Option<&str>, env: &EnvConfig) -> Result<String> {
    flag.filter(|value| !value.trim().is_empty())
        .map(str::to_owned)
        .or_else(|| env.server.clone().filter(|value| !value.trim().is_empty()))
        .ok_or(CliError::MissingServer)
}

pub fn first_non_empty(values: &[Option<String>]) -> Option<String> {
    values
        .iter()
        .flatten()
        .find(|value| !value.trim().is_empty())
        .cloned()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolves_server_from_flag_first() {
        let env = EnvConfig {
            server: Some("http://env".into()),
            ..EnvConfig::default()
        };
        assert_eq!(
            resolve_server(Some("http://flag"), &env).unwrap(),
            "http://flag"
        );
    }

    #[test]
    fn resolves_server_from_environment() {
        let env = EnvConfig {
            server: Some("http://env".into()),
            ..EnvConfig::default()
        };
        assert_eq!(resolve_server(None, &env).unwrap(), "http://env");
    }

    #[test]
    fn missing_server_reports_flag_and_env() {
        let err = resolve_server(None, &EnvConfig::default())
            .unwrap_err()
            .to_string();
        assert!(err.contains("--server"));
        assert!(err.contains("JMAL_CLOUD_SERVER"));
    }
}
