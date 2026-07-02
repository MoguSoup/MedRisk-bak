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

## MedRisk 项目知识沉淀与实习报告素材

本节用于记录截至 2026-07-01 在 MedRisk 项目协作中沉淀出的需求、架构、版本迭代和测试经验，供后续继续开发、写实习报告和复盘使用。这里记录的是项目知识和工程结论，不记录任何真实 `.env` 凭据、第三方 API 凭据、邮箱登录凭据、会话令牌或个人账号信息。

### 项目定位与技术栈

- MedRisk 是一个医疗健康风险预测与医学知识平台，面向教学演示、健康风险提示、疾病知识库、知识图谱、智能问答、模型训练和后台管理场景；页面必须持续提示“不能替代医生诊断”。
- 前端主体是 Vue 3、Vite、Element Plus，包含登录注册、用户/医生/管理员界面、疾病预测、风险大屏、知识图谱可视化、智能问答、模型训练管理、审计日志等模块。
- 后端主体是 Spring Boot 3，负责认证授权、用户管理、疾病/病历/知识文档、知识图谱任务、审计日志、邮件验证码、报告生成、LLM 配置和问答编排。
- 模型服务是 FastAPI + Python，负责风险预测、训练任务、模型版本、模型启用、公开数据集评估和可选高级模型能力。
- 本地演示使用 H2 demo profile；云端 `med_tencent` 使用 Docker Compose 编排 MySQL、Neo4j、backend、frontend、model-service。
- Neo4j 用于医学知识图谱、GraphRAG 检索和图谱可视化；本地必须连接本机 `bolt://localhost:7687`，远端 Compose 内部使用 `bolt://neo4j:7687`，不应把远端 Bolt 默认开放到公网。

### 需求与版本迭代过程

- 启动修复阶段：最早的问题是项目启动后前端登录 500，本质是后端未正常启动；本地 demo H2 库已有旧 Flyway 迁移记录，迁移文件变化后触发 checksum 校验失败。修复方向是仅在 `application-demo.yml` 对本地 H2 放宽 Flyway 校验，同时保持 Linux/MySQL 严格迁移策略，并要求本地后端用 `mvn clean spring-boot:run -Dspring-boot.run.profiles=demo` 避免旧 `target/classes` 残留。
- 本地环境阶段：Windows 本地模型服务必须固定使用 conda `MedRisk` 环境；反复强调不得回退到 `.venv`、任意 PATH Python 或宽松便携解析。启动脚本还需要幂等、后台 stdin 重定向、依赖检查和清晰的 Neo4j 缺失提示。
- Linux 部署阶段：云服务器 `med_tencent` 的稳定启动路径是 `docker compose up --build`；前端容器通过 Nginx 代理 `/api/` 到后端，远端同步不得覆盖 `.env`、数据目录、上传目录、模型目录、日志目录和 Docker 卷。
- Neo4j 阶段：修复图谱管理页才刷新连接状态的问题，把 Neo4j health 从页面局部状态提升为管理员登录后的共享状态，定时轮询并在图谱操作后刷新；本地 Neo4j 缺失时脚本要明确失败原因。
- 侧栏与布局阶段：侧栏从普通滚动布局迭代为固定高度、主内容独立滚动、可收起、可分组、可拖拽调整宽度；后来根据反馈改为 Codex 风格左上角单按钮切换展开/收起，主内容区随侧栏宽度自动填满屏幕。
- 品牌与登录阶段：登录页经历居中窗口、参考图右侧面板、浅色渐变背景、去掉重置密码标签、保留忘记密码链接和注册新用户入口等迭代。HBUT 与 ZP logo 多次调整，最终原则是透明背景、自然融入界面、无白底/黑底突兀块、ZP 图形不含内部文字或 MedRisk AI。
- 邮箱验证码阶段：注册必须使用真实邮箱验证码，忘记密码通过邮箱验证码重置。SMTP 配置只进入 gitignored `.env` 和远端 `.env`，仓库文档只写变量名和示例，不写真实凭据。163 邮箱推荐使用 `smtp.163.com:465 + SSL`、认证开启、连接和读写超时明确。
- 审计日志阶段：审计日志从只记录登录信息扩展为所有 HTTP 用户操作尽量记录请求 IP；IP 来源按 `X-Forwarded-For`、`X-Real-IP`、remote address 解析。异步流式问答需要显式传递请求 IP，避免线程上下文丢失。后续增加 IP 黑名单能力，黑名单命中应阻断请求并记录。
- 智能问答阶段：从普通问答扩展为 OpenAI-compatible LLM、阿里云百炼接入、管理员模型配置、流式输出、Markdown 安全渲染、推理过程、报告同步、多模态输入输出和自动模型选择。不得保存或使用 ChatGPT 网页会话、网页账号或浏览器 token，统一走正式 API 配置。
- 问答模式阶段：早期提供“日常聊天/医学问答”切换，后来需求调整为系统自动判断。日常聊天不检索知识库；医学问答必须检索 Neo4j、疾病档案和病历案例；包含图片时自动走视觉或全模态模型。
- 可视化阶段：风险大屏曾尝试 Three.js，随后用户确认风险大屏使用平面动态可视化；知识图谱可视化则最终保持三维，并要求显示文字、宽度不超过屏幕、标签美观不拥挤。
- 图谱构建阶段：修复全量重建和增量构建。全量重建应先清理 MedRisk 图谱，再逐文档构建；单文档失败时记录失败原因并继续处理后续文档；增量构建包含未构建和构建失败文档。非空文档应通过确定性规则生成足够多的文档相关节点，避免跨文档 `MERGE name` 导致节点过少。
- 疾病预测阶段：管理员需要拥有普通用户疾病预测能力，用于测试预测、解释和报告生成。预测界面要避免文字重叠和大片空白，可视化采用平面 SCI 风格图。后续方向是一个模型输出多疾病概率，而不是只给单一二分类结果。
- 模型训练阶段：模型体系从单一 XGBoost 扩展为多模型训练、评估、启用和重启恢复闭环。稳定默认模型包括 Logistic Regression、RandomForest、ExtraTrees、HistGradientBoosting、XGBoost；高级模型如 LightGBM、CatBoost、TabPFN、TabICL、FT-Transformer 作为可选能力，缺依赖时显示原因但不阻断基础功能。
- 公开数据评估阶段：为了让预测模型评估更可信，选用 CDC BRFSS 2024、CDC NHANES 2017-March 2020、UCI Heart Disease、UCI Chronic Kidney Disease 等公开数据源作为训练/评估来源；大型数据不提交仓库，通过脚本生成并在运行环境导入。
- 管理员功能阶段：管理员菜单需要收紧，删除重复或类似测试入口，不再重复提供患者控制台、医生控制台等冗余页面；但保留真正需要的正式能力，例如智能问答、疾病预测、知识库、知识图谱、模型训练管理、系统管理和审计日志。

### 关键模块知识

- 启动与部署：本地启动依赖 `scripts/start-all.ps1`、`scripts/stop-all.ps1`、`scripts/check-env.ps1`、`scripts/ci.ps1` 和 `scripts/medrisk-conda.ps1`；远端以 Docker Compose 为准。任何本地环境规则变化都必须同步 README 和 docs。
- 认证与邮箱：登录、注册、忘记密码、邮箱验证码和审计 IP 是一组联动功能。邮件发送失败要返回可理解的业务错误，但日志不得输出验证码、邮箱登录凭据或第三方 API 凭据。
- 知识图谱：图谱任务需要可诊断失败原因，包括 Neo4j 连接失败、清理失败、空文档、字段非法、写入失败等。前端文档列表不能只显示“构建失败”，还要能看到失败原因。
- 智能问答：后端需要同时处理本地兜底、GraphRAG 检索、OpenAI-compatible LLM、推理内容、流式 SSE、多模态图片和报告摘要。前端要即时显示发送成功，然后逐段显示推理和回答正文。
- Markdown 渲染：模型输出不能直接把 `###`、`***`、列表符号等原始标记展示给用户，前端应使用安全 Markdown 渲染，并修复样式选择器导致的徽章/文字重叠问题。
- 多模态：同一 API Key 下可配置文本模型、视觉/全模态模型和图像生成模型；后端应根据是否含图片、是否请求生成图片、问题是否医学相关自动选择合适模型。
- 风险预测：表单要有完整 placeholder 和单位提示，布尔开关不能和说明文字重叠；结果区应展示多疾病概率、关键风险因素、SCI 风格平面图和报告生成入口。
- 模型训练：训练任务需保存 `modelType`、模型专属超参数、评估数据集、评估指标和启用状态。模型服务重启后应继续加载当前启用模型，预测接口真正使用启用版本。
- 审计与 IP：所有用户操作都应尽量写入请求 IP。SSE、后台任务和无请求上下文场景要明确降级为 `-` 或显式传入 IP，避免出现同类记录缺失。
- 前端 UI：避免一栏按钮换行、避免右侧大片空白、避免文本过小和内容重叠。审计日志、表格和图谱容器应铺满可用宽度但不超过屏幕。

### 测试与验证过程

- 后端基线测试使用 `mvn test`，重点覆盖登录审计 IP、邮箱验证码、权限继承、图谱构建、问答策略、LLM profile、报告生成和模型训练转发。
- 前端单元测试使用 `npm run test`，重点覆盖管理员导航、侧栏收起/展开、问答流式渲染、Markdown 渲染、训练表单、审计 IP 展示和风险预测布局。
- 前端生产构建使用 `npm run build`，构建通过后仍需关注 Vite/Rollup 的 chunk size 和注释警告，但这些警告不一定阻断发布。
- 模型服务测试使用 conda `MedRisk` Python 执行 pytest，重点覆盖模型 capabilities、多模型 adapter、XGBoost 稳定训练、可选高级模型缺依赖提示、公开评估数据和启用模型重启加载。
- Compose 验证使用 `docker compose config --quiet` 或 `docker compose config`，确认 Linux 云端服务配置可解析。
- 远端验证路径：同步必要代码到 `med_tencent:/home/med/MedRisk`，执行 `docker compose up -d --build backend frontend`，必要时包含 `model-service`，再检查 `docker compose ps`、`http://127.0.0.1:5241/api/health` 和公网 `/api/health`。
- UI 手动验证包括登录页、侧栏、管理员菜单、智能问答、疾病预测、风险大屏、图谱可视化、模型训练管理、审计日志和报告导出。
- 邮件验证包括注册验证码和忘记密码验证码；验证时只能确认发送状态和收件结果，不得把真实邮件凭据写入仓库。
- 安全检查应扫描 AGENTS、README 和 docs，确认没有真实第三方 API 凭据、邮箱登录凭据、网页会话令牌或个人账号信息。

### 远端同步经验

- `med_tencent` 远端项目路径是 `/home/med/MedRisk`。远端可能不是 git 仓库，本地也可能没有 `rsync`，因此曾采用 zip/scp 加远端解压的方式同步。
- 同步时必须排除 `.env`、数据目录、上传目录、模型目录、日志目录、`target`、`dist`、`node_modules` 和 Docker 卷。
- 远端 `.env` 只允许定向更新必要变量，不得整体覆盖，以免破坏 MySQL、Neo4j、SMTP、LLM 等已有配置。
- 前端 Nginx 需要代理 `/api/` 到后端；后端、模型服务、Neo4j 和 MySQL 通过 Compose 服务名互联。
- 如果只改前端资源，优先只重建 frontend；如果改后端问答、审计、邮件或模型接口，需要重建 backend；如果改模型服务 adapter、训练或预测，需要重建 model-service。

### 实习报告可用素材

- 问题背景：原项目存在启动不稳定、前后端代理不完整、Neo4j 状态漂移、图谱批量构建失败、UI 滚动和重叠、问答不流式、模型训练能力不足、审计 IP 缺失等问题。
- 技术难点：Windows 本地环境和 Linux 云端环境差异大；H2/Flyway 与 MySQL/Flyway 策略不同；Neo4j 图谱构建需要批处理容错；LLM 问答需要兼顾 RAG、多模态、流式输出和安全边界；模型训练需要兼容基础模型和高级可选依赖。
- 解决方案：固化 conda 环境和启动脚本；Docker Compose 作为云端基准；共享 Neo4j health；侧栏和管理菜单重构；邮箱验证码和忘记密码闭环；审计 IP 上下文；OpenAI-compatible LLM profile；GraphRAG 与多模态自动路由；多模型 adapter 和公开数据集评估。
- 验证结果：通过后端、前端、模型服务和 Compose 配置测试，并在远端进行健康检查、登录、问答、邮件、图谱、预测和管理界面验证。
- 可继续改进：完善多疾病概率统一模型；继续优化图谱节点质量和标签布局；补齐更多公开医学数据集评估；为 IP 黑名单和 LLM profile 增加更完整的管理 UI；增加端到端测试和远端发布脚本。

### 保密与维护规则

- AGENTS、README、docs、源码、测试和提交记录中不得出现真实 `.env` 值、第三方 API 凭据、邮箱登录凭据、网页会话令牌、个人账号信息或可直接复用的远端访问凭据。
- 可以记录环境变量名，例如 `MEDRISK_LLM_BASE_URL`、`MEDRISK_LLM_API_KEY`、`MEDRISK_LLM_MODEL`、`MEDRISK_VISION_MODEL`、`MEDRISK_IMAGE_MODEL`、`MEDRISK_MAIL_HOST`、`MEDRISK_MAIL_USERNAME`、`MEDRISK_MAIL_PASSWORD`、`MEDRISK_IP_BLACKLIST`，但真实值只能放在 gitignored `.env` 或远端受控环境中。
- 如果后续继续修改启动、停止、CI、环境、部署或密钥处理规则，必须同步更新本文件和项目文档，避免同类问题反复出现。
