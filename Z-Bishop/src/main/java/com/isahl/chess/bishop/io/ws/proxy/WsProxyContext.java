/*
 * MIT License
 *
 * Copyright (c) 2016~2021. Z-Chess
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

package com.isahl.chess.bishop.io.ws.proxy;

import com.isahl.chess.bishop.io.ws.WsContext;
import com.isahl.chess.queen.io.core.features.model.channels.INetworkOption;
import com.isahl.chess.queen.io.core.features.model.content.IPacket;
import com.isahl.chess.queen.io.core.features.model.session.ISort;
import com.isahl.chess.queen.io.core.features.model.session.proxy.IPContext;
import com.isahl.chess.queen.io.core.features.model.session.proxy.IProxyContext;
import com.isahl.chess.queen.io.core.net.socket.AioPacket;

/**
 * @author william.d.zk
 */
public class WsProxyContext<A extends IPContext>
        extends WsContext
        implements IProxyContext<A>
{

    private final A       _ActingContext;
    private       IPacket mTransfer;

    public WsProxyContext(INetworkOption option, ISort.Mode mode, ISort.Type type, A actingContext)
    {
        super(option, mode, type);
        _ActingContext = actingContext;
    }

    @Override
    public A getActingContext()
    {
        return _ActingContext;
    }

    @Override
    public boolean isProxy()
    {
        return true;
    }

    @Override
    public void ready()
    {
        super.ready();
        _ActingContext.ready();
    }

    /*这是一个协议性代理上下文特有的处理机制，粘包时，这个操作异常重要*/
    public IPacket append(IPacket packet)
    {
        if(mTransfer == null) {
            mTransfer = new AioPacket(1 << 12, false);
        }
        if(mTransfer.getBuffer()
                    .capacity() < mTransfer.getBuffer()
                                           .position() + packet.getBuffer()
                                                               .limit())
        {
            AioPacket newBuf = new AioPacket(mTransfer.getBuffer()
                                                      .capacity() * 2 + packet.getBuffer()
                                                                              .limit(), false);
            newBuf.getBuffer()
                  .put(mTransfer.getBuffer()
                                .array())
                  .put(packet.getBuffer()
                             .array());
            mTransfer = newBuf;
        }
        else {
            mTransfer.getBuffer()
                     .put(packet.getBuffer()
                                .array());
        }
        return mTransfer;
    }
}
