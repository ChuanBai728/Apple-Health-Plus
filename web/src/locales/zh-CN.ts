export const zhCN = {
  brand: 'HealthPlus',
  tagline: '身体年报',
  hero: {
    eyebrow: '健康数据 · 本地解析 · 身体年报',
    titleA: '你的身体，',
    titleB: '这一年走了多远',
    sub: '把 1GB 的健康导出，变成一份你自己都没见过的身体年报：年度人格、小睡档案、训练生涯、爬楼纪录与里程成就。',
    privacy: '文件只在你的浏览器里解析，不会上传',
    pick: '选择导出文件 export.zip',
    dropHint: '支持 export.zip / 导出.xml · 也可以直接拖进来',
    demo: '没有导出文件？先看一份示例年报',
  },
  steps: [
    { title: '打开 iPhone 上的「健康」', desc: '点右上角的头像' },
    { title: '滑到最底部，点「导出所有健康数据」', desc: '生成需要几分钟，记录越多越久' },
    { title: '存到「文件」，再回来选择它', desc: '建议先存到「我的 iPhone」，避免 iCloud 未下载完' },
  ],
  faq: [
    {
      q: '我的数据会去哪里？',
      a: '哪里也不去。整个解析过程都在你的浏览器里完成，没有服务器、没有数据库、没有网络请求。关掉页面，一切随之消失。',
    },
    {
      q: '导出文件很大，会卡吗？',
      a: '不会一次性读进内存。解析在 Web Worker 里边解压边扫描，1GB 级别的导出也能处理，页面保持流畅，随时可以取消。',
    },
    {
      q: '为什么有些模块没有数据？',
      a: '活动圆环、睡眠阶段等需要 Apple Watch。只有 iPhone 数据时，这些模块会显示中性提示，其余模块照常生成。',
    },
    {
      q: '和健康 App 的年度总结有什么不同？',
      a: '健康 App 给你看圆环和步数的罗列；这里做的是纪录、连续性、训练生涯、小睡这类重新组织过的角度——还有可以保存分享的年报卡片。',
    },
  ],
  parse: {
    zip: '正在读取压缩包',
    inflate: '正在解压并扫描',
    scan: '正在扫描记录',
    cancel: '取消',
    hint: '导出包可能有几百 MB，解析期间请保持页面打开。',
    elapsed: '已用',
    eta: '预计剩余',
  },
  report: {
    share: '生成分享图',
    restart: '换个文件',
    ignored: (n: number) => `已忽略 ${n} 条异常记录`,
    empty: '这段时间没有相关记录。',
  },
  modules: {
    persona: '年度人格',
    naps: '小睡档案',
    training: '训练生涯',
    stairs: '向上纪录',
    distance: '里程与圆环',
  },
  share: {
    title: '生成分享图',
    vertical: '竖版 9:16',
    square: '方形 1:1',
    save: '保存图片',
    copy: '复制图片',
    copied: '已复制',
    hint: '图片在你的浏览器里生成，不会上传。',
  },
  footer: {
    disclaimer: 'Apple 健康是 Apple Inc. 的商标。',
    local: '健康数据在本机解析 · 不上传 · 不留存',
  },
  errors: {
    notExport: '这不是 Apple 健康的导出文件。请在「健康 → 头像 → 导出所有健康数据」里生成导出.zip 再试。',
    cancelled: '已取消解析。',
    generic: '解析失败，请确认文件完整后重试。',
  },
}