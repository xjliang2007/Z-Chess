/*
 * MIT License
 *
 * Copyright (c) 2016~2020. Z-Chess
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.securityinnovation.jNeo.digest;

/**
 * <p>
 * This class defines an interface for digest operations. It is not declared
 * as an interface because it provides a number of utility methods
 * for common operations on digest objects.
 * <p>
 * There are number of coventions used by all implementations
 * of all methods in the digest subclasses:
 * <UL>
 * <LI>All buffers must be non-null; a <code>NullPointerException</code>
 * will be thrown if any buffer is null. This is true even if the
 * input length is 0.
 * <LI>In all cases where an offset and a length are supplied to indicate
 * the input or output range, an <code>IllegalArgumentException</code>
 * will be thrown if the specified range of the buffer overruns the
 * bounds of the array.
 * <LI>In all cases where an offset is supplied for an input or output
 * buffer, an <code>IllegalArgumentException</code> will be thrown
 * if the offset is negative.
 * <LI>In all cases where a length is supplied for an input or output
 * buffer, an <code>IllegalArgumentException</code> will be thrown
 * if the length is negative.
 * </UL>
 */
public abstract class Digest
{
    /**
     * Get the length of the hash output, in bytes.
     */
    public abstract int getDigestLen();

    /**
     * Get the size of the input block for the core hash algorithm in bytes.
     */
    public abstract int getBlockLen();

    /**
     * Calculate the digest of the input and return the result.
     * This discards any existing internal state of the object.
     * The object is left initialized for a new hash operation
     * (see <code>reset()</code>).
     *
     * @param input
     *                     the array holding the input
     * @param inputOffset
     *                     the index of the start of the input data
     * @param inputLength
     *                     the number of bytes of input
     * @param output
     *                     the array to write the digest into
     * @param outputOffset
     *                     the index where the first byte of output
     *                     should go. This will use getDigestLen() bytes
     *                     following this index.
     */
    public void digest(byte[] input, int inputOffset, int inputLength, byte[] output, int outputOffset)
    {
        reset();
        update(input, inputOffset, inputLength);
        finishDigest(output, outputOffset);
    }

    /**
     * Reinitialize the digest operation, discarding any internal state.
     */
    public abstract void reset();

    /**
     * Updates the message digest with new data.
     *
     * @param data
     *               the data to be added.
     * @param offset
     *               the start of the data in the array.
     * @param length
     *               the number of bytes of data to add.
     */
    public abstract void update(byte[] data, int offset, int length);

    /**
     * Completes the digest calculation and returns the result in the
     * supplied array. The output will be <code>getDigestLen()</code>
     * bytes long. The object is reinialized (see
     * <code>reset()</code>).
     */
    public abstract void finishDigest(byte[] out, int outOffset);

    /**
     * Completes the digest calculation and returns the result
     * in a newly allocated array.
     */
    public byte[] finishDigest()
    {
        byte[] dig = new byte[getDigestLen()];
        finishDigest(dig, 0);
        return dig;
    }

    /**
     * Default constructor. Hidden from the public API.
     */
    protected Digest()
    {}
}
