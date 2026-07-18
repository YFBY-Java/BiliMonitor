import { afterEach, describe, expect, it, vi } from 'vitest'
import { useQrPolling } from './useQrPolling'

interface Status {
  status: string
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

async function flushPromises() {
  await Promise.resolve()
  await Promise.resolve()
}

describe('useQrPolling', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('never starts a second request while polling is in flight', async () => {
    vi.useFakeTimers()
    const first = deferred<Status>()
    const second = deferred<Status>()
    const poll = vi.fn()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const polling = useQrPolling({ poll, intervalMs: 1_000 })

    polling.start()
    await vi.advanceTimersByTimeAsync(0)
    await vi.advanceTimersByTimeAsync(5_000)
    expect(poll).toHaveBeenCalledTimes(1)

    first.resolve({ status: 'WAITING' })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1_000)
    expect(poll).toHaveBeenCalledTimes(2)
    second.resolve({ status: 'WAITING' })
    polling.stop()
  })

  it('stops scheduling after a terminal state', async () => {
    vi.useFakeTimers()
    const onResult = vi.fn()
    const poll = vi.fn().mockResolvedValue({ status: 'SUCCESS' })
    const polling = useQrPolling({ poll, intervalMs: 500, onResult })

    polling.start()
    await vi.advanceTimersByTimeAsync(0)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(5_000)

    expect(onResult).toHaveBeenCalledWith({ status: 'SUCCESS' })
    expect(poll).toHaveBeenCalledTimes(1)
    expect(polling.active.value).toBe(false)
  })

  it('ignores an in-flight result after the dialog stops polling', async () => {
    vi.useFakeTimers()
    const request = deferred<Status>()
    const onResult = vi.fn()
    const polling = useQrPolling({ poll: () => request.promise, intervalMs: 500, onResult })

    polling.start()
    await vi.advanceTimersByTimeAsync(0)
    polling.stop()
    request.resolve({ status: 'WAITING' })
    await flushPromises()

    expect(onResult).not.toHaveBeenCalled()
    expect(polling.active.value).toBe(false)
  })

  it('reschedules a reopened generation while the previous request is still pending', async () => {
    vi.useFakeTimers()
    const previous = deferred<Status>()
    const poll = vi.fn()
      .mockReturnValueOnce(previous.promise)
      .mockResolvedValueOnce({ status: 'WAITING' })
    const polling = useQrPolling({ poll, intervalMs: 500 })

    polling.start()
    await vi.advanceTimersByTimeAsync(0)
    polling.stop()
    polling.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(poll).toHaveBeenCalledTimes(1)

    previous.resolve({ status: 'WAITING' })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)

    expect(poll).toHaveBeenCalledTimes(2)
    polling.stop()
  })
})
