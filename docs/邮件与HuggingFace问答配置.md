# 邮件验证码与 Hugging Face 问答模型配置

## 163 SMTP 邮件验证码

注册验证码和忘记密码验证码使用 Spring Mail 发送，运行环境通过 `.env` 配置 SMTP。授权码属于秘密，只能写入本地或服务器 `.env`，不能写入 README、文档、源码或提交记录。

163 邮箱推荐配置：

```env
MEDRISK_MAIL_HOST=smtp.163.com
MEDRISK_MAIL_PORT=465
MEDRISK_MAIL_USERNAME=your-email@163.com
MEDRISK_MAIL_PASSWORD=your-smtp-authorization-code
MEDRISK_MAIL_FROM=your-email@163.com
MEDRISK_MAIL_SSL=true
MEDRISK_MAIL_STARTTLS=false
MEDRISK_MAIL_AUTH=true
MEDRISK_MAIL_AUTH_MECHANISMS=LOGIN
MEDRISK_MAIL_SSL_TRUST=smtp.163.com
MEDRISK_MAIL_CONNECTION_TIMEOUT_MS=10000
MEDRISK_MAIL_READ_TIMEOUT_MS=10000
MEDRISK_MAIL_WRITE_TIMEOUT_MS=10000
```

如果验证码接口返回邮件配置错误，先检查当前后端容器或本地后端进程是否读取到了上述环境变量，再查看后端日志中的 SMTP 连接、认证或超时错误。

## Hugging Face 本地权重问答

智能问答默认仍可使用 OpenAI-compatible 接口或本地兜底答案。需要使用 Hugging Face 已训练好的模型权重时，启用可选模型服务能力：

```env
MEDRISK_HF_LLM_ENABLED=true
MEDRISK_HF_QA_MODEL=your-huggingface-model-id-or-local-path
MEDRISK_HF_QA_DEVICE=auto
MEDRISK_HF_QA_MAX_NEW_TOKENS=512
MEDRISK_HF_QA_TRUST_REMOTE_CODE=false
```

高级依赖不放入默认最小启动依赖。启用本地权重前，需要在模型服务环境中安装：

```powershell
pip install -r medrisk_model_service/requirements-advanced.txt
```

模型服务会懒加载 Hugging Face `transformers` text-generation pipeline。未配置模型、缺少依赖、显存不足或加载失败时，`/qa/capabilities` 会返回不可用原因；后端会继续使用 OpenAI-compatible 或本地兜底，不阻断智能问答发送。

## 阿里云百炼与 OpenAI-compatible 网关

阿里云百炼 workspace 专属域名可以直接作为 OpenAI-compatible Chat Completions 地址。只在本地或服务器 `.env` 写入真实 API Key，文档和示例文件只保留占位：

```env
MEDRISK_LLM_BASE_URL=https://your-workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
MEDRISK_LLM_API_KEY=replace-with-bailian-api-key
MEDRISK_LLM_MODEL=qwen-plus
MEDRISK_HTTP_CONNECT_TIMEOUT_MS=5000
MEDRISK_HTTP_READ_TIMEOUT_MS=20000
```

MedRisk 会在调用外部模型前做平台范围判断。医疗健康、疾病风险预测、医学知识库、病历/文档、知识图谱、模型训练评估和平台使用相关问题才会进入检索与 LLM；明显无关问题返回 `usedModel=policy-guard`、`provider=medrisk-policy`，并且不会调用百炼或其他外部模型。

云服务器可以接入 cockpit-tools v0.26.5 这类 provider gateway/反向代理网关。MedRisk 后端只需要标准兼容接口配置：

```env
MEDRISK_LLM_BASE_URL=http://127.0.0.1:8787/v1
MEDRISK_LLM_API_KEY=replace-with-gateway-token
MEDRISK_LLM_MODEL=replace-with-gateway-model
MEDRISK_HTTP_CONNECT_TIMEOUT_MS=5000
MEDRISK_HTTP_READ_TIMEOUT_MS=20000
```

网关如果和 Docker Compose 在同一台云服务器，优先使用内网或本机地址；如果网关部署在其他机器，必须使用 HTTPS 或受限内网隧道。不要把 ChatGPT 网页登录邮箱、密码、cookie、refresh token 或浏览器会话写入项目配置；MedRisk 只消费网关 token。

## 开源发布边界

本文件只能保留环境变量名、占位符和配置方法。公开仓库不得包含真实 SMTP 授权码、Hugging Face token、百炼/DeepSeek/OpenAI-compatible API Key、provider gateway token、ChatGPT 网页账号、cookie、refresh token、浏览器会话或 AI 工具使用记录。发布前请按 `docs/开源发布清单.md` 扫描公开文件。

参考依据：

- Three.js examples: https://threejs.org/examples/
- 3d-force-graph: https://github.com/vasturiano/3d-force-graph
- Hugging Face Transformers pipeline: https://huggingface.co/docs/transformers/en/main_classes/pipelines
- Spring Boot Email: https://docs.spring.io/spring-boot/reference/io/email.html
- cockpit-tools release v0.26.5: https://github.com/jlcodes99/cockpit-tools/releases/tag/v0.26.5
