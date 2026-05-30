use clap::{ArgAction, Args, CommandFactory, Parser, Subcommand};
use jmal_cloud_cli::api::JmalApiClient;
use jmal_cloud_cli::auth::{store_session, AuthHeaders};
use jmal_cloud_cli::config::{first_non_empty, load_config, resolve_server, EnvConfig};
use jmal_cloud_cli::errors::{CliError, Result};
use jmal_cloud_cli::upload::{UploadOptions, Uploader};
use std::path::{Path, PathBuf};

const DEFAULT_CHUNK_SIZE: u64 = 1024 * 1024;
const ROOT_HELP_TEMPLATE: &str = r#"{about-with-newline}
用法: {usage}

命令:
{subcommands}

选项:
{options}{after-help}
"#;
const COMMAND_HELP_TEMPLATE: &str = r#"{about-with-newline}
用法: {usage}

选项:
{options}{after-help}
"#;
const COMMAND_WITH_ARGS_HELP_TEMPLATE: &str = r#"{about-with-newline}
用法: {usage}

参数:
{positionals}

选项:
{options}{after-help}
"#;
const HELP_EXAMPLES: &str = r#"常用流程:
  使用用户名、密码登录；如果服务端要求 2FA，会继续提示输入动态验证码:
    jmalcloud login --server http://127.0.0.1:8088 --username jmal

  登录后上传一个文件，使用 JMAL_CLOUD_SERVER 代替 --server:
    JMAL_CLOUD_SERVER=http://127.0.0.1:8088 jmalcloud upload ./file.txt --remote /

  登录后上传一个文件夹:
    JMAL_CLOUD_SERVER=http://127.0.0.1:8088 jmalcloud upload ./dir --remote /

  使用 access token 上传:
    jmalcloud upload ./file.txt --server http://127.0.0.1:8088 --remote / --access-token <token> --username jmal

  上传到公开分享目录:
    jmalcloud upload ./dir --server http://127.0.0.1:8088 --remote / --share-id <id> --share-token <token>

认证:
  login 会把服务地址、用户名、用户 ID 和 jmal-token 保存到本机 jmalcloud 配置文件。
  upload 需要满足以下任一认证方式：已登录配置、--access-token，或 --share-id 搭配 --share-token。

环境变量:
  JMAL_CLOUD_SERVER        未传 --server 时使用的服务地址
  JMAL_CLOUD_USERNAME      未传 --username 时使用的用户名
  JMAL_CLOUD_ACCESS_TOKEN  未传 --access-token 时使用的 access token
  JMAL_CLOUD_JMAL_TOKEN    未传 --jmal-token 时使用的 jmal-token
"#;

#[derive(Debug, Parser)]
#[command(
    name = "jmalcloud",
    version,
    about = "上传文件和文件夹到 JmalCloud",
    after_help = HELP_EXAMPLES,
    help_template = ROOT_HELP_TEMPLATE,
    subcommand_value_name = "命令",
    disable_help_flag = true,
    disable_version_flag = true,
    disable_help_subcommand = true
)]
pub struct Cli {
    #[arg(
        short = 'h',
        long = "help",
        action = ArgAction::Help,
        help = "显示帮助信息"
    )]
    _help: Option<bool>,
    #[arg(
        short = 'V',
        long = "version",
        action = ArgAction::Version,
        help = "显示版本信息"
    )]
    _version: Option<bool>,
    #[command(subcommand)]
    command: Commands,
}

#[derive(Debug, Subcommand)]
enum Commands {
    #[command(
        about = "使用用户名、密码登录，支持 2FA 动态验证码",
        help_template = COMMAND_HELP_TEMPLATE,
        override_usage = "jmalcloud login [选项] --username <用户名>",
        disable_help_flag = true
    )]
    Login(LoginArgs),
    #[command(
        about = "上传本地文件或文件夹",
        help_template = COMMAND_WITH_ARGS_HELP_TEMPLATE,
        override_usage = "jmalcloud upload [选项] <路径>",
        disable_help_flag = true
    )]
    Upload(UploadArgs),
    #[command(
        about = "显示帮助信息",
        help_template = COMMAND_WITH_ARGS_HELP_TEMPLATE,
        override_usage = "jmalcloud help [命令]",
        disable_help_flag = true
    )]
    Help(HelpArgs),
}

#[derive(Debug, Args)]
struct HelpFlag {
    #[arg(
        short = 'h',
        long = "help",
        action = ArgAction::Help,
        help = "显示帮助信息"
    )]
    _help: Option<bool>,
}

#[derive(Debug, Args)]
struct LoginArgs {
    #[command(flatten)]
    _help: HelpFlag,
    #[arg(
        long,
        value_name = "服务地址",
        help = "JmalCloud 服务地址，例如 http://127.0.0.1:8088"
    )]
    server: Option<String>,
    #[arg(long, value_name = "用户名", help = "JmalCloud 用户名")]
    username: String,
    #[arg(
        long,
        default_value_t = false,
        help = "请求服务端返回更长有效期的登录会话"
    )]
    remember_me: bool,
}

#[derive(Debug, Args)]
struct UploadArgs {
    #[command(flatten)]
    _help: HelpFlag,
    #[arg(value_name = "路径", help = "本地要上传的文件或文件夹")]
    path: PathBuf,
    #[arg(
        long,
        value_name = "服务地址",
        help = "JmalCloud 服务地址；也可以通过 JMAL_CLOUD_SERVER 设置"
    )]
    server: Option<String>,
    #[arg(
        long,
        default_value = "/",
        hide_default_value = true,
        value_name = "远端目录",
        help = "远端目标目录，默认 /，例如 / 或 /Documents"
    )]
    remote: String,
    #[arg(
        long,
        value_name = "用户名",
        help = "JmalCloud 用户名；使用 --jmal-token 时必填，使用 --access-token 时可辅助识别用户"
    )]
    username: Option<String>,
    #[arg(
        long,
        value_name = "用户ID",
        help = "JmalCloud 用户 ID；通常会通过 /api/user/info 自动获取"
    )]
    user_id: Option<String>,
    #[arg(
        long,
        value_name = "TOKEN",
        help = "在 JmalCloud 用户设置中创建的 access token"
    )]
    access_token: Option<String>,
    #[arg(
        long,
        value_name = "TOKEN",
        help = "登录接口返回的短期 jmal-token；通常从本地登录配置读取"
    )]
    jmal_token: Option<String>,
    #[arg(
        long,
        default_value_t = DEFAULT_CHUNK_SIZE,
        hide_default_value = true,
        value_name = "字节数",
        help = "分片上传的单片大小，单位为字节，默认 1048576"
    )]
    chunk_size: u64,
    #[arg(
        long,
        default_value_t = false,
        help = "远端已存在同名文件或文件夹时继续上传"
    )]
    overwrite: bool,
    #[arg(long, value_name = "分享ID", help = "公开分享上传接口使用的分享 ID")]
    share_id: Option<String>,
    #[arg(
        long,
        value_name = "分享TOKEN",
        help = "公开分享上传接口使用的分享 token"
    )]
    share_token: Option<String>,
    #[arg(
        long,
        default_value_t = 3,
        hide_default_value = true,
        value_name = "次数",
        help = "分片上传遇到临时失败时的重试次数，默认 3"
    )]
    retries: u32,
    #[arg(long, default_value_t = false, help = "输出创建远端文件夹的详细信息")]
    verbose: bool,
}

#[derive(Debug, Args)]
struct HelpArgs {
    #[command(flatten)]
    _help: HelpFlag,
    #[arg(
        value_name = "命令",
        help = "可选，要查看帮助的命令：login、upload 或 help"
    )]
    command: Option<String>,
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
        Commands::Help(args) => run_help(args),
    }
}

fn run_help(args: HelpArgs) -> Result<()> {
    let mut command = Cli::command();
    if let Some(topic) = args.command {
        let subcommand = command
            .find_subcommand_mut(&topic)
            .ok_or_else(|| CliError::InvalidInput(format!("未知帮助主题: {topic}")))?;
        subcommand.write_long_help(&mut std::io::stdout())?;
    } else {
        command.write_long_help(&mut std::io::stdout())?;
    }
    println!();
    Ok(())
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
    let local_path = args.path.clone();
    let remote = args.remote.clone();
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
    })?;
    println!("{}", upload_success_message(&local_path, &remote));
    Ok(())
}

fn upload_success_message(path: &Path, remote: &str) -> String {
    format!("Upload completed: {} -> {}", path.display(), remote)
}

#[cfg(test)]
mod tests {
    use super::*;
    use clap::CommandFactory;
    use std::path::Path;

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

    #[test]
    fn top_level_help_is_chinese() {
        let mut command = Cli::command();
        let mut buffer = Vec::new();
        command.write_long_help(&mut buffer).unwrap();
        let help = String::from_utf8(buffer).unwrap();

        assert!(help.contains("常用流程:"));
        assert!(help.contains("用法: jmalcloud"));
        assert!(help.contains("命令:"));
        assert!(help.contains("选项:"));
        assert!(help.contains("jmalcloud login --server http://127.0.0.1:8088 --username jmal"));
        assert!(help.contains("JMAL_CLOUD_SERVER=http://127.0.0.1:8088"));
        assert!(help.contains("jmalcloud upload ./file.txt --remote /"));
        assert!(help.contains("JMAL_CLOUD_ACCESS_TOKEN"));
        assert!(help.contains("显示帮助信息"));
        assert!(!help.contains("[OPTIONS]"));
        assert!(!help.contains("Common flows:"));
        assert!(!help.contains("Usage:"));
        assert!(!help.contains("Commands:"));
        assert!(!help.contains("Options:"));
        assert!(!help.contains("Print help"));
    }

    #[test]
    fn upload_help_is_chinese() {
        let mut command = Cli::command();
        let upload = command.find_subcommand_mut("upload").unwrap();
        let mut buffer = Vec::new();
        upload.write_long_help(&mut buffer).unwrap();
        let help = String::from_utf8(buffer).unwrap();

        assert!(help.contains("上传本地文件或文件夹"));
        assert!(help.contains("参数:"));
        assert!(help.contains("本地要上传的文件或文件夹"));
        assert!(help.contains("JmalCloud 服务地址"));
        assert!(help.contains("远端目标目录"));
        assert!(help.contains("显示帮助信息"));
        assert!(!help.contains("[OPTIONS]"));
        assert!(!help.contains("[default:"));
        assert!(!help.contains("Upload a local file or folder"));
        assert!(!help.contains("Usage:"));
        assert!(!help.contains("Arguments:"));
        assert!(!help.contains("Options:"));
    }

    #[test]
    fn login_help_is_chinese() {
        let mut command = Cli::command();
        let login = command.find_subcommand_mut("login").unwrap();
        let mut buffer = Vec::new();
        login.write_long_help(&mut buffer).unwrap();
        let help = String::from_utf8(buffer).unwrap();

        assert!(help.contains("使用用户名、密码登录"));
        assert!(help.contains("JmalCloud 服务地址"));
        assert!(help.contains("JmalCloud 用户名"));
        assert!(help.contains("显示帮助信息"));
        assert!(!help.contains("[OPTIONS]"));
        assert!(!help.contains("Login with username/password"));
        assert!(!help.contains("Usage:"));
        assert!(!help.contains("Options:"));
    }

    #[test]
    fn upload_success_message_names_path_and_remote() {
        let message = upload_success_message(Path::new("/tmp/file.txt"), "/Documents");

        assert!(message.contains("Upload completed"));
        assert!(message.contains("/tmp/file.txt"));
        assert!(message.contains("/Documents"));
    }
}
