export const MODEL_VERSION = "2.1.0";
export const TRIAL_DURATION_HOURS = 72;
export const TRIAL_DURATION_MILLIS = TRIAL_DURATION_HOURS * 3600 * 1000;

export const ALL_COSTING_METHODS = [
  "actual",
  "standard",
  "target",
  "abc",
  "job_order",
  "process",
  "absorption",
  "variable"
];

export const SPEC_SCENARIO_PRESETS = [
  { key: "material_plus_20", name: "Raw Materials +20%", matPct: 20, labPct: 0, ovhPct: 0, pricePct: 0, fxPct: 0, volumePct: 0 },
  { key: "fx_plus_15", name: "Exchange Rate +15%", matPct: 15, labPct: 0, ovhPct: 5, pricePct: 0, fxPct: 15, volumePct: 0 },
  { key: "price_minus_10", name: "Selling Price -10%", matPct: 0, labPct: 0, ovhPct: 0, pricePct: -10, fxPct: 0, volumePct: 0 },
  { key: "volume_minus_25", name: "Volume -25%", matPct: 0, labPct: 0, ovhPct: 0, pricePct: 0, fxPct: 0, volumePct: -25 }
];

export function n(v) {
  const num = Number(v);
  return Number.isFinite(num) ? num : 0;
}

export function money(v) {
  return Math.round((n(v) + Number.EPSILON) * 100) / 100;
}

export function pct4(v) {
  return Math.round((n(v) + Number.EPSILON) * 10000) / 10000;
}

export function calculatePercentageDelta(previousPrice, newPrice) {
  const absChange = money(n(newPrice) - n(previousPrice));
  const direction = absChange > 0 ? "up" : absChange < 0 ? "down" : "unchanged";
  if (n(previousPrice) <= 0) {
    return { absChange, pctChange: null, direction, isUnusual: false };
  }
  const pctChange = pct4((absChange / n(previousPrice)) * 100);
  return { absChange, pctChange, direction, isUnusual: Math.abs(pctChange) >= 15 };
}

export function calculateCompoundedInflation(monthlyRates) {
  if (!monthlyRates.length) return 0;
  let factor = 1;
  for (const r of monthlyRates) {
    factor *= 1 + n(r) / 100;
  }
  return pct4((factor - 1) * 100);
}

export function calculateWeightedBasket(items) {
  let baseTotal = 0;
  let currTotal = 0;
  for (const it of items) {
    const qtyWeight = n(it.quantity) * n(it.weight || 1);
    baseTotal += qtyWeight * n(it.basePrice);
    currTotal += qtyWeight * n(it.currentPrice);
  }
  const baseCost = money(baseTotal);
  const currentCost = money(currTotal);
  const indexValue = baseCost > 0 ? pct4((currentCost / baseCost) * 100) : null;
  const basketInflationPct = baseCost > 0 ? pct4(((currentCost - baseCost) / baseCost) * 100) : null;
  return { baseCost, currentCost, indexValue, basketInflationPct };
}

export function calculateCosting(input) {
  const units = n(input.goodUnits);
  if (units <= 0) throw new Error("Good units must be greater than zero");

  const variableTotal =
    n(input.directMaterial) +
    n(input.indirectMaterial) +
    n(input.directLabor) +
    n(input.indirectLabor) +
    n(input.overhead) +
    n(input.energyFuel) +
    n(input.packaging) +
    n(input.transport) +
    n(input.wasteScrap);

  const fixed = n(input.fixedCosts);
  const totalCost = money(variableTotal + fixed);
  const unitCost = money(totalCost / units);
  const unitVarCost = variableTotal / units;
  const price = money(input.sellingPrice);
  const revenue = money(price * units);
  const grossProfit = money(revenue - variableTotal);
  const netProfit = money(revenue - totalCost);

  const profitMarginPct = price > 0 ? pct4(((price - unitCost) / price) * 100) : null;
  const markupPct = unitCost > 0 ? pct4(((price - unitCost) / unitCost) * 100) : null;

  const unitContribution = price - unitVarCost;
  const breakEvenUnits = fixed > 0 && unitContribution > 0 ? Math.ceil(fixed / unitContribution) : null;

  return {
    totalCost,
    unitCost,
    unitVarCost: money(unitVarCost),
    revenue,
    grossProfit,
    netProfit,
    profitMarginPct,
    markupPct,
    breakEvenUnits
  };
}

export function calculateCostingByMethod(method, input, targetMarginPct = 20, completedEquivUnits = null) {
  const norm = ALL_COSTING_METHODS.includes(method) ? method : "actual";
  let effectiveInput = { ...input };
  let effectiveUnits = n(input.goodUnits);
  let targetAllowableUnitCost = null;
  let targetCostGapPerUnit = null;
  let note = "";

  if (norm === "variable") {
    effectiveInput.fixedCosts = 0;
    note = "Variable Costing excludes Fixed Overhead from unit product cost (period expense).";
  } else if (norm === "process") {
    const eq = completedEquivUnits && n(completedEquivUnits) > 0 ? n(completedEquivUnits) : effectiveUnits;
    effectiveInput.goodUnits = eq;
    effectiveUnits = eq;
    note = `Process Costing allocates total batch cost across ${eq} equivalent units.`;
  } else if (norm === "target") {
    const res = calculateCosting(effectiveInput);
    const allowable = money(n(input.sellingPrice) * (1 - n(targetMarginPct) / 100));
    targetAllowableUnitCost = allowable;
    targetCostGapPerUnit = money(res.unitCost - allowable);
    note = `Target Costing: Allowable Unit Cost = ${allowable} at ${targetMarginPct}% margin.`;
    return { method: norm, coreResult: res, effectiveUnitsUsed: effectiveUnits, targetAllowableUnitCost, targetCostGapPerUnit, methodNote: note };
  } else if (norm === "absorption") {
    note = "Absorption Costing includes Direct, Variable Overhead & Fixed Manufacturing Overhead.";
  } else if (norm === "standard") {
    note = "Standard Costing benchmarks budgeted unit recipes against actual factory spend.";
  } else if (norm === "abc") {
    note = "Activity-Based Costing assigns overhead pools via measured cost drivers.";
  } else if (norm === "job_order") {
    note = "Job Order Costing accumulates materials, labor, and overhead per custom batch.";
  } else {
    note = "Actual Costing records full historical incurred factory costs per good unit.";
  }

  const coreResult = calculateCosting(effectiveInput);
  return { method: norm, coreResult, effectiveUnitsUsed: effectiveUnits, targetAllowableUnitCost, targetCostGapPerUnit, methodNote: note };
}

export function evaluateFinancialRisk(profitMarginPct, inflationRatePct = null, totalDebt = 0, totalEquity = 0) {
  const margin = profitMarginPct == null ? null : n(profitMarginPct);
  const inf = inflationRatePct == null ? null : n(inflationRatePct);
  const deRatio = n(totalEquity) > 0 ? money(n(totalDebt) / n(totalEquity)) : null;

  if (margin != null && margin < 10) {
    return { level: "high", ruleName: "LOW_MARGIN", actualValue: `${margin}%`, thresholdValue: "10.00%", explanation: "Profit margin is below the 10% safety floor — high risk of cost-erosion loss." };
  }
  if (inf != null && inf > 20 && margin != null && margin < inf) {
    return { level: "high", ruleName: "INFLATION_SQUEEZE", actualValue: `Margin ${margin}% vs Infl ${inf}%`, thresholdValue: "Margin >= Inflation", explanation: "Monthly/period inflation exceeds unit margin; capital replacement erodes without repricing." };
  }
  if (deRatio != null && deRatio > 2) {
    return { level: "high", ruleName: "HIGH_LEVERAGE", actualValue: `${deRatio}x`, thresholdValue: "2.00x", explanation: "Debt-to-Equity exceeds 2.0x — elevated financial solvency stress." };
  }
  if (margin != null && margin < 18) {
    return { level: "medium", ruleName: "MODERATE_MARGIN", actualValue: `${margin}%`, thresholdValue: "18.00%", explanation: "Margin is positive but tight under inflationary supply volatility (10%–18%)." };
  }
  if (inf != null && inf > 10) {
    return { level: "medium", ruleName: "ELEVATED_INFLATION", actualValue: `${inf}%`, thresholdValue: "10.00%", explanation: "Inflation exceeds 10%; maintain dynamic replacement-cost monitoring." };
  }
  return {
    level: "low",
    ruleName: "HEALTHY_BUFFER",
    actualValue: margin != null ? `${margin}%` : "Pending Margin",
    thresholdValue: ">= 18%",
    explanation: "Unit profitability, leverage, and inflation buffers stay within healthy tolerance."
  };
}

export function computeDetailedVariance(params) {
  const stdMatAllowed = n(params.stdMaterialQtyPerUnit) * n(params.goodUnits) * n(params.stdMaterialPrice);
  const actMatCost = n(params.actualMaterialQty) * n(params.actualMaterialPrice);
  const matPriceVar = money((n(params.actualMaterialPrice) - n(params.stdMaterialPrice)) * n(params.actualMaterialQty));
  const matUsageVar = money((n(params.actualMaterialQty) - n(params.stdMaterialQtyPerUnit) * n(params.goodUnits)) * n(params.stdMaterialPrice));

  const stdLabHrsAllowed = n(params.stdLaborHoursPerUnit) * n(params.goodUnits);
  const stdLabAllowed = stdLabHrsAllowed * n(params.stdLaborRate);
  const actLabCost = n(params.actualLaborHours) * n(params.actualLaborRate);
  const labRateVar = money((n(params.actualLaborRate) - n(params.stdLaborRate)) * n(params.actualLaborHours));
  const labEffVar = money((n(params.actualLaborHours) - stdLabHrsAllowed) * n(params.stdLaborRate));

  const stdOvhAllowed = n(params.stdOverheadPerUnit) * n(params.goodUnits);
  const ovhVar = money(n(params.actualOverheadCost) - stdOvhAllowed);

  const stdTotal = money(stdMatAllowed + stdLabAllowed + stdOvhAllowed);
  const actTotal = money(actMatCost + actLabCost + n(params.actualOverheadCost));
  const totalVar = money(actTotal - stdTotal);

  return {
    materialPriceVariance: matPriceVar,
    materialUsageVariance: matUsageVar,
    laborRateVariance: labRateVar,
    laborEfficiencyVariance: labEffVar,
    overheadVariance: ovhVar,
    standardTotalAllowed: stdTotal,
    actualTotal: actTotal,
    totalVariance: totalVar
  };
}

export function runExtendedScenario({ baseMaterial, baseLabor, baseOverheadAndOther, baseRevenue, materialChangePct = 0, laborChangePct = 0, overheadChangePct = 0, priceChangePct = 0, fxChangePct = 0, volumeChangePct = 0 }) {
  const baseTotalCost = money(n(baseMaterial) + n(baseLabor) + n(baseOverheadAndOther));
  const baseProfit = money(n(baseRevenue) - baseTotalCost);
  const volFactor = 1 + n(volumeChangePct) / 100;
  const fxFactor = 1 + n(fxChangePct) / 100;

  const newMat = n(baseMaterial) * (1 + n(materialChangePct) / 100) * fxFactor * volFactor;
  const newLab = n(baseLabor) * (1 + n(laborChangePct) / 100) * volFactor;
  const newOvh = n(baseOverheadAndOther) * (1 + n(overheadChangePct) / 100);
  const newRev = n(baseRevenue) * (1 + n(priceChangePct) / 100) * volFactor;

  const scenarioTotalCost = money(newMat + newLab + newOvh);
  const scenarioProfit = money(newRev - scenarioTotalCost);
  return {
    baseTotalCost,
    scenarioTotalCost,
    baseProfit,
    scenarioProfit,
    profitDelta: money(scenarioProfit - baseProfit),
    disclaimer: "Scenario outcome — not a prediction"
  };
}

export function generateFinancialStatements(accounts, postedEntries) {
  const drMap = new Map();
  const crMap = new Map();
  for (const je of postedEntries) {
    if (je.status !== "posted" && je.status !== "reversed") continue;
    for (const l of je.lines) {
      drMap.set(l.accountCode, (drMap.get(l.accountCode) || 0) + n(l.debit));
      crMap.set(l.accountCode, (crMap.get(l.accountCode) || 0) + n(l.credit));
    }
  }

  let totalTrialDebits = 0;
  let totalTrialCredits = 0;
  let revenue = 0, cogs = 0, operatingExpenses = 0, interestAndOther = 0;
  let cashAndBank = 0, receivables = 0, inventoryBookValue = 0, nonCurrentAssets = 0;
  let currentLiabilities = 0, nonCurrentLiabilities = 0, equityBase = 0;

  const trialBalance = accounts.map((acc) => {
    const dr = drMap.get(acc.code) || 0;
    const cr = crMap.get(acc.code) || 0;
    const open = n(acc.openingBalance);
    const net = acc.normalBalance === "debit" ? open + dr - cr : open + cr - dr;
    const debitBal = acc.normalBalance === "debit" ? (net >= 0 ? money(net) : 0) : (net < 0 ? money(Math.abs(net)) : 0);
    const creditBal = acc.normalBalance === "credit" ? (net >= 0 ? money(net) : 0) : (net < 0 ? money(Math.abs(net)) : 0);
    totalTrialDebits += debitBal;
    totalTrialCredits += creditBal;

    if (acc.accountType === "revenue") revenue += net;
    else if (acc.accountType === "cogs") cogs += net;
    else if (acc.accountType === "expense") {
      if (acc.subType === "interest" || acc.subType === "tax") interestAndOther += net;
      else operatingExpenses += net;
    } else if (acc.accountType === "asset") {
      if (acc.subType === "cash") cashAndBank += net;
      else if (acc.subType === "receivable") receivables += net;
      else if (acc.subType === "inventory") inventoryBookValue += net;
      else if (acc.subType === "fixed_asset") nonCurrentAssets += net;
    } else if (acc.accountType === "liability") {
      if (acc.subType === "long_term_liability") nonCurrentLiabilities += net;
      else currentLiabilities += net;
    } else if (acc.accountType === "equity") {
      equityBase += net;
    }
    return { code: acc.code, name: acc.name, accountType: acc.accountType, debitBalance: debitBal, creditBalance: creditBal };
  });

  const grossProfit = money(revenue - cogs);
  const operatingProfit = money(grossProfit - operatingExpenses);
  const netProfit = money(operatingProfit - interestAndOther);
  const currentAssets = money(cashAndBank + receivables + inventoryBookValue);
  const totalAssets = money(currentAssets + nonCurrentAssets);
  const totalLiabilities = money(currentLiabilities + nonCurrentLiabilities);
  const totalEquityWithNetIncome = money(equityBase + netProfit);
  const totalLiabilitiesAndEquity = money(totalLiabilities + totalEquityWithNetIncome);
  const isBalanced = Math.abs(totalAssets - totalLiabilitiesAndEquity) <= 0.05;

  return {
    trialBalance,
    totalTrialDebits: money(totalTrialDebits),
    totalTrialCredits: money(totalTrialCredits),
    revenue: money(revenue),
    cogs: money(cogs),
    grossProfit,
    operatingExpenses: money(operatingExpenses),
    operatingProfit,
    interestAndOther: money(interestAndOther),
    netProfit,
    cashAndBank: money(cashAndBank),
    receivables: money(receivables),
    inventoryBookValue: money(inventoryBookValue),
    currentAssets,
    nonCurrentAssets: money(nonCurrentAssets),
    totalAssets,
    currentLiabilities: money(currentLiabilities),
    nonCurrentLiabilities: money(nonCurrentLiabilities),
    totalLiabilities,
    totalEquityWithNetIncome,
    totalLiabilitiesAndEquity,
    isAccountingEquationBalanced: isBalanced,
    operatingCashFlow: netProfit,
    investingCashFlow: money(-nonCurrentAssets),
    financingCashFlow: money(nonCurrentLiabilities + equityBase),
    closingCash: money(cashAndBank)
  };
}

export function computeFinancialRatios(stmt) {
  function ratio(key, category, num, den, mult = 1, unit = "x", formula = "", missingMsg = "Denominator is zero or missing") {
    const valid = den > 0;
    const val = valid ? pct4((num / den) * mult) : null;
    return {
      key,
      category,
      value: val,
      unitSuffix: unit,
      formula,
      missingDataWarning: valid ? null : missingMsg
    };
  }
  const quickAssets = stmt.cashAndBank + stmt.receivables;
  const ebit = stmt.operatingProfit;
  return [
    ratio("current_ratio", "Liquidity", stmt.currentAssets, stmt.currentLiabilities, 1, "x", "Current Assets / Current Liabilities", "Current Liabilities = 0"),
    ratio("quick_ratio", "Liquidity", quickAssets, stmt.currentLiabilities, 1, "x", "(Cash + AR) / Current Liabilities", "Current Liabilities = 0"),
    ratio("cash_ratio", "Liquidity", stmt.cashAndBank, stmt.currentLiabilities, 1, "x", "Cash / Current Liabilities", "Current Liabilities = 0"),
    ratio("working_capital_ratio", "Liquidity", stmt.currentAssets - stmt.currentLiabilities, stmt.totalAssets, 100, "%", "Working Capital / Total Assets", "Total Assets = 0"),
    ratio("gross_profit_margin", "Profitability", stmt.grossProfit, stmt.revenue, 100, "%", "Gross Profit / Revenue", "Revenue = 0"),
    ratio("operating_profit_margin", "Profitability", stmt.operatingProfit, stmt.revenue, 100, "%", "Operating Profit / Revenue", "Revenue = 0"),
    ratio("net_profit_margin", "Profitability", stmt.netProfit, stmt.revenue, 100, "%", "Net Profit / Revenue", "Revenue = 0"),
    ratio("return_on_assets_roa", "Profitability", stmt.netProfit, stmt.totalAssets, 100, "%", "Net Profit / Total Assets", "Total Assets = 0"),
    ratio("return_on_equity_roe", "Profitability", stmt.netProfit, stmt.totalEquityWithNetIncome, 100, "%", "Net Profit / Total Equity", "Equity <= 0"),
    ratio("return_on_capital_employed", "Profitability", ebit, stmt.totalAssets - stmt.currentLiabilities, 100, "%", "EBIT / Capital Employed", "Capital Employed <= 0"),
    ratio("inventory_turnover", "Efficiency", stmt.cogs, stmt.inventoryBookValue, 1, "x", "COGS / Inventory Book Value", "Inventory = 0"),
    ratio("dio_days_inventory", "Efficiency", stmt.inventoryBookValue * 365, stmt.cogs, 1, "days", "(Inventory / COGS) * 365", "COGS = 0"),
    ratio("receivables_turnover", "Efficiency", stmt.revenue, stmt.receivables, 1, "x", "Revenue / Receivables", "Receivables = 0"),
    ratio("dso_days_sales_outstanding", "Efficiency", stmt.receivables * 365, stmt.revenue, 1, "days", "(Receivables / Revenue) * 365", "Revenue = 0"),
    ratio("asset_turnover", "Efficiency", stmt.revenue, stmt.totalAssets, 1, "x", "Revenue / Total Assets", "Total Assets = 0"),
    ratio("debt_to_equity", "Leverage", stmt.totalLiabilities, stmt.totalEquityWithNetIncome, 1, "x", "Total Liabilities / Equity", "Equity <= 0"),
    ratio("debt_ratio", "Leverage", stmt.totalLiabilities, stmt.totalAssets, 100, "%", "Total Liabilities / Total Assets", "Total Assets = 0"),
    ratio("interest_coverage", "Leverage", ebit, stmt.interestAndOther, 1, "x", "EBIT / Interest & Finance Cost", "Interest Cost = 0")
  ];
}

export function computeTrialCountdown(trialEndsAtMillis, nowMillis) {
  const diff = Math.max(0, trialEndsAtMillis - nowMillis);
  const totalSecs = Math.floor(diff / 1000);
  const days = Math.floor(totalSecs / 86400);
  const hours = Math.floor((totalSecs % 86400) / 3600);
  const minutes = Math.floor((totalSecs % 3600) / 60);
  const seconds = totalSecs % 60;
  return { days, hours, minutes, seconds, isExpired: diff <= 0 };
}
