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

package com.isahl.chess.pawn.endpoint.spring.device;

import static com.isahl.chess.king.base.schedule.TimeWheel.IWheelItem.PRIORITY_NORMAL;
import static com.isahl.chess.queen.event.inf.IOperator.Type.CLUSTER_LOCAL;

import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import com.isahl.chess.bishop.io.ZSortHolder;
import com.isahl.chess.bishop.io.ws.control.X103_Ping;
import com.isahl.chess.king.base.inf.IPair;
import com.isahl.chess.king.base.inf.ITriple;
import com.isahl.chess.king.base.schedule.ScheduleHandler;
import com.isahl.chess.king.base.schedule.TimeWheel;
import com.isahl.chess.king.base.util.Triple;
import com.isahl.chess.king.topology.ZUID;
import com.isahl.chess.knight.cluster.spring.IClusterNode;
import com.isahl.chess.knight.raft.config.IRaftConfig;
import com.isahl.chess.knight.raft.model.RaftMachine;
import com.isahl.chess.queen.config.IAioConfig;
import com.isahl.chess.queen.config.IMixConfig;
import com.isahl.chess.queen.event.handler.cluster.IClusterCustom;
import com.isahl.chess.queen.event.handler.mix.ILinkCustom;
import com.isahl.chess.queen.event.handler.mix.ILogicHandler;
import com.isahl.chess.queen.event.inf.ISort;
import com.isahl.chess.queen.io.core.async.AioSession;
import com.isahl.chess.queen.io.core.async.BaseAioClient;
import com.isahl.chess.queen.io.core.async.BaseAioServer;
import com.isahl.chess.queen.io.core.executor.ServerCore;
import com.isahl.chess.queen.io.core.inf.IAioClient;
import com.isahl.chess.queen.io.core.inf.IAioServer;
import com.isahl.chess.queen.io.core.inf.IConnectActivity;
import com.isahl.chess.queen.io.core.inf.ISession;
import com.isahl.chess.queen.io.core.inf.ISessionDismiss;
import com.isahl.chess.queen.io.core.manager.MixManager;

/**
 * @author william.d.zk
 * 
 * @date 2019-05-12
 */
public class DeviceNode
        extends
        MixManager
        implements
        ISessionDismiss,
        IClusterNode<ServerCore>
{

    private final List<IAioServer> _AioServers;
    private final IAioClient       _PeerClient;
    private final IAioClient       _GateClient;
    private final TimeWheel        _TimeWheel;
    private final ZUID             _ZUID;
    private final X103_Ping        _Ping;

    @Override
    public void onDismiss(ISession session)
    {
        _Logger.debug("dismiss %s", session);
        rmSession(session);
    }

    public DeviceNode(List<ITriple> hosts,
                      IAioConfig bizIoConfig,
                      IRaftConfig raftConfig,
                      IMixConfig serverConfig,
                      TimeWheel timeWheel) throws IOException
    {
        super(bizIoConfig, new ServerCore(serverConfig));
        _TimeWheel = timeWheel;
        _ZUID = raftConfig.createZUID();
        IPair bind = raftConfig.getBind();
        final String _ClusterHost = bind.getFirst();
        final int _ClusterPort = bind.getSecond();
        hosts.add(new Triple<>(_ClusterHost, _ClusterPort, ZSortHolder.WS_CLUSTER_SERVER));

        _AioServers = hosts.stream()
                           .map(triple ->
                           {
                               final String _Host = triple.getFirst();
                               final int _Port = triple.getSecond();
                               final ISort<?> _Sort = triple.getThird();
                               ISort.Mode mode = _Sort.getMode();
                               ISort.Type type = _Sort.getType();
                               final long _SessionType;
                               if (mode == ISort.Mode.CLUSTER) {
                                   _SessionType = type == ISort.Type.SYMMETRY ? ZUID.TYPE_CLUSTER
                                                                              : type == ISort.Type.INNER ? ZUID.TYPE_INTERNAL
                                                                                                         : ZUID.TYPE_PROVIDER;
                               }
                               else {
                                   _SessionType = ZUID.TYPE_CONSUMER;
                               }
                               return new BaseAioServer(_Host, _Port, getSocketConfig(getSlot(_SessionType)))
                               {
                                   @Override
                                   public ISort.Mode getMode()
                                   {
                                       return _Sort.getMode();
                                   }

                                   @Override
                                   public ISession createSession(AsynchronousSocketChannel socketChannel,
                                                                 IConnectActivity activity) throws IOException
                                   {
                                       return new AioSession<>(socketChannel,
                                                               this,
                                                               _Sort.newContext(this),
                                                               activity,
                                                               DeviceNode.this);
                                   }

                                   @Override
                                   public void onCreate(ISession session)
                                   {
                                       session.setIndex(_ZUID.getId(_SessionType));
                                       DeviceNode.this.addSession(session);
                                   }

                                   //                                   @Override
                                   //                                   public ISort<?> getSort()
                                   //                                   {
                                   //                                       return _Sort;
                                   //                                   }
                                   //
                                   //                                   @Override
                                   //                                   public ZContext createContext(ISessionOption option, ISort<ZContext> sort)
                                   //                                   {
                                   //                                       return sort.newContext(option);
                                   //                                   }
                                   //
                                   //                                   @Override
                                   //                                   public IControl<ZContext>[] createCommands(ISession<ZContext> session)
                                   //                                   {
                                   //                                       if (_SessionType != ZUID.TYPE_CONSUMER) {
                                   //                                           X106_Identity x106 = new X106_Identity(_ZUID.getPeerId());
                                   //                                           return new X106_Identity[] { x106
                                   //                                           };
                                   //                                       }
                                   //                                       return null;
                                   //                                   }
                               };
                           })
                           .collect(Collectors.toList());
        _GateClient = new BaseAioClient(_TimeWheel, getCore().getClusterChannelGroup())
        {
            @Override
            public void onCreate(ISession session)
            {
                super.onCreate(session);
                Duration gap = Duration.ofSeconds(session.getReadTimeOutSeconds() / 2);
                _TimeWheel.acquire(session,
                                   new ScheduleHandler<>(gap, true, DeviceNode.this::heartbeat, PRIORITY_NORMAL));
            }

            @Override
            public void onDismiss(ISession session)
            {
                DeviceNode.this.onDismiss(session);
                super.onDismiss(session);
            }
        };
        _PeerClient = new BaseAioClient(_TimeWheel, getCore().getClusterChannelGroup())
        {

            @Override
            public void onCreate(ISession session)
            {
                Duration gap = Duration.ofSeconds(session.getReadTimeOutSeconds() / 2);
                _TimeWheel.acquire(session,
                                   new ScheduleHandler<>(gap, true, DeviceNode.this::heartbeat, PRIORITY_NORMAL));
            }

            @Override
            public void onDismiss(ISession session)
            {
                DeviceNode.this.onDismiss(session);
                super.onDismiss(session);
            }
        };
        _Ping = new X103_Ping(String.format("%#x,%s:%d", _ZUID.getPeerId(), _ClusterHost, _ClusterPort)
                                    .getBytes(StandardCharsets.UTF_8));
        _Logger.debug("Device Node Bean Load");
    }

    public void start(ILogicHandler logicHandler,
                      ILinkCustom linkCustom,
                      IClusterCustom<RaftMachine> clusterCustom) throws IOException
    {
        getCore().build(this, logicHandler, linkCustom, clusterCustom);
        for (IAioServer server : _AioServers) {
            server.bindAddress(server.getLocalAddress(), getCore().getServiceChannelGroup());
            server.pendingAccept();
            _Logger.info(String.format("device node start %s ", server.getLocalAddress()));
        }
    }

    @Override
    public void addPeer(IPair remote) throws IOException
    {
        _PeerClient.connect(buildConnector(remote,
                                           getSocketConfig(ZUID.TYPE_PROVIDER_SLOT),
                                           _PeerClient,
                                           ZUID.TYPE_PROVIDER,
                                           this,
                                           ZSortHolder.WS_CLUSTER_CONSUMER,
                                           _ZUID));
    }

    @Override
    public void addGate(IPair remote) throws IOException
    {
        _GateClient.connect(buildConnector(remote,
                                           getSocketConfig(ZUID.TYPE_INTERNAL_SLOT),
                                           _GateClient,
                                           ZUID.TYPE_INTERNAL,
                                           this,
                                           ZSortHolder.WS_CLUSTER_SYMMETRY,
                                           _ZUID));
    }

    @Override
    public ServerCore getCore()
    {
        return super.getCore();
    }

    private void heartbeat(ISession session)
    {
        _Logger.debug("device_cluster heartbeat => %s ", session.getRemoteAddress());
        getCore().send(session, CLUSTER_LOCAL, _Ping);
    }

    @Override
    public long getZuid()
    {
        return _ZUID.getId();
    }
}
