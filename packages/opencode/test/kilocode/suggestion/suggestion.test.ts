import { describe, expect, test } from "bun:test"
import { Instance } from "../../../src/project/instance"
import { Suggestion } from "../../../src/kilocode/suggestion"
import { tmpdir } from "../../fixture/fixture"

describe("suggestion", () => {
  test("show adds pending request with blocking flag", async () => {
    await using tmp = await tmpdir({ git: true })
    await Instance.provide({
      directory: tmp.path,
      fn: async () => {
        const pending = Suggestion.show({
          sessionID: "ses_test",
          text: "Run review?",
          blocking: false,
          actions: [{ label: "Start", description: "Run it", prompt: "/local-review-uncommitted" }],
        })

        const list = await Suggestion.list()
        expect(list).toHaveLength(1)
        expect(list[0]?.blocking).toBe(false)
        expect(list[0]?.text).toBe("Run review?")

        await Suggestion.dismiss(list[0]!.id)
        await expect(pending).rejects.toBeInstanceOf(Suggestion.DismissedError)
      },
    })
  })

  test("accept resolves selected action and removes pending request", async () => {
    await using tmp = await tmpdir({ git: true })
    await Instance.provide({
      directory: tmp.path,
      fn: async () => {
        const ask = Suggestion.show({
          sessionID: "ses_test",
          text: "Next step?",
          actions: [
            { label: "Review", description: "Start review", prompt: "/local-review-uncommitted" },
            { label: "Test", description: "Run tests", prompt: "Run the relevant tests now." },
          ],
        })

        const list = await Suggestion.list()
        await Suggestion.accept({ requestID: list[0]!.id, index: 1 })

        await expect(ask).resolves.toEqual({
          label: "Test",
          description: "Run tests",
          prompt: "Run the relevant tests now.",
        })
        await expect(Suggestion.list()).resolves.toEqual([])
      },
    })
  })

  test("dismiss rejects pending request and removes it", async () => {
    await using tmp = await tmpdir({ git: true })
    await Instance.provide({
      directory: tmp.path,
      fn: async () => {
        const ask = Suggestion.show({
          sessionID: "ses_test",
          text: "Review changes?",
          actions: [{ label: "Start", prompt: "/local-review-uncommitted" }],
        })

        const list = await Suggestion.list()
        await Suggestion.dismiss(list[0]!.id)

        await expect(ask).rejects.toBeInstanceOf(Suggestion.DismissedError)
        await expect(Suggestion.list()).resolves.toEqual([])
      },
    })
  })
})
