use crate::auth::{login_payload, totp_payload, AuthHeaders};
use crate::errors::{CliError, Result};
use crate::models::{CheckExistResponse, JmalResponse, LoginData, UploadResponse, UserInfo};
use crate::paths::normalize_api_base;
use reqwest::blocking::multipart;
use reqwest::blocking::{Client, RequestBuilder};
use serde::de::DeserializeOwned;
use serde_json::json;
use std::collections::BTreeMap;
use std::path::Path;
use std::time::Duration;

#[derive(Clone)]
pub struct JmalApiClient {
    client: Client,
    api_base: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UploadFileParams {
    pub chunk_number: u32,
    pub chunk_size: u64,
    pub current_chunk_size: u64,
    pub total_size: u64,
    pub identifier: String,
    pub filename: String,
    pub relative_path: String,
    pub total_chunks: u32,
    pub is_folder: bool,
    pub current_directory: String,
    pub username: Option<String>,
    pub user_id: Option<String>,
    pub folder: Option<String>,
    pub file_id: Option<String>,
    pub last_modified: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FolderParams {
    pub folder_path: String,
    pub filename: String,
    pub current_directory: String,
    pub username: Option<String>,
    pub user_id: Option<String>,
    pub folder: Option<String>,
    pub file_id: Option<String>,
}

impl JmalApiClient {
    pub fn new(server: &str) -> Result<Self> {
        let client = Client::builder()
            .timeout(Duration::from_secs(300))
            .connect_timeout(Duration::from_secs(15))
            .build()?;
        Ok(Self {
            client,
            api_base: normalize_api_base(server)?,
        })
    }

    pub fn server(&self) -> &str {
        &self.api_base
    }

    fn endpoint(&self, path: &str) -> String {
        format!("{}/{}", self.api_base, path.trim_start_matches('/'))
    }

    fn with_headers(&self, request: RequestBuilder, headers: &AuthHeaders) -> RequestBuilder {
        let mut request = request;
        if let Some(token) = &headers.access_token {
            request = request.header("access-token", token);
        }
        if let Some(token) = &headers.jmal_token {
            request = request.header("jmal-token", token);
        }
        if let Some(username) = &headers.username {
            request = request.header("name", username);
        }
        if let Some(share_id) = &headers.share_id {
            request = request.header("shareId", share_id);
        }
        if let Some(share_token) = &headers.share_token {
            request = request.header("share-token", share_token);
        }
        request.header("lang", "zh-CN")
    }

    fn parse_envelope<T: DeserializeOwned>(&self, text: String) -> Result<Option<T>> {
        let envelope: JmalResponse<T> = serde_json::from_str(&text)?;
        envelope.into_result()
    }

    pub fn login(&self, username: &str, password: &str, remember_me: bool) -> Result<LoginData> {
        let response = self
            .client
            .post(self.endpoint("login"))
            .json(&login_payload(username, password, remember_me))
            .send()?
            .error_for_status()?
            .text()?;
        self.parse_envelope(response)?
            .ok_or_else(|| CliError::InvalidInput("login response did not include data".into()))
    }

    pub fn verify_totp(
        &self,
        username: &str,
        mfa_token: &str,
        mfa_code: &str,
    ) -> Result<LoginData> {
        let response = self
            .client
            .post(self.endpoint("public/verify-totp"))
            .json(&totp_payload(username, mfa_token, mfa_code))
            .send()?
            .error_for_status()?
            .text()?;
        self.parse_envelope(response)?
            .ok_or_else(|| CliError::InvalidInput("TOTP response did not include data".into()))
    }

    pub fn user_info(&self, headers: &AuthHeaders) -> Result<UserInfo> {
        let response = self
            .with_headers(self.client.get(self.endpoint("user/info")), headers)
            .send()?
            .error_for_status()?
            .text()?;
        self.parse_envelope(response)?
            .ok_or_else(|| CliError::InvalidInput("user info response did not include data".into()))
    }

    pub fn check_exist(
        &self,
        headers: &AuthHeaders,
        filenames: &[String],
        current_directory: &str,
        username: Option<&str>,
        user_id: Option<&str>,
        file_id: Option<&str>,
    ) -> Result<CheckExistResponse> {
        let endpoint = if headers.is_public() {
            "public/checkExist"
        } else {
            "checkExist"
        };
        let body = json!({
            "filenames": filenames,
            "currentDirectory": current_directory,
            "username": username,
            "userId": user_id,
            "fileId": file_id,
        });
        let response = self
            .with_headers(self.client.post(self.endpoint(endpoint)), headers)
            .json(&body)
            .send()?
            .error_for_status()?
            .text()?;
        let upload: Option<UploadResponse> = self.parse_envelope(response)?;
        Ok(upload.unwrap_or_default().into())
    }

    pub fn test_chunk(
        &self,
        headers: &AuthHeaders,
        params: &UploadFileParams,
    ) -> Result<UploadResponse> {
        let endpoint = if headers.is_public() {
            "public/upload"
        } else {
            "upload"
        };
        let response = self
            .with_headers(
                self.client
                    .get(self.endpoint(endpoint))
                    .query(&params.to_query_map()),
                headers,
            )
            .send()?
            .error_for_status()?
            .text()?;
        Ok(self.parse_envelope(response)?.unwrap_or_default())
    }

    pub fn upload_chunk(
        &self,
        headers: &AuthHeaders,
        params: &UploadFileParams,
        chunk_path: &Path,
    ) -> Result<UploadResponse> {
        let endpoint = if headers.is_public() {
            "public/upload"
        } else {
            "upload"
        };
        let mut form = multipart::Form::new();
        for (key, value) in params.to_query_map() {
            form = form.text(key, value);
        }
        let bytes = std::fs::read(chunk_path)?;
        let part = multipart::Part::bytes(bytes).file_name(params.filename.clone());
        form = form.part("file", part);
        let response = self
            .with_headers(self.client.post(self.endpoint(endpoint)), headers)
            .multipart(form)
            .send()?
            .error_for_status()?
            .text()?;
        Ok(self.parse_envelope(response)?.unwrap_or_default())
    }

    pub fn merge(
        &self,
        headers: &AuthHeaders,
        params: &UploadFileParams,
    ) -> Result<UploadResponse> {
        let endpoint = if headers.is_public() {
            "public/merge"
        } else {
            "merge"
        };
        let response = self
            .with_headers(
                self.client
                    .post(self.endpoint(endpoint))
                    .query(&params.to_merge_map()),
                headers,
            )
            .send()?
            .error_for_status()?
            .text()?;
        Ok(self.parse_envelope(response)?.unwrap_or_default())
    }

    pub fn upload_folder(&self, headers: &AuthHeaders, params: &FolderParams) -> Result<()> {
        let endpoint = if headers.is_public() {
            "public/upload-folder"
        } else {
            "upload-folder"
        };
        let response = self
            .with_headers(
                self.client
                    .post(self.endpoint(endpoint))
                    .query(&params.to_query_map()),
                headers,
            )
            .send()?
            .error_for_status()?
            .text()?;
        let _: Option<serde_json::Value> = self.parse_envelope(response)?;
        Ok(())
    }

    pub fn new_folder(&self, headers: &AuthHeaders, params: &FolderParams) -> Result<()> {
        let response = self
            .with_headers(
                self.client
                    .post(self.endpoint("new_folder"))
                    .query(&params.to_query_map()),
                headers,
            )
            .send()?
            .error_for_status()?
            .text()?;
        let _: Option<serde_json::Value> = self.parse_envelope(response)?;
        Ok(())
    }
}

impl UploadFileParams {
    pub fn to_query_map(&self) -> BTreeMap<String, String> {
        let mut map = BTreeMap::new();
        map.insert("chunkNumber".into(), self.chunk_number.to_string());
        map.insert("chunkSize".into(), self.chunk_size.to_string());
        map.insert(
            "currentChunkSize".into(),
            self.current_chunk_size.to_string(),
        );
        map.insert("totalSize".into(), self.total_size.to_string());
        map.insert("identifier".into(), self.identifier.clone());
        map.insert("filename".into(), self.filename.clone());
        map.insert("relativePath".into(), self.relative_path.clone());
        map.insert("totalChunks".into(), self.total_chunks.to_string());
        map.insert("isFolder".into(), self.is_folder.to_string());
        map.insert("currentDirectory".into(), self.current_directory.clone());
        if let Some(value) = &self.username {
            map.insert("username".into(), value.clone());
        }
        if let Some(value) = &self.user_id {
            map.insert("userId".into(), value.clone());
        }
        if let Some(value) = &self.folder {
            map.insert("folder".into(), value.clone());
        }
        if let Some(value) = &self.file_id {
            map.insert("fileId".into(), value.clone());
        }
        if let Some(value) = self.last_modified {
            map.insert("lastModified".into(), value.to_string());
        }
        map
    }

    pub fn to_merge_map(&self) -> BTreeMap<String, String> {
        let mut map = self.to_query_map();
        map.remove("chunkNumber");
        map.remove("chunkSize");
        map.remove("currentChunkSize");
        map.remove("totalChunks");
        map
    }
}

impl FolderParams {
    pub fn to_query_map(&self) -> BTreeMap<String, String> {
        let mut map = BTreeMap::new();
        map.insert("isFolder".into(), "true".into());
        map.insert("folderPath".into(), self.folder_path.clone());
        map.insert("filename".into(), self.filename.clone());
        map.insert("currentDirectory".into(), self.current_directory.clone());
        if let Some(value) = &self.username {
            map.insert("username".into(), value.clone());
        }
        if let Some(value) = &self.user_id {
            map.insert("userId".into(), value.clone());
        }
        if let Some(value) = &self.folder {
            map.insert("folder".into(), value.clone());
        }
        if let Some(value) = &self.file_id {
            map.insert("fileId".into(), value.clone());
        }
        map
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::auth::AuthHeaders;
    use httpmock::prelude::*;
    use serde_json::json;

    #[test]
    fn login_posts_to_api_login() {
        let server = MockServer::start();
        let mock = server.mock(|when, then| {
            when.method(POST).path("/api/login").json_body(json!({
                "username": "admin",
                "password": "secret",
                "rememberMe": false,
            }));
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "jmal-token": "tok",
                    "userId": "u1"
                }
            }));
        });
        let api = JmalApiClient::new(&server.base_url()).unwrap();
        let data = api.login("admin", "secret", false).unwrap();
        mock.assert();
        assert_eq!(data.jmal_token.as_deref(), Some("tok"));
        assert_eq!(data.user_id.as_deref(), Some("u1"));
    }

    #[test]
    fn verify_totp_posts_to_public_endpoint() {
        let server = MockServer::start();
        let mock = server.mock(|when, then| {
            when.method(POST)
                .path("/api/public/verify-totp")
                .json_body(json!({
                    "username": "admin",
                    "mfaToken": "mfa",
                    "mfaCode": "123456",
                }));
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "jmal-token": "tok",
                    "userId": "u1"
                }
            }));
        });
        let api = JmalApiClient::new(&server.base_url()).unwrap();
        let data = api.verify_totp("admin", "mfa", "123456").unwrap();
        mock.assert();
        assert_eq!(data.jmal_token.as_deref(), Some("tok"));
    }

    #[test]
    fn access_token_user_info_uses_access_token_header() {
        let server = MockServer::start();
        let mock = server.mock(|when, then| {
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
        let api = JmalApiClient::new(&server.base_url()).unwrap();
        let info = api
            .user_info(&AuthHeaders::access_token("access".into()))
            .unwrap();
        mock.assert();
        assert_eq!(info.username.as_deref(), Some("admin"));
    }

    #[test]
    fn authenticated_check_exist_posts_to_private_endpoint() {
        let server = MockServer::start();
        let mock = server.mock(|when, then| {
            when.method(POST)
                .path("/api/checkExist")
                .header("jmal-token", "tok")
                .header("name", "admin");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "exist": false,
                    "upload": true
                }
            }));
        });
        let api = JmalApiClient::new(&server.base_url()).unwrap();
        let response = api
            .check_exist(
                &AuthHeaders::authenticated("admin".into(), "tok".into()),
                &["hello.txt".into()],
                "/",
                Some("admin"),
                Some("u1"),
                None,
            )
            .unwrap();
        mock.assert();
        assert!(!response.exist);
        assert!(response.upload);
    }

    #[test]
    fn public_check_exist_uses_public_endpoint_and_share_headers() {
        let server = MockServer::start();
        let mock = server.mock(|when, then| {
            when.method(POST)
                .path("/api/public/checkExist")
                .header("shareId", "sid")
                .header("share-token", "stok");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "exist": false,
                    "upload": true
                }
            }));
        });
        let api = JmalApiClient::new(&server.base_url()).unwrap();
        let response = api
            .check_exist(
                &AuthHeaders::public_share("sid".into(), "stok".into()),
                &["hello.txt".into()],
                "/",
                None,
                None,
                Some("folder"),
            )
            .unwrap();
        mock.assert();
        assert!(!response.exist);
    }

    #[test]
    fn test_chunk_uses_get_upload_and_resume_response() {
        let server = MockServer::start();
        let mock = server.mock(|when, then| {
            when.method(GET)
                .path("/api/upload")
                .query_param("identifier", "10-hellotxt")
                .query_param("chunkNumber", "1");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "pass": false,
                    "resume": [1, 3],
                    "upload": true
                }
            }));
        });
        let api = JmalApiClient::new(&server.base_url()).unwrap();
        let response = api
            .test_chunk(
                &AuthHeaders::access_token("access".into()),
                &sample_upload_params(),
            )
            .unwrap();
        mock.assert();
        assert_eq!(response.resume, vec![1, 3]);
    }

    #[test]
    fn merge_posts_to_merge_endpoint() {
        let server = MockServer::start();
        let mock = server.mock(|when, then| {
            when.method(POST)
                .path("/api/merge")
                .query_param("identifier", "10-hellotxt");
            then.status(200).json_body(json!({
                "code": 0,
                "message": "true",
                "data": {
                    "upload": true
                }
            }));
        });
        let api = JmalApiClient::new(&server.base_url()).unwrap();
        let response = api
            .merge(
                &AuthHeaders::access_token("access".into()),
                &sample_upload_params(),
            )
            .unwrap();
        mock.assert();
        assert!(response.upload);
    }

    fn sample_upload_params() -> UploadFileParams {
        UploadFileParams {
            chunk_number: 1,
            chunk_size: 1024,
            current_chunk_size: 10,
            total_size: 10,
            identifier: "10-hellotxt".into(),
            filename: "hello.txt".into(),
            relative_path: "hello.txt".into(),
            total_chunks: 1,
            is_folder: false,
            current_directory: "/".into(),
            username: Some("admin".into()),
            user_id: Some("u1".into()),
            folder: None,
            file_id: None,
            last_modified: Some(1),
        }
    }
}
