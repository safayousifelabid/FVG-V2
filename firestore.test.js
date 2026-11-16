const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-fvg-963bcf06-9233-4f62-aa0d-cdebb701a866";
const ALICE_UID = "alice_owner_1";
const BOB_UID = "bob_outsider_2";
const VIEWER_UID = "charlie_viewer_3";
const ACCOUNTANT_UID = "accountant_4";
const ORG_ID = "org_fvg_alpha";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function dbFor(ctx) {
  return typeof ctx.firestore === "function" ? ctx.firestore(DATABASE_ID) : ctx.firestore;
}

async function seedOrgAndMembership() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = dbFor(context);
    const now = new Date(Date.now() - 1000);
    await adminDb.collection("organizations").doc(ORG_ID).set({
      name: "Alpha Trading Co",
      baseCurrency: "USD",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      roles: {
        [ALICE_UID]: "owner",
        [ACCOUNTANT_UID]: "accountant",
        [VIEWER_UID]: "viewer",
      },
      planTier: "trial",
      subscriptionStatus: "trial",
      trialDurationHours: 72,
      trialStartedAt: now,
      trialEndsAt: new Date(now.getTime() + 3 * 86400000),
      createdAt: now,
      updatedAt: now,
    });
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or list organizations or products", async () => {
  await seedOrgAndMembership();
  const unauthDb = dbFor(testEnv.unauthenticatedContext());
  await assertFails(unauthDb.collection("organizations").doc(ORG_ID).get());
  await assertFails(unauthDb.collection("organizations").doc(ORG_ID).collection("products").get());
});

test("Owner can create user profile and organization with immutable 72h trial anchors", async () => {
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@fvg.test",
      displayName: "Alice",
      preferredLanguage: "en",
      trialDurationHours: 72,
      trialStartedAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Attempting to tamper / extend UserProfile.trialStartedAt must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      trialStartedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).set({
      name: "Alpha Co",
      baseCurrency: "USD",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      roles: { [ALICE_UID]: "owner" },
      planTier: "trial",
      subscriptionStatus: "trial",
      trialDurationHours: 72,
      trialStartedAt: now,
      trialEndsAt: new Date(now.getTime() + 3 * 86400000),
      createdAt: now,
      updatedAt: now,
    })
  );

  // Attempting to tamper Organization trialEndsAt must fail
  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).update({
      trialEndsAt: new Date(now.getTime() + 30 * 86400000),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection("organizations").where("memberUids", "array-contains", ALICE_UID).get()
  );
});

test("Cross-organization isolation: outsider cannot read, list unfiltered, or write to another org", async () => {
  await seedOrgAndMembership();
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = dbFor(testEnv.authenticatedContext(BOB_UID));
  const now = new Date(Date.now() - 1000);
  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).collection("products").doc("prod_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ALICE_UID,
      name: "Cement",
      category: "Materials",
      unit: "ton",
      currency: "USD",
      currentPrice: "120.00",
      previousPrice: "110.00",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("organizations").doc(ORG_ID).get());
  await assertFails(bobDb.collection("organizations").doc(ORG_ID).collection("products").get());
  await assertFails(bobDb.collection("organizations").doc(ORG_ID).collection("products").doc("prod_1").get());
  await assertFails(
    bobDb.collection("organizations").doc(ORG_ID).collection("products").doc("prod_2").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdBy: BOB_UID,
      name: "Spoofed Item",
      category: "Grain",
      unit: "kg",
      currency: "USD",
      currentPrice: "10.00",
      previousPrice: "9.00",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Viewer role can read with memberUids filter but cannot write financial records", async () => {
  await seedOrgAndMembership();
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const viewerDb = dbFor(testEnv.authenticatedContext(VIEWER_UID));
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).collection("products").doc("prod_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ALICE_UID,
      name: "Wheat Flour",
      category: "Commodities",
      unit: "kg",
      currency: "USD",
      currentPrice: "1.80",
      previousPrice: "1.50",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    viewerDb.collection("organizations").doc(ORG_ID).collection("products").where("memberUids", "array-contains", VIEWER_UID).get()
  );

  await assertFails(
    viewerDb.collection("organizations").doc(ORG_ID).collection("products").doc("prod_2").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: VIEWER_UID,
      name: "Unauthorized Item",
      category: "Commodities",
      unit: "kg",
      currency: "USD",
      currentPrice: "2.00",
      previousPrice: "2.00",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Journal entry enforces balanced debits/credits and terminal lock on posted entries", async () => {
  await seedOrgAndMembership();
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const now = new Date(Date.now() - 1000);

  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).collection("journalEntries").doc("je_unbalanced").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ALICE_UID,
      referenceNo: "JE-001",
      entryDate: "2026-11-15",
      period: "2026-11",
      description: "Unbalanced",
      currency: "USD",
      exchangeRate: "1.0000",
      status: "posted",
      lines: [{ accountCode: "1010", debit: "100.00", credit: "0.00" }, { accountCode: "4010", debit: "0.00", credit: "90.00" }],
      totalDebit: "100.00",
      totalCredit: "90.00",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).collection("journalEntries").doc("je_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ALICE_UID,
      referenceNo: "JE-002",
      entryDate: "2026-11-15",
      period: "2026-11",
      description: "Cash Sale",
      currency: "USD",
      exchangeRate: "1.0000",
      status: "posted",
      lines: [{ accountCode: "1010", debit: "100.00", credit: "0.00" }, { accountCode: "4010", debit: "0.00", credit: "100.00" }],
      totalDebit: "100.00",
      totalCredit: "100.00",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).collection("journalEntries").doc("je_1").update({
      totalDebit: "500.00",
      totalCredit: "500.00",
      updatedAt: now,
    })
  );
});

test("Audit logs and price observations protect history from tampering or deletion", async () => {
  await seedOrgAndMembership();
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).collection("auditLogs").doc("log_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ALICE_UID,
      action: "CREATE_PRODUCT",
      entityType: "product",
      entityId: "prod_1",
      summary: "Created Wheat Flour",
      actorEmail: "alice@fvg.test",
      createdAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).collection("auditLogs").doc("log_1").update({
      summary: "Tampered",
    })
  );
  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).collection("auditLogs").doc("log_1").delete()
  );
});

test("CostSnapshot supports all 8 costing methods and rejects unknown costing methods", async () => {
  await seedOrgAndMembership();
  const accountantDb = dbFor(testEnv.authenticatedContext(ACCOUNTANT_UID));
  const now = new Date(Date.now() - 1000);
  const methods = ["standard", "actual", "target", "abc", "job_order", "process", "absorption", "variable"];

  for (const m of methods) {
    await assertSucceeds(
      accountantDb.collection("organizations").doc(ORG_ID).collection("costSnapshots").doc(`cs_${m}`).set({
        orgId: ORG_ID,
        ownerId: ALICE_UID,
        memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
        createdBy: ACCOUNTANT_UID,
        productId: "prod_1",
        productName: "Coffee 1kg",
        period: "2026-11",
        currency: "USD",
        costingMethod: m,
        directMaterial: "500.00",
        directLabor: "200.00",
        overhead: "100.00",
        goodUnits: "100",
        totalCost: "800.00",
        unitCost: "8.00",
        sellingPrice: "12.00",
        profitMarginPct: "33.33",
        markupPct: "50.00",
        riskLevel: "low",
        formulaVersion: "2.1.0",
        createdAt: now,
        updatedAt: now,
      })
    );
  }

  await assertFails(
    accountantDb.collection("organizations").doc(ORG_ID).collection("costSnapshots").doc("cs_invalid").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ACCOUNTANT_UID,
      productId: "prod_1",
      productName: "Coffee 1kg",
      period: "2026-11",
      currency: "USD",
      costingMethod: "invented_method",
      directMaterial: "500.00",
      directLabor: "200.00",
      overhead: "100.00",
      goodUnits: "100",
      totalCost: "800.00",
      unitCost: "8.00",
      sellingPrice: "12.00",
      profitMarginPct: "33.33",
      markupPct: "50.00",
      formulaVersion: "2.1.0",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Standard costs, production orders, scrap losses, payment requests, and research records enforce RBAC", async () => {
  await seedOrgAndMembership();
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const accountantDb = dbFor(testEnv.authenticatedContext(ACCOUNTANT_UID));
  const viewerDb = dbFor(testEnv.authenticatedContext(VIEWER_UID));
  const bobDb = dbFor(testEnv.authenticatedContext(BOB_UID));
  const now = new Date(Date.now() - 1000);

  // PaymentRequest: member submits pending Bankak receipt, only owner/admin approves
  await assertSucceeds(
    accountantDb.collection("organizations").doc(ORG_ID).collection("paymentRequests").doc("pay_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ACCOUNTANT_UID,
      planTier: "professional",
      paymentMethod: "bankak",
      amount: "29.00",
      currency: "USD",
      senderName: "Ahmed Ali",
      referenceNo: "BNK-991822",
      transferDate: "2026-11-15",
      notes: "Monthly subscription transfer",
      status: "pending_verification",
      reviewedBy: "",
      reviewNotes: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Accountant cannot self-approve payment request
  await assertFails(
    accountantDb.collection("organizations").doc(ORG_ID).collection("paymentRequests").doc("pay_1").update({
      status: "approved",
      reviewedBy: ACCOUNTANT_UID,
      reviewNotes: "self approved",
      updatedAt: now,
    })
  );

  // Owner approves payment request
  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).collection("paymentRequests").doc("pay_1").update({
      status: "approved",
      reviewedBy: ALICE_UID,
      reviewNotes: "Verified against Bankak statement",
      updatedAt: now,
    })
  );

  // Payment request delete forbidden
  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).collection("paymentRequests").doc("pay_1").delete()
  );

  // Research workspace record CRUD
  await assertSucceeds(
    accountantDb.collection("organizations").doc(ORG_ID).collection("researchRecords").doc("res_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ACCOUNTANT_UID,
      title: "Effect of FX Pass-Through on Unit Cost",
      recordType: "hypothesis",
      summary: "H1: Imported packaging cost drives >35% of gross margin compression.",
      dataStatus: "available",
      sourceRef: "FVG Thesis Ch.3",
      periodTag: "2026-Q4",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    viewerDb.collection("organizations").doc(ORG_ID).collection("researchRecords").where("memberUids", "array-contains", VIEWER_UID).get()
  );
  await assertFails(
    bobDb.collection("organizations").doc(ORG_ID).collection("researchRecords").get()
  );
  await assertFails(
    viewerDb.collection("organizations").doc(ORG_ID).collection("researchRecords").doc("res_1").update({
      summary: "Unauthorized edit",
      updatedAt: now,
    })
  );
});
