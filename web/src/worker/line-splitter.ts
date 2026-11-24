/** 跨块切行：块尾不完整行留到下一块，兼容 \r\n */
export class LineSplitter {
  private rest = ''

  push(text: string, emit: (line: string) => void): void {
    const buf = this.rest + text
    let start = 0
    for (;;) {
      const i = buf.indexOf('\n', start)
      if (i < 0) break
      let line = buf.slice(start, i)
      if (line.endsWith('\r')) line = line.slice(0, -1)
      emit(line)
      start = i + 1
    }
    this.rest = buf.slice(start)
  }

  flush(emit: (line: string) => void): void {
    if (this.rest.length > 0) {
      let line = this.rest
      if (line.endsWith('\r')) line = line.slice(0, -1)
      emit(line)
      this.rest = ''
    }
  }
}