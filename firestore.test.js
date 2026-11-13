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

function validNotePayload(userId, now = new Date()) {
  return {
    userId,
    title: "Quantum Mechanics Lecture 1",
    content: "Wave-particle duality and Schrodinger equation fundamentals.",
    subject: "Lecture Notes",
    courseCode: "PHY-301",
    instituteName: "Oxford Institute of Technology",
    colorIndex: 1,
    isPinned: false,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or list notes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").get());
});

test("Authenticated user can create, read, list, update, and delete their own note", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1");
  const initialTime = new Date(Date.now() - 5000);

  await assertSucceeds(noteRef.set(validNotePayload(ALICE_UID, initialTime)));
  await assertSucceeds(noteRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notes").get());

  // Update note title and content while preserving createdAt and userId
  await assertSucceeds(
    noteRef.update({
      title: "Updated Quantum Mechanics Lecture 1",
      content: "Updated notes with operators and eigenvalues.",
      isPinned: true,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(noteRef.delete());
});

test("Authenticated user cannot read, update, or delete another user's note", async () => {
  const initialTime = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("notes")
      .doc("bob_note_1")
      .set(validNotePayload(BOB_UID, initialTime));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobNoteRef = aliceDb.collection("users").doc(BOB_UID).collection("notes").doc("bob_note_1");

  await assertFails(bobNoteRef.get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("notes").get());
  await assertFails(bobNoteRef.update({ title: "Hacked", updatedAt: new Date() }));
  await assertFails(bobNoteRef.delete());
});

test("Shadow update with ghost field or invalid schema is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_shadow");
  const initialTime = new Date(Date.now() - 5000);

  // Rejects ghost field on create
  await assertFails(
    noteRef.set({
      ...validNotePayload(ALICE_UID, initialTime),
      isAdminVerified: true,
    })
  );

  await assertSucceeds(noteRef.set(validNotePayload(ALICE_UID, initialTime)));

  // Rejects changing immutable createdAt on update
  await assertFails(
    noteRef.update({
      title: "Valid Title",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Rejects empty title
  await assertFails(
    noteRef.update({
      title: "",
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user can create and update their own institute profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID).collection("profile").doc("institute");

  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      instituteName: "Global Institute of Science & Engineering",
      department: "Department of Computer Science",
      academicSession: "2026 - 2027",
      studentOrFacultyName: "Alex Rivera",
      rollOrIdNumber: "CS-2026-042",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    profileRef.update({
      department: "Department of Artificial Intelligence",
      updatedAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("profile").doc("institute").get()
  );
});
