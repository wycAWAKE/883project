import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { parse as parseSfc } from '@vue/compiler-sfc'
import { parse as parseTemplate } from '@vue/compiler-dom'

const han = /\p{Script=Han}/u
test('all authored visible text is English; stored radio and option values stay Chinese', () => {
  for (const folder of ['views', 'components']) {
    const directory = new URL('../src/' + folder + '/', import.meta.url)
    for (const file of readdirSync(directory).filter(name => name.endsWith('.vue'))) {
      const { descriptor } = parseSfc(readFileSync(new URL(file, directory), 'utf8'))
      const ast = parseTemplate(descriptor.template.content)
      function walk(node) {
        if (node.type === 5 && /\.(state|gender|type|grade)\b/.test(node.content.content)) {
          assert.match(node.content.content, /\$en\(/, file + ': raw enum in details')
        }
        if (node.type === 2) assert.doesNotMatch(node.content, han, file + ': visible text')
        for (const prop of node.props || []) {
          if (prop.type !== 6 || !prop.value) continue
          if (['placeholder', 'title', 'content', 'description', 'label'].includes(prop.name)) {
            if (node.tag === 'el-radio' && prop.name === 'label') continue
            assert.doesNotMatch(prop.value.content, han, file + ': ' + prop.name)
          }
        }
        for (const child of node.children || []) walk(child)
      }
      walk(ast)
    }
  }
})

test('English demo scripts cannot select the original database', () => {
  for (const name of ['dormitory_en_demo.sql', 'test_data_en.sql']) {
    const sql = readFileSync(new URL('../../database/' + name, import.meta.url), 'utf8')
    assert.match(sql, /USE dormitory_en_demo;/)
    assert.doesNotMatch(sql, /\bUSE\s+dormitory\s*;/i)
    assert.doesNotMatch(sql, /DROP\s+(?:TABLE|DATABASE)|TRUNCATE/i)
    const internalValues = new Set(['男', '女', '未处理', '待处理', '未完成', '完成', '通过', '驳回', '充值', '扣费', '优秀', '良好', '合格', '不合格', '待改进'])
    for (const [, value] of sql.matchAll(/'([^']*)'/g)) {
      if (han.test(value)) assert.ok(internalValues.has(value), name + ': untranslated data: ' + value)
    }
  }
})

test('each seeded inspection grade has an English select label when editing', () => {
  const page = readFileSync(new URL('../src/views/CheckInfo.vue', import.meta.url), 'utf8')
  const options = [...page.matchAll(/<el-option\s+label="([^"]+)"\s+value="([^"]+)"/g)]
  const labels = new Map(options.map(([, label, value]) => [value, label]))
  for (const file of ['dormitory_en_demo.sql', 'test_data_en.sql']) {
    const sql = readFileSync(new URL('../../database/' + file, import.meta.url), 'utf8')
    const records = sql.match(/INSERT INTO dorm_check\b[\s\S]*?;/)[0]
    for (const [, grade] of records.matchAll(/'([^']+)'\s*,\s*'[^']*'\s*,\s*'\d{4}-/g)) {
      assert.ok(labels.has(grade), 'Missing select label for seeded grade: ' + grade)
      assert.doesNotMatch(labels.get(grade), han)
    }
  }
})
