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

package com.tgx.chess.knight.raft.model;

import static com.tgx.chess.knight.raft.RaftState.CANDIDATE;
import static com.tgx.chess.knight.raft.RaftState.FOLLOWER;
import static com.tgx.chess.knight.raft.RaftState.LEADER;
import static com.tgx.chess.knight.raft.model.RaftCode.ALREADY_VOTE;
import static com.tgx.chess.knight.raft.model.RaftCode.CONFLICT;
import static com.tgx.chess.knight.raft.model.RaftCode.ILLEGAL_STATE;
import static com.tgx.chess.knight.raft.model.RaftCode.LOWER_TERM;
import static com.tgx.chess.knight.raft.model.RaftCode.OBSOLETE;
import static com.tgx.chess.knight.raft.model.RaftCode.SPLIT_CLUSTER;
import static com.tgx.chess.knight.raft.model.RaftCode.SUCCESS;
import static com.tgx.chess.queen.db.inf.IStorage.Operation.OP_APPEND;
import static java.lang.Math.min;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tgx.chess.bishop.io.zfilter.ZContext;
import com.tgx.chess.bishop.io.zprotocol.raft.X72_RaftVote;
import com.tgx.chess.bishop.io.zprotocol.raft.X76_RaftResult;
import com.tgx.chess.bishop.io.zprotocol.raft.X7E_RaftBroadcast;
import com.tgx.chess.bishop.io.zprotocol.raft.X7F_RaftResponse;
import com.tgx.chess.king.base.inf.IPair;
import com.tgx.chess.king.base.inf.ITriple;
import com.tgx.chess.king.base.log.Logger;
import com.tgx.chess.king.base.schedule.ICancelable;
import com.tgx.chess.king.base.schedule.ScheduleHandler;
import com.tgx.chess.king.base.schedule.TimeWheel;
import com.tgx.chess.king.base.util.Pair;
import com.tgx.chess.king.base.util.Triple;
import com.tgx.chess.king.topology.ZUID;
import com.tgx.chess.knight.json.JsonUtil;
import com.tgx.chess.knight.raft.IRaftDao;
import com.tgx.chess.knight.raft.IRaftMachine;
import com.tgx.chess.knight.raft.IRaftMessage;
import com.tgx.chess.knight.raft.RaftState;
import com.tgx.chess.knight.raft.config.IRaftConfig;
import com.tgx.chess.knight.raft.model.log.LogEntry;
import com.tgx.chess.queen.io.core.inf.IActivity;
import com.tgx.chess.queen.io.core.inf.IClusterPeer;
import com.tgx.chess.queen.io.core.inf.IClusterTimer;
import com.tgx.chess.queen.io.core.inf.IControl;
import com.tgx.chess.queen.io.core.inf.IProtocol;
import com.tgx.chess.queen.io.core.inf.ISessionManager;

/**
 * @author william.d.zk
 * @date 2020/1/4
 */
public class RaftNode<T extends IActivity<ZContext> & IClusterPeer & IClusterTimer>
{
    private final Logger                       _Logger         = Logger.getLogger(getClass().getSimpleName());
    private final ZUID                         _ZUID;
    private final IRaftConfig                  _ClusterConfig;
    private final T                            _ClusterPeer;
    private final IRaftDao                     _RaftDao;
    private final TimeWheel                    _TimeWheel;
    private final ScheduleHandler<RaftNode<T>> _ElectSchedule, _HeartbeatSchedule, _TickSchedule;
    private final RaftGraph                    _RaftGraph;
    private final RaftMachine                  _SelfMachine;
    private final Queue<LogEntry>              _AppendLogQueue = new LinkedList<>();
    private final Random                       _Random         = new Random();
    private ICancelable                        mElectTask, mHeartbeatTask, mTickTask;

    public RaftNode(TimeWheel timeWheel,
                    IRaftConfig clusterConfig,
                    IRaftDao raftDao,
                    T manager)
    {
        _TimeWheel = timeWheel;
        _ClusterConfig = clusterConfig;
        _ClusterPeer = manager;
        _ZUID = clusterConfig.createZUID();
        _RaftDao = raftDao;
        _ElectSchedule = new ScheduleHandler<>(_ClusterConfig.getElectInSecond(), RaftNode::stepDown, this);
        _HeartbeatSchedule = new ScheduleHandler<>(_ClusterConfig.getHeartbeatInSecond(),
                                                   true,
                                                   RaftNode::heartbeat,
                                                   this);
        _TickSchedule = new ScheduleHandler<>(_ClusterConfig.getHeartbeatInSecond()
                                                            .multipliedBy(2),
                                              RaftNode::startVote,
                                              this);
        _RaftGraph = new RaftGraph();
        _SelfMachine = new RaftMachine(_ZUID.getPeerId());
        _RaftGraph.append(_SelfMachine);
    }

    private void heartbeat()
    {
        RaftMachine update = new RaftMachine(_SelfMachine.getPeerId());
        update.setOperation(OP_APPEND);
        update.setIndex(_SelfMachine.getIndex());
        update.setIndexTerm(_SelfMachine.getIndexTerm());
        update.setCommit(_SelfMachine.getCommit());
        _ClusterPeer.timerEvent(update);
        _Logger.info("leader heartbeat");
    }

    public void init() throws IOException
    {
        if (!_ClusterConfig.isClusterModel()) {
            _Logger.info("single model skip init raft node");
            return;
        }
        /* _RaftDao 启动的时候已经装载了 snapshot */
        _SelfMachine.setTerm(_RaftDao.getLogMeta()
                                     .getTerm());
        _SelfMachine.setCandidate(_RaftDao.getLogMeta()
                                          .getCandidate());
        _SelfMachine.setCommit(_RaftDao.getLogMeta()
                                       .getCommit());
        _SelfMachine.setApplied(_RaftDao.getLogMeta()
                                        .getApplied());
        _SelfMachine.setIndex(_RaftDao.getLogMeta()
                                      .getIndex());
        _SelfMachine.setIndexTerm(_RaftDao.getLogMeta()
                                          .getTerm());
        _SelfMachine.setPeerSet(_RaftDao.getLogMeta()
                                        .getPeerSet());
        _SelfMachine.setGateSet(_RaftDao.getLogMeta()
                                        .getGateSet());
        if (_SelfMachine.getPeerSet() == null && _SelfMachine.getGateSet() == null) {
            /*首次启动或删除本地状态机重启,仅需要连接node_id < self.node_id的peer*/
            List<IPair> peers = _ClusterConfig.getPeers();
            if (peers != null) {
                for (int i = 0, size = peers.size(); i < size; i++) {
                    IPair pair = peers.get(i);
                    _SelfMachine.appendPeer(new Triple<>(_ZUID.getPeerId(i), pair.getFirst(), pair.getSecond()));
                }
                _RaftDao.getLogMeta()
                        .setPeerSet(_SelfMachine.getPeerSet());
            }
            List<IPair> gates = _ClusterConfig.getGates();
            if (gates != null) {
                for (int i = 0, size = gates.size(); i < size; i++) {
                    IPair pair = gates.get(i);
                    _SelfMachine.appendGate(new Triple<>(_ZUID.getClusterId(i), pair.getFirst(), pair.getSecond()));
                }
                _RaftDao.getLogMeta()
                        .setGateSet(_SelfMachine.getGateSet());
            }
        }
        //启动snapshot定时回写计时器
        _TimeWheel.acquire(_RaftDao,
                           new ScheduleHandler<>(_ClusterConfig.getSnapshotInSecond(),
                                                 true,
                                                 this::takeSnapshot,
                                                 _RaftDao));
        _RaftDao.updateAll();
        //启动集群连接
        if (_SelfMachine.getPeerSet() != null) {
            for (ITriple remote : _SelfMachine.getPeerSet()) {
                long peerId = remote.getFirst();
                //Graph中转装入所有需要管理的集群状态机，self已经装入了，此处不再重复
                if (peerId != _SelfMachine.getPeerId()) {
                    _RaftGraph.append(new RaftMachine(peerId));
                }
                //仅连接NodeId<自身的节点
                if (peerId < _SelfMachine.getPeerId()) {
                    _ClusterPeer.addPeer(new Pair<>(remote.getSecond(), remote.getThird()));
                }
            }
        }
        if (_SelfMachine.getGateSet() != null) {
            for (ITriple remote : _SelfMachine.getGateSet()) {
                long gateId = remote.getFirst();
                if (gateId != _ZUID.getClusterId()) {
                    _ClusterPeer.addGate(new Pair<>(remote.getSecond(), remote.getThird()));
                }
            }
        }
        //初始化为FOLLOWER 状态，等待LEADER的HEARTBEAT
        mTickTask = _TimeWheel.acquire(this, _TickSchedule);

        _Logger.info("raft node init -> %s", _SelfMachine);
    }

    public void load(List<IRaftMessage> snapshot)
    {

    }

    public void takeSnapshot(IRaftDao snapshot)
    {
        long localTerm;
        if (_RaftDao.getTotalSize() < _ClusterConfig.getSnapshotMinSize()) {
            _Logger.info("snapshot size less than threshold");
            return;
        }
        //状态迁移未完成
        if (_SelfMachine.getApplied() <= _SelfMachine.getCommit()) {
            _Logger.info(" applied < commit sync is running");
            return;
        }
        if (_SelfMachine.getApplied() >= _RaftDao.getStartIndex()
            && _SelfMachine.getApplied() <= _RaftDao.getEndIndex())
        {
            localTerm = _RaftDao.getEntryTerm(_SelfMachine.getApplied());
            _RaftDao.updateSnapshotMeta(_SelfMachine.getApplied(), localTerm);
            _Logger.info("take snapshot");
        }
        long lastSnapshotIndex = _RaftDao.getSnapshotMeta()
                                         .getCommit();
        if (lastSnapshotIndex > 0 && _RaftDao.getStartIndex() <= lastSnapshotIndex) {
            _RaftDao.truncatePrefix(lastSnapshotIndex + 1);
            _Logger.info("snapshot truncate prefix %d", lastSnapshotIndex);
        }

    }

    public IRaftMachine getMachine()
    {
        return _SelfMachine;
    }

    /**
     * 此方法会在各种超时处理器中被启用，所以执行线程为TimeWheel.pool中的任意子线程
     */
    private void startVote()
    {
        /*关闭TickTask,此时执行容器可能为ElectTask 或 TickTask自身
          由于Elect.timeout << Tick.timeout,此处不应出现Tick无法
          关闭的，或关闭异常。同时cancel 配置了lock 防止意外出现。
        */
        tickCancel();
        try {
            long wait = _Random.nextInt(150) + 100;
            Thread.sleep(wait);
            _Logger.info("random wait for %d mills", wait);
        }
        catch (InterruptedException e) {
            //ignore
        }
        _ClusterPeer.timerEvent(_SelfMachine.createCandidate());
    }

    private X7F_RaftResponse success()
    {
        return response(SUCCESS.getCode());
    }

    private X7F_RaftResponse response(int code)
    {
        X7F_RaftResponse response = new X7F_RaftResponse(_ZUID.getId());
        response.setCode(code);
        response.setPeerId(_SelfMachine.getPeerId());
        response.setTerm(_SelfMachine.getTerm());
        response.setCatchUp(_SelfMachine.getIndex());
        response.setCandidate(_SelfMachine.getCandidate());
        return response;
    }

    private X7F_RaftResponse reject(RaftCode raftCode)
    {
        _AppendLogQueue.clear();
        return response(raftCode.getCode());
    }

    private X7F_RaftResponse stepUp(long peerId, long term)
    {
        _SelfMachine.beElector(peerId, term, _RaftDao);
        mElectTask = _TimeWheel.acquire(this, _ElectSchedule);
        _Logger.info("[follower → elector] %s", _SelfMachine);
        return success();
    }

    private void stepDown()
    {
        _SelfMachine.follow(_RaftDao);
        mTickTask = _TimeWheel.acquire(this, _TickSchedule);
        _Logger.info("[* → follower] %s", _SelfMachine);
    }

    private X7F_RaftResponse follow(long peerId, long term, long commit)
    {
        _Logger.info("follower %#x @%d => %d", peerId, term, commit);
        _SelfMachine.follow(peerId, term, commit, _RaftDao);
        mTickTask = _TimeWheel.acquire(this, _TickSchedule);
        if (catchUp()) {
            _Logger.info("catch up => %s", _SelfMachine);
            return success();
        }
        else {
            _Logger.info("roll back =>%s ", _SelfMachine);
            return reject(CONFLICT);
        }
    }

    private void vote4me()
    {
        _SelfMachine.beCandidate(_RaftDao);
        mElectTask = _TimeWheel.acquire(this, _ElectSchedule);
        _Logger.info("follower => candidate %s", _SelfMachine);
    }

    private void beLeader()
    {
        _SelfMachine.beLeader(_RaftDao);
        mHeartbeatTask = _TimeWheel.acquire(this, _HeartbeatSchedule);
        _Logger.info("be leader=>%s", _SelfMachine);
    }

    private void tickCancel()
    {
        if (mTickTask != null) {
            _Logger.info("cancel tick");
            mTickTask.cancel();
        }
    }

    private void heartbeatCancel()
    {
        if (mHeartbeatTask != null) {
            _Logger.info("cancel heartbeat");
            mHeartbeatTask.cancel();
        }
    }

    private void electCancel()
    {
        if (mElectTask != null) {
            _Logger.info("cancel elect");
            mElectTask.cancel();
        }
    }

    private X7F_RaftResponse rejectAndStepDown(long term, RaftCode raftCode)
    {
        _SelfMachine.follow(term, _RaftDao);
        mTickTask = _TimeWheel.acquire(this, _TickSchedule);
        return reject(raftCode);
    }

    public X7F_RaftResponse merge(IRaftMachine update)
    {
        X7F_RaftResponse response = null;
        //RaftGraph中不存LEARNER节点,所以集群广播和RPC时不涉及LEARNER
        if (_SelfMachine.getTerm() > update.getTerm()) { return reject(LOWER_TERM); }
        if (update.getTerm() > _SelfMachine.getTerm()) {
            tickCancel();
            heartbeatCancel();
            electCancel();
            // * -> follower -> elector X72_RaftVote
            if (update.getState() == CANDIDATE) {
                if (_SelfMachine.getIndex() <= update.getIndex()
                    && _SelfMachine.getIndexTerm() <= update.getIndexTerm())
                {
                    response = stepUp(update.getPeerId(), update.getTerm());
                    _Logger.info("new term %d [follower → elector] | candidate: %#x",
                                 _SelfMachine.getTerm(),
                                 _SelfMachine.getCandidate());
                }
                else {
                    _Logger.info("less than me; reject and step down [→ follower] | mine:%d@%d > in:%d@%d",
                                 _SelfMachine.getIndex(),
                                 _SelfMachine.getIndexTerm(),
                                 update.getIndex(),
                                 update.getIndexTerm());
                    return rejectAndStepDown(update.getTerm(), OBSOLETE);
                }
            }//X7E_RaftBroadcast
            else if (update.getState() == LEADER) {
                _Logger.info("follow leader %#x leader_commit: %d", update.getPeerId(), update.getCommit());
                return follow(update.getPeerId(), update.getTerm(), update.getCommit());
            }
            //else ignore { follower elector 都不会成为update.getState的结果，所以此处忽略}
            else {
                _Logger.fetal("illegal state %s", update.getState());
                return reject(ILLEGAL_STATE);
            }
        }
        //_SelfMachine.getTerm == update.getTerm
        else switch (_SelfMachine.getState())
        {
            case FOLLOWER:
                if (update.getState() == CANDIDATE) {
                    if (_SelfMachine.getIndex() <= update.getIndex()
                        && _SelfMachine.getIndexTerm() <= update.getIndexTerm())
                    {
                        tickCancel();
                        return stepUp(update.getPeerId(), update.getTerm());
                    }
                    else {
                        _Logger.info("less than me,reject | mine:%d@%d > in:%d@%d",
                                     _SelfMachine.getIndex(),
                                     _SelfMachine.getIndexTerm(),
                                     update.getIndex(),
                                     update.getIndexTerm());
                        return reject(OBSOLETE);
                    }
                }
                else if (update.getState() == LEADER) {
                    tickCancel();
                    return follow(update.getPeerId(), update.getTerm(), update.getCommit());
                }
                else {
                    return reject(ILLEGAL_STATE);
                }
            case ELECTOR:
                if (update.getState() == CANDIDATE) {
                    //不重置elect-timer
                    if (_SelfMachine.getCandidate() != update.getPeerId()) {
                        _Logger.info("already vote [elector ×] | vote for:%#x not ♂ %#x",
                                     _SelfMachine.getCandidate(),
                                     update.getCandidate());
                        return reject(ALREADY_VOTE);
                    }
                    else {
                        _Logger.warning("already vote for %#x", update.getPeerId());
                        //response => null ignore
                    }
                }
                if (update.getState() == LEADER) {
                    electCancel();
                    return follow(update.getPeerId(), update.getTerm(), update.getCommit());
                }
                break;
            case CANDIDATE:
                if (update.getState() == CANDIDATE) {
                    if (_SelfMachine.getIndex() < update.getIndex()
                        || _SelfMachine.getIndexTerm() < update.getIndexTerm())
                    {
                        _Logger.warning("other %#x better; candidate => elector");
                        electCancel();
                        return stepUp(update.getPeerId(), update.getTerm());
                    }
                    else if (_SelfMachine.getIndex() > update.getIndex()
                             || _SelfMachine.getIndexTerm() > update.getIndexTerm())
                    {
                        return reject(OBSOLETE);
                    }
                    else return reject(ALREADY_VOTE);
                }
                else if (update.getState() == LEADER) {
                    electCancel();
                    return follow(update.getPeerId(), update.getTerm(), update.getCommit());
                }
                break;
            case LEADER:
                if (update.getState() == LEADER) {
                    if (update.getPeerId() != _SelfMachine.getPeerId()) {
                        _Logger.fetal("add new peer more than old-graph-size / 2");
                        heartbeatCancel();
                        return rejectAndStepDown(update.getTerm(), SPLIT_CLUSTER);
                    }
                    else {
                        _Logger.warning("check raft graph & broadcast leader self->self");
                        break;
                    }
                }
                else if (update.getState() == CANDIDATE) {
                    _Logger.warning("same term %d,late recv X72 from %#x", update.getTerm(), update.getPeerId());
                    return reject(ALREADY_VOTE);
                }
            case LEARNER:
                break;
        }
        return response;
    }

    public IPair onResponse(long peerId,
                            long term,
                            long index,
                            long candidate,
                            RaftState state,
                            RaftCode code,
                            ISessionManager<ZContext> manager)
    {
        RaftMachine peerMachine = _RaftGraph.getMachine(peerId);
        if (peerMachine == null) {
            _Logger.warning("peer %#x is not found", peerId);
            return null;
        }
        peerMachine.setIndex(index);
        peerMachine.setTerm(term);
        peerMachine.setState(state);
        peerMachine.setCandidate(candidate);
        switch (code)
        {
            case SUCCESS:
                if (_SelfMachine.getState() == CANDIDATE) {
                    //granted
                    if (_RaftGraph.isMajorAccept(_SelfMachine.getPeerId(), _SelfMachine.getTerm())) {
                        electCancel();
                        beLeader();
                        return new Pair<>(createBroadcasts().toArray(X7E_RaftBroadcast[]::new), null);
                    }
                }
                else if (_SelfMachine.getState() == LEADER) {
                    peerMachine.setMatchIndex(index);
                    X7E_RaftBroadcast x7e = null;
                    if (peerMachine.getIndex() < _SelfMachine.getIndex()) {
                        //append log -> follower
                        x7e = createBroadcast(peerMachine);
                        x7e.setSession(manager.findSessionByPrefix(peerId));
                    }
                    LogEntry logEntry = _RaftDao.getEntry(index);
                    if (index > _SelfMachine.getCommit()
                        && logEntry.getTerm() == _SelfMachine.getTerm()
                        && _RaftGraph.isMajorAccept(_SelfMachine.getPeerId(), _SelfMachine.getTerm(), index))
                    {
                        _SelfMachine.commit(index, _RaftDao);
                        X76_RaftResult x76 = new X76_RaftResult(_ZUID.getId());
                        x76.setPayloadSerial(logEntry.getPayloadSerial());
                        x76.setCode(SUCCESS.getCode());
                        x76.setPayload(logEntry.getPayload());
                        x76.setOrigin(logEntry.getOrigin());
                        _Logger.info("consensus done->");
                        if (logEntry.getRaftClientId() != _SelfMachine.getPeerId()) {
                            //leader -> follower -> client
                            x76.setSession(manager.findSessionByPrefix(logEntry.getRaftClientId()));
                            return x7e != null ? new Pair<>(new IControl[] { x7e,
                                                                             x76 },
                                                            null)
                                               : new Pair<>(new IControl[] { x76 }, null);
                        }
                        else {
                            //leader -> client
                            return x7e != null ? new Pair<>(new IControl[] { x7e }, x76)
                                               : new Pair<>(null, x76);
                        }
                    }
                }
                break;
            case LOWER_TERM:
                tickCancel();
                heartbeatCancel();
                electCancel();
                _Logger.info("self.term %d < response.term %d => step_down",
                             _SelfMachine.getTerm(),
                             peerMachine.getTerm());
                stepDown();
                break;
            case CONFLICT:
                if (_SelfMachine.getState() == LEADER) {
                    _Logger.info("follower %#x,match failed,rollback %d",
                                 peerMachine.getPeerId(),
                                 peerMachine.getIndex());
                    return new Pair<>(new X7E_RaftBroadcast[] { createBroadcast(peerMachine, 1) }, null);
                }
                else {
                    _Logger.warning("self %#x is old leader & send logs=> %#x,next-index wasn't catchup");
                    //Ignore
                }
                break;
            case ILLEGAL_STATE:
                //Ignore 
                if (_SelfMachine.getState() == CANDIDATE) {
                    _Logger.info("my term is over,the leader will be %#x and receive x7E_raftBroadcast then step_down",
                                 peerMachine.getPeerId());
                }
                break;
            case ALREADY_VOTE:
                if (_SelfMachine.getState() == CANDIDATE
                    && _RaftGraph.isMajorReject(_SelfMachine.getPeerId(), _SelfMachine.getTerm()))
                {
                    _Logger.info("major reject step down => follower");
                    electCancel();
                    stepDown();
                }
                /*
                    收到 X7F_Response的时候可能已经先收到X7E_Broadcast,从而已经转换为Follower状态，忽略这个
                    response
                */
                break;
            case OBSOLETE:
                electCancel();
                if (_SelfMachine.getState() == CANDIDATE) {
                    _Logger.info("self obsolete => follower");
                    stepDown();
                }
                break;
        }
        return null;
    }

    public List<LogEntry> diff()
    {
        long start = _SelfMachine.getApplied();
        long commit = _SelfMachine.getCommit();
        long current = _SelfMachine.getIndex();
        if (start >= commit) {
            return null;
        }
        else {
            long end = min(current, commit);
            List<LogEntry> result = new ArrayList<>((int) (end - start));
            for (long i = start; i <= end; i++) {
                LogEntry entry = _RaftDao.getEntry(i);
                if (entry != null) result.add(entry);
            }
            return result.isEmpty() ? null
                                    : result;
        }
    }

    private void commit(long commit)
    {
        _SelfMachine.setCommit(commit);
        _RaftDao.updateLogCommit(commit);
    }

    private boolean catchUp()
    {
        if (_SelfMachine.getState() == LEADER) {
            _Logger.warning("leader needn't catch up any log.");
            return false;
        }
        ITERATE_APPEND:
        {
            for (Iterator<LogEntry> it = _AppendLogQueue.iterator(); it.hasNext();) {
                LogEntry entry = it.next();
                if (_RaftDao.append(entry)) {
                    _SelfMachine.appendLog(entry.getIndex(), entry.getTerm());
                    _Logger.info("catch up %d@%d", entry.getIndex(), entry.getTerm());
                }
                else {
                    LogEntry old = _RaftDao.getEntry(entry.getIndex());
                    if (old == null || old.getTerm() != entry.getTerm()) {
                        if (old != null) {
                            _Logger.warning("log conflict [old: %d@%d <=>new: %d@%d ]",
                                            entry.getIndex(),
                                            old.getTerm(),
                                            entry.getIndex(),
                                            entry.getTerm());
                        }
                        else {
                            _Logger.warning("log %d miss,self %s", entry.getIndex(), _SelfMachine);
                        }
                        /*
                           此处存在一个可优化点，将此冲突对应的 term 下所有的index 都注销，向leader反馈
                           上一个term.end_index,能有效减少以-1进行删除的数据量。此种优化的意义不是很大。
                        */
                        long newEndIndex = entry.getIndex() - 1;
                        LogEntry rollback = _RaftDao.truncateSuffix(newEndIndex);
                        if (rollback != null) {
                            _SelfMachine.appendLog(rollback.getIndex(), rollback.getTerm());
                            _Logger.warning("machine rollback %d@%d", rollback.getIndex(), rollback.getTerm());
                        }
                        else {
                            /*
                            回滚异常，但是不会出现 newEndIndex < _RaftDao.start 的情况，否则说明
                            集群中的大多数都是错误的数据，在此之前一定出现了大规模集群失败，且丢失正确的数据的情况
                            */
                            _Logger.fetal("cluster failed over flow,lost data.");
                        }
                        break ITERATE_APPEND;
                    }
                    else {
                        /*
                            old != null && old.term == entry.term && old.index == entry.index
                            1.old ==  null entry.index 处于 未正常管控的位置
                            启动协商之后是不会出现此类情况的。
                            2.old.term==entry.term已经完成了日志存储，不再重复append 
                         */
                        _Logger.info("already has the log -> %d@%d | ignore", entry.getIndex(), entry.getTerm());
                    }
                }
                _SelfMachine.apply(_RaftDao);
                it.remove();
            }
            return true;
            //end of IT_APPEND
        }
        _AppendLogQueue.clear();
        return false;
    }

    public List<X72_RaftVote> checkVoteState(RaftMachine update)
    {
        if (update.getPeerId() == _SelfMachine.getPeerId()
            && update.getTerm() == _SelfMachine.getTerm() + 1
            && update.getIndex() == _SelfMachine.getIndex()
            && update.getIndexTerm() == _SelfMachine.getIndexTerm()
            && update.getCandidate() == _SelfMachine.getPeerId()
            && update.getCommit() == _SelfMachine.getCommit()
            && _SelfMachine.getState() == FOLLOWER
            && update.getState() == CANDIDATE)
        {
            vote4me();
            return _RaftGraph.getNodeMap()
                             .keySet()
                             .stream()
                             .filter(peerId -> peerId != _SelfMachine.getPeerId())
                             .map(peerId ->
                             {
                                 X72_RaftVote x72 = new X72_RaftVote(_ZUID.getId());
                                 x72.setPeerId(update.getPeerId());
                                 x72.setTerm(update.getTerm());
                                 x72.setLogIndex(update.getIndex());
                                 x72.setLogTerm(update.getIndexTerm());
                                 x72.setElector(peerId);
                                 x72.setCommit(update.getCommit());
                                 return x72;
                             })
                             .collect(Collectors.toList());
        }
        _Logger.warning("machine state checked failed { %s }", _SelfMachine);
        return null;
    }

    public List<X7E_RaftBroadcast> checkLogAppend(RaftMachine update)
    {
        if (_SelfMachine.getState() == LEADER
            && _SelfMachine.getPeerId() == update.getPeerId()
            && _SelfMachine.getTerm() >= update.getTerm()
            && _SelfMachine.getIndex() >= update.getIndex()
            && _SelfMachine.getIndexTerm() >= update.getIndexTerm()
            && _SelfMachine.getCommit() >= update.getCommit())
        {
            return createBroadcasts().collect(Collectors.toList());
        }
        return null;
    }

    public void appendLogs(List<LogEntry> entryList)
    {
        //offer all
        _AppendLogQueue.addAll(entryList);
    }

    public Stream<X7E_RaftBroadcast> newLogEntry(IProtocol request, long raftClientId, long origin)
    {
        return newLogEntry(request.serial(), request.encode(), raftClientId, origin);
    }

    public Stream<X7E_RaftBroadcast> newLogEntry(int requestSerial, byte[] requestData, long raftClientId, long origin)
    {
        LogEntry newEntry = new LogEntry(_SelfMachine.getTerm(),
                                         _SelfMachine.getIndex() + 1,
                                         raftClientId,
                                         origin,
                                         requestSerial,
                                         requestData);
        if (_RaftDao.append(newEntry)) {
            _SelfMachine.increaseIndex();
            return createBroadcasts();
        }
        _Logger.fetal("Raft WAL failed!");
        throw new IllegalStateException("Raft WAL failed!");
    }

    private Stream<X7E_RaftBroadcast> createBroadcasts()
    {
        return _RaftGraph.getNodeMap()
                         .values()
                         .stream()
                         .filter(other -> other.getPeerId() != _SelfMachine.getPeerId())
                         .map(this::createBroadcast);
    }

    private X7E_RaftBroadcast createBroadcast(RaftMachine follower)
    {
        return createBroadcast(follower, -1);
    }

    private X7E_RaftBroadcast createBroadcast(RaftMachine follower, int limit)
    {
        X7E_RaftBroadcast x7e = new X7E_RaftBroadcast(_ZUID.getId());
        x7e.setPeerId(_SelfMachine.getPeerId());
        x7e.setTerm(_SelfMachine.getTerm());
        x7e.setCommit(_SelfMachine.getCommit());
        x7e.setPreIndex(_SelfMachine.getIndex());
        x7e.setPreIndexTerm(_SelfMachine.getIndexTerm());
        x7e.setFollower(follower.getPeerId());
        if (follower.getIndex() == _SelfMachine.getIndex()) { return x7e; }
        List<LogEntry> entryList = new LinkedList<>();
        LogEntry logEntry = _RaftDao.getEntry(follower.getIndex() + 1);
        if (logEntry == null) {
            _Logger.warning("leader truncate prefix，wait for installing snapshot");
            return x7e;//leader 也没有这么久远的数据需要等install snapshot之后才能恢复正常同步
        }
        x7e.setPreIndex(logEntry.getIndex());
        x7e.setPreIndexTerm(logEntry.getTerm());
        entryList.add(logEntry);
        for (long i = logEntry.getIndex() + 1, l = _SelfMachine.getIndex(); i <= l; i++) {
            if (limit > 0 && entryList.size() >= limit) {
                break;
            }
            entryList.add(_RaftDao.getEntry(i));
        }
        x7e.setPayload(JsonUtil.writeValueAsBytes(entryList));
        return x7e;
    }

    public boolean isClusterModel()
    {
        return _ClusterConfig.isClusterModel();
    }
}
