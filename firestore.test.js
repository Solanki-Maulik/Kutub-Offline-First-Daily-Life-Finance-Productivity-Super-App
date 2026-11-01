const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validPayload(uid = ALICE_UID, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    deviceName: "Pixel 8 Pro",
    schemaVersion: 2,
    summaryText: "12 Transactions, 3 Accounts, 4 Notes",
    transactionCount: 12,
    totalItemsCount: 25,
    backupPayload: "{\"schemaVersion\":2,\"transactions\":[]}",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
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

test("1. Unauthenticated user cannot read or write cloud backups", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("cloud_backups").doc("latest").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("cloud_backups").doc("latest").set(validPayload(ALICE_UID))
  );
});

test("2. Authenticated owner can create and read their own cloud backup", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("cloud_backups").doc("latest");
  await assertSucceeds(docRef.set(validPayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
});

test("3. Authenticated user cannot read or overwrite another user's cloud backup", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cloud_backups").doc("latest").set(validPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("cloud_backups").doc("latest").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("cloud_backups").doc("latest").set(validPayload(BOB_UID))
  );
});

test("4. Shadow update with ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("cloud_backups").doc("latest");
  await assertFails(
    docRef.set(validPayload(ALICE_UID, { isVerified: true }))
  );
});

test("5. Value poisoning (negative counts or empty deviceName) is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("cloud_backups").doc("latest");
  await assertFails(
    docRef.set(validPayload(ALICE_UID, { transactionCount: -5 }))
  );
  await assertFails(
    docRef.set(validPayload(ALICE_UID, { deviceName: "" }))
  );
});

test("6. Authenticated owner can list their own backups with matching userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cloud_backups").doc("latest").set(validPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("cloud_backups")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("7. Update preserves immutable createdAt and userId, rejects mutating userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("cloud_backups").doc("latest");
  await assertSucceeds(docRef.set(validPayload(ALICE_UID)));

  await assertSucceeds(
    docRef.update({
      summaryText: "15 Transactions, 3 Accounts",
      transactionCount: 15,
      updatedAt: new Date(),
    })
  );

  await assertFails(
    docRef.update({
      userId: BOB_UID,
      updatedAt: new Date(),
    })
  );
});
