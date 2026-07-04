#!/usr/bin/env node
/**
 * Sube contenido de la ruleta (preguntas, retos, ronda relampago, castigos)
 * directamente a Firestore, sin pasar por la app.
 *
 * Replica exactamente lo que hace el importador de la app: escribe cada
 * pregunta en sessions/{sessionId}/content/{auto-id} Y actualiza
 * sessions/{sessionId}/categoryStats/{category} en el mismo commit, para que
 * la ruleta y la ronda relampago vean el contenido disponible de inmediato.
 * Si solo escribieras "content" sin tocar "categoryStats", la app seguiria
 * pensando que no hay nada que jugar.
 *
 * Uso:
 *   node import-content.js --service-account ./service-account.json \
 *     --session <sessionId> --category pregunta --file ./preguntas.txt
 *
 * Formato del archivo (una linea por pregunta/reto/castigo):
 *   Texto de la pregunta
 *   3;Texto de la pregunta con numero explicito
 *
 * Categorias aceptadas (--category): pregunta/question, reto/challenge,
 * relampago/lightning/rr, castigo/punishment.
 *
 * Volver a ejecutar el mismo archivo es seguro: las filas ya importadas
 * (mismo texto normalizado + numero + categoria) se saltan, igual que en la app.
 */

const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");

const CATEGORY_ALIASES = {
  question: "QUESTION",
  questions: "QUESTION",
  pregunta: "QUESTION",
  preguntas: "QUESTION",
  q: "QUESTION",
  challenge: "CHALLENGE",
  challenges: "CHALLENGE",
  reto: "CHALLENGE",
  retos: "CHALLENGE",
  r: "CHALLENGE",
  lightning: "LIGHTNING",
  relampago: "LIGHTNING",
  "ronda relampago": "LIGHTNING",
  rr: "LIGHTNING",
  punishment: "PUNISHMENT",
  punishments: "PUNISHMENT",
  castigo: "PUNISHMENT",
  castigos: "PUNISHMENT",
  c: "PUNISHMENT",
};

function parseArgs(argv) {
  const result = { dryRun: false };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case "--service-account":
        result.serviceAccount = argv[++i];
        break;
      case "--session":
        result.sessionId = argv[++i];
        break;
      case "--category":
        result.category = argv[++i];
        break;
      case "--file":
        result.file = argv[++i];
        break;
      case "--created-by":
        result.createdBy = argv[++i];
        break;
      case "--dry-run":
        result.dryRun = true;
        break;
      default:
        throw new Error(`Argumento desconocido: ${arg}`);
    }
  }
  const missing = ["serviceAccount", "sessionId", "category", "file"].filter((key) => !result[key]);
  if (missing.length > 0) {
    throw new Error(
      "Faltan argumentos obligatorios: " +
        missing.join(", ") +
        "\nUso: node import-content.js --service-account <json> --session <id> --category <cat> --file <txt> [--created-by <uid>] [--dry-run]"
    );
  }
  return result;
}

function resolveCategory(raw) {
  const normalized = raw.trim().toLowerCase();
  const direct = raw.trim().toUpperCase();
  if (["QUESTION", "CHALLENGE", "LIGHTNING", "PUNISHMENT"].includes(direct)) return direct;
  const mapped = CATEGORY_ALIASES[normalized];
  if (!mapped) {
    throw new Error(
      `Categoria "${raw}" no reconocida. Usa: pregunta, reto, relampago, castigo (o question/challenge/lightning/punishment).`
    );
  }
  return mapped;
}

// Replica java.lang.String.hashCode() / Kotlin String.hashCode(), que es lo que
// usa la app para el hash de deduplicacion.
function javaStringHashCode(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (Math.imul(31, hash) + str.charCodeAt(i)) | 0;
  }
  return hash;
}

// Replica normalizedContentHash() de RouletteModels.kt
function normalizedContentHash(text) {
  const normalized = text.trim().toLowerCase().replace(/\s+/g, " ");
  const hash = javaStringHashCode(normalized);
  const abs = hash < 0 ? -hash : hash;
  return abs.toString(36);
}

function stableHash(categoryFirestoreValue, number, text) {
  return `${categoryFirestoreValue}|${number}|${normalizedContentHash(text)}`;
}

function parseLines(rawText, startingNumber) {
  const lines = rawText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith("#"));

  let autoNumber = startingNumber;
  return lines.map((line, index) => {
    const separatorMatch = line.match(/^(\d+)\s*[;\t.]\s*(.*)$/);
    let number;
    let text;
    if (separatorMatch) {
      number = parseInt(separatorMatch[1], 10);
      text = separatorMatch[2].trim();
    } else {
      number = autoNumber++;
      text = line;
    }
    return { sourceLine: index + 1, number, text };
  });
}

function validate(row) {
  if (!row.text || row.text.trim().length === 0) return "Texto obligatorio";
  if (row.text.length > 400) return "Texto demasiado largo: maximo 400 caracteres";
  if (!Number.isInteger(row.number) || row.number <= 0) return "Numero obligatorio y mayor que cero";
  return null;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const categoryValue = resolveCategory(args.category);
  const serviceAccountPath = path.resolve(args.serviceAccount);
  const filePath = path.resolve(args.file);

  const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, "utf8"));
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
  const db = admin.firestore();

  const sessionRef = db.collection("sessions").doc(args.sessionId);
  const sessionSnapshot = await sessionRef.get();
  if (!sessionSnapshot.exists) {
    throw new Error(`No existe ninguna sesion con id "${args.sessionId}". Revisa el sessionId en Firestore.`);
  }

  const statsRef = sessionRef.collection("categoryStats").doc(categoryValue);
  const statsSnapshot = await statsRef.get();
  const existingStats = statsSnapshot.exists
    ? statsSnapshot.data()
    : { totalCount: 0, availableCount: 0, usedCount: 0, availableContentIds: [], contentHashes: [] };

  const rawText = fs.readFileSync(filePath, "utf8");
  const rows = parseLines(rawText, (existingStats.totalCount || 0) + 1);
  if (rows.length > 490) {
    throw new Error(
      `El archivo tiene ${rows.length} lineas; Firestore no permite mas de 500 escrituras por lote. Divide el archivo en partes de 490 o menos.`
    );
  }

  const seenHashes = new Set(existingStats.contentHashes || []);
  const availableIds = [...(existingStats.availableContentIds || [])];
  const contentHashes = [...(existingStats.contentHashes || [])];

  let inserted = 0;
  let skippedDuplicate = 0;
  let skippedInvalid = 0;
  const errors = [];

  const batch = db.batch();
  const now = admin.firestore.FieldValue.serverTimestamp();

  for (const row of rows) {
    const error = validate(row);
    if (error) {
      skippedInvalid++;
      errors.push(`Linea ${row.sourceLine}: ${error} ("${row.text}")`);
      continue;
    }
    const hash = stableHash(categoryValue, row.number, row.text);
    if (seenHashes.has(hash)) {
      skippedDuplicate++;
      continue;
    }
    seenHashes.add(hash);

    const contentRef = sessionRef.collection("content").doc();
    const contentData = {
      category: categoryValue,
      number: row.number,
      text: row.text.trim(),
      searchHash: normalizedContentHash(row.text),
      importHash: hash,
      active: true,
      used: false,
      importId: "manual-script",
      createdBy: args.createdBy || "manual-script",
      createdAt: now,
      updatedAt: now,
    };
    if (!args.dryRun) {
      batch.set(contentRef, contentData);
    }
    availableIds.push(contentRef.id);
    contentHashes.push(hash);
    inserted++;
  }

  if (inserted > 0 && !args.dryRun) {
    batch.set(
      statsRef,
      {
        category: categoryValue,
        totalCount: (existingStats.totalCount || 0) + inserted,
        availableCount: availableIds.length,
        usedCount: existingStats.usedCount || 0,
        availableContentIds: availableIds,
        contentHashes: contentHashes,
        exhausted: false,
        updatedAt: now,
      },
      { merge: true }
    );
    await batch.commit();
  }

  console.log(`Categoria: ${categoryValue}`);
  console.log(`Lineas leidas: ${rows.length}`);
  console.log(`Insertadas: ${inserted}${args.dryRun ? " (simulado, no se escribio nada)" : ""}`);
  console.log(`Duplicadas (ya existian): ${skippedDuplicate}`);
  console.log(`Invalidas: ${skippedInvalid}`);
  if (errors.length > 0) {
    console.log("Detalle de invalidas:");
    errors.forEach((line) => console.log(`  - ${line}`));
  }
}

main().catch((error) => {
  console.error("Error:", error.message);
  process.exit(1);
});
