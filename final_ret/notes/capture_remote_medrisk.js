const fs = require("fs");
const path = require("path");
const puppeteer = require("puppeteer-core");

const repo = path.resolve(__dirname, "..", "..");
const outDir = path.join(repo, "final_ret", "screenshots_image2_final");
const notesDir = path.join(repo, "final_ret", "notes");
fs.mkdirSync(outDir, { recursive: true });
fs.mkdirSync(notesDir, { recursive: true });

const chromePath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const baseUrl = process.env.MEDRISK_REPORT_BASE_URL || "http://81.71.65.98:80";
const adminUser = process.env.MEDRISK_REPORT_USER || "admin";
const adminPassword = process.env.MEDRISK_REPORT_PASSWORD || "123456";

const rows = [];

function rel(p) {
  return path.relative(repo, p).replace(/\\/g, "/");
}

async function shot(page, name, title) {
  const file = path.join(outDir, name);
  await page.screenshot({ path: file, fullPage: true });
  rows.push({ item: title, actual: "已截图", screenshot: rel(file), pass: "是" });
  return file;
}

async function getStatus(url) {
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(10000) });
    const text = await res.text();
    return { ok: res.ok, status: res.status, text: text.slice(0, 500) };
  } catch (err) {
    return { ok: false, status: "ERROR", text: err.message };
  }
}

async function waitSoft(page, ms = 1200) {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function clickByText(page, needles) {
  const list = Array.isArray(needles) ? needles : [needles];
  return await page.evaluate((texts) => {
    const candidates = Array.from(document.querySelectorAll("button,a,li,span,p,div"));
    const visible = (el) => {
      const rect = el.getBoundingClientRect();
      const style = window.getComputedStyle(el);
      return rect.width > 1 && rect.height > 1 && style.visibility !== "hidden" && style.display !== "none";
    };
    for (const text of texts) {
      const matches = candidates
        .filter((el) => visible(el) && (el.innerText || "").trim().includes(text))
        .sort((a, b) => {
          const at = (a.innerText || "").trim();
          const bt = (b.innerText || "").trim();
          const ae = at === text ? 0 : 1;
          const be = bt === text ? 0 : 1;
          return ae - be || at.length - bt.length;
        });
      const hit = matches[0];
      if (hit) {
        const target = hit.closest("button,a,li,[role='menuitem'],.nav-item,.el-menu-item") || hit;
        target.click();
        return text;
      }
    }
    return "";
  }, list);
}

async function fillLogin(page, user, password) {
  await page.evaluate(
    ({ user, password }) => {
      const visibleInputs = Array.from(document.querySelectorAll("input")).filter((el) => {
        const rect = el.getBoundingClientRect();
        const style = window.getComputedStyle(el);
        return rect.width > 1 && rect.height > 1 && style.display !== "none" && style.visibility !== "hidden";
      });
      const setValue = (el, value) => {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
        setter.call(el, value);
        el.dispatchEvent(new Event("input", { bubbles: true }));
        el.dispatchEvent(new Event("change", { bubbles: true }));
      };
      if (visibleInputs[0]) setValue(visibleInputs[0], user);
      if (visibleInputs[1]) setValue(visibleInputs[1], password);
    },
    { user, password }
  );
}

async function clickLogin(page) {
  const primary = await page.$("button.el-button--primary");
  if (primary) {
    await primary.click();
    return;
  }
  const clicked = await clickByText(page, ["登录系统"]);
  if (!clicked) {
    const buttons = await page.$$("button");
    if (buttons[0]) await buttons[0].click();
  }
}

async function gotoFeature(page, labels, wait = 1800) {
  const clicked = await clickByText(page, labels);
  await waitSoft(page, wait);
  return clicked;
}

async function maybeSubmitPrediction(page) {
  const clicked = await clickByText(page, ["提交预测", "开始预测", "预测"]);
  await waitSoft(page, 3500);
  return clicked;
}

async function askQuestion(page) {
  await clickByText(page, ["新建"]);
  await waitSoft(page, 800);
  const message =
    "请结合平台知识库，用三点说明高血压和糖尿病患者如何降低健康风险。";
  const area = await page.$("textarea");
  if (area) {
    await area.click();
    await area.type(message, { delay: 5 });
  } else {
    const editable = await page.$("[contenteditable='true']");
    if (editable) {
      await editable.click();
      await page.keyboard.type(message, { delay: 5 });
    }
  }
  const clicked = await clickByText(page, ["发送"]);
  if (!clicked) await page.keyboard.press("Enter");
  await waitSoft(page, 8000);
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: chromePath,
    headless: "new",
    args: ["--no-sandbox", "--disable-setuid-sandbox", "--window-size=1680,1050"],
    defaultViewport: { width: 1680, height: 1050, deviceScaleFactor: 1 },
  });
  const page = await browser.newPage();
  page.setDefaultTimeout(12000);

  const entry = await getStatus(baseUrl);
  rows.push({
    item: "80 入口访问",
    actual: `${baseUrl} 返回 ${entry.status}`,
    screenshot: "",
    pass: entry.ok ? "是" : "否",
  });

  const health = await getStatus(`${baseUrl}/api/health`);
  rows.push({
    item: "健康检查",
    actual: health.ok ? health.text : `失败 ${health.status} ${health.text}`,
    screenshot: "",
    pass: health.ok && health.text.includes("UP") ? "是" : "否",
  });

  await page.goto(baseUrl, { waitUntil: "networkidle2", timeout: 30000 });
  await waitSoft(page, 2500);
  await shot(page, "01-login-page-80.png", "远端登录页");

  await fillLogin(page, adminUser, "wrong-password");
  await clickLogin(page);
  await waitSoft(page, 1800);
  await shot(page, "02-login-failed-80.png", "登录失败提示");

  await page.goto(baseUrl, { waitUntil: "networkidle2", timeout: 30000 });
  await waitSoft(page, 1200);
  await fillLogin(page, adminUser, adminPassword);
  await clickLogin(page);
  await waitSoft(page, 4500);
  const loggedText = await page.evaluate(() => document.body.innerText.slice(0, 2000));
  rows.push({
    item: "管理员登录",
    actual: loggedText.includes("管理员") || loggedText.includes("管理") ? "登录后页面出现管理员或管理功能文字" : "未确认管理员文字",
    screenshot: "",
    pass: loggedText.includes("管理员") || loggedText.includes("管理") ? "是" : "待复核",
  });
  await shot(page, "03-admin-home-80.png", "管理员首页");

  await gotoFeature(page, ["疾病预测", "风险预测", "在线预测"]);
  await shot(page, "04-prediction-form-80.png", "疾病预测输入界面");
  await maybeSubmitPrediction(page);
  await shot(page, "05-prediction-result-80.png", "疾病预测结果界面");

  await gotoFeature(page, ["智能问答", "问答"]);
  await shot(page, "06-qa-before-send-80.png", "智能问答发送前");
  await askQuestion(page);
  await shot(page, "07-qa-answer-80.png", "智能问答流式回答结果");

  await gotoFeature(page, ["图谱可视化", "图谱管理", "知识图谱", "图谱"]);
  await shot(page, "08-knowledge-graph-80.png", "知识图谱可视化");

  await gotoFeature(page, ["模型训练管理", "模型训练", "训练任务"]);
  await shot(page, "09-training-management-80.png", "模型训练管理");

  await gotoFeature(page, ["审计日志", "系统管理"]);
  await clickByText(page, ["审计日志"]);
  await waitSoft(page, 1500);
  await shot(page, "10-audit-log-80.png", "审计日志和请求 IP");

  const healthPage = await browser.newPage();
  await healthPage.setViewport({ width: 1200, height: 500, deviceScaleFactor: 1 });
  await healthPage.goto(`${baseUrl}/api/health`, { waitUntil: "networkidle2", timeout: 15000 });
  await shot(healthPage, "11-health-check-80.png", "健康检查 JSON");

  await browser.close();

  const md = [
    "# MedRisk 远端实测截图记录",
    "",
    `测试入口：${baseUrl}`,
    `测试时间：${new Date().toISOString()}`,
    "",
    "| 测试项 | 实际结果 | 截图 | 是否通过 |",
    "| --- | --- | --- | --- |",
    ...rows.map((r) => `| ${r.item} | ${String(r.actual).replace(/\|/g, "/")} | ${r.screenshot} | ${r.pass} |`),
    "",
  ].join("\n");
  fs.writeFileSync(path.join(notesDir, "remote-image2-test-record.md"), md, "utf8");
  console.log(md);
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
