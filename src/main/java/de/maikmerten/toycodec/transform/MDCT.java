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

package de.maikmerten.toycodec.transform;

import java.util.HashMap;
import java.util.Map;

public class MDCT {

    final int N;
    float[] sampleBuffer;
    float[] coeffBuffer;
    float[] window;
    float[][] cosTerm;

    private static Map<Integer, MDCTPrecompute> precompMap = new HashMap<>();

    /**
     * Private class to share precomputed values between MDCT-instances
     */
    private static class MDCTPrecompute {
        private int width;
        private float[] window;
        private float[][] cosTerm;

        private MDCTPrecompute(int width) {
            this.width = width;
            precomputeCosTerm();
            precomputeWindow();
        }

        float windowFunc(int n) {
            int windowWidth = 2 * width; // width of window
            double nOffset = 0.5;    // for symmetry
            return (float) Math.sin(((n + nOffset) / (1.0 * windowWidth)) * Math.PI);
        }

        private void precomputeWindow() {
            this.window = new float[2 * width];
            for(int n = 0; n < (2 * width); n++) {
                window[n] = windowFunc(n);
            }
        }

        float cosTermFunc(int n, int k) {
            double c;
            c = Math.cos((Math.PI / width) * (n + 0.5 + (width / 2.0)) * (k + 0.5));
            return (float) c;
        }
        
        private void precomputeCosTerm() {
            cosTerm = new float[2 * width][];
            for(int n = 0; n < (2 * width); n++) {
                cosTerm[n] = new float[2 * width];
                for(int k = 0; k < (2 * width); k++) {
                    cosTerm[n][k] = cosTermFunc(n, k);
                }
            }
        }
    }

    private static MDCTPrecompute getPrecompute(int n) {
        MDCTPrecompute pre = precompMap.get(n);
        if(pre == null) {
            pre = new MDCTPrecompute(n);
            precompMap.put(n, pre);
        }
        return pre;
    }

    public MDCT(int n) {
        this.N = n;
        // space for two blocks of sample data
        this.sampleBuffer = new float[2 * N];
        // space for two blocks of coefficients
        this.coeffBuffer = new float[2 * N];

        MDCTPrecompute pre = getPrecompute(n);
        this.window = pre.window;
        this.cosTerm = pre.cosTerm;
    }


    public void mdct(float[] outputCoeffs) {
        if (outputCoeffs.length != N) {
            throw new IllegalArgumentException("array size needed: " + N);
        }

        for (int k = 0; k < N; k++) {
            float coeff = 0;
            for (int n = 0; n < (2 * N); n++) {
                coeff += window[n] * sampleBuffer[n] * cosTerm[n][k];
            }
            outputCoeffs[k] = coeff;
        }
    }

    public void imdct(float[] outputSamples) {
        if (outputSamples.length != N) {
            throw new IllegalArgumentException("array size needed: " + N);
        }
        
        float twoDivN = (2f / N);

        // compute n samples
        for (int n = 0; n < N; n++) {

            // accumulators for sum-terms 1 and 2
            float t1 = 0f;
            float t2 = 0f;

            for (int k = 0; k < N; k++) {
                t1 += coeffBuffer[N + k] * cosTerm[n][k]; // coeff-block b+1
                t2 += coeffBuffer[k] * cosTerm[n + N][k]; // coeff-block b
            }

            t1 *= window[n];
            t2 *= window[N + n];

            outputSamples[n] = twoDivN * (t1 + t2);
        }
    }

    public void submitSamples(float[] newSamples) {
        if (newSamples.length != N) {
            throw new IllegalArgumentException(N + "samples needed");
        }

        // copy second half to first half of sample buffer
        System.arraycopy(sampleBuffer, N, sampleBuffer, 0, N);
        // copy new sample data into second half of sample buffer
        System.arraycopy(newSamples, 0, sampleBuffer, N, N);
    }

    public void submitCoefficients(float[] newCoeffs) {
        if (newCoeffs.length != N) {
            throw new IllegalArgumentException(N + "coeffs needed");
        }

        // copy second half to first halve of coeff buffer
        System.arraycopy(coeffBuffer, N, coeffBuffer, 0, N);
        // copy new coefficents into second half of coefficients buffer
        System.arraycopy(newCoeffs, 0, coeffBuffer, N, N);
    }

}
