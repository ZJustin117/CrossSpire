import { readFileSync, existsSync } from "node:fs"
import { join } from "node:path"

/** Whitelist only CrossSpire / Amethyst harness keys — never secrets/API keys. */
const ALLOWED_KEYS = new Set([
  "SLAY_THE_AMETHYST_ROOT",
  "CROSSSPIRE_STS_JAR",
  "CROSSSPIRE_BASEMOD_JAR",
  "CROSSSPIRE_MODTHESPIRE_JAR",
  "STS_CONNECTOR_PORT",
  "CROSSSPIRE_HARNESS_OUT_DIR",
  "CROSSSPIRE_AMETHYST_TOOLS_DIR",
  "CROSSSPIRE_D1_SERIAL",
  "CROSSSPIRE_D2_SERIAL",
  "CROSSSPIRE_GAME_PORT",
  "STS_TEST_DEVICE",
  "CROSSSPIRE_GAME_PROBE_PORT",
  "CROSSSPIRE_ARTHAS_PORT",
])

const TEST_AGENTS = new Set([
  "junit-test",
  "android-deploy-jar",
  "android-harness",
  "android-arthas",
])

function parseDotEnv(content: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const raw of content.split(/\r?\n/)) {
    const line = raw.trim()
    if (!line || line.startsWith("#")) continue
    const eq = line.indexOf("=")
    if (eq <= 0) continue
    const key = line.slice(0, eq).trim()
    if (!ALLOWED_KEYS.has(key)) continue
    let val = line.slice(eq + 1).trim()
    if (
      (val.startsWith('"') && val.endsWith('"')) ||
      (val.startsWith("'") && val.endsWith("'"))
    ) {
      val = val.slice(1, -1)
    }
    if (val) out[key] = val
  }
  return out
}

function loadLocalEnv(directory: string): {
  filePath: string
  fromFile: Record<string, string>
  merged: Record<string, string>
  missingFile: boolean
} {
  const filePath = join(directory, ".env.local")
  const missingFile = !existsSync(filePath)
  const fromFile = missingFile ? {} : parseDotEnv(readFileSync(filePath, "utf8"))
  const merged: Record<string, string> = {}
  for (const key of ALLOWED_KEYS) {
    const fromProcess = process.env[key]
    if (fromProcess && fromProcess.length > 0) {
      merged[key] = fromProcess
    } else if (fromFile[key]) {
      merged[key] = fromFile[key]
    }
  }
  return { filePath, fromFile, merged, missingFile }
}

function applyDerivedJars(merged: Record<string, string>): void {
  const root = merged.SLAY_THE_AMETHYST_ROOT
  if (!root) return
  if (!merged.CROSSSPIRE_BASEMOD_JAR) {
    merged.CROSSSPIRE_BASEMOD_JAR = join(
      root,
      "app/src/main/assets/components/mods/BaseMod.jar",
    )
  }
  if (!merged.CROSSSPIRE_MODTHESPIRE_JAR) {
    merged.CROSSSPIRE_MODTHESPIRE_JAR = join(
      root,
      "app/src/main/assets/components/mods/ModTheSpire.jar",
    )
  }
  if (!merged.CROSSSPIRE_AMETHYST_TOOLS_DIR) {
    merged.CROSSSPIRE_AMETHYST_TOOLS_DIR = join(root, "scripts/tools")
  }
}

function formatSystemBlock(
  merged: Record<string, string>,
  missingFile: boolean,
  filePath: string,
): string {
  const lines = [
    "## Local machine config (CrossSpire)",
    "Source: process env overrides, then repo `.env.local` (gitignored).",
    "Only use these names in harness/gradle commands. Do not write absolute paths into shared docs or production code.",
  ]
  if (missingFile) {
    lines.push(
      `Note: \`${filePath}\` is missing. Copy \`.env.example\` → \`.env.local\` before device E2E.`,
    )
  }
  const keys = [...ALLOWED_KEYS]
  for (const key of keys) {
    const v = merged[key]
    if (v) lines.push(`${key}=${v}`)
    else lines.push(`${key}=(unset)`)
  }
  lines.push(
    "If a required key is unset for the task, stop and report which keys are missing.",
  )
  return lines.join("\n")
}

/** OpenCode child sessions are ses… ; models often invent UUIDs for task_id. */
function isTaskTool(name: string): boolean {
  return name.toLowerCase() === "task"
}

function isValidSessionTaskId(id: unknown): boolean {
  return typeof id === "string" && id.startsWith("ses")
}

/**
 * Drop invented task_id so Task creates a new session instead of failing
 * schema validation (Expected a string starting with "ses").
 * Never rewrite UUID → ses_+UUID (that still does not resume a real session).
 */
function stripInvalidTaskId(args: Record<string, unknown>): void {
  for (const key of ["task_id", "taskId"] as const) {
    if (!(key in args)) continue
    const id = args[key]
    if (id == null || id === "") {
      delete args[key]
      continue
    }
    if (isValidSessionTaskId(id)) continue
    delete args[key]
  }
}

export const LocalEnvPlugin = async ({ directory }: { directory: string }) => {
  const root = directory
  const { filePath, merged, missingFile } = loadLocalEnv(root)
  applyDerivedJars(merged)

  // Seed process.env so subsequent tools see values without re-reading the file.
  for (const [k, v] of Object.entries(merged)) {
    if (!process.env[k]) process.env[k] = v
  }

  const systemBlock = formatSystemBlock(merged, missingFile, filePath)

  return {
    "shell.env": async (
      _input: unknown,
      output: { env?: Record<string, string> },
    ) => {
      if (!output.env) output.env = {}
      for (const [k, v] of Object.entries(merged)) {
        if (!output.env[k]) output.env[k] = v
      }
    },

    // Models often pass invented UUIDs as task_id after reading AGENTS.md.
    // Strip non-ses ids so new Task runs succeed; keep real ses… for resume.
    "tool.execute.before": async (
      input: { tool: string },
      output: { args?: Record<string, unknown> },
    ) => {
      if (!output.args || typeof output.args !== "object") return
      if (!isTaskTool(input.tool)) return
      stripInvalidTaskId(output.args)
    },

    // Inject into system prompts for test subagents when the hook is available.
    "experimental.chat.system.transform": async (
      input: { agent?: string },
      output: { system?: string | string[] },
    ) => {
      const agent = input?.agent
      if (agent && !TEST_AGENTS.has(agent)) return
      if (typeof output.system === "string") {
        output.system = `${output.system}\n\n${systemBlock}`
      } else if (Array.isArray(output.system)) {
        output.system.push(systemBlock)
      }
    },

    // Fallback: prepend a short reminder on first user message for test agents.
    "chat.message": async (
      input: { agent?: string },
      output: { message?: { parts?: Array<{ type?: string; text?: string }> } },
    ) => {
      const agent = input?.agent
      if (!agent || !TEST_AGENTS.has(agent)) return
      // no-op body: system transform is preferred; agents also Read .env.local
      void output
    },
  }
}

export default LocalEnvPlugin
