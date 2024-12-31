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

public class Quant {

    private final float scale;
    private final int[] quantizers;

    private int[] quantMap;
    private boolean[] skipMap;


    public Quant(int scale, int[] quantizers, Bands bands) {
        if(quantizers.length != 63) {
            throw new IllegalArgumentException("quantizers-array needs to be length 63");
        }

        this.scale = scale * 1f;
        this.quantizers = new int[quantizers.length + 1];
        System.arraycopy(quantizers, 0, this.quantizers, 0, quantizers.length);
        this.quantizers[quantizers.length] = 9999999;

        this.quantMap = bands.getBandMap();
        this.skipMap = bands.getSkipMap();
    }
  

    public void quantize(float[] coeffs, int[] quantCoeffs, int[] quantIdx) {
        if(quantIdx.length != 16) {
            throw new IllegalArgumentException("expected length of quantIdx: 16");
        }

        for(int i = 0; i < coeffs.length; i++) {
            int bandIdx = this.quantMap[i];
            int qidx = quantIdx[bandIdx];

            // special case: harshest quantizer quantizes to zero
            if(qidx >= this.quantizers.length - 1 || skipMap[i]) {
                quantCoeffs[i] = 0;
                continue;
            }

            float scaledCoeff = (coeffs[i] * scale) / this.quantizers[qidx];
            
            // add -0.5 or +0.5 for rounding when truncating to int
            float roundOffset = scaledCoeff < 0 ? -0.5f : 0.5f;
            quantCoeffs[i] = (int)(scaledCoeff + roundOffset);
        }
    }

    public void unquantize(int[] quantCoeffs, float[] coeffs, int[] quantIdx) {
        if(quantIdx.length != 16) {
            throw new IllegalArgumentException("expected length of quantIdx: 16");
        }

        for(int i = 0; i < coeffs.length; i++) {
            int bandIdx = this.quantMap[i];
            int qidx = quantIdx[bandIdx];
            
            int qc = quantCoeffs[i];
            qc *= this.quantizers[qidx];
            coeffs[i] = (qc / scale);
        }
    }
 

}