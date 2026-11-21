import * as Engine from "./engine.js";
import { FVG_FIREBASE_CONFIG, isFirebaseWebConfigured } from "./firebase-config.js";

const STORAGE_KEY = "fvg_web_workspace_v2";
const APPROVED_ARABIC_ABOUT_US =
  "في ظل متغيّرات اقتصادية متسارعة وضغوط تضخمية حادة تهدد استمرارية المشاريع، يأتي 'دليل رؤيتك المالي' ليكون بوصلتك نحو الاستقرار المالي. نحن لا نكتفي بحساب الأرقام، بل نسهل لك اتخاذ قرارات التسعير الصحيحة بشكل تلقائي ومدروس، لنحول معادلات التكاليف المعقدة إلى أدوات عملية دقيقة تضمن لك الحفاظ على رأس المال، تعظيم الأرباح، والعبور بمشروعك نحو بر الأمان بكل ثقة واحترافية.";

const DEFAULT_COA = [
  { code: "1010", name: "Cash & Bank", nameAr: "النقدية والبنوك", accountType: "asset", subType: "cash", normalBalance: "debit", openingBalance: "0.00" },
  { code: "1110", name: "Accounts Receivable", nameAr: "الذمم المدينة", accountType: "asset", subType: "receivable", normalBalance: "debit", openingBalance: "0.00" },
  { code: "1210", name: "Inventory", nameAr: "المخزون", accountType: "asset", subType: "inventory", normalBalance: "debit", openingBalance: "0.00" },
  { code: "1510", name: "Property & Equipment", nameAr: "الأصول الثابتة", accountType: "asset", subType: "fixed_asset", normalBalance: "debit", openingBalance: "0.00" },
  { code: "2010", name: "Accounts Payable", nameAr: "الذمم الدائنة", accountType: "liability", subType: "current_liability", normalBalance: "credit", openingBalance: "0.00" },
  { code: "2510", name: "Long-Term Loans", nameAr: "قروض طويلة الأجل", accountType: "liability", subType: "long_term_liability", normalBalance: "credit", openingBalance: "0.00" },
  { code: "3010", name: "Owner Capital", nameAr: "رأس المال", accountType: "equity", subType: "equity", normalBalance: "credit", openingBalance: "0.00" },
  { code: "4010", name: "Sales Revenue", nameAr: "إيرادات المبيعات", accountType: "revenue", subType: "revenue", normalBalance: "credit", openingBalance: "0.00" },
  { code: "5010", name: "Cost of Goods Sold", nameAr: "تكلفة البضاعة المباعة", accountType: "cogs", subType: "cogs", normalBalance: "debit", openingBalance: "0.00" },
  { code: "6010", name: "Operating & Admin Expenses", nameAr: "مصروفات تشغيلية وإدارية", accountType: "expense", subType: "operating_expense", normalBalance: "debit", openingBalance: "0.00" },
  { code: "6210", name: "Inventory Loss & Scrap", nameAr: "خسائر الهدر والتلف", accountType: "expense", subType: "operating_expense", normalBalance: "debit", openingBalance: "0.00" },
  { code: "7010", name: "Finance & Interest Expense", nameAr: "تكاليف التمويل والفوائد", accountType: "expense", subType: "interest", normalBalance: "debit", openingBalance: "0.00" }
];

function loadSavedState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw);
  } catch {}
  return null;
}

const saved = loadSavedState();

const state = {
  stage: "splash", // "splash" | "developer" | "public" | "app"
  isArabic: saved?.isArabic ?? false,
  isDark: saved?.isDark ?? false,
  publicPage: 0,
  appTab: 0, // 0 Home, 1 Prices, 2 Costing, 3 Ledger, 4 Stock, 5 Research, 6 AI & Org
  subTab: { prices: 0, costing: 0, ledger: 0, stock: 0, research: 0, reports: 0 },
  user: saved?.user ?? null,
  activeOrg: saved?.activeOrg ?? null,
  products: saved?.products ?? [],
  priceObservations: saved?.priceObservations ?? [],
  inflationRecords: saved?.inflationRecords ?? [],
  baskets: saved?.baskets ?? [],
  fxRates: saved?.fxRates ?? [],
  costSnapshots: saved?.costSnapshots ?? [],
  scenarios: saved?.scenarios ?? [],
  accounts: saved?.accounts ?? DEFAULT_COA,
  journalEntries: saved?.journalEntries ?? [],
  counterpartyItems: saved?.counterpartyItems ?? [],
  inventoryItems: saved?.inventoryItems ?? [],
  stocktakes: saved?.stocktakes ?? [],
  scrapLosses: saved?.scrapLosses ?? [],
  budgets: saved?.budgets ?? [],
  researchRecords: saved?.researchRecords ?? [],
  paymentRequests: saved?.paymentRequests ?? [],
  ocrDrafts: saved?.ocrDrafts ?? [],
  auditLogs: saved?.auditLogs ?? [],
  aiMode: "financial",
  aiResponse: "",
  chatTranscript: saved?.chatTranscript ?? [],
  costDraftMethod: "actual",
  selectedReport: "statements",
  toast: null
};

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      isArabic: state.isArabic,
      isDark: state.isDark,
      user: state.user,
      activeOrg: state.activeOrg,
      products: state.products,
      priceObservations: state.priceObservations,
      inflationRecords: state.inflationRecords,
      baskets: state.baskets,
      fxRates: state.fxRates,
      costSnapshots: state.costSnapshots,
      scenarios: state.scenarios,
      accounts: state.accounts,
      journalEntries: state.journalEntries,
      counterpartyItems: state.counterpartyItems,
      inventoryItems: state.inventoryItems,
      stocktakes: state.stocktakes,
      scrapLosses: state.scrapLosses,
      budgets: state.budgets,
      researchRecords: state.researchRecords,
      paymentRequests: state.paymentRequests,
      ocrDrafts: state.ocrDrafts,
      auditLogs: state.auditLogs,
      chatTranscript: state.chatTranscript
    }));
  } catch {}
}

function logAudit(action, entityType, summary) {
  state.auditLogs.unshift({
    id: "aud_" + Date.now(),
    action,
    entityType,
    summary,
    actorEmail: state.user?.email || "workspace@fvg",
    at: new Date().toISOString().slice(0, 19)
  });
}

function showToast(msg) {
  state.toast = msg;
  render();
  setTimeout(() => {
    if (state.toast === msg) {
      state.toast = null;
      render();
    }
  }, 3200);
}

function isTrialLocked() {
  const org = state.activeOrg;
  if (!org) return false;
  if (org.subscriptionStatus === "active") return false;
  if (org.subscriptionStatus === "expired" || org.subscriptionStatus === "cancelled") return true;
  return Date.now() >= org.trialEndsAtMillis;
}

function esc(str) {
  return String(str ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function t(en, ar) {
  return state.isArabic ? ar : en;
}

function renderTrialBanner(preview = false) {
  const target = preview ? Date.now() + Engine.TRIAL_DURATION_MILLIS : state.activeOrg?.trialEndsAtMillis;
  if (!target) return "";
  const cd = Engine.computeTrialCountdown(target, Date.now());
  const pad = (v) => String(v).padStart(2, "0");
  return `
    <div class="card cream" data-trial-banner="${preview ? "preview" : target}">
      <div class="row-between">
        <strong>${cd.isExpired ? t("3-Day (72h) Trial Ended", "انتهت الفترة التجريبية (72 ساعة)") : (preview ? t("Full 3-Day (72-Hour) Free Trial Included", "فترة تجربة كاملة 3 أيام (72 ساعة)") : t("3-Day Active Trial", "الفترة التجريبية النشطة"))}</strong>
        <span class="badge ${cd.isExpired ? "high" : "low"}">${cd.isExpired ? "EXPIRED" : "72H TRIAL"}</span>
      </div>
      <div class="grid-4" style="margin-top:8px;">
        <div class="timer-box"><strong data-cd="d">${pad(cd.days)}</strong><span>${t("Days", "أيام")}</span></div>
        <div class="timer-box"><strong data-cd="h">${pad(cd.hours)}</strong><span>${t("Hours", "ساعات")}</span></div>
        <div class="timer-box"><strong data-cd="m">${pad(cd.minutes)}</strong><span>${t("Mins", "دقائق")}</span></div>
        <div class="timer-box"><strong data-cd="s">${pad(cd.seconds)}</strong><span>${t("Secs", "ثواني")}</span></div>
      </div>
    </div>
  `;
}

function renderSplash() {
  return `
    <div class="fvg-splash" id="splashRoot">
      <svg class="fvg-splash-rays" viewBox="0 0 400 800" preserveAspectRatio="none">
        <polygon points="224,0 320,0 136,352 64,352" fill="#517D77" fill-opacity="0.08"/>
        <polygon points="336,0 400,0 400,112 208,400 152,400" fill="#517D77" fill-opacity="0.06"/>
        <line x1="300" y1="92" x2="300" y2="160" stroke="#517D77" stroke-opacity="0.16" stroke-width="3"/>
        <rect x="295" y="106" width="10" height="42" rx="3" fill="#517D77" fill-opacity="0.16"/>
        <line x1="324" y1="74" x2="324" y2="148" stroke="#517D77" stroke-opacity="0.16" stroke-width="3"/>
        <rect x="319" y="88" width="10" height="48" rx="3" fill="#517D77" fill-opacity="0.16"/>
        <line x1="348" y1="54" x2="348" y2="132" stroke="#517D77" stroke-opacity="0.16" stroke-width="3"/>
        <rect x="343" y="68" width="10" height="54" rx="3" fill="#517D77" fill-opacity="0.16"/>
        <line x1="372" y1="34" x2="372" y2="118" stroke="#DDB065" stroke-opacity="0.24" stroke-width="3"/>
        <rect x="367" y="48" width="10" height="58" rx="3" fill="#DDB065" fill-opacity="0.24"/>
      </svg>
      <div class="fvg-splash-center">
        <img class="fvg-splash-logo" src="./assets/fvg_brand_symbol.png" onerror="this.src='./assets/fvg-icon.svg'" alt="FVG Logo"/>
        <h1 class="fvg-splash-title">FVG</h1>
        <div class="fvg-splash-sub-en">Financial Vision Guide</div>
        <div class="fvg-splash-sub-ar">
          <span class="fvg-gold-line"></span>
          <span>دليل رؤيتك المالي</span>
          <span class="fvg-gold-line"></span>
        </div>
        <p class="fvg-splash-tagline">${t("Smarter Decisions.<br>Stronger Financial Future.", "قرارات أذكى.<br>مستقبل مالي أقوى.")}</p>
      </div>
      <svg class="fvg-splash-waves" viewBox="0 0 400 200" preserveAspectRatio="none">
        <path d="M0,45 C110,0 270,95 400,25 L400,200 L0,200 Z" fill="#15423A"/>
        <path d="M0,78 C120,32 280,125 400,60 L400,200 L0,200 Z" fill="#DDB065"/>
        <path d="M0,96 C120,50 280,142 400,78 L400,200 L0,200 Z" fill="#022A27"/>
      </svg>
      <div class="fvg-splash-footer">${t("Tap anywhere to continue", "انقر في أي مكان للمتابعة")}</div>
    </div>
  `;
}

function renderBanner(title, subtitle) {
  return `
    <div class="fvg-banner">
      <div>
        <div style="font-size:18px;font-weight:800;">${esc(title)}</div>
        <div style="font-size:12px;color:#DDB065;">${esc(subtitle)}</div>
      </div>
      <div class="fvg-banner-actions">
        <button class="icon-pill" data-action="toggleLang">${state.isArabic ? "EN" : "AR"}</button>
        <button class="icon-pill" data-action="toggleTheme">${state.isDark ? "☀" : "☾"}</button>
      </div>
    </div>
  `;
}

function renderDeveloperShowcase() {
  return `
    <div class="main-content" style="padding-bottom:32px;">
      ${renderBanner(t("Researcher & FVG Vision", "الباحثة ومطوّرة FVG"), "Financial Vision Guide • دليل رؤيتك المالي")}
      <div class="card hero" style="text-align:center;">
        <img class="portrait-circle" src="./assets/zahra_yousif_portrait.png" onerror="this.src='./assets/fvg-icon.svg'" alt="Zahra Yousif Elabid Wagiealla Ahmed" style="margin:0 auto 10px;"/>
        <h2 style="margin:6px 0 2px;">Zahra Yousif Elabid Wagiealla Ahmed</h2>
        <div style="color:#DDB065;font-weight:700;margin-bottom:8px;">زهراء يوسف العبد وقيع الله أحمد</div>
        <div class="badge" style="color:#FAF2E8;border-color:#DDB065;">${t("Nile University — Faculty of Business Administration — Accounting — 2026", "جامعة النيل — كلية إدارة الأعمال — تخصص المحاسبة — 2026")}</div>
        <div style="margin-top:8px;font-size:12px;color:#DDB065;">Financialvisionguide@gmail.com • 00249911569966</div>
      </div>
      <div class="card cream">
        <h3 style="margin:0 0 8px;color:#022A27;">رؤية دليل رؤيتك المالي (FVG)</h3>
        <p style="line-height:1.7;margin:0 0 10px;">${APPROVED_ARABIC_ABOUT_US}</p>
        <hr style="border:none;border-top:1px solid rgba(221,176,101,0.5);margin:10px 0;"/>
        <p style="font-size:13px;color:var(--fvg-text-muted);margin:0;">Amid rapid economic shifts and acute inflationary pressures threatening business continuity, Financial Vision Guide (FVG) turns complex cost equations into precise, practical tools that safeguard capital and maximize profitability.</p>
      </div>
      ${renderTrialBanner(true)}
      <button class="btn btn-gold" style="width:100%;" data-action="enterPublicOrApp">${t("Continue to Financial Vision Guide", "الدخول إلى منصة دليل رؤيتك المالي")} →</button>
    </div>
  `;
}

function renderPublicSite() {
  const pages = [
    t("Home", "الرئيسية"),
    t("Features & Sectors", "المزايا والقطاعات"),
    t("Plans & Pay", "الباقات والدفع"),
    t("About & Cases", "عن المنصة"),
    t("Guide & FAQ", "الدليل 9 خطوات"),
    t("Contact", "تواصل معنا"),
    t("Privacy", "الخصوصية"),
    t("Terms", "الشروط")
  ];
  let body = "";
  if (state.publicPage === 0) {
    body = `
      <div class="card hero">
        <div style="display:flex;align-items:center;gap:12px;">
          <img src="./assets/fvg_brand_symbol.png" onerror="this.src='./assets/fvg-icon.svg'" style="width:52px;height:52px;border-radius:12px;background:#FAF2E8;padding:4px;" alt="FVG"/>
          <div>
            <h2 style="margin:0;">${t("FVG Management Accounting & Pricing", "منصة FVG للمحاسبة الإدارية والتسعير")}</h2>
            <div style="color:#DDB065;font-size:13px;">${t("Installable on iPhone (Safari -> Add to Home Screen), Android & Web", "تثبيت مباشر على iPhone (Safari -> إضافة إلى الشاشة الرئيسية) والكمبيوتر")}</div>
          </div>
        </div>
        <p style="font-size:14px;line-height:1.6;">${t("8 Costing Methods, Risk Engine, Price & Inflation Monitoring, Double-Entry Statements, 18 Ratios, FIFO/MWA Stock, Nobo Thesis Workspace & 3-Mode Grounded AI.", "مراقبة الأسعار والتضخم، 8 طرق للتكلفة، القوائم المالية والـ 18 نسبة، تقييم المخزون FIFO/MWA، بحث نوبو، والمساعد الذكي.")}</p>
        <div class="field-group">
          <label style="color:#DDB065;">${t("Workspace Email (Starts Strict 3-Day / 72-Hour Trial)", "البريد الإلكتروني لبدء تجربة 3 أيام (72 ساعة)")}</label>
          <input id="authEmailInput" type="email" placeholder="name@company.com" value="${esc(state.user?.email || "")}" />
        </div>
        <div class="field-group">
          <label style="color:#DDB065;">${t("Organization Name (SDG Default)", "اسم المنظمة / الشركة")}</label>
          <input id="authOrgInput" type="text" placeholder="${t("My Company", "شركتي")}" value="${esc(state.activeOrg?.name || "")}" />
        </div>
        <button class="btn btn-gold" style="width:100%;" data-action="launchWorkspace">${isFirebaseWebConfigured() ? t("Sign In with Google / Start 3-Day Trial", "دخول جوجل وبدء تجربة 3 أيام") : t("Launch Workspace (Start 3-Day Trial)", "تشغيل مساحة العمل (تجربة 3 أيام)")}</button>
      </div>
      ${renderTrialBanner(true)}
      <div class="card">
        <div style="display:flex;align-items:center;gap:12px;">
          <img class="portrait-circle" style="width:68px;height:68px;" src="./assets/zahra_yousif_portrait.png" onerror="this.src='./assets/fvg-icon.svg'" alt="Zahra Yousif"/>
          <div>
            <strong>Zahra Yousif Elabid Wagiealla Ahmed</strong>
            <div style="font-size:12px;color:var(--fvg-text-muted);">زهراء يوسف العبد وقيع الله أحمد • Nile University (2026)</div>
          </div>
        </div>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Commercial Reference — Ben Al-Zahra (Isolated Showcase)", "مشروع بن الزهراء التجاري (منفصل تماماً عن نوبو)")}</h3>
        <img class="showcase-banner" src="./assets/fvg_ben_alzahra_products.png" onerror="this.style.display='none'" alt="Ben Al-Zahra"/>
        <p style="font-size:13px;color:var(--fvg-text-muted);margin-bottom:0;">${t("Zero fabricated ledger metrics; strictly isolated from Nobo academic research.", "نموذج مرئي لتسعير المنتجات التجارية بدون أي أرقام مفبركة ومعزول تماماً عن دراسة نوبو.")}</p>
      </div>
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Academic Field Study — Nobo Factory (Khartoum North)", "الدراسة الميدانية الأكاديمية — مصنع نوبو بالخرطوم بحري")}</h3>
        <p style="margin-bottom:0;">${t("Supervisor: Dr. Fadl Allah Berier Gafa Ibrahim • Zero fabricated field results.", "الإشراف الأكاديمي: د. فضل الله برير قفة إبراهيم • حفظ كامل للنزاهة العلمية دون أي نتائج مخترعة.")}</p>
      </div>
    `;
  } else if (state.publicPage === 1) {
    const activeSec = ["Manufacturing & Industrial Production", "Food, Beverage & Retail Packaging"];
    const soonSec = ["Retail & Wholesale Trade", "Pharmacy & Healthcare", "Agriculture & Livestock", "Construction & Contracting", "Logistics & Transport", "Hospitality & Catering", "Professional Services & Consulting", "E-Commerce & Digital Supply"];
    body = `
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Supported Sectors & Expansion Roadmap", "القطاعات المفعّلة والقطاعات القادمة")}</h3>
        ${activeSec.map((s) => `<div class="row-between"><span>• ${s}</span><span class="badge low">${t("Active", "مفعّل")}</span></div>`).join("")}
        <hr style="border:none;border-top:1px solid var(--fvg-border);margin:8px 0;"/>
        ${soonSec.map((s) => `<div class="row-between"><span>• ${s}</span><span class="badge">${t("Coming Soon", "قريباً")}</span></div>`).join("")}
      </div>
    `;
  } else if (state.publicPage === 2) {
    body = `
      ${renderTrialBanner(true)}
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Manual Sudan Payment & Visa Adapter Notice", "الشفافية في الدفع (بنكك / أوكاش / تحويل بنكي)")}</h3>
        <p>${t("All workspaces start with a strict 3-day (72-hour) free trial. Live subscriptions operate via Bankak, O-Cash, or Bank Transfer proof verification (pending_verification -> approved). Visa/MasterCard gateway is an Adapter Notice only (no card/CVV collection).", "تبدأ كل منظمة بتجربة 3 أيام (72 ساعة). التفعيل داخل السودان يتم برفع إثبات الدفع عبر بنكك أو أوكاش أو تحويل بنكي للمراجعة. بوابة Visa عبارة عن محوّل غير مفعّل ولا يجمع أرقام بطاقات.")}</p>
      </div>
    `;
  } else if (state.publicPage === 3) {
    body = `
      <div class="card">
        <h3 style="margin-top:0;">Zahra Yousif Elabid Wagiealla Ahmed</h3>
        <p>${APPROVED_ARABIC_ABOUT_US}</p>
        <p><strong>${t("Academic Supervisor:", "المشرف الأكاديمي:")}</strong> Dr. Fadl Allah Berier Gafa Ibrahim (د. فضل الله برير قفة إبراهيم)</p>
      </div>
    `;
  } else if (state.publicPage === 4) {
    const steps = [
      "1. Enter workspace email & organization name (SDG default) to activate strict 3-day (72h) trial.",
      "2. Record product prices and monitor >= 15% unusual movement alerts or import CSV.",
      "3. Log monthly/annual inflation rates (official/calculated/manual/provisional) & weighted baskets.",
      "4. Compute unit cost via any of the 8 Costing Methods + Risk Score + Break-Even CEILING.",
      "5. Benchmark Standard Costs vs Production Orders to inspect Price/Usage & Rate/Efficiency variances.",
      "6. Post balanced Double-Entry Journals to render Income Statement, Balance Sheet & 18 Ratios.",
      "7. Track FIFO / Moving Average inventory against current market replacement cost.",
      "8. Document Nobo thesis hypotheses & field readiness with [Pending Field Data] integrity.",
      "9. Query 3-Mode Grounded AI, generate reports (CSV/JSON/PDF), and submit Bankak/O-Cash proofs."
    ];
    body = `<div class="card cream"><h3 style="margin-top:0;">${t("9-Step Quick Start Guide", "دليل التشغيل السريع في 9 خطوات")}</h3>${steps.map((st) => `<p style="margin:6px 0;">${st}</p>`).join("")}</div>`;
  } else if (state.publicPage === 5) {
    body = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Contact FVG", "تواصل مع دليل رؤيتك المالي")}</h3>
        <p><strong>Email:</strong> Financialvisionguide@gmail.com</p>
        <p><strong>Phone / WhatsApp:</strong> 00249911569966</p>
        <a class="btn btn-primary" href="mailto:Financialvisionguide@gmail.com?subject=FVG%20Inquiry">${t("Open Email Client", "مراسلة عبر البريد")}</a>
      </div>
    `;
  } else if (state.publicPage === 6) {
    body = `<div class="card"><h3 style="margin-top:0;">${t("Privacy & Tenant Isolation", "الخصوصية وعزل البيانات")}</h3><p>${t("Each organization's financial records, products, inventory, and research entries stay isolated. FVG never fabricates empirical or accounting metrics.", "تُحفظ بيانات كل منظمة بعزل كامل ولا تقوم المنصة باختلاق أي أرقام.")}</p></div>`;
  } else {
    body = `<div class="card"><h3 style="margin-top:0;">${t("Terms & Methodology", "الشروط والمنهجية المحاسبية")}</h3><p>${t("What-If simulations display 'Scenario outcome — not a prediction'. Posted journals are immutable and corrected via formal reversals.", "نتائج المحاكاة افتراضية وليست تنبؤاً، وتُصحح القيود المرحّلة عبر قيود العكس.")}</p></div>`;
  }

  return `
    <div class="main-content">
      ${renderBanner("FVG • Financial Vision Guide", "دليل رؤيتك المالي")}
      <div class="subtabs">
        ${pages.map((p, i) => `<button class="subtab ${state.publicPage === i ? "active" : ""}" data-public-page="${i}">${esc(p)}</button>`).join("")}
      </div>
      ${body}
    </div>
  `;
}

function renderStatementsAndRatios() {
  return Engine.generateFinancialStatements(state.accounts, state.journalEntries);
}

function renderTrialGateCard() {
  return `
    <div class="card cream" style="border-color:var(--fvg-negative);">
      <h3 style="color:var(--fvg-negative);margin-top:0;">${t("3-Day (72-Hour) Free Trial Ended", "انتهت الفترة التجريبية (3 أيام / 72 ساعة)")}</h3>
      <p>${t("Your 72-hour trial has ended. Submit a Bankak, O-Cash, or Bank Transfer payment proof in AI & Org -> Org & Payment to unlock Prices, Costing, Ledger & Stock write tools.", "انتهت مدة التجربة. يرجى تقديم إثبات الدفع عبر بنكك أو أوكاش أو التحويل البنكي في تبويب الإعدادات لإعادة فتح التعديل.")}</p>
      <button class="btn btn-gold" data-action="goBilling">${t("Upgrade Plan & Submit Payment Proof", "ترقية الباقة وإرسال إثبات الدفع")}</button>
    </div>
  `;
}

function renderHomeTab() {
  const org = state.activeOrg;
  const curr = org?.baseCurrency || "SDG";
  const stmt = renderStatementsAndRatios();
  const invBook = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.avgCost), 0));
  const invRepl = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.marketPrice || it.avgCost), 0));
  const portfolio = Engine.money(stmt.cashAndBank + invBook);
  const latestInf = state.inflationRecords[0];
  const latestSnap = state.costSnapshots[state.costSnapshots.length - 1];
  const risk = latestSnap
    ? Engine.evaluateFinancialRisk(latestSnap.coreResult.profitMarginPct, latestInf ? latestInf.monthlyRate : null, stmt.totalLiabilities, stmt.totalEquityWithNetIncome)
    : null;

  return `
    ${isTrialLocked() ? renderTrialGateCard() : renderTrialBanner(false)}
    <div class="card hero">
      <div class="row-between">
        <span style="color:#DDB065;font-weight:700;">${t("Total Cash & Inventory Book Value", "إجمالي النقدية والقيمة الدفترية للمخزون")}</span>
        <span class="badge" style="color:#DDB065;border-color:#DDB065;">${esc(curr)}</span>
      </div>
      <div style="font-size:30px;font-weight:800;margin:6px 0;">${portfolio} ${esc(curr)}</div>
      <div style="font-size:13px;">${t("Cash", "النقدية")}: ${stmt.cashAndBank} • ${t("Inventory Book", "المخزون الدفتري")}: ${invBook}</div>
      <div style="font-size:12px;color:#DDB065;margin-top:4px;">${t("Replacement Estimate", "تكلفة الاستبدال الحالية")}: ${invRepl} ${esc(curr)}</div>
    </div>
    <div class="grid-3">
      <div class="card"><div style="font-size:12px;color:var(--fvg-text-muted);">${t("Revenue", "الإيرادات")}</div><strong style="font-size:20px;color:var(--fvg-forest-green);">${stmt.revenue}</strong></div>
      <div class="card"><div style="font-size:12px;color:var(--fvg-text-muted);">${t("COGS & Expenses", "التكاليف والمصروفات")}</div><strong style="font-size:20px;color:var(--fvg-antique-gold);">${Engine.money(stmt.cogs + stmt.operatingExpenses)}</strong></div>
      <div class="card"><div style="font-size:12px;color:var(--fvg-text-muted);">${t("Net Profit", "صافي الربح")}</div><strong style="font-size:20px;color:${stmt.netProfit < 0 ? "var(--fvg-negative)" : "var(--fvg-positive)"};">${stmt.netProfit}</strong></div>
    </div>
    ${risk ? `
      <div class="card cream">
        <div class="row-between">
          <strong>${t("Financial Risk Engine", "محرك تقييم المخاطر المالية")} (${esc(latestSnap.productName)})</strong>
          <span class="badge ${risk.level}">${risk.level.toUpperCase()} RISK</span>
        </div>
        <div style="font-size:13px;margin-top:4px;">${esc(risk.ruleName)}: ${esc(risk.actualValue)} (Threshold ${esc(risk.thresholdValue)})</div>
        <div style="font-size:13px;color:var(--fvg-text-muted);">${esc(risk.explanation)}</div>
      </div>
    ` : ""}
    <div class="card">
      <h3 style="margin-top:0;">${t("Quick Shortcuts", "إجراءات سريعة")}</h3>
      <div class="grid-3">
        <button class="btn btn-outline" data-tab="1">${t("Prices", "الأسعار")}</button>
        <button class="btn btn-outline" data-tab="2">${t("Costing", "التكاليف")}</button>
        <button class="btn btn-outline" data-tab="3">${t("Ledger", "المحاسبة")}</button>
        <button class="btn btn-outline" data-tab="4">${t("Stock", "المخزون")}</button>
        <button class="btn btn-outline" data-tab="5">${t("Research", "البحث")}</button>
        <button class="btn btn-outline" data-tab="6">${t("AI & Org", "الذكاء والمنظمة")}</button>
      </div>
    </div>
  `;
}

function renderPricesTab() {
  const curr = state.activeOrg?.baseCurrency || "SDG";
  const sub = state.subTab.prices;
  const subLabels = [t("Prices & CSV", "الأسعار وCSV"), t("Inflation & Baskets", "التضخم والسلات"), t("Historical FX", "الصرف التاريخي")];
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Record Product Price Observation", "تسجيل ملاحظة سعر جديدة")}</h3>
        <div class="grid-2">
          <div class="field-group"><label>${t("Product Name", "اسم المنتج")}</label><input id="prName" placeholder="Sugar 50kg"/></div>
          <div class="field-group"><label>SKU</label><input id="prSku" placeholder="SUG-50"/></div>
          <div class="field-group"><label>${t("Category", "الفئة")}</label><input id="prCat" value="Raw Material"/></div>
          <div class="field-group"><label>${t("New Price", "السعر الجديد")} (${esc(curr)})</label><input id="prVal" type="number" step="0.01"/></div>
        </div>
        <button class="btn btn-primary" data-action="savePrice">${t("Save Price Observation", "حفظ السعر وحساب التغير %")}</button>
      </div>
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Import Product Prices via CSV", "استيراد الأسعار عبر CSV")}</h3>
        <p style="font-size:12px;margin-top:0;color:var(--fvg-text-muted);">${t("Format: name,sku,category,price (one item per line)", "التنسيق: اسم المنتج,الرمز,الفئة,السعر")}</p>
        <div class="field-group"><input id="csvFile" type="file" accept=".csv,text/csv"/></div>
        <div class="field-group"><textarea id="csvPaste" rows="2" placeholder="Sugar 50kg,SUG-50,Raw Material,42000&#10;Flour 25kg,FLR-25,Raw Material,18500"></textarea></div>
        <button class="btn btn-gold" data-action="importCsv">${t("Import CSV Rows", "استيراد صفوف CSV")}</button>
      </div>
      ${state.priceObservations.length === 0 ? `<div class="card cream">${t("No price records yet.", "لا توجد أسعار مسجلة بعد.")}</div>` : state.priceObservations.slice().reverse().map((o) => `
        <div class="card">
          <div class="row-between"><strong>${esc(o.productName)} (${esc(o.sku)})</strong><span class="badge ${o.isUnusual ? "high" : "low"}">${o.pctChange == null ? "NEW" : `${o.pctChange}% ${o.direction}`}</span></div>
          <div style="font-size:13px;">${o.previousPrice} -> ${o.price} ${esc(curr)} • ${esc(o.date)}</div>
        </div>
      `).join("")}
    `;
  } else if (sub === 1) {
    const compound = Engine.calculateCompoundedInflation(state.inflationRecords.map((r) => r.monthlyRate));
    panel = `
      <div class="card cream">
        <div class="row-between"><strong>${t("Cumulative Compounded Inflation", "التضخم المركب التراكمي")}</strong><strong style="color:var(--fvg-negative);">${compound}%</strong></div>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Add Inflation Record", "تسجيل معدل التضخم")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("Period (YYYY-MM)", "الفترة")}</label><input id="infPeriod" value="${new Date().toISOString().slice(0, 7)}"/></div>
          <div class="field-group"><label>${t("Monthly %", "الشهري %")}</label><input id="infMonthly" type="number" step="0.01"/></div>
          <div class="field-group"><label>${t("Source Type", "المصدر")}</label><select id="infSource"><option value="official">official</option><option value="calculated">calculated</option><option value="manual">manual</option><option value="provisional">provisional</option></select></div>
        </div>
        <button class="btn btn-primary" data-action="saveInflation">${t("Save Inflation Record", "حفظ معدل التضخم")}</button>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Weighted Inflation Price Basket", "سلة الأسعار الموزونة لقياس التضخم")}</h3>
        <div class="grid-4">
          <div class="field-group"><label>${t("Basket Name", "اسم السلة")}</label><input id="bskName" value="Factory Inputs"/></div>
          <div class="field-group"><label>${t("Item", "الصنف")}</label><input id="bskItem" placeholder="Diesel / Sugar"/></div>
          <div class="field-group"><label>${t("Base Price", "سعر الأساس")}</label><input id="bskBase" type="number" value="1000"/></div>
          <div class="field-group"><label>${t("Current Price", "السعر الحالي")}</label><input id="bskCurr" type="number" value="1250"/></div>
        </div>
        <div class="grid-2">
          <div class="field-group"><label>${t("Quantity", "الكمية")}</label><input id="bskQty" type="number" value="10"/></div>
          <div class="field-group"><label>${t("Weight", "الوزن")}</label><input id="bskWeight" type="number" step="0.1" value="1"/></div>
        </div>
        <button class="btn btn-gold" data-action="saveBasket">${t("Calculate & Save Weighted Basket", "حساب وحفظ السلة الموزونة")}</button>
      </div>
      ${state.baskets.map((b) => `<div class="card cream"><div class="row-between"><strong>${esc(b.name)} (${esc(b.item)})</strong><span class="badge high">Index ${b.indexValue ?? "N/A"} (${b.basketInflationPct ?? 0}%)</span></div><div style="font-size:13px;">Base: ${b.baseCost} -> Current: ${b.currentCost} ${esc(curr)}</div></div>`).join("")}
      ${state.inflationRecords.map((r) => `<div class="card"><div class="row-between"><strong>${esc(r.period)}</strong><span class="badge">${esc(r.sourceType)}</span></div><div>${r.monthlyRate}% / mo</div></div>`).join("")}
    `;
  } else {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Record Historical FX Rate (Zero Fallback)", "توثيق سعر الصرف التاريخي")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>Base</label><input id="fxBase" value="USD"/></div>
          <div class="field-group"><label>Quote</label><input id="fxQuote" value="${esc(curr)}"/></div>
          <div class="field-group"><label>Rate</label><input id="fxRate" type="number" step="0.0001"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveFx">${t("Save FX Rate", "حفظ سعر الصرف")}</button>
      </div>
      ${state.fxRates.map((f) => `<div class="card"><div class="row-between"><strong>${esc(f.base)}/${esc(f.quote)}</strong><strong>${f.rate}</strong></div><div style="font-size:12px;">${esc(f.date)}</div></div>`).join("")}
    `;
  }
  return `
    <div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="prices:${i}">${esc(l)}</button>`).join("")}</div>
    ${panel}
  `;
}

function renderCostingTab() {
  const curr = state.activeOrg?.baseCurrency || "SDG";
  const sub = state.subTab.costing;
  const subLabels = [t("8 Costing Methods", "8 طرق للتكلفة"), t("CVP & Risk", "التعادل والمخاطر"), t("Variance & Prod", "الانحراف"), t("What-If Simulator", "محاكي ماذا لو")];
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("8 Management Accounting Costing Methods", "طرق التكلفة الـ 8 ومحرك المخاطر")}</h3>
        <div class="field-group">
          <label>${t("Select Method", "اختر طريقة التكلفة")}</label>
          <select id="costMethod">
            ${Engine.ALL_COSTING_METHODS.map((m) => `<option value="${m}" ${state.costDraftMethod === m ? "selected" : ""}>${m.toUpperCase().replace("_", " ")}</option>`).join("")}
          </select>
        </div>
        <div class="grid-2">
          <div class="field-group"><label>${t("Product Name", "اسم المنتج")}</label><input id="cProd" value="Industrial Batch A"/></div>
          <div class="field-group"><label>${t("Good Units", "الوحدات الصالحة")}</label><input id="cUnits" type="number" value="100"/></div>
          <div class="field-group"><label>${t("Direct Material", "مواد مباشرة")}</label><input id="cMat" type="number" value="500"/></div>
          <div class="field-group"><label>${t("Direct Labor", "عمالة مباشرة")}</label><input id="cLab" type="number" value="200"/></div>
          <div class="field-group"><label>${t("Variable Overhead + Energy + Pack", "تكاليف إضافية متغيرة")}</label><input id="cOvh" type="number" value="150"/></div>
          <div class="field-group"><label>${t("Fixed Costs", "التكاليف الثابتة")}</label><input id="cFix" type="number" value="250"/></div>
          <div class="field-group"><label>${t("Selling Price / Unit", "سعر البيع للوحدة")}</label><input id="cPrice" type="number" value="16"/></div>
          <div class="field-group"><label>${t("Target Margin % / Process Equiv Units", "الهامش المستهدف / الوحدات المكافئة")}</label><input id="cExtra" type="number" value="20"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveCosting">${t("Compute & Save Snapshot", "حساب التكلفة والمخاطر وحفظها")}</button>
      </div>
      ${state.costSnapshots.slice().reverse().map((s) => `
        <div class="card cream">
          <div class="row-between"><strong>${esc(s.productName)} (${esc(s.method.toUpperCase())})</strong><span class="badge ${s.risk.level}">${s.risk.level.toUpperCase()} RISK</span></div>
          <div>Unit Cost: <strong>${s.coreResult.unitCost} ${esc(curr)}</strong> | Price: ${s.sellingPrice} | Margin: ${s.coreResult.profitMarginPct ?? "N/A"}% | Markup: ${s.coreResult.markupPct ?? "N/A"}%</div>
          <div style="font-size:12px;">Break-Even (CEILING): ${s.coreResult.breakEvenUnits ?? "N/A"} units • ${esc(s.methodNote)}</div>
        </div>
      `).join("")}
    `;
  } else if (sub === 1) {
    const snap = state.costSnapshots[state.costSnapshots.length - 1];
    panel = !snap ? `<div class="card cream">${t("Save a cost snapshot in 8 Costing Methods first.", "احفظ حساب تكلفة أولاً لعرض تحليل التعادل والمخاطر.")}</div>` : `
      <div class="card cream">
        <div class="row-between"><strong>${esc(snap.productName)} — Break-Even & Risk</strong><span class="badge ${snap.risk.level}">${snap.risk.level.toUpperCase()}</span></div>
        <div style="font-size:26px;font-weight:800;margin:8px 0;">${snap.coreResult.breakEvenUnits ?? "N/A"} units</div>
        <div>Revenue: ${snap.coreResult.revenue} ${esc(curr)} | Total Cost: ${snap.coreResult.totalCost} | Net Profit: ${snap.coreResult.netProfit}</div>
        <p style="font-size:13px;color:var(--fvg-text-muted);">${esc(snap.risk.ruleName)}: ${esc(snap.risk.explanation)}</p>
      </div>
    `;
  } else if (sub === 2) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Detailed Material & Labor Variance Decomposition", "تحليل انحراف المواد والعمالة")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>Good Units</label><input id="vUnits" type="number" value="100"/></div>
          <div class="field-group"><label>Std Mat Qty/U</label><input id="vSmq" type="number" value="2"/></div>
          <div class="field-group"><label>Std Mat Price</label><input id="vSmp" type="number" value="5"/></div>
          <div class="field-group"><label>Act Mat Qty</label><input id="vAmq" type="number" value="215"/></div>
          <div class="field-group"><label>Act Mat Price</label><input id="vAmp" type="number" value="5.4"/></div>
          <div class="field-group"><label>Std Labor Hrs/U</label><input id="vSlh" type="number" value="1.5"/></div>
          <div class="field-group"><label>Std Labor Rate</label><input id="vSlr" type="number" value="6"/></div>
          <div class="field-group"><label>Act Labor Hrs</label><input id="vAlh" type="number" value="145"/></div>
          <div class="field-group"><label>Act Labor Rate</label><input id="vAlr" type="number" value="6.2"/></div>
        </div>
        <button class="btn btn-gold" data-action="calcVariance">${t("Run Variance Decomposition", "حساب الانحرافات التفصيلية")}</button>
        <div id="varianceResultBox" style="margin-top:10px;"></div>
      </div>
    `;
  } else {
    const snap = state.costSnapshots[state.costSnapshots.length - 1];
    panel = !snap ? `<div class="card cream">${t("Save a cost snapshot first to run What-If Scenarios.", "احفظ لقطة تكلفة أولاً لتشغيل محاكي ماذا لو.")}</div>` : `
      <div class="card">
        <div class="row-between"><strong>${t("What-If Sensitivity Simulator", "محاكي ماذا لو")}</strong><span class="badge">${t("Scenario outcome — not a prediction", "نتيجة سيناريو — ليست توقعاً")}</span></div>
        <div class="subtabs" style="margin-top:8px;">
          ${Engine.SPEC_SCENARIO_PRESETS.map((p) => `<button class="subtab" data-preset="${p.key}">${esc(p.name)}</button>`).join("")}
        </div>
        <div class="grid-3">
          <div class="field-group"><label>Material %</label><input id="scMat" type="number" value="20"/></div>
          <div class="field-group"><label>FX Shock %</label><input id="scFx" type="number" value="15"/></div>
          <div class="field-group"><label>Volume %</label><input id="scVol" type="number" value="0"/></div>
          <div class="field-group"><label>Price %</label><input id="scPrice" type="number" value="0"/></div>
        </div>
        <button class="btn btn-primary" data-action="runScenario">${t("Simulate & Save Scenario", "محاكاة وحفظ السيناريو")}</button>
      </div>
      ${state.scenarios.slice().reverse().map((sc) => `
        <div class="card cream">
          <div class="row-between"><strong>${esc(sc.name)}</strong><span class="badge">${esc(sc.disclaimer)}</span></div>
          <div>Base Profit: ${sc.baseProfit} -> Scenario Profit: <strong style="color:${sc.scenarioProfit < 0 ? "var(--fvg-negative)" : "var(--fvg-positive)"};">${sc.scenarioProfit} ${esc(curr)}</strong></div>
        </div>
      `).join("")}
    `;
  }
  return `
    <div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="costing:${i}">${esc(l)}</button>`).join("")}</div>
    ${panel}
  `;
}

function renderLedgerTab() {
  const curr = state.activeOrg?.baseCurrency || "SDG";
  const sub = state.subTab.ledger;
  const subLabels = [t("Journal & COA", "القيود والدليل"), t("Statements", "القوائم المالية"), t("18 Ratios", "18 نسبة مالية"), t("AR & AP", "الذمم والعملاء")];
  const stmt = renderStatementsAndRatios();
  const ratios = Engine.computeFinancialRatios(stmt);
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Post Balanced Double-Entry Journal", "ترحيل قيد يومية مزدوج متوازن")}</h3>
        <div class="grid-2">
          <div class="field-group"><label>${t("Description", "البيان")}</label><input id="jeDesc" placeholder="Sales cash settlement"/></div>
          <div class="field-group"><label>${t("Amount", "المبلغ")} (${esc(curr)})</label><input id="jeAmt" type="number" step="0.01"/></div>
          <div class="field-group"><label>${t("Debit Account", "حساب المدين")}</label><select id="jeDr">${state.accounts.map((a) => `<option value="${a.code}">${a.code} ${esc(state.isArabic ? a.nameAr : a.name)}</option>`).join("")}</select></div>
          <div class="field-group"><label>${t("Credit Account", "حساب الدائن")}</label><select id="jeCr">${state.accounts.map((a, idx) => `<option value="${a.code}" ${idx === 7 ? "selected" : ""}>${a.code} ${esc(state.isArabic ? a.nameAr : a.name)}</option>`).join("")}</select></div>
        </div>
        <button class="btn btn-primary" data-action="postJe">${t("Post Double-Entry Journal", "ترحيل القيد")}</button>
      </div>
      ${state.journalEntries.slice().reverse().map((je) => `
        <div class="card">
          <div class="row-between"><strong>${esc(je.referenceNo)} • ${esc(je.date)}</strong><span class="badge">${esc(je.status)}</span></div>
          <div>${esc(je.description)} — ${je.total} ${esc(curr)}</div>
          ${je.status === "posted" ? `<button class="btn btn-outline" style="margin-top:8px;" data-reverse-je="${esc(je.id)}">${t("Reverse Entry", "عكس القيد")}</button>` : ""}
        </div>
      `).join("")}
    `;
  } else if (sub === 1) {
    panel = `
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Income Statement", "قائمة الدخل")}</h3>
        <div class="row-between"><span>Revenue</span><strong>${stmt.revenue} ${esc(curr)}</strong></div>
        <div class="row-between"><span>COGS</span><strong>(${stmt.cogs}) ${esc(curr)}</strong></div>
        <div class="row-between"><span>Operating Expenses</span><strong>(${stmt.operatingExpenses}) ${esc(curr)}</strong></div>
        <div class="row-between"><strong>Net Profit</strong><strong>${stmt.netProfit} ${esc(curr)}</strong></div>
      </div>
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">${t("Balance Sheet", "الميزانية العمومية")}</h3><span class="badge ${stmt.isAccountingEquationBalanced ? "low" : "high"}">${stmt.isAccountingEquationBalanced ? "A = L + E ✓" : "Check"}</span></div>
        <div class="row-between"><span>Total Assets</span><strong>${stmt.totalAssets} ${esc(curr)}</strong></div>
        <div class="row-between"><span>Total Liabilities</span><strong>${stmt.totalLiabilities} ${esc(curr)}</strong></div>
        <div class="row-between"><span>Total Equity (incl. Net Income)</span><strong>${stmt.totalEquityWithNetIncome} ${esc(curr)}</strong></div>
      </div>
      <div class="card table-wrap">
        <h3 style="margin-top:0;">${t("Trial Balance", "ميزان المراجعة")} (Dr ${stmt.totalTrialDebits} = Cr ${stmt.totalTrialCredits})</h3>
        <table><thead><tr><th>Code</th><th>Account</th><th>Debit</th><th>Credit</th></tr></thead>
        <tbody>${stmt.trialBalance.map((r) => `<tr><td>${r.code}</td><td>${esc(r.name)}</td><td>${r.debitBalance}</td><td>${r.creditBalance}</td></tr>`).join("")}</tbody></table>
      </div>
    `;
  } else if (sub === 2) {
    panel = ratios.map((r) => `
      <div class="card">
        <div class="row-between"><strong>${esc(r.key.replaceAll("_", " ").toUpperCase())}</strong><strong style="color:${r.value == null ? "var(--fvg-warning)" : "var(--fvg-forest-green)"};">${r.value == null ? "N/A" : `${r.value} ${r.unitSuffix}`}</strong></div>
        <div style="font-size:12px;color:var(--fvg-text-muted);">${esc(r.category)} • ${esc(r.formula)}</div>
        ${r.missingDataWarning ? `<div style="font-size:12px;color:var(--fvg-negative);">⚠ ${esc(r.missingDataWarning)}</div>` : ""}
      </div>
    `).join("");
  } else {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Receivables & Payables Tracker (AR / AP)", "متابعة الذمم المدينة والدائنة")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>Type</label><select id="cpType"><option value="receivable">Receivable (AR)</option><option value="payable">Payable (AP)</option></select></div>
          <div class="field-group"><label>Party Name</label><input id="cpParty" placeholder="Al-Nile Distributor"/></div>
          <div class="field-group"><label>Invoice #</label><input id="cpInv" placeholder="INV-101"/></div>
          <div class="field-group"><label>Amount (${esc(curr)})</label><input id="cpAmt" type="number" step="0.01"/></div>
          <div class="field-group"><label>Due Date</label><input id="cpDue" value="${new Date().toISOString().slice(0, 10)}"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveCounterparty">${t("Save AR/AP Record", "حفظ الذمة")}</button>
      </div>
      ${state.counterpartyItems.map((c) => `<div class="card"><div class="row-between"><strong>${esc(c.partyName)} (${esc(c.invoiceNo)})</strong><span class="badge">${esc(c.type.toUpperCase())} • ${esc(c.status)}</span></div><div>Amount: ${c.amount} | Paid: ${c.paidAmount} ${esc(curr)} • Due ${esc(c.dueDate)}</div></div>`).join("")}
    `;
  }
  return `
    <div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="ledger:${i}">${esc(l)}</button>`).join("")}</div>
    ${panel}
  `;
}

function renderStockTab() {
  const curr = state.activeOrg?.baseCurrency || "SDG";
  const sub = state.subTab.stock;
  const subLabels = [t("Inventory & Replacement", "المخزون وتكلفة الاستبدال"), t("Scrap & Losses", "الهدر والتلف"), t("Budgets", "الموازنات")];
  const bookTotal = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.avgCost), 0));
  const replTotal = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.marketPrice), 0));
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Book Value vs Current Replacement Cost", "القيمة الدفترية مقابل تكلفة الاستبدال")}</h3>
        <div class="row-between"><span>Accounting Book Value</span><strong>${bookTotal} ${esc(curr)}</strong></div>
        <div class="row-between"><span>Current Replacement Cost</span><strong>${replTotal} ${esc(curr)}</strong></div>
        <div class="row-between"><span>Replacement Capital Gap</span><strong style="color:var(--fvg-negative);">${Engine.money(replTotal - bookTotal)} ${esc(curr)}</strong></div>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Register Inventory Item (FIFO / MWA)", "إضافة صنف مخزني")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>SKU</label><input id="invSku" placeholder="BEN-01"/></div>
          <div class="field-group"><label>Item Name</label><input id="invName" placeholder="Green Coffee 60kg"/></div>
          <div class="field-group"><label>Method</label><select id="invMethod"><option value="fifo">FIFO</option><option value="moving_avg">Moving Avg</option></select></div>
          <div class="field-group"><label>Qty</label><input id="invQty" type="number" value="50"/></div>
          <div class="field-group"><label>Unit Cost</label><input id="invCost" type="number" value="100"/></div>
          <div class="field-group"><label>Market Price</label><input id="invMkt" type="number" value="130"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveInventory">${t("Create Inventory Item", "حفظ الصنف")}</button>
      </div>
      ${state.inventoryItems.map((it) => `
        <div class="card">
          <div class="row-between"><strong>${esc(it.sku)} • ${esc(it.name)}</strong><span class="badge">${esc(it.method.toUpperCase())}</span></div>
          <div>Qty: ${it.qty} | Book Avg: ${it.avgCost} | Market: ${it.marketPrice} ${esc(curr)}</div>
          <div style="display:flex;gap:8px;margin-top:8px;">
            <button class="btn btn-outline" data-stock-move="in:${esc(it.id)}">+10 Receipt</button>
            <button class="btn btn-outline" data-stock-move="out:${esc(it.id)}">-10 Issue</button>
          </div>
        </div>
      `).join("")}
    `;
  } else if (sub === 1) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Log Scrap / Damaged / Expired Loss", "تسجيل فاقد وتلف المخزون")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>Item</label><input id="scrItem" placeholder="Packaging Film"/></div>
          <div class="field-group"><label>Loss Type</label><select id="scrType"><option value="scrap">Scrap</option><option value="damaged">Damaged</option><option value="expired">Expired</option></select></div>
          <div class="field-group"><label>Qty</label><input id="scrQty" type="number" value="5"/></div>
          <div class="field-group"><label>Unit Cost</label><input id="scrCost" type="number" value="100"/></div>
          <div class="field-group"><label>Reason</label><input id="scrReason" placeholder="Heat sealing waste"/></div>
        </div>
        <button class="btn btn-danger" data-action="saveScrap">${t("Log Scrap Loss", "تسجيل الفاقد")}</button>
      </div>
      ${state.scrapLosses.map((s) => `<div class="card cream"><div class="row-between"><strong>${esc(s.itemName)} (${esc(s.lossType)})</strong><strong style="color:var(--fvg-negative);">-${s.totalLossCost} ${esc(curr)}</strong></div><div>Qty: ${s.quantity} • ${esc(s.reason)}</div></div>`).join("")}
    `;
  } else {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Budget & Variance", "إنشاء موازنة ومقارنتها بالفعلي")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>Budget Name</label><input id="bdgName" placeholder="Q4 Production"/></div>
          <div class="field-group"><label>Planned</label><input id="bdgPlan" type="number" value="5000"/></div>
          <div class="field-group"><label>Actual</label><input id="bdgAct" type="number" value="5400"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveBudget">${t("Save Budget", "حفظ الموازنة")}</button>
      </div>
      ${state.budgets.map((b) => `<div class="card"><div class="row-between"><strong>${esc(b.name)}</strong><span class="badge">${b.variancePct}%</span></div><div>Planned: ${b.planned} | Actual: ${b.actual} | Variance: ${b.variance} ${esc(curr)}</div></div>`).join("")}
    `;
  }
  return `
    <div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="stock:${i}">${esc(l)}</button>`).join("")}</div>
    ${panel}
  `;
}

function renderResearchTab() {
  const sub = state.subTab.research;
  const subLabels = [t("Nobo Thesis Workspace", "مساحة بحث نوبو"), t("Ben Al-Zahra Showcase", "بن الزهراء التجاري")];
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card cream">
        <h3 style="margin-top:0;">${t("The Role of Management Accounting Techniques in Pricing Industrial Products in an Inflationary Environment: A Field Study of Nobo Factory in Khartoum North, Sudan", "دور أساليب المحاسبة الإدارية في تسعير المنتجات الصناعية في ظل بيئة تضخمية: دراسة ميدانية على مصنع نوبو بالخرطوم بحري، السودان")}</h3>
        <p style="margin-bottom:4px;"><strong>${t("Researcher:", "الباحثة:")}</strong> Zahra Yousif Elabid Wagiealla Ahmed (زهراء يوسف العبد وقيع الله أحمد)</p>
        <p style="margin:0;"><strong>${t("Academic Supervisor:", "المشرف الأكاديمي:")}</strong> Dr. Fadl Allah Berier Gafa Ibrahim (د. فضل الله برير قفة إبراهيم)</p>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Academic Integrity Notice [Pending Field Data]", "سياسة النزاهة الأكاديمية [Pending Field Data]")}</h3>
        <p style="font-size:13px;color:var(--fvg-text-muted);">${t("Zero fabricated field survey sizes or invented Nobo factory numbers. Empirical items remain tagged [Pending Field Data] until entered by the researcher.", "لا توجد أي استبيانات مختلقة أو تكاليف مفبركة لمصنع نوبو؛ تبقى البنود الميدانية موسومة بحالة [Pending Field Data] حتى إدخال البيانات الموثقة.")}</p>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Add Research Record / Hypothesis", "إضافة سجل بحثي / فرضية")}</h3>
        <div class="grid-2">
          <div class="field-group"><label>Title</label><input id="resTitle" placeholder="H1 [Pending Field Data]"/></div>
          <div class="field-group"><label>Status</label><select id="resStatus"><option value="missing">missing [Pending]</option><option value="partial">partial</option><option value="available">available</option></select></div>
        </div>
        <div class="field-group"><label>Summary / Finding</label><textarea id="resSummary" rows="2"></textarea></div>
        <button class="btn btn-primary" data-action="saveResearch">${t("Save Research Record", "حفظ السجل البحثي")}</button>
      </div>
      ${state.researchRecords.map((r) => `<div class="card"><div class="row-between"><strong>${esc(r.title)}</strong><span class="badge">${esc(r.dataStatus)}</span></div><p style="margin:6px 0 0;">${esc(r.summary)}</p></div>`).join("")}
    `;
  } else {
    panel = `
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Ben Al-Zahra Commercial Project (Isolated Showcase)", "مشروع بن الزهراء التجاري (معزول عن نوبو)")}</h3>
        <img class="showcase-banner" src="./assets/fvg_ben_alzahra_products.png" onerror="this.style.display='none'" alt="Ben Al-Zahra"/>
        <p>${t("Independent commercial coffee & packaging costing demonstration inside FVG, strictly isolated from Nobo academic records.", "مرجع تجاري مستقل لتطبيق أساليب التكلفة والتسعير دون أي خلط مع بيانات البحث العلمي لمصنع نوبو.")}</p>
      </div>
    `;
  }
  return `
    <div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="research:${i}">${esc(l)}</button>`).join("")}</div>
    ${panel}
  `;
}

function renderReportsAiTab() {
  const curr = state.activeOrg?.baseCurrency || "SDG";
  const sub = state.subTab.reports;
  const subLabels = [t("Reports & Export", "التقارير والتصدير"), t("AI Modes", "أوضاع الذكاء"), t("Org & Payment", "المنظمة والدفع")];
  const stmt = renderStatementsAndRatios();
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Verified Financial Reports & Exports", "التقارير المالية والتصدير")}</h3>
        <div class="field-group">
          <label>${t("Report Dataset", "التقرير")}</label>
          <select id="repSelect">
            <option value="statements">Financial Statements</option>
            <option value="prices">Prices History</option>
            <option value="costing">8-Method Costing Snapshots</option>
            <option value="ratios">18 Financial Ratios</option>
          </select>
        </div>
        <div class="grid-3">
          <button class="btn btn-primary" data-action="exportCsv">Export CSV</button>
          <button class="btn btn-gold" data-action="exportJson">Export JSON</button>
          <button class="btn btn-outline" data-action="printPdf">Print / Save PDF</button>
        </div>
      </div>
      <div class="card cream">
        <div class="row-between"><strong>Net Profit: ${stmt.netProfit} ${esc(curr)}</strong><strong>Total Assets: ${stmt.totalAssets} ${esc(curr)}</strong></div>
      </div>
    `;
  } else if (sub === 1) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("3-Mode Grounded Workspace Analyzer", "المساعد التحليلي بـ 3 أوضاع")}</h3>
        <div class="subtabs">
          ${["general", "financial", "research"].map((m) => `<button class="subtab ${state.aiMode === m ? "active" : ""}" data-ai-mode="${m}">${m.toUpperCase()}</button>`).join("")}
        </div>
        <div class="field-group"><label>${t("Question", "سؤالك المالي أو البحثي")}</label><textarea id="aiPrompt" rows="2" placeholder="Summarize margins, replacement gap, and Nobo readiness"></textarea></div>
        <div class="grid-2">
          <button class="btn btn-primary" data-action="askAi">${t("Analyze Verified Records", "تحليل البيانات الموثقة")}</button>
          <button class="btn btn-outline" data-action="exportTranscript">${t("Export Transcript TXT", "تصدير المحادثة TXT")}</button>
        </div>
        ${state.aiResponse ? `<div class="card cream" style="margin-top:12px;white-space:pre-wrap;">${esc(state.aiResponse)}</div>` : ""}
      </div>
    `;
  } else {
    panel = `
      ${renderTrialBanner(false)}
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Submit Payment Proof (Bankak / O-Cash / Bank Transfer)", "تقديم إثبات الدفع (بنكك / أوكاش / تحويل بنكي)")}</h3>
        <p style="font-size:12px;">${t("Visa/MasterCard Adapter Notice: No online card/CVV collection is enabled. Submit Bankak, O-Cash, or Bank Transfer receipt reference for Admin approval.", "ملاحظة Visa: لا يتم جمع أرقام البطاقات. التفعيل يتم بإثبات بنكك / أوكاش / التحويل البنكي.")}</p>
        <div class="grid-3">
          <div class="field-group"><label>Plan</label><select id="payPlan"><option value="starter">Starter ($12)</option><option value="professional" selected>Professional ($29)</option><option value="enterprise">Enterprise ($79)</option></select></div>
          <div class="field-group"><label>Method</label><select id="payMethod"><option value="bankak">Bankak</option><option value="ocash">O-Cash</option><option value="bank_transfer">Bank Transfer</option></select></div>
          <div class="field-group"><label>Amount</label><input id="payAmt" type="number" value="29"/></div>
          <div class="field-group"><label>Sender Name</label><input id="paySender" placeholder="Full Name"/></div>
          <div class="field-group"><label>Reference No</label><input id="payRef" placeholder="BNK-2026-01"/></div>
        </div>
        <button class="btn btn-gold" data-action="submitPayment">${t("Submit Proof [pending_verification]", "إرسال إثبات الدفع للمراجعة")}</button>
      </div>
      ${state.paymentRequests.map((p) => `
        <div class="card">
          <div class="row-between"><strong>${esc(p.paymentMethod.toUpperCase())} • ${esc(p.referenceNo)}</strong><span class="badge">${esc(p.status)}</span></div>
          <div>${esc(p.planTier)} • ${p.amount} • ${esc(p.senderName)}</div>
          ${p.status === "pending_verification" ? `<button class="btn btn-primary" style="margin-top:8px;" data-approve-pay="${esc(p.id)}">${t("Admin: Approve & Activate Plan", "اعتماد وتفعيل الباقة")}</button>` : ""}
        </div>
      `).join("")}
      <div class="card">
        <h3 style="margin-top:0;">${t("Install Website on iPhone / Android", "تثبيت الموقع كتطبيق على iPhone أو Android")}</h3>
        <p style="font-size:13px;margin:0 0 10px;">${t("On iPhone Safari: Tap Share -> 'Add to Home Screen'. On Android Chrome: Tap Menu -> 'Install app'.", "في iPhone (Safari): اضغط زر المشاركة ثم 'إضافة إلى الشاشة الرئيسية'. في Android Chrome: اختر 'تثبيت التطبيق'.")}</p>
        <button class="btn btn-danger" data-action="signOut">${t("Sign Out / Switch Workspace", "تسجيل الخروج")}</button>
      </div>
    `;
  }
  return `
    <div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="reports:${i}">${esc(l)}</button>`).join("")}</div>
    ${panel}
  `;
}

function renderAppShell() {
  const nav = [
    { idx: 0, label: t("Home", "الرئيسية"), icon: "🏠" },
    { idx: 1, label: t("Prices", "الأسعار"), icon: "📈" },
    { idx: 2, label: t("Costing", "التكاليف"), icon: "🧮" },
    { idx: 3, label: t("Ledger", "المحاسبة"), icon: "⚖️" },
    { idx: 4, label: t("Stock", "المخزون"), icon: "📦" },
    { idx: 5, label: t("Research", "البحث"), icon: "🎓" },
    { idx: 6, label: t("AI & Org", "الذكاء"), icon: "✨" }
  ];

  let screenHtml = "";
  if (isTrialLocked() && state.appTab >= 1 && state.appTab <= 4) {
    screenHtml = renderTrialGateCard();
  } else {
    if (state.appTab === 0) screenHtml = renderHomeTab();
    else if (state.appTab === 1) screenHtml = renderPricesTab();
    else if (state.appTab === 2) screenHtml = renderCostingTab();
    else if (state.appTab === 3) screenHtml = renderLedgerTab();
    else if (state.appTab === 4) screenHtml = renderStockTab();
    else if (state.appTab === 5) screenHtml = renderResearchTab();
    else screenHtml = renderReportsAiTab();
  }

  return `
    <div class="app-shell">
      <aside class="side-rail">
        <div class="rail-brand">
          <img src="./assets/fvg_brand_symbol.png" onerror="this.src='./assets/fvg-icon.svg'" alt="FVG"/>
          <div><div style="font-weight:800;">FVG</div><div style="font-size:11px;color:#DDB065;">${esc(state.activeOrg?.name || "Workspace")}</div></div>
        </div>
        ${nav.map((n) => `<button class="nav-btn ${state.appTab === n.idx ? "active" : ""}" data-tab="${n.idx}"><span>${n.icon}</span><span>${esc(n.label)}</span></button>`).join("")}
      </aside>
      <main class="main-content">
        ${renderBanner(state.activeOrg?.name || "FVG", `${state.user?.email || ""} • ${state.activeOrg?.baseCurrency || "SDG"}`)}
        ${state.toast ? `<div class="card cream" style="border-color:var(--fvg-warm-gold);font-weight:700;">${esc(state.toast)}</div>` : ""}
        ${screenHtml}
      </main>
      <nav class="bottom-nav">
        ${nav.map((n) => `<button class="nav-btn ${state.appTab === n.idx ? "active" : ""}" data-tab="${n.idx}"><span>${n.icon}</span><span>${esc(n.label)}</span></button>`).join("")}
      </nav>
    </div>
  `;
}

function downloadFile(filename, content, mime) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function bindEvents() {
  const root = document.getElementById("app");
  if (!root) return;

  const splash = document.getElementById("splashRoot");
  if (splash) {
    splash.onclick = () => {
      state.stage = "developer";
      render();
    };
  }

  root.querySelectorAll("[data-public-page]").forEach((btn) => {
    btn.onclick = () => {
      state.publicPage = Number(btn.dataset.publicPage);
      render();
    };
  });

  root.querySelectorAll("[data-tab]").forEach((btn) => {
    btn.onclick = () => {
      state.appTab = Number(btn.dataset.tab);
      render();
    };
  });

  root.querySelectorAll("[data-sub]").forEach((btn) => {
    btn.onclick = () => {
      const [group, idx] = btn.dataset.sub.split(":");
      state.subTab[group] = Number(idx);
      render();
    };
  });

  root.querySelectorAll("[data-ai-mode]").forEach((btn) => {
    btn.onclick = () => {
      state.aiMode = btn.dataset.aiMode;
      render();
    };
  });

  root.querySelectorAll("[data-preset]").forEach((btn) => {
    btn.onclick = () => {
      const p = Engine.SPEC_SCENARIO_PRESETS.find((x) => x.key === btn.dataset.preset);
      if (!p) return;
      const setV = (id, v) => { const el = document.getElementById(id); if (el) el.value = v; };
      setV("scMat", p.matPct);
      setV("scFx", p.fxPct);
      setV("scVol", p.volumePct);
      setV("scPrice", p.pricePct);
    };
  });

  root.querySelectorAll("[data-approve-pay]").forEach((btn) => {
    btn.onclick = () => {
      const req = state.paymentRequests.find((r) => r.id === btn.dataset.approvePay);
      if (req && state.activeOrg) {
        req.status = "approved";
        state.activeOrg.planTier = req.planTier;
        state.activeOrg.subscriptionStatus = "active";
        logAudit("APPROVE_PAYMENT", "paymentRequest", `Approved ${req.referenceNo} (${req.planTier})`);
        persist();
        showToast("Payment approved and plan activated.");
      }
    };
  });

  const costSel = document.getElementById("costMethod");
  if (costSel) {
    costSel.onchange = () => { state.costDraftMethod = costSel.value; };
  }

  root.querySelectorAll("[data-action]").forEach((btn) => {
    btn.onclick = () => {
      const act = btn.dataset.action;
      if (act === "toggleLang") {
        state.isArabic = !state.isArabic;
        persist();
        render();
      } else if (act === "toggleTheme") {
        state.isDark = !state.isDark;
        persist();
        render();
      } else if (act === "enterPublicOrApp") {
        state.stage = state.user && state.activeOrg ? "app" : "public";
        render();
      } else if (act === "goBilling") {
        state.appTab = 6;
        state.subTab.reports = 2;
        render();
      } else if (act === "signOut") {
        state.stage = "public";
        render();
      } else if (act === "launchWorkspace") {
        const email = document.getElementById("authEmailInput")?.value?.trim();
        const orgName = document.getElementById("authOrgInput")?.value?.trim() || "FVG Workspace";
        if (isFirebaseWebConfigured()) {
          launchWithFirebaseWeb(orgName);
          return;
        }
        if (!email || !email.includes("@")) {
          showToast(t("Please enter a valid email address.", "يرجى إدخال بريد إلكتروني صحيح."));
          return;
        }
        if (!state.user || state.user.email !== email) {
          state.user = { email, trialStartedAtMillis: Date.now() };
          state.activeOrg = {
            id: "org_" + Date.now(),
            name: orgName,
            baseCurrency: "SDG",
            planTier: "trial",
            subscriptionStatus: "trial",
            trialEndsAtMillis: Date.now() + Engine.TRIAL_DURATION_MILLIS
          };
          logAudit("INIT_WORKSPACE", "organization", `Initialized ${orgName} (72h trial)`);
        }
        state.stage = "app";
        persist();
        render();
      } else if (act === "savePrice") {
        const name = document.getElementById("prName").value.trim();
        const sku = document.getElementById("prSku").value.trim() || "SKU";
        const price = Engine.money(document.getElementById("prVal").value);
        if (!name || price <= 0) return showToast("Product name & positive price required.");
        recordPriceObservationItem(name, sku, price);
        persist();
        showToast("Price observation saved.");
      } else if (act === "importCsv") {
        const fileEl = document.getElementById("csvFile");
        const pasteText = document.getElementById("csvPaste")?.value?.trim() || "";
        const handleCsvText = (txt) => {
          const lines = txt.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
          let count = 0;
          lines.forEach((line, idx) => {
            const cols = line.split(",").map((c) => c.trim());
            if (idx === 0 && cols[0].toLowerCase() === "name") return;
            const name = cols[0];
            const sku = cols[1] || "CSV";
            const price = Engine.money(cols[3] || cols[2]);
            if (name && price > 0) {
              recordPriceObservationItem(name, sku, price);
              count++;
            }
          });
          persist();
          showToast(count ? `Imported ${count} CSV product prices.` : "No valid rows found in CSV.");
        };
        if (fileEl && fileEl.files && fileEl.files[0]) {
          fileEl.files[0].text().then(handleCsvText);
        } else {
          handleCsvText(pasteText);
        }
      } else if (act === "saveInflation") {
        const period = document.getElementById("infPeriod").value.trim();
        const monthlyRate = Engine.pct4(document.getElementById("infMonthly").value);
        const sourceType = document.getElementById("infSource").value;
        if (!period) return;
        state.inflationRecords.unshift({ id: "inf_" + Date.now(), period, monthlyRate, sourceType });
        persist();
        showToast("Inflation record saved.");
      } else if (act === "saveBasket") {
        const name = document.getElementById("bskName").value.trim() || "Basket";
        const item = document.getElementById("bskItem").value.trim() || "Input";
        const basePrice = Engine.n(document.getElementById("bskBase").value);
        const currentPrice = Engine.n(document.getElementById("bskCurr").value);
        const quantity = Engine.n(document.getElementById("bskQty").value);
        const weight = Engine.n(document.getElementById("bskWeight").value);
        const res = Engine.calculateWeightedBasket([{ quantity, weight, basePrice, currentPrice }]);
        state.baskets.unshift({ id: "bsk_" + Date.now(), name, item, ...res });
        persist();
        showToast(`Weighted basket saved (Index ${res.indexValue ?? "N/A"}).`);
      } else if (act === "saveFx") {
        const base = document.getElementById("fxBase").value.trim().toUpperCase();
        const quote = document.getElementById("fxQuote").value.trim().toUpperCase();
        const rate = Engine.pct4(document.getElementById("fxRate").value);
        if (rate <= 0) return showToast("Positive FX rate required.");
        state.fxRates.unshift({ id: "fx_" + Date.now(), base, quote, rate, date: new Date().toISOString().slice(0, 10) });
        persist();
        showToast("FX rate saved.");
      } else if (act === "saveCosting") {
        const method = document.getElementById("costMethod").value;
        const productName = document.getElementById("cProd").value.trim() || "Product";
        const extra = Engine.n(document.getElementById("cExtra").value);
        const input = {
          directMaterial: Engine.n(document.getElementById("cMat").value),
          indirectMaterial: 0,
          directLabor: Engine.n(document.getElementById("cLab").value),
          indirectLabor: 0,
          overhead: Engine.n(document.getElementById("cOvh").value),
          energyFuel: 0, packaging: 0, transport: 0, wasteScrap: 0,
          fixedCosts: Engine.n(document.getElementById("cFix").value),
          goodUnits: Engine.n(document.getElementById("cUnits").value),
          sellingPrice: Engine.n(document.getElementById("cPrice").value)
        };
        const out = Engine.calculateCostingByMethod(method, input, extra, extra);
        const latestInf = state.inflationRecords[0]?.monthlyRate ?? null;
        const stmt = renderStatementsAndRatios();
        const risk = Engine.evaluateFinancialRisk(out.coreResult.profitMarginPct, latestInf, stmt.totalLiabilities, stmt.totalEquityWithNetIncome);
        state.costSnapshots.push({
          id: "cs_" + Date.now(),
          productName,
          method,
          input,
          sellingPrice: input.sellingPrice,
          coreResult: out.coreResult,
          methodNote: out.methodNote,
          risk
        });
        persist();
        showToast(`Saved ${method.toUpperCase()} costing (${risk.level.toUpperCase()} risk).`);
      } else if (act === "calcVariance") {
        const d = Engine.computeDetailedVariance({
          goodUnits: document.getElementById("vUnits").value,
          stdMaterialQtyPerUnit: document.getElementById("vSmq").value,
          stdMaterialPrice: document.getElementById("vSmp").value,
          actualMaterialQty: document.getElementById("vAmq").value,
          actualMaterialPrice: document.getElementById("vAmp").value,
          stdLaborHoursPerUnit: document.getElementById("vSlh").value,
          stdLaborRate: document.getElementById("vSlr").value,
          actualLaborHours: document.getElementById("vAlh").value,
          actualLaborRate: document.getElementById("vAlr").value,
          stdOverheadPerUnit: 0,
          actualOverheadCost: 0
        });
        const box = document.getElementById("varianceResultBox");
        if (box) {
          box.innerHTML = `
            <div class="card cream">
              <div>Mat Price Var: <strong>${d.materialPriceVariance}</strong> | Mat Usage Var: <strong>${d.materialUsageVariance}</strong></div>
              <div>Labor Rate Var: <strong>${d.laborRateVariance}</strong> | Labor Eff Var: <strong>${d.laborEfficiencyVariance}</strong></div>
              <div>Total Cost Variance: <strong>${d.totalVariance}</strong></div>
            </div>
          `;
        }
      } else if (act === "runScenario") {
        const snap = state.costSnapshots[state.costSnapshots.length - 1];
        if (!snap) return;
        const sim = Engine.runExtendedScenario({
          baseMaterial: snap.input.directMaterial,
          baseLabor: snap.input.directLabor,
          baseOverheadAndOther: snap.input.overhead + snap.input.fixedCosts,
          baseRevenue: snap.coreResult.revenue,
          materialChangePct: document.getElementById("scMat").value,
          fxChangePct: document.getElementById("scFx").value,
          volumeChangePct: document.getElementById("scVol").value,
          priceChangePct: document.getElementById("scPrice").value
        });
        state.scenarios.push({ id: "sc_" + Date.now(), name: `Shock (${snap.productName})`, ...sim });
        persist();
        showToast("What-If Scenario saved.");
      } else if (act === "postJe") {
        const desc = document.getElementById("jeDesc").value.trim();
        const amt = Engine.money(document.getElementById("jeAmt").value);
        const dr = document.getElementById("jeDr").value;
        const cr = document.getElementById("jeCr").value;
        if (!desc || amt <= 0 || dr === cr) return showToast("Valid description, positive amount, and distinct Debit/Credit accounts required.");
        state.journalEntries.push({
          id: "je_" + Date.now(),
          referenceNo: "JE-" + String(state.journalEntries.length + 1).padStart(3, "0"),
          date: new Date().toISOString().slice(0, 10),
          description: desc,
          status: "posted",
          total: amt,
          lines: [
            { accountCode: dr, debit: amt, credit: 0 },
            { accountCode: cr, debit: 0, credit: amt }
          ]
        });
        persist();
        showToast("Balanced double-entry journal posted.");
      } else if (act === "saveInventory") {
        const sku = document.getElementById("invSku").value.trim();
        const name = document.getElementById("invName").value.trim();
        if (!sku || !name) return showToast("SKU & Name required.");
        state.inventoryItems.push({
          id: "inv_" + Date.now(),
          sku,
          name,
          method: document.getElementById("invMethod").value,
          qty: Engine.n(document.getElementById("invQty").value),
          avgCost: Engine.n(document.getElementById("invCost").value),
          marketPrice: Engine.n(document.getElementById("invMkt").value)
        });
        persist();
        showToast("Inventory item saved.");
      } else if (act === "saveBudget") {
        const name = document.getElementById("bdgName").value.trim();
        const planned = Engine.money(document.getElementById("bdgPlan").value);
        const actual = Engine.money(document.getElementById("bdgAct").value);
        if (!name || planned <= 0) return showToast("Budget name and planned amount required.");
        const variance = Engine.money(actual - planned);
        const variancePct = Engine.pct4((variance / planned) * 100);
        state.budgets.push({ id: "bdg_" + Date.now(), name, planned, actual, variance, variancePct });
        persist();
        showToast("Budget saved.");
      } else if (act === "saveResearch") {
        const title = document.getElementById("resTitle").value.trim();
        const summary = document.getElementById("resSummary").value.trim();
        const dataStatus = document.getElementById("resStatus").value;
        if (!title || !summary) return showToast("Research title and summary required.");
        state.researchRecords.unshift({ id: "res_" + Date.now(), title, summary, dataStatus });
        persist();
        showToast("Research record saved.");
      } else if (act === "askAi") {
        const q = document.getElementById("aiPrompt").value.trim();
        if (!q) return;
        const stmt = renderStatementsAndRatios();
        const ans = [
          `[Mode: ${state.aiMode.toUpperCase()} — Grounded in Workspace Records]`,
          `Organization: ${state.activeOrg?.name || "None"} (${state.activeOrg?.baseCurrency || "SDG"})`,
          `Products Tracked: ${state.products.length} | Cost Snapshots: ${state.costSnapshots.length} | Posted JEs: ${state.journalEntries.length}`,
          `Revenue: ${stmt.revenue} | Gross Profit: ${stmt.grossProfit} | Net Profit: ${stmt.netProfit}`,
          `Research Records: ${state.researchRecords.length} (Nobo study keeps uncollected empirical items as [Pending Field Data]).`,
          `Prompt Answered: "${q}"`
        ].join("\n");
        state.aiResponse = ans;
        state.chatTranscript.push({ role: `User [${state.aiMode}]`, text: q }, { role: `FVG [${state.aiMode}]`, text: ans });
        persist();
        render();
      } else if (act === "exportTranscript") {
        const txt = state.chatTranscript.map((m, i) => `[${i + 1}] ${m.role}:\n${m.text}\n`).join("\n") || "No transcript messages yet.";
        downloadFile("fvg_ai_transcript.txt", txt, "text/plain");
      } else if (act === "exportCsv") {
        const stmt = renderStatementsAndRatios();
        const csv = `Metric,Value\nRevenue,${stmt.revenue}\nCOGS,${stmt.cogs}\nNet Profit,${stmt.netProfit}\nTotal Assets,${stmt.totalAssets}\nTotal Liabilities,${stmt.totalLiabilities}\nEquity,${stmt.totalEquityWithNetIncome}\n`;
        downloadFile("fvg_report.csv", csv, "text/csv");
      } else if (act === "exportJson") {
        downloadFile("fvg_workspace_backup.json", JSON.stringify(state, null, 2), "application/json");
      } else if (act === "printPdf") {
        window.print();
      } else if (act === "submitPayment") {
        const senderName = document.getElementById("paySender").value.trim();
        const referenceNo = document.getElementById("payRef").value.trim();
        if (!senderName || referenceNo.length < 2) return showToast("Sender name and reference number required.");
        state.paymentRequests.unshift({
          id: "pay_" + Date.now(),
          planTier: document.getElementById("payPlan").value,
          paymentMethod: document.getElementById("payMethod").value,
          amount: document.getElementById("payAmt").value,
          senderName,
          referenceNo,
          status: "pending_verification"
        });
        persist();
        showToast("Payment proof submitted [pending_verification].");
      } else if (act === "saveCounterparty") {
        const partyName = document.getElementById("cpParty").value.trim();
        const invoiceNo = document.getElementById("cpInv").value.trim() || "INV";
        const amount = Engine.money(document.getElementById("cpAmt").value);
        if (!partyName || amount <= 0) return showToast("Party name and positive amount required.");
        state.counterpartyItems.unshift({
          id: "cp_" + Date.now(),
          type: document.getElementById("cpType").value,
          partyName,
          invoiceNo,
          amount,
          paidAmount: 0,
          dueDate: document.getElementById("cpDue").value,
          status: "open"
        });
        persist();
        showToast("AR/AP counterparty record saved.");
      } else if (act === "saveScrap") {
        const itemName = document.getElementById("scrItem").value.trim();
        const qty = Engine.n(document.getElementById("scrQty").value);
        const cost = Engine.n(document.getElementById("scrCost").value);
        const reason = document.getElementById("scrReason").value.trim() || "Waste";
        if (!itemName || qty <= 0) return showToast("Item name and positive quantity required.");
        state.scrapLosses.unshift({
          id: "scr_" + Date.now(),
          itemName,
          lossType: document.getElementById("scrType").value,
          quantity: qty,
          unitCost: cost,
          totalLossCost: Engine.money(qty * cost),
          reason
        });
        persist();
        showToast("Scrap/damage loss logged.");
      }
    };
  });

  root.querySelectorAll("[data-reverse-je]").forEach((btn) => {
    btn.onclick = () => {
      const orig = state.journalEntries.find((j) => j.id === btn.dataset.reverseJe);
      if (!orig || orig.status !== "posted") return;
      orig.status = "reversed";
      state.journalEntries.push({
        id: "je_rev_" + Date.now(),
        referenceNo: orig.referenceNo + "-REV",
        date: new Date().toISOString().slice(0, 10),
        description: "Reversal of " + orig.referenceNo + ": " + orig.description,
        status: "posted",
        total: orig.total,
        lines: orig.lines.map((l) => ({ accountCode: l.accountCode, debit: l.credit, credit: l.debit }))
      });
      persist();
      showToast("Journal reversed via offsetting contra entry.");
    };
  });

  root.querySelectorAll("[data-stock-move]").forEach((btn) => {
    btn.onclick = () => {
      const [dir, itemId] = btn.dataset.stockMove.split(":");
      const item = state.inventoryItems.find((i) => i.id === itemId);
      if (!item) return;
      if (dir === "in") item.qty = Engine.money(Engine.n(item.qty) + 10);
      else item.qty = Math.max(0, Engine.money(Engine.n(item.qty) - 10));
      persist();
      showToast(`${item.sku} stock updated to ${item.qty} units.`);
    };
  });
}

function recordPriceObservationItem(name, sku, price) {
  const existing = state.products.find((p) => p.name.toLowerCase() === name.toLowerCase());
  const prev = existing ? existing.currentPrice : 0;
  const delta = Engine.calculatePercentageDelta(prev, price);
  if (existing) existing.currentPrice = price;
  else state.products.push({ id: "p_" + Date.now() + "_" + Math.random().toString(36).slice(2, 6), name, sku, currentPrice: price });
  state.priceObservations.push({
    id: "obs_" + Date.now() + "_" + Math.random().toString(36).slice(2, 6),
    productName: name,
    sku,
    previousPrice: prev,
    price,
    pctChange: delta.pctChange,
    direction: delta.direction,
    isUnusual: delta.isUnusual,
    date: new Date().toISOString().slice(0, 10)
  });
  logAudit("SAVE_PRICE", "priceObservation", `${name}: ${price}`);
}

async function launchWithFirebaseWeb(orgName) {
  try {
    const [{ initializeApp }, { getAuth, GoogleAuthProvider, signInWithPopup }, { getFirestore, doc, getDoc, setDoc, serverTimestamp, Timestamp }] = await Promise.all([
      import("https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js"),
      import("https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js"),
      import("https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js")
    ]);
    const app = initializeApp(FVG_FIREBASE_CONFIG);
    const auth = getAuth(app);
    const cred = await signInWithPopup(auth, new GoogleAuthProvider());
    const db = getFirestore(app, FVG_FIREBASE_CONFIG.databaseId);
    const uid = cred.user.uid;
    const email = cred.user.email || "";
    const uRef = doc(db, "users", uid);
    const uSnap = await getDoc(uRef);
    let orgId = uSnap.exists() ? uSnap.data().activeOrgId : null;
    if (!orgId) {
      orgId = "org_" + uid.slice(0, 12);
      const trialEnd = Timestamp.fromMillis(Date.now() + Engine.TRIAL_DURATION_MILLIS);
      await setDoc(doc(db, "organizations", orgId), {
        name: orgName,
        baseCurrency: "SDG",
        ownerId: uid,
        memberUids: [uid],
        roles: { [uid]: "owner" },
        planTier: "trial",
        subscriptionStatus: "trial",
        trialDurationHours: 72,
        trialStartedAt: serverTimestamp(),
        trialEndsAt: trialEnd,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
      await setDoc(uRef, {
        userId: uid,
        email,
        displayName: cred.user.displayName || email,
        activeOrgId: orgId,
        preferredLanguage: state.isArabic ? "ar" : "en",
        darkMode: state.isDark,
        trialDurationHours: 72,
        trialStartedAt: serverTimestamp(),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
    }
    const oSnap = await getDoc(doc(db, "organizations", orgId));
    const od = oSnap.data();
    state.user = { uid, email, trialStartedAtMillis: Date.now() };
    state.activeOrg = {
      id: orgId,
      name: od?.name || orgName,
      baseCurrency: od?.baseCurrency || "SDG",
      planTier: od?.planTier || "trial",
      subscriptionStatus: od?.subscriptionStatus || "trial",
      trialEndsAtMillis: od?.trialEndsAt?.toMillis ? od.trialEndsAt.toMillis() : (Date.now() + Engine.TRIAL_DURATION_MILLIS)
    };
    state.stage = "app";
    persist();
    render();
    showToast("Signed in via Google & synced with FVG Firestore.");
  } catch (err) {
    showToast("Firebase Web Auth failed: " + (err?.message || "Check firebase-config.js"));
  }
}

function render() {
  document.documentElement.dir = state.isArabic ? "rtl" : "ltr";
  document.documentElement.lang = state.isArabic ? "ar" : "en";
  document.documentElement.setAttribute("data-theme", state.isDark ? "dark" : "light");

  const root = document.getElementById("app");
  if (!root) return;

  if (state.stage === "splash") root.innerHTML = renderSplash();
  else if (state.stage === "developer") root.innerHTML = renderDeveloperShowcase();
  else if (state.stage === "public") root.innerHTML = renderPublicSite();
  else root.innerHTML = renderAppShell();

  bindEvents();
}

setInterval(() => {
  document.querySelectorAll("[data-trial-banner]").forEach((el) => {
    const raw = el.dataset.trialBanner;
    const target = raw === "preview" ? Date.now() + Engine.TRIAL_DURATION_MILLIS : Number(raw);
    if (!Number.isFinite(target)) return;
    const cd = Engine.computeTrialCountdown(target, Date.now());
    const pad = (v) => String(v).padStart(2, "0");
    const setT = (k, val) => { const node = el.querySelector(`[data-cd="${k}"]`); if (node) node.textContent = pad(val); };
    setT("d", cd.days); setT("h", cd.hours); setT("m", cd.minutes); setT("s", cd.seconds);
  });
}, 1000);

render();
