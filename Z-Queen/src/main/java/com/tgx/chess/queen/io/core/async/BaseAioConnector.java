/*
 * MIT License
 *
 * Copyright (c) 2016~2020. Z-Chess
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

package com.tgx.chess.queen.io.core.async;

import static com.tgx.chess.king.base.schedule.Status.STOP;
import static com.tgx.chess.king.base.schedule.Status.RUNNING;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

import com.tgx.chess.king.base.inf.IFailed;
import com.tgx.chess.king.base.inf.ITriple;
import com.tgx.chess.king.base.schedule.inf.ITask;
import com.tgx.chess.queen.config.ISocketConfig;
import com.tgx.chess.queen.event.inf.IOperator;
import com.tgx.chess.queen.event.operator.ConnectFailedOperator;
import com.tgx.chess.queen.event.operator.ConnectedOperator;
import com.tgx.chess.queen.io.core.inf.IAioConnector;
import com.tgx.chess.queen.io.core.inf.IConnectActivity;
import com.tgx.chess.queen.io.core.inf.IContext;

/**
 * @author william.d.zk
 */
public abstract class BaseAioConnector<C extends IContext<C>>
        extends
        AioCreator<C>
        implements
        IAioConnector<C>
{

    protected BaseAioConnector(String host,
                               int port,
                               ISocketConfig socketConfig,
                               IFailed<IAioConnector<C>> failed)
    {
        super(socketConfig);
        _RemoteAddress = new InetSocketAddress(host, port);
        _FailedHandler = failed;
    }

    private final ConnectFailedOperator<C>  _ConnectFailedOperator = new ConnectFailedOperator<>();
    private final ConnectedOperator<C>      _ConnectedOperator     = new ConnectedOperator<>();
    private final InetSocketAddress         _RemoteAddress;
    private final AtomicInteger             _State                 = new AtomicInteger(ITask.ctlOf(RUNNING.getCode(),
                                                                                                   0));
    private final IFailed<IAioConnector<C>> _FailedHandler;

    private InetSocketAddress mLocalBind;

    @Override
    public void error()
    {
        _FailedHandler.onFailed(this);
    }

    /**
     * 在执行retry操作之前需要先切换到stop状态
     */
    @Override
    public void retry()
    {
        retry:
        for (;;) {
            int c = _State.get();
            int rs = ITask.runStateOf(c);
            if (rs >= STOP.getCode()) { return; }
            for (;;) {
                int rc = ITask.retryCountOf(c);
                if (rc >= RETRY_LIMIT) { return; }
                if (ITask.compareAndIncrementRetry(_State, rc)) { return; }
                c = _State.get();// reload 
                if (ITask.runStateOf(c) != rs) {
                    continue retry;
                }
            }
        }
    }

    @Override
    public InetSocketAddress getRemoteAddress()
    {
        return _RemoteAddress;
    }

    @Override
    public InetSocketAddress getLocalAddress()
    {
        return mLocalBind;
    }

    @Override
    public void setLocalAddress(InetSocketAddress address)
    {
        mLocalBind = address;
    }

    @Override
    public IOperator<IConnectActivity<C>,
                     AsynchronousSocketChannel,
                     ITriple> getConnectedOperator()
    {
        return _ConnectedOperator;
    }

    @Override
    public IOperator<Throwable,
                     IAioConnector<C>,
                     Void> getErrorOperator()
    {
        return _ConnectFailedOperator;
    }

    @Override
    public void shutdown()
    {
        ITask.advanceRunState(_State, STOP.getCode());
    }

    @Override
    public boolean isShutdown()
    {
        return ITask.runStateOf(_State.get()) >= STOP.getCode();
    }

    @Override
    public boolean isRunning()
    {
        return ITask.runStateOf(_State.get()) < STOP.getCode();
    }

    @Override
    public boolean isValid()
    {
        return !isShutdown();
    }
}
