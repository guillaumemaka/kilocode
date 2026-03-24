import type { KiloClient, Session } from "@kilocode/sdk/v2/client"
import type { CreateWorktreeResult } from "./WorktreeManager"
import type { WorktreeStateManager } from "./WorktreeStateManager"
import { capture as captureGitState, apply as applyGitState } from "./git-transfer"
import { getErrorMessage } from "../kilo-provider-utils"
import { PLATFORM } from "./constants"

export interface ContinueContext {
  root: string
  getClient: () => KiloClient
  createWorktreeOnDisk: (opts: { baseBranch: string }) => Promise<{
    worktree: { id: string }
    result: CreateWorktreeResult
  } | null>
  runSetupScript: (path: string, branch: string, worktreeId: string) => Promise<void>
  getStateManager: () => WorktreeStateManager | undefined
  registerWorktreeSession: (sessionId: string, directory: string) => void
  registerSession: (session: Session) => void
  notifyReady: (sessionId: string, result: CreateWorktreeResult, worktreeId: string) => void
  capture: (event: string, props: Record<string, unknown>) => void
  log: (...args: unknown[]) => void
}

/**
 * Continue a sidebar session in a new worktree.
 * Captures git state, creates worktree, applies state, forks session.
 *
 * Pure orchestration — no vscode imports.
 */
export async function continueInWorktree(
  ctx: ContinueContext,
  sessionId: string,
  progress: (status: string, detail?: string, error?: string) => void,
): Promise<void> {
  // Abort the session if it's running
  try {
    const client = ctx.getClient()
    await client.session.abort({ sessionID: sessionId }).catch((err) => {
      ctx.log("Session abort failed (may already be idle):", getErrorMessage(err))
    })
  } catch (err) {
    ctx.log("Client not available for abort, continuing:", getErrorMessage(err))
  }

  // 1. Capture git state (read-only, non-destructive)
  progress("capturing", "Capturing git changes...")
  let snapshot
  try {
    snapshot = await captureGitState(ctx.root, (...args) => ctx.log(...args))
  } catch (err) {
    progress("error", undefined, `Failed to capture git state: ${getErrorMessage(err)}`)
    return
  }

  // 2. Create worktree from current branch
  progress("creating", "Creating worktree...")
  const created = await ctx.createWorktreeOnDisk({ baseBranch: snapshot.branch })
  if (!created) {
    progress("error", undefined, "Failed to create worktree")
    return
  }

  // 3. Run setup script
  progress("setup", "Running setup script...")
  await ctx.runSetupScript(created.result.path, created.result.branch, created.worktree.id)

  // 4. Apply git state to worktree
  progress("transferring", "Transferring changes...")
  const applied = await applyGitState(snapshot, created.result.path, (...args) => ctx.log(...args))
  if (!applied.ok) {
    ctx.log("Git state transfer failed:", applied.error)
    progress("error", undefined, applied.error ?? "Failed to apply changes to worktree")
    return
  }

  // 5. Fork session into worktree
  progress("forking", "Forking session...")
  let client: KiloClient
  try {
    client = ctx.getClient()
  } catch (err) {
    ctx.log("Client not available for session fork:", getErrorMessage(err))
    progress("error", undefined, "Not connected to CLI backend")
    return
  }

  let forked: Session
  try {
    const { data } = await client.session.fork(
      { sessionID: sessionId, directory: created.result.path },
      { throwOnError: true },
    )
    forked = data
  } catch (err) {
    progress("error", undefined, `Failed to fork session: ${getErrorMessage(err)}`)
    return
  }

  // 6. Register session in state and notify
  const state = ctx.getStateManager()
  if (state) {
    state.addSession(forked.id, created.worktree.id)
  }
  ctx.registerWorktreeSession(forked.id, created.result.path)
  ctx.registerSession(forked)
  ctx.notifyReady(forked.id, created.result, created.worktree.id)

  ctx.capture("Continue in Worktree", {
    source: PLATFORM,
    sessionId: forked.id,
    worktreeId: created.worktree.id,
  })
  ctx.log(`Continued sidebar session ${sessionId} → worktree ${created.worktree.id} (session ${forked.id})`)

  progress("done")
}
