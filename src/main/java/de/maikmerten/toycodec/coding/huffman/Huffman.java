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

package de.maikmerten.toycodec.coding.huffman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author maik
 */
public class Huffman {

    private static class HuffNode {
        public final int freq;
        public final int id;
        private HuffNode left;
        private HuffNode right;

        public HuffNode(HuffNode left, HuffNode right, int id) {
            this(left.freq + right.freq, id);
            this.left = left;
            this.right = right;
        }

        public HuffNode(int freq, int id) {
            this.id = id;
            this.freq = freq;
            this.left = null;
            this.right = null;
        }

        public int getLengthForSymbol(int id) {
            int[] l = {0};
            this.getLengthForSymbol(l, id);
            return l[0];
        }

        private boolean getLengthForSymbol(int[] l, int id) {
            if (this.left == null && this.right == null) {      // leaf
                return this.id == id;
            }

            l[0]++;
            if(this.left.getLengthForSymbol(l, id) || this.right.getLengthForSymbol(l, id)) {
                return true;
            }
            l[0]--;

            return false;
        }
    }

    public static int[] getHuffmanLengthsByFrequencies(List<Integer> frequencies) {
        LinkedList<HuffNode> nodes = new LinkedList<>();
        int id = 0;
        for (int sym = 0; sym < frequencies.size(); sym++) {
            nodes.add(new HuffNode(frequencies.get(sym), id++));
        }

        while (nodes.size() > 1) {
            Collections.sort(nodes, (HuffNode n1, HuffNode n2) -> {
                int diff = n1.freq - n2.freq;               // sort by frequency
                return diff != 0 ? diff : n1.id - n2.id;    // break ties by id
            });
            nodes.add(new HuffNode(nodes.poll(), nodes.poll(), id++));
        }

        HuffNode root = nodes.poll();
        int[] lengths = new int[frequencies.size()];
        for (int sym = 0; sym < lengths.length; sym++) {
            lengths[sym] = root.getLengthForSymbol(sym);
        }
        
        return lengths;
    }

    public static int[] getHuffmanLengthsByFrequencies(int[] frequencies) {
        List<Integer> freqs = new ArrayList<>(frequencies.length);
        for (int f : frequencies) {
            freqs.add(f);
        }
        return getHuffmanLengthsByFrequencies(freqs);
    }


    public static void main(String[] args) {

        List<Integer> freqs = new ArrayList<>();
        freqs.add(5); // A, freq 5
        freqs.add(1); // B, freq 1
        freqs.add(6); // C, freq 6
        freqs.add(3); // D, freq 3

        int[] lengths = Huffman.getHuffmanLengthsByFrequencies(freqs);
        for(int sym = 0; sym < lengths.length; sym++) {
            System.out.println("symbol: " + sym  + "\tlength: " + lengths[sym]);
        }

    }
}
