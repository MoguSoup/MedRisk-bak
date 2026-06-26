# MedRisk AI 疾病风险预测与辅助报告生成系统

MedRisk AI 是面向教学实训展示的智能医疗风险预测平台，支持中文界面、多病种风险预测、风险因素解释、预测历史、管理员模型训练管理和辅助报告生成。系统仅用于教学演示和健康风险提示，不能替代医生诊断。

## 技术栈

- 前端：Vue 3、Vite、TypeScript、Element Plus、ECharts
- 业务后端：Spring Boot 3、Spring Data JPA、MySQL、Neo4j Java Driver、JWT、OpenPDF、PDFBox、Apache POI
- 模型服务：FastAPI、Pydantic、XGBoost 训练/评估、规则化 SHAP 风格解释基线
- 部署：Docker Compose，包含前端、后端、模型服务、MySQL、Neo4j

## 本地运行

本地脚本固定使用 conda 虚拟环境 `MedRisk`。默认兼容 `D:\IDEA\Anaconda\envs\MedRisk\python.exe`，也会在其他电脑上通过 conda 根目录、当前激活的 `MedRisk` 环境、常见 Anaconda/Miniconda 安装目录和 `conda env list` 发现同名环境。模型服务不创建、不激活 `medrisk_model_service\.venv`，也不从随机 `PATH` Python 启动。

换电脑运行前请先安装 Java 17、Maven、Node.js 20+ 和 Anaconda/Miniconda，并创建名为 `MedRisk` 的 conda 环境。若 conda 安装在非常规目录，可在当前 PowerShell 设置根目录：

```powershell
$env:MEDRISK_CONDA_ROOT = "D:\Tools\miniconda3"
.\scripts\check-env.ps1
```

智能问答和知识图谱依赖外部 Neo4j。本地 `scripts/start-all.ps1` 默认检查 `MEDRISK_NEO4J_URI=bolt://localhost:7687`，只校验连接，不安装、不拉起、不停止 Neo4j。未就绪时脚本会在启动后端前失败并提示先启动外部 Neo4j。可用 Docker 单独启动本地 Neo4j：

```powershell
docker run --name medrisk-neo4j -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/medrisk-neo4j -d neo4j:5.26-community
```

```powershell
.\scripts\start-all.ps1
```

该脚本使用 Spring Boot `demo` profile 和本地 H2 文件库，方便不安装 Docker 时直接演示。脚本会：

- 按需安装模型服务 Python 依赖到 conda `MedRisk` 环境。
- 后台启动模型服务、后端和前端，并将日志写入 `logs/`。
- 将后台进程 stdin 重定向到 `logs/empty.stdin`，启动结束后终端应可继续输入命令。
- 按端口幂等启动，重复执行不会重复拉起服务。
- 后端使用 `mvn clean spring-boot:run`，避免旧 `target/classes` 迁移文件导致 Flyway 版本冲突。
- 前端优先使用 `5173`；若 Windows/Docker/Hyper-V 保留该端口，脚本会自动回退到 `5241` 等可绑定端口，并在输出中显示实际访问地址。

停止本地服务：

```powershell
.\scripts\stop-all.ps1
```

Docker Compose 路径使用 MySQL + Neo4j，并通过 `.env` 中的 `MEDRISK_NEO4J_*`、`MEDRISK_LLM_*` 配置连接图谱库和 Qwen/百炼 OpenAI-compatible 接口。

或使用 Docker：

```powershell
docker compose up --build
```

默认访问：

- 前端：以 `scripts/start-all.ps1` 输出为准，通常为 `http://localhost:5173`，本机 5173 被系统保留时为 `http://localhost:5241`
- 后端健康检查：`http://localhost:8080/api/health`
- 模型服务健康检查：`http://localhost:8090/health`

## 演示账号

系统首次启动会自动创建：

- 管理员：`admin / 123456`
- 医生：`doctor / 123456`
- 患者：`patient / 123456`

## 仓库内演示数据

为保证合作者拉取私有仓库后可以直接演示，仓库允许提交根目录 `data/` 下的小型教学数据：`medrisk_sample.csv`、`medrisk_demo_cases.csv`、`medrisk_batch_template.csv` 和数据说明文件。登录账号、基础疾病和演示知识库由后端启动时幂等 seed，不需要提交本机 H2 数据库。

以下内容仍保持忽略：`medrisk_backend/data/*.db`、`uploads/`、`logs/`、大体量 `data/raw/` 与 `data/processed/*.csv`、模型二进制产物。大型公开数据集请通过 `scripts/prepare-large-datasets.ps1` 重新生成，或按团队约定使用对象存储/Git LFS。

## 核心功能

- 注册、登录、退出和患者/医生/管理员角色权限分离
- 糖尿病、心脏病、慢性肾病、肝病、中风五类预测
- 风险等级、概率、置信度、Top 5 风险因素、建议和模型版本
- 预测历史、报告预览、PDF 下载
- 智能问答替换旧 AI 辅助分析，支持会话列表、多轮问答、可选图片上传、疾病/病历/图谱上下文引用；缺少 LLM API Key 时返回可演示的知识库兜底答案
- 文档管理支持 `txt/pdf/docx` 上传、内容抽取、摘要、来源元数据、可见性标识、下载和图谱构建状态
- 中等演示数据包会幂等补齐约 50 个疾病、100 篇知识文档、50 个合成病历、5 组以上训练/评估数据集，并可由管理员重建图谱
- 图谱管理支持 Neo4j 健康检查、全量重建、增量构建、单文档同步、任务历史、实体搜索和可视化数据
- 图谱可视化使用 ECharts graph，支持关键词、节点类型、关系类型、来源、可见性和布局切换
- 疾病信息与病历管理支持管理员维护、医生提交草稿、图片上传、来源标识和医生/管理员分级检索；患者端不直接浏览知识库管理页，仅通过智能问答使用检索结果
- 管理后台模型版本、数据集管理、训练任务、训练曲线、模型评估、模型反馈和审计日志
- 管理员训练成功并启用模型后，对应病种预测优先使用训练模型；未启用训练模型时使用教学基线
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
