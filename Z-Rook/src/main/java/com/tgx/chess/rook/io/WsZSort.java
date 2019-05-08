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

package com.tgx.chess.rook.io;

import com.tgx.chess.bishop.io.ws.bean.WsContext;
import com.tgx.chess.bishop.io.ws.filter.WsControlFilter;
import com.tgx.chess.bishop.io.ws.filter.WsFrameFilter;
import com.tgx.chess.bishop.io.ws.filter.WsHandShakeFilter;
import com.tgx.chess.bishop.io.zfilter.ZCommandFilter;
import com.tgx.chess.bishop.io.zfilter.ZTlsFilter;
import com.tgx.chess.bishop.io.zprotocol.ZConsumerFactory;
import com.tgx.chess.king.base.log.Logger;
import com.tgx.chess.queen.event.inf.ISort;
import com.tgx.chess.queen.event.operator.AioWriter;
import com.tgx.chess.queen.event.operator.CloseOperator;
import com.tgx.chess.queen.event.operator.ErrorOperator;
import com.tgx.chess.queen.event.operator.PipeDecoder;
import com.tgx.chess.queen.event.operator.PipeEncoder;
import com.tgx.chess.queen.event.operator.TransferOperator;
import com.tgx.chess.queen.io.core.inf.IFilterChain;
import com.tgx.chess.queen.io.core.inf.IPipeDecoder;
import com.tgx.chess.queen.io.core.inf.IPipeEncoder;

/**
 * @author william.d.zk
 */
@SuppressWarnings("unchecked")
public enum WsZSort
        implements
        ISort
{
    /**
     * 
     */

    CONSUMER,
    CONSUMER_SSL
    {
        @Override
        public boolean isSSL()
        {
            return true;
        }
    };

    @Override
    public Mode getMode()
    {
        return Mode.LINK;
    }

    @Override
    public Type getType()
    {
        return Type.CONSUMER;
    }

    private static final Logger            _Log             = Logger.getLogger(WsZSort.class.getName());
    private final CloseOperator<WsContext> _CloseOperator   = new CloseOperator<>();
    private final ErrorOperator<WsContext> _ErrorOperator   = new ErrorOperator<>(_CloseOperator);
    private final AioWriter<WsContext>     _AioWriter       = new AioWriter<>();
    final WsHandShakeFilter<WsContext>     _HandshakeFilter = new WsHandShakeFilter(this);
    {
        IFilterChain<WsContext> header = new ZTlsFilter<>();
        _HandshakeFilter.linkAfter(header)
                        .linkFront(new WsFrameFilter<>())
                        .linkFront(new ZCommandFilter<>(new ZConsumerFactory<WsContext>()
                        {
                        }))
                        .linkFront(new WsControlFilter<>());
    }

    private final IPipeEncoder<WsContext> _ConsumerEncoder = new PipeEncoder<>(getFilterChain(),
                                                                               _ErrorOperator,
                                                                               _AioWriter);

    private final TransferOperator<WsContext> _ConsumerTransfer = new TransferOperator<>(_ConsumerEncoder);
    private final IPipeDecoder<WsContext>     _ConsumerDecoder  = new PipeDecoder<>(getFilterChain(),
                                                                                    _ConsumerTransfer);

    public CloseOperator<WsContext> getCloseOperator()
    {
        return _CloseOperator;
    }

    public IPipeDecoder<WsContext> getConsumerDecoder()
    {
        return _ConsumerDecoder;
    }

    public IPipeEncoder<WsContext> getConsumerEncoder()
    {
        return _ConsumerEncoder;
    }

    public TransferOperator<WsContext> getTransfer()
    {
        return _ConsumerTransfer;
    }

    IFilterChain<WsContext> getFilterChain()
    {
        return _HandshakeFilter;
    }
}
