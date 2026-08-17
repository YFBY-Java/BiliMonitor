import { afterEach, describe, expect, it, vi } from 'vitest'

import { useSessionAutoRefresh } from './useSessionAutoRefresh'

function deferred() {
  let resolve!: () => void
  const promise = new Promise<void>(resolvePromise => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

describe('useSessionAutoRefresh', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('refreshes at the user-selected number of seconds', async () => {
    vi.useFakeTimers()
    const refresh = vi.fn().mockResolvedValue(undefined)
    const polling = useSessionAutoRefresh({ refresh, intervalSeconds: 10 })

    polling.start()
    await vi.advanceTimersByTimeAsync(9_999)
    expect(refresh).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(refresh).toHaveBeenCalledTimes(1)

    polling.setIntervalSeconds(3)
    await vi.advanceTimersByTimeAsync(2_999)
    expect(refresh).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(refresh).toHaveBeenCalledTimes(2)
    polling.stop()
  })

  it('does not overlap refresh requests and supports an immediate refresh', async () => {
    vi.useFakeTimers()
    const pending = deferred()
    const refresh = vi.fn().mockReturnValueOnce(pending.promise).mockResolvedValue(undefined)
    const polling = useSessionAutoRefresh({ refresh, intervalSeconds: 2 })

    polling.start()
    await vi.advanceTimersByTimeAsync(2_000)
    await vi.advanceTimersByTimeAsync(10_000)
    expect(refresh).toHaveBeenCalledTimes(1)

    pending.resolve()
    await Promise.resolve()
    await polling.refreshNow()
    expect(refresh).toHaveBeenCalledTimes(2)
    polling.stop()
  })
})
