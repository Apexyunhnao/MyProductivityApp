# 久隆站本地服务器（V2）

用于 5 台左右 Android 手机之间同步：员工、配送记录、待办、价格、瓶身厂/检年份，以及后续的政策/配件/卸瓶轮值。

## 1. Windows 小主机启动

要求：安装 Python 3.10+，命令行里 `python --version` 能正常输出。

双击：

```text
server/start_server.bat
```

第一次会自动创建 `.venv` 并安装依赖。默认：

- 端口：`8000`
- 测试连接密码：`gas-station-local`
- 数据库：`server/data/gas_station.db`
- SQLite 使用 WAL 模式

浏览器打开：

```text
http://127.0.0.1:8000/health
```

看到 `"ok": true` 说明服务端已启动。

## 2. 找到小主机局域网 IP

在小主机 CMD 运行：

```bat
ipconfig
```

找到当前网卡的 IPv4，例如：

```text
192.168.1.50
```

那么手机 App 第一次启动时填写：

```text
http://192.168.1.50:8000
```

连接密码填：

```text
gas-station-local
```

先让手机和小主机连接同一个 Wi-Fi 做测试。

## 3. Windows 防火墙

第一次启动 Uvicorn/Python 时，Windows 可能弹出防火墙提示。局域网测试需要允许“专用网络”。

如果手机仍连不上，可在小主机确认：

```text
http://小主机IP:8000/health
```

在同一 Wi-Fi 的另一台设备浏览器中能否打开。

## 4. 服务端自测

服务启动后，在另一个 CMD：

```bat
cd server
.venv\Scripts\activate
python smoke_test.py
```

最后看到：

```text
smoke test: OK
```

说明增删改查正常。

## 5. 数据备份

正式使用后，至少每天备份：

```text
server/data/gas_station.db
```

最好连同 `gas_station.db-wal` 和 `gas_station.db-shm` 一起备份；更稳妥的自动备份脚本后续再加。

## 5.1 开机自启（Windows 小主机）

已配置开机自启，方式为「启动文件夹 + VBS 静默启动」，不需要管理员权限：

- 文件：`C:\Users\31619\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\GasStationV2Server.vbs`
- 行为：登录时静默调用 `server/start_server.bat`，隐藏窗口，自动使用 `.venv`、API Key `gas-station-local`、数据库 `server/data/gas_station.db`
- 取消自启：删除该 VBS 文件即可
- 验证：`http://192.168.1.2:8000/health` 返回 `{"ok": true, ...}`

若机器有多个用户或未自动登录，登录一次后即自动启动；如需要「开机未登录就启动」，改用任务计划程序（需管理员）：
`schtasks /Create /TN GasStationV2Server /TR "cmd /c cd /d C:\Users\31619\MyProductivityApp\server && start_server.bat" /SC ONSTART /RU <用户名> /RP <密码> /F`

## 5.2 端口说明

- V2 本地服务器监听 `0.0.0.0:8000`（局域网 IP `192.168.1.2:8000`）
- 本机知识库 RAG 监听 `127.0.0.1:8000`
- 两者可稳定共存：本机 `127.0.0.1:8000` 走知识库，局域网 `192.168.1.2:8000` 走 V2，互不干扰

## 6. 当前安全边界

这一版先做站内局域网测试，Android 临时允许 HTTP 明文访问局域网 IP。

**不要直接把 8000 端口映射到公网。**

以后送气员在外面需要访问时，再加 HTTPS + VPN/内网穿透，而不是直接裸露 FastAPI。

## 7. 与 CloudBase 的关系

V2 已开始把 Android Repository 从 CloudBase 类型中解耦，正式方向是：

```text
Android Room（离线）
        ↕
LocalServerClient
        ↕
FastAPI + SQLite（新站小主机）
```

CloudBase 暂时保留旧代码文件作为回退参考，但 V2 启动链路不再依赖 CloudBase 登录。
