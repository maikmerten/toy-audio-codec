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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.maikmerten.toycodec.coding.ZigZag;
import de.maikmerten.toycodec.coding.huffman.HuffCoder;
import de.maikmerten.toycodec.coding.huffman.HuffTables;
import de.maikmerten.toycodec.io.BitOutputStream;

public class CoeffCoding {

    private List<int[]> coeffs = new ArrayList<>();
    private HuffCoder hc = new HuffCoder(HuffTables.COEFFS);
    private int n = 256;
    private ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private BitOutputStream writer = new BitOutputStream(baos);

    private void readCoeffs(InputStream is) throws Exception {
        byte[] buf = new byte[2 * n];

        long zero = 0;
        long positive = 0;
        long negative = 0;

        while (is.available() > 0) {
            int[] coeffBlock = new int[n];

            int read = is.read(buf);
            if (read > 0) {
                for (int i = 0; i < coeffBlock.length; i++) {
                    // read 16 bit integers, big endian
                    byte msb = buf[(i * 2) + 0];
                    byte lsb = buf[(i * 2) + 1];

                    short s = (short) (((msb & 0xFF) << 8) | (lsb & 0xFF));
                    coeffBlock[i] = s;

                    negative += s < 0 ? 1 : 0;
                    positive += s > 0 ? 1 : 0;
                    zero += s == 0 ? 1 : 0;
                }
                coeffs.add(coeffBlock);
            }
        }
        System.out.println("read " + coeffs.size() + " coeff blocks");
        System.out.println("positive: " + positive + "\tnegative: " + negative + "\tzero: " + zero);
    }

    private int getLastNonzeroByteIdx(byte[] coeffByteBuf) {
        int idx = 0;
        for (int i = 0; i < coeffByteBuf.length; i++) {
            if (coeffByteBuf[i] != 0) {
                idx = i;
            }
        }
        return idx;
    }

    private int packCoeffsLsbMsb(int[] coeffs, byte[] coeffByteBuf) {
        int msbOffset = coeffByteBuf.length / 2;
        for (int i = 0; i < coeffs.length; i++) {
            int coeff = ZigZag.encodeZigZag(coeffs[i]);
            coeffByteBuf[i] = (byte) (coeff & 0xFF);
            coeffByteBuf[i + msbOffset] = (byte) ((coeff >> 8) & 0xFF);
        }
        return getLastNonzeroByteIdx(coeffByteBuf);
    }

    private int packCoeffsNatural(int[] coeffs, byte[] coeffByteBuf) {
        for (int i = 0; i < coeffs.length; i++) {
            int coeff = ZigZag.encodeZigZag(coeffs[i]);
            coeffByteBuf[i * 2] = (byte) (coeff & 0xFF);
            coeffByteBuf[(i * 2) + 1] = (byte) ((coeff >> 8) & 0xFF);
        }
        return getLastNonzeroByteIdx(coeffByteBuf);
    }

    private int packCoeffsSingleByte(int[] coeffs, byte[] coeffByteBuf, int shift, int offset) {
        int lastNonZero = offset;
        for (int i = 0; i < coeffs.length; i++) {
            int coeff = ZigZag.encodeZigZag(coeffs[i]);
            byte b = (byte) ((coeff >> shift) & 0xFF);
            coeffByteBuf[i + offset] = b;
            if (b != 0) {
                lastNonZero = i + offset;
            }
        }
        return lastNonZero;
    }

    private byte[] huffEncode(byte[] buf, int firstidx, int lastidx) {
        writer.flush();
        baos.reset();
        hc.setWriter(writer);

        for (int i = firstidx; i < buf.length && i <= lastidx; i++) {
            byte b = buf[i];
            int sym = b & 0xFF;
            hc.writeSymbol(sym);
        }
        hc.writeSymbol(256); // STOP
        writer.flush();
        return baos.toByteArray();
    }

    private void experiment1() {
        byte[] coeffByteBuf = new byte[2 * n];
        long bits = 0;
        long bytes = 0;
        for (int[] coeffBlock : coeffs) {
            int lastidx = packCoeffsLsbMsb(coeffBlock, coeffByteBuf);
            bits += hc.estimateBits(coeffByteBuf, 0, lastidx);

            byte[] huffBytes = huffEncode(coeffByteBuf, 0, lastidx);
            bytes += huffBytes.length;
        }
        System.out.println("experiment 1: " + bits + " bits\tbytes: " + bytes + "\t (LsbMsb)");
    }

    private void experiment2() {
        byte[] coeffByteBuf = new byte[2 * n];
        long bits = 0;
        long bytes = 0;
        for (int[] coeffBlock : coeffs) {
            int lastidx = packCoeffsNatural(coeffBlock, coeffByteBuf);
            long addbits = hc.estimateBits(coeffByteBuf, 0, lastidx);
            bits += addbits;

            byte[] huffBytes = huffEncode(coeffByteBuf, 0, lastidx);
            bytes += huffBytes.length;
        }
        System.out.println("experiment 2: " + bits + " bits\tbytes: " + bytes + "\t (natural)");
    }

    private void experiment3() {
        byte[] coeffByteBuf = new byte[n * 2];

        long bits = 0;
        long bytes = 0;
        for (int[] coeffBlock : coeffs) {
            int lastidx = packCoeffsSingleByte(coeffBlock, coeffByteBuf, 0, 0);
            long addbits = hc.estimateBits(coeffByteBuf, 0, lastidx);
            bits += addbits;

            byte[] huffBytes = huffEncode(coeffByteBuf, 0, lastidx);
            bytes += huffBytes.length;

            lastidx = packCoeffsSingleByte(coeffBlock, coeffByteBuf, 8, n);
            addbits = hc.estimateBits(coeffByteBuf, n, lastidx);
            bits += addbits;

            huffBytes = huffEncode(coeffByteBuf, n, lastidx);
            bytes += huffBytes.length;
        }
        System.out.println("experiment 3: " + bits + " bits\tbytes: " + bytes + "\t (separate LSB, then MSB)");
    }


    public static void main(String[] args) throws Exception {
        FileInputStream fis = new FileInputStream(new File("/tmp/coeffs.bin"));

        CoeffCoding cc = new CoeffCoding();
        cc.readCoeffs(fis);
        cc.experiment1();
        cc.experiment2();
        cc.experiment3();

    }

}
