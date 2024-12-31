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

package de.maikmerten.toycodec.bitstream;

import de.maikmerten.toycodec.io.BitInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FrameHeader {

    public static final int BYTES = 1;
    
    public static final int SYNC = 0xF0;
    public static final int QUANTINFO_MASK = 0x0C;
    public static final int QUANTINFO_NONE = 0x00;
    public static final int QUANTINFO_SHARED = 0x08;
    public static final int QUANTINFO_PERCHANNEL = 0x0C;

    private static final int MIDSIDE = 0x02;
    
    private int quantInfo;
    private boolean midside;

    public FrameHeader(int quantInfo, boolean midside) {
        this.quantInfo = quantInfo;
        this.midside = midside;
    }

    public int getQuantInfo() {
        return this.quantInfo;
    }
    
    public boolean getMidSide() {
        return this.midside;
    }


    public void writeHeader(OutputStream os) {
        int headerbits = SYNC;
        headerbits |= quantInfo;
        headerbits |= midside ? MIDSIDE : 0;

        try {
            os.write(headerbits & 0xFF);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static FrameHeader fromInputStream(BitInputStream bis) {

        byte[] buf = new byte[1];
        bis.read(buf);
        int headerbits = buf[0] & 0xFF;

        if((headerbits & SYNC) != SYNC) {
            throw new RuntimeException("SYNC-mismatch for frame header");
        }

        int quantInfo = (headerbits & QUANTINFO_MASK);
        boolean midside = (headerbits & MIDSIDE) == MIDSIDE;
        return new FrameHeader(quantInfo, midside);
    }

}
