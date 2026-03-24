/**
 * ChatView component
 * Main chat container that combines all chat components
 */

import { Component, Show, createEffect, createMemo, createSignal, on, onCleanup, onMount } from "solid-js"
import { Button } from "@kilocode/kilo-ui/button"
import { Icon } from "@kilocode/kilo-ui/icon"
import { Spinner } from "@kilocode/kilo-ui/spinner"
import { TaskHeader } from "./TaskHeader"
import { MessageList } from "./MessageList"
import { PromptInput } from "./PromptInput"
import { QuestionDock } from "./QuestionDock"
import { PermissionDock } from "./PermissionDock"
import { StartupErrorBanner } from "./StartupErrorBanner"
import { useSession } from "../../context/session"
import { useVSCode } from "../../context/vscode"
import { useLanguage } from "../../context/language"
import { useWorktreeMode } from "../../context/worktree-mode"
import { useServer } from "../../context/server"

interface ChatViewProps {
  onSelectSession?: (id: string) => void
  readonly?: boolean
  /** When true, show the "Continue in Worktree" button. Defaults to true in the sidebar. */
  continueInWorktree?: boolean
}

export const ChatView: Component<ChatViewProps> = (props) => {
  const session = useSession()
  const vscode = useVSCode()
  const language = useLanguage()
  const worktreeMode = useWorktreeMode()
  const server = useServer()
  // Show "Show Changes" only in the standalone sidebar, not inside Agent Manager
  const isSidebar = () => worktreeMode === undefined
  // Show "Continue in Worktree": explicit prop, or default to true in sidebar
  const canContinueInWorktree = () => props.continueInWorktree ?? isSidebar()

  const id = () => session.currentSessionID()
  const hasMessages = () => session.messages().length > 0
  const idle = () => session.status() !== "busy"

  // "Continue in Worktree" state
  const [transferring, setTransferring] = createSignal(false)
  const [transferDetail, setTransferDetail] = createSignal("")

  // Permissions and questions scoped to this session's family (self + subagents).
  // Each ChatView only sees its own session tree — no cross-session leakage.
  // Memoized so the BFS walk in sessionFamily() runs once per reactive update,
  // not once per accessor call (questionRequest, permissionRequest, blocked all read these).
  const familyPermissions = createMemo(() => session.scopedPermissions(id()))
  const familyQuestions = createMemo(() => session.scopedQuestions(id()))

  // Prefer non-tool questions in the dock: current-session non-tool first,
  // then any non-tool, then fall back to any remaining scoped question.
  const questionRequest = () =>
    familyQuestions().find((q) => q.sessionID === id() && !q.tool) ??
    familyQuestions().find((q) => !q.tool) ??
    familyQuestions()[0]
  const permissionRequest = () => familyPermissions().find((p) => p.sessionID === id()) ?? familyPermissions()[0]
  const blocked = () => familyPermissions().length > 0 || familyQuestions().length > 0
  const dock = () => !props.readonly || !!questionRequest() || !!permissionRequest()

  // When a bottom-dock permission/question disappears while the session is busy,
  // the scroll container grows taller. Dispatch a custom event so MessageList can
  // resume auto-scroll.
  createEffect(
    on(blocked, (isBlocked, wasBlocked) => {
      if (wasBlocked && !isBlocked && !idle()) {
        window.dispatchEvent(new CustomEvent("resumeAutoScroll"))
      }
    }),
  )

  onMount(() => {
    if (props.readonly) return
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape" && session.status() === "busy") {
        e.preventDefault()
        session.abort()
      }
    }
    document.addEventListener("keydown", handler)
    onCleanup(() => document.removeEventListener("keydown", handler))
  })

  // Listen for "Continue in Worktree" progress messages
  {
    const labels: Record<string, string> = {
      capturing: "Capturing changes...",
      creating: "Creating worktree...",
      setup: "Running setup...",
      transferring: "Transferring changes...",
      forking: "Starting session...",
    }
    const cleanup = vscode.onMessage((msg) => {
      if (msg.type !== "continueInWorktreeProgress") return
      const status = (msg as { status: string }).status
      if (status === "done" || status === "error") {
        setTransferring(false)
        setTransferDetail("")
        return
      }
      setTransferDetail(labels[status] ?? "Working...")
    })
    onCleanup(cleanup)
  }

  const decide = (response: "once" | "always" | "reject", approvedAlways: string[], deniedAlways: string[]) => {
    const perm = permissionRequest()
    if (!perm || session.respondingPermissions().has(perm.id)) return
    session.respondToPermission(perm.id, response, approvedAlways, deniedAlways)
  }

  return (
    <div class="chat-view">
      <TaskHeader readonly={props.readonly} />
      <div class="chat-messages-wrapper">
        <div class="chat-messages">
          <MessageList onSelectSession={props.onSelectSession} />
        </div>
      </div>

      <Show when={dock()}>
        <div class="chat-input">
          <Show when={server.connectionState() === "error" && server.errorMessage()}>
            <StartupErrorBanner errorMessage={server.errorMessage()!} errorDetails={server.errorDetails()!} />
          </Show>
          <Show when={questionRequest()} keyed>
            {(req) => <QuestionDock request={req} />}
          </Show>
          <Show when={permissionRequest()} keyed>
            {(perm) => (
              <PermissionDock
                request={perm}
                responding={session.respondingPermissions().has(perm.id)}
                onDecide={decide}
              />
            )}
          </Show>
          <Show when={!props.readonly && hasMessages() && idle() && !blocked()}>
            <div class="new-task-button-wrapper">
              <Button
                variant="secondary"
                size="small"
                data-full-width="true"
                onClick={() => window.dispatchEvent(new CustomEvent("newTaskRequest"))}
                aria-label={language.t("command.session.new.task")}
              >
                {language.t("command.session.new.task")}
              </Button>
              <Show when={isSidebar()}>
                <Button
                  variant="ghost"
                  size="small"
                  data-full-width="true"
                  onClick={() => vscode.postMessage({ type: "openChanges" })}
                  aria-label={language.t("command.session.show.changes")}
                >
                  <Icon name="file-tree" size="small" />
                  {language.t("command.session.show.changes")}
                </Button>
              </Show>
              <Show when={canContinueInWorktree()}>
                <Button
                  variant="ghost"
                  size="small"
                  data-full-width="true"
                  disabled={transferring()}
                  onClick={() => {
                    const sid = id()
                    if (!sid) return
                    setTransferring(true)
                    setTransferDetail("Capturing changes...")
                    vscode.postMessage({ type: "continueInWorktree", sessionId: sid })
                  }}
                  aria-label="Continue in Worktree"
                >
                  <Show when={transferring()} fallback={<Icon name="branch" size="small" />}>
                    <Spinner class="chat-spinner-small" />
                  </Show>
                  {transferring() ? transferDetail() : "Continue in Worktree"}
                </Button>
              </Show>
            </div>
          </Show>
          <Show when={!props.readonly}>
            <PromptInput blocked={blocked} />
          </Show>
        </div>
      </Show>
    </div>
  )
}
