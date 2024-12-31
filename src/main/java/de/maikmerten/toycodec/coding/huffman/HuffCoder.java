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

package de.maikmerten.toycodec.coding.huffman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.maikmerten.toycodec.io.BitInputStream;
import de.maikmerten.toycodec.io.BitOutputStream;
import java.util.HashMap;
import java.util.Map;

public class HuffCoder {

    private BitOutputStream writer;
    private BitInputStream bitInput;
    private int currentCtx;
    private Context[] contexts;

    private class Context {
        private int[] codetable;
        private final Map<Integer, Integer> codemap = new HashMap<>();
        private int stopSymbol;
        private int[] frequencies;
    }

    public HuffCoder(int[][] lengths) {
        contexts = new Context[lengths.length];
        for (int ctx = 0; ctx < lengths.length; ctx++) {
            List<Integer> lenList = new ArrayList<>();
            contexts[ctx] = new Context();
            for (int len : lengths[ctx]) {
                lenList.add(len);
            }
            buildCanonicalCodeTable(contexts[ctx], lenList);
        }
    }

    public void setWriter(BitOutputStream w) {
        this.writer = w;
    }

    public void setBitInput(BitInputStream r) {
        this.bitInput = r;
    }

    public void setContext(int currentCtx) {
        if (currentCtx < 0 || currentCtx >= contexts.length) {
            throw new IllegalArgumentException("invalid context: " + currentCtx);
        }
        this.currentCtx = currentCtx;
    }

    public int getContextsSize() {
        return this.contexts.length;
    }

    public int[] getContextStats(int context) {
        return contexts[context].frequencies;
    }

    public int[] getSymbolLengths() {
        Context ctx = contexts[currentCtx];

        int[] lengths = new int[ctx.codetable.length];
        for (int sym = 0; sym < ctx.codetable.length; sym++) {
            lengths[sym] = (ctx.codetable[sym] >> 24) & 0xFF;
        }
        return lengths;
    }

    public long estimateBits(byte[] bytes, int start, int lastIdx) {
        Context ctx = contexts[currentCtx];

        long bits = 0;
        for (int i = start; i < bytes.length && i <= lastIdx; i++) {
            int sym = bytes[i] & 0xFF;
            bits += (ctx.codetable[sym] >> 24) & 0xFF;
        }
        bits += (ctx.codetable[ctx.stopSymbol] >> 24) & 0xFF; // STOP symbol

        return bits;
    }

    private void buildCanonicalCodeTable(Context ctx, List<Integer> lengths) {

        ctx.codetable = new int[lengths.size()];
        ctx.frequencies = new int[lengths.size()];
        ctx.stopSymbol = lengths.size() - 1;

        // combine length and symbol, order first by length, then symbol order
        List<Integer> symbolsAndLengths = new ArrayList<>(lengths.size());
        for (int sym = 0; sym < lengths.size(); sym++) {
            int len = lengths.get(sym);
            symbolsAndLengths.add((len << 16) | sym);
        }
        Collections.sort(symbolsAndLengths);

        int currlen = 0;
        int code = 0;
        for (int symbolAndLength : symbolsAndLengths) {
            int sym = symbolAndLength & 0xFFFF;
            int len = (symbolAndLength >> 16) & 0xFFFF;

            code <<= (len - currlen);
            currlen = len;
            ctx.codetable[sym] = (len << 24) | code;
            code++;
        }

        // build code map (code to symbol)
        ctx.codemap.clear();
        for (int sym = 0; sym < ctx.codetable.length; sym++) {
            ctx.codemap.put(ctx.codetable[sym], sym);
        }

    }

    public void printCodeTable() {
        Context ctx = contexts[currentCtx];

        for (int sym = 0; sym < ctx.codetable.length; sym++) {
            int code = ctx.codetable[sym];
            int len = (code >> 24) & 0xFF;
            code &= 0xFFFFFF;

            System.out.print(sym + "\t");
            int bitmask = (1 << len - 1);
            while (bitmask > 0) {
                System.out.print((code & bitmask) > 0 ? "1" : "0");
                bitmask >>= 1;
            }
            System.out.println();
        }
    }

    public int writeSymbol(int sym) {
        Context ctx = contexts[currentCtx];
        ctx.frequencies[sym]++;
        int code = ctx.codetable[sym];
        int len = (code >> 24) & 0xFF;
        code &= 0xFFFFFF;

        int bitmask = (1 << len - 1);
        while (bitmask > 0) {
            writer.writeBit((code & bitmask) > 0 ? 1 : 0);
            bitmask >>= 1;
        }
        return len;
    }

    public int readSymbol() {
        Context ctx = contexts[currentCtx];
        int bits = bitInput.readBit();
        int len = 1;
        while (len < 24) {
            int code = (len << 24) | bits;
            Integer sym = ctx.codemap.get(code);
            if (sym != null) {
                return sym;
            }
            bits = (bits << 1) | bitInput.readBit();
            len++;
        }
        throw new RuntimeException("could not decode byte from Huffman bitstream");
    }

    public int writeByteSymbols(byte[] symbols) {
        Context ctx = this.contexts[currentCtx];
        int bits = 0;
        int lastidx = 0;
        for (int i = 0; i < symbols.length; i++) {
            if (symbols[i] != 0) {
                lastidx = i;
            }
        }
        for (int i = 0; i < symbols.length && i <= lastidx; i++) {
            bits += writeSymbol(symbols[i] & 0xFF);
        }

        if (lastidx < symbols.length - 1) {
            bits += writeSymbol(ctx.stopSymbol); // STOP-symbol
        }
        return bits;
    }

    public int writeByteSymbols(byte[][] symbols2d) {
        int bits = 0;
        for (int ctx = 0; ctx < symbols2d.length; ctx++) {
            setContext(ctx);
            byte[] symbols = symbols2d[ctx];
            bits += writeByteSymbols(symbols);
        }
        return bits;
    }

    public int writeByteSymbols(byte[][][] symbols3d) {
        int bits = 0;
        for (byte[][] symbols2d : symbols3d) {
            bits += writeByteSymbols(symbols2d);
        }
        return bits;
    }

    public int readByteSymbols(byte[] array, boolean startOnByteBoundary) {
        Context ctx = this.contexts[currentCtx];
        if (startOnByteBoundary) {
            bitInput.resetBits();
        }

        int bytes = 0;
        int i = 0;
        while (i < array.length) {
            int sym = readSymbol();
            if (sym < ctx.stopSymbol) {
                array[i] = (byte) (sym & 0xFF);
                bytes++;
            } else { // STOP-symbol encountered
                break;
            }
            i++;
        }
        for (; i < array.length; i++) {
            array[i] = 0;
        }
        return bytes;
    }

    public int readByteSymbols(byte[][] array2d, boolean startOnByteBoundary) {
        int bytes = 0;

        for (int ctx = 0; ctx < array2d.length; ctx++) {
            setContext(ctx);
            byte[] array = array2d[ctx];
            bytes += readByteSymbols(array, startOnByteBoundary);
            startOnByteBoundary = false;
        }
        return bytes;
    }

    public int readByteSymbols(byte[][][] array3d, boolean startOnByteBoundary) {
        int bytes = 0;
        for (byte[][] array2d : array3d) {
            bytes += readByteSymbols(array2d, startOnByteBoundary);
            startOnByteBoundary = false;
        }
        return bytes;
    }

    public static void main(String[] args) {
        HuffCoder hc = new HuffCoder(HuffTables.COEFFS);
        hc.printCodeTable();

    }

}
