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

package de.maikmerten.toycodec;

import de.maikmerten.toycodec.transform.MDCT;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;

import de.maikmerten.toycodec.bitstream.FrameHeader;
import de.maikmerten.toycodec.bitstream.QuantInfo;
import de.maikmerten.toycodec.bitstream.StreamHeader;
import de.maikmerten.toycodec.coding.Bands;
import de.maikmerten.toycodec.coding.MidSide;
import de.maikmerten.toycodec.coding.Quant;
import de.maikmerten.toycodec.coding.ZigZag;
import de.maikmerten.toycodec.coding.huffman.HuffCoder;
import de.maikmerten.toycodec.io.AudioWriter;
import de.maikmerten.toycodec.io.BitInputStream;

public class Decoder {

    private long[] quantInfoStat = new long[3];

    private void unpackCoeffs(byte[][] coeffBytes, int[] coeffs) {
        if (coeffBytes.length != 2) {
            throw new IllegalArgumentException("coeffBytes.length must be 2");
        }

        for (int i = 0; i < coeffs.length; i++) {
            byte lsb = coeffBytes[0][i];
            byte msb = coeffBytes[1][i];
            int coeff = ((msb & 0xFF) << 8) | (lsb & 0xFF);
            coeffs[i] = ZigZag.decodeZigZag(coeff);
        }
    }

    private void decodeMidSide(float[][] samples, int midSideChannels) {
        for (int c = 0; (c + 1) < samples.length && (c + 1) < midSideChannels; c += 2) {
            float[][] channelSamples = { samples[c], samples[c + 1] };
            MidSide.midSideToStereo(channelSamples);
        }
    }

    private void readQuantInfo(FrameHeader fh, QuantInfo[] quantInfos, int channels, int bandsNum, BitInputStream bis) {
        // read quantization information
        switch (fh.getQuantInfo()) {
            case FrameHeader.QUANTINFO_SHARED: // single quant info for all channels
                QuantInfo granuleHead = QuantInfo.fromInputStream(bis, bandsNum);
                for (int c = 0; c < channels; c++) {
                    quantInfos[c] = granuleHead;
                }
                quantInfoStat[0]++;
                break;
            case FrameHeader.QUANTINFO_PERCHANNEL: // quant info per channel
                for (int c = 0; c < channels; c++) {
                    quantInfos[c] = QuantInfo.fromInputStream(bis, bandsNum);
                }
                quantInfoStat[1]++;
                break;
            default:
                quantInfoStat[2]++;
                break;
        }
    }

    private void printQuantInfoStats() {
        System.out.println();
        System.out.println("    QUANTINFO_SHARED: " + quantInfoStat[0]);
        System.out.println("QUANTINFO_PERCHANNEL: " + quantInfoStat[1]);
        System.out.println("      QUANTINFO_NONE: " + quantInfoStat[2]);
    }

    public void decode(DecoderParams parms) {
        File bitstreamfile = parms.infile;
        File pcmfile = parms.outfile;

        try {
            BitInputStream bis = new BitInputStream(new FileInputStream(bitstreamfile));
            FileOutputStream fos = new FileOutputStream(pcmfile);

            StreamHeader sh = StreamHeader.fromInputStream(bis);
            int channels = sh.getChannels();
            int sampleRate = sh.getSampleRate();
            int coeffScale = sh.getCoeffScale();
            int width = sh.getWidth();
            int bandsNum = sh.getBands();
            long totalSamples = sh.getTotalSamples();

            System.out.println("sampleRate: " + sampleRate + "   channels: " + channels + "   width: " + width + "   bands: " + bandsNum);

            // +++++++++++++++++++++++++++++++++++++++++
            AudioWriter audioWriter = new AudioWriter(channels, sampleRate);
            audioWriter.updateHeader(pcmfile);
            audioWriter.setSkipSamples(sh.getPreRoll());
            audioWriter.setMaxSamples(totalSamples);
            Bands bandsInst = Bands.fromBandWidths(sh.getBandWidths());
            Quant quant = new Quant(coeffScale, sh.getQuantizers(), bandsInst);

            HuffCoder hc = new HuffCoder(sh.getHuffmanLengths());
            hc.setBitInput(bis);

            MDCT[] mdcts = new MDCT[channels];
            for (int c = 0; c < channels; c++) {
                mdcts[c] = new MDCT(width);
            }

            byte[][][] coeffsBytes = new byte[channels][2][width];
            int[] coeffs = new int[width];
            float[] coeffsUnquantized = new float[width];
            float[][] sampleBuffers = new float[channels][width];
            QuantInfo[] quantInfos = new QuantInfo[channels];

            FrameHeader fh = null;

            long frames = 0;
            int flushframes = 0;
            while (bis.available() > 0 || flushframes > 0) {

                boolean readFromStream = false;

                if (bis.available() > 0) {
                    // read frame header
                    fh = FrameHeader.fromInputStream(bis);

                    // read quantization information
                    readQuantInfo(fh, quantInfos, channels, bandsNum, bis);

                    // read Huffman-coded quanitzed coefficients from stream
                    hc.readByteSymbols(coeffsBytes, true);

                    // we have fresh data, no dummy frames
                    readFromStream = true;
                }

                for (int c = 0; c < channels; c++) {
                    MDCT mdct = mdcts[c];
                    float[] samples = sampleBuffers[c];

                    if (readFromStream) { // read from stream
                        QuantInfo qi = quantInfos[c];
                        unpackCoeffs(coeffsBytes[c], coeffs);
                        quant.unquantize(coeffs, coeffsUnquantized, qi.getQuantIndexes());
                    } else { // push dummy frames to flush window overlap
                        Arrays.fill(coeffsUnquantized, 0f);
                        if (c == channels - 1) {
                            flushframes--;
                        }
                    }

                    mdct.submitCoefficients(coeffsUnquantized);
                    mdct.imdct(samples);
                }

                if (fh.getMidSide()) {
                    decodeMidSide(sampleBuffers, sh.getMidSideChannels());
                }

                System.out.print("\rdecoded " + (++frames) + " frames");

                audioWriter.writeAudio(fos, sampleBuffers);
            }

            bis.close();
            audioWriter.updateHeader(pcmfile);
            fos.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        printQuantInfoStats();

    }

}
