use clap::{Args, Parser, Subcommand};
use jmal_cloud_cli::api::JmalApiClient;
use jmal_cloud_cli::auth::{store_session, AuthHeaders};
use jmal_cloud_cli::config::{first_non_empty, load_config, resolve_server, EnvConfig};
use jmal_cloud_cli::errors::{CliError, Result};
use jmal_cloud_cli::upload::{UploadOptions, Uploader};
use std::path::PathBuf;

const DEFAULT_CHUNK_SIZE: u64 = 1024 * 1024;

#[derive(Debug, Parser)]
#[command(name = "jmalcloud", version, about = "Upload files to JmalCloud")]
pub struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Debug, Subcommand)]
enum Commands {
    Login(LoginArgs),
    Upload(UploadArgs),
}

#[derive(Debug, Args)]
struct LoginArgs {
    #[arg(long)]
    server: Option<String>,
    #[arg(long)]
    username: String,
    #[arg(long, default_value_t = false)]
    remember_me: bool,
}

#[derive(Debug, Args)]
struct UploadArgs {
    path: PathBuf,
    #[arg(long)]
    server: Option<String>,
    #[arg(long, default_value = "/")]
    remote: String,
    #[arg(long)]
    username: Option<String>,
    #[arg(long)]
    user_id: Option<String>,
    #[arg(long)]
    access_token: Option<String>,
    #[arg(long)]
    jmal_token: Option<String>,
    #[arg(long, default_value_t = DEFAULT_CHUNK_SIZE)]
    chunk_size: u64,
    #[arg(long, default_value_t = false)]
    overwrite: bool,
    #[arg(long)]
    share_id: Option<String>,
    #[arg(long)]
    share_token: Option<String>,
    #[arg(long, default_value_t = 3)]
    retries: u32,
    #[arg(long, default_value_t = false)]
    verbose: bool,
}

fn main() {
    if let Err(err) = run(Cli::parse()) {
        eprintln!("{err}");
        std::process::exit(1);
    }
}

fn run(cli: Cli) -> Result<()> {
    match cli.command {
        Commands::Login(args) => run_login(args),
        Commands::Upload(args) => run_upload(args),
    }
}

fn run_login(args: LoginArgs) -> Result<()> {
    let env = EnvConfig::from_env();
    let server = resolve_server(args.server.as_deref(), &env)?;
    let api = JmalApiClient::new(&server)?;
    let password = rpassword::prompt_password("Password: ")?;
    let first_attempt = api.login(&args.username, &password, args.remember_me)?;
    let session = if let Some(mfa_token) = first_attempt.mfa_token {
        let code = rpassword::prompt_password("TOTP code: ")?;
        let final_data = api.verify_totp(&args.username, &mfa_token, &code)?;
        jmal_cloud_cli::auth::session_from_login(
            api.server().to_string(),
            args.username,
            final_data,
        )?
    } else {
        jmal_cloud_cli::auth::session_from_login(
            api.server().to_string(),
            args.username,
            first_attempt,
        )?
    };
    store_session(&session)?;
    println!("logged in as {}", session.username);
    Ok(())
}

fn run_upload(args: UploadArgs) -> Result<()> {
    let env = EnvConfig::from_env();
    let stored = load_config().unwrap_or_default();
    let server = resolve_server(args.server.as_deref(), &env)?;
    let api = JmalApiClient::new(&server)?;
    let mut username = first_non_empty(&[
        args.username.clone(),
        env.username.clone(),
        stored.username.clone(),
    ]);
    let access_token = first_non_empty(&[
        args.access_token.clone(),
        env.access_token.clone(),
        stored.access_token.clone(),
    ]);
    let jmal_token = first_non_empty(&[
        args.jmal_token.clone(),
        env.jmal_token.clone(),
        stored.jmal_token.clone(),
    ]);
    let auth = if let (Some(share_id), Some(share_token)) =
        (args.share_id.clone(), args.share_token.clone())
    {
        AuthHeaders::public_share(share_id, share_token)
    } else if let Some(token) = access_token {
        AuthHeaders::access_token(token)
    } else if let (Some(name), Some(token)) = (username.clone(), jmal_token) {
        AuthHeaders::authenticated(name, token)
    } else {
        return Err(CliError::MissingAuth);
    };
    if !auth.is_public() && username.is_none() {
        let info = api.user_info(&auth)?;
        username = info.username;
    }
    let uploader = Uploader::new(api, auth);
    uploader.upload_path(UploadOptions {
        local_path: args.path,
        remote: args.remote,
        username,
        user_id: args.user_id.or(stored.user_id),
        folder: None,
        file_id: None,
        chunk_size: args.chunk_size,
        overwrite: args.overwrite,
        retries: args.retries,
        verbose: args.verbose,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use clap::CommandFactory;

    #[test]
    fn command_name_is_jmalcloud() {
        assert_eq!(Cli::command().get_name(), "jmalcloud");
    }

    #[test]
    fn exposes_login_and_upload_commands() {
        let command = Cli::command();
        let names = command
            .get_subcommands()
            .map(|subcommand| subcommand.get_name().to_string())
            .collect::<Vec<_>>();
        assert!(names.contains(&"login".to_string()));
        assert!(names.contains(&"upload".to_string()));
    }
}
