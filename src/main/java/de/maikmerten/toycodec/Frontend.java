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

package de.maikmerten.toycodec;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Frontend {


    private static Options buildOpts() {
        Options opts = new Options();

        Option input = Option.builder("i").longOpt("input").desc("input file to process").required().hasArg().build();
        opts.addOption(input);

        Option output = Option.builder("o").longOpt("output").desc("write output to this file").required().hasArg().build();
        opts.addOption(output);

        Option decode = Option.builder("d").longOpt("decode").desc("decode compressed file").build();
        opts.addOption(decode);

        Option quality = Option.builder("q").longOpt("quality").desc("encoding quality, VBR operation").hasArg().build();
        opts.addOption(quality);

        Option ratio = Option.builder("r").longOpt("ratio").desc("approx. compression ratio, ~ABR operation").hasArg().build();
        opts.addOption(ratio);

        Option lowpass = Option.builder("l").longOpt("lowpass").desc("encoder lowpass in Hz").hasArg().build();
        opts.addOption(lowpass);

        return opts;
    }

    private static void usage(Options opts) {
        HelpFormatter hf = new HelpFormatter();
        hf.printHelp("<OPTIONS>", opts);
    }

    private static void encode(CommandLine cmdline) {
        EncoderParams parms = new EncoderParams();

        parms.infile = new File(cmdline.getOptionValue("input"));
        parms.outfile = new File(cmdline.getOptionValue("output"));
        parms.ratio = Float.parseFloat(cmdline.getOptionValue("ratio", "6"));
        parms.quality = Float.parseFloat(cmdline.getOptionValue("quality", "-1"));
        parms.lowpass = Integer.parseInt(cmdline.getOptionValue("lowpass", "20000"));

        Encoder enc = new Encoder();
        enc.encodeWav(parms);

    }


    private static void decode(CommandLine cmdline) {
        DecoderParams parms = new DecoderParams();

        parms.infile = new File(cmdline.getOptionValue("input"));
        parms.outfile = new File(cmdline.getOptionValue("output"));

        Decoder dec = new Decoder();
        dec.decode(parms);

    }


    public static void main(String[] args) {

        System.out.println("+--------------------------------------------+");
        System.out.println("| TOY-codec, enjoy (but don't seriously use) |");
        System.out.println("+--------------------------------------------+");
        
        Options opts = buildOpts();

        CommandLine cmdline = null;
        CommandLineParser parser = new DefaultParser();

        try {
            cmdline = parser.parse(opts, args);
        } catch (ParseException e) {
            usage(opts);
            System.exit(1);
        }

        if(cmdline.hasOption("decode")) {
            decode(cmdline);
        } else {
            encode(cmdline);
        }

    }
}
