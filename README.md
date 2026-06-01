# [JmalCloud](https://github.com/jamebal/jmal-cloud-view) 个人网盘 服务端  [查看说明](https://github.com/jamebal/jmal-cloud-view/blob/master/README.md)

## 附加文档

- [STUN 通道地址与 gost 3.x 动态节点](docs/stun-channel.md)

## jmalcloud CLI

Rust 命令行上传工具位于 `cli/jmal-cloud-cli`，二进制名为 `jmalcloud`。

安装最新发布版：

```bash
curl -fsSL https://github.com/jamebal/jmal-cloud-server/releases/latest/download/install.sh | sh
```

无法访问 GitHub 的环境，可以把对应平台的 `jmalcloud-<target>.tar.gz` 和 `install.sh`
上传到已有 JmalCloud 网盘，然后在安装时指定压缩包完整地址：

```bash
curl -fsSL "https://your-jmalcloud.example.com/path/install.sh" \
  | JMALCLOUD_CLI_ARCHIVE_URL="https://your-jmalcloud.example.com/path/jmalcloud-x86_64-unknown-linux-gnu.tar.gz" sh
```

安装脚本会在安装目录不在 `PATH` 中时自动写入当前 shell 配置文件。写入后执行提示中的
`source` 命令，或重新打开终端即可直接使用 `jmalcloud`。

本地构建：

```bash
cargo build --manifest-path cli/jmal-cloud-cli/Cargo.toml --release
```

使用用户名密码登录，2FA 用户会提示输入 TOTP：

```bash
jmalcloud login --server http://127.0.0.1:8088 --username admin
```

上传一个或多个文件、目录：

```bash
jmalcloud upload ./file.txt --server http://127.0.0.1:8088 --remote /
JMAL_CLOUD_SERVER=http://127.0.0.1:8088 jmalcloud upload ./a.txt ./b.txt --remote /
JMAL_CLOUD_SERVER=http://127.0.0.1:8088 jmalcloud upload ./dir --remote /
```

使用访问令牌上传：

```bash
jmalcloud upload ./file.txt --server http://127.0.0.1:8088 --remote / --access-token <token> --username admin
```

分享目录上传：

```bash
jmalcloud upload ./dir --server http://127.0.0.1:8088 --remote / --share-id <id> --share-token <token>
```

### 许可

[MIT](https://github.com/jamebal/jmal-cloud-view/blob/master/LICENSE) license.

Copyright (c) 2023-present jmal
