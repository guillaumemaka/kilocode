import { Component, createMemo, For, Show } from "solid-js"
import { Card } from "@kilocode/kilo-ui/card"

import { useConfig } from "../../../context/config"
import { useLanguage } from "../../../context/language"

const WorkflowsTab: Component = () => {
  const language = useLanguage()
  const { config } = useConfig()

  const cmds = createMemo(() => Object.entries(config().command ?? {}))

  return (
    <div>
      {/* Description */}
      <div
        style={{
          "font-size": "12px",
          color: "var(--text-weak-base, var(--vscode-descriptionForeground))",
          "margin-bottom": "12px",
          "line-height": "1.5",
        }}
      >
        {language.t("settings.agentBehaviour.workflows.description")}
      </div>

      <Show
        when={cmds().length > 0}
        fallback={
          <Card>
            <div
              style={{
                "font-size": "12px",
                color: "var(--text-weak-base, var(--vscode-descriptionForeground))",
              }}
            >
              {language.t("settings.agentBehaviour.workflows.empty")}
            </div>
          </Card>
        }
      >
        <Card>
          <For each={cmds()}>
            {([name, cmd], index) => (
              <div
                style={{
                  padding: "8px 0",
                  "border-bottom": index() < cmds().length - 1 ? "1px solid var(--border-weak-base)" : "none",
                }}
              >
                <div style={{ display: "flex", "align-items": "center", gap: "6px" }}>
                  <span
                    style={{
                      "font-weight": "500",
                      "font-family": "var(--vscode-editor-font-family, monospace)",
                    }}
                  >
                    /{name}
                  </span>
                </div>
                <Show when={cmd.description}>
                  <div
                    style={{
                      "font-size": "12px",
                      color: "var(--text-weak-base, var(--vscode-descriptionForeground))",
                      "margin-top": "2px",
                    }}
                  >
                    {cmd.description}
                  </div>
                </Show>
                <Show when={cmd.template}>
                  <div
                    style={{
                      "font-size": "11px",
                      "font-family": "var(--vscode-editor-font-family, monospace)",
                      color: "var(--text-weak-base, var(--vscode-descriptionForeground))",
                      "margin-top": "4px",
                      overflow: "hidden",
                      "text-overflow": "ellipsis",
                      "white-space": "nowrap",
                    }}
                  >
                    {cmd.template}
                  </div>
                </Show>
              </div>
            )}
          </For>
        </Card>
      </Show>
    </div>
  )
}

export default WorkflowsTab
