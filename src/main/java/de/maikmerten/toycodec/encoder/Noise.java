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

public class Noise {

    public static float snr(float[] coeffsOriginal, float[] coeffsUnquantized, int[] bandMap, boolean[] skipMap,
            int band) {
        float snr = 0f;
        int count = 0;
        float max = 0f;

        for (int i = 0; i < coeffsOriginal.length; i++) {
            if (bandMap[i] != band || skipMap[i] || coeffsOriginal[i] == 0f) {
                continue;
            }
            float original = coeffsOriginal[i];
            float original2 = original * original;
            max = original2 > max ? original2 : max;

            float unquantized = coeffsUnquantized[i];

            float err = (original - unquantized);
            snr += (err * err);
            count++;
        }

        snr /= max;
        if(count > 0) {
            snr /= count;
        }

        return snr;
    }

}
