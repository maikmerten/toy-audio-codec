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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public class StreamHeader {

    private String fourCC = "TOY1";
    private int sampleRate;
    private int preRoll;
    private long totalSamples;
    private int channels;
    private int midSideChannels;
    private int width;
    private int bands;
    private int[] bandWidths;
    private int coeffScale;
    private int[] quantizers;
    private int[][] huffmanLengths;

    public StreamHeader(int sampleRate, int preRoll, long totalSamples, int channels, int lastMidSideChannel, int width, int bands, int[] bandWidths, int coeffScale, int[] quantizers, int[][] huffmanLengths) {
        if(((width / 16) * 16) != width) {
            throw new IllegalArgumentException("width needs to be multiples of 16");
        }

        this.sampleRate = sampleRate;
        this.channels = channels;
        this.midSideChannels = lastMidSideChannel;
        this.width = width;
        this.bands = bands;
        this.coeffScale = coeffScale;

        this.preRoll = preRoll;
        this.totalSamples = totalSamples;

        this.quantizers = new int[quantizers.length];
        System.arraycopy(quantizers, 0, this.quantizers, 0, quantizers.length);

        this.huffmanLengths = new int[huffmanLengths.length][];
        for(int i = 0; i < huffmanLengths.length; i++) {
            this.huffmanLengths[i] = new int[huffmanLengths[i].length];
            System.arraycopy(huffmanLengths[i], 0, this.huffmanLengths[i], 0, huffmanLengths[i].length);
        }

        this.bandWidths = new int[bandWidths.length];
        System.arraycopy(bandWidths, 0, this.bandWidths, 0, bandWidths.length);
    }

    public String getFourCC() {
        return this.fourCC;
    }

    public int getSampleRate() {
        return this.sampleRate;
    }

    public int getPreRoll() {
        return this.preRoll;
    }

    public long getTotalSamples() {
        return this.totalSamples;
    }

    public void setTotalSamples(long totalSamples) {
        this.totalSamples = totalSamples;
    }

    public int getChannels() {
        return this.channels;
    }

    public int getMidSideChannels() {
        return this.midSideChannels;
    }

    public int getWidth() {
        return this.width;
    }

    public int getBands() {
        return this.bands;
    }

    public int[] getBandWidths() {
        return this.bandWidths;
    }

    public int getCoeffScale() {
        return this.coeffScale;
    }

    public int[] getQuantizers() {
        return this.quantizers;
    }

    public int[][] getHuffmanLengths() {
        return this.huffmanLengths;
    }

    private byte[] packHuffmanLengths(int[][] lengths) {
        if(lengths.length != 2 || lengths[0].length != 257 || lengths[1].length != 257) {
            throw new IllegalArgumentException("unexpected size of Huffman length data");
        }
        byte[] packed = new byte[257];
        for(int i = 0; i < packed.length; i++) {
            int len0 = lengths[0][i];
            int len1 = lengths[1][i];
            if(len0 < 1 || len0 > 16 || len1 < 1 || len1 > 16) {
                throw new RuntimeException("Huffman code lengths illegal len0: " + len0 + " len1:" + len1);
            }
            packed[i] = (byte)(((len0 - 1) << 4) | (len1 - 1));
        }
        return packed;
    }

    private static int[][] unpackHuffmanLengths(byte[] lenData) {
        if(lenData.length != 257) {
            throw new IllegalArgumentException("expected size of Huffmann code length byte array is 257");
        }
        int[][] lengths = new int[2][257];
        for(int i = 0; i < lenData.length; i++) {
            lengths[0][i] = ((lenData[i] >> 4) & 0xF) + 1;
            lengths[1][i] = ((lenData[i]) & 0xF) + 1;
        }
        return lengths;
    }



    private byte[] getHeaderBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BitOutputStream bos = new BitOutputStream(baos);

        byte[] ccBytes = this.fourCC.getBytes(StandardCharsets.US_ASCII);
        bos.write(ccBytes);
        bos.write16(sampleRate / 25);
        bos.write16(preRoll);
        bos.write64(totalSamples);

        bos.write(channels - 1);
        bos.write(midSideChannels - 1);
        bos.write(width / 16);
        bos.write(bands - 1);

        // write band widths (in lines), last band does not need to be transmitted
        for(int i = 0; i < bandWidths.length - 1; i++) {
            bos.write16(bandWidths[i]);
        }

        bos.write16(coeffScale);

        for(int quantizer : quantizers) {
            bos.write16(quantizer);
        }

        bos.write(packHuffmanLengths(huffmanLengths));

        bos.flush();
        bos.close();
        return baos.toByteArray();
    }

    public void writeHeader(OutputStream os) {
        byte[] headerBytes = getHeaderBytes();
        try {
            os.write(headerBytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateHeader(File f) {
        try (RandomAccessFile raf = new RandomAccessFile(f, "rws")) {
            raf.seek(0);
            raf.write(getHeaderBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static StreamHeader fromInputStream(BitInputStream bis) {

        byte[] fourCCBytes = new byte[4];
        bis.read(fourCCBytes);
        String fourCC = new String(fourCCBytes);
        if(!fourCC.equals("TOY1")) {
            throw new RuntimeException("input stream is not a TOY1 stream");
        }

        int sampleRate = bis.read16() * 25;
        int preRoll = bis.read16();
        long totalSamples = bis.read64();

        int channels = bis.readByte() + 1;
        int midSideChannels = bis.readByte() + 1;
        int width = bis.readByte() * 16;
        int bands = bis.readByte() + 1;

        int[] bandWidths = new int[bands];
        int bandLines = 0;
        for(int i = 0; i < bandWidths.length - 1; i++) {
            bandWidths[i] = bis.read16();
            bandLines += bandWidths[i];
        }
        if(bandLines > width) {
            throw new RuntimeException("illegal band-widths in stream header");
        }
        bandWidths[bandWidths.length - 1] = width - bandLines;

        int coeffScale = bis.read16();

        int[] quantizers = new int[63];
        for(int i = 0; i < quantizers.length; i++) {
            quantizers[i] = bis.read16();
        }

        byte[] huffLenData = new byte[257];
        bis.read(huffLenData);
        int[][] huffmanLengths = unpackHuffmanLengths(huffLenData);

        return new StreamHeader(sampleRate, preRoll, totalSamples, channels, midSideChannels, width, bands, bandWidths, coeffScale, quantizers, huffmanLengths);
    }

}
