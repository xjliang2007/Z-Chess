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

package com.tgx.chess.queen.event.handler;

import static com.tgx.chess.queen.event.inf.IError.Type.HANDLE_DATA;
import static com.tgx.chess.queen.event.inf.IError.Type.ILLEGAL_STATE;
import static com.tgx.chess.queen.event.inf.IOperator.Type.WRITE;
import static com.tgx.chess.queen.event.inf.IOperator.Type.WROTE;

import java.util.List;

import com.lmax.disruptor.RingBuffer;
import com.tgx.chess.king.base.inf.IPair;
import com.tgx.chess.king.base.inf.ITriple;
import com.tgx.chess.king.base.log.Logger;
import com.tgx.chess.king.base.util.Pair;
import com.tgx.chess.queen.event.inf.IOperator;
import com.tgx.chess.queen.event.inf.IPipeEventHandler;
import com.tgx.chess.queen.event.processor.QEvent;
import com.tgx.chess.queen.io.core.inf.IContext;
import com.tgx.chess.queen.io.core.inf.IControl;
import com.tgx.chess.queen.io.core.inf.ISession;

/**
 * @author william.d.zk
 */
public class WriteDispatcher<C extends IContext<C>>
        implements
        IPipeEventHandler<QEvent>
{
    private final RingBuffer<QEvent>[] _Encoders;
    private final RingBuffer<QEvent>   _Error;
    private final int                  _Mask;
    private final Logger               _Logger = Logger.getLogger("io.queen.dispatcher." + getClass().getSimpleName());

    @SafeVarargs
    public WriteDispatcher(RingBuffer<QEvent> error,
                           RingBuffer<QEvent>... workers)
    {
        _Error = error;
        _Encoders = workers;
        _Mask = _Encoders.length - 1;
        if (Integer.bitCount(_Encoders.length) != 1) {
            throw new IllegalArgumentException("workers' length must be a power of 2");
        }
    }

    private RingBuffer<QEvent> dispatchEncoder(long seq)
    {
        return _Encoders[(int) (seq & _Mask)];
    }

    @Override
    public void onEvent(QEvent event, long sequence, boolean endOfBatch) throws Exception
    {
        if (event.hasError()) {
            if (event.getErrorType() == HANDLE_DATA) {// from logic handler 
                /* logic 处理错误，转换为shutdown目标投递给 _Error
                 交由 IoDispatcher转发给对应的MappingHandler 执行close 
                */
                error(_Error, HANDLE_DATA, event.getContent(), event.getEventOp());
            }
        }
        else switch (event.getEventType())
        {
            case NULL://在前一个处理器event.reset().
            case IGNORE://没有任何时间需要跨 Barrier 投递向下一层 Pipeline
                break;
            case BIZ_LOCAL://from biz local
            case CLUSTER_LOCAL://from cluster local
            case WRITE://from LinkIo/Cluster
            case LOGIC://from read->logic
                IPair writeContent = event.getContent();
                IControl<C>[] commands = writeContent.getFirst();
                ISession<C> session = writeContent.getSecond();
                IOperator<IControl<C>[],
                          ISession<C>,
                          List<ITriple>> transferOperator = event.getEventOp();
                if (commands != null && commands.length > 0) {
                    List<ITriple> triples = transferOperator.handle(commands, session);
                    for (ITriple triple : triples) {
                        ISession<C> targetSession = triple.getSecond();
                        IControl<C> content = triple.getFirst();
                        _Logger.debug("write %s", content);
                        if (content.isShutdown()) {
                            if (targetSession.isValid()) {
                                error(_Error,
                                      ILLEGAL_STATE,
                                      new Pair<>(new IllegalStateException("session to shutdown"), targetSession),
                                      targetSession.getContext()
                                                   .getSort()
                                                   .getError());
                            }
                        }
                        else tryPublish(dispatchEncoder(targetSession.getHashKey()),
                                        WRITE,
                                        new Pair<>(content, targetSession),
                                        triple.getThird());
                    }
                    _Logger.debug("write_dispatcher, source %s, transfer:%d", event.getEventType(), commands.length);
                }
                break;
            case WROTE://from io-wrote
                IPair wroteContent = event.getContent();
                int wroteCount = wroteContent.getFirst();
                session = wroteContent.getSecond();
                if (session.isValid()) {
                    tryPublish(dispatchEncoder(session.getHashKey()),
                               WROTE,
                               new Pair<>(wroteCount, session),
                               event.getEventOp());
                }
                break;
            case DISPATCH:
                List<ITriple> writeContents = event.getContentList();
                for (ITriple content : writeContents) {
                    IControl<C> command = content.getFirst();
                    session = content.getSecond();
                    if (command.isShutdown()) {
                        if (session != null && session.isValid()) {
                            error(_Error,
                                  ILLEGAL_STATE,
                                  new Pair<>(new IllegalStateException("session to shutdown"), session),
                                  session.getContext()
                                         .getSort()
                                         .getError());
                        }
                        /*
                        session == null 意味着 _SessionManager.find..失败
                        !session.isValid 意味着 session 已经被关闭
                        这两种情形都忽略执行即可 
                         */
                        else {
                            _Logger.warning("dispatch failed [ %s ]", session);
                        }
                    }
                    else tryPublish(dispatchEncoder(session.getHashKey()),
                                    WRITE,
                                    new Pair<>(command, session),
                                    content.getThird());
                }
            default:
                break;
        }
        event.reset();
    }

    @Override
    public Logger getLogger()
    {
        return _Logger;
    }
}
