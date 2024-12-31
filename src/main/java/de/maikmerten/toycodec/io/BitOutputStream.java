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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;


public class BitOutputStream extends OutputStream {

    private final OutputStream os;
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);

    private int bits = 0;
    private int bitbuf = 0;
    private boolean closeOutputStream = true;

    public BitOutputStream(OutputStream os) {
        this.os = os;
    }

    public void setCloseOutputStream(boolean closeOutputStream) {
        this.closeOutputStream = closeOutputStream;
    }

    public void flushBits() {
        if (bits < 1) {
            return;
        }

        // pad bits to full byte
        while (bits < 8) {
            bitbuf <<= 1;
            bits++;
        }

        write((byte) (bitbuf & 0xFF));
        bits = 0;
        bitbuf = 0;
    }
    
    @Override
    public void flush() {
        flushBits();
        
        if(baos.size() > 0) {
            try {
                os.write(baos.toByteArray());
                os.flush();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            baos.reset();
        }
    }

    public void writeBit(int b) {
        if (bits >= 8) {
            flushBits();
        }

        bitbuf <<= 1;
        bitbuf |= (b & 0x1);

        bits++;
    }

    public void writeBit(int[] bits) {
        for (int b : bits) {
            writeBit(b);
        }
    }

    public void write(byte b) {
        baos.write(b);

        if (baos.size() >= 512) {
            try {
                os.write(baos.toByteArray());
                baos.reset();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public void write(byte[] bytes) {
        for (byte b : bytes) {
            write(b);
        }
    }
    
    @Override
    public void write(int i) {
        write((byte)(i & 0xFF));
    }

    
    public void write16(int s) {
        write(s & 0xFF);
        write((s >> 8) & 0xFF);
    }

    public void write32(int s) {
        write(s & 0xFF);
        write((s >> 8) & 0xFF);
        write((s >> 16) & 0xFF);
        write((s >> 24) & 0xFF);
    }

    public void write64(long l) {
        write((int)(l & 0xFF));
        write((int)((l >> 8) & 0xFF));
        write((int)((l >> 16) & 0xFF));
        write((int)((l >> 24) & 0xFF));
        write((int)((l >> 32) & 0xFF));
        write((int)((l >> 40) & 0xFF));
        write((int)((l >> 48) & 0xFF));
        write((int)((l >> 56) & 0xFF));
    }


    public void close() {
        try {
            flush();
            
            if(closeOutputStream) {
                os.close();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}
