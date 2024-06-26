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

package com.isahl.chess.knight.raft.service;

import com.isahl.chess.bishop.protocol.zchat.model.command.raft.*;
import com.isahl.chess.king.base.cron.ScheduleHandler;
import com.isahl.chess.king.base.cron.TimeWheel;
import com.isahl.chess.king.base.cron.features.ICancelable;
import com.isahl.chess.king.base.disruptor.features.functions.OperateType;
import com.isahl.chess.king.base.features.IValid;
import com.isahl.chess.king.base.features.model.ITriple;
import com.isahl.chess.king.base.features.model.IoSerial;
import com.isahl.chess.king.base.log.Logger;
import com.isahl.chess.king.base.model.ListSerial;
import com.isahl.chess.king.base.util.Pair;
import com.isahl.chess.king.base.util.Triple;
import com.isahl.chess.king.env.ZUID;
import com.isahl.chess.knight.cluster.IClusterNode;
import com.isahl.chess.knight.raft.config.IRaftConfig;
import com.isahl.chess.knight.raft.features.IRaftControl;
import com.isahl.chess.knight.raft.features.IRaftMachine;
import com.isahl.chess.knight.raft.features.IRaftMapper;
import com.isahl.chess.knight.raft.features.IRaftService;
import com.isahl.chess.knight.raft.model.*;
import com.isahl.chess.knight.raft.model.replicate.LogEntry;
import com.isahl.chess.knight.raft.model.replicate.LogMeta;
import com.isahl.chess.queen.db.model.IStorage;
import com.isahl.chess.queen.events.model.QEvent;
import com.isahl.chess.queen.io.core.features.cluster.IClusterTimer;
import com.isahl.chess.queen.io.core.features.model.content.IProtocol;
import com.isahl.chess.queen.io.core.features.model.pipe.IPipeEncoder;
import com.isahl.chess.queen.io.core.features.model.session.IManager;
import com.isahl.chess.queen.io.core.features.model.session.ISession;
import com.isahl.chess.queen.io.core.features.model.session.ISort;
import com.lmax.disruptor.RingBuffer;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.isahl.chess.king.base.disruptor.features.functions.OperateType.*;
import static com.isahl.chess.king.env.ZUID.INVALID_PEER_ID;
import static com.isahl.chess.knight.raft.features.IRaftMachine.INDEX_NAN;
import static com.isahl.chess.knight.raft.features.IRaftMachine.MIN_START;
import static com.isahl.chess.knight.raft.model.RaftCode.*;
import static com.isahl.chess.knight.raft.model.RaftState.*;
import static com.isahl.chess.queen.db.model.IStorage.Operation.OP_APPEND;
import static com.isahl.chess.queen.db.model.IStorage.Operation.OP_MODIFY;
import static java.lang.Math.min;

/**
 * @author william.d.zk
 * @date 2020/1/4
 */
public class RaftPeer
        implements IValid,
                   IRaftService,
                   IClusterTimer
{
    private final Logger          _Logger       = Logger.getLogger("cluster.knight." + getClass().getSimpleName());
    private final ZUID            _ZUid;
    private final IRaftConfig     _RaftConfig;
    private final IRaftMapper     _RaftMapper;
    private final TimeWheel       _TimeWheel;
    private final RaftGraph       _SelfGraph;
    private final IRaftMachine    _SelfMachine;
    private final RaftGraph       _JointGraph;
    private final Queue<LogEntry> _RecvLogQueue = new LinkedList<>();
    private final Random          _Random       = new Random();
    private final long            _SnapshotFragmentMaxSize;
    private final int             _SyncBatchMaxSize;

    /*
     * key(Long) → msgId
     * value(X75_RaftReq) → request
     */
    private final ScheduleHandler<RaftPeer> _ElectSchedule, _HeartbeatSchedule, _TickSchedule;

    private IClusterNode mClusterNode;
    private ICancelable  mElectTask, mHeartbeatTask, mTickTask;

    public RaftPeer(TimeWheel timeWheel, IRaftConfig raftConfig, IRaftMapper raftMapper)
    {
        _TimeWheel = timeWheel;
        _RaftConfig = raftConfig;
        _ZUid = raftConfig.getZUID();
        _RaftMapper = raftMapper;
        _ElectSchedule = new ScheduleHandler<>(_RaftConfig.getElectInSecond(), RaftPeer::stepDown);
        _TickSchedule = new ScheduleHandler<>(_RaftConfig.getHeartbeatInSecond()
                                                         .multipliedBy(2), RaftPeer::start);
        _HeartbeatSchedule = new ScheduleHandler<>(_RaftConfig.getHeartbeatInSecond(), RaftPeer::heartbeat);
        _SelfGraph = RaftGraph.create("_Self_");
        _JointGraph = RaftGraph.create("_Joint_");
        _SelfMachine = RaftMachine.createBy(_ZUid.getPeerId(), OP_MODIFY);
        _SelfGraph.append(_SelfMachine);
        _JointGraph.append(_SelfMachine);
        _SnapshotFragmentMaxSize = _RaftConfig.getSnapshotFragmentMaxSize();
        _SyncBatchMaxSize = _RaftConfig.getSyncBatchMaxSize();
    }

    private RaftMachine updateMachine(IRaftMachine machine, RaftState state)
    {
        RaftMachine update = RaftMachine.createBy(machine.peer(), OP_APPEND);
        update.from(machine);
        update.approve(state);
        return update;
    }

    private IRaftMachine beCandidate()
    {
        RaftMachine update = updateMachine(_SelfMachine, CANDIDATE);
        update.term(update.term() + 1);
        update.candidate(_SelfMachine.peer());
        return update;
    }

    private void heartbeat()
    {
        if(_SelfMachine.isInState(LEADER)) {
            timeTrigger(updateMachine(_SelfMachine, LEADER));
        }
        else {
            beatCancel();
        }
    }

    public void start(final IClusterNode _Node)
    {
        LogMeta meta = _RaftMapper.getLogMeta();
        _SelfMachine.term(meta.getTerm());
        _SelfMachine.commit(meta.getCommit());
        _SelfMachine.accept(meta.getAccept());
        _SelfMachine.index(meta.getIndex());
        _SelfMachine.indexTerm(meta.getIndexTerm());
        _SelfMachine.matchIndex(meta.getAccept());
        mClusterNode = _Node;
        if(_RaftConfig.isClusterMode()) {
            // 初始化集群角色
            graphInit(_SelfGraph,
                      // _RaftConfig.getPeers().keySet() 是议会的成员
                      _RaftConfig.getPeers()
                                 .keySet(),
                      // _RaftConfig.getNodes().keySet() 是集群节点成员，包括议会成员
                      _RaftConfig.getNodes()
                                 .keySet());
            // 启动集群连接
            graphUp(_SelfGraph.getPeers(), _RaftConfig.getNodes());
            if(_RaftConfig.isInCongress()) {
                _SelfMachine.approve(FOLLOWER);
                // 延迟一个'投票周期'，等待议会完成连接。
                mTickTask = _TimeWheel.acquire(RaftPeer.this, new ScheduleHandler<>(_RaftConfig.getElectInSecond(), RaftPeer::start));
            }
            else {
                _SelfMachine.approve(CLIENT);
            }
            _Logger.debug("raft node init -> %s", _SelfMachine);
        }
        else {
            _SelfMachine.outside();
            _Logger.debug("not in cluster, single model!");
        }
    }

    private void graphInit(RaftGraph graph, Collection<Long> congress, Collection<Long> nodes)
    {
        for(long node : nodes) {
            // Graph中 装入所有需要管理的集群状态机，self已经装入了，此处不再重复
            IRaftMachine machine = RaftMachine.createBy(node, OP_MODIFY);
            // nodes 至少是配置图谱中的一员，所以
            machine.approve(congress.contains(node) ? FOLLOWER : CLIENT);
            graph.append(machine);
        }
    }

    private void graphUp(Map<Long, IRaftMachine> peers, Map<Long, RaftNode> nodes)
    {
        peers.forEach((peer, machine)->{
            // 仅连接 peer < self.peer 的节点
            if(peer < _SelfMachine.peer()) {
                RaftNode remote = nodes.get(peer);
                try {
                    mClusterNode.setupPeer(remote.getHost(), remote.getPort());
                    _Logger.debug("setup, connect [peer : %s:%d]", remote.getHost(), remote.getPort());
                }
                catch(Exception e) {
                    _Logger.warning("peer connect error: %s:%d", e, remote.getHost(), remote.getPort());
                }
            }
        });
    }

    public void installSnapshot(List<IRaftControl> snapshot)
    {
    }

    public void takeSnapshot(IRaftMapper snapshot)
    {
        long localTerm;
        if(_RaftMapper.getTotalSize() < _RaftConfig.getSnapshotMinSize()) {
            _Logger.debug("snapshot size less than threshold");
            return;
        }
        // 状态迁移未完成
        if(_SelfMachine.accept() <= _SelfMachine.commit()) {
            _Logger.debug(" applied < commit sync is running");
            return;
        }
        if(_SelfMachine.accept() >= _RaftMapper.getStartIndex() && _SelfMachine.accept() <= _RaftMapper.getEndIndex()) {
            localTerm = _RaftMapper.getEntryTerm(_SelfMachine.accept());
            _RaftMapper.updateSnapshotMeta(_SelfMachine.accept(), localTerm);
            _Logger.debug("take snapshot");
        }
        long lastSnapshotIndex = _RaftMapper.getSnapshotMeta()
                                            .getCommit();
        if(lastSnapshotIndex > 0 && _RaftMapper.getStartIndex() <= lastSnapshotIndex) {
            _RaftMapper.truncatePrefix(lastSnapshotIndex + 1);
            _Logger.debug("snapshot truncate prefix %d", lastSnapshotIndex);
        }

    }

    private void start()
    {
        /*
         * 关闭TickTask,此时执行容器可能为ElectTask 或 TickTask自身
         * 由于Elect.timeout << Tick.timeout,此处不应出现Tick无法
         * 关闭的，或关闭异常。同时cancel 配置了lock 防止意外出现。
         */
        tickCancel();
        if(_SelfMachine.isLessThanState(FOLLOWER)) {
            _Logger.debug("peer[%#] → CLIENT/OUTSIDE, don't join congress", _SelfMachine.peer());
        }
        else {
            _Logger.debug("peer[ %#x ], start vote", _SelfMachine.peer());
            startVote();
        }
    }

    /**
     * 此方法会在各种超时处理器中被启用，所以执行线程为TimeWheel.pool中的任意子线程
     */
    private void startVote()
    {
        try {
            long wait = _Random.nextInt(70) + 50;
            Thread.sleep(wait);
            _Logger.debug("random wait for %d mills, then vote", wait);
        }
        catch(InterruptedException e) {
            // ignore
        }
        timeTrigger(beCandidate());
    }

    private Map<Long, IRaftMachine> vote4me(long term)
    {
        tickCancel();
        updateTerm(term);
        _SelfMachine.leader(INVALID_PEER_ID);
        _SelfMachine.candidate(_SelfMachine.peer());
        mElectTask = _TimeWheel.acquire(this, _ElectSchedule);
        _Logger.debug("vote4me %s", _SelfMachine.toPrimary());
        return RaftGraph.join(_SelfMachine.peer(), _SelfGraph, _JointGraph);
    }

    private void updateTerm(long term)
    {
        _SelfMachine.term(term);
        _RaftMapper.updateTerm(term);
    }

    private List<ITriple> createVotes(Map<Long, IRaftMachine> peers, IManager manager)
    {
        return peers != null && !peers.isEmpty() ? peers.keySet()
                                                        .stream()
                                                        .filter(peer->peer != _SelfMachine.peer())
                                                        .map(peer->{
                                                            ISession session = manager.fairLoadSessionByPrefix(peer);
                                                            if(session == null) {
                                                                _Logger.warning("elector :%#x session has not found", peer);
                                                                return null;
                                                            }
                                                            _Logger.debug("vote4me to %s", session);
                                                            X70_RaftVote x70 = new X70_RaftVote(_ZUid.getId());
                                                            x70.with(session);
                                                            x70.peer(peer);
                                                            x70.candidate(_SelfMachine.peer());
                                                            x70.term(_SelfMachine.term());
                                                            x70.index(_SelfMachine.index());
                                                            x70.indexTerm(_SelfMachine.indexTerm());
                                                            x70.accept(_SelfMachine.accept());
                                                            x70.commit(_SelfMachine.commit());
                                                            return x70;
                                                        })
                                                        .filter(Objects::nonNull)
                                                        .map(this::map)
                                                        .collect(Collectors.toList()) : new LinkedList<>();
    }

    private List<ITriple> rejectThenVote(long rejectTo, long msgId, IManager manager, ISession session)
    {
        List<ITriple> broadcast = createVotes(vote4me(_SelfMachine.term()), manager);
        X74_RaftReject x74 = reject(RaftCode.OBSOLETE, rejectTo, msgId);
        broadcast.add(Triple.of(x74, session, session.encoder()));
        return broadcast;
    }

    public List<ITriple> vote4me(IRaftMachine update, IManager manager)
    {
        //@formatter:off
        if(_SelfMachine.isInState(FOLLOWER) &&
           update.index() <= _SelfMachine.index() &&
           update.term() > _SelfMachine.term() &&
           update.accept() <= _SelfMachine.accept() &&
           update.candidate() == _SelfMachine.peer())
        //@formatter:on
        {
            return createVotes(vote4me(update.term()), manager);
        }
        _Logger.warning("check vote failed; now: %s\n%s", _SelfMachine, update);
        return null;
    }

    private void fromRecord(IRaftRecord record, RaftGraph graph, long peer)
    {
        IRaftMachine machine = getMachine(graph, peer);
        if(machine != null) {
            machine.term(record.term());
            machine.index(record.index());
            machine.indexTerm(record.indexTerm());
            machine.commit(record.commit());
            machine.accept(record.accept());
            machine.matchIndex(record.index());
            machine.candidate(record.candidate());
        }
    }

    /**
     * @return triple, fst:集群分发消息体,snd: → link ,分发消息是单体还是集合
     */
    private ITriple checkTerm(IRaftRecord record, long msgId, ISession session)
    {
        if(lowTerm(record.term())) {
            _Logger.debug("{low term: reject %#x, mine:[%d@%d(%d)] from:[%d@%d(%d)]}",
                          record.peer(),
                          _SelfMachine.index(),
                          _SelfMachine.indexTerm(),
                          _SelfMachine.term(),
                          record.index(),
                          record.indexTerm(),
                          record.term());
            return Triple.of(reject(LOWER_TERM, record.peer(), msgId).with(session), null, SINGLE);
        }
        if(highTerm(record.term())) {
            // term > my term
            _Logger.debug("{step down [%#x → follower] high term [ %d > %d ]}", _SelfMachine.peer(), record.term(), _SelfMachine.term());
            stepDown(record.term());
        }
        return null;
    }

    public ITriple onVote(X70_RaftVote x70, IManager manager, ISession session)
    {
        fromRecord(x70, _SelfGraph, x70.candidate());
        //联合一致
        if(_SelfMachine.isInState(JOINT)) {fromRecord(x70, _JointGraph, x70.candidate());}
        ITriple reject = checkTerm(x70, x70.msgId(), session);
        if(reject != null) {return reject;}
        else if(_SelfMachine.isInState(FOLLOWER)) {
            //x70.term > my term → my term = x70.term
            //@formatter:off
            if(_SelfMachine.commit() > x70.commit() ||
               _SelfMachine.accept() > x70.accept() ||
               _SelfMachine.indexTerm() > x70.indexTerm() ||
               _SelfMachine.index() > x70.index()
            )
            //@formatter:on
            {
                _Logger.debug("less than me; reject[%s]  mine:[ %d@%d,a:%d | c:%d ] > candidate:[ %d@%d, a:%d | c:%d ] then vote4me[ follower → candidate ] ",
                              OBSOLETE,
                              _SelfMachine.index(),
                              _SelfMachine.indexTerm(),
                              _SelfMachine.accept(),
                              _SelfMachine.commit(),
                              x70.index(),
                              x70.indexTerm(),
                              x70.accept(),
                              x70.commit());
                return Triple.of(rejectThenVote(x70.candidate(), x70.msgId(), manager, session), null, BATCH);
            }
            else {
                //投票给候选人
                _Logger.debug("new term [ %d ] follower [ %#x ] → elector | candidate:[ %#x ]", x70.term(), _SelfMachine.peer(), x70.candidate());
                IProtocol ballot = stepUp(x70.term(), x70.candidate(), x70.msgId());
                return Triple.of(ballot.with(session), null, SINGLE);
            }
        }
        // elector|leader|candidate,one of these states ，candidate != INDEX_NAN 不需要重复判断
        else if(_SelfMachine.candidate() != x70.candidate()) {
            _Logger.debug("already vote [elector ×] | vote for:[ %#x not ♂ %#x ]", _SelfMachine.candidate(), x70.candidate());
            return Triple.of(reject(ALREADY_VOTE, x70.candidate(), x70.msgId()).with(session), null, SINGLE);
        }
        else {
            // 重复投票给相同的候选人
            _Logger.debug("same vote [elector x] | vote for:[%#x ♂ %#x]", _SelfMachine.candidate(), x70.candidate());
            IProtocol ballot = ballot(x70.msgId());
            return Triple.of(ballot.with(session), null, SINGLE);
        }
    }

    public ITriple onBallot(X71_RaftBallot x71, IManager manager, ISession session)
    {
        fromRecord(x71, _SelfGraph, x71.peer());
        if(_SelfMachine.isInState(JOINT)) {fromRecord(x71, _JointGraph, x71.peer());}
        ITriple reject = checkTerm(x71, x71.msgId(), session);
        if(reject != null) {return reject;}
        else {
            boolean condition = _SelfMachine.isInState(CANDIDATE) && _SelfMachine.term() == x71.term() &&
                                _SelfGraph.isMajorAccept(_SelfMachine.peer(), _SelfMachine.term());
            boolean joint = _SelfMachine.isInState(JOINT) && _JointGraph.isMajorAccept(_SelfMachine.peer(), _SelfMachine.term());
            if(joint && condition || !_SelfMachine.isInState(JOINT) && condition) {
                //term == my term
                lead();
                if(joint) {
                    return Triple.of(followersAppend(RaftGraph.join(_SelfMachine.peer(), _SelfGraph, _JointGraph), manager), null, BATCH);
                }
                else {
                    return Triple.of(followersAppend(_SelfGraph.getPeers(), manager), null, BATCH);
                }
            }
        }
        return null;
    }

    public ITriple onAppend(X72_RaftAppend x72, ISession session)
    {
        ITriple reject = checkTerm(x72, x72.msgId(), session);
        if(reject != null) {return reject;}
        //term == my term
        RaftState state = RaftState.valueOf(_SelfMachine.state());
        switch(state) {
            case LEADER -> {
                _Logger.warning("state:[%s], leader[%#x] → the other [%#x] ", state, _SelfMachine.leader(), x72.leader());
                return Triple.of(reject(SPLIT_CLUSTER, x72.leader(), x72.msgId()).with(session), null, SINGLE);
            }
            case FOLLOWER, ELECTOR -> {
                if(x72.payload() != null) {
                    _RecvLogQueue.addAll(x72.deserializeSub(ListSerial._Factory(LogEntry::new)));
                }
                fromRecord(x72, _SelfGraph, x72.leader());
                if(_SelfMachine.isInState(JOINT)) {fromRecord(x72, _JointGraph, x72.leader());}
                return follow(x72.term(), x72.leader(), x72.commit(), x72.index(), x72.indexTerm(), x72.msgId(), session);
            }
            default -> _Logger.warning("illegal state :%s", RaftState.roleOf(_SelfMachine.state()));
        }
        return null;
    }

    public ITriple onReject(X74_RaftReject x74, ISession session)
    {
        fromRecord(x74, _SelfGraph, x74.peer());
        if(_SelfMachine.isInState(JOINT)) {fromRecord(x74, _JointGraph, x74.peer());}
        IRaftMachine machine = getMachine(_SelfGraph, x74.peer());
        if(highTerm(x74.term())) {
            //peer's term > my term
            _Logger.debug(" → reject {step down [%s → follower]}", RaftState.roleOf(_SelfMachine.state()));
            stepDown(x74.term());
        }
        else {
            // peer's term == my term
            // peer's term <  my term 是不存在的情况
            STEP_DOWN:
            {
                switch(RaftCode.valueOf(x74.code())) {
                    case CONFLICT -> {
                        // follower 持有的 log 纪录 index@term 与leader 投送的不一致，后续进行覆盖同步
                        if(_SelfMachine.isInState(LEADER)) {
                            _Logger.debug("follower %#x,match failed,rollback %d@%d", x74.peer(), x74.index(), x74.indexTerm());
                            IProtocol append =
                                    machine != null ? createAppend(machine, min((int) (_SelfMachine.commit() - machine.index()), _SyncBatchMaxSize)).with(
                                            session) : null;
                            if(_SelfMachine.isInState(JOINT)) {
                                IRaftMachine jMachine = getMachine(_JointGraph, x74.peer());
                                IProtocol jAppend = jMachine != null ? createAppend(jMachine,
                                                                                    min((int) (_SelfMachine.commit() - jMachine.index()),
                                                                                        _SyncBatchMaxSize)).with(session) : null;
                                if(append != null && jAppend != null) {
                                    return Triple.of(List.of(append, jAppend), null, BATCH);
                                }
                                else if(jAppend != null) {
                                    return Triple.of(jAppend, null, SINGLE);
                                }
                            }
                            if(append != null) {
                                return Triple.of(append, null, SINGLE);
                            }
                        }
                        else {
                            _Logger.warning("self %#x is old leader & send logs → %#x,next-index wasn't catchup", _SelfMachine.peer(), x74.peer());
                            // Ignore
                        }
                    }
                    case SPLIT_CLUSTER -> {
                        if(_SelfMachine.isInState(LEADER)) {
                            _Logger.debug("other leader:[%#x]", x74.leader());
                        }
                    }
                    case ALREADY_VOTE -> {
                        if(_SelfMachine.isInState(CANDIDATE)) {
                            _Logger.debug("elector[%#x] has vote", x74.peer());
                        }
                    }
                    case OBSOLETE -> {
                        if(_SelfMachine.isGreaterThanState(FOLLOWER)) {
                            stepDown(_SelfMachine.term());
                        }
                        break STEP_DOWN;
                    }
                }
                if(_SelfGraph.isMajorReject(_SelfMachine.peer(), _SelfMachine.term())) {
                    stepDown(_SelfMachine.term());
                }
            }
        }
        return null;
    }

    /**
     * follower → leader
     *
     * @return result triple
     * fst : broadcast to peer
     * snd : leader handle x77 → notify client
     * trd : operator type 「 SINGLE/BATCH 」
     */
    public ITriple onAccept(X73_RaftAccept x73, IManager manager)
    {
        fromRecord(x73, _SelfGraph, x73.peer());
        if(_SelfMachine.isInState(JOINT)) {fromRecord(x73, _JointGraph, x73.peer());}
        /*
         * member.accept > leader.commit 完成半数 match 之后只触发一次 leader commit
         */
        long next = _SelfMachine.commit() + 1;
        boolean condition = _SelfMachine.isInState(LEADER) && _SelfGraph.isMajorAccept(next);
        boolean joint = _SelfMachine.isInState(JOINT) && _JointGraph.isMajorAccept(next);
        if(x73.accept() >= next && condition && (joint || !_SelfMachine.isInState(JOINT))) {
            _SelfMachine.commit(next, _RaftMapper);
            LogEntry entry = _RaftMapper.getEntry(next);
            if(entry.client() != _SelfMachine.peer()) {
                // leader → client → device
                ISession session = manager.fairLoadSessionByPrefix(entry.client());
                if(session != null) {
                    return Triple.of(createNotify(entry).with(session), createNotify(entry), SINGLE);
                }
            }
            else {
                // leader ≡ client → device
                return Triple.of(null, createNotify(entry), NULL);
            }
        }
        else if(x73.accept() < next) {// machine.accept < next → 已经执行过 self commit 过, 无须重复 commit
            _Logger.debug("already commit %d, follow[ %#x ] accept: %d commit: %d", _SelfMachine.commit(), x73.peer(), x73.accept(), x73.commit());
        }
        else {
            // !_SelfGraph.isMajorAccept(next) → 未满足 commit 条件, 不执行 commit
            _Logger.debug("member %#x, catchup:%d → %d", x73.peer(), x73.accept(), _SelfMachine.accept());
        }
        return null;
    }

    private IRaftMachine getMachine(RaftGraph graph, long peer)
    {
        IRaftMachine machine = graph.get(peer);
        if(machine == null) {
            _Logger.warning("%s:peer %#x is not found", graph.name(), peer);
            return null;
        }
        return machine;
    }

    private X71_RaftBallot ballot(long msgId)
    {
        X71_RaftBallot vote = new X71_RaftBallot(msgId);
        vote.peer(_SelfMachine.peer());
        vote.term(_SelfMachine.term());
        vote.index(_SelfMachine.index());
        vote.indexTerm(_SelfMachine.indexTerm());
        vote.accept(_SelfMachine.accept());
        vote.commit(_SelfMachine.commit());
        vote.candidate(_SelfMachine.candidate());
        return vote;
    }

    private X73_RaftAccept accept(long msgId)
    {
        X73_RaftAccept accept = new X73_RaftAccept(msgId);
        accept.peer(_SelfMachine.peer());
        accept.term(_SelfMachine.term());
        accept.index(_SelfMachine.index());
        accept.indexTerm(_SelfMachine.indexTerm());
        accept.leader(_SelfMachine.leader());
        accept.commit(_SelfMachine.commit());
        return accept;
    }

    private X74_RaftReject reject(RaftCode raftCode, long rejectTo, long msgId)
    {
        X74_RaftReject reject = new X74_RaftReject(msgId);
        reject.peer(_SelfMachine.peer());
        reject.term(_SelfMachine.term());
        reject.index(_SelfMachine.index());
        reject.indexTerm(_SelfMachine.indexTerm());
        reject.accept(_SelfMachine.accept());
        reject.commit(_SelfMachine.commit());
        reject.reject(rejectTo);
        reject.candidate(_SelfMachine.candidate());
        reject.leader(_SelfMachine.leader());
        reject.setCode(raftCode.getCode());
        reject.state(_SelfMachine.state());
        return reject;
    }

    private X71_RaftBallot stepUp(long term, long candidate, long msgId)
    {
        tickCancel();
        updateTerm(term);
        _SelfMachine.leader(INVALID_PEER_ID);
        _SelfMachine.candidate(candidate);
        _SelfMachine.approve(ELECTOR);
        mElectTask = _TimeWheel.acquire(this, _ElectSchedule);
        _Logger.debug("[follower → elector] %s", _SelfMachine.toPrimary());
        return ballot(msgId);
    }

    private void stepDown(long term)
    {
        if(_SelfMachine.isGreaterThanState(FOLLOWER)) {
            _Logger.debug("step down:%s → FOLLOWER", RaftState.roleOf(_SelfMachine.state()));
            beatCancel();
            electCancel();
            updateTerm(term);
            _SelfMachine.leader(INVALID_PEER_ID);
            _SelfMachine.candidate(INVALID_PEER_ID);
            _SelfMachine.approve(FOLLOWER);
            mTickTask = _TimeWheel.acquire(this, _TickSchedule);

        }
        else {_Logger.warning("step down [ignore],state now[%s]", RaftState.roleOf(_SelfMachine.state()));}
    }

    private void lead()
    {
        electCancel();
        _SelfMachine.leader(_SelfMachine.peer());
        mHeartbeatTask = _TimeWheel.acquire(this, _HeartbeatSchedule);
        _Logger.debug("be leader → %s", _SelfMachine.toPrimary());
    }

    private void stepDown()
    {
        timeTrigger(updateMachine(_SelfMachine, FOLLOWER));
    }

    private ITriple follow(long term, long leader, long commit, long preIndex, long preIndexTerm, long msgId, ISession session)
    {
        tickCancel();
        _SelfMachine.follow(term, leader, _RaftMapper);
        mTickTask = _TimeWheel.acquire(this, _TickSchedule);
        if(catchUp(preIndex, preIndexTerm)) {
            List<X77_RaftNotify> notifies = null;
            if(_SelfMachine.commit() < commit) {
                for(long i = preIndex; i <= _SelfMachine.accept(); i++) {
                    if(notifies == null) notifies = new LinkedList<>();
                    LogEntry entry = _RaftMapper.getEntry(i);
                    if(entry != null && entry.client() != _SelfMachine.peer()) {
                        notifies.add(createNotify(entry));
                    }
                    else if(entry == null) {
                        _Logger.warning("entry %d is null,self accept:%d; check segment", i, _SelfMachine.accept());
                    }
                }
                _SelfMachine.commit(min(_SelfMachine.accept(), commit), _RaftMapper);
            }
            return Triple.of(accept(msgId).with(session), notifies, SINGLE);
        }
        else {
            _Logger.debug("catch up failed reject → %#x ", leader);
            return Triple.of(reject(CONFLICT, leader, msgId).with(session), null, SINGLE);
        }
    }

    private void tickCancel()
    {
        if(mTickTask != null) {
            mTickTask.cancel();
        }
    }

    private void beatCancel()
    {
        if(mHeartbeatTask != null) {
            mHeartbeatTask.cancel();
        }
    }

    private void electCancel()
    {
        if(mElectTask != null) {
            mElectTask.cancel();
        }
    }

    private boolean highTerm(long term)
    {
        return term > _SelfMachine.term();
    }

    private boolean lowTerm(long term)
    {
        return term < _SelfMachine.term();
    }

    public LogEntry getLogEntry(long index)
    {
        _Logger.debug("peer get log entry: %d", index);
        return _RaftMapper.getEntry(index);
    }

    public List<LogEntry> diff()
    {
        long start = _SelfMachine.accept();
        long commit = _SelfMachine.commit();
        long current = _SelfMachine.index();
        if(start >= commit) {
            return null;
        }
        else {
            long end = min(current, commit);
            List<LogEntry> result = new ArrayList<>((int) (end - start));
            for(long i = start; i <= end; i++) {
                LogEntry entry = _RaftMapper.getEntry(i);
                if(entry != null) {result.add(entry);}
            }
            return result.isEmpty() ? null : result;
        }
    }

    private boolean catchUp(long preIndex, long preIndexTerm)
    {
        CHECK:
        {
            if(_SelfMachine.index() == preIndex && _SelfMachine.indexTerm() == preIndexTerm) {
                /*
                   follower 与 Leader 依据 index@term 进行对齐
                   开始同步接受缓存在_LogQueue中的数据
                 */
                for(Iterator<LogEntry> it = _RecvLogQueue.iterator(); it.hasNext(); ) {
                    LogEntry entry = it.next();
                    if(_RaftMapper.append(entry)) {
                        _SelfMachine.accept(entry.index(), entry.term());
                        _Logger.debug("follower catch up %d@%d", entry.index(), entry.term());
                    }
                    it.remove();
                }
                break CHECK;
            }
            else if(_SelfMachine.index() == 0 && preIndex == 0) {
                // 初始态，raft-machine 中不包含任何 log 记录
                _Logger.debug("self machine is empty");
                break CHECK;
            }
            else if(_SelfMachine.index() < preIndex) {
                /*
                 * 1.follower 未将自身index 同步给leader，所以leader 发送了一个最新状态过来
                 * 后续执行accept() → response 会将index 同步给leader。
                 * 2.leader 已经获知follower的需求 但是 self.index < leader.start
                 * 需要follower先完成snapshot的安装才能正常同步。
                 */
                _Logger.debug("leader doesn't know my next  || leader hasn't log, need to install snapshot");
            }
            else if(rollback(_RecvLogQueue, preIndex)) {
                // _SelfMachine.index >= preIndex
                break CHECK;
            }
            _RecvLogQueue.clear();
            return false;
        }
        return true;
    }

    private boolean rollback(final Queue<LogEntry> _LogQueue, long preIndex)
    {
        if(preIndex < 1) {
            _Logger.debug("rollback leader empty");
            //preIndex ==0
            _SelfMachine.reset();
            _RaftMapper.reset();
            if(_LogQueue.isEmpty()) {
                _Logger.debug("empty leader → clean!");
                // preIndex == 1 && queue.empty → reject
            }
            else {
                _Logger.debug("follower empty,accept leader's at all");

                //刚好follow是空的, leader投过来的数据就接收了
                LogEntry entry;
                // mapper.append false的状态包含了, _LogQueue.empty的情况
                while(_RaftMapper.append(entry = _LogQueue.poll())) {
                    assert entry != null;//其实没用,mapper.append的结果已经保障了entry != null
                    _SelfMachine.accept(entry.index(), entry.term());
                    _Logger.debug("follower catch up %d@%d", entry.index(), entry.term());
                }
            }
            return true;
        }
        else {
            _Logger.debug("rollback → leader [%d]", preIndex);
            // preIndex >= MIN_START(1)
            LogEntry rollback = _RaftMapper.truncateSuffix(preIndex);
            if(rollback != null) {
                _SelfMachine.rollBack(rollback.index(), rollback.term(), _RaftMapper);
                _Logger.debug("machine rollback %d@%d", rollback.index(), rollback.term());
                LogEntry entry;
                while(_RaftMapper.append(entry = _LogQueue.poll())) {
                    assert entry != null;
                    _SelfMachine.accept(entry.index(), entry.term());
                    _Logger.debug("follower catch up %d@%d", entry.index(), entry.term());
                }
                return true;
            }
            // rollback == null
            else if(preIndex >= _RaftMapper.getStartIndex()) {
                _Logger.fetal("lost data → reset machine & mapper ");
                _SelfMachine.reset();
                _RaftMapper.reset();
            }
        }
        return false;
    }

    public List<ITriple> turnDown(IRaftMachine update)
    {
        CHECK:
        {
            //@formatter:off
            if(_SelfMachine.isGreaterThanState(FOLLOWER) &&
               _SelfMachine.isLessThanState(LEADER) &&
               update.term() >= _SelfMachine.term())
            //@formatter:on
            {
                _Logger.debug("elect time out → turn to follower");
                stepDown(update.term());
                break CHECK;
            }
            _Logger.warning("state %s event [ignore]", RaftState.roleOf(_SelfMachine.state()));
        }
        return null;
    }

    public List<ITriple> logAppend(IRaftMachine update, IManager manager)
    {
        //@formatter:off
        if(_SelfMachine.peer() == update.peer() &&
           _SelfMachine.term() >= update.term() &&
           _SelfMachine.index() >= update.index() &&
           _SelfMachine.indexTerm() >= update.indexTerm() &&
           _SelfMachine.commit() >= update.commit())
        //@formatter:on
        {
            mHeartbeatTask = _TimeWheel.acquire(this, _HeartbeatSchedule);
            return followersAppend(RaftGraph.join(_SelfMachine.peer(), _SelfGraph, _JointGraph), manager);
        }
        // state change => ignore
        _Logger.warning("check leader heartbeat failed; now:%s", _SelfMachine);
        return null;
    }

    public List<ITriple> onSubmit(IoSerial request, IManager manager, long origin, int factory)
    {
        switch(RaftState.valueOf(_SelfMachine.state())) {
            case LEADER -> {
                List<ITriple> responses = leaderAppend(request.encoded(), _SelfMachine.peer(), origin, factory, manager);
                if(responses == null) {
                    stepDown(_SelfMachine.term());
                    return null;
                }
                else {return responses;}
            }
            case CLIENT, FOLLOWER -> {
                ISession session = manager.fairLoadSessionByPrefix(_SelfMachine.leader());
                if(session != null) {
                    _Logger.debug(" [%#x][%s] → leader[%#x] x75, device[%#x] ",
                                  _SelfMachine.peer(),
                                  RaftState.roleOf(_SelfMachine.state()),
                                  _SelfMachine.leader(),
                                  origin);
                    X75_RaftReq x75 = new X75_RaftReq(generateId());
                    x75.withSub(request);
                    x75.origin(origin);
                    x75.factory(factory);
                    x75.client(_SelfMachine.peer());
                    x75.with(session);
                    return Collections.singletonList(Triple.of(x75, session, session.encoder()));
                }
                else {
                    _Logger.fetal("Leader connection miss,wait for reconnecting");
                }
            }
            default -> {
                _Logger.fetal("cluster is electing self[%#x]→ %s", _SelfMachine.peer(), RaftState.roleOf(_SelfMachine.state()));
                return null;
            }
        }
        return null;
    }

    public ITriple onRequest(X75_RaftReq x75, IManager manager, ISession session)
    {
        if(_SelfMachine.isInState(LEADER)) {
            List<ITriple> appends = leaderAppend(x75.payload(), x75.client(), x75.origin(), x75.factory(), manager);
            if(appends != null && !appends.isEmpty()) {
                return Triple.of(appends, response(SUCCESS, x75.client(), x75.msgId(), x75.origin()).with(session), BATCH);
            }
            else {
                return Triple.of(null, response(WAL_FAILED, x75.client(), x75.msgId(), x75.origin()).with(session), NULL);
            }
        }
        else {
            return Triple.of(null, response(ILLEGAL_STATE, x75.client(), x75.msgId(), x75.origin()).with(session), NULL);
        }
    }

    //client receive x76
    public ITriple onResponse(X76_RaftResp x76)
    {
        return Triple.of(null, x76, NULL);
    }

    public ITriple onNotify(X77_RaftNotify x77)
    {
        _Logger.debug("client[%#x] onNotify", _SelfMachine.peer());
        return Triple.of(null, x77, NULL);
    }

    public List<ITriple> onModify(RaftNode delta, IManager manager)
    {
        X78_RaftModify x78 = new X78_RaftModify(generateId());
        long leader = _SelfMachine.leader();
        _RaftConfig.change(delta);
        x78.newGraph(leader,
                     _RaftConfig.getPeers()
                                .keySet());
        RaftState state = RaftState.valueOf(_SelfMachine.state());
        switch(state) {
            case LEADER -> {
                //old_graph
                _SelfMachine.modify();
                List<ITriple> joints = new LinkedList<>();
                long[] peers = x78.getNewGraph();
                for(long peer : peers) {_JointGraph.append(RaftMachine.createBy(peer, delta.operation()));}
                Map<Long, IRaftMachine> union = RaftGraph.join(leader, _SelfGraph, _JointGraph);
                if(union != null && !union.isEmpty()) {
                    union.forEach((peer, machine)->{
                        ISession session = manager.fairLoadSessionByPrefix(peer);
                        if(session != null) {
                            X78_RaftModify copy = x78.duplicate();
                            copy.with(session);
                            copy.msgId(_ZUid.getId());
                            joints.add(Triple.of(copy, session, session.encoder()));
                        }
                    });
                    return joints;
                }
            }
            case CLIENT, FOLLOWER -> {
                ISession session = manager.fairLoadSessionByPrefix(leader);
                return List.of(Triple.of(x78, session, session.encoder()));
            }
            //ignore ELECTOR, CANDIDATE, GATE
            default -> _Logger.warning("illegal state: [%s]", state);
        }
        return null;
    }

    /*
        1. 转换leader 状态为 joint union 状态
        2. 向 old_graph 和 new_graph 同时发送状态信息
        3.
     */
    public ITriple onModify(X78_RaftModify x78, IManager manager)
    {
        long[] peers = x78.getNewGraph();
        long leader = x78.getLeader();
        X7A_RaftJoint x7a = new X7A_RaftJoint();
        x7a.code(SUCCESS.getCode());
        x7a.msgId(x78.msgId());
        x7a.peer(_SelfMachine.peer());
        X7B_RaftConfirm x7c = new X7B_RaftConfirm();
        x7c.peer(_SelfMachine.peer());
        x7c.term(_SelfMachine.term());
        x7c.commit(_SelfMachine.commit());
        x7c.index(_SelfMachine.index());
        x7c.indexTerm(_SelfMachine.indexTerm());
        x7c.leader(_SelfMachine.leader());
        RaftState state = RaftState.valueOf(_SelfMachine.state());
        for(long peer : peers) {_JointGraph.append(RaftMachine.createBy(peer, OP_MODIFY));}
        switch(state) {
            case LEADER -> {
                //old_graph
                _SelfMachine.modify();
                List<IProtocol> joints = new LinkedList<>();
                Map<Long, IRaftMachine> union = RaftGraph.join(leader, _SelfGraph, _JointGraph);
                if(union != null) {
                    union.forEach((peer, machine)->{
                        ISession session = manager.fairLoadSessionByPrefix(peer);
                        if(session != null) {
                            X78_RaftModify copy = x78.duplicate();
                            copy.with(session);
                            copy.msgId(_ZUid.getId());
                            joints.add(copy);
                        }
                    });
                    return Triple.of(joints, x7a, BATCH);
                }
                x7a.code(GRAPH_CONFIG.getCode());
                return Triple.of(null, x7a, NULL);
            }
            case CLIENT, OUTSIDE -> {
                ITriple confirm = null;
                for(long peer : peers) {
                    if(peer == _SelfMachine.peer()) {
                        _SelfMachine.reset();
                        _SelfMachine.modify(FOLLOWER);
                        _SelfMachine.leader(leader);
                        confirm = Triple.of(x7c, null, SINGLE);
                    }
                }
                if(confirm != null) {return confirm;}
            }
            case FOLLOWER -> {
                if(_SelfMachine.leader() == leader) {
                    _SelfMachine.modify();
                    return Triple.of(x7c, null, SINGLE);
                }
                //else 选举态 不改变状态
                _Logger.warning("expect:{ FOLLOW → %#x }, from:%#x ; illegal state", _SelfMachine.leader(), leader);
            }
            default -> {
                //ignore ELECTOR, CANDIDATE, GATE
                _Logger.warning("illegal state: [%s]", state);
                x7a.code(ILLEGAL_STATE.getCode());
                return Triple.of(null, x7a, NULL);
            }
        }
        return null;
    }

    public ITriple onConfirm(X7B_RaftConfirm x7c, IManager manager)
    {
        if(_SelfMachine.isInState(JOINT)) {
            fromRecord(x7c, _SelfGraph, x7c.peer());
            fromRecord(x7c, _JointGraph, x7c.peer());
            if(_SelfGraph.isMajorConfirm() && _JointGraph.isMajorConfirm()) {
                _SelfMachine.confirm();
                _SelfGraph.resetTo(_JointGraph);
                Map<Long, IRaftMachine> union = RaftGraph.join(_SelfMachine.peer(), _SelfGraph, _JointGraph);
                if(union != null) {
                    return Triple.of(union.entrySet()
                                          .stream()
                                          .map(entry->{
                                              X79_RaftConfirm x79 = new X79_RaftConfirm();
                                              x79.peer(entry.getKey());
                                              x79.code(SUCCESS.getCode());
                                              x79.state(entry.getValue()
                                                             .state());
                                              ISession ps = manager.fairLoadSessionByPrefix(x79.peer());
                                              if(ps != null) {
                                                  return x79.with(ps);
                                              }
                                              return null;
                                          })
                                          .filter(Objects::nonNull)
                                          .collect(Collectors.toList()), null, BATCH);
                }
                _Logger.warning("graph union NULL");
            }
        }
        // leader 已经完成 confirm 忽略
        return null;
    }

    public void onConfirm(X79_RaftConfirm x79)
    {
        if(_SelfMachine.isInState(JOINT)) {
            IRaftMachine leader = getMachine(_JointGraph, x79.peer());
            if(leader != null) {leader.confirm();}
            _SelfMachine.confirm();
            _SelfGraph.resetTo(_JointGraph);
            _Logger.debug("joint confirm");
        }
    }

    private X76_RaftResp response(RaftCode code, long client, long reqId, long origin)
    {
        X76_RaftResp x76 = new X76_RaftResp(reqId);
        x76.client(client);
        x76.origin(origin);
        x76.code(code.getCode());
        return x76;
    }

    private List<ITriple> leaderAppend(byte[] payload, long client, long origin, int factory, IManager manager)
    {
        LogEntry newEntry = new LogEntry(_SelfMachine.index() + 1, _SelfMachine.term(), client, origin, factory, payload);
        _Logger.debug("leader append new log {%s}", newEntry);
        if(_RaftMapper.append(newEntry)) {
            _SelfMachine.accept(newEntry.index(), newEntry.term());
            _Logger.debug("leader appended log %d@%d", newEntry.index(), newEntry.term());
            return followersAppend(RaftGraph.join(_SelfMachine.peer(), _SelfGraph, _JointGraph), manager);
        }
        _Logger.fetal("RAFT WAL failed!");
        return null;
    }

    private List<ITriple> followersAppend(Map<Long, IRaftMachine> peers, IManager manager)
    {
        return peers != null && !peers.isEmpty() ? peers.entrySet()
                                                        .stream()
                                                        .filter(e->e.getKey() != _SelfMachine.peer())
                                                        .map(e->{
                                                            ISession session = manager.fairLoadSessionByPrefix(e.getKey());
                                                            return session == null ? null : createAppend(e.getValue()).with(session);
                                                        })
                                                        .filter(Objects::nonNull)
                                                        .map(this::map)
                                                        .collect(Collectors.toList()) : null;
    }

    private X72_RaftAppend createAppend(IRaftMachine acceptor)
    {
        return createAppend(acceptor, -1);
    }

    private X72_RaftAppend createAppend(IRaftMachine acceptor, int limit)
    {
        X72_RaftAppend x72 = new X72_RaftAppend(_ZUid.getId());
        x72.leader(_SelfMachine.peer());
        x72.term(_SelfMachine.term());
        x72.commit(_SelfMachine.commit());
        x72.preIndex(_SelfMachine.index());
        x72.preIndexTerm(_SelfMachine.indexTerm());
        x72.setFollower(acceptor.peer());
        CHECK:
        {
            long preIndex = acceptor.index();
            long preIndexTerm = acceptor.indexTerm();
            if(preIndex == _SelfMachine.index() || preIndex == INDEX_NAN) {
                // acceptor 已经同步 或 acceptor.next 未知
                break CHECK;
            }
            // preIndex < self.index && preIndex >= 0
            ListSerial<LogEntry> entryList = new ListSerial<>(LogEntry::new);
            long next = preIndex + 1;//next >= 1
            if(next > MIN_START) {
                if(next > _SelfMachine.index()) {
                    //acceptor.index > leader.index, 后续用leader的数据进行覆盖
                    break CHECK;
                }
                // 存有数据的状态
                LogEntry matched;
                if((matched = _RaftMapper.getEntry(preIndex)) == null) {
                    _Logger.warning("match point %d@%d lost", preIndex, preIndexTerm);
                    break CHECK;
                }
                else {
                    if(matched.index() == preIndex && matched.term() == preIndexTerm) {
                        x72.preIndex(preIndex);
                        x72.preIndexTerm(preIndexTerm);
                        _Logger.debug("follow[%#x] %d@%d,leader %d@%d",
                                      acceptor.peer(),
                                      preIndex,
                                      preIndexTerm,
                                      _SelfMachine.index(),
                                      _SelfMachine.indexTerm());
                    }
                    else {
                        _Logger.warning("matched %#x vs %#x no consistency;%d@%d", peerId(), acceptor.peer(), preIndex, preIndexTerm);
                        break CHECK;
                    }
                }
            }
            else {
                /*
                 * preIndex == 0
                 * follower是以空数据状态启动。
                 */
                x72.preIndex(0);
                x72.preIndexTerm(0);
            }
            for(long end = _SelfMachine.index(), payloadSize = 0; next <= end && payloadSize < _SnapshotFragmentMaxSize; next++) {
                if(limit > 0 && entryList.size() >= limit) {
                    break;
                }
                LogEntry nextLog = _RaftMapper.getEntry(next);
                _Logger.debug("leader → acceptor:%s", nextLog);
                entryList.add(nextLog);
                payloadSize += nextLog.sizeOf();
            }
            x72.withSub(entryList);
            _Logger.debug("leader → acceptor[ %#x ] %d@%d with %d", x72.peer(), x72.index(), x72.indexTerm(), entryList.size());
        }
        return x72;
    }

    private Triple<IProtocol, ISession, IPipeEncoder> map(IProtocol source)
    {
        // source 一定持有 session 在上一步完成了这个操作。
        return Triple.of(source,
                         source.session(),
                         source.session()
                               .encoder());
    }

    @Override
    public boolean isInCongress()
    {
        return RaftState.isInCongress(_SelfMachine.state());
    }

    @Override
    public long peerId()
    {
        return _SelfMachine.peer();
    }

    @Override
    public long generateId()
    {
        return _ZUid.getId();
    }

    @Override
    public long generateIdWithType(ISort.Type type)
    {
        return _ZUid.getId(type.prefix());
    }

    @Override
    public RaftNode getLeader()
    {
        if(_SelfMachine.leader() != INVALID_PEER_ID) {
            return _RaftConfig.findById(_SelfMachine.leader());
        }
        return null;
    }

    private <T> void trigger(T delta, OperateType type)
    {
        final RingBuffer<QEvent> _Publisher = mClusterNode.selectPublisher(type);
        final ReentrantLock _Lock = mClusterNode.selectLock(type);
        _Lock.lock();
        try {
            long sequence = _Publisher.next();
            try {
                QEvent event = _Publisher.get(sequence);
                event.produce(type, Pair.of(this, delta), null);
            }
            finally {
                _Publisher.publish(sequence);
            }
        }
        finally {
            _Lock.unlock();
        }
    }

    @Override
    public void topology(RaftNode delta)
    {
        trigger(delta, OperateType.CLUSTER_TOPOLOGY);
    }

    @Override
    public <T extends IStorage> void timeTrigger(T content)
    {
        trigger(content, OperateType.CLUSTER_TIMER);
    }

    @Override
    public Collection<RaftNode> topology()
    {
        return _RaftConfig.getPeers()
                          .values();
    }

    private X77_RaftNotify createNotify(LogEntry raftLog)
    {
        if(raftLog != null) {
            X77_RaftNotify x77 = new X77_RaftNotify(_ZUid.getId());
            x77.withSub(raftLog);
            x77.client(raftLog.client());
            x77.origin(raftLog.origin());
            return x77;
        }
        return null;
    }

    public IRaftMapper mapper()
    {
        return _RaftMapper;
    }

    @Override
    public RaftState raftState()
    {
        return RaftState.valueOf(_SelfMachine.state());
    }
}
