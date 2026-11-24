import puppeteer from 'puppeteer-core'
import fs from 'node:fs'

const SHOTS = '.tmp-shots'
fs.mkdirSync(SHOTS, { recursive: true })
const errors = []
const results = []
const ok = (name, cond, extra = '') => {
  results.push(`${cond ? 'PASS' : 'FAIL'} ${name}${extra ? ' · ' + extra : ''}`)
  if (!cond) process.exitCode = 1
}

const browser = await puppeteer.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: 'new',
  args: ['--no-sandbox', '--disable-gpu', '--force-device-scale-factor=1'],
})

async function newPage(width, height) {
  const page = await browser.newPage()
  await page.setViewport({ width, height })
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message))
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push('console: ' + m.text())
  })
  return page
}

const overflow = (page) =>
  page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)

// ---------- 桌面端 ----------
const page = await newPage(1440, 900)
await page.goto('http://localhost:5173/', { waitUntil: 'networkidle0' })
await new Promise((r) => setTimeout(r, 800))
ok('首页渲染', await page.$('.hero h1') !== null)
ok('首页无横向溢出', (await overflow(page)) <= 0, `delta=${await overflow(page)}`)
await page.screenshot({ path: `${SHOTS}/01-home-desktop.png` })

// 皮肤切换
const skinBefore = await page.evaluate(() => document.documentElement.dataset.skin)
await page.click('.site-header .btn-ghost')
await new Promise((r) => setTimeout(r, 300))
const skinItems = await page.$$('.skin-pop .skin-item')
ok('皮肤菜单打开', skinItems.length >= 6)
const skinTarget = await page.evaluateHandle(() =>
  [...document.querySelectorAll('.skin-pop .skin-item')].find((el) => el.getAttribute('aria-pressed') === 'false'),
)
await skinTarget.asElement().click()
await new Promise((r) => setTimeout(r, 400))
const skinAfter = await page.evaluate(() => document.documentElement.dataset.skin)
ok('皮肤切换生效', skinBefore !== skinAfter, `${skinBefore} -> ${skinAfter}`)
await page.screenshot({ path: `${SHOTS}/02-home-skin.png` })

// 示例报告
await page.click('.demo-link')
await page.waitForFunction(() => location.hash === '#/report', { timeout: 30000 })
await page.waitForFunction(() => document.querySelectorAll('.report-card').length === 5, { timeout: 15000 })
await new Promise((r) => setTimeout(r, 1600))
const cards = await page.$$eval('.report-card h2', (els) => els.map((e) => e.textContent))
ok('五张报告卡片', cards.length === 5, cards.join(' / '))
const chips = await page.$$eval('.period-row .chip', (els) => els.map((e) => e.textContent.trim()))
ok('时间段选项', chips.length >= 3, chips.join(' | '))
const bodyText = await page.evaluate(() => document.body.innerText)
ok('无 NaN / undefined', !bodyText.includes('NaN') && !bodyText.includes('undefined'))
const canvases = await page.$$eval('.chart canvas', (els) => els.length)
ok('ECharts 画布渲染', canvases >= 4, `canvases=${canvases}`)
ok('报告页无横向溢出', (await overflow(page)) <= 0)
await page.screenshot({ path: `${SHOTS}/03-report-top.png` })
await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight * 0.45))
await new Promise((r) => setTimeout(r, 900))
await page.screenshot({ path: `${SHOTS}/04-report-mid.png` })
await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
await new Promise((r) => setTimeout(r, 900))
await page.screenshot({ path: `${SHOTS}/05-report-bottom.png` })

// 时间段切换
const totalBefore = await page.evaluate(() => document.querySelector('.report-card .big-figure')?.textContent)
const yearChip = await page.$$('.period-row .chip')
await yearChip[1].click()
await new Promise((r) => setTimeout(r, 1200))
const periodLabel = await page.evaluate(() => document.querySelector('.report-head h1 span')?.textContent)
ok('切换到年份', periodLabel.includes('2025'), periodLabel)
const text2025 = await page.evaluate(() => document.body.innerText)
ok('年份视图无 NaN', !text2025.includes('NaN'))

// 分享图
await page.evaluate(() => window.scrollTo(0, 0))
await new Promise((r) => setTimeout(r, 400))
const shareBtns = await page.$$('.report-card .card-head .btn')
await shareBtns[0].click()
await page.waitForSelector('.share-preview img', { timeout: 15000 })
await new Promise((r) => setTimeout(r, 500))
const imgSize = await page.$eval('.share-preview img', (img) => ({ w: img.naturalWidth, h: img.naturalHeight }))
ok('分享图 9:16 生成', imgSize.w === 1080 && imgSize.h === 1920, JSON.stringify(imgSize))
await page.screenshot({ path: `${SHOTS}/06-share-modal.png` })
const ratioChips = await page.$$('.modal .share-row .chip')
await ratioChips[1].click()
await new Promise((r) => setTimeout(r, 900))
const imgSize2 = await page.$eval('.share-preview img', (img) => ({ w: img.naturalWidth, h: img.naturalHeight }))
ok('分享图 1:1 生成', imgSize2.w === 1080 && imgSize2.h === 1080, JSON.stringify(imgSize2))
await page.click('.modal .close-x')
await new Promise((r) => setTimeout(r, 400))
ok('分享弹窗关闭', (await page.$('.modal-scrim')) === null)

// 带图表的卡片分享
const shareBtns2 = await page.$$('.report-card .card-head .btn')
await shareBtns2[2].click()
await page.waitForSelector('.share-preview img', { timeout: 15000 })
await new Promise((r) => setTimeout(r, 600))
await page.screenshot({ path: `${SHOTS}/07-share-chart.png` })
await page.click('.modal .close-x')

// ---------- 真实 zip 上传 ----------
await page.click('.site-header .btn-ghost.btn-sm')
await page.waitForFunction(() => location.hash === '#/', { timeout: 8000 })
await new Promise((r) => setTimeout(r, 500))
const input = await page.$('.upload-zone input[type=file]')
await input.uploadFile('test/fixtures/export-sample.zip')
await page.waitForFunction(() => location.hash === '#/report', { timeout: 30000 })
await page.waitForFunction(() => document.querySelectorAll('.report-card').length === 5, { timeout: 15000 })
await new Promise((r) => setTimeout(r, 1200))
const zipText = await page.evaluate(() => document.body.innerText)
ok('zip 上传生成报告', zipText.includes('年度人格') && !zipText.includes('NaN'))
ok('zip 报告含忽略提示', zipText.includes('已忽略'))
await page.screenshot({ path: `${SHOTS}/08-zip-report.png` })

// ---------- 错误路径 ----------
await page.click('.site-header .btn-ghost.btn-sm')
await page.waitForFunction(() => location.hash === '#/', { timeout: 8000 })
fs.writeFileSync('.tmp-fake.zip', 'this is definitely not a zip file')
const input2 = await page.$('.upload-zone input[type=file]')
await input2.uploadFile('.tmp-fake.zip')
await page.waitForSelector('.error-box', { timeout: 15000 })
const errText = await page.$eval('.error-box', (e) => e.textContent)
ok('非导出文件报错', errText.includes('不是 Apple 健康的导出文件'), errText.slice(0, 40))
await page.screenshot({ path: `${SHOTS}/09-error.png` })

// ---------- 移动端 ----------
const mob = await newPage(375, 812)
await mob.goto('http://localhost:5173/', { waitUntil: 'networkidle0' })
await new Promise((r) => setTimeout(r, 700))
ok('移动端首页无横向溢出', (await overflow(mob)) <= 0, `delta=${await overflow(mob)}`)
await mob.screenshot({ path: `${SHOTS}/10-home-mobile.png` })
await mob.click('.demo-link')
await mob.waitForFunction(() => location.hash === '#/report', { timeout: 30000 })
await mob.waitForFunction(() => document.querySelectorAll('.report-card').length === 5, { timeout: 15000 })
await new Promise((r) => setTimeout(r, 1500))
ok('移动端报告无横向溢出', (await overflow(mob)) <= 0, `delta=${await overflow(mob)}`)
await mob.screenshot({ path: `${SHOTS}/11-report-mobile.png` })
await mob.evaluate(() => window.scrollTo(0, document.body.scrollHeight * 0.5))
await new Promise((r) => setTimeout(r, 800))
await mob.screenshot({ path: `${SHOTS}/12-report-mobile-mid.png` })

// 移动端分享
await mob.evaluate(() => window.scrollTo(0, 0))
await new Promise((r) => setTimeout(r, 400))
const mShare = await mob.$$('.report-card .card-head .btn')
await mShare[0].click()
await mob.waitForSelector('.share-preview img', { timeout: 15000 })
ok('移动端分享弹窗', true)
await mob.screenshot({ path: `${SHOTS}/13-share-mobile.png` })

ok('无运行时错误', errors.length === 0, errors.slice(0, 5).join(' | '))

await browser.close()
console.log(results.join('\n'))