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

package com.isahl.chess.rook.biz.device.client;

import static com.isahl.chess.king.base.schedule.TimeWheel.IWheelItem.PRIORITY_NORMAL;
import static com.isahl.chess.queen.event.inf.IOperator.Type.BIZ_LOCAL;
import static com.isahl.chess.queen.event.inf.IOperator.Type.WRITE;

import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import com.isahl.chess.rook.config.ConsumerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.isahl.chess.bishop.io.mqtt.control.X111_QttConnect;
import com.isahl.chess.bishop.io.mqtt.control.X112_QttConnack;
import com.isahl.chess.bishop.io.ws.WsContext;
import com.isahl.chess.bishop.io.ws.control.X101_HandShake;
import com.isahl.chess.bishop.io.ws.control.X103_Close;
import com.isahl.chess.bishop.io.ws.control.X104_Ping;
import com.isahl.chess.bishop.io.ws.control.X105_Pong;
import com.isahl.chess.bishop.io.zcrypt.EncryptHandler;
import com.isahl.chess.bishop.io.zfilter.ZContext;
import com.isahl.chess.bishop.io.zprotocol.device.X21_SignUpResult;
import com.isahl.chess.bishop.io.zprotocol.device.X22_SignIn;
import com.isahl.chess.bishop.io.zprotocol.device.X23_SignInResult;
import com.isahl.chess.bishop.io.zprotocol.device.X30_EventMsg;
import com.isahl.chess.bishop.io.zprotocol.device.X31_ConfirmMsg;
import com.isahl.chess.bishop.io.zprotocol.ztls.X03_Cipher;
import com.isahl.chess.bishop.io.zprotocol.ztls.X05_EncryptStart;
import com.isahl.chess.king.base.exception.ZException;
import com.isahl.chess.king.base.inf.IPair;
import com.isahl.chess.king.base.log.Logger;
import com.isahl.chess.king.base.schedule.ScheduleHandler;
import com.isahl.chess.king.base.schedule.TimeWheel;
import com.isahl.chess.king.base.schedule.inf.ICancelable;
import com.isahl.chess.king.base.util.IoUtil;
import com.isahl.chess.king.base.util.Pair;
import com.isahl.chess.king.topology.ZUID;
import com.isahl.chess.queen.config.IAioConfig;
import com.isahl.chess.queen.event.inf.ISort;
import com.isahl.chess.queen.event.processor.QEvent;
import com.isahl.chess.queen.io.core.async.AioSession;
import com.isahl.chess.queen.io.core.async.AioSessionManager;
import com.isahl.chess.queen.io.core.async.BaseAioConnector;
import com.isahl.chess.queen.io.core.executor.ClientCore;
import com.isahl.chess.queen.io.core.inf.IAioClient;
import com.isahl.chess.queen.io.core.inf.IAioConnector;
import com.isahl.chess.queen.io.core.inf.IConnectActivity;
import com.isahl.chess.queen.io.core.inf.IControl;
import com.isahl.chess.queen.io.core.inf.ISession;
import com.isahl.chess.queen.io.core.inf.ISessionDismiss;
import com.isahl.chess.queen.io.core.inf.ISessionOption;
import com.isahl.chess.rook.io.ConsumerZSort;

/**
 * @author william.d.zk
 * 
 * @date 2019-05-12
 */
@Component
public class DeviceConsumer
        extends
        AioSessionManager<ZContext, ClientCore<ZContext>>
        implements
        IAioClient<ZContext>,
        ISessionDismiss<ZContext>
{
    private final Logger                   _Logger = Logger.getLogger(getClass().getSimpleName());
    private final ConsumerConfig _ConsumerConfig;
    private final AsynchronousChannelGroup _ChannelGroup;
    private final ClientCore<ZContext>     _ClientCore;
    private final TimeWheel                _TimeWheel;
    private final ZUID                     _ZUid   = new ZUID();
    private final Map<Long, ZClient>       _ZClientMap;
    private final X104_Ping                _Ping   = new X104_Ping();

    @SuppressWarnings("unchecked")
    @Autowired
    public DeviceConsumer(IAioConfig bizIoConfig, ConsumerConfig consumerConfig) throws IOException
    {
        super(bizIoConfig);
        _ZClientMap = new HashMap<>(1 << getConfigPower(getSlot(ZUID.TYPE_PROVIDER)));
        _ConsumerConfig = consumerConfig;
        int ioCount = _ConsumerConfig.getIoCount();
        _ClientCore = new ClientCore<>(ioCount);
        _TimeWheel = _ClientCore.getTimeWheel();
        _ChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(ioCount, _ClientCore.getWorkerThreadFactory());
        _ClientCore.build((QEvent event, long sequence, boolean endOfBatch) ->
        {
            IControl<ZContext>[]     commands;
            final ISession<ZContext> session;
            switch (event.getEventType())
            {
                case LOGIC:
                    // 与 Server Node 处理过程存在较大的差异，中间去掉一个decoded dispatcher 所以此处入参为 IControl[]
                    IPair logicContent = event.getContent();
                    commands = logicContent.getFirst();
                    session = logicContent.getSecond();
                    if (Objects.nonNull(commands))
                    {
                        commands = Stream.of(commands).map(cmd ->
                        {
                            _Logger.debug("recv:{ %s }", cmd);
                            switch (cmd.serial())
                            {
                                case X03_Cipher.COMMAND:
                                case X05_EncryptStart.COMMAND:
                                    return cmd;
                                case X21_SignUpResult.COMMAND:
                                    X21_SignUpResult x21 = (X21_SignUpResult) cmd;
                                    X22_SignIn x22 = new X22_SignIn();
                                    byte[] token = x22.getToken();
                                    ZClient zClient = _ZClientMap.get(session.getIndex());
                                    if (zClient == null)
                                    {
                                        _Logger.warning("z-client not found");
                                    }
                                    else
                                    {
                                        zClient.setToken(IoUtil.bin2Hex(token));
                                    }
                                    x22.setToken(token);
                                    x22.setPassword("password");
                                    return x22;
                                case X23_SignInResult.COMMAND:
                                    X23_SignInResult x23 = (X23_SignInResult) cmd;
                                    if (x23.isSuccess())
                                    {
                                        _Logger.debug("sign in success token invalid @ %s",
                                                      Instant.ofEpochMilli(x23.getInvalidTime())
                                                             .atZone(ZoneId.of("GMT+8")));
                                    }
                                    else
                                    {
                                        return new X103_Close("sign in failed! ws_close".getBytes());
                                    }
                                    break;
                                case X30_EventMsg.COMMAND:
                                    X30_EventMsg x30 = (X30_EventMsg) cmd;
                                    _Logger.debug("x30 payload: %s",
                                                  new String(x30.getPayload(), StandardCharsets.UTF_8));
                                    X31_ConfirmMsg x31 = new X31_ConfirmMsg(x30.getMsgId());
                                    x31.setStatus(X31_ConfirmMsg.STATUS_RECEIVED);
                                    x31.setToken(x30.getToken());
                                    return x31;
                                case X101_HandShake.COMMAND:
                                    _Logger.debug("ws_handshake ok");
                                    break;
                                case X105_Pong.COMMAND:
                                    _Logger.debug("ws_heartbeat ok");
                                    break;
                                case X103_Close.COMMAND:
                                    close(session.getIndex());
                                    break;
                                case X112_QttConnack.COMMAND:
                                    _Logger.debug("qtt connack %s", cmd);
                                    break;
                                default:
                                    break;
                            }
                            return null;
                        }).filter(Objects::nonNull).toArray(IControl[]::new);
                    }
                    break;
                default:
                    _Logger.warning("event type no mappingHandle %s", event.getEventType());
                    commands = null;
                    session = null;
                    break;
            }
            if (Objects.nonNull(commands) && commands.length > 0 && Objects.nonNull(session))
            {
                event.produce(WRITE, new Pair<>(commands, session), session.getContext().getSort().getTransfer());
            }
            else
            {
                event.ignore();
            }
        }, new EncryptHandler());
        _Logger.debug("device consumer created");
    }

    @PostConstruct
    private void init()
    {

    }

    @Override
    public void connect(IAioConnector<ZContext> connector) throws IOException
    {
        if (_ChannelGroup.isShutdown() || connector.isShutdown()) return;
        AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open(_ChannelGroup);
        socketChannel.connect(connector.getRemoteAddress(), socketChannel, connector);
    }

    @Override
    public void shutdown(ISession<ZContext> session)
    {

    }

    @SuppressWarnings("unchecked")
    public void connect(ConsumerZSort sort, ZClient zClient) throws IOException
    {
        final String host;
        final int    port;
        switch (sort)
        {
            case WS_CONSUMER:
                host = _ConsumerConfig.getWs().getHost();
                port = _ConsumerConfig.getWs().getPort();
                break;
            case QTT_SYMMETRY:
                host = _ConsumerConfig.getQtt().getHost();
                port = _ConsumerConfig.getQtt().getPort();
                break;
            default:
                throw new UnsupportedOperationException();

        }
        BaseAioConnector<ZContext> connector = new BaseAioConnector<ZContext>(host,
                                                                              port,
                                                                              getSocketConfig(getSlot(ZUID.TYPE_CONSUMER)),
                                                                              DeviceConsumer.this)
        {
            @Override
            public ISort<ZContext> getSort()
            {
                return sort;
            }

            @Override
            public void onCreate(ISession<ZContext> session)
            {
                long sessionIndex = _ZUid.getId(ZUID.TYPE_CONSUMER);
                session.setIndex(sessionIndex);
                DeviceConsumer.this.addSession(session);
                zClient.setSessionIndex(sessionIndex);
                _ZClientMap.put(session.getIndex(), zClient);
                _Logger.debug("connected :%d", sessionIndex);
            }

            @Override
            public ISession<ZContext> createSession(AsynchronousSocketChannel socketChannel,
                                                    IConnectActivity<ZContext> activity) throws IOException
            {
                return new AioSession<>(socketChannel, this, this, activity, DeviceConsumer.this);
            }

            @Override
            public ZContext createContext(ISessionOption option, ISort<ZContext> sort)
            {
                return sort.newContext(option);
            }

            @Override
            public IControl<ZContext>[] createCommands(ISession<ZContext> session)
            {
                switch (sort)
                {
                    case WS_CONSUMER:
                        WsContext wsContext = (WsContext) session.getContext();
                        X101_HandShake x101 = new X101_HandShake(host, wsContext.getSeKey(), wsContext.getWsVersion());
                        return new IControl[] { x101
                        };
                    case QTT_SYMMETRY:
                        try
                        {
                            X111_QttConnect x111 = new X111_QttConnect();
                            x111.setClientId(zClient.getClientId());
                            x111.setUserName(zClient.getUsername());
                            x111.setPassword(zClient.getPassword());
                            x111.setClean();
                            return new IControl[] { x111
                            };
                        }
                        catch (Exception e)
                        {
                            _Logger.warning("create init commands fetal", e);
                            return null;
                        }
                    default:
                        throw new UnsupportedOperationException();
                }
            }
        };
        connect(connector);
    }

    private ICancelable mHeartbeatTask;

    @Override
    public void onCreate(ISession<ZContext> session)
    {
        Duration gap = Duration.ofSeconds(session.getReadTimeOutSeconds() / 2);
        mHeartbeatTask = _TimeWheel.acquire(session,
                                            new ScheduleHandler<>(gap,
                                                                  true,
                                                                  DeviceConsumer.this::heartbeat,
                                                                  PRIORITY_NORMAL));
    }

    @Override
    public void onDismiss(ISession<ZContext> session)
    {
        mHeartbeatTask.cancel();
        rmSession(session);
        _Logger.warning("device consumer dismiss session %s", session);
    }

    private void heartbeat(ISession<ZContext> session)
    {
        getCore().send(session, BIZ_LOCAL, _Ping);
    }

    @SafeVarargs
    public final void sendLocal(long sessionIndex, IControl<ZContext>... toSends)
    {
        ISession<ZContext> session = findSessionByIndex(sessionIndex);
        if (Objects.nonNull(session))
        {
            _ClientCore.send(session, BIZ_LOCAL, toSends);
        }
        else
        {
            throw new ZException("client-id:%d,is offline;send % failed", sessionIndex, Arrays.toString(toSends));
        }
    }

    public void close(long sessionIndex)
    {
        ISession<ZContext> session = findSessionByIndex(sessionIndex);
        if (Objects.nonNull(session))
        {
            _ClientCore.close(session, BIZ_LOCAL);
        }
        else
        {
            throw new ZException("client session is not exist");
        }
    }

    public void wsHeartbeat(long sessionIndex, String msg)
    {
        Objects.requireNonNull(msg);
        sendLocal(sessionIndex, new X104_Ping(msg.getBytes()));
    }

    @Override
    public void onFailed(IAioConnector<ZContext> connector)
    {
        _TimeWheel.acquire(connector, new ScheduleHandler<>(connector.getConnectTimeout().multipliedBy(3), c ->
        {
            try
            {
                _Logger.debug("%s retry connect", Thread.currentThread().getName());
                connect(c);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }));
    }

    @Override
    protected ClientCore<ZContext> getCore()
    {
        return _ClientCore;
    }
}