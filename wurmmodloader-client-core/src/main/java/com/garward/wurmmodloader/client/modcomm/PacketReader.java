package com.garward.wurmmodloader.client.modcomm;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class PacketReader extends DataInputStream {
    private static class ByteBufferBackedInputStream extends InputStream {
        private final ByteBuffer buf;

        private ByteBufferBackedInputStream(ByteBuffer buf) {
            this.buf = buf;
        }

        public int read() {
            if (buf.hasRemaining()) {
                return buf.get() & 0xFF;
            }
            return -1;
        }

        public int read(byte[] bytes, int off, int len) {
            if (buf.hasRemaining()) {
                len = Math.min(len, buf.remaining());
                buf.get(bytes, off, len);
                return len;
            }
            return -1;
        }
    }

    public PacketReader(ByteBuffer buffer) {
        super(new ByteBufferBackedInputStream(buffer));
    }
}
