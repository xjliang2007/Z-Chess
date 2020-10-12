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

package com.isahl.chess.queen.event.operator;

import java.util.Objects;

import com.isahl.chess.king.base.inf.ITriple;
import com.isahl.chess.king.base.log.Logger;
import com.isahl.chess.king.base.util.Triple;
import com.isahl.chess.queen.io.core.inf.IContext;
import com.isahl.chess.queen.io.core.inf.IControl;
import com.isahl.chess.queen.io.core.inf.IPacket;
import com.isahl.chess.queen.io.core.inf.ISession;
import com.isahl.chess.queen.io.core.inf.IPipeEncoder;

/**
 * @author william.d.zk
 * 
 * @date 2019-05-08
 */
public class PipeEncoder<C extends IContext<C>>
        implements
        IPipeEncoder<C>
{
    private final Logger       _Logger = Logger.getLogger("io.queen.operator." + getClass().getSimpleName());
    private final AioWriter<C> _AioWriter;

    public PipeEncoder(AioWriter<C> aioWriter)
    {
        _AioWriter = aioWriter;
    }

    @Override
    public ITriple handle(IControl<C> command, ISession<C> session)
    {
        try
        {
            IPacket send = filterWrite(command, session.getContext());
            Objects.requireNonNull(send);
            _Logger.debug("%s ", command);
            session.write(send, _AioWriter);
        }
        catch (Exception e)
        {
            return new Triple<>(e, session, session.getContext().getSort().getError());
        }
        return null;
    }

    @Override
    public String getName()
    {
        return "operator.pipe.encoder";
    }
}