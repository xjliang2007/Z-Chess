/*
 * MIT License
 *
 * Copyright (c) 2016~2021. Z-Chess
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

package com.isahl.chess.pawn.endpoint.device.service;

import com.isahl.chess.bishop.protocol.zchat.custom.ZClusterCustom;
import com.isahl.chess.bishop.protocol.zchat.custom.ZLinkCustom;
import com.isahl.chess.bishop.sort.ZSortHolder;
import com.isahl.chess.king.base.cron.TimeWheel;
import com.isahl.chess.king.base.features.model.ITriple;
import com.isahl.chess.king.base.log.Logger;
import com.isahl.chess.king.base.util.Triple;
import com.isahl.chess.knight.raft.config.IRaftConfig;
import com.isahl.chess.knight.raft.features.IRaftMapper;
import com.isahl.chess.knight.raft.service.RaftCustom;
import com.isahl.chess.knight.raft.service.RaftPeer;
import com.isahl.chess.pawn.endpoint.device.DeviceNode;
import com.isahl.chess.pawn.endpoint.device.config.MixConfig;
import com.isahl.chess.pawn.endpoint.device.spi.IAccessService;
import com.isahl.chess.pawn.endpoint.device.spi.IHandleHook;
import com.isahl.chess.queen.config.IAioConfig;
import com.isahl.chess.queen.config.IMixCoreConfig;
import com.isahl.chess.queen.events.server.ILinkCustom;
import com.isahl.chess.queen.events.server.ILogicHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author william.d.zk
 * @date 2019-06-10
 */

@Service
public class NodeService
{
    private final Logger _Logger = Logger.getLogger("endpoint.pawn." + getClass().getSimpleName());

    private final DeviceNode            _DeviceNode;
    private final ILinkCustom           _LinkCustom;
    private final RaftCustom            _RaftCustom;
    private final RaftPeer              _RaftPeer;
    private final ILogicHandler.factory _LogicFactory;

    @Autowired
    public NodeService(MixConfig mixConfig,
                       @Qualifier("pawn_io_config")
                       IAioConfig ioConfig,
                       TimeWheel timeWheel,
                       IMixCoreConfig mixCoreConfig,
                       IRaftConfig raftConfig,
                       IRaftMapper raftMapper,
                       ILinkCustom linkCustom,
                       List<IAccessService> accessAdapters,
                       List<IHandleHook> hooks) throws IOException
    {
        List<ITriple> hosts = mixConfig.getListeners()
                                          .stream()
                                          .map(listener->Triple.of(listener.getHost(), listener.getPort(), ZSortHolder._Mapping(listener.getScheme())))
                                          .collect(Collectors.toList());
        _RaftPeer = new RaftPeer(timeWheel, raftConfig, raftMapper);
        _DeviceNode = new DeviceNode(hosts, mixConfig.isMultiBind(), ioConfig, raftConfig, mixCoreConfig, timeWheel, _RaftPeer);
        _RaftCustom = new RaftCustom(_RaftPeer);
        _LinkCustom = linkCustom;
        _LogicFactory = threadId->new LogicHandler<>(_DeviceNode, threadId, accessAdapters, hooks);
        _Logger.debug("NodeService created %s", hooks);
    }

    @PostConstruct
    private void start()
    {
        _DeviceNode.start(_LogicFactory, new ZLinkCustom(_LinkCustom), new ZClusterCustom<>(_RaftCustom));
        _RaftPeer.start(_DeviceNode);
        _Logger.info(" device service start ");
    }

    /**
     * @return DeviceNode
     * → NodeService
     * → ConsistencyOpenService
     */
    @Bean
    public DeviceNode _DeviceNodeBean()
    {
        return _DeviceNode;
    }

    /**
     * @return RaftPeer
     * → ConsistencyOpenService
     */
    @Bean
    public RaftPeer _RaftPeerBean() {return _RaftPeer;}

}
