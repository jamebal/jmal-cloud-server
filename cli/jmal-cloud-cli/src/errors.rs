#[derive(Debug, thiserror::Error)]
pub enum CliError {
    #[error("missing server URL: pass --server or set JMAL_CLOUD_SERVER")]
    MissingServer,
    #[error("missing authentication: use --access-token, --jmal-token with --username, or run login first")]
    MissingAuth,
    #[error("missing username: pass --username, set JMAL_CLOUD_USERNAME, or login first")]
    MissingUsername,
    #[error("missing user id: login first or allow lookup through /api/user/info")]
    MissingUserId,
    #[error("server returned error code {code}: {message}")]
    Server { code: i32, message: String },
    #[error("invalid input: {0}")]
    InvalidInput(String),
    #[error("config directory is unavailable")]
    ConfigDirUnavailable,
    #[error("http error: {0}")]
    Http(#[from] reqwest::Error),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("json error: {0}")]
    Json(#[from] serde_json::Error),
}

pub type Result<T> = std::result::Result<T, CliError>;
