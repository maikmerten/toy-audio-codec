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

import de.maikmerten.toycodec.bitstream.FrameHeader;
import de.maikmerten.toycodec.bitstream.QuantInfo;

/**
 *
 * @author maik
 */
public class BitrateControl {
    

    private final int bitsPerSample;
    private final float ratio;
    private final int channels;
    private final int samplesPerFrame;

    private final int maxUnspentBits = 128 * 1024 * 8;
    
    private int frameBitBudget = 0;
    private int unspentBits = 0;
    
    public BitrateControl(int bitsperSample, float ratio, int channels, int samplesPerFrame) {
        this.bitsPerSample = bitsperSample;
        this.ratio = ratio;
        this.channels = channels;
        this.samplesPerFrame = samplesPerFrame;
        
        computeFrameBitBudget();
    }
    
    
    private void computeFrameBitBudget() {
        float bitsPerFrameSample = bitsPerSample / ratio;
        int budget = (int) (samplesPerFrame * bitsPerFrameSample * channels);
        budget -= FrameHeader.BYTES * 8;
        this.frameBitBudget = budget;
    }
    
    
    public int getGranuleCoeffsBitBudget(boolean withGranuleHeader) {
        int budget = this.frameBitBudget;
        budget /= channels;
        
        int bitboost = (int)((unspentBits * 1.0) / channels) / 8;
        unspentBits -= bitboost;
        
        budget += bitboost;
        if(withGranuleHeader) {
            budget -= QuantInfo.BYTES * 8;
        }
        return budget;
    }
    
    
    public void submitFrameBits(int frameBits) {
        this.unspentBits = frameBitBudget - frameBits;
       
        if(this.unspentBits > this.maxUnspentBits) {
            this.unspentBits = this.maxUnspentBits;
        }

    }
    
       
    

    
}
