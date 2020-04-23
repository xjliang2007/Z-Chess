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
package com.tgx.chess.knight.cluster.spring.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tgx.chess.king.base.schedule.TimeWheel;
import com.tgx.chess.knight.cluster.ClusterNode;
import com.tgx.chess.knight.raft.IRaftDao;
import com.tgx.chess.knight.raft.config.IRaftConfig;
import com.tgx.chess.knight.raft.model.RaftNode;
import com.tgx.chess.knight.raft.service.ClusterCustom;

@Service
public class ClusterService
{

    private final ClusterNode                _ClusterNode;
    private final IRaftConfig _ClusterConfig;
    private final ConsistentCustom           _ConsistentCustom;
    private final ClusterCustom<ClusterNode> _ClusterCustom;
    private final TimeWheel                  _TimeWheel;
    private final RaftNode<ClusterNode>      _RaftNode;

    @Autowired
    public ClusterService(ClusterNode clusterNode,
                          IRaftConfig clusterConfig,
                          ConsistentCustom consistentCustom,
                          ClusterCustom<ClusterNode> clusterCustom,
                          IRaftDao raftDao)
    {
        _TimeWheel = new TimeWheel();
        _ClusterNode = clusterNode;
        _ClusterConfig = clusterConfig;
        _ConsistentCustom = consistentCustom;
        _ClusterCustom = clusterCustom;
        _RaftNode = new RaftNode<>(_TimeWheel, _ClusterConfig, raftDao, _ClusterNode);
        _ClusterCustom.setRaftNode(_RaftNode);
    }
}
