// Optional real-browser smoke test. The Java harness supplies an isolated API and Vite origin.
import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { runStaffWorkflow } from './repair-staff.browser.mjs'

const playwright = process.env.M4_PLAYWRIGHT_MODULE
  ? await import(pathToFileURL(resolve(process.env.M4_PLAYWRIGHT_MODULE)).href) : await import('playwright')
const { chromium, request } = playwright
const base = process.env.M4_BROWSER_BASE_URL
if (!base) throw new Error('Set M4_BROWSER_BASE_URL to the isolated test server.')
const output = process.env.M4_BROWSER_OUTPUT || './browser-output'
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: process.env.M4_BROWSER_CHANNEL || 'msedge', headless: true })
const errors = [], checks = []
const context = await browser.newContext({ viewport:{ width:1366,height:1000 } })
const page = await context.newPage()
page.on('pageerror',error => errors.push(error.message))
const pauseUntil = async condition => { for (let i=0;i<100;i++) { if (await condition()) return; await new Promise(r=>setTimeout(r,100)) } throw new Error('Timed out waiting for UI state.') }
async function login(target,username) {
  await target.getByPlaceholder('Username',{ exact:true }).fill(username)
  await target.getByPlaceholder('Password',{ exact:true }).fill('m4_demo_only')
  await target.getByText('Student',{ exact:true }).click()
  assert.equal(await target.getByRole('radio',{ name:'Student',exact:true }).isChecked(),true)
  await target.getByRole('button',{ name:'Sign In',exact:true }).click()
  await target.waitForURL(url => !url.pathname.startsWith('/Login'))
}
async function assertVisible(locator) { await locator.waitFor({ state:'visible',timeout:15000 }) }
let staffApi
try {
  await page.goto(base+'/Login'); await login(page,'m4_student_a')
  await page.goto(base+'/repairs')
  await assertVisible(page.getByRole('heading',{ name:'My fault reports' }))
  await assertVisible(page.getByRole('heading',{ name:'No reports yet' }))
  checks.push('real student login and empty state')
  await page.getByRole('button',{ name:'New report',exact:true }).click()
  await page.getByLabel('Title',{ exact:true }).fill('Browser light report')
  await page.getByLabel('Location type',{ exact:true }).selectOption('CLASSROOM')
  await page.getByLabel('Location',{ exact:true }).fill('Block A room 101')
  await page.getByLabel('Description',{ exact:true }).fill('The light is broken. <b>Literal note</b>')
  await page.getByRole('button',{ name:'Submit report',exact:true }).click()
  await assertVisible(page.getByRole('heading',{ name:'Browser light report',exact:true }))
  const id = new URL(page.url()).searchParams.get('ticketId')
  assert.match(id,/^[1-9]\d*$/)
  await assertVisible(page.getByText('The light is broken. <b>Literal note</b>',{ exact:true }))
  assert.equal(await page.locator('.repair-description b').count(),0)
  checks.push('real create, deep link, detail and escaped user content')
  await page.getByRole('button',{ name:'Close report details' }).click()
  await page.getByLabel('Search reports',{ exact:true }).fill('does-not-exist')
  await page.getByRole('button',{ name:'Search',exact:true }).click()
  await assertVisible(page.getByRole('heading',{ name:'No matching reports' }))
  await page.getByLabel('Search reports',{ exact:true }).fill('Browser')
  await page.getByRole('button',{ name:'Search',exact:true }).click()
  await assertVisible(page.getByRole('button',{ name:'Browser light report',exact:true }))
  checks.push('real title filtering and empty search recovery')
  const failList = route => route.abort('failed')
  await page.route('**/api/repairs?**',failList)
  await page.getByRole('button',{ name:'Search',exact:true }).click()
  await assertVisible(page.getByRole('heading',{ name:'Unable to load reports' }))
  await page.unroute('**/api/repairs?**',failList)
  await page.getByRole('button',{ name:'Retry reports',exact:true }).click()
  await assertVisible(page.getByRole('button',{ name:'Browser light report',exact:true }))
  checks.push('network error and explicit retry recovery')

  staffApi=await request.newContext({ baseURL:base })
  const auth=await staffApi.post('/api/dormManager/login',{ data:{ username:'m4_staff',password:'m4_demo_only' } })
  assert.equal((await auth.json()).code,'0')
  const operations = [
    ['put','assignment',{ assigneeUsername:'m4_staff',version:0 }],
    ['put','status',{ targetStatus:'IN_PROGRESS',version:1,note:'Inspection started' }],
    ['post','progress',{ version:2,note:'Replacement part fitted' }],
    ['put','status',{ targetStatus:'RESOLVED',version:3,note:'Light replaced' }],
    ['put','status',{ targetStatus:'CLOSED',version:4,note:'Checked and closed' }],
  ]
  for (const [method,path,data] of operations) {
    const result=await staffApi[method](`/api/repairs/${id}/${path}`,{ data })
    assert.equal(result.status(),200,await result.text())
  }
  await page.goto(base+`/repairs?ticketId=${id}`)
  await assertVisible(page.getByText('Report closed',{ exact:true }))
  await assertVisible(page.getByText('Replacement part fitted',{ exact:true }))
  await page.screenshot({ path:resolve(output,'desktop-detail.png'),fullPage:true })
  await page.getByRole('button',{ name:'Close report details' }).click()
  checks.push('student sees the real six-event maintenance timeline after staff API processing')

  const mobile=await browser.newContext({ viewport:{ width:390,height:844 },storageState:await context.storageState() })
  const phone=await mobile.newPage(); phone.on('pageerror',error=>errors.push(error.message))
  await phone.goto(base+'/repairs')
  await assertVisible(phone.getByRole('button',{ name:'Browser light report',exact:true }))
  assert.equal(await phone.locator('.repair-page').evaluate(el=>el.scrollWidth<=el.clientWidth+1),true)
  await phone.screenshot({ path:resolve(output,'mobile-list.png'),fullPage:true })
  await phone.getByRole('button',{ name:`View report M4-${id}`,exact:true }).click()
  await assertVisible(phone.getByText('Report closed',{ exact:true }))
  await phone.screenshot({ path:resolve(output,'mobile-detail.png'),fullPage:true })
  await mobile.close(); checks.push('390px mobile list and real detail layout')

  const second=await browser.newContext({ viewport:{ width:1280,height:900 } })
  const other=await second.newPage(); other.on('pageerror',error=>errors.push(error.message))
  await other.goto(base+'/Login'); await login(other,'m4_student_b')
  await other.goto(base+`/repairs?ticketId=${id}`)
  await assertVisible(other.getByText('Ticket not found.',{ exact:true }))
  assert.equal(await other.locator('.repair-title-link').count(),0)
  await other.getByRole('button',{ name:'Close report details' }).click()
  checks.push('second student cannot read the first student report or its history')

  await other.getByRole('button',{ name:'New report',exact:true }).click()
  await other.getByLabel('Title',{ exact:true }).fill('Draft after expiry')
  await other.getByLabel('Location type',{ exact:true }).selectOption('PUBLIC_AREA')
  await other.getByLabel('Location',{ exact:true }).fill('Library entrance')
  await other.getByLabel('Description',{ exact:true }).fill('This draft must be restored')
  await second.request.get(base+'/api/main/signOut')
  await other.getByRole('button',{ name:'Submit report',exact:true }).click()
  await other.waitForURL(url=>url.pathname==='/Login' && url.searchParams.get('reason')==='expired')
  await login(other,'m4_student_b')
  await assertVisible(other.getByRole('heading',{ name:'Report a fault',exact:true }))
  assert.equal(await other.getByLabel('Title',{ exact:true }).inputValue(),'Draft after expiry')
  assert.equal(await other.getByLabel('Description',{ exact:true }).inputValue(),'This draft must be restored')
  const remaining=await second.request.get(base+'/api/repairs')
  assert.equal((await remaining.json()).data.total,0)
  await second.close(); checks.push('expired real Session redirects, restores same-account draft, and never resubmits automatically')
  await runStaffWorkflow({ browser,base,output,student:page,checks,errors })
  assert.deepEqual(errors,[])
  checks.push('no uncaught browser exceptions')
  console.log(JSON.stringify({ passed:true,checks },null,2))
} catch(error) {
  await page.screenshot({ path:resolve(output,'failure.png'),fullPage:true }).catch(()=>{})
  throw error
} finally { await staffApi?.dispose(); await browser.close() }
