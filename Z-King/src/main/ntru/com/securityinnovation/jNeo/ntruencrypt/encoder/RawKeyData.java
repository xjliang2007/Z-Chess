/*
 * MIT License
 *
 * Copyright (c) 2016~2019 Z-Chess
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.securityinnovation.jNeo.ntruencrypt.encoder;

import com.securityinnovation.jNeo.math.FullPolynomial;
import com.securityinnovation.jNeo.ntruencrypt.KeyParams;

/**
 * This class holds the result of parsing a key blob. The
 * result contains the parameter set, the public key, and the
 * private key (which will be null if the input blob was a public
 * key blob).
 */
public class RawKeyData
{
    public KeyParams      keyParams;
    public FullPolynomial h;
    public FullPolynomial f;

    public RawKeyData(KeyParams _keyParams,
                      FullPolynomial _h)
    {
        keyParams = _keyParams;
        h = _h;
        f = null;
    }

    public RawKeyData(KeyParams _keyParams,
                      FullPolynomial _h,
                      FullPolynomial _f)
    {
        keyParams = _keyParams;
        h = _h;
        f = _f;
    }
}