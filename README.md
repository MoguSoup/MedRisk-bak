# MedRisk AI 疾病风险预测与辅助报告生成系统

MedRisk AI 是面向教学实训展示的智能医疗风险预测平台，支持中文界面、多病种风险预测、风险因素解释、预测历史、管理员模型训练管理和辅助报告生成。系统仅用于教学演示和健康风险提示，不能替代医生诊断。

## 技术栈

- 前端：Vue 3、Vite、TypeScript、Element Plus、ECharts、Three.js、3d-force-graph
- 业务后端：Spring Boot 3、Spring Data JPA、MySQL、Neo4j Java Driver、JWT、Spring Mail、OpenPDF、PDFBox、Apache POI
- 模型服务：FastAPI、Pydantic、scikit-learn 多基线、XGBoost 训练/评估、可选 LightGBM/CatBoost/TabPFN/TabICL/FT-Transformer 能力入口、规则化 SHAP 风格解释基线
- 部署：Docker Compose，包含前端、后端、模型服务、MySQL、Neo4j

## 本地运行

本地脚本固定使用 conda 虚拟环境 `MedRisk`。默认兼容 `D:\IDEA\Anaconda\envs\MedRisk\python.exe`，也会在其他电脑上通过 conda 根目录、当前激活的 `MedRisk` 环境、常见 Anaconda/Miniconda 安装目录和 `conda env list` 发现同名环境。模型服务不创建、不激活 `medrisk_model_service\.venv`，也不从随机 `PATH` Python 启动。

换电脑运行前请先安装 Java 17、Maven、Node.js 20+ 和 Anaconda/Miniconda，并创建名为 `MedRisk` 的 conda 环境。若 conda 安装在非常规目录，可在当前 PowerShell 设置根目录：

```powershell
$env:MEDRISK_CONDA_ROOT = "D:\Tools\miniconda3"
.\scripts\check-env.ps1
```

智能问答和知识图谱依赖本地 Neo4j。本地 `scripts/start-all.ps1` 默认检查 `MEDRISK_NEO4J_URI=bolt://localhost:7687`，只校验本机 Bolt 连接，不安装、不拉起、不停止 Neo4j。未就绪时脚本会在启动后端前失败，并提示启动本机 Neo4j 服务或在 Docker Desktop 可用时单独启动本地 Neo4j 容器。Windows 本地演示不会自动连接 `med_tencent`，也不要求把 `med_tencent:7687` 开放到公网：

```powershell
docker run --name medrisk-neo4j -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/medrisk-neo4j -d neo4j:5.26-community
```

防复发规则：`bolt://neo4j:7687` 是 Docker Compose 内部服务名，只能用于 Linux/Compose 容器网络；Windows 本地 `.env` 和当前 PowerShell 环境应使用 `MEDRISK_NEO4J_URI=bolt://localhost:7687`。如果本地启动脚本发现 `neo4j` 服务名，会自动改用 `localhost` 并输出提示。

```powershell
.\scripts\start-all.ps1
```

该脚本使用 Spring Boot `demo` profile 和本地 H2 文件库，方便不安装 Docker 时直接演示。脚本会：

- 按需安装模型服务 Python 依赖到 conda `MedRisk` 环境，安装输出写入 `logs/model-service-install.log`，不在终端刷 `Requirement already satisfied`。
- 后台启动模型服务、后端和前端，并将日志写入 `logs/`。
- 将后台进程 stdin 重定向到 `logs/empty.stdin`，启动结束后终端应可继续输入命令。
- 等待模型服务、后端和前端健康检查通过后才输出 `MedRisk AI services are ready`；若后端未 ready，会失败并打印相关日志尾部。
- 按端口幂等启动，重复执行会复用已登记或已运行的前端 fallback 端口，不会在 5241、5484、5500 等端口重复拉起 Vite。
- 后端使用 `mvn clean spring-boot:run`，避免旧 `target/classes` 迁移文件导致 Flyway 版本冲突。
- `demo` profile 仅针对本地 H2 文件库关闭 Flyway checksum 校验，用于兼容已存在的旧演示库；Docker Compose/MySQL 部署仍按默认 Flyway 迁移规则运行。
- 前端优先使用 `5173`；若该端口被其他进程占用或被 Windows/Docker/Hyper-V 保留，脚本会自动回退到 `5241` 等可绑定端口，并在输出中显示实际访问地址。

反复启动故障记录：如果前端登录提示 500，先检查后端 `http://localhost:8080/api/health` 和 `logs/backend.out.log`。本项目曾反复出现的根因是本地 `data/medrisk-demo` H2 库保存了旧 Flyway 迁移 checksum，迁移文件更新后后端启动阶段校验失败，导致 8080 未监听。正确修复是保留 `application-demo.yml` 中的 `baseline-on-migrate: true` 和 `validate-on-migrate: false`，并继续使用 `mvn clean spring-boot:run -Dspring-boot.run.profiles=demo`；不要把脚本回退到 `.venv`、随机 `PATH` Python、旧 `Resolve-CondaPython` 或非 `clean` 的后端启动命令。该宽松校验只属于本地 H2 demo，Linux `med_tencent` Docker Compose/MySQL 不应关闭 Flyway 校验。

停止本地服务：

```powershell
.\scripts\stop-all.ps1
```

Docker Compose 路径使用 MySQL + Neo4j，并通过 `.env` 中的 `MEDRISK_NEO4J_*`、`MEDRISK_LLM_*` 配置连接图谱库和 OpenAI-compatible LLM 接口。Linux 云服务器 `med_tencent` 以该路径为准，后端连接 Compose 内部的 `bolt://neo4j:7687`；前端容器内的 Nginx 会把 `/api/` 反向代理到 Compose 服务名 `backend:8080`，浏览器访问前端端口即可完成页面和 API 调用。不推荐也不默认开放远端 Neo4j Bolt 端口到公网。

智能问答可接入 Qwen/百炼、DeepSeek 或 cockpit-tools provider gateway 等兼容 `/v1` 网关，只配置 `MEDRISK_LLM_BASE_URL`、`MEDRISK_LLM_API_KEY`、`MEDRISK_LLM_MODEL`。阿里云百炼 workspace 专属域名示例为 `https://your-workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`，模型名可使用已开通的 `qwen-plus` 等兼容模型；同一个百炼 API Key 下，图片输入会自动切到 `MEDRISK_VISION_MODEL`，打开输出图片时会调用 `MEDRISK_IMAGE_MODEL`。不要把真实 API Key、ChatGPT 网页登录邮箱、密码、cookie 或浏览器会话写入源码、文档、`.env.example` 或命令日志；真实密钥只写入本地/远端 gitignored `.env`。

智能问答后端会先做问题范围判断：只允许医疗健康、疾病风险预测、医学知识库、病历/文档、知识图谱、模型训练评估和平台使用相关问题。明显无关的问题不会调用外部模型，会直接返回平台范围提示和教学演示免责声明。

注册邮箱验证码和忘记密码邮件使用 SMTP 环境变量：`MEDRISK_MAIL_HOST`、`MEDRISK_MAIL_PORT`、`MEDRISK_MAIL_USERNAME`、`MEDRISK_MAIL_PASSWORD`、`MEDRISK_MAIL_FROM`、`MEDRISK_MAIL_SSL`。163 邮箱使用 `smtp.163.com:465`、`MEDRISK_MAIL_SSL=true`、`MEDRISK_MAIL_STARTTLS=false`，`MEDRISK_MAIL_PASSWORD` 必须填写第三方客户端授权码，不要填写网页登录密码。未配置 SMTP 时系统仍可启动，但发送验证码接口会提示邮件服务未配置。

或使用 Docker：

```powershell
docker compose up --build
```

默认访问：

- 本地脚本前端：以 `scripts/start-all.ps1` 输出为准，通常为 `http://localhost:5173`，本机 5173 被系统保留时为 `http://localhost:5241`
- Docker Compose 前端：`http://localhost:${FRONTEND_PORT:-5241}`，页面内 `/api` 由 Nginx 代理到后端容器
- 后端健康检查：`http://localhost:8080/api/health`
- 模型服务健康检查：`http://localhost:8090/health`
- Docker Compose Neo4j Browser/Bolt 默认只绑定服务器本机 `127.0.0.1:7474/7687`，供排障使用；公网访问只暴露前端端口。

## 演示账号

系统首次启动会自动创建：

- 管理员：`admin / 123456`
- 医生：`doctor / 123456`
- 患者：`patient / 123456`

## 仓库内演示数据

为保证合作者拉取私有仓库后可以直接演示，仓库允许提交根目录 `data/` 下的小型教学数据：`medrisk_sample.csv`、`medrisk_demo_cases.csv`、`medrisk_batch_template.csv` 和数据说明文件。登录账号、基础疾病和演示知识库由后端启动时幂等 seed，不需要提交本机 H2 数据库。

以下内容仍保持忽略：`medrisk_backend/data/*.db`、`uploads/`、`logs/`、大体量 `data/raw/` 与 `data/processed/*.csv`、模型二进制产物。大型公开数据集请通过 `scripts/prepare-large-datasets.ps1` 重新生成，或按团队约定使用对象存储/Git LFS。生成后的 `manifest.json` 可由管理员在“数据集管理”点击“导入公开训练数据集”注册到平台；Docker Compose 默认读取 `MEDRISK_PUBLIC_DATASET_DIR=/shared/data/processed`。

## 核心功能

- 注册、登录、退出、邮箱验证码注册、忘记密码邮件验证码重置和患者/医生/管理员角色权限分离
- 糖尿病、心脏病、慢性肾病、肝病、中风五类预测
- 风险等级、概率、置信度、Top 5 风险因素、建议和模型版本
- 预测历史、报告预览、PDF 下载
- 智能问答替换旧 AI 辅助分析，支持会话列表、多轮问答、日常聊天/医学问答双模式、可选图片上传、可选图片输出、疾病/病历/图谱上下文引用，并展示模型来源、提供方、兜底状态和证据来源；缺少 LLM API Key 或外部接口不可用时返回可演示的知识库兜底答案，医学问答中的平台外问题由 `policy-guard` 拦截且不调用外部模型
- 文档管理支持 `txt/pdf/docx` 上传、内容抽取、摘要、来源元数据、可见性标识、下载和图谱构建状态
- 中等演示数据包会幂等补齐约 50 个疾病、100 篇知识文档、50 个合成病历、5 组以上训练/评估数据集，并可由管理员重建图谱
- 图谱管理支持 Neo4j 健康检查、全量重建、增量构建、单文档同步、任务历史、实体搜索和可视化数据；管理员登录后会持续刷新 Neo4j 状态，切换页面不会丢失连接状态
- 图谱构建使用离线医学实体、文本单元、关键词和观点抽取规则，单篇非空文档会生成至少 50 个文档相关节点，增量构建会同时处理未构建和上次失败的文档
- 风险大屏使用 ECharts 平面动态图表展示风险等级、病种分布、近 7 天趋势和模型指标，保持深蓝医疗数据大屏布局
- 图谱可视化使用 3d-force-graph 的成熟 Three.js 力导向布局，支持节点文字标签、关键词、节点类型、关系类型、来源、可见性和布局切换，并提高默认展示规模以便查看批量构建结果
- 疾病信息与病历管理支持管理员维护、医生提交草稿、图片上传、来源标识和医生/管理员分级检索；患者端不直接浏览知识库管理页，仅通过智能问答使用检索结果
- 管理后台模型版本、数据集管理、训练任务、训练曲线、模型评估、模型反馈和审计日志；审计日志会记录所有 HTTP 操作来源 IP，优先读取 `X-Forwarded-For`、`X-Real-IP` 和请求远端地址。`V7` 迁移会清空旧审计日志，后续日志按新规则重新写入
- 默认预测模型登记为已部署的 XGBoost 公开数据评估基线：糖尿病和中风参考 CDC BRFSS 2024 派生数据，肝病参考 CDC NHANES 2017-March 2020 派生数据，心脏病与慢性肾病同时支持 UCI Heart Disease / UCI Chronic Kidney Disease 公开数据评估导入；管理员模型版本页会显示评估数据集、样本量和来源。
- 管理员训练成功并启用模型后，对应病种预测优先使用训练模型；启用结果会写入 `MEDRISK_ACTIVE_MODELS_FILE`，Docker Compose 默认持久化到 `/shared/models/training/active-models.json`，模型服务重启后继续加载。训练模型类型支持 `xgboost` 基线，以及可选高级依赖安装后的 `tabpfn`、`tabicl`。训练表单会按模型类型显示不同超参数：XGBoost 使用树数量、深度、学习率、采样和正则项；TabPFN 使用最大训练样本、设备和集成规模；TabICL 使用上下文大小、最大训练样本、设备和随机种子。
- 全站中文界面，集成湖北工业大学校徽与琢朴 logo

## 角色权限与演示数据

- 患者只显示疾病预测、个人历史、报告和智能问答；不能看到文档管理、疾病信息、病历管理、图谱管理、数据集、用户管理或管理员数据导入入口。个人资料通过顶部头像进入“个人信息”。
- 医生可以查看公开和医生专用知识、脱敏完整病历、只读图谱可视化，并可提交文档、疾病和病历草稿；不能发布公共知识库、删除内容或触发全量重建。
- 管理员拥有数据源/演示数据包导入、发布编辑删除、数据集、图谱构建、全量重建、用户管理和审计能力。
- 演示数据来自公开来源元数据和本地合成内容：MedlinePlus、Disease Ontology、HPO、UCI、NHANES、openFDA 链接型知识，以及 Synthea 风格合成病历。所有病历均标注为合成数据，非真实患者。

## 测试

```powershell
.\scripts\ci.ps1
```

CI 脚本会先停止本项目本地服务，避免 Vite/esbuild 锁住前端依赖目录；随后依次运行后端测试、模型服务测试、前端测试和生产构建。原生命令返回非零退出码时，CI 会直接失败。

也可以分别运行：

```powershell
cd medrisk_backend
mvn test

cd ..\medrisk_model_service
. ..\scripts\medrisk-conda.ps1
& (Resolve-MedRiskPython) -s -m pytest

cd ..\medrisk_frontend
npm install
npm run test
npm run build
```

## 开源参考与许可边界

本项目按照开发文档对下列开源项目做迁移式参考：AI-Healthcare-System、Health-Predictor---V2、PredictiX、Machine-Learning-based-Disease-Prediction-System、stroke-predictor、symptocare、XHealthAI。直接复用仅限许可证兼容的 MIT/BSD 等代码；未确认许可证的项目只参考业务流程、页面结构和交互思路。详见 [第三方引用说明](docs/第三方引用说明.md)。
