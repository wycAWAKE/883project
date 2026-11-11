import test from 'node:test'
import assert from 'node:assert/strict'
import { createRepairsApi, RepairApiError } from '../src/api/repairs.js'
import { createRepairStaffModel, repairStaffActions, repairStaffDraftStore } from '../src/utils/repair.js'
const actor={ identity:'dormManager',user:{ username:'staff1' } }
const deferred=()=>{ let resolve,reject; const promise=new Promise((yes,no)=>{ resolve=yes; reject=no }); return { promise,resolve,reject } }
function fixture(status='IN_PROGRESS', overrides={}) {
  let ticket={ id:1,status,version:2,assigneeUsername:'staff1' }
  const calls=[]
  const api={ list:async params=>{ calls.push(['list',params]); return { records:[{ ...ticket }],total:1 } },
    detail:async()=>({ ...ticket }),history:async()=>[],staff:async()=>[{ username:'staff1',displayName:'First' },{ username:'staff2',displayName:'Second' }],
    addProgress:async(id,body)=>{ calls.push(['progress',id,body]); ticket.version++; return { ...ticket } },
    assign:async(id,body)=>{ calls.push(['assign',id,body]); ticket={ ...ticket,status:'ASSIGNED',assigneeUsername:body.assigneeUsername,version:ticket.version+1 }; return { ...ticket } },
    changeStatus:async(id,body)=>{ calls.push(['status',id,body]); ticket={ ...ticket,status:body.targetStatus,version:ticket.version+1 }; return { ...ticket } }, ...overrides }
  return { api,calls,set:values=>Object.assign(ticket,values),model:createRepairStaffModel({ api,actor }) }
}
test('staff action availability follows role, owner and terminal state',()=>{
  assert.deepEqual(repairStaffActions(actor,{ status:'ASSIGNED',assigneeUsername:'staff1' }),['assign','start'])
  assert.deepEqual(repairStaffActions(actor,{ status:'IN_PROGRESS',assigneeUsername:'staff2' }),[])
  assert.deepEqual(repairStaffActions({ identity:'admin' },{ status:'IN_PROGRESS' }),['progress','resolve'])
  assert.deepEqual(repairStaffActions(actor,{ status:'CLOSED',assigneeUsername:'staff1' }),[])
  assert.deepEqual(repairStaffActions({ identity:'stu' },{ status:'SUBMITTED' }),[])
})
test('staff API uses protected options and the correct write methods and fields',async()=>{
  const calls=[]; const client={ get:async()=>({ code:'0',data:[{ username:'a',displayName:'A',password:'exclude' }] }),
    put:async(path,body)=>{ calls.push(['put',path,body]); return { code:'0',data:{} } },
    post:async(path,body)=>{ calls.push(['post',path,body]); return { code:'0',data:{} } } }
  const api=createRepairsApi(client); assert.deepEqual(await api.staff(),[{ username:'a',displayName:'A' }])
  await api.assign('1',{ version:2,assigneeUsername:'a' }); await api.changeStatus('1',{ version:3,targetStatus:'IN_PROGRESS' }); await api.addProgress('1',{ version:4,note:'Work' })
  assert.deepEqual(calls.map(call=>call.slice(0,2)),[['put','/repairs/1/assignment'],['put','/repairs/1/status'],['post','/repairs/1/progress']])
})
test('assignment uses the selected staff and current detail version',async()=>{
  const { model,calls }=fixture('SUBMITTED'); await model.openTicket('1'); model.beginOperation('assign'); await model.loadStaff(); await Promise.resolve()
  model.state.assigneeUsername='staff2'; model.state.note=' Inspect '; assert.equal(await model.perform(),true)
  assert.deepEqual(calls.find(call=>call[0]==='assign'),['assign','1',{ version:2,note:'Inspect',assigneeUsername:'staff2' }])
  assert.equal(model.state.detail.assigneeUsername,'staff2'); assert.equal(model.state.operation,'')
})
test('required notes and non-owner actions are rejected before making a write',async()=>{
  const { model,calls }=fixture(); await model.openTicket('1'); model.beginOperation('resolve'); model.state.note='\u3000'
  assert.equal(await model.perform(),false); assert.equal(calls.filter(call=>call[0]==='status').length,0)
  const other=createRepairStaffModel({ api:fixture().api,actor:{ identity:'dormManager',user:{ username:'other' } } })
  await other.openTicket('1'); assert.equal(other.beginOperation('progress'),false)
})
test('a conflict refreshes the record, retains the note and requires explicit reconfirmation',async()=>{
  let attempts=0
  const value=fixture('IN_PROGRESS',{ addProgress:async()=>{ attempts++; if(attempts===1) { value.set({ version:3 }); throw new RepairApiError(409,'Changed') } value.set({ version:4 }); return {} } })
  const { model }=value; await model.openTicket('1'); model.beginOperation('progress'); model.state.note='Keep this note'
  assert.equal(await model.perform(),false); assert.equal(model.state.note,'Keep this note'); assert.equal(model.state.detail.version,3)
  assert.equal(model.state.requiresReview,true); assert.equal(await model.perform(),false); assert.equal(attempts,1)
  assert.equal(model.acceptRefreshed(),true); assert.equal(await model.perform(),true); assert.equal(attempts,2)
})
test('network uncertainty requires a successful refresh before accepting another version',async()=>{
  const { model }=fixture('IN_PROGRESS',{ addProgress:async()=>{ throw new RepairApiError(0,'Network') } })
  await model.openTicket('1'); model.beginOperation('progress'); model.state.note='Working'; await model.perform()
  assert.equal(model.acceptRefreshed(),false); assert.equal(model.state.note,'Working')
  await model.refreshTicket(); assert.equal(model.acceptRefreshed(),true)
})
test('a pending staff write blocks duplicate writes and closing the detail',async()=>{
  const pending=deferred(); let attempts=0
  const { model }=fixture('IN_PROGRESS',{ addProgress:()=>{ attempts++; return pending.promise } })
  await model.openTicket('1'); model.beginOperation('progress'); model.state.note='Working'
  const first=model.perform(); model.closeDetail(); await model.perform()
  assert.equal(model.state.detailOpen,true); assert.equal(attempts,1)
  pending.resolve({ id:1,version:3,status:'IN_PROGRESS' }); await first
})
test('a successful write is not reported as failed when read refresh fails',async()=>{
  let detailCalls=0
  const { model }=fixture('IN_PROGRESS',{ detail:async()=>{ if(detailCalls++) throw new RepairApiError(500,'Read failed'); return { id:1,status:'IN_PROGRESS',assigneeUsername:'staff1',version:2 } } })
  await model.openTicket('1'); model.beginOperation('progress'); model.state.note='Working'
  assert.equal(await model.perform(),true); assert.match(model.state.notice,/successfully/); assert.equal(model.state.operation,''); assert.ok(model.state.detailError)
})
test('missing records close the detail and preserve a clear queue notice',async()=>{
  const { model }=fixture('IN_PROGRESS',{ addProgress:async()=>{ throw new RepairApiError(404,'Gone') } })
  await model.openTicket('1'); model.beginOperation('progress'); model.state.note='Working'; await model.perform()
  assert.equal(model.state.detailOpen,false); assert.match(model.state.notice,/no longer available/)
})
test('staff drafts are isolated by role and account and restored only for the same ticket',async()=>{
  const values=new Map(), storage={ getItem:key=>values.get(key)||null,setItem:(key,value)=>values.set(key,value),removeItem:key=>values.delete(key) }
  const drafts=repairStaffDraftStore(storage,actor)
  drafts.save({ ticketId:'1',operation:'progress',note:'Keep on expiry',assigneeUsername:'staff1' })
  assert.equal(repairStaffDraftStore(storage,{ identity:'admin',user:{ username:'staff1' } }).read(),null)
  const model=createRepairStaffModel({ api:fixture().api,actor,drafts }); await model.openTicket('1')
  assert.equal(model.state.note,'Keep on expiry'); assert.equal(model.state.requiresReview,true)
  assert.equal(model.acceptRefreshed(),true); await model.perform(); assert.equal(drafts.read(),null)
})
