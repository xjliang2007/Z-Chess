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

package com.tgx.chess.queen.io.core.inf;

import java.io.Closeable;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Queue;

import com.tgx.chess.king.base.inf.IDisposable;
import com.tgx.chess.king.base.inf.IReset;

/**
 * @author William.d.zk
 */
public interface ISession<C extends IContext<C>>
        extends
        Queue<IPacket>,
        IReset,
        Closeable,
        IDisposable,
        IAddress,
        IValid,
        IReadable<ISession<C>>,
        IWritable<ISession<C>>
{
    long DEFAULT_INDEX = -1;

    long getIndex();

    void setIndex(long _Index);

    long getHashKey();

    AsynchronousSocketChannel getChannel();

    C getContext();

    ISessionDismiss<C> getDismissCallback();

    long[] getPortChannels();

    void bindPort2Channel(long portId);

    boolean isClosed();

    default int getHaIndex()
    {
        return 0;
    }

    default int getPortIndex()
    {
        return 0;
    }
}
