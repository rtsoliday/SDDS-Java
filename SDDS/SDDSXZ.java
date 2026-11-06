/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDS;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Pure Java support for the <code>.xz</code> and <code>.lzma</code> files that the
 * SDDS toolkit reads and writes through liblzma. Like <code>lzma_auto_decoder</code>,
 * input accepts both the XZ container and the legacy LZMA format, whatever the file
 * is called. As in the C library, reading stops at the end of the first stream, and
 * input that ends early simply ends the data, leaving the SDDS reader to judge what it
 * got. Like the C writer, output is always an XZ container with a CRC32 check.
 * The encoder favors simplicity over ratio: a greedy search over hash chains with a
 * 2 MiB dictionary.
 */
final class SDDSXZ {
    private static final byte[] HEADER_MAGIC = { (byte) 0xfd, '7', 'z', 'X', 'Z', 0 };
    private static final int STATES = 12;
    private static final int PROB_INIT = 1024;
    private static final int END_POS_MODEL_INDEX = 14;
    private static final int FULL_DISTANCES = 128;
    private static final int MATCH_MIN_LEN = 2;
    private static final int MATCH_MAX_LEN = 273;
    private static final int LZMA2_MAX_UNCOMPRESSED = 1 << 21;
    private static final int LZMA2_MAX_COMPRESSED = 1 << 16;

    private SDDSXZ() {
    }

    /** Wrap a compressed stream, detecting XZ or legacy LZMA from its first byte. */
    static InputStream openInput(InputStream raw) throws IOException {
        InputStream in = raw instanceof BufferedInputStream ? raw : new BufferedInputStream(raw, 65536);
        in.mark(1);
        int first = in.read();
        in.reset();
        if (first == 0xfd)
            return new XZInput(in);
        return new AloneInput(in);
    }

    /** Wrap a stream so that everything written to the result is XZ compressed. */
    static OutputStream openOutput(OutputStream raw) throws IOException {
        return new XZOutput(raw);
    }

    private static void initProbs(short[] probs) {
        Arrays.fill(probs, (short) PROB_INIT);
    }

    private static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int n = in.read(b, off, len);
            if (n < 0)
                throw new EOFException("Unexpected end of compressed data");
            off += n;
            len -= n;
        }
    }

    private static int readByte(InputStream in) throws IOException {
        int c = in.read();
        if (c < 0)
            throw new EOFException("Unexpected end of compressed data");
        return c;
    }

    /** The probability models shared in layout by the decoder and the encoder. */
    private static class Model {
        int lc, lp, pb;
        short[] literal = new short[0];
        final short[] isMatch = new short[STATES << 4];
        final short[] isRep = new short[STATES];
        final short[] isRepG0 = new short[STATES];
        final short[] isRepG1 = new short[STATES];
        final short[] isRepG2 = new short[STATES];
        final short[] isRep0Long = new short[STATES << 4];
        final short[][] posSlot = new short[4][64];
        final short[] posSpecial = new short[1 + FULL_DISTANCES - END_POS_MODEL_INDEX];
        final short[] align = new short[16];
        final short[] matchLen = new short[2 + (16 << 3) * 2 + 256];
        final short[] repLen = new short[2 + (16 << 3) * 2 + 256];
        int state;
        int rep0, rep1, rep2, rep3;

        void setProperties(int props) throws IOException {
            if (props >= 225)
                throw new IOException("Invalid LZMA properties");
            pb = props / 45;
            props -= pb * 45;
            lp = props / 9;
            lc = props - lp * 9;
            int size = 0x300 << (lc + lp);
            if (literal.length != size)
                literal = new short[size];
        }

        void resetState() {
            initProbs(literal);
            initProbs(isMatch);
            initProbs(isRep);
            initProbs(isRepG0);
            initProbs(isRepG1);
            initProbs(isRepG2);
            initProbs(isRep0Long);
            for (short[] slot : posSlot)
                initProbs(slot);
            initProbs(posSpecial);
            initProbs(align);
            initProbs(matchLen);
            initProbs(repLen);
            state = 0;
            rep0 = rep1 = rep2 = rep3 = 0;
        }
    }

    /** LZMA decoder with a circular dictionary, resumable after any number of bytes. */
    private static final class Decoder extends Model {
        private byte[] dict;
        private int dictSize, dictPos, dictFull;
        private long position;
        private int range, code;
        private InputStream source;
        private int pendingLen;
        boolean endMarker;
        boolean inputEnded;

        void setDictionary(int size) throws IOException {
            if (size < 4096)
                size = 4096;
            if (dict == null || dict.length != size) {
                try {
                    dict = new byte[size];
                } catch (OutOfMemoryError e) {
                    throw new IOException("Not enough memory for an LZMA dictionary of " + size + " bytes");
                }
            }
            dictSize = size;
            resetDictionary();
        }

        void resetDictionary() {
            dictPos = dictFull = 0;
            position = 0;
            pendingLen = 0;
        }

        void startRange(InputStream in) throws IOException {
            source = in;
            if (readByte(in) != 0)
                throw new IOException("Corrupt LZMA data");
            code = 0;
            for (int i = 0; i < 4; i++)
                code = (code << 8) | readByte(in);
            range = -1;
        }

        private void normalize() throws IOException {
            if ((range & 0xff000000) == 0) {
                range <<= 8;
                code = (code << 8) | readByte(source);
            }
        }

        private int bit(short[] probs, int index) throws IOException {
            normalize();
            int p = probs[index];
            int bound = (range >>> 11) * p;
            if (Integer.compareUnsigned(code, bound) < 0) {
                range = bound;
                probs[index] = (short) (p + ((2048 - p) >>> 5));
                return 0;
            }
            range -= bound;
            code -= bound;
            probs[index] = (short) (p - (p >>> 5));
            return 1;
        }

        private int tree(short[] probs, int offset, int bits) throws IOException {
            int m = 1;
            for (int i = 0; i < bits; i++)
                m = (m << 1) + bit(probs, offset + m);
            return m - (1 << bits);
        }

        private int reverseTree(short[] probs, int offset, int bits) throws IOException {
            int m = 1, symbol = 0;
            for (int i = 0; i < bits; i++) {
                int b = bit(probs, offset + m);
                m = (m << 1) + b;
                symbol |= b << i;
            }
            return symbol;
        }

        private int directBits(int count) throws IOException {
            int result = 0;
            for (int i = 0; i < count; i++) {
                normalize();
                range >>>= 1;
                code -= range;
                int t = -(code >>> 31);
                code += range & t;
                result = (result << 1) + t + 1;
            }
            return result;
        }

        private int length(short[] probs, int posState) throws IOException {
            if (bit(probs, 0) == 0)
                return tree(probs, 2 + (posState << 3), 3);
            if (bit(probs, 1) == 0)
                return 8 + tree(probs, 2 + (16 << 3) + (posState << 3), 3);
            return 16 + tree(probs, 2 + (16 << 3) * 2, 8);
        }

        private byte dictByte(int distance) {
            int i = dictPos - distance - 1;
            if (i < 0)
                i += dictSize;
            return dict[i];
        }

        private void put(byte b, byte[] out, int index) {
            out[index] = b;
            dict[dictPos++] = b;
            if (dictPos == dictSize)
                dictPos = 0;
            if (dictFull < dictSize)
                dictFull++;
            position++;
        }

        /**
         * Decode up to <code>limit</code> bytes.
         *
         * @return the number of bytes produced, which is less than the limit only after
         *         an end marker or when the input ended
         */
        int decode(byte[] out, int off, int limit) throws IOException {
            int n = 0;
            int posMask = (1 << pb) - 1;
            int lpMask = (1 << lp) - 1;
            try {
                while (n < limit) {
                    int produced = decodeSymbol(out, off + n, limit - n, posMask, lpMask);
                    if (produced < 0)
                        break;
                    n += produced;
                }
            } catch (EOFException e) {
                inputEnded = true;
            }
            return n;
        }

        /** @return the number of bytes produced, or -1 at an end marker */
        private int decodeSymbol(byte[] out, int off, int limit, int posMask, int lpMask) throws IOException {
            if (pendingLen > 0) {
                int count = Math.min(pendingLen, limit);
                for (int i = 0; i < count; i++)
                    put(dictByte(rep0), out, off + i);
                pendingLen -= count;
                return count;
            }
            int posState = (int) position & posMask;
            if (bit(isMatch, (state << 4) + posState) == 0) {
                int prev = dictFull == 0 ? 0 : dictByte(0) & 0xff;
                int offset = 0x300 * ((((int) position & lpMask) << lc) + (prev >>> (8 - lc)));
                int symbol = 1;
                if (state < 7) {
                    while (symbol < 0x100)
                        symbol = (symbol << 1) + bit(literal, offset + symbol);
                } else {
                    if (Integer.compareUnsigned(rep0, dictFull) >= 0)
                        throw new IOException("Corrupt LZMA data");
                    int matchByte = dictByte(rep0) & 0xff;
                    int offs = 0x100;
                    while (symbol < 0x100) {
                        matchByte <<= 1;
                        int matchBit = matchByte & offs;
                        int b = bit(literal, offset + offs + matchBit + symbol);
                        symbol = (symbol << 1) + b;
                        offs &= b == 0 ? ~matchBit : matchBit;
                    }
                }
                put((byte) symbol, out, off);
                state = state < 4 ? 0 : state < 10 ? state - 3 : state - 6;
                return 1;
            }
            int len;
            if (bit(isRep, state) != 0) {
                if (dictFull == 0)
                    throw new IOException("Corrupt LZMA data");
                if (bit(isRepG0, state) == 0) {
                    if (bit(isRep0Long, (state << 4) + posState) == 0) {
                        if (Integer.compareUnsigned(rep0, dictFull) >= 0)
                            throw new IOException("Corrupt LZMA data");
                        state = state < 7 ? 9 : 11;
                        put(dictByte(rep0), out, off);
                        return 1;
                    }
                } else {
                    int distance;
                    if (bit(isRepG1, state) == 0) {
                        distance = rep1;
                    } else {
                        if (bit(isRepG2, state) == 0) {
                            distance = rep2;
                        } else {
                            distance = rep3;
                            rep3 = rep2;
                        }
                        rep2 = rep1;
                    }
                    rep1 = rep0;
                    rep0 = distance;
                }
                len = length(repLen, posState);
                state = state < 7 ? 8 : 11;
            } else {
                rep3 = rep2;
                rep2 = rep1;
                rep1 = rep0;
                len = length(matchLen, posState);
                state = state < 7 ? 7 : 10;
                int lenState = len < 4 ? len : 3;
                int slot = tree(posSlot[lenState], 0, 6);
                if (slot < 4) {
                    rep0 = slot;
                } else {
                    int footer = (slot >> 1) - 1;
                    rep0 = (2 | (slot & 1)) << footer;
                    if (slot < END_POS_MODEL_INDEX) {
                        rep0 += reverseTree(posSpecial, rep0 - slot, footer);
                    } else {
                        rep0 += directBits(footer - 4) << 4;
                        rep0 += reverseTree(align, 0, 4);
                    }
                }
                if (rep0 == -1) {
                    endMarker = true;
                    return -1;
                }
            }
            if (Integer.compareUnsigned(rep0, dictFull) >= 0)
                throw new IOException("Corrupt LZMA data");
            pendingLen = len + MATCH_MIN_LEN;
            return 0;
        }

        void copyUncompressed(byte[] out, int off, int count) {
            for (int i = 0; i < count; i++)
                put(out[off + i], out, off + i);
        }
    }

    /** Reader for the legacy LZMA format: a 13-byte header and one raw stream. */
    private static final class AloneInput extends InputStream {
        private final InputStream in;
        private final Decoder decoder = new Decoder();
        private final byte[] buffer = new byte[65536];
        private int bufferPos, bufferEnd;
        private long remaining;
        private final byte[] one = new byte[1];

        AloneInput(InputStream in) throws IOException {
            this.in = in;
            byte[] header = new byte[13];
            readFully(in, header, 0, 13);
            decoder.setProperties(header[0] & 0xff);
            long dictSize = 0;
            for (int i = 0; i < 4; i++)
                dictSize |= (header[1 + i] & 0xffL) << (8 * i);
            remaining = 0;
            for (int i = 0; i < 8; i++)
                remaining |= (header[5 + i] & 0xffL) << (8 * i);
            /* Like liblzma, refuse what the legacy format's own tools would never write. */
            long rounded = dictSize == 0 ? 0 : dictSize - 1;
            for (int shift : new int[] { 2, 3, 4, 8, 16 })
                rounded |= rounded >>> shift;
            if (((rounded + 1) & 0xffffffffL) != (dictSize & 0xffffffffL)
                    || (remaining != -1 && (remaining >>> 38) != 0))
                throw new IOException("Not an LZMA stream");
            if (dictSize > Integer.MAX_VALUE - 8)
                throw new IOException("LZMA dictionary too large for Java");
            decoder.setDictionary((int) dictSize);
            decoder.resetState();
            decoder.startRange(in);
        }

        public int read() throws IOException {
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0)
                return 0;
            if (bufferPos == bufferEnd) {
                if (remaining == 0 || decoder.endMarker || decoder.inputEnded)
                    return -1;
                int limit = remaining < 0 ? buffer.length : (int) Math.min(remaining, buffer.length);
                bufferEnd = decoder.decode(buffer, 0, limit);
                bufferPos = 0;
                if (remaining > 0)
                    remaining -= bufferEnd;
                if (bufferEnd == 0)
                    return -1;
            }
            int count = Math.min(len, bufferEnd - bufferPos);
            System.arraycopy(buffer, bufferPos, b, off, count);
            bufferPos += count;
            return count;
        }

        public void close() throws IOException {
            in.close();
        }
    }

    /** Reader for one or more concatenated XZ streams holding LZMA2 blocks. */
    private static final class XZInput extends InputStream {
        private final InputStream in;
        private final Decoder decoder = new Decoder();
        private byte[] buffer = new byte[LZMA2_MAX_UNCOMPRESSED];
        private final byte[] compressed = new byte[LZMA2_MAX_COMPRESSED];
        private int bufferPos, bufferEnd;
        private final byte[] one = new byte[1];
        private int streamFlags;
        private boolean inBlock, finished, needProperties, needDictionaryReset;
        private long blockCompressed, blockUncompressed;
        private int blockHeaderSize;
        private final ArrayList<long[]> records = new ArrayList<long[]>();
        private CRC32 crc32;
        private long crc64;
        private MessageDigest sha256;

        XZInput(InputStream in) throws IOException {
            this.in = in;
            readStreamHeader();
        }

        private void readStreamHeader() throws IOException {
            byte[] header = new byte[12];
            readFully(in, header, 0, 12);
            for (int i = 0; i < 6; i++)
                if (header[i] != HEADER_MAGIC[i])
                    throw new IOException("Not an XZ stream");
            CRC32 crc = new CRC32();
            crc.update(header, 6, 2);
            if ((int) crc.getValue() != littleInt(header, 8))
                throw new IOException("Corrupt XZ stream header");
            if (header[6] != 0 || (header[7] & 0xf0) != 0)
                throw new IOException("Unsupported XZ stream flags");
            streamFlags = header[7] & 0x0f;
            records.clear();
        }

        private int littleInt(byte[] b, int off) {
            return (b[off] & 0xff) | (b[off + 1] & 0xff) << 8 | (b[off + 2] & 0xff) << 16 | (b[off + 3] & 0xff) << 24;
        }

        private long readVli(InputStream s, CRC32 crc) throws IOException {
            long value = 0;
            for (int i = 0; i < 9; i++) {
                int c = readByte(s);
                if (crc != null)
                    crc.update(c);
                value |= (long) (c & 0x7f) << (7 * i);
                if ((c & 0x80) == 0) {
                    if (c == 0 && i > 0)
                        throw new IOException("Corrupt XZ integer");
                    return value;
                }
            }
            throw new IOException("Corrupt XZ integer");
        }

        /** Start the next block, or process the index and footer if none is left. */
        private boolean startBlock() throws IOException {
            while (true) {
                int size = in.read();
                if (size < 0)
                    throw new EOFException("Unexpected end of XZ stream");
                if (size == 0) {
                    readIndexAndFooter();
                    return false;
                }
                int realSize = (size + 1) * 4;
                byte[] header = new byte[realSize];
                header[0] = (byte) size;
                readFully(in, header, 1, realSize - 1);
                CRC32 crc = new CRC32();
                crc.update(header, 0, realSize - 4);
                if ((int) crc.getValue() != littleInt(header, realSize - 4))
                    throw new IOException("Corrupt XZ block header");
                int flags = header[1] & 0xff;
                if ((flags & 0x3c) != 0)
                    throw new IOException("Unsupported XZ block flags");
                ByteArrayInputStream body = new ByteArrayInputStream(header, 2, realSize - 6);
                if ((flags & 0x40) != 0)
                    readVli(body, null);
                if ((flags & 0x80) != 0)
                    readVli(body, null);
                if ((flags & 3) != 0)
                    throw new IOException("Unsupported XZ filter chain; only LZMA2 is supported");
                long filter = readVli(body, null);
                long propsSize = readVli(body, null);
                if (filter != 0x21 || propsSize != 1)
                    throw new IOException("Unsupported XZ filter; only LZMA2 is supported");
                int dictBits = readByte(body) & 0x3f;
                if (dictBits > 40)
                    throw new IOException("Corrupt LZMA2 properties");
                long dictSize = dictBits == 40 ? 0xffffffffL : (2L | (dictBits & 1)) << (dictBits / 2 + 11);
                if (dictSize > Integer.MAX_VALUE - 8)
                    throw new IOException("LZMA dictionary too large for Java");
                decoder.setDictionary((int) dictSize);
                for (int c; (c = body.read()) >= 0;)
                    if (c != 0)
                        throw new IOException("Corrupt XZ block header");
                inBlock = true;
                blockHeaderSize = realSize;
                blockCompressed = blockUncompressed = 0;
                needProperties = needDictionaryReset = true;
                startCheck();
                return true;
            }
        }

        private void startCheck() throws IOException {
            crc32 = null;
            sha256 = null;
            crc64 = -1L;
            if (streamFlags == 1)
                crc32 = new CRC32();
            else if (streamFlags == 10) {
                try {
                    sha256 = MessageDigest.getInstance("SHA-256");
                } catch (NoSuchAlgorithmException e) {
                    throw new IOException("SHA-256 is unavailable");
                }
            }
        }

        private void updateCheck(byte[] b, int len) {
            if (crc32 != null)
                crc32.update(b, 0, len);
            else if (sha256 != null)
                sha256.update(b, 0, len);
            else if (streamFlags == 4) {
                long c = crc64;
                for (int i = 0; i < len; i++)
                    c = CRC64_TABLE[(int) ((c ^ b[i]) & 0xff)] ^ (c >>> 8);
                crc64 = c;
            }
        }

        private int checkSize() {
            if (streamFlags == 0)
                return 0;
            if (streamFlags <= 3)
                return 4;
            if (streamFlags <= 6)
                return 8;
            if (streamFlags <= 9)
                return 16;
            if (streamFlags <= 12)
                return 32;
            if (streamFlags <= 15)
                return 64;
            return 0;
        }

        private void finishBlock() throws IOException {
            int padding = (int) ((4 - (blockCompressed & 3)) & 3);
            for (int i = 0; i < padding; i++)
                if (readByte(in) != 0)
                    throw new IOException("Corrupt XZ block padding");
            int size = checkSize();
            byte[] stored = new byte[size];
            readFully(in, stored, 0, size);
            byte[] actual = null;
            if (crc32 != null) {
                long v = crc32.getValue();
                actual = new byte[4];
                for (int i = 0; i < 4; i++)
                    actual[i] = (byte) (v >>> (8 * i));
            } else if (streamFlags == 4) {
                long v = ~crc64;
                actual = new byte[8];
                for (int i = 0; i < 8; i++)
                    actual[i] = (byte) (v >>> (8 * i));
            } else if (sha256 != null) {
                actual = sha256.digest();
            }
            if (actual != null && !Arrays.equals(actual, stored))
                throw new IOException("XZ integrity check failed");
            records.add(new long[] { blockHeaderSize + blockCompressed + size, blockUncompressed });
            inBlock = false;
        }

        private void readIndexAndFooter() throws IOException {
            CRC32 crc = new CRC32();
            crc.update(0);
            long indexSize = 1;
            long[] counter = new long[] { 0 };
            CountingStream counting = new CountingStream(in, counter);
            long count = readVli(counting, crc);
            if (count != records.size())
                throw new IOException("Corrupt XZ index");
            for (int i = 0; i < count; i++) {
                long unpadded = readVli(counting, crc);
                long uncompressed = readVli(counting, crc);
                long[] record = records.get(i);
                if (unpadded != record[0] || uncompressed != record[1])
                    throw new IOException("Corrupt XZ index");
            }
            indexSize += counter[0];
            int padding = (int) ((4 - (indexSize & 3)) & 3);
            for (int i = 0; i < padding; i++) {
                int c = readByte(in);
                if (c != 0)
                    throw new IOException("Corrupt XZ index");
                crc.update(c);
            }
            indexSize += padding;
            byte[] stored = new byte[4];
            readFully(in, stored, 0, 4);
            if ((int) crc.getValue() != littleInt(stored, 0))
                throw new IOException("Corrupt XZ index");
            byte[] footer = new byte[12];
            readFully(in, footer, 0, 12);
            if (footer[10] != 'Y' || footer[11] != 'Z')
                throw new IOException("Corrupt XZ stream footer");
            CRC32 footerCrc = new CRC32();
            footerCrc.update(footer, 4, 6);
            if ((int) footerCrc.getValue() != littleInt(footer, 0))
                throw new IOException("Corrupt XZ stream footer");
            if (footer[8] != 0 || (footer[9] & 0xff) != streamFlags)
                throw new IOException("Corrupt XZ stream footer");
            long backward = ((littleInt(footer, 4) & 0xffffffffL) + 1) * 4;
            if (backward != indexSize + 4)
                throw new IOException("Corrupt XZ stream footer");
        }

        /** Read as many bytes as the input still has, up to the count asked for. */
        private int readAvailable(byte[] b, int len) throws IOException {
            int n = 0;
            while (n < len) {
                int count = in.read(b, n, len - n);
                if (count < 0)
                    break;
                n += count;
            }
            return n;
        }

        /** Decode more data, treating the end of the input as the end of the data. */
        private boolean fill() throws IOException {
            if (finished)
                return false;
            try {
                return fillChunk();
            } catch (EOFException e) {
                finished = true;
                return false;
            }
        }

        /** Decode one LZMA2 chunk into the buffer. */
        private boolean fillChunk() throws IOException {
            while (!finished) {
                if (!inBlock && !startBlock()) {
                    finished = true;
                    return false;
                }
                int control = readByte(in);
                blockCompressed++;
                if (control == 0) {
                    finishBlock();
                    continue;
                }
                if (control >= 0xe0 || control == 1) {
                    needProperties = true;
                    needDictionaryReset = false;
                    decoder.resetDictionary();
                } else if (needDictionaryReset) {
                    throw new IOException("Corrupt LZMA2 data");
                }
                int produced;
                if (control >= 0x80) {
                    int uncompressedSize = ((control & 0x1f) << 16) + (readByte(in) << 8) + readByte(in) + 1;
                    int compressedSize = (readByte(in) << 8) + readByte(in) + 1;
                    blockCompressed += 4;
                    if (control >= 0xc0) {
                        needProperties = false;
                        int props = readByte(in);
                        blockCompressed++;
                        decoder.setProperties(props);
                        if (decoder.lc + decoder.lp > 4)
                            throw new IOException("Corrupt LZMA2 properties");
                        decoder.resetState();
                    } else if (needProperties) {
                        throw new IOException("Corrupt LZMA2 data");
                    } else if (control >= 0xa0) {
                        decoder.resetState();
                    }
                    int available = readAvailable(compressed, compressedSize);
                    blockCompressed += compressedSize;
                    decoder.startRange(new ByteArrayInputStream(compressed, 0, available));
                    produced = decoder.decode(buffer, 0, uncompressedSize);
                    if (available < compressedSize || decoder.inputEnded)
                        finished = true;
                    else if (produced != uncompressedSize)
                        throw new IOException("Corrupt LZMA2 data");
                } else {
                    if (control > 2)
                        throw new IOException("Corrupt LZMA2 data");
                    produced = (readByte(in) << 8) + readByte(in) + 1;
                    blockCompressed += 2 + produced;
                    int available = readAvailable(buffer, produced);
                    if (available < produced) {
                        produced = available;
                        finished = true;
                    }
                    decoder.copyUncompressed(buffer, 0, produced);
                }
                updateCheck(buffer, produced);
                blockUncompressed += produced;
                bufferPos = 0;
                bufferEnd = produced;
                return produced > 0;
            }
            return false;
        }

        public int read() throws IOException {
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0)
                return 0;
            if (bufferPos == bufferEnd && !fill())
                return -1;
            int count = Math.min(len, bufferEnd - bufferPos);
            System.arraycopy(buffer, bufferPos, b, off, count);
            bufferPos += count;
            return count;
        }

        public void close() throws IOException {
            in.close();
        }
    }

    /** Counts bytes read through it, for working out the size of an XZ index. */
    private static final class CountingStream extends InputStream {
        private final InputStream in;
        private final long[] count;

        CountingStream(InputStream in, long[] count) {
            this.in = in;
            this.count = count;
        }

        public int read() throws IOException {
            int c = in.read();
            if (c >= 0)
                count[0]++;
            return c;
        }
    }

    private static final long[] CRC64_TABLE = new long[256];
    static {
        for (int i = 0; i < 256; i++) {
            long c = i;
            for (int k = 0; k < 8; k++)
                c = (c & 1) != 0 ? (c >>> 1) ^ 0xc96c5795d7870f42L : c >>> 1;
            CRC64_TABLE[i] = c;
        }
    }

    /** Writer for one XZ stream holding one LZMA2 block with a CRC32 check. */
    private static final class XZOutput extends OutputStream {
        private static final int DICT_BYTE = 18; /* 2 MiB */
        private static final int DICT_SIZE = 1 << 21;
        private static final int BUFFER_SIZE = DICT_SIZE + (1 << 21);
        private static final int LOOKAHEAD = MATCH_MAX_LEN + 1;
        private static final int HASH_BITS = 16;
        private static final int CHAIN_DEPTH = 24;
        private static final int PROPS = (2 * 5 + 0) * 9 + 3; /* lc=3 lp=0 pb=2 */

        private final OutputStream out;
        private final CRC32 check = new CRC32();
        private final byte[] buf = new byte[BUFFER_SIZE];
        private final int[] head = new int[1 << HASH_BITS];
        private final int[] prev = new int[DICT_SIZE];
        private final Model m = new Model();
        private int readPos, writePos, hashedTo;
        private long base;
        private long blockCompressed, blockUncompressed;
        private boolean firstChunk = true, needProps = true, needStateReset = true, closed;
        private final byte[] one = new byte[1];

        /* range encoder */
        private final byte[] rcOut = new byte[LZMA2_MAX_COMPRESSED + 4096];
        private int rcPos;
        private long low;
        private int range;
        private int cache;
        private long cacheSize;

        XZOutput(OutputStream out) throws IOException {
            this.out = out;
            Arrays.fill(head, -1);
            m.setProperties(PROPS);
            byte[] flags = { 0, 1 };
            out.write(HEADER_MAGIC);
            out.write(flags);
            writeCrc(flags, 0, 2);
            byte[] blockHeader = { 2, 0, 0x21, 1, DICT_BYTE, 0, 0, 0 };
            out.write(blockHeader);
            writeCrc(blockHeader, 0, blockHeader.length);
        }

        private void writeCrc(byte[] b, int off, int len) throws IOException {
            CRC32 crc = new CRC32();
            crc.update(b, off, len);
            writeLittleInt(crc.getValue());
        }

        private void writeLittleInt(long v) throws IOException {
            for (int i = 0; i < 4; i++)
                out.write((int) (v >>> (8 * i)) & 0xff);
        }

        public void write(int b) throws IOException {
            one[0] = (byte) b;
            write(one, 0, 1);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (writePos == buf.length)
                    compress(false);
                int count = Math.min(len, buf.length - writePos);
                System.arraycopy(b, off, buf, writePos, count);
                check.update(b, off, count);
                writePos += count;
                blockUncompressed += count;
                off += count;
                len -= count;
            }
        }

        /** Encode buffered data, keeping lookahead back unless this is the end. */
        private void compress(boolean finish) throws IOException {
            int keep = finish ? 0 : LOOKAHEAD;
            while (writePos - readPos > keep)
                encodeChunk(keep);
            if (!finish && readPos > DICT_SIZE) {
                int delta = readPos - DICT_SIZE;
                System.arraycopy(buf, delta, buf, 0, writePos - delta);
                for (int i = 0; i < head.length; i++)
                    head[i] = head[i] >= delta ? head[i] - delta : -1;
                for (int i = 0; i < prev.length; i++)
                    prev[i] = prev[i] >= delta ? prev[i] - delta : -1;
                base += delta;
                readPos -= delta;
                writePos -= delta;
                hashedTo = Math.max(hashedTo - delta, 0);
            }
        }

        private void shiftLow() {
            if ((low >>> 32) != 0 || low < 0xff000000L) {
                int carry = (int) (low >>> 32);
                int temp = cache;
                do {
                    rcOut[rcPos++] = (byte) (temp + carry);
                    temp = 0xff;
                } while (--cacheSize != 0);
                cache = (int) (low >>> 24) & 0xff;
            }
            cacheSize++;
            low = (low & 0x00ffffffL) << 8;
        }

        private void encodeBit(short[] probs, int index, int bit) {
            int p = probs[index];
            int bound = (range >>> 11) * p;
            if (bit == 0) {
                range = bound;
                probs[index] = (short) (p + ((2048 - p) >>> 5));
            } else {
                low += bound & 0xffffffffL;
                range -= bound;
                probs[index] = (short) (p - (p >>> 5));
            }
            if ((range & 0xff000000) == 0) {
                range <<= 8;
                shiftLow();
            }
        }

        private void encodeDirect(int value, int bits) {
            for (int i = bits - 1; i >= 0; i--) {
                range >>>= 1;
                if (((value >>> i) & 1) != 0)
                    low += range & 0xffffffffL;
                if ((range & 0xff000000) == 0) {
                    range <<= 8;
                    shiftLow();
                }
            }
        }

        private void tree(short[] probs, int offset, int bits, int symbol) {
            int t = 1;
            for (int i = bits - 1; i >= 0; i--) {
                int b = (symbol >>> i) & 1;
                encodeBit(probs, offset + t, b);
                t = (t << 1) + b;
            }
        }

        private void reverseTree(short[] probs, int offset, int bits, int symbol) {
            int t = 1;
            for (int i = 0; i < bits; i++) {
                int b = symbol & 1;
                symbol >>>= 1;
                encodeBit(probs, offset + t, b);
                t = (t << 1) + b;
            }
        }

        private void length(short[] probs, int posState, int len) {
            if (len < 8) {
                encodeBit(probs, 0, 0);
                tree(probs, 2 + (posState << 3), 3, len);
            } else if (len < 16) {
                encodeBit(probs, 0, 1);
                encodeBit(probs, 1, 0);
                tree(probs, 2 + (16 << 3) + (posState << 3), 3, len - 8);
            } else {
                encodeBit(probs, 0, 1);
                encodeBit(probs, 1, 1);
                tree(probs, 2 + (16 << 3) * 2, 8, len - 16);
            }
        }

        private int hash(int i) {
            int v = (buf[i] & 0xff) | (buf[i + 1] & 0xff) << 8 | (buf[i + 2] & 0xff) << 16;
            return (v * 0x9e3779b1) >>> (32 - HASH_BITS);
        }

        /** Add every position before <code>end</code> to the hash chains. */
        private void insertHashes(int end) {
            int limit = Math.min(end, writePos - 2);
            for (; hashedTo < limit; hashedTo++) {
                int h = hash(hashedTo);
                prev[(int) ((hashedTo + base) & (DICT_SIZE - 1))] = head[h];
                head[h] = hashedTo;
            }
        }

        private int matchLength(int a, int b, int max) {
            int n = 0;
            while (n < max && buf[a + n] == buf[b + n])
                n++;
            return n;
        }

        private void encodeLiteral(int i, long position) {
            int prevByte = position == 0 ? 0 : buf[i - 1] & 0xff;
            int offset = 0x300 * (prevByte >>> (8 - m.lc));
            int symbol = buf[i] & 0xff;
            if (m.state < 7) {
                int t = 1;
                for (int k = 7; k >= 0; k--) {
                    int b = (symbol >>> k) & 1;
                    encodeBit(m.literal, offset + t, b);
                    t = (t << 1) + b;
                }
            } else {
                int matchByte = buf[i - m.rep0 - 1] & 0xff;
                int offs = 0x100;
                int t = 1;
                for (int k = 7; k >= 0; k--) {
                    matchByte <<= 1;
                    int matchBit = matchByte & offs;
                    int b = (symbol >>> k) & 1;
                    encodeBit(m.literal, offset + offs + matchBit + t, b);
                    t = (t << 1) + b;
                    offs &= b == 0 ? ~matchBit : matchBit;
                }
            }
            m.state = m.state < 4 ? 0 : m.state < 10 ? m.state - 3 : m.state - 6;
        }

        private void encodeDistance(int distance, int len) {
            int lenState = len < 4 ? len : 3;
            int slot;
            if (distance < 4) {
                slot = distance;
            } else {
                int n = 31 - Integer.numberOfLeadingZeros(distance);
                slot = (n << 1) | ((distance >>> (n - 1)) & 1);
            }
            tree(m.posSlot[lenState], 0, 6, slot);
            if (slot >= 4) {
                int footer = (slot >> 1) - 1;
                int reduced = distance - ((2 | (slot & 1)) << footer);
                if (slot < END_POS_MODEL_INDEX) {
                    reverseTree(m.posSpecial, ((2 | (slot & 1)) << footer) - slot, footer, reduced);
                } else {
                    encodeDirect(reduced >>> 4, footer - 4);
                    reverseTree(m.align, 0, 4, reduced & 15);
                }
            }
        }

        /** Encode one LZMA2 chunk, leaving <code>keep</code> bytes unencoded. */
        private void encodeChunk(int keep) throws IOException {
            boolean resetState = needStateReset;
            if (resetState)
                m.resetState();
            rcPos = 0;
            low = 0;
            range = -1;
            cache = 0;
            cacheSize = 1;
            int start = readPos;
            while (writePos - readPos > keep && readPos - start < LZMA2_MAX_UNCOMPRESSED - MATCH_MAX_LEN
                    && rcPos + cacheSize < LZMA2_MAX_COMPRESSED - 128) {
                insertHashes(readPos);
                long position = readPos + base;
                int posState = (int) position & 3;
                int available = Math.min(writePos - readPos, MATCH_MAX_LEN);
                int window = (int) Math.min(position, DICT_SIZE - 1);

                int bestRep = -1, repLen = 0;
                int[] reps = { m.rep0, m.rep1, m.rep2, m.rep3 };
                if (position > 0) {
                    for (int r = 0; r < 4; r++) {
                        if (reps[r] < window) {
                            int len = matchLength(readPos, readPos - reps[r] - 1, available);
                            if (len > repLen) {
                                repLen = len;
                                bestRep = r;
                            }
                        }
                    }
                }
                int bestLen = 0, bestDistance = 0;
                if (available >= 3) {
                    int candidate = head[hash(readPos)];
                    for (int depth = 0; depth < CHAIN_DEPTH && candidate >= 0; depth++) {
                        int distance = readPos - candidate - 1;
                        if (distance < 0 || distance >= window)
                            break;
                        if (bestLen == 0 || buf[candidate + bestLen] == buf[readPos + Math.min(bestLen, available - 1)]) {
                            int len = matchLength(readPos, candidate, available);
                            if (len > bestLen) {
                                bestLen = len;
                                bestDistance = distance;
                                if (len == available)
                                    break;
                            }
                        }
                        candidate = prev[(int) ((candidate + base) & (DICT_SIZE - 1))];
                    }
                }
                if (bestLen == 3 && bestDistance >= (1 << 16))
                    bestLen = 0;
                if (bestLen == 4 && bestDistance >= (1 << 20))
                    bestLen = 0;

                if (repLen >= MATCH_MIN_LEN && repLen + 2 >= bestLen) {
                    encodeBit(m.isMatch, (m.state << 4) + posState, 1);
                    encodeBit(m.isRep, m.state, 1);
                    if (bestRep == 0) {
                        encodeBit(m.isRepG0, m.state, 0);
                        encodeBit(m.isRep0Long, (m.state << 4) + posState, 1);
                    } else {
                        int distance = reps[bestRep];
                        encodeBit(m.isRepG0, m.state, 1);
                        if (bestRep == 1) {
                            encodeBit(m.isRepG1, m.state, 0);
                        } else {
                            encodeBit(m.isRepG1, m.state, 1);
                            encodeBit(m.isRepG2, m.state, bestRep - 2);
                            if (bestRep == 3)
                                m.rep3 = m.rep2;
                            m.rep2 = m.rep1;
                        }
                        m.rep1 = m.rep0;
                        m.rep0 = distance;
                    }
                    length(m.repLen, posState, repLen - MATCH_MIN_LEN);
                    m.state = m.state < 7 ? 8 : 11;
                    readPos += repLen;
                } else if (bestLen >= 3) {
                    encodeBit(m.isMatch, (m.state << 4) + posState, 1);
                    encodeBit(m.isRep, m.state, 0);
                    length(m.matchLen, posState, bestLen - MATCH_MIN_LEN);
                    encodeDistance(bestDistance, bestLen - MATCH_MIN_LEN);
                    m.rep3 = m.rep2;
                    m.rep2 = m.rep1;
                    m.rep1 = m.rep0;
                    m.rep0 = bestDistance;
                    m.state = m.state < 7 ? 7 : 10;
                    readPos += bestLen;
                } else {
                    encodeBit(m.isMatch, (m.state << 4) + posState, 0);
                    encodeLiteral(readPos, position);
                    readPos++;
                }
            }
            for (int i = 0; i < 5; i++)
                shiftLow();
            int uncompressed = readPos - start;
            if (rcPos >= uncompressed) {
                /* Not worth compressing. The next compressed chunk has to reset its state. */
                int p = start;
                while (p < readPos) {
                    int count = Math.min(readPos - p, LZMA2_MAX_COMPRESSED);
                    out.write(firstChunk ? 1 : 2);
                    out.write((count - 1) >>> 8);
                    out.write((count - 1) & 0xff);
                    out.write(buf, p, count);
                    blockCompressed += 3 + count;
                    p += count;
                    firstChunk = false;
                }
                needStateReset = true;
                return;
            }
            int control = firstChunk ? 0xe0 : needProps ? 0xc0 : resetState ? 0xa0 : 0x80;
            out.write(control | ((uncompressed - 1) >>> 16));
            out.write(((uncompressed - 1) >>> 8) & 0xff);
            out.write((uncompressed - 1) & 0xff);
            out.write((rcPos - 1) >>> 8);
            out.write((rcPos - 1) & 0xff);
            blockCompressed += 5;
            if (control >= 0xc0) {
                out.write(PROPS);
                blockCompressed++;
            }
            out.write(rcOut, 0, rcPos);
            blockCompressed += rcPos;
            firstChunk = false;
            needProps = false;
            needStateReset = false;
        }

        private byte[] vli(long value) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(9);
            while (value >= 0x80) {
                bytes.write((int) (value & 0x7f) | 0x80);
                value >>>= 7;
            }
            bytes.write((int) value);
            return bytes.toByteArray();
        }

        public void flush() throws IOException {
            /* A compressor cannot flush part way through without hurting the ratio. */
            out.flush();
        }

        public void close() throws IOException {
            if (closed)
                return;
            closed = true;
            compress(true);
            out.write(0);
            blockCompressed++;
            int padding = (int) ((4 - (blockCompressed & 3)) & 3);
            for (int i = 0; i < padding; i++)
                out.write(0);
            writeLittleInt(check.getValue());

            ByteArrayOutputStream index = new ByteArrayOutputStream();
            index.write(0);
            index.write(1);
            index.write(vli(12 + blockCompressed + 4));
            index.write(vli(blockUncompressed));
            while (index.size() % 4 != 0)
                index.write(0);
            byte[] indexBytes = index.toByteArray();
            out.write(indexBytes);
            writeCrc(indexBytes, 0, indexBytes.length);

            long backward = (indexBytes.length + 4) / 4 - 1;
            byte[] footer = new byte[6];
            for (int i = 0; i < 4; i++)
                footer[i] = (byte) (backward >>> (8 * i));
            footer[5] = 1;
            writeCrc(footer, 0, 6);
            out.write(footer);
            out.write('Y');
            out.write('Z');
            out.close();
        }
    }
}
