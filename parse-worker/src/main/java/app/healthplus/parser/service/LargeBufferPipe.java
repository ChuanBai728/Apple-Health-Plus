package app.healthplus.parser.service;

import java.io.*;
import java.util.concurrent.locks.*;

/**
 * 基于环形缓冲区（Ring Buffer）的高吞吐内存管道。
 * <p>
 * 设计目的：替代临时文件方案，在写线程（StAX 解析器）和读线程（PostgreSQL CopyManager）
 * 之间建立零拷贝的数据通道，消除磁盘 I/O 瓶颈。
 * <p>
 * 实现要点：
 * <ul>
 *   <li>使用 {@link ReentrantLock} + {@link Condition} 实现有界阻塞队列语义</li>
 *   <li>环形缓冲区默认 32MB，适合批量健康数据的吞吐需求</li>
 *   <li>写满时写线程阻塞等待（notFull），读空时读线程阻塞等待（notEmpty）</li>
 *   <li>写端 close() 后读端返回 -1（EOF），实现生产者-消费者优雅终止</li>
 * </ul>
 *
 * @see app.healthplus.parser.core.HighThroughputParsePipeline
 */
public class LargeBufferPipe {

    /** 底层环形缓冲区 */
    private final byte[] buf;
    /** 读指针、写指针、当前可用字节数 */
    private int readPos, writePos, available;
    /** 写端是否已关闭，用于通知读端不再有新数据 */
    private boolean writerClosed;
    private final Lock lock = new ReentrantLock();
    /** 缓冲区未满时唤醒写线程 */
    private final Condition notFull = lock.newCondition();
    /** 缓冲区非空时唤醒读线程 */
    private final Condition notEmpty = lock.newCondition();

    /** 写端 OutputStream，外部写入数据的入口 */
    public final OutputStream out;
    /** 读端 InputStream，供 CopyManager 等消费者读取 */
    public final InputStream in;

    /**
     * 创建指定缓冲区大小的内存管道。
     *
     * @param bufferSize 缓冲区字节数，建议设为数据批量大小的数倍以减少阻塞
     */
    public LargeBufferPipe(int bufferSize) {
        this.buf = new byte[bufferSize];
        this.out = new PipeOutputStream();
        this.in  = new PipeInputStream();
    }

    /** 默认构造：32MB 缓冲区，适合百万级健康记录的流式传输 */
    public LargeBufferPipe() { this(32 * 1024 * 1024); } // 32MB default

    /**
     * 写端实现：将数据写入环形缓冲区。
     * <p>
     * 当缓冲区满时阻塞等待消费者读取腾出空间；
     * 当缓冲区未满时计算可写入的连续空间（需处理环形回绕），分块写入。
     */
    private class PipeOutputStream extends OutputStream {
        public void write(int b) throws IOException { write(new byte[]{(byte)b}, 0, 1); }
        public void write(byte[] b, int off, int len) throws IOException {
            lock.lock();
            try {
                while (len > 0) {
                    // 缓冲区满，等待消费者读取释放空间
                    while (available >= buf.length && !writerClosed)
                        notFull.await();
                    if (writerClosed) throw new IOException("Pipe broken");
                    // 计算本次可写入的字节数：取剩余数据、可用空间、到缓冲区末尾的距离三者最小值
                    int space = buf.length - available;
                    int chunk = Math.min(len, Math.min(space, buf.length - writePos));
                    System.arraycopy(b, off, buf, writePos, chunk);
                    writePos = (writePos + chunk) % buf.length; // 环形回绕
                    available += chunk;
                    off += chunk; len -= chunk;
                    notEmpty.signalAll(); // 唤醒等待数据的读线程
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            } finally { lock.unlock(); }
        }
        /** 关闭写端：设置标志并唤醒所有等待线程，使读端能检测到 EOF */
        public void close() {
            lock.lock();
            try { writerClosed = true; notEmpty.signalAll(); notFull.signalAll(); }
            finally { lock.unlock(); }
        }
    }

    /**
     * 读端实现：从环形缓冲区读取数据。
     * <p>
     * 当缓冲区为空时阻塞等待写线程生产数据；
     * 写端关闭且缓冲区为空时返回 -1（EOF）。
     */
    private class PipeInputStream extends InputStream {
        public int read() throws IOException {
            byte[] b = new byte[1]; int n = read(b, 0, 1); return n < 0 ? -1 : b[0] & 0xff;
        }
        public int read(byte[] b, int off, int len) throws IOException {
            lock.lock();
            try {
                while (available == 0) {
                    if (writerClosed) return -1; // 写端已关闭且无数据，返回 EOF
                    notEmpty.await();
                    // 唤醒后再次检查：可能写端在 await 期间关闭了
                    if (writerClosed && available == 0) return -1;
                }
                // 计算本次可读取的字节数：取请求量、可用量、到缓冲区末尾的距离三者最小值
                int chunk = Math.min(len, Math.min(available, buf.length - readPos));
                System.arraycopy(buf, readPos, b, off, chunk);
                readPos = (readPos + chunk) % buf.length; // 环形回绕
                available -= chunk;
                notFull.signalAll(); // 唤醒等待空间的写线程
                return chunk;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            } finally { lock.unlock(); }
        }
    }
}
