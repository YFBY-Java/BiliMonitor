import { describe, expect, it } from 'vitest'
import { createRequestGeneration } from './requestGeneration'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(next => { resolve = next })
  return { promise, resolve }
}

describe('request generation guard', () => {
  it('ignores a start result from a dialog generation closed before it resolved', async () => {
    const generations = createRequestGeneration()
    const oldStart = deferred<string>()
    const applied: string[] = []
    const oldToken = generations.next()
    const oldTask = oldStart.promise.then(value => {
      if (generations.isCurrent(oldToken)) applied.push(value)
    })

    generations.invalidate()
    const reopenedToken = generations.next()
    oldStart.resolve('old-login-id')
    await oldTask

    expect(applied).toEqual([])
    expect(generations.isCurrent(reopenedToken)).toBe(true)
  })

  it('applies only the newest image when responses resolve out of order', async () => {
    const generations = createRequestGeneration()
    const first = deferred<string>()
    const second = deferred<string>()
    let displayed = ''
    const firstToken = generations.next()
    const firstTask = first.promise.then(value => {
      if (generations.isCurrent(firstToken)) displayed = value
    })
    const secondToken = generations.next()
    const secondTask = second.promise.then(value => {
      if (generations.isCurrent(secondToken)) displayed = value
    })

    second.resolve('new-image')
    await secondTask
    first.resolve('old-image')
    await firstTask

    expect(displayed).toBe('new-image')
  })
})
