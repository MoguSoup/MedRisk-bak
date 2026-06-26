# AGENTS.md

## MedRisk 本地环境规则

重要：不要把本地模型服务环境改回 `.venv`、随机 `PATH` Python、`MEDRISK_CONDA_ENV` 可选默认，或抽象成 `portable-env.ps1` 一类的宽松便携化解析。这个项目在 Windows 本地开发时必须默认使用 conda 的 `MedRisk` 环境。

```text
D:\IDEA\Anaconda\envs\MedRisk\python.exe
```

为支持换电脑运行，脚本可以通过 `scripts/medrisk-conda.ps1` 严格发现名为 `MedRisk` 的 conda 环境：优先显式 `MEDRISK_CONDA_ROOT`、当前激活的 `CONDA_PREFIX=...\MedRisk`、常见 Anaconda/Miniconda 安装目录、以及 `conda env list` 中名为 `MedRisk` 的环境。禁止退回裸 `python` 或任意 PATH 解释器。

环境修复仅限脚本、配置说明和文档，不得修改具体业务功能代码逻辑。

## 启动脚本约束

- `scripts/start-all.ps1` 必须固定解析 conda `MedRisk` Python，找不到应直接失败并给出创建/配置提示。
- 不创建、不激活 `medrisk_model_service\.venv`。
- 后台进程 stdin 必须重定向到 `logs/empty.stdin`，保证脚本结束后当前终端仍可输入命令。
- 启动必须幂等：重复运行不得重复拉起前端、后端或模型服务。
- 后端本地启动必须使用 `mvn clean spring-boot:run -Dspring-boot.run.profiles=demo`，避免旧 `target/classes` 中的 Flyway 迁移残留。
- 前端启动前必须检查 `vite`、`@vitejs/plugin-vue`、`jsdom`、`vue` 等依赖，不完整时执行 `npm install`。
- `scripts/stop-all.ps1` 必须清理 5173、8080、8090 端口和本项目目录下残留的 Vite/esbuild、Maven/Spring Boot、Uvicorn 相关进程。
- `scripts/ci.ps1` 必须先停止本项目服务，再用 conda `MedRisk` 执行模型服务测试，并正确处理原生命令非零退出码。

## 文档同步规则

如果修改本地启动、停止、CI 或环境规则，必须同步更新 README、`docs/部署说明.md`、`docs/测试计划.md`、`docs/模型说明.md`、`docs/接口说明.md`，并确保文档不会把默认环境描述成 `.venv`、随机 `PATH` Python、`MEDRISK_CONDA_ENV` 或不受控的便携环境。

## 完成提示

每次准备停止回复并等待用户下一步输入前，必须先播放明显提示音：重复蜂鸣，并用 Windows 语音播报 “Codex task complete. Please check the result.”
