import test from 'node:test'
import assert from 'node:assert/strict'
import { createRepairsApi, RepairApiError } from '../src/api/repairs.js'
import { createRepairStudentModel, validateRepairForm, validTicketId, repairDraftStore } from '../src/utils/repair.js'

const valid = () => ({ title: ' Light broken ', description: ' Please repair it ', locationType: 'CLASSROOM', locationText: ' Block A 101 ' })
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const cache = () => { const values = new Map(); return { getItem: key => values.get(key) ?? null, setItem: (key,value) => values.set(key,value), removeItem: key => values.delete(key) } }
const baseApi = () => ({ list: async () => ({ records: [], total: 0 }), create: async () => ({ id: 1 }), detail: async id => ({ id }), history: async () => [] })

test('report validation matches backend limits, removes whitespace and only submits allowed fields', () => {
  const result = validateRepairForm({ ...valid(), requesterUsername: 'forged', status: 'CLOSED' })
  assert.deepEqual(result.errors, {})
  assert.deepEqual(result.body, { title: 'Light broken', description: 'Please repair it', locationType: 'CLASSROOM', locationText: 'Block A 101' })
  for (const [field,max] of [['title',100],['description',2000],['locationText',200]]) {
    assert.ok(validateRepairForm({ ...valid(), [field]: '\u3000\u00a0' }).errors[field])
    assert.ok(validateRepairForm({ ...valid(), [field]: 'x'.repeat(max+1) }).errors[field])
  }
  assert.ok(validateRepairForm({ ...valid(), locationType: 'UNKNOWN' }).errors.locationType)
})
test('ticket deep links preserve long integer IDs and reject malformed or repeated values', () => {
  assert.equal(validTicketId('9223372036854775807'), '9223372036854775807')
  for (const value of ['0','01','-1','1/2','1e3','9223372036854775808',['1'],undefined]) assert.equal(validTicketId(value), null)
})
test('API uses the shared client without a second api prefix and checks response envelopes', async () => {
  const calls = []
  const api = createRepairsApi({ get: async (path,config) => { calls.push([path,config]); return { code:'0', data:{ records:[],total:0 } } },
    post: async (path,body) => { calls.push([path,body]); return { code:'0',data:{ id:9 } } } })
  await api.list({ pageNum:2 }); await api.create(valid())
  assert.equal(calls[0][0],'/repairs'); assert.equal(calls[1][0],'/repairs')
  const invalid = createRepairsApi({ get: async () => ({ code:'0', data:{} }) })
  await assert.rejects(invalid.list({}), error => error.status === 500)
})
test('API distinguishes network failure, HTTP authorization and unsuccessful business responses', async () => {
  for (const status of [0,401,403,404,409,500]) {
    const api = createRepairsApi({ get: async () => { throw status ? { response:{ status } } : new Error('network') } })
    await assert.rejects(api.detail('1'), error => error.status === status)
  }
  await assert.rejects(createRepairsApi({ post: async () => ({ code:'400',msg:'Invalid' }) }).create(valid()), error => error.status === 400)
})
test('session loading strips credentials and treats HTTP or legacy expiry as authentication failure', async () => {
  const api = createRepairsApi({ get: async path => ({ code:'0',data:path.endsWith('Identity') ? 'stu' : { username:'a',password:'secret',token:'obsolete' } }) })
  assert.deepEqual(await api.session(),{ identity:'stu',user:{ username:'a' } })
  for (const client of [{ get:async () => ({ code:'-1' }) }, { get:async () => { throw { response:{ status:401 } } } }]) {
    await assert.rejects(createRepairsApi(client).session(), error => error.status === 401)
  }
})
test('newer list queries win even when an old response arrives last', async () => {
  const requests = [deferred(),deferred()]; let index = 0
  const model = createRepairStudentModel({ api:{ ...baseApi(),list:() => requests[index++].promise } })
  const old = model.loadList(); model.state.status = 'CLOSED'; const latest = model.applyFilters()
  requests[1].resolve({ records:[{ id:2 }],total:1 }); await latest
  requests[0].resolve({ records:[{ id:1 }],total:100 }); await old
  assert.equal(model.state.records[0].id,2); assert.equal(model.state.total,1); assert.equal(model.state.listLoading,false)
})
test('filters and page-size changes reset pagination and use the chosen server parameters', async () => {
  const calls = []; const model = createRepairStudentModel({ api:{ ...baseApi(),list:async params => { calls.push(params); return { records:[],total:0 } } } })
  await model.changePage(3); model.state.keyword=' light '; await model.applyFilters(); await model.changeSize('20')
  assert.equal(calls[0].pageNum,3); assert.equal(calls[1].pageNum,1); assert.equal(calls[1].keyword,'light')
  assert.equal(calls[2].pageSize,20); assert.equal(calls[2].pageNum,1)
})
test('detail switching and closing cannot display a stale report', async () => {
  const old = deferred(); const model = createRepairStudentModel({ api:{ ...baseApi(), detail:id => id==='1' ? old.promise : Promise.resolve({ id }) } })
  const pending = model.openTicket('1'); await model.openTicket('2'); old.resolve({ id:'1' }); await pending
  assert.equal(model.state.detail.id,'2')
  await model.openTicket('bad'); assert.ok(model.state.detailError); assert.equal(model.state.detail,null)
  model.closeDetail(); assert.equal(model.state.detailOpen,false)
})
test('a pending submission cannot run twice and a timeout preserves input without auto retry', async () => {
  const pending=deferred(); let count=0
  const model=createRepairStudentModel({ api:{ ...baseApi(),create:() => { count++; return pending.promise } } })
  Object.assign(model.state.form,valid()); const first=model.submit(); await model.submit()
  pending.reject(new RepairApiError(0,'timeout')); await first
  assert.equal(count,1); assert.equal(model.state.form.title,valid().title); assert.equal(model.state.uncertain,true)
  assert.match(model.state.submitError,/Check your reports/)
})
test('a saved report remains successful when the subsequent list refresh fails', async () => {
  let count=0
  const model=createRepairStudentModel({ api:{ ...baseApi(),create:async () => { count++; return { id:5 } },list:async () => { throw new RepairApiError(500,'Unavailable') } } })
  Object.assign(model.state.form,valid()); const result=await model.submit()
  assert.equal(result.id,5); assert.equal(count,1); assert.match(model.state.submitSuccess,/M4-5/)
  assert.equal(model.state.submitError,''); assert.equal(model.state.form.title,''); assert.ok(model.state.listError)
})
test('session expiry saves only the current student draft and calls the sign-in handler', async () => {
  const storage=cache(); const drafts=repairDraftStore(storage,'alice'); let expired=0
  const model=createRepairStudentModel({ drafts,onExpired:() => expired++,api:{ ...baseApi(),create:async () => { throw new RepairApiError(401,'Expired') } } })
  Object.assign(model.state.form,valid()); await model.submit()
  assert.equal(expired,1); assert.deepEqual(drafts.read(),valid()); assert.equal(repairDraftStore(storage,'bob').read(),null)
})
test('drafts expire, tolerate unavailable storage and are removed after successful submission', async () => {
  const storage=cache(); const drafts=repairDraftStore(storage,'alice',() => 100)
  drafts.save(valid()); assert.equal(repairDraftStore(storage,'alice',() => 7200101).read(),null)
  const model=createRepairStudentModel({ drafts,api:baseApi() }); await model.submit(); assert.equal(drafts.read(),null)
  assert.equal(repairDraftStore(null,'alice').read(),null)
})
test('unmounted models ignore in-flight results and errors', async () => {
  const pending=deferred(); const model=createRepairStudentModel({ api:{ ...baseApi(),list:() => pending.promise } })
  const request=model.loadList(); model.dispose(); pending.resolve({ records:[{ id:1 }],total:1 }); await request
  assert.deepEqual([...model.state.records],[])
})


test('a student submission never emits navigation after disposal during the post-create refresh', async () => {
  const pending=deferred(); const model=createRepairStudentModel({ api:{ ...baseApi(),list:()=>pending.promise } })
  Object.assign(model.state.form,valid()); const submitting=model.submit(); await Promise.resolve()
  model.dispose(); pending.resolve({ records:[],total:0 })
  assert.equal(await submitting,null)
})
test('a successful create followed by session expiry does not navigate back to the protected report', async () => {
  let expired=0; const model=createRepairStudentModel({ onExpired:()=>expired++,api:{ ...baseApi(),list:async()=>{ throw new RepairApiError(401,'Expired') } } })
  Object.assign(model.state.form,valid()); assert.equal(await model.submit(),null); assert.equal(expired,1)
})
