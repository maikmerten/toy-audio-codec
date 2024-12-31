# TOY codec format overview

> **Note:** This is not (yet?) a complete stand-alone specification sufficient to develop independent implementations, so looking into the reference implementation might as of now still be needed for clarifications.

## The transform

First, math.

### MDCT

TOY streams use a single MDCT block size. There is no block size switching mechanic. Instead, the encoder shall employ a block size that is a somewhat sensible tradeoff between frequency resolution and transient response.

> **Note:** The reference encoder currently operates on a MDCT-size of 256. In the following, the MDCT block size is denoted as $N$.


> **Note:** The following definitions are respectfully appropriated and adapted from [Wikipedia, the free online encyclopedia that anyone can edit](https://de.wikipedia.org/wiki/Modifizierte_diskrete_Kosinustransformation).


#### Window function

To avoid discontinuities between MDCT blocks, a windowing function is used to smoothly blend between overlapping blocks. TOY currently uses a simple sine-window as defined via:


```math
w_n = \sin \left[ \frac{n + 0.5}{2N} * \pi \right]
```

The window function is applied over two blocks worth of MDCT data, thus the width of the window function is $2N$.


#### Forward transform

With a $2N$-sized buffer $x$ of two consecutive blocks $b$ of sample data, a $N$-sized block of coefficients for a block can be computed as follows:

```math
X_{b,k} = \sum_{n=0}^{2\mathrm{N}-1} w_n x_{_{b\mathrm{N}-\mathrm{N}+n}} \cos \left[\frac{\pi}{\mathrm{N}} \left(n+\frac{1}{2}+\frac{\mathrm{N}}{2}\right) \left(k+\frac{1}{2}\right) \right]
```

Here, $X_{b,k}$ denotes coefficent $k$ within the coefficient block $X$ of block $b$.


#### Inverse transform

To reconstruct a $N$-sized block of sample data from coefficients, two consecutive blocks of coefficients $X_b$ and $X_{b+1}$ are processed according to following definition:

```math
y_{_{b\mathrm{N}+n}} = \frac{2}{\mathrm{N}} \Bigg(w_{n} \sum_{k=0}^{\mathrm{N}-1} X_{b+1,k} \cos \left[\frac{\pi}{\mathrm{N}} \left(n+\frac{1}{2}+\frac{\mathrm{N}}{2}\right) \left(k+\frac{1}{2}\right) \right] \ +\  w_{n+\mathrm{N}} \sum_{k=0}^{\mathrm{N}-1} X_{b,k} \cos \left[\frac{\pi}{\mathrm{N}} \left(n+N+\frac{1}{2}+\frac{\mathrm{N}}{2}\right) \left(k+\frac{1}{2}\right) \right] \Bigg)
```

#### Implementation in TOY

The reference software implements the transform naively as-is, resulting in a $O(N^2)$ runtime complexity. Optimized implementations can utilize a FFT-inspired implementation approach that should provide $O(N \log N)$ complexity.

The TOY-implementation of the MDCT is provided in [MDCT.java](./src/main/java/de/maikmerten/toycodec/transform/MDCT.java).

## Quantization

MDCT coefficients are float values that are scaled and quanitized to produce integer values, which then are transmitted in the bitstream.

### Quanitzation of MDCT coefficients in the encoder

1. all coefficients are scaled by a common _scale_ factor
1. the scaled coefficients are divided by a _quantizer_ value
1. the resulting value is rounded to an integer value

The division and the rounding are the _lossy_ part of the lossy codec. By choosing the quanitzer appropriately, the encoder can choose the precision of the transmitted values. A small quantizer means high precision, whereas a big quantizer will result in a low-precision representation of the original value.

### Reconstruction of MDCT coefficients in the decoder

Before the inverse MDCT can reconstruct sample value, the transmitted coefficients need to be converted back to a floating point representation. This is achieved as follows:

1. the transmitted integer-coefficients are multiplied by their respective _quantizer_ value
1. the resulting value is divided by the common _scale_ factor

The last step also facilitates the conversion from integer to a float value.

### Implementation in TOY

Quantization and reconstruction of coefficients is handled in [Quant.java](./src/main/java/de/maikmerten/toycodec/coding/Quant.java).


## ZigZag encoding

After scaling and quantizing coefficients, the resulting integer values can have a positive or negative sign, with small absolute values being more common than big absolute values. To simplify modeling probabilities for entropy coding later on, all values are converted into positive integers via ZigZag encoding.

### ZigZag-encode a value

Any integer value $i$ can be converted to a positive ZigZag-encoded integer via

```math
ZigZagEncode(i) =
\begin{cases}
2i, &\text{if } i \geq 0 \\
(-2i)-1, &\text{otherwise}
\end{cases}
```

### Decode a ZigZag-encoded value

A ZigZag-encoded value $z$ can be converted back to it original signed integer value via

```math
ZigZagDecode(z) = 
\begin{cases}
z / 2, &\text{if } z \text{ is even} \\
(-z / 2) - 1, &\text{otherwise}
\end{cases}
```

> **Note:** The division here needs to be an integer division, with digits behind the decimal point being discarded.

### Implementation in TOY

The TOY-implementation of ZigZag-encoding and -decoding is provided in [ZigZag.java](./src/main/java/de/maikmerten/toycodec/coding/ZigZag.java). It is possible to implement ZigZag-coding without multiplies or branches using shifts and bitwise operations.


## Bitstream format

In the TOY format, every multi-byte value is stored as a **little-endian** value.

### StreamHeader

Each TOY stream starts with a stream header which is needed to initialize the decoder.

> **Note**: In the following, for some fields, value adjustments such as "minus 1" or "divided by 25" are mentioned. If fields are later on referred to, what is referred to are the values **after reverting these adjustments** (i.e. after doing "add 1" in the case of "minus 1" or "times 25" in the case of "divided by 25").

| Field           | Format             | Description
|---              |---                 |---
| fourCC          | 4 * uint8          | Four ASCII characters encoding "TOY1"
| sampleRate      | uint16             | Sample rate (Hz) of the current stream, divided by 25.
| preRoll         | uint16             | Number of samples to be discarded by the decoder at the start of the stream.
| totalSamples    | uint64             | Total number of samples stored in this stream. Zero denotes "unknown".
| channels        | uint8              | Number of audio channels, minus 1.
| midSideChannels | uint8              | Number of channels on which Mid-Side-stereo coding is applied, minus 1.
| width           | uint8              | Width of the MDCT transform (number of MDCT lines), divided by 16
| bands           | uint8              | Number of bands, minus 1.
| bandWidths      | (bands-1) * uint16     | Number of MDCT lines assigned to each band. The remaining lines are assigned to the last band, so the total number of lines equals `width`. Thus, no information for the last band needs to be transmitted.
| coeffScale      | uint16             | Scale factor used during quantization and reconstruction.
| quantizers      | 63 * uint16        | 63 quantization values used in this stream. The 64th quantization value always quantizes coefficients to zero and thus does not need to be transmitted.
| huffmanLengths  | 257 * uint8        | Number of bits for 257 symbols in Huffman coding. The first 256 symbols encode byte literals (values `0x00` to `0xFF`). Symbol 257 encodes a STOP-symbol (this and the remaining symbols of this run have the byte-value `0x00`). Each of the 257 `uint8` values **contain two four-bit values (nibbles)**: The most significant nibble encodes the symbol bit lengths (minus 1) for "context 0", while the least significant nibble encodes the symbol bit lengths (minus 1) for "context 1". Thus symbols in each context can have a bit length between 1 and 16 bits (inclusive).


### FrameHeader

Each frame in TOY contains `width` samples of audio data per channel. Each frame begins with a single byte frame header, which starts at a byte-aligned position.

The single-byte frame header is divided into several subfields. The most significant bit is at position 7, the least significant bit is at position 0.

| Field           | Bit-position       | Description
|---              |---                 |---
| sync            | [7-4]              | The uppermost four bits of the frame header need to have the value `0xF` (all bits set). Otherwise, a bitstream desync has occured.
| quantInfo       | [3-2]              | This field encodes if this frame includes quantization indexes and if they are per-channel or shared by all channels. This is described in more detail later on.
| midSide         | [1]                | Denotes whether mid-side coding is used in this frame (field has value `0b1`) or not (field has value `0b0`).
| reserved        | [0]                | Currently unused, but if there ever is a short/long block switching mechanism, this bit would work just fine to signal block length.

### Quantization indexes

In the TOY codec, for every band a "quantization index" selects a quantizer value from the `quantizers` array transmitted in the stream header. Each quantization index is 6 bits (64 possible values). The quantization indexes from four bands (24 bits) are packed into three bytes, with the lower-numbered bands transmitted "first" (in the most significant bits).

If 16 bands are used, a complete set of quantization indexes thus is comprised of 12 bytes. This is considerable overhead and thus makes it desirable to not transmit this every single frame for every channel.


### Selection of quantization indexes via `quantInfo`

The `quantInfo` field in the frame header can assume following values:

- `0b00`: This frame does not contain new quantization indexes. Previously transmitted quanitzation indexes remain valid and in use.
- `0b01`: unused/invalid value
- `0b10`: This frame contains new quantization indexes. One set of quanitzation indexes is transmitted. This single set of quanitzation indexes is used for all channels.
- `0b11`: This frame contains new quantization indexes. For every channel an individual set of quanitzation indexes is transmitted and used for the respective channel.

> **Note:** These values are chosen so that when seeking, decoding can resume on a frame with a frame header where the most significant five bits are set (`sync` bits and most significant bit of `quantInfo`).

### Huffman coding

#### Computing the Huffman codes for context 0 and 1

In the stream header, the bit lengths for 257 symbols (symbols 0 to 256) are provided via the field `huffmanLengths`. Each uint8-value contains two 4-bit "nibbles". The most-significant nibble encodes the symbol-bit-length for context 0, while the least-significant nibble encodes the symbol-bit-length for context 1. Every nibble is adjusted by minus 1, so the bit-lengths reach from 1 to 16 (inclusive).

Construct canonical Huffman codes in the following way:

1. For each context (0 or 1)
   1. Insert all 257 symbols (symbols 0 to 256) into a list.
   1. Sort the list of symbols by the symbol bit-lengths as encoded in `huffmanLengths`, with symbols with small symbol bit-lengths at the top of the list. For symbols with the same bit length, the tie is broken by the symbol index (e.g., symbol `3` before symbol `4`).
   1. Initialize a variable `code` with zero.
   1. For each symbol in the symbol list:
      1. Determine the bit-length of the symbol and store that in `bitLength`.
      1. If `bitLength` is greater than the length of the previous symbol, left-shift `code` by the difference between current and previous bit length.
      1. The Huffman code for the symbol is the least-significant `bitLength` bits in `code`.
      1. Increment `code` by 1.

An implementation of this approach is provided in [HuffCoder.java](./src/main/java/de/maikmerten/toycodec/coding/huffman/HuffCoder.java), method `buildCanonicalCodeTable`.


#### Huffman coding of MDCT coefficients

The scaled and quantized MDCT-coefficients are ZigZag-encoded to 16-bit unsigned integers. These are then Huffman-encoded to variable-length bit strings.

The process of Huffman-coding the coefficients in each frame is done as follows:

1. for every audio channel
   1. for every MDCT-line (there are `width` lines)
       1. encode the least-significant byte of the 16-bit ZigZag-encoded value with "Context 0". If the current and all following byte-values of this run are `0xFF`, a STOP-symbol can be emitted.
   1. for every MDCT-line (there are `width` lines)
      1. encode the most-significant byte of the 16-bit ZigZag-encoded value with "Context 1". If the current and all following byte-values of this run are `0xFF`, a STOP-symbol can be emitted.
1. zero-pad the Huffman-bits so that the following frame header is on a byte-aligned position within the stream.

### Mid-Side encoding

If the frame header indicates Mid-Side-coding as active for the current frame (`midSide`-bit in the frame header), then the first `midSideChannels` (as provided by the stream header) are pairwise Mid-Side coded.

> **Example:** In a stream with 5 channels (channels 0, 1, 2, 3 and 4) and with `mmidSideChannels` having a value of 4, then channels 0 and 1 are a mid-side-pair and channels 2 and 3 are a second mid-side-pair. Channel 4 is not mid-side coded.

Within a mid-side-pair, the channel with the lower index is considered "left" and the channel with the higher index is considered "right". After mid-side-coding, the "left" channel carries "mid" and the "right" channel carries "side".

#### Encoding Mid-Side


```math
mid = 0.5 * (left + right)
```
```math
side = 0.5 * (left - right)
```

#### Decoding Mid-Side

```math
left = mid + side
```
```math
right = mid - side
```

#### Implementation in TOY

Mid-Side coding of channels is implemented in [MidSide.java](./src/main/java/de/maikmerten/toycodec/coding/MidSide.java).