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
import de.maikmerten.toycodec.io.BitOutputStream;

import java.io.OutputStream;

public class QuantInfo {

    private int[] quantIndexes;

    public static final int BYTES = 12;
    
    public QuantInfo(int[] quantIndexes) {
        this.quantIndexes = quantIndexes;
    }

    public int[] getQuantIndexes() {
        return quantIndexes;
    }

    private void packQuantIdxData(int[] quantIdx, byte[] byteBuf) {
        if (quantIdx.length != 16) {
            throw new IllegalArgumentException("expected length of quantIdx: 16");
        }
        if (byteBuf.length != 12) {
            throw new IllegalArgumentException("expected byte buf size for quantIdx data: 12");
        }

        int bitbuf = 0;
        int bitcount = 0;
        int byteIdx = 0;

        for (int qidx : quantIdx) {
            if (qidx < 0 || qidx > 63) {
                throw new RuntimeException("qidx must range from 0 to 63");
            }
            bitbuf <<= 6;
            bitbuf |= qidx & 0x3F;
            bitcount += 6;
            while (bitcount >= 8) {
                bitcount -= 8;
                byteBuf[byteIdx++] = (byte) ((bitbuf >> bitcount) & 0xFF);
            }
        }
    }

    private static void unpackQuantIdx(byte[] quantIdxBytes, int[] quantIdx) {
        int bitbuf = 0;
        int bitcount = 0;
        int qidx = 0;

        for (byte b : quantIdxBytes) {
            bitbuf <<= 8;
            bitbuf |= b & 0xFF;
            bitcount += 8;
            while (bitcount >= 6) {
                bitcount -= 6;
                quantIdx[qidx++] = (bitbuf >> bitcount) & 0x3F;
            }
        }
    }

    public void writeHeader(OutputStream os) {
        BitOutputStream w = new BitOutputStream(os);

        byte[] quantIdxBytes = new byte[(quantIndexes.length * 6) / 8];
        packQuantIdxData(quantIndexes, quantIdxBytes);
        w.write(quantIdxBytes);

        w.setCloseOutputStream(false);
        w.close();
    }

    public static QuantInfo fromInputStream(BitInputStream bis, int bands) {

        byte[] quantIdxBytes = new byte[(bands * 6) / 8];
        int[] quantIndexes = new int[bands];
        
        int read = bis.read(quantIdxBytes);
        if(read != quantIdxBytes.length) {
            throw new RuntimeException("not enough quantIdxBytes read");
        }
        unpackQuantIdx(quantIdxBytes, quantIndexes);
        
        return new QuantInfo(quantIndexes);
    }

}
