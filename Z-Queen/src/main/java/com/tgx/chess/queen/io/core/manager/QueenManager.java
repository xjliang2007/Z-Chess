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

package com.tgx.chess.queen.io.core.manager;

import com.tgx.chess.king.base.inf.IPair;
import com.tgx.chess.king.base.util.Pair;
import com.tgx.chess.queen.config.IBizIoConfig;
import com.tgx.chess.queen.io.core.async.AioSessionManager;
import com.tgx.chess.queen.io.core.executor.ServerCore;
import com.tgx.chess.queen.io.core.inf.IActivity;
import com.tgx.chess.queen.io.core.inf.IContext;
import com.tgx.chess.queen.io.core.inf.IControl;
import com.tgx.chess.queen.io.core.inf.IPipeTransfer;
import com.tgx.chess.queen.io.core.inf.ISession;
import com.tgx.chess.queen.io.core.inf.ISessionCloser;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author william.d.zk
 */
public abstract class QueenManager<C extends IContext<C>>
        extends
        AioSessionManager<C>
        implements
        IActivity<C>
{
    protected final ServerCore<C> _ServerCore;
    private final List<IPair>     _ClusterGates;
    private final List<IPair>     _ClusterPeers;

    public QueenManager(IBizIoConfig config,
                        ServerCore<C> serverCore)
    {
        super(config);
        _ServerCore = serverCore;
        _ClusterGates = new ArrayList<>();
        _ClusterPeers = new ArrayList<>();
    }

    @Override
    public void close(ISession<C> session)
    {
        _ServerCore.close(session);
    }

    @Override
    public boolean send(ISession<C> session, IControl<C>... commands)
    {
        return _ServerCore.send(session, commands);
    }

    protected ServerCore<C> getServerCore()
    {
        return _ServerCore;
    }

    private void add(int index, List<IPair> pairs, IPair pair)
    {
        Objects.requireNonNull(pairs);
        Objects.requireNonNull(pair);
        if (index >= pairs.size()) {
            for (int i = pairs.size(); i < index; i++) {
                pairs.add(new Pair<>(-1, null));
            }
            pairs.add(pair);
        }
        else {
            pairs.set(index, pair);
        }
    }

    public void addPeer(int nodeId,
                        Pair<Long,
                             InetSocketAddress> peer)
    {
        add(nodeId, _ClusterPeers, peer);
    }

    public void addGate(int nodeId,
                        Pair<Long,
                             InetSocketAddress> gate)
    {
        add(nodeId, _ClusterGates, gate);
    }

}
