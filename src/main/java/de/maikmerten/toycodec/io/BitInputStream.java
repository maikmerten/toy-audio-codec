/*
MIT License

Copyright (c) 2024 Maik Merten

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package de.maikmerten.toycodec.io;

import java.io.IOException;
import java.io.InputStream;


public class BitInputStream extends InputStream {
    
    private final InputStream is;
    private int bits = 0;
    private int bitbuf = 0;
    private boolean empty = false;
    private byte[] byteBuf = new byte[1024];
    private byte[] singleByte = new byte[1];
    private int byteBufPos = 0;
    private int byteBufBytes = 0;
    
    public BitInputStream(InputStream is) {
        this.is = is;
    }

    private int readSingleByte(byte[] out) {
        if(byteBufPos >= byteBufBytes) {
            // no fresh bytes in byte buffer, read from InputStream
            try {
                byteBufBytes = is.read(byteBuf);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            byteBufPos = 0;
            if(byteBufBytes <= 0) {
                return -1;
            }
        }
        out[0] = byteBuf[byteBufPos++];
        return 1;
    }

    private void fillBitBuf() {
        bits = 0;
        int read = readSingleByte(singleByte);
        if(read < 1) {
            empty = true;
            bits = 0;
        } else {
            bitbuf = singleByte[0] & 0xFF;
            bits = 8;
        }
    }
   

    public int readBit() {
        if(bits < 1) {
            fillBitBuf();
        }
        int bit = ((bitbuf & 0x80) >> 7);
        bitbuf <<= 1;
        bits--;
        return bit;
    }

    public int readByte() {
        int read = readSingleByte(singleByte);
        if(read <= 0) {
            return -1;
        } else {
            return singleByte[0] & 0xFF;
        }
    }
    
    @Override
    public int read(byte[] buf) {
        int total = 0;
        for(int i = 0; i < buf.length; i++) {
            int read = readByte();
            if(read < 0) {
                if(i == 0) {
                    return -1;
                } else {
                    return total;
                }
            }
            buf[i] = (byte)(read & 0xFF);
            total++;
        }

        return total;
    }
    
    public int read16() {
        int byte1 = readByte();
        int byte2 = readByte();
        return (byte2 << 8) | byte1;
    }
    
    public int read32() {
        int byte1 = readByte();
        int byte2 = readByte();
        int byte3 = readByte();
        int byte4 = readByte();
        return (byte4 << 24) | (byte3 << 16) | (byte2 << 8) | byte1;
    }

    public long read64() {
        long byte1 = readByte();
        long byte2 = readByte();
        long byte3 = readByte();
        long byte4 = readByte();
        long byte5 = readByte();
        long byte6 = readByte();
        long byte7 = readByte();
        long byte8 = readByte();
        return (byte8 << 56) | (byte7 << 48) | (byte6 << 40) | (byte5 << 32) | (byte4 << 24) | (byte3 << 16) | (byte2 << 8) | byte1;
    }
    
    public boolean hasBits() {
        if(bits < 1) {
            fillBitBuf();
        }
        return !empty;
    }
    
    @Override
    public int available() {
        try {
            return is.available() + (byteBufBytes - byteBufPos);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    @Override
    public void close() {
        try {
            is.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void resetBits() {
        bits = 0;
        bitbuf = 0;
        empty = false;
    }

    @Override
    public synchronized void reset() {
        resetBits();
        byteBufPos = 0;
        byteBufBytes = 0;
        try {
            is.reset();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public int read() throws IOException {
        return readByte();
    }
    
}
