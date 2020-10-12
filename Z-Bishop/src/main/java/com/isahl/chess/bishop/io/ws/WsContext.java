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
package com.isahl.chess.bishop.io.ws;

import java.io.IOException;
import java.util.Base64;
import java.util.Random;

import com.isahl.chess.bishop.io.zfilter.ZContext;
import com.isahl.chess.queen.event.inf.ISort;
import com.isahl.chess.queen.io.core.inf.ISessionOption;

/**
 * @author William.d.zk
 */
public class WsContext
        extends ZContext
{
    public final static int HS_State_GET          = 1;
    public final static int HS_State_HOST         = 1 << 1;
    public final static int HS_State_UPGRADE      = 1 << 2;
    public final static int HS_State_CONNECTION   = 1 << 3;
    public final static int HS_State_SEC_KEY      = 1 << 4;
    public final static int HS_State_ORIGIN       = 1 << 5;
    public final static int HS_State_SEC_PROTOCOL = 1 << 6;
    // public final String mSecProtocol, mSubProtocol; //not support right now
    public final static int HS_State_SEC_VERSION  = 1 << 7;
    public final static int HS_State_HTTP_101     = 1 << 8;
    public final static int HS_State_SEC_ACCEPT   = 1 << 9;
    public final static int HS_State_ACCEPT_OK    = HS_State_HTTP_101
                                                    | HS_State_SEC_ACCEPT
                                                    | HS_State_UPGRADE
                                                    | HS_State_CONNECTION;
    public final static int HS_State_CLIENT_OK    = HS_State_GET
                                                    | HS_State_HOST
                                                    | HS_State_UPGRADE
                                                    | HS_State_CONNECTION
                                                    | HS_State_SEC_KEY
                                                    | HS_State_SEC_VERSION;
    private final String    _SecKey, _SecAcceptExpect;
    private final int       _MaxPayloadSize;
    private int             mHandshakeState;
    private WsHandshake     mHandshake;

    public WsContext(ISessionOption option, ISort<ZContext> sort)
    {
        super(option, sort);
        _MaxPayloadSize = option.getSnfInByte() - 2;
        if (sort.getType().equals(ISort.Type.CONSUMER))
        {
            Random r    = new Random(System.nanoTime());
            byte[] seed = new byte[17];
            r.nextBytes(seed);
            _SecKey = Base64.getEncoder().encodeToString(getCryptUtil().sha1(seed));
            _SecAcceptExpect = getSecAccept(_SecKey);
        }
        else
        {
            _SecKey = _SecAcceptExpect = null;
        }

        switch (sort.getMode())
        {
            case CLUSTER:
                transfer();
                break;
            case LINK:
                handshake();
                break;
            default:
                break;
        }
    }

    public WsHandshake getHandshake()
    {
        return mHandshake;
    }

    public void setHandshake(WsHandshake handshake)
    {
        mHandshake = handshake;
    }

    public final int getMaxPayloadSize()
    {
        return _MaxPayloadSize;
    }

    @Override
    public void close() throws IOException
    {
        super.close();
    }

    @Override
    public void reset()
    {
        if (mHandshake != null)
        {
            mHandshake.dispose();
        }
        mHandshake = null;
        super.reset();
    }

    @Override
    public void dispose()
    {
        mHandshake = null;
        super.dispose();
    }

    @Override
    public void finish()
    {
        super.finish();
        mHandshake = null;
    }

    public String getSecAccept(String secKey)
    {
        return Base64.getEncoder()
                     .encodeToString(getCryptUtil().sha1((secKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes()));
    }

    public String getSeKey()
    {
        return _SecKey;
    }

    public final void updateHandshakeState(int state)
    {
        mHandshakeState |= state;
    }

    public final boolean checkState(int state)
    {
        return mHandshakeState == state || (mHandshakeState & state) == state;
    }

    public final int getWsVersion()
    {
        return 13;
    }

    public String getSecAcceptExpect()
    {
        return _SecAcceptExpect;
    }
}