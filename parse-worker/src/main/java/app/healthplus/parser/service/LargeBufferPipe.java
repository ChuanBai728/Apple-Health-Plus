package app.healthplus.parser.service;

import java.io.*;
import java.util.concurrent.locks.*;

/**
 * High-throughput pipe with a large ring buffer (32MB default).
 * Writer thread writes CSV rows, reader thread (CopyManager) reads and feeds PostgreSQL.
 * Eliminates the temp-file I/O step entirely.
 */
public class LargeBufferPipe {

    private final byte[] buf;
    private int readPos, writePos, available;
    private boolean writerClosed;
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public final OutputStream out;
    public final InputStream in;

    public LargeBufferPipe(int bufferSize) {
        this.buf = new byte[bufferSize];
        this.out = new PipeOutputStream();
        this.in  = new PipeInputStream();
    }

    public LargeBufferPipe() { this(32 * 1024 * 1024); } // 32MB default

    private class PipeOutputStream extends OutputStream {
        public void write(int b) throws IOException { write(new byte[]{(byte)b}, 0, 1); }
        public void write(byte[] b, int off, int len) throws IOException {
            lock.lock();
            try {
                while (len > 0) {
                    while (available >= buf.length && !writerClosed)
                        notFull.await();
                    if (writerClosed) throw new IOException("Pipe broken");
                    int space = buf.length - available;
                    int chunk = Math.min(len, Math.min(space, buf.length - writePos));
                    System.arraycopy(b, off, buf, writePos, chunk);
                    writePos = (writePos + chunk) % buf.length;
                    available += chunk;
                    off += chunk; len -= chunk;
                    notEmpty.signalAll();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            } finally { lock.unlock(); }
        }
        public void close() {
            lock.lock();
            try { writerClosed = true; notEmpty.signalAll(); notFull.signalAll(); }
            finally { lock.unlock(); }
        }
    }

    private class PipeInputStream extends InputStream {
        public int read() throws IOException {
            byte[] b = new byte[1]; int n = read(b, 0, 1); return n < 0 ? -1 : b[0] & 0xff;
        }
        public int read(byte[] b, int off, int len) throws IOException {
            lock.lock();
            try {
                while (available == 0) {
                    if (writerClosed) return -1;
                    notEmpty.await();
                    if (writerClosed && available == 0) return -1;
                }
                int chunk = Math.min(len, Math.min(available, buf.length - readPos));
                System.arraycopy(buf, readPos, b, off, chunk);
                readPos = (readPos + chunk) % buf.length;
                available -= chunk;
                notFull.signalAll();
                return chunk;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            } finally { lock.unlock(); }
        }
    }
}
