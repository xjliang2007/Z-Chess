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
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.lmax.disruptor.RingBuffer;
import com.tgx.chess.bishop.biz.db.dao.DeviceEntry;
import com.tgx.chess.bishop.io.mqtt.control.X111_QttConnect;
import com.tgx.chess.bishop.io.mqtt.control.X112_QttConnack;
import com.tgx.chess.bishop.io.ws.control.X101_HandShake;
import com.tgx.chess.bishop.io.ws.control.X103_Close;
import com.tgx.chess.bishop.io.ws.control.X104_Ping;
import com.tgx.chess.bishop.io.ws.control.X105_Pong;
import com.tgx.chess.bishop.io.zcrypt.EncryptHandler;
import com.tgx.chess.bishop.io.zfilter.ZContext;
import com.tgx.chess.bishop.io.zhandler.ZLinkedControl;
import com.tgx.chess.bishop.io.zprotocol.device.X20_SignUp;
import com.tgx.chess.bishop.io.zprotocol.device.X21_SignUpResult;
import com.tgx.chess.bishop.io.zprotocol.device.X22_SignIn;
import com.tgx.chess.bishop.io.zprotocol.device.X23_SignInResult;
import com.tgx.chess.bishop.io.zprotocol.device.X30_EventMsg;
import com.tgx.chess.bishop.io.zprotocol.device.X31_ConfirmMsg;
import com.tgx.chess.bishop.io.zprotocol.device.X32_MsgStatus;
import com.tgx.chess.bishop.io.zprotocol.device.X50_DeviceMsg;
import com.tgx.chess.bishop.io.zprotocol.device.X51_DeviceMsgAck;
import com.tgx.chess.king.base.inf.ITriple;
import com.tgx.chess.king.base.log.Logger;
import com.tgx.chess.king.base.util.IoUtil;
import com.tgx.chess.king.config.Config;
import com.tgx.chess.queen.config.QueenCode;
import com.tgx.chess.queen.db.inf.IRepository;
import com.tgx.chess.queen.event.handler.LogicHandler;
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
public class DeviceNode
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

    DeviceNode(List<ITriple> hosts,
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
                               final String _Host = triple.first();
                               final int _Port = triple.second();
                               final ISort<ZContext> _Sort = triple.third();
                               final ISessionCreator<ZContext> _SessionCreator = new AioCreator<ZContext>(getConfig())
                               {
                                   @Override
                                   public ISession<ZContext> createSession(AsynchronousSocketChannel socketChannel,
                                                                           IConnectActivity<ZContext> activity) throws IOException
                                   {

                                       return new AioSession<>(socketChannel, this, this, activity, DeviceNode.this);
                                   }

                                   @Override
                                   public ZContext createContext(ISessionOption option, ISort<ZContext> sort)
                                   {
                                       return sort.newContext(option);
                                   }
                               };
                               final ICommandCreator<ZContext> _CommandCreator = session -> null;
                               return new BaseAioServer<ZContext>(_Host, _Port)
                               {

                                   @Override
                                   public ISort<ZContext> getSort()
                                   {
                                       return _Sort;
                                   }

                                   @Override
                                   public ISessionCreator<ZContext> getSessionCreator()
                                   {
                                       return _SessionCreator;
                                   }

                                   @Override
                                   public ISessionCreated<ZContext> getSessionCreated()
                                   {
                                       return DeviceNode.this;
                                   }

                                   @Override
                                   public ICommandCreator<ZContext> getCommandCreator()
                                   {
                                       return _CommandCreator;
                                   }
                               };
                           })
                           .collect(Collectors.toList());
        _DeviceRepository = repository;
        _Logger.info("Device Node Bean Load");
    }

    @SafeVarargs
    public final void localBizSend(long deviceId, ISort<ZContext> sort, IControl<ZContext>... toSends)
    {
        localSend(findSessionByIndex(deviceId), sort.getTransfer(), toSends);
    }

    public void localBizClose(long deviceId, ISort<ZContext> sort)
    {
        localClose(findSessionByIndex(deviceId), sort.getCloser());
    }

    public void start() throws IOException
    {
        _ServerCore.build(new LogicHandler((command, session) ->
        {
            //前置的 dispatcher 将 ICommands 拆分了

            _Logger.info("qtt node logic handle %s", command);
            switch (command.getSerial())
            {
                case X30_EventMsg.COMMAND:
                    X30_EventMsg x30 = (X30_EventMsg) command;
                    return new X31_ConfirmMsg(x30.getUID());
                case X31_ConfirmMsg.COMMAND:
                    X31_ConfirmMsg x31 = (X31_ConfirmMsg) command;
                    return new X32_MsgStatus(x31.getUID());
                case X50_DeviceMsg.COMMAND:
                    X50_DeviceMsg x50 = (X50_DeviceMsg) command;
                    return new X51_DeviceMsgAck(x50.getUID());
                case X103_Close.COMMAND:
                    //session is not null
                    localClose(session,
                               session.getContext()
                                      .getSort()
                                      .getCloser());
                    break;
                case X104_Ping.COMMAND:
                    return new X105_Pong("Server pong".getBytes());
                case X101_HandShake.COMMAND:
                    return command;
                case X51_DeviceMsgAck.COMMAND:
                case X105_Pong.COMMAND:
                default:
                    break;
            }
            return null;
        }), this, new ZLinkedControl(), new EncryptHandler());
        for (IAioServer<ZContext> server : _AioServers) {
            server.bindAddress(server.getLocalAddress(),
                               AsynchronousChannelGroup.withFixedThreadPool(_ServerCore.getServerCount(),
                                                                            _ServerCore.getWorkerThreadFactory()));
            server.pendingAccept();
            _Logger.info(String.format("device node start %s", server.getLocalAddress()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public IControl<ZContext>[] handle(IControl<ZContext> tar, ISession<ZContext> session)
    {
        switch (tar.getSerial())
        {
            case X20_SignUp.COMMAND:
                X21_SignUpResult x21 = new X21_SignUpResult();
                DeviceEntry deviceEntry = _DeviceRepository.save(tar);

                if (Objects.nonNull(deviceEntry)) {
                    x21.setSuccess();
                    x21.setToken(IoUtil.hex2bin(deviceEntry.getToken()));
                    x21.setPasswordId(deviceEntry.getSecondaryLongKey());
                    mapSession(deviceEntry.getPrimaryKey(), session);
                }
                else {
                    x21.setFailed();
                }
                return new IControl[] { x21 };
            case X22_SignIn.COMMAND:
                deviceEntry = _DeviceRepository.find(tar);
                X23_SignInResult x23 = new X23_SignInResult();
                if (Objects.nonNull(deviceEntry)) {
                    x23.setSuccess();
                    x23.setInvalidTime(deviceEntry.getInvalidTime());
                    mapSession(deviceEntry.getPrimaryKey(), session);
                }
                else {
                    x23.setFailed();
                }
                return new IControl[] { x23 };
            case X111_QttConnect.COMMAND:
                X111_QttConnect x111 = (X111_QttConnect) tar;
                X112_QttConnack x112 = new X112_QttConnack();
                x112.responseOk();
                if (!x111.isCleanSession() && x111.getClientIdLength() == 0) {
                    x112.rejectIdentifier();
                }
                else {
                    deviceEntry = _DeviceRepository.find(x111);
                    if (Objects.isNull(deviceEntry)) {
                        x112.rejectIdentifier();
                    }
                    else {
                        switch (deviceEntry.getOperation())
                        {
                            case OP_NULL:
                                x112.rejectIdentifier();
                                break;
                            case OP_INVALID:
                                x112.rejectBadUserOrPassword();
                                break;
                            default:
                                break;
                        }
                    }
                }
                return new IControl[] { x112 };
        }
        return new IControl[0];
    }
}
