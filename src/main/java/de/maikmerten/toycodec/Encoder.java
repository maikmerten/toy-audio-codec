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
import java.io.FileOutputStream;
import java.util.Arrays;

import de.maikmerten.toycodec.bitstream.CoeffWriter;
import de.maikmerten.toycodec.bitstream.FrameHeader;
import de.maikmerten.toycodec.bitstream.QuantInfo;
import de.maikmerten.toycodec.coding.Bands;
import de.maikmerten.toycodec.coding.MidSide;
import de.maikmerten.toycodec.coding.Quant;
import de.maikmerten.toycodec.coding.huffman.HuffTables;
import de.maikmerten.toycodec.bitstream.StreamHeader;
import de.maikmerten.toycodec.encoder.BitrateControl;
import de.maikmerten.toycodec.encoder.MidSideAnalysis;
import de.maikmerten.toycodec.encoder.Noise;
import de.maikmerten.toycodec.io.AudioReader;

public class Encoder {

    private int width = 256;
    private float freqCutoff = 20000;
    private int ratio = 6;
    private float qualityAdjust = 10f;

    private Quant quant = null;
    private Bands bands = null;
    private BitrateControl bitratectrl = null;
    private CoeffWriter coeffWriter = null;
    private int prevQuantCounter = 0;
    private long quantizerCount = 0;
    private long quantizerSum = 0;

    private final int[] defaultQuantIdx = {
        5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 5
    };

    private final int[] maxQuantIdx = {
        62, 62, 62, 62, 62, 62, 62, 63, 63, 63, 63, 63, 63, 63, 63, 63
    };

    private final float[] noiseTargets = {
        .002f, .0001f, .0002f, .0005f, .001f, .001f, .001f, .001f, .001f, .001f, .001f, .001f, .002f, .003f, .003f, .02f
    };

    // scale and quantizers must be transmitted in stream header
    private int scale = 512;

    private int[] generateQuantizers() {
        int[] quantizers = new int[63];

        double step = 10f / (quantizers.length - 1);
        for (int i = 0; i < quantizers.length; i++) {
            int q = (int) Math.round((Math.pow(2, i * step)));
            if (i > 0 && q <= quantizers[i - 1]) {
                q = quantizers[i - 1] + 1;
            }
            quantizers[i] = q;
        }

        return quantizers;
    }

    private void getDefaultQuant(int[] quantIdx) {
        if (quantIdx.length != defaultQuantIdx.length) {
            throw new IllegalArgumentException("expected length of quantIdx: " + defaultQuantIdx.length);
        }
        System.arraycopy(this.defaultQuantIdx, 0, quantIdx, 0, quantIdx.length);
    }

    private void increaseQuant(int[] quantIdx, int band) {
        int qidx = quantIdx[band] + 1;
        int maxqidx = maxQuantIdx[band];
        quantIdx[band] = qidx <= maxqidx ? qidx : maxqidx;
    }

    private void increaseQuant(int[] quantIdx) {
        if (quantIdx.length != defaultQuantIdx.length) {
            throw new IllegalArgumentException("expected length of quantIdx: " + defaultQuantIdx.length);
        }
        for (int i = 0; i < quantIdx.length; i++) {
            increaseQuant(quantIdx, i);
        }
    }

    private void ensureCoeffsInRange(CoeffWriter coeffWriter, Quant quant, Bands bands, float[] channelCoeffs,
            int[] quantCoeffs, int[] quantIdx) {
        int bandOutsideRange = -1;
        do {
            quant.quantize(channelCoeffs, quantCoeffs, quantIdx);
            bandOutsideRange = coeffWriter.checkCoeffsInRange(quantCoeffs, bands);
            if (bandOutsideRange >= 0) {
                increaseQuant(quantIdx, bandOutsideRange);
            }
        } while (bandOutsideRange >= 0);
    }

    private boolean areQuantizersSimilar(int[] quantIdx1, int[] quantIdx2) {
        for(int i = 0; i < quantIdx1.length; i++) {
            int q1 = quantIdx1[i];
            int q2 = quantIdx2[i];
            int diff = Math.abs(q1 - q2);
            if(q1 < q2) {
                // decreasing quality, follow lazily
                if(diff > 30) {
                    return false;
                }
            } else {
                // increasing quality, follow more eager
                if(diff > 12) {
                    return false;
                }
            }
        }
        return true;
    }


    private void searchABRQuantizers(long frame, float[][] coeffs, int[][] quantCoefficients, int[][] quantIndexes,
            int[][] prevQuantIndexes, boolean[] needsNewQuantIdx, float[] midsideBitrateAdjust) {

        int channels = coeffs.length;

        // ### Quantization loop per channel ###
        for (int c = 0; c < channels; c++) {
            float[] channelCoeffs = coeffs[c];
            int[] quantIdx = quantIndexes[c];
            int[] quantCoeffs = quantCoefficients[c];

            // check if previous quantizers can be used for current granule
            quant.quantize(channelCoeffs, quantCoeffs, prevQuantIndexes[c]);
            boolean isPrevQuantIdxInRange = coeffWriter.checkCoeffsInRange(quantCoeffs);

            // search for quantizers
            boolean firstQuant = true;
            int count = 0;
            int coeffBits = Integer.MAX_VALUE;
            int coeffBitsBudget = bitratectrl.getGranuleCoeffsBitBudget(true);
            coeffBitsBudget = (int) (coeffBitsBudget * midsideBitrateAdjust[c]);

            getDefaultQuant(quantIdx);
            ensureCoeffsInRange(coeffWriter, quant, bands, channelCoeffs, quantCoeffs, quantIdx);

            while (coeffBits > coeffBitsBudget) {
                if (firstQuant) {
                    firstQuant = false;
                } else {
                    increaseQuant(quantIdx);
                }

                // ruin coefficients via quantization
                quant.quantize(channelCoeffs, quantCoeffs, quantIdx);

                // estimate bytes needed to encode these coefficients
                coeffBits = (int) coeffWriter.estimateCoeffBits(quantCoeffs);

                if (count++ > 200) {
                    System.out.println("WARNING: couldn't increase quantizers to match bitrate\n");
                    break;
                }
            }

            // choose between previous quantIdx and new quantIdx
            if (frame > 1 && isPrevQuantIdxInRange && areQuantizersSimilar(quantIdx, prevQuantIndexes[c])) {
                needsNewQuantIdx[c] = false;
            } else {
                needsNewQuantIdx[c] = true;
            }
        }
    }

    private void searchVBRQuantizers(long frame, float[][] coeffs, int[][] quantCoefficients, int[][] quantIndexes,
            int[][] prevQuantIndexes, boolean[] needsNewQuantIdx, float[] midsideBitrateAdjust) {

        int channels = coeffs.length;

        float[] unquantizedCoeffs = new float[coeffs[0].length];

        // ### Quantization loop per channel ###
        for (int c = 0; c < channels; c++) {
            float[] channelCoeffs = coeffs[c];
            int[] quantIdx = quantIndexes[c];
            int[] quantCoeffs = quantCoefficients[c];

            // check if previous quantizers can be used for current granule
            quant.quantize(channelCoeffs, quantCoeffs, prevQuantIndexes[c]);
            boolean isPrevQuantIdxInRange = coeffWriter.checkCoeffsInRange(quantCoeffs);


            Arrays.fill(quantIdx, 0);
            ensureCoeffsInRange(coeffWriter, quant, bands, channelCoeffs, quantCoeffs, quantIdx);
            
            int[] bandMap = bands.getBandMap();
            boolean[] skipMap = bands.getSkipMap();

            boolean finished = false;
            while(!finished) {
                finished = true;
                quant.quantize(channelCoeffs, quantCoeffs, quantIdx);
                quant.unquantize(quantCoeffs, unquantizedCoeffs, quantIdx);
                for(int band = 0; band < quantIdx.length; band++) {
                    float noise = Noise.snr(channelCoeffs, unquantizedCoeffs, bandMap, skipMap, band);
                    float noiseTarget = noiseTargets[band] * qualityAdjust;

                    noiseTarget *= midsideBitrateAdjust[c];

                    if(noise < noiseTarget && quantIdx[band] < 62) {
                        finished = false;
                        increaseQuant(quantIdx, band);
                    }
                }
            }


            // choose between previous quantIdx and new quantIdx
            if (frame > 1 && isPrevQuantIdxInRange && areQuantizersSimilar(quantIdx, prevQuantIndexes[c])) {
                needsNewQuantIdx[c] = false;
            } else {
                needsNewQuantIdx[c] = true;
            }
        }
    }


    private int writeFrameToBitstream(CoeffWriter coeffWriter, boolean midSide, int[][] quantIndexes,
            int[][] quantizedCoefficients) {
        int frameBits = 0;

        boolean hasGranuleHeaders = quantIndexes != null;
        int granuleHeaders = FrameHeader.QUANTINFO_NONE;
        if (hasGranuleHeaders) {
            granuleHeaders = (quantIndexes.length == 1 ? FrameHeader.QUANTINFO_SHARED
                    : FrameHeader.QUANTINFO_PERCHANNEL);
        }

        // write frame header
        FrameHeader fh = new FrameHeader(granuleHeaders, midSide);
        fh.writeHeader(coeffWriter.getOutputStream());
        frameBits += FrameHeader.BYTES * 8;

        // Write granule headers (none, one, or for all channels)
        for (int c = 0; quantIndexes != null && c < quantIndexes.length; c++) {
            int[] quantIdx = quantIndexes[c];
            QuantInfo qi = new QuantInfo(quantIdx);
            qi.writeHeader(coeffWriter.getOutputStream());
            frameBits += QuantInfo.BYTES * 8;
        }

        // Write Huffman-coded quantized coefficients of all channels
        byte[] coeffBytes = coeffWriter.encodeCoeffsHuffman(quantizedCoefficients);
        coeffWriter.writeData(coeffBytes);
        frameBits += coeffBytes.length * 8;

        return frameBits;
    }

    private boolean selectQuantizers(boolean[] needsNewQuantIdx, int[][] quantIndexes, int[][] prevQuantIndexes,
            float[][] coeffs, int[][] quantCoefficients) {
        int channels = needsNewQuantIdx.length;

        // ### select between old and new quantizers
        boolean newQuantizers = false;
        for (int c = 0; c < channels; c++) {
            newQuantizers |= needsNewQuantIdx[c];
        }
        newQuantizers |= prevQuantCounter >= 16;

        for (int c = 0; c < channels; c++) {
            if (newQuantizers) {
                // copy over new quantizers for future reference
                System.arraycopy(quantIndexes[c], 0, prevQuantIndexes[c], 0, prevQuantIndexes[c].length);
                prevQuantCounter = 0;
            } else {
                // copy over previous quantizers
                System.arraycopy(prevQuantIndexes[c], 0, quantIndexes[c], 0, quantIndexes[c].length);
                prevQuantCounter++;
            }
            // quantize with whatever quantizers are selected
            quant.quantize(coeffs[c], quantCoefficients[c], quantIndexes[c]);

            // update quant statistics
            for (int i = 0; i < quantIndexes[c].length; i++) {
                quantizerSum += quantIndexes[c][i];
            }
            quantizerCount += quantIndexes[c].length;
        }

        return newQuantizers;
    }

    public void encodeWav(EncoderParams parms) {

        File infile = parms.infile;
        File bitstreamfile = parms.outfile;
        this.ratio = (int) parms.ratio;
        this.qualityAdjust = parms.quality;
        this.freqCutoff = parms.lowpass;

        int n = width; // select block size

        FileOutputStream bitOut = null;
        try {
            bitOut = new FileOutputStream(bitstreamfile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.coeffWriter = new CoeffWriter(bitOut);

        AudioReader aio = new AudioReader();
        float[][] samples = aio.openAudioStream(infile, n);
        int channels = aio.getChannels();
        int sampleRate = aio.getSampleRate();

        float[][] coeffs = new float[channels][n];
        int[][] quantIndexes = new int[channels][16];
        int[][] prevQuantIndexes = new int[channels][16];
        int[][] quantCoefficients = new int[channels][n];
        boolean[] needsNewQuantIdx = new boolean[channels];
        float[] midsideBitrateAdjust = new float[channels];
        MDCT[] mdcts = new MDCT[channels];
        for (int i = 0; i < mdcts.length; i++) {
            mdcts[i] = new MDCT(n);
        }

        int[] quantizers = generateQuantizers();
        this.prevQuantCounter = 0;
        this.quantizerSum = 0;
        this.quantizerCount = 0;

        this.bands = new Bands(width, sampleRate, this.freqCutoff);

        int[] bandWidths = bands.getBandWidths();
        StreamHeader streamheader = new StreamHeader(aio.getSampleRate(), width, 0, channels, channels, width,
                bandWidths.length,
                bandWidths, this.scale, quantizers, HuffTables.COEFFS);
        streamheader.writeHeader(bitOut);

        this.bitratectrl = new BitrateControl(16, ratio, channels, n);
        this.quant = new Quant(this.scale, quantizers, bands);

        long bytesTotal = 0;

        try {
            // read new block of audio data
            int samplesRead = aio.readAudio(samples);

            long frame = 0;
            long totalSamples = 0;

            int flush = 1; // when finishing, process one final frame with silence
            while (samplesRead > 0 || flush > 0) {
                if (samplesRead <= 0) {
                    flush--;
                }

                frame++;

                boolean vbr = qualityAdjust >= 0;

                boolean midSide = MidSideAnalysis.decideMidSide(samples, midsideBitrateAdjust, vbr);
                // transform to mid/side
                if (channels == 2 && midSide) {
                    MidSide.stereoToMidSide(samples);
                }

                // first run the MDCT for all channels for coeff analysis
                for (int c = 0; c < samples.length; c++) {
                    MDCT m = mdcts[c];
                    // put samples into sample buffer
                    m.submitSamples(samples[c]);
                    // run MDCT, get coeffs
                    m.mdct(coeffs[c]);
                }

                if(vbr) {
                    searchVBRQuantizers(frame, coeffs, quantCoefficients, quantIndexes, prevQuantIndexes, needsNewQuantIdx,
                    midsideBitrateAdjust);
                } else {
                    searchABRQuantizers(frame, coeffs, quantCoefficients, quantIndexes, prevQuantIndexes, needsNewQuantIdx,
                    midsideBitrateAdjust);
                }
                

                boolean newQuantizers = selectQuantizers(needsNewQuantIdx, quantIndexes, prevQuantIndexes, coeffs,
                        quantCoefficients);

                // ### Write bitstream ###
                int frameBits = writeFrameToBitstream(coeffWriter, midSide, (newQuantizers ? quantIndexes : null),
                        quantCoefficients);
                bitratectrl.submitFrameBits(frameBits);
                bytesTotal += frameBits / 8;

                // ### Show statistics
                totalSamples += samplesRead / channels;
                float seconds = (1f * frame * width) / sampleRate;
                float kbps = (bytesTotal * 8) / seconds / 1000;

                System.out.print("\rframe: " + frame + "\tsamples read: " + samplesRead + "\tmidSide: "
                        + (midSide ? 1 : 0) + "\t" + String.format("%.02f", seconds) + " s   "
                        + String.format("%.02f", kbps) + " kbps");

                samplesRead = aio.readAudio(samples);
            }
            bitOut.close();

            System.out.println();
            coeffWriter.computeHuffmanLengths();

            double averageQuantIdx = (quantizerSum * 1.0) / (quantizerCount * 1.0);
            System.out.println("Average quantizer index: " + averageQuantIdx);

            System.out.println("Total samples: " + totalSamples);
            streamheader.setTotalSamples(totalSamples);
            streamheader.updateHeader(bitstreamfile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
