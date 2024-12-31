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

package de.maikmerten.toycodec.coding;

public class Bands {

    private int width;
    private float freqCutoff;
    private float freqNyquist;

    private final int[] bandFreqs = {
        0, 150, 320, 600, 900, 1200, 1600, 2100, 2600, 3200, 4000, 5500, 7300, 9500, 12000, 16000
    };

    private int[] bandMap;
    private boolean[] skipMap;

    private Bands() {
        this.bandMap = null;
        this.skipMap = null;
    }

    public Bands(int width, int sampleRate) {
        this(width, sampleRate, sampleRate/2f);
    }

    public Bands(int width, int sampleRate, float freqCutoff) {
        this.width = width;
        this.freqNyquist = sampleRate / 2f;
        this.freqCutoff = freqCutoff;

        computeBandMapping();
    }

    private void computeBandMapping() {
        this.bandMap = new int[this.width];
        this.skipMap= new boolean[this.width];

        float freqStep = this.freqNyquist / this.width;

        int band = 0;
        for(int i = 0; i < this.bandMap.length; i++) {
            float freqStart = (i * freqStep);
            while((band + 1) < bandFreqs.length && bandFreqs[band + 1] <= freqStart) {
                band++;
            }

            skipMap[i] = freqStart > this.freqCutoff;
            
            //System.out.println("i: " + i + " -> band: " + band + "    freqStart: " + freqStart);
            bandMap[i] = band;
        }
    }

    public int[] getBandWidths() {
        int[] widths = new int[bandFreqs.length];
        for(int band : bandMap) {
            widths[band]++;
        }
        return widths;
    }

    public int[] getBandMap() {
        return this.bandMap;
    }

    public boolean[] getSkipMap() {
        return this.skipMap;
    }

    public static Bands fromBandWidths(int[] bandWidths) {
        int width = 0;
        for(int bandWidth : bandWidths) {
            width += bandWidth;
        }

        int[] bandMap = new int[width];

        int idx = 0;
        for(int band = 0; band < bandWidths.length; band++) {
            int bandWidth = bandWidths[band];
            for(int i = 0; i < bandWidth; i++) {
                bandMap[idx++] = band;
            }
        }

        Bands b = new Bands();
        b.width = width;
        b.bandMap = bandMap;
        b.skipMap = new boolean[width];
        return b;
    }


}