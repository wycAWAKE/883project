import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { displayText, displayMessage, namePattern } from '../src/utils/english.js'

test('display translation keeps original records and filter values intact', () => {
  const record = Object.freeze({ gender: '女', state: '通过', type: '充值', grade: '优秀' })
  assert.deepEqual(Object.values(record).map(displayText), ['Female', 'Approved', 'Top-up', 'Excellent'])
  assert.equal(record.state, '通过')
})

test('translates both pending repair states and all approval outcomes', () => {
  assert.deepEqual(['待处理', '未处理', '未完成', '完成', '通过', '驳回'].map(displayText),
    ['Pending', 'Pending', 'Incomplete', 'Completed', 'Approved', 'Rejected'])
  assert.equal(displayText('充值'), 'Top-up')
  assert.equal(displayText('扣费'), 'Deduction')
  assert.equal(displayText('优秀'), 'Excellent')
  assert.equal(displayText('合格'), 'Pass')
  assert.equal(displayText('不合格'), 'Fail')
})

test('leaves user content and English values unchanged', () => {
  assert.equal(displayText('用户自己的公告正文'), '用户自己的公告正文')
  assert.equal(displayText('Building A'), 'Building A')
  assert.equal(displayText(null), '')
})

test('translates errors only for display, preserving original response comparisons', () => {
  const response = Object.freeze({ code: '-1', msg: '重复操作' })
  assert.equal(displayMessage(response.msg), 'This operation has already been performed.')
  assert.equal(response.msg, '重复操作')
  assert.equal(displayMessage('用户名或密码错误'), 'Incorrect username or password.')
  assert.equal(displayMessage('Network Error'), 'Network Error')
})

test('unknown Chinese errors use English fallback and retain diagnostics', () => {
  const diagnostics = []
  const warn = console.warn
  console.warn = (...args) => diagnostics.push(args)
  try {
    assert.equal(displayMessage('未预料的新错误'), 'Unable to complete the operation. Please try again.')
    assert.ok(diagnostics.some(args => args.includes('未预料的新错误')))
  } finally { console.warn = warn }
})

test('names accept English punctuation and existing Chinese names within VARCHAR(50)', () => {
  for (const name of ['Alex Smith', "O'Connor", 'Anne-Marie', 'Mary O’Neil', '张三', '阿不都·买买提', 'A'.repeat(50)]) {
    assert.ok(namePattern.test(name), name)
  }
  for (const name of ['', ' ', ' Alex', 'Alex ', 'Alex123', '<script>', 'A'.repeat(51)]) {
    assert.equal(namePattern.test(name), false, name)
  }
})

test('every existing backend error has a specific English message', () => {
  const directory = new URL('../../springboot/src/main/java/com/example/springboot/controller/', import.meta.url)
  for (const file of readdirSync(directory).filter(name => name.endsWith('.java'))) {
    const source = readFileSync(new URL(file, directory), 'utf8')
    for (const [, message] of source.matchAll(/Result\.error\("[^"]+",\s*"([^"]+)"\)/g)) {
      const english = displayMessage(message)
      assert.doesNotMatch(english, /\p{Script=Han}/u)
      assert.notEqual(english, 'Unable to complete the operation. Please try again.', message)
    }
  }
})
