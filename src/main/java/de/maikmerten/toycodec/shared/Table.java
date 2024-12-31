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

package de.maikmerten.toycodec.shared;

import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.FileOutputStream;

public class Table {

    private final String[] headings;
    private final List<Object[]> rows;
    private final String csvSep = "\t";

    public Table(String[] headings) {
        this.headings = headings;
        this.rows = new ArrayList<>();
    }

    public void addData(Object[] data) {
        if(data.length != headings.length) {
            throw new RuntimeException("expected " + headings.length + " columns of data, got " + data.length);
        }
        rows.add(data);
    }

    public void writeCsv(String filename) {
        writeCsv(new File(filename));
    }

    private String escape(String s) {
        return s.replaceAll(csvSep, "\\" + csvSep);
    }

    public void writeCsv(File f) {
        try {
            StringBuilder sb = new StringBuilder();
            for(String colname : headings) {
                sb.append(escape(colname));
                sb.append(csvSep);
            }
            sb.append("\n");

            for(Object[] data : rows) {
                for(Object value : data) {
                    sb.append(escape(value.toString()));
                    sb.append(csvSep);
                }
                sb.append("\n");
            }

            FileOutputStream fos = new FileOutputStream(f);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
    
}
