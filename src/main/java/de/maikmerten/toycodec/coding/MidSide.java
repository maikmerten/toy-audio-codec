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

/**
 *
 * @author maik
 */
public class MidSide {

    public static void stereoToMidSide(float[][] samples) {
        if (samples.length != 2) {
            throw new RuntimeException("mid-side stereo needs two channels");
        }

        float[] left = samples[0];
        float[] right = samples[1];

        for (int i = 0; i < left.length; i++) {
            float l = left[i];
            float r = right[i];
            float m = 0.5f * (l + r);
            float s = 0.5f * (l - r);

            samples[0][i] = m;
            samples[1][i] = s;
        }
    }

    public static void midSideToStereo(float[][] samples) {
        if (samples.length != 2) {
            throw new RuntimeException("mid-side stereo needs two channels");
        }

        float[] mid = samples[0];
        float[] side = samples[1];

        for (int i = 0; i < mid.length; i++) {
            float m = mid[i];
            float s = side[i];
            float l = (m + s);
            float r = (m - s);

            samples[0][i] = l;
            samples[1][i] = r;
        }
    }

}
