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

package com.tgx.chess.bishop.biz.device;

import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;
import java.util.stream.Collectors;

import com.lmax.disruptor.RingBuffer;
import com.tgx.chess.bishop.biz.db.dao.DeviceEntry;
import com.tgx.chess.bishop.io.ws.bean.WsContext;
import com.tgx.chess.bishop.io.zfilter.ZContext;
import com.tgx.chess.king.base.inf.ITriple;
import com.tgx.chess.king.base.log.Logger;
import com.tgx.chess.king.config.Config;
import com.tgx.chess.queen.config.QueenCode;
import com.tgx.chess.queen.db.inf.IRepository;
import com.tgx.chess.queen.event.inf.ISort;
import com.tgx.chess.queen.event.processor.QEvent;
import com.tgx.chess.queen.io.core.async.AioCreator;
import com.tgx.chess.queen.io.core.async.AioSession;
import com.tgx.chess.queen.io.core.async.BaseAioServer;
import com.tgx.chess.queen.io.core.executor.ServerCore;
import com.tgx.chess.queen.io.core.inf.IAioServer;
import com.tgx.chess.queen.io.core.inf.ICommandCreator;
import com.tgx.chess.queen.io.core.inf.IConnectActivity;
import com.tgx.chess.queen.io.core.inf.IControl;
import com.tgx.chess.queen.io.core.inf.ISession;
import com.tgx.chess.queen.io.core.inf.ISessionCreated;
import com.tgx.chess.queen.io.core.inf.ISessionCreator;
import com.tgx.chess.queen.io.core.inf.ISessionDismiss;
import com.tgx.chess.queen.io.core.inf.ISessionOption;
import com.tgx.chess.queen.io.core.manager.QueenManager;

/**
 * @author william.d.zk
 * @date 2019-05-12
 */
public abstract class BaseDeviceNode
        extends
        QueenManager<ZContext>
        implements
        ISessionDismiss<ZContext>,
        ISessionCreated<ZContext>
{

    final Logger                     _Logger = Logger.getLogger(getClass().getName());
    final List<IAioServer<ZContext>> _AioServers;
    final IRepository<DeviceEntry>   _DeviceRepository;

    @Override
    public void onDismiss(ISession<ZContext> session)
    {
        rmSession(session);
    }

    @Override
    public void onCreate(ISession<ZContext> session)
    {
        /* 进入这里的都是 _AioServer 建立的链接*/
        session.setIndex(QueenCode.CM_XID);
        addSession(session);
    }

    BaseDeviceNode(List<ITriple> hosts,
                   IRepository<DeviceEntry> repository)
    {
        super(new Config("device"), new ServerCore<ZContext>()
        {

            @Override
            public RingBuffer<QEvent> getLocalPublisher(ISession<ZContext> session)
            {
                switch (getSlot(session))
                {
                    case QueenCode.CM_XID_LOW:
                    case QueenCode.RM_XID_LOW:
                        return getClusterLocalSendEvent();
                    default:
                        return getBizLocalSendEvent();
                }
            }

            @Override
            public RingBuffer<QEvent> getLocalCloser(ISession<ZContext> session)
            {
                switch (getSlot(session))
                {
                    case QueenCode.CM_XID_LOW:
                    case QueenCode.RM_XID_LOW:
                        return getClusterLocalCloseEvent();
                    default:
                        return getBizLocalCloseEvent();
                }
            }
        });

        _AioServers = hosts.stream()
                           .map(triple ->
                           {
                               String host = triple.first();
                               int port = triple.second();
                               ISort sort = triple.third();
                               ISessionCreator<ZContext> sessionCreator = new AioCreator<ZContext>(getConfig())
                               {
                                   @Override
                                   public ISession<ZContext> createSession(AsynchronousSocketChannel socketChannel,
                                                                           IConnectActivity<ZContext> activity)
                                   {
                                       return null;
                                   }

                                   @Override
                                   public ZContext createContext(ISessionOption option, ISort sort)
                                   {

                                       return new WsContext(option, sort);
                                   }
                               };

                           })
                           .collect(Collectors.toList());

        _SessionCreator = new AioCreator<C>(getConfig())
        {

            @Override
            public ISession<C> createSession(AsynchronousSocketChannel socketChannel, IConnectionContext<C> context)
            {
                try {
                    return new AioSession<>(socketChannel,
                                            context.getConnectActivity(),
                                            this,
                                            this,
                                            BaseDeviceNode.this);
                }
                catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public C createContext(ISessionOption option, ISort sort)
            {
                return BaseDeviceNode.this.createContext(option, sort);
            }
        };
        _CommandCreator = (session) -> null;
        _AioServer = new BaseAioServer<C>(_ServerHost, _ServerPort, _Sort.getEncoder(), _Sort.getDecoder())
        {

            @Override
            public ISort<C> getSort()
            {
                return _Sort;
            }

            @Override
            public ISessionCreator<C> getSessionCreator()
            {
                return _SessionCreator;
            }

            @Override
            public ISessionCreated<C> getSessionCreated()
            {
                return BaseDeviceNode.this;
            }

            @Override
            public ICommandCreator<C> getCommandCreator()
            {
                return _CommandCreator;
            }

        };
        _DeviceRepository = repository;
        _Logger.info("Device Node Bean Load");
    }

    @SafeVarargs
    public final void localBizSend(long deviceId, IControl<C>... toSends)
    {
        localSend(findSessionByIndex(deviceId), _Sort.getTransfer(), toSends);
    }

    public void localBizClose(long deviceId)
    {
        localClose(findSessionByIndex(deviceId), _Sort.getCloser());
    }
}
