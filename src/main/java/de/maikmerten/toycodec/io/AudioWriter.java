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
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class AudioWriter {

    private int channels;
    private int sampleRate;
    private ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private int skipSamples = 0;
    private long samplesWritten = 0;
    private long maxSamples = 0;

    public AudioWriter(int channels, int sampleRate) {
        this.channels = channels;
        this.sampleRate = sampleRate;
    }


    public int getSampleRate() {
        return this.sampleRate;
    }

    public int getChannels() {
        return this.channels;
    }

    public void setSkipSamples(int skip) {
        if (skip < 0) {
            throw new IllegalArgumentException("samples skip must not be negative (time travel not yet implemented)");
        }
        this.skipSamples = skip;
    }

    public void setMaxSamples(long totalSamples) {
        this.maxSamples = totalSamples;
    }


    private static void writeInt(ByteArrayOutputStream baos, int value) {
        baos.write(value & 0xFF); // LSB (least significant byte)
        baos.write((value >> 8) & 0xFF);
        baos.write((value >> 16) & 0xFF);
        baos.write((value >> 24) & 0xFF); // MSB (most significant byte)
    }

    private static void writeShort(ByteArrayOutputStream baos, short value) {
        baos.write(value & 0xFF); // LSB
        baos.write((value >> 8) & 0xFF); // MSB
    }

    private byte[] getHeaderBytes() {
        int samplerate = this.getSampleRate();
        int numChannels = this.getChannels();
        int numSamples = (int) (this.samplesWritten & 0xFFFFFFFF);


        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 16-Bit PCM, 2 Bytes pro Sample
        int sampleBytes = 2;

        // ByteRate = SampleRate * NumChannels * BytesPerSample
        int byteRate = samplerate * numChannels * sampleBytes;

        // BlockAlign = NumChannels * BytesPerSample
        int blockAlign = numChannels * sampleBytes;

        // Subchunk2Size = NumSamples * NumChannels * BytesPerSample
        int subchunk2Size = numSamples * numChannels * sampleBytes;

        // ChunkSize = 36 + Subchunk2Size
        int chunkSize = 36 + subchunk2Size;

        try {
            // Write the RIFF Header
            baos.write("RIFF".getBytes()); // ChunkID
            writeInt(baos, chunkSize); // ChunkSize
            baos.write("WAVE".getBytes()); // Format

            // Write the "fmt " chunk
            baos.write("fmt ".getBytes()); // Subchunk1ID
            writeInt(baos, 16); // Subchunk1Size (always 16 for PCM)
            writeShort(baos, (short) 1); // AudioFormat (1 for PCM)
            writeShort(baos, (short) numChannels); // NumChannels
            writeInt(baos, samplerate); // SampleRate
            writeInt(baos, byteRate); // ByteRate
            writeShort(baos, (short) blockAlign); // BlockAlign
            writeShort(baos, (short) 16); // BitsPerSample (16 bits per sample)

            // Write the "data" chunk
            baos.write("data".getBytes()); // Subchunk2ID
            writeInt(baos, subchunk2Size); // Subchunk2Size
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return baos.toByteArray();
    }

    public void updateHeader(File f) {
        try (RandomAccessFile raf = new RandomAccessFile(f, "rws")) {
            raf.seek(0);
            raf.write(getHeaderBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private void writeSample(float sample, ByteArrayOutputStream baos) {
        boolean f32 = false;
        if (f32) {
            // 32-bit float
            int floatbits = Float.floatToIntBits(sample);
            baos.write((byte) ((floatbits >> 24) & 0xFF));
            baos.write((byte) ((floatbits >> 16) & 0xFF));
            baos.write((byte) ((floatbits >> 8) & 0xFF));
            baos.write((byte) (floatbits & 0xFF));

        } else {
            // 16-bit signed integer
            int samplebits = (int) (sample * Short.MAX_VALUE);
            samplebits = samplebits > Short.MAX_VALUE ? Short.MAX_VALUE : samplebits;
            samplebits = samplebits < Short.MIN_VALUE ? Short.MIN_VALUE : samplebits;
            baos.write((byte) (samplebits & 0xFF));
            baos.write((byte) ((samplebits >> 8) & 0xFF));
        }
    }

    public void writeAudio(OutputStream os, float[][] samples) {
        baos.reset();

        for (int s = 0; s < samples[0].length; s++) {
            if (skipSamples > 0) {
                skipSamples--;
                continue;
            }

            if (samplesWritten >= maxSamples && maxSamples > 0) {
                break;
            }

            for (int c = 0; c < samples.length; c++) {
                float sample = samples[c][s];
                writeSample(sample, baos);
            }

            samplesWritten++;
        }

        try {
            os.write(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
