export type DiffLine = {
  text: string
  kind: 'same' | 'add' | 'remove'
  old?: number
  next?: number
}
// Bound the comparison so unusually large demo files cannot lock the interface.
export function diff(before = '', after = ''): DiffLine[] {
  const a = before ? before.split('\n') : [],
    b = after ? after.split('\n') : []
  if (a.length * b.length > 160000)
    return [
      ...a.map((text, i) => ({ text, kind: 'remove' as const, old: i + 1 })),
      ...b.map((text, i) => ({ text, kind: 'add' as const, next: i + 1 })),
    ]
  const table = Array.from({ length: a.length + 1 }, () => new Uint16Array(b.length + 1))
  for (let i = a.length - 1; i >= 0; i--)
    for (let j = b.length - 1; j >= 0; j--)
      table[i][j] =
        a[i] === b[j] ? table[i + 1][j + 1] + 1 : Math.max(table[i + 1][j], table[i][j + 1])
  const result: DiffLine[] = []
  let i = 0,
    j = 0
  while (i < a.length || j < b.length) {
    if (i < a.length && j < b.length && a[i] === b[j]) {
      result.push({ text: a[i], kind: 'same', old: ++i, next: ++j })
    } else if (i < a.length && (j === b.length || table[i + 1][j] >= table[i][j + 1])) {
      result.push({ text: a[i], kind: 'remove', old: ++i })
    } else {
      result.push({ text: b[j], kind: 'add', next: ++j })
    }
  }
  return result
}
