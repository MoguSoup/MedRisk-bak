# -*- coding: utf-8 -*-
from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(r"D:\CScode\code\aaa_production\MedRisk")
RAW = ROOT / "final_ret" / "image2_diagrams" / "raw"
OUT = ROOT / "final_ret" / "image2_diagrams" / "labeled"
NOTES = ROOT / "final_ret" / "notes"


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\simhei.ttf",
        r"C:\Windows\Fonts\simsun.ttc",
    ]
    for path in candidates:
        if path and Path(path).exists():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()


def box(draw: ImageDraw.ImageDraw, xy, fill=(255, 255, 255, 226), outline=(52, 132, 176, 255), width=2, radius=18):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def text_center(draw: ImageDraw.ImageDraw, xy, text: str, size=34, fill=(18, 52, 82), bold=True):
    f = font(size, bold)
    lines = text.split("\n")
    spacing = int(size * 0.22)
    heights = []
    widths = []
    for line in lines:
        b = draw.textbbox((0, 0), line, font=f)
        widths.append(b[2] - b[0])
        heights.append(b[3] - b[1])
    total_h = sum(heights) + spacing * (len(lines) - 1)
    y = xy[1] + (xy[3] - xy[1] - total_h) / 2
    for idx, line in enumerate(lines):
        x = xy[0] + (xy[2] - xy[0] - widths[idx]) / 2
        draw.text((x, y), line, font=f, fill=fill)
        y += heights[idx] + spacing


def label(draw, x, y, w, h, text, size=30, accent=(52, 132, 176), fill=(255, 255, 255, 230)):
    xy = (x, y, x + w, y + h)
    box(draw, xy, fill=fill, outline=accent, width=2, radius=14)
    text_center(draw, xy, text, size=size, bold=True)


def rel(size, x, y, w, h):
    W, H = size
    return int(W * x), int(H * y), int(W * w), int(H * h)


def title(draw, size, text):
    W, H = size
    box(draw, (int(W * 0.03), int(H * 0.025), int(W * 0.97), int(H * 0.10)), fill=(220, 240, 248, 235), outline=(220, 240, 248, 235), radius=0)
    text_center(draw, (int(W * 0.03), int(H * 0.025), int(W * 0.97), int(H * 0.10)), text, size=36, fill=(10, 48, 82), bold=True)


def annotate_01(path: Path, out: Path):
    img = Image.open(path).convert("RGBA")
    d = ImageDraw.Draw(img)
    title(d, img.size, "MedRisk 系统功能框图")
    labels = [
        (0.40, 0.36, 0.20, 0.16, "MedRisk AI\n教学演示平台"),
        (0.08, 0.18, 0.20, 0.11, "用户认证\n邮箱验证码"),
        (0.08, 0.39, 0.20, 0.11, "疾病预测\n报告生成"),
        (0.08, 0.60, 0.20, 0.11, "风险大屏\n可视化展示"),
        (0.72, 0.18, 0.20, 0.11, "知识库管理\n文档维护"),
        (0.72, 0.39, 0.20, 0.11, "智能问答\n多模态输入"),
        (0.72, 0.60, 0.20, 0.11, "模型训练\n评估启用"),
        (0.18, 0.78, 0.21, 0.10, "审计日志\n请求 IP"),
        (0.61, 0.78, 0.21, 0.10, "云端部署\nDocker Compose"),
    ]
    for args in labels:
        label(d, *rel(img.size, *args[:4]), args[4], size=26)
    img.convert("RGB").save(out)


def annotate_02(path: Path, out: Path):
    img = Image.open(path).convert("RGBA")
    d = ImageDraw.Draw(img)
    title(d, img.size, "系统总体架构设计")
    labels = [
        (0.18, 0.12, 0.20, 0.06, "浏览器端 Vue3"),
        (0.40, 0.12, 0.20, 0.06, "Nginx / Vite 代理"),
        (0.62, 0.12, 0.20, 0.06, "Element Plus 界面"),
        (0.18, 0.30, 0.22, 0.07, "Spring Boot 后端"),
        (0.44, 0.30, 0.20, 0.07, "认证 权限 审计"),
        (0.68, 0.30, 0.20, 0.07, "报告与问答接口"),
        (0.18, 0.49, 0.22, 0.07, "FastAPI 模型服务"),
        (0.44, 0.49, 0.20, 0.07, "训练 评估 预测"),
        (0.68, 0.49, 0.20, 0.07, "多模型 Adapter"),
        (0.18, 0.68, 0.20, 0.07, "MySQL / H2"),
        (0.42, 0.68, 0.20, 0.07, "Neo4j 知识图谱"),
        (0.66, 0.68, 0.20, 0.07, "百炼大模型接口"),
        (0.58, 0.84, 0.30, 0.07, "med_tencent 云服务器"),
    ]
    for args in labels:
        label(d, *rel(img.size, *args[:4]), args[4], size=24, accent=(55, 126, 184))
    img.convert("RGB").save(out)


def annotate_03(path: Path, out: Path):
    img = Image.open(path).convert("RGBA")
    d = ImageDraw.Draw(img)
    title(d, img.size, "生产实习项目流程图")
    labels = [
        (0.03, 0.16, 0.12, 0.10, "实习动员\n6.15"),
        (0.20, 0.16, 0.14, 0.10, "理论学习\n6.16-6.22"),
        (0.39, 0.16, 0.14, 0.10, "需求分析\n6.23"),
        (0.58, 0.16, 0.14, 0.10, "系统设计\n6.24"),
        (0.77, 0.16, 0.14, 0.10, "编码实现\n6.25-6.30"),
        (0.16, 0.45, 0.15, 0.10, "联调测试\n7.1"),
        (0.37, 0.45, 0.15, 0.10, "远端部署\n7.2-7.3"),
        (0.58, 0.45, 0.15, 0.10, "报告整理\n7.4"),
        (0.78, 0.45, 0.15, 0.10, "实习答辩\n7.5"),
    ]
    for args in labels:
        label(d, *rel(img.size, *args[:4]), args[4], size=24, accent=(92, 164, 194))
    img.convert("RGB").save(out)


def annotate_04(path: Path, out: Path):
    img = Image.open(path).convert("RGBA")
    d = ImageDraw.Draw(img)
    title(d, img.size, "知识图谱全量构建流程")
    labels = [
        (0.05, 0.32, 0.13, 0.12, "清理旧图谱"),
        (0.22, 0.32, 0.13, 0.12, "读取全部文档"),
        (0.39, 0.32, 0.13, 0.12, "实体抽取"),
        (0.56, 0.32, 0.13, 0.12, "关系抽取"),
        (0.73, 0.32, 0.13, 0.12, "写入 Neo4j"),
        (0.40, 0.62, 0.18, 0.11, "失败原因记录"),
        (0.68, 0.62, 0.18, 0.11, "三维图谱展示"),
    ]
    for args in labels:
        color = (210, 86, 86) if "失败" in args[4] else (52, 132, 176)
        label(d, *rel(img.size, *args[:4]), args[4], size=24, accent=color)
    img.convert("RGB").save(out)


def annotate_05(path: Path, out: Path):
    img = Image.open(path).convert("RGBA")
    d = ImageDraw.Draw(img)
    title(d, img.size, "疾病风险预测 UML 活动图")
    labels = [
        (0.39, 0.10, 0.22, 0.06, "开始"),
        (0.36, 0.21, 0.28, 0.07, "填写健康指标"),
        (0.36, 0.33, 0.28, 0.07, "后端参数校验"),
        (0.36, 0.47, 0.28, 0.08, "调用多分类模型"),
        (0.36, 0.61, 0.28, 0.08, "输出各疾病概率"),
        (0.15, 0.46, 0.18, 0.08, "缺失项提示"),
        (0.67, 0.61, 0.20, 0.08, "因素解释"),
        (0.36, 0.76, 0.28, 0.07, "生成报告"),
        (0.39, 0.88, 0.22, 0.06, "结束"),
    ]
    for args in labels:
        color = (205, 83, 83) if "缺失" in args[4] else (78, 120, 190)
        label(d, *rel(img.size, *args[:4]), args[4], size=23, accent=color)
    img.convert("RGB").save(out)


def annotate_06(path: Path, out: Path):
    img = Image.open(path).convert("RGBA")
    d = ImageDraw.Draw(img)
    title(d, img.size, "智能问答与多模态时序图")
    labels = [
        (0.04, 0.11, 0.11, 0.06, "用户"),
        (0.20, 0.11, 0.12, 0.06, "前端"),
        (0.37, 0.11, 0.12, 0.06, "后端"),
        (0.54, 0.11, 0.12, 0.06, "检索服务"),
        (0.70, 0.11, 0.12, 0.06, "大模型"),
        (0.85, 0.11, 0.12, 0.06, "审计报告"),
        (0.10, 0.30, 0.24, 0.06, "文本或图片输入"),
        (0.33, 0.42, 0.24, 0.06, "自动判断问答类型"),
        (0.50, 0.54, 0.24, 0.06, "医学问题检索证据"),
        (0.61, 0.68, 0.24, 0.06, "选择合适百炼模型"),
        (0.25, 0.82, 0.32, 0.06, "推理与答案流式返回"),
    ]
    for args in labels:
        label(d, *rel(img.size, *args[:4]), args[4], size=22, accent=(94, 116, 188))
    img.convert("RGB").save(out)


def annotate_07(path: Path, out: Path):
    img = Image.open(path).convert("RGBA")
    d = ImageDraw.Draw(img)
    title(d, img.size, "模型训练与评估思维导图")
    labels = [
        (0.42, 0.38, 0.18, 0.12, "风险预测\n模型训练"),
        (0.08, 0.16, 0.18, 0.09, "公开数据集\nCDC UCI"),
        (0.08, 0.46, 0.18, 0.09, "经典模型\nLR RF XGBoost"),
        (0.08, 0.72, 0.18, 0.09, "高级模型\nTabPFN TabICL"),
        (0.72, 0.16, 0.18, 0.09, "超参数\n按模型区分"),
        (0.72, 0.46, 0.18, 0.09, "评估指标\nAUC F1 Brier"),
        (0.72, 0.72, 0.18, 0.09, "启用部署\n重启加载"),
    ]
    accents = [(82, 160, 198), (82, 180, 132), (142, 116, 196), (230, 150, 69)]
    for i, args in enumerate(labels):
        label(d, *rel(img.size, *args[:4]), args[4], size=23, accent=accents[i % len(accents)])
    img.convert("RGB").save(out)


def annotate_08(path: Path, out: Path):
    img = Image.open(path).convert("RGBA")
    d = ImageDraw.Draw(img)
    title(d, img.size, "测试覆盖矩阵")
    labels = [
        (0.04, 0.14, 0.13, 0.06, "测试项"),
        (0.20, 0.14, 0.13, 0.06, "输入"),
        (0.36, 0.14, 0.13, 0.06, "预期"),
        (0.52, 0.14, 0.13, 0.06, "实际"),
        (0.68, 0.14, 0.13, 0.06, "截图"),
        (0.04, 0.29, 0.22, 0.06, "登录成功与失败"),
        (0.04, 0.42, 0.22, 0.06, "预测正常与缺失项"),
        (0.04, 0.55, 0.22, 0.06, "医学问答与日常聊天"),
        (0.04, 0.68, 0.22, 0.06, "图谱构建与降级提示"),
        (0.73, 0.34, 0.20, 0.08, "通过率\n主要功能通过"),
        (0.73, 0.54, 0.20, 0.08, "异常记录\n80 入口备用"),
        (0.73, 0.74, 0.20, 0.08, "健康检查\nUP"),
    ]
    for args in labels:
        color = (218, 128, 54) if "异常" in args[4] or "80" in args[4] else (54, 142, 176)
        label(d, *rel(img.size, *args[:4]), args[4], size=21, accent=color)
    img.convert("RGB").save(out)


ANNOTATORS = [
    ("image2_raw_01.png", "image2_fig01_function_blocks.png", annotate_01),
    ("image2_raw_02.png", "image2_fig02_architecture.png", annotate_02),
    ("image2_raw_03.png", "image2_fig03_project_workflow.png", annotate_03),
    ("image2_raw_04.png", "image2_fig04_graph_pipeline.png", annotate_04),
    ("image2_raw_05.png", "image2_fig05_prediction_uml.png", annotate_05),
    ("image2_raw_06.png", "image2_fig06_qa_sequence.png", annotate_06),
    ("image2_raw_07.png", "image2_fig07_training_mindmap.png", annotate_07),
    ("image2_raw_08.png", "image2_fig08_test_matrix.png", annotate_08),
]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for raw_name, out_name, fn in ANNOTATORS:
        fn(RAW / raw_name, OUT / out_name)
    summary = """# image2 插图说明

本目录中的 8 张 labeled 图片均以 GPT image2 生成底图为基础，再通过本地后处理覆盖准确中文标签。

1. `image2_fig01_function_blocks.png` 系统功能框图。
2. `image2_fig02_architecture.png` 系统总体架构图。
3. `image2_fig03_project_workflow.png` 生产实习项目流程图。
4. `image2_fig04_graph_pipeline.png` 知识图谱全量构建流程图。
5. `image2_fig05_prediction_uml.png` 疾病预测 UML 活动图。
6. `image2_fig06_qa_sequence.png` 智能问答与多模态时序图。
7. `image2_fig07_training_mindmap.png` 模型训练与评估思维导图。
8. `image2_fig08_test_matrix.png` 测试覆盖矩阵图。

处理原则：保留 image2 的手工 Visio 风格、图标、线条和布局；删除对随机文字的依赖；所有中文标签由本地脚本覆盖，保证报告正文、图题和图内文字一致。
"""
    (NOTES / "image2-diagram-prompts.md").write_text(summary, encoding="utf-8")
    print(OUT)


if __name__ == "__main__":
    main()
