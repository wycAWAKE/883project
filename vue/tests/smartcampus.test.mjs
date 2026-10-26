import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'

test('the rendered portal, sidebar and all six real routes share one module registry', async () => {
  const server = await createServer({ server: { middlewareMode: true, watch: null }, optimizeDeps: { noDiscovery: true, include: [] }, appType: 'custom' })
  try {
    const { campusModules } = await server.ssrLoadModule('/src/config/modules.js')
    const { routes } = await server.ssrLoadModule('/src/router/routes.js')
    const { createSSRApp, h } = await import('vue')
    const { renderToString } = await import('vue/server-renderer')
    const { createRouter, createMemoryHistory, RouterView } = await import('vue-router')
    const router = createRouter({ history: createMemoryHistory(), routes })
    const renderPage = () => renderToString(createSSRApp({ render: () => h(RouterView) }).use(router))
    const expected = ['/faq', '/courses', '/bookings', '/repairs', '/applications', '/notifications']
    assert.deepEqual(campusModules.map(module => module.path), expected)
    await router.push('/home')
    let html = await renderPage()
    for (const path of expected) {
      // One navigation link and one home card must lead to each real module page.
      assert.equal(html.split(`href="${path}"`).length - 1, 2, path)
    }
    assert.doesNotMatch(html, /href="\/(?:stuInfo|roomInfo|WaterInfo|ElectricInfo|adjustRoomInfo)"/)
    assert.match(html, /SmartCampus/)
    for (const module of campusModules) {
      await router.push(module.path)
      const matched = router.currentRoute.value.matched.at(-1)
      assert.equal(matched.meta.moduleId, module.id)
      html = await renderPage()
      assert.match(html, new RegExp(`<h1[^>]*>${module.name}</h1>`))
      assert.match(html, /Under development/)
      assert.match(html, /Back to home/)
    }
    // Obsolete bookmarks and unknown URLs recover to the portal.
    await router.push('/stuInfo')
    assert.equal(router.currentRoute.value.path, '/home')
  } finally {
    await server.close()
  }
})
