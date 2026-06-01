use crate::api::JmalApiClient;
use crate::config::{save_config, StoredConfig};
use crate::errors::{CliError, Result};
use crate::models::{LoginData, UserInfo};
use serde_json::json;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AuthSession {
    pub server: String,
    pub username: String,
    pub user_id: Option<String>,
    pub jmal_token: Option<String>,
    pub access_token: Option<String>,
}

#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct AuthHeaders {
    pub username: Option<String>,
    pub jmal_token: Option<String>,
    pub access_token: Option<String>,
    pub share_id: Option<String>,
    pub share_token: Option<String>,
}

impl AuthHeaders {
    pub fn authenticated(username: String, token: String) -> Self {
        Self {
            username: Some(username),
            jmal_token: Some(token),
            ..Self::default()
        }
    }

    pub fn access_token(token: String) -> Self {
        Self {
            access_token: Some(token),
            ..Self::default()
        }
    }

    pub fn public_share(share_id: String, share_token: String) -> Self {
        Self {
            share_id: Some(share_id),
            share_token: Some(share_token),
            ..Self::default()
        }
    }

    pub fn is_public(&self) -> bool {
        self.share_id.is_some() && self.share_token.is_some()
    }
}

pub fn login_with_password(
    api: &JmalApiClient,
    username: &str,
    password: &str,
    remember_me: bool,
    totp_code: Option<&str>,
) -> Result<AuthSession> {
    let data = api.login(username, password, remember_me)?;
    let final_data = if let Some(mfa_token) = data.mfa_token {
        let code =
            totp_code.ok_or_else(|| CliError::InvalidInput("TOTP code is required".into()))?;
        api.verify_totp(username, &mfa_token, code)?
    } else {
        data
    };
    session_from_login(api.server().to_string(), username.to_string(), final_data)
}

pub fn session_from_login(
    server: String,
    username: String,
    data: LoginData,
) -> Result<AuthSession> {
    let jmal_token = data.jmal_token.ok_or_else(|| {
        CliError::InvalidInput("login response did not include jmal-token".into())
    })?;
    Ok(AuthSession {
        server,
        username,
        user_id: data.user_id,
        jmal_token: Some(jmal_token),
        access_token: None,
    })
}

pub fn store_session(session: &AuthSession) -> Result<()> {
    save_config(&StoredConfig {
        server: Some(session.server.clone()),
        username: Some(session.username.clone()),
        user_id: session.user_id.clone(),
        access_token: session.access_token.clone(),
        jmal_token: session.jmal_token.clone(),
    })
}

pub fn user_info_from_value(value: Option<UserInfo>) -> Result<UserInfo> {
    value.ok_or_else(|| CliError::InvalidInput("user info response did not include data".into()))
}

pub fn login_payload(username: &str, password: &str, remember_me: bool) -> serde_json::Value {
    json!({
        "username": username,
        "password": password,
        "rememberMe": remember_me,
    })
}

pub fn totp_payload(username: &str, mfa_token: &str, mfa_code: &str) -> serde_json::Value {
    json!({
        "username": username,
        "mfaToken": mfa_token,
        "mfaCode": mfa_code,
    })
}
