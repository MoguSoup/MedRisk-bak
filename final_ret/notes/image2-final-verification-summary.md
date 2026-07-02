# MedRisk 生产实习报告 image2 图文增强终版验证摘要

- 最终 DOCX：`final_ret/唐俊_2026计科生产实习报告册_MedRisk图文增强终版.docx`
- 原始模板：`d:\湖北工业大学作业\6.大三（下）\生产实习\最终实践项目\提交材料\唐俊_2026计科生产实习报告册.doc`，本次未修改原件。
- image2 图目录：`final_ret/image2_diagrams/labeled/`
- 远端截图目录：`final_ret/screenshots_image2_final/`
- 渲染检查目录：`final_ret/render_check_image2_final/`

## 内容检查

- 图题数量：26 个，覆盖功能框图、总体架构图、项目流程图、知识图谱流程图、智能问答时序图、疾病预测 UML、模型训练思维导图、测试覆盖矩阵和远端页面截图。
- 参考文献数量：12 条，其中包含 Deep Learning、Patterns of Enterprise Application Architecture、Spring in Action 等专业书籍。
- 实习体会长度：约 1495 字，覆盖技术成长、问题定位、理论联系实践、前端体验、工程伦理和后续改进。
- 正文时间线：2026.6.15 实习动员，2026.6.16 到 2026.6.22 理论学习，2026.6.23 到 2026.7.3 项目开发与管理，2026.7.4 到 2026.7.5 报告整理与答辩准备。

## 远端实测

- 公网入口 `http://81.71.65.98:80` 返回 200。
- 健康检查 `http://81.71.65.98:80/api/health` 返回 `status: UP`。
- 管理员登录成功后截图包含管理控制台、疾病预测、智能问答、知识图谱、模型训练管理和审计日志。
- 异常登录截图包含 401 失败提示。
- 审计日志截图显示登录、问答、预测等记录带请求 IP。

## 渲染 QA

- LibreOffice 转换 PDF 成功，输出 36 页。
- Poppler 渲染逐页 PNG 成功。
- 联系表：`final_ret/render_check_image2_final/contact_sheet.png`
- 抽查图文密集页、测试表页和报告后续评分表页面，未发现图片溢出、文字重叠、表格错位或大块异常空白。

## 保密检查

- 最终 DOCX 正文扫描未发现真实 API Key、邮箱访问凭据、access token、refresh token 或明文密码。
- notes 和脚本中的密钥相关文字仅用于保密规则说明，不包含真实值。
