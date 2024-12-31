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

public class ZigZag {
    /**
     * Encode a signed integer into a positive integer representation
     *
     * @param i a signed integer to be encoded
     * @return a positive integer encoding of the provided signed integer
     */
    public static int encodeZigZag(int i) {
        return (i < 0) ? (-2 * i) - 1 : (i << 1);
    }

    /**
     * Decode a zig-zag encoded signed integer
     *
     * @param code zig-zag encoded value
     * @return signed integer
     */
    public static int decodeZigZag(int code) {
        return (code & 0x1) == 0x1 ? (-(code >> 1)) - 1 : code >> 1;
    }
}
