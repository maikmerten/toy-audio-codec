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

package de.maikmerten.toycodec.encoder;

/**
 *
 * @author maik
 */
public class MidSideAnalysis {

    public static boolean decideMidSide(float[][] samples, float[] channelBitrateAdjust, boolean vbr) {
        int channels = samples.length;

        boolean midSide = false;

        if (vbr) {
            if(channels == 2) {
                midSide = true;
                channelBitrateAdjust[0] = 0.9f;
                channelBitrateAdjust[1] = 1.1f;
            }
        } else {
            if (channels == 2) {
                midSide = true;
                channelBitrateAdjust[0] = (11f / 8f);
                channelBitrateAdjust[1] = (5f / 8f);
            }
        }

        if (!midSide) {
            for (int c = 0; c < channels; c++) {
                channelBitrateAdjust[c] = 1f;
            }
        }

        return midSide;
    }

}
