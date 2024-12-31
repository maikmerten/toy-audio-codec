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

import de.maikmerten.toycodec.coding.Bands;
import de.maikmerten.toycodec.coding.ZigZag;
import de.maikmerten.toycodec.coding.huffman.HuffCoder;
import de.maikmerten.toycodec.coding.huffman.HuffTables;
import de.maikmerten.toycodec.coding.huffman.Huffman;
import de.maikmerten.toycodec.io.BitOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;


public class CoeffWriter {

    final OutputStream os;
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final HuffCoder huffCoder = new HuffCoder(HuffTables.COEFFS);
    final BitOutputStream writer = new BitOutputStream(baos);

    private byte[][][] coeffByteBuf = null;

    public CoeffWriter(File f) {
        try {
            this.os = new FileOutputStream(f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CoeffWriter(OutputStream os) {
        this.os = os;
    }

    public OutputStream getOutputStream() {
        return os;
    }

    public boolean checkCoeffsInRange(int[] coeffs) {
        for (int coeff : coeffs) {
            coeff = ZigZag.encodeZigZag(coeff);
            if ((coeff & 0xFFFF0000) != 0) {
                return false;
            }
        }
        return true;
    }

    public int checkCoeffsInRange(int[] coeffs, Bands bands) {
        for (int i = 0; i < coeffs.length; i++) {
            int coeff = coeffs[i];
            coeff = ZigZag.encodeZigZag(coeff);
            if ((coeff & 0xFFFF0000) != 0) {
                return bands.getBandMap()[i];
            }
        }
        return -1;
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

    public long estimateCoeffBits(int[] coeffs) {
        byte[] buf = new byte[coeffs.length];

        huffCoder.setContext(0);
        int lastidx = packCoeffsSingleByte(coeffs, buf, 0, 0);
        long bits = huffCoder.estimateBits(buf, 0, lastidx);

        huffCoder.setContext(1);
        lastidx = packCoeffsSingleByte(coeffs, buf, 8, 0);
        bits += huffCoder.estimateBits(buf, 0, lastidx);

        return bits;
    }

    private byte[][][] allocateCoeffByteBuffer(int channels, int width) {
        boolean allocate = coeffByteBuf == null || coeffByteBuf.length != channels || coeffByteBuf[0].length != 2
                || coeffByteBuf[0][0].length != width;
        if (allocate) {
            coeffByteBuf = new byte[channels][2][width];
        }
        return coeffByteBuf;
    }


    public byte[] encodeCoeffsHuffman(int[][] perChannelCoeffs) {
        try {
            int channels = perChannelCoeffs.length;
            int width = perChannelCoeffs[0].length;
            byte[][][] coeffBytes = allocateCoeffByteBuffer(channels, width);

            writer.flush();
            baos.reset();
            huffCoder.setWriter(writer);

            for (int c = 0; c < perChannelCoeffs.length; c++) {
                int[] coeffs = perChannelCoeffs[c];
                byte[][] byteBuf = coeffBytes[c];

                if (!checkCoeffsInRange(coeffs)) {
                    throw new RuntimeException("coeffs > 16 bits");
                }

                // pack LSB
                packCoeffsSingleByte(coeffs, byteBuf[0], 0, 0);

                // pack MSB
                packCoeffsSingleByte(coeffs, byteBuf[1], 8, 0);
            }

            huffCoder.writeByteSymbols(coeffBytes);

            writer.flush();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void writeData(byte[] data) {
        try {
            os.write(data);
            os.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            os.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void smoothenFrequencies(int[] frequencies) {
        if(frequencies[255] <= 0) {
            frequencies[255] = 1;
        }
        for(int idx = 254; idx >= 0; idx--) {
            if(frequencies[idx] <= frequencies[idx + 1]) {
                frequencies[idx] = frequencies[idx  + 1] + 1;
            }
        }
    }

    public void computeHuffmanLengths() {

        for (int ctx = 0; ctx < 2; ctx++) {
            System.out.println("Context " + ctx);
            int[] frequencies = huffCoder.getContextStats(ctx);

            int[] huffLengths = null;
            do {
                smoothenFrequencies(frequencies);
                huffLengths = Huffman.getHuffmanLengthsByFrequencies(frequencies);
                if(huffLengths[255] > 16) {
                    frequencies[255]++;
                }
            } while(huffLengths[255] > 16);


            System.out.print("{");
            for (int sym = 0; sym < frequencies.length; sym++) {
                int len = huffLengths[sym];
                System.out.print(len);
                if (sym < frequencies.length - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println("}");
        }

    }

}
