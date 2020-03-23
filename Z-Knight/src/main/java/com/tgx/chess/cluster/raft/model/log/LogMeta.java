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

package com.tgx.chess.cluster.raft.model.log;

import java.io.RandomAccessFile;
import java.util.Objects;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.tgx.chess.cluster.raft.model.RaftGraph;
import com.tgx.chess.json.JsonUtil;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LogMeta
        extends
        BaseMeta
{
    private final static int _SERIAL = INTERNAL_SERIAL + 1;

    /** 已存储日志的start index */
    private long      start;
    /** 已存储日志的最大任期号 */
    private long      term;
    /** 当前状态机候选人 */
    private long      candidate;
    /** 集群中已知的最大的被提交的日志index */
    private long      commit;
    /** 已被应用到状态机日志index */
    private long      applied;
    /** 集群节点信息 */
    private RaftGraph raftGraph;

    LogMeta()
    {
        super(null);
    }

    LogMeta(RandomAccessFile file)
    {
        super((file));
    }

    LogMeta load()
    {
        loadFromFile();
        return this;
    }

    @Override
    public int decode(byte[] data)
    {
        LogMeta json = JsonUtil.readValue(data, getClass());
        Objects.requireNonNull(json);
        start = json.getStart();
        term = json.getTerm();
        candidate = json.getCandidate();
        raftGraph = json.getRaftGraph();
        return length = data.length;
    }

    @Override
    public byte[] encode()
    {
        byte[] data = JsonUtil.writeValueAsBytes(this);
        Objects.requireNonNull(data);
        length = data.length;
        return data;
    }

    @Override
    public int serial()
    {
        return _SERIAL;
    }

    @Override
    public int superSerial()
    {
        return INTERNAL_SERIAL;
    }

    public long getStart()
    {
        return start;
    }

    public void setStart(long start)
    {
        this.start = start;
    }

    public long getTerm()
    {
        return term;
    }

    public void setTerm(long term)
    {
        this.term = term;
    }

    public long getCandidate()
    {
        return candidate;
    }

    public void setCandidate(long candidate)
    {
        this.candidate = candidate;
    }

    public RaftGraph getRaftGraph()
    {
        return raftGraph;
    }

    public void setRaftGraph(RaftGraph raftGraph)
    {
        this.raftGraph = raftGraph;
    }

    public long getCommit()
    {
        return commit;
    }

    public void setCommit(long commit)
    {
        this.commit = commit;
    }

    public long getApplied()
    {
        return applied;
    }

    public void setApplied(long applied)
    {
        this.applied = applied;
    }
}