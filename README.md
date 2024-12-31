# TOY Audio Codec

This is an **experimental** audio codec, intended as learning vehicle. Do not use to store audio in production environments, **the format may change at any time**.


## Why? Oh please, WHY?!

Audio codecs are fun and I'd like to learn a thing or two.

## Goals

The codec shall demonstrate very basic audio coding concepts and combine relative simplicty, low decoder complexity and okay-ish coding efficiency.

For great coding efficiency, [look at a well-tested, stable and widely-supported audio codec](https://opus-codec.org/).

As is most often the case with codecs, the encoder is more complex and slower than the decoder. Within reason, the format shall be decodable with a comparatively simple decoder and allow for high decoding speed.

The reference implementation available here is not performance optimized in any significant way.

## TOY codec features

- MDCT transform (doing the heavy lifting)
- Huffman-coded coefficients (for simplicity, not efficiency)
- up to 256 audio channels (because why not)
- support for Mid-Side encoding of stereo-pairs (easy enough to implement)
- sample-accurate length of decoded audio (useful for gapless playback)

A description of the format is provided in [format.md](format.md).


## Usage example

Encoding:

```
java -jar ToyCodec.jar --input original.wav --output encoded.toy
```

Decoding:

```
java -jar ToyCodec.jar --decode --input encoded.toy --output decoded.wav
```