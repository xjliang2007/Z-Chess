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
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tgx.chess.queen.event.handler.cluster;

import static com.tgx.chess.queen.event.inf.IOperator.Type.CLUSTER;
import static com.tgx.chess.queen.event.inf.IOperator.Type.DISPATCH;
import static com.tgx.chess.queen.event.inf.IOperator.Type.LOGIC;

import java.util.Objects;

import com.lmax.disruptor.RingBuffer;
import com.tgx.chess.king.base.inf.IPair;
import com.tgx.chess.king.base.inf.ITriple;
import com.tgx.chess.king.base.log.Logger;
import com.tgx.chess.king.base.util.Pair;
import com.tgx.chess.queen.event.inf.IOperator;
import com.tgx.chess.queen.event.inf.IPipeEventHandler;
import com.tgx.chess.queen.event.inf.ISort;
import com.tgx.chess.queen.event.processor.QEvent;
import com.tgx.chess.queen.io.core.inf.IContext;
import com.tgx.chess.queen.io.core.inf.IControl;
import com.tgx.chess.queen.io.core.inf.ISession;

/**
 * @author william.d.zk
 */
public class DecodedDispatcher<C extends IContext<C>>
        implements
        IPipeEventHandler<QEvent>
{
    private final Logger               _Logger = Logger.getLogger("io.queen.dispatcher." + getClass().getSimpleName());
    private final RingBuffer<QEvent>   _Cluster;
    private final RingBuffer<QEvent>   _Error;
    private final RingBuffer<QEvent>[] _LogicWorkers;
    private final int                  _WorkerMask;

    @SafeVarargs
    public DecodedDispatcher(RingBuffer<QEvent> cluster,
                             RingBuffer<QEvent> error,
                             RingBuffer<QEvent>... workers)
    {
        _Cluster = cluster;
        _Error = error;
        _LogicWorkers = workers;
        _WorkerMask = _LogicWorkers.length - 1;
    }

    @Override
    public void onEvent(QEvent event, long sequence, boolean endOfBatch) throws Exception
    {
        if (event.hasError()) {//错误处理
            IPair dispatchError = event.getContent();
            ISession<C> session = dispatchError.getSecond();
            if (!session.isClosed()) {
                error(_Error, event.getErrorType(), dispatchError, event.getEventOp());
            }
        }
        else {
            if (event.getEventType() == DISPATCH) {
                IPair dispatchContent = event.getContent();
                ISession<C> session = dispatchContent.getSecond();
                IControl<C>[] commands = dispatchContent.getFirst();
                if (Objects.nonNull(commands)) {
                    for (IControl<C> cmd : commands) {
                        //dispatch 到对应的 处理器里
                        dispatch(session.getContext()
                                        .getSort(),
                                 cmd,
                                 session,
                                 event.getEventOp());
                    }
                }
            }
            else {
                _Logger.warning("decoded dispatcher event type error: %s",
                                event.getEventType()
                                     .name());
            }
        }
        event.reset();
    }

    private void dispatch(ISort<C> sorter,
                          IControl<C> cmd,
                          ISession<C> session,
                          IOperator<IControl<C>,
                                    ISession<C>,
                                    ITriple> op)
    {
        cmd.setSession(session);
        IPair nextPipe = getNextPipe(sorter.getMode(), cmd);
        publish(nextPipe.getFirst(), nextPipe.getSecond(), new Pair<>(cmd, session), op);
    }

    protected IPair getNextPipe(ISort.Mode mode, IControl<C> cmd)
    {
        _Logger.debug("decoded: %s | %s", cmd, mode);
        if (mode == ISort.Mode.CLUSTER && cmd.isMapping()) { return new Pair<>(_Cluster, CLUSTER); }
        return new Pair<>(dispatchWorker(cmd), LOGIC);
    }

    protected RingBuffer<QEvent> dispatchWorker(IControl<C> cmd)
    {
        return _LogicWorkers[(int) cmd.getSession()
                                      .getHashKey()
                             & _WorkerMask];
    }

    @Override
    public Logger getLogger()
    {
        return _Logger;
    }
}
