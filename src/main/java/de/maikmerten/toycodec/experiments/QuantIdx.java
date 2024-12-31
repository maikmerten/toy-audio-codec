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

package de.maikmerten.toycodec.experiments;

public class QuantIdx {

    public void packQuantIdx(int[] quantIdx, byte[] byteBuf) {
        int bitbuf = 0;
        int bitcount = 0;
        int byteIdx = 0;

        for (int qidx : quantIdx) {
            bitbuf <<= 6;
            bitbuf |= qidx & 0x3F;
            bitcount += 6;
            while (bitcount >= 8) {
                bitcount -= 8;
                byteBuf[byteIdx++] = (byte) ((bitbuf >> bitcount) & 0xFF);
            }
        }
    }

    public void unpackQuantIdx(byte[] quantIdxBytes, int[] quantIdx) {
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

    private int[] generateQuantizers() {
        int[] quantizers = new int[63];

        boolean exponential = true;

        if (exponential) {
            double step = 9f / (quantizers.length - 1);
            for (int i = 0; i < quantizers.length; i++) {
                int q = (int) Math.round((Math.pow(2, i * step)));
                if (i > 0 && q <= quantizers[i - 1]) {
                    q = quantizers[i - 1] + 1;
                }
                quantizers[i] = q;
            }
        } else {
            for(int i = 1; i <= 16; i++) {
                quantizers[i - 1] = i;
            }
            int floor = 16;
            double step = (512.0 - 16) / (63 - 16);
            for(int i = 17; i <= quantizers.length; i++) {
                quantizers[i - 1] = (int) Math.round(((i - 16) * step) + floor);
            }
        }
        return quantizers;
    }

    public static void main(String[] args) {

        QuantIdx q = new QuantIdx();

        int[] qidx = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 63 };
        byte[] bytebuf = new byte[12];

        q.packQuantIdx(qidx, bytebuf);
        q.unpackQuantIdx(bytebuf, qidx);

        for (int i : qidx) {
            System.out.println(i);
        }

        System.out.println("quantizers:");
        for (int i : q.generateQuantizers()) {
            System.out.println(i);
        }

    }
}
