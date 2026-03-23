import * as vscode from "vscode"
import { AutocompleteServiceManager } from "./AutocompleteServiceManager"
import type { KiloConnectionService } from "../cli-backend"

export const registerAutocompleteProvider = (
  context: vscode.ExtensionContext,
  connectionService: KiloConnectionService,
) => {
  const autocompleteManager = new AutocompleteServiceManager(context, connectionService)
  context.subscriptions.push(autocompleteManager)

  // Register AutocompleteServiceManager Commands
  context.subscriptions.push(
    vscode.commands.registerCommand("kilo-code.new.autocomplete.reload", async () => {
      await autocompleteManager.load()
    }),
  )
  context.subscriptions.push(
    vscode.commands.registerCommand("kilo-code.new.autocomplete.codeActionQuickFix", async () => {
      return
    }),
  )
  context.subscriptions.push(
    vscode.commands.registerCommand("kilo-code.new.autocomplete.cancelSuggestions", () => {
      vscode.commands.executeCommand("editor.action.inlineSuggest.hide")
      vscode.commands.executeCommand("setContext", "kilo-code.new.autocomplete.hasSuggestions", false)
    }),
  )
  context.subscriptions.push(
    vscode.commands.registerCommand("kilo-code.new.autocomplete.generateSuggestions", async () => {
      autocompleteManager.codeSuggestion()
    }),
  )
  context.subscriptions.push(
    vscode.commands.registerCommand("kilo-code.new.autocomplete.showIncompatibilityExtensionPopup", async () => {
      await autocompleteManager.showIncompatibilityExtensionPopup()
    }),
  )
  context.subscriptions.push(
    vscode.commands.registerCommand("kilo-code.new.autocomplete.disable", async () => {
      await autocompleteManager.disable()
    }),
  )

  // Register AutocompleteServiceManager Code Actions
  context.subscriptions.push(
    vscode.languages.registerCodeActionsProvider("*", autocompleteManager.codeActionProvider, {
      providedCodeActionKinds: Object.values(autocompleteManager.codeActionProvider.providedCodeActionKinds),
    }),
  )

  // Re-load when autocomplete settings change (e.g. toggled from webview or VS Code settings UI).
  // When autocomplete is turned on, also ensure the CLI backend is running — the eager connect()
  // in activate() only fires if autocomplete was already enabled at startup.
  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration("kilo-code.new.autocomplete")) {
        const enabled =
          vscode.workspace.getConfiguration("kilo-code.new.autocomplete").get<boolean>("enableAutoTrigger") ?? true
        if (enabled) {
          const dir = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath
          if (dir) {
            connectionService.connect(dir).catch((err) => {
              console.error("[Kilo New] Autocomplete: Failed to start CLI backend on config change:", err)
            })
          }
        }
        void autocompleteManager.load()
      }
    }),
  )
}
