use crate::errors::{CliError, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JmalResponse<T> {
    pub code: i32,
    pub message: Option<String>,
    pub data: Option<T>,
    pub count: Option<serde_json::Value>,
    pub props: Option<HashMap<String, serde_json::Value>>,
}

impl<T> JmalResponse<T> {
    pub fn into_result(self) -> Result<Option<T>> {
        if self.code == 0 {
            Ok(self.data)
        } else {
            Err(CliError::Server {
                code: self.code,
                message: self.message.unwrap_or_else(|| "server error".to_string()),
            })
        }
    }
}

#[derive(Debug, Default, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct LoginData {
    #[serde(rename = "jmal-token")]
    pub jmal_token: Option<String>,
    #[serde(rename = "userId")]
    pub user_id: Option<String>,
    pub username: Option<String>,
    #[serde(rename = "mfaToken")]
    pub mfa_token: Option<String>,
    #[serde(rename = "mfaForceEnable")]
    pub mfa_force_enable: Option<bool>,
}

#[derive(Debug, Default, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct UserInfo {
    pub id: Option<String>,
    pub username: Option<String>,
}

#[derive(Debug, Default, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct UploadResponse {
    #[serde(default)]
    pub pass: bool,
    #[serde(default)]
    pub exist: bool,
    #[serde(default)]
    pub resume: Vec<u32>,
    #[serde(default)]
    pub upload: bool,
    #[serde(default)]
    pub merge: bool,
}

#[derive(Debug, Default, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct CheckExistResponse {
    #[serde(default)]
    pub pass: bool,
    #[serde(default)]
    pub exist: bool,
    #[serde(default)]
    pub resume: Vec<u32>,
    #[serde(default)]
    pub upload: bool,
    #[serde(default)]
    pub merge: bool,
}

impl From<UploadResponse> for CheckExistResponse {
    fn from(value: UploadResponse) -> Self {
        Self {
            pass: value.pass,
            exist: value.exist,
            resume: value.resume,
            upload: value.upload,
            merge: value.merge,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn envelope_rejects_server_error() {
        let response: JmalResponse<LoginData> =
            serde_json::from_str(r#"{"code":-1,"message":"login.error","data":null}"#).unwrap();
        let err = response.into_result().unwrap_err().to_string();
        assert!(err.contains("login.error"));
    }
}
