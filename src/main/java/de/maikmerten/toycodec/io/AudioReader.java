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

import java.io.File;
import java.util.Arrays;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class AudioReader {

    private byte[] buf;
    private AudioInputStream ais;
    private int bytesPerSample;
    private int channels;
    private int sampleRate;

    public float[][] openAudioStream(File inputFile, int blocksize) {
        try {
            this.ais = AudioSystem.getAudioInputStream(inputFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        AudioFormat af = ais.getFormat();

        this.channels = af.getChannels();
        this.sampleRate = (int) af.getSampleRate();
        this.bytesPerSample = af.getSampleSizeInBits() / 8;

        if (bytesPerSample != 2) {
            throw new RuntimeException("Expects 16 bits per sample");
        }

        if (channels > 2) {
            throw new RuntimeException("No support yet for channels > 2");
        }

        this.buf = new byte[channels * bytesPerSample * blocksize];

        return new float[channels][blocksize];
    }

    public int readAudio(float[][] samples) {

        for (float[] channel : samples) {
            Arrays.fill(channel, 0f);
        }

        int read = 0;
        try {
            read = ais.read(buf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        int samplesRead = (read / bytesPerSample);

        for (int s = 0; s < samplesRead; s++) {
            int idx = bytesPerSample * s;
            byte byte0 = buf[idx];
            byte byte1 = buf[idx + 1];

            int sample = (byte1 << 8) | (byte0 & 0xFF);

            int c = s % channels;
            float[] channel = samples[c];
            channel[s / channels] = ((float) sample) / (Short.MAX_VALUE);
        }

        return samplesRead;
    }

    public int getSampleRate() {
        return this.sampleRate;
    }

    public int getChannels() {
        return this.channels;
    }


}
