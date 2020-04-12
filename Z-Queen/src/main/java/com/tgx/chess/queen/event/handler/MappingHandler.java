/*
 * MIT License                                                                    
 *                                                                                
 * Copyright (c) 2016~2020 Z-Chess                                                
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

import static com.tgx.chess.queen.event.inf.IError.Type.MAPPING_ERROR;
import static com.tgx.chess.queen.event.inf.IError.Type.MAPPING_LOGIN_ERROR;
import static com.tgx.chess.queen.event.inf.IOperator.Type.CONSENSUS;
import static com.tgx.chess.queen.event.inf.IOperator.Type.WRITE;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;

import com.lmax.disruptor.RingBuffer;
import com.tgx.chess.king.base.exception.LinkRejectException;
import com.tgx.chess.king.base.inf.IPair;
import com.tgx.chess.king.base.inf.ITriple;
import com.tgx.chess.king.base.log.Logger;
import com.tgx.chess.king.base.util.Pair;
import com.tgx.chess.queen.db.inf.IStorage;
import com.tgx.chess.queen.event.inf.ICustomLogic;
import com.tgx.chess.queen.event.inf.IOperator;
import com.tgx.chess.queen.event.inf.IPipeEventHandler;
import com.tgx.chess.queen.event.processor.QEvent;
import com.tgx.chess.queen.io.core.inf.IAioConnector;
import com.tgx.chess.queen.io.core.inf.IAioServer;
import com.tgx.chess.queen.io.core.inf.IConnectActivity;
import com.tgx.chess.queen.io.core.inf.IContext;
import com.tgx.chess.queen.io.core.inf.IControl;
import com.tgx.chess.queen.io.core.inf.ISession;
import com.tgx.chess.queen.io.core.inf.ISessionDismiss;
import com.tgx.chess.queen.io.core.manager.QueenManager;

/**
 * @author william.d.zk
 * @date 2020/2/15
 */
public class MappingHandler<C extends IContext<C>,
                            T extends IStorage>
        implements
        IPipeEventHandler<QEvent>
{
    private final Logger             _Logger;
    private final RingBuffer<QEvent> _Error;
    private final RingBuffer<QEvent> _Writer;
    private final RingBuffer<QEvent> _Transfer;
    private final QueenManager<C>    _QueenManager;
    private final ICustomLogic<C,
                               T>    _CustomLogic;

    public MappingHandler(String mapper,
                          QueenManager<C> manager,
                          RingBuffer<QEvent> error,
                          RingBuffer<QEvent> writer,
                          RingBuffer<QEvent> transfer,
                          ICustomLogic<C,
                                       T> customLogic)
    {
        _Logger = Logger.getLogger(mapper);
        _QueenManager = manager;
        _Writer = writer;
        _Error = error;
        _Transfer = transfer;
        _CustomLogic = customLogic;
    }

    @Override
    public void onEvent(QEvent event, long sequence, boolean endOfBatch)
    {
        if (event.hasError()) {
            _Logger.info(String.format("error type %s,ignore ", event.getErrorType()));
            switch (event.getErrorType())
            {
                case ACCEPT_FAILED:
                    IOperator<Throwable,
                              IAioServer<C>,
                              Void> acceptFailedOperator = event.getEventOp();
                    IPair errorContent = event.getContent();
                    acceptFailedOperator.handle(errorContent.getFirst(), errorContent.getSecond());
                    break;
                case CONNECT_FAILED:
                    IOperator<Throwable,
                              IAioConnector<C>,
                              Void> connectFailedOperator = event.getEventOp();
                    errorContent = event.getContent();
                    connectFailedOperator.handle(errorContent.getFirst(), errorContent.getSecond());
                    break;
                case WAIT_CLOSE:
                    _Logger.warning("server io error , do close session");
                    IOperator<Void,
                              ISession<C>,
                              Void> closeOperator = event.getEventOp();
                    errorContent = event.getContent();
                    ISession<C> session = errorContent.getSecond();
                    ISessionDismiss<C> dismiss = session.getDismissCallback();
                    boolean closed = session.isClosed();
                    closeOperator.handle(null, session);
                    if (!closed) {
                        dismiss.onDismiss(session);
                    }
                    break;
                default:
                    _Logger.warning("can't handle %s",
                                    event.getErrorType()
                                         .name());
                    break;
            }
        }
        else {
            switch (event.getEventType())
            {
                case CONNECTED:
                    try {
                        IPair connectedContent = event.getContent();
                        AsynchronousSocketChannel channel = connectedContent.getSecond();
                        IConnectActivity<C> connectActivity = connectedContent.getFirst();
                        IOperator<IConnectActivity<C>,
                                  AsynchronousSocketChannel,
                                  ITriple> connectedOperator = event.getEventOp();
                        ITriple connectedHandled = connectedOperator.handle(connectActivity, channel);
                        /*connectedHandled 不可能为 null*/
                        ISession<C> session = connectedHandled.getSecond();
                        IControl<C>[] toSends = connectedHandled.getFirst();
                        if (toSends != null) {
                            publish(_Writer,
                                    WRITE,
                                    new Pair<>(toSends, session),
                                    session.getContext()
                                           .getSort()
                                           .getTransfer());
                        }
                    }
                    catch (Exception e) {
                        _Logger.fetal("link create session failed", e);
                    }
                    break;
                case LINK:
                case CLUSTER:
                    IControl<C> received = event.getContent()
                                                .getFirst();
                    ISession<C> session = event.getContent()
                                               .getSecond();
                    if (received != null) {
                        try {
                            IControl<C>[] toSends = _CustomLogic.handle(_QueenManager, session, received);
                            if (toSends != null && toSends.length > 0) {
                                publish(_Writer,
                                        WRITE,
                                        new Pair<>(toSends, session),
                                        session.getContext()
                                               .getSort()
                                               .getTransfer());
                            }
                            if (received.isConsensus()) {
                                publish(_Transfer, CONSENSUS, new Pair<>(received, session), null);
                            }
                        }
                        catch (LinkRejectException e) {
                            _Logger.warning("mapping handler reject", e);
                            error(_Error,
                                  MAPPING_LOGIN_ERROR,
                                  new Pair<>(e, session),
                                  session.getContext()
                                         .getSort()
                                         .getError());
                        }
                        catch (Exception e) {
                            _Logger.warning("link mapping handler error", e);
                            error(_Error,
                                  MAPPING_ERROR,
                                  new Pair<>(e, session),
                                  session.getContext()
                                         .getSort()
                                         .getError());
                        }
                        break;
                    }
                    break;
                case CONSENSUS:
                    received = event.getContent()
                                    .getFirst();
                    session = event.getContent()
                                   .getSecond();
                    try {
                        List<ITriple> result = _CustomLogic.consensus(_QueenManager, received, session);
                        if (result != null) {
                            publish(_Writer, result);
                        }
                    }
                    catch (Exception e) {
                        _Logger.warning("mapping consensus error", e);
                        error(_Error,
                              MAPPING_ERROR,
                              new Pair<>(e, session),
                              session.getContext()
                                     .getSort()
                                     .getError());
                    }
                    break;
                case TIMER://ClusterConsumer Timeout->start_vote
                    /*TIMER 必然是单个IControl,通过前项RingBuffer 向MappingHandler 投递*/
                    T content = event.getContent()
                                     .getFirst();
                    List<ITriple> toSends = _CustomLogic.onTransfer(_QueenManager, content);
                    if (toSends != null && !toSends.isEmpty()) {
                        publish(_Writer, toSends);
                    }
                    break;
                default:
                    _Logger.warning("mapping handler error %s",
                                    event.getEventType()
                                         .name());
                    break;
            }
        }
        event.reset();
    }
}
