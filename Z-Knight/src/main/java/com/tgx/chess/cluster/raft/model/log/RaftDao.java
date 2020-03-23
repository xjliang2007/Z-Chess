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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.tgx.chess.bishop.ZUID;
import com.tgx.chess.cluster.raft.IRaftDao;
import com.tgx.chess.config.ZRaftStorageConfig;
import com.tgx.chess.king.base.exception.ZException;
import com.tgx.chess.king.base.log.Logger;

@Component
public class RaftDao
        implements
        IRaftDao
{
    private final static long            TERM_NAN          = -1;
    private final static long            INDEX_NAN         = -1;
    private final Logger                 _Logger           = Logger.getLogger(RaftDao.class.getName());
    private final ZRaftStorageConfig     _Config;
    private final String                 _BaseDir;
    private final String                 _LogDataDir;
    private final String                 _LogMetaDir;
    private final String                 _SnapshotDir;
    private final int                    _MaxSegmentSize;
    private final TreeMap<Long,
                          Segment>       _Index2SegmentMap = new TreeMap<>();
    private LogMeta                      mLogMeta;
    private SnapshotMeta                 mSnapshotMeta;
    private volatile long                vTotalSize;

    // 表示是否正在安装snapshot，leader向follower安装，leader和follower同时处于installSnapshot状态
    private final AtomicBoolean _InstallSnapshot = new AtomicBoolean(false);
    // 表示节点自己是否在对状态机做snapshot
    private final AtomicBoolean _TakeSnapshot = new AtomicBoolean(false);
    private final Lock          _SnapshotLock = new ReentrantLock();

    @Autowired
    public RaftDao(ZRaftStorageConfig config)
    {
        _Config = config;
        _BaseDir = _Config.getBaseDir();
        _LogMetaDir = String.format("%s%s.raft", _BaseDir, File.separator);
        _LogDataDir = String.format("%s%sdata", _BaseDir, File.separator);
        _SnapshotDir = String.format("%s%s.snapshot", _BaseDir, File.separator);
        _MaxSegmentSize = _Config.getMaxSegmentSize();
    }

    @PostConstruct
    private void init()
    {
        File file = new File(_LogMetaDir);
        if (!file.exists() && !file.mkdirs()) {
            throw new SecurityException(String.format("%s check mkdir authority", _LogMetaDir));
        }
        file = new File(_LogDataDir);
        if (!file.exists() && !file.mkdirs()) {
            throw new SecurityException(String.format("%s check mkdir authority", _LogDataDir));
        }
        file = new File(_SnapshotDir);
        if (!file.exists() && !file.mkdirs()) {
            throw new SecurityException(String.format("%s check mkdir authority", _SnapshotDir));
        }
        List<Segment> segments = readSegments();
        if (segments != null) {
            for (Segment segment : segments) {
                _Index2SegmentMap.put(segment.getStartIndex(), segment);
            }
        }
        {
            String metaFileName = _LogMetaDir + File.separator + ".metadata";
            try {
                RandomAccessFile metaFile = new RandomAccessFile(metaFileName, "rw");
                mLogMeta = new LogMeta(metaFile).load();
            }
            catch (FileNotFoundException e) {
                _Logger.warning("meta file not exist, name=%s", metaFileName);
            }
            metaFileName = _SnapshotDir + File.separator + ".metadata";
            try {
                RandomAccessFile metaFile = new RandomAccessFile(metaFileName, "rw");
                mSnapshotMeta = new SnapshotMeta(metaFile).load();
            }
            catch (FileNotFoundException e) {
                _Logger.warning("meta file not exist, name=%s", metaFileName);
            }
        }
    }

    @Override
    public void updateAll()
    {
        mLogMeta.update();
        mSnapshotMeta.update();
    }

    @Override
    public long getEndIndex()
    {
        /* 
         有两种情况segment为空
         1、第一次初始化，firstLogIndex = 1，lastLogIndex = 0
         2、snapshot刚完成，日志正好被清理掉，firstLogIndex = snapshotIndex + 1， lastLogIndex = snapshotIndex
        */
        if (_Index2SegmentMap.isEmpty()) { return getStartIndex() - 1; }
        Segment lastSegment = _Index2SegmentMap.lastEntry()
                                               .getValue();
        return lastSegment.getEndIndex();
    }

    @Override
    public long getStartIndex()
    {
        return mLogMeta.getStart();
    }

    @Override
    public LogEntry getEntry(long index)
    {
        long startIndex = getStartIndex();
        long endIndex = getEndIndex();
        if (index == INDEX_NAN || index < startIndex || index > endIndex) {
            _Logger.info("index out of range, index=%d, start_index=%d, end_index=%d", index, startIndex, endIndex);
            return null;
        }
        if (_Index2SegmentMap.isEmpty()) { return null; }
        Segment segment = _Index2SegmentMap.floorEntry(index)
                                           .getValue();
        return segment.getEntry(index);
    }

    @Override
    public long getEntryTerm(long index)
    {
        LogEntry entry = getEntry(index);
        return entry == null ? TERM_NAN
                             : entry.getTerm();
    }

    @Override
    public void updateLogMeta(long term, long start, long candidate)
    {
        if (term > TERM_NAN) mLogMeta.setTerm(term);
        mLogMeta.setStart(start);
        if (candidate != ZUID.INVALID_PEER_ID) mLogMeta.setCandidate(candidate);
        mLogMeta.update();
        _Logger.info(" term:%d,first log index:%d,candidate:%d", term, start, candidate);
    }

    public void updateLogMeta(long start)
    {
        updateLogMeta(-1, start, 0);
    }

    @Override
    public void updateSnapshotMeta(long lastIncludeIndex, long lastIncludeTerm)
    {
        mSnapshotMeta.setCommit(lastIncludeIndex);
        mSnapshotMeta.setTerm(lastIncludeTerm);
        mSnapshotMeta.update();
    }

    private final static Pattern SEGMENT_NAME = Pattern.compile("z_chess_raft_seg_(\\d+)-(\\d+)_([rw])");

    private List<Segment> readSegments()
    {
        File dataDir = new File(_LogDataDir);
        if (!dataDir.isDirectory()) throw new IllegalArgumentException("_LogDataDir doesn't point to a directory");
        File[] subs = dataDir.listFiles();
        if (subs != null) {
            return Stream.of(subs)
                         .filter(sub -> !sub.isDirectory())
                         .map(sub ->
                         {
                             try {
                                 String fileName = sub.getName();
                                 _Logger.info("sub:%s", fileName);
                                 Matcher matcher = SEGMENT_NAME.matcher(fileName);
                                 if (matcher.matches()) {
                                     long start = Long.parseLong(matcher.group(1));
                                     long end = Long.parseLong(matcher.group(2));
                                     String g3 = matcher.group(3);
                                     boolean readOnly = !g3.equalsIgnoreCase("w");
                                     return new Segment(sub, start, end, !readOnly);
                                 }
                             }
                             catch (IOException e) {
                                 e.printStackTrace();
                             }
                             return null;
                         })
                         .filter(Objects::nonNull)
                         .peek(segment -> vTotalSize += segment.getFileSize())
                         .collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public long append(LogEntry entry)
    {
        Objects.requireNonNull(entry);
        long newEndIndex = getEndIndex();
        newEndIndex++;
        if (entry.getIndex() != newEndIndex) { return newEndIndex; }
        int entrySize = entry.dataLength() + 4;
        boolean isNeedNewSegmentFile = false;
        if (_Index2SegmentMap.isEmpty()) {
            isNeedNewSegmentFile = true;
        }
        else {
            Segment segment = _Index2SegmentMap.lastEntry()
                                               .getValue();
            if (!segment.isCanWrite()) {
                isNeedNewSegmentFile = true;
            }
            else if (segment.getFileSize() + entrySize >= _MaxSegmentSize) {
                isNeedNewSegmentFile = true;
                // 最后一个segment的文件close并改名
                segment.close();
            }
        }
        Segment targetSegment = null;
        if (isNeedNewSegmentFile) {
            String newFileName = String.format("z_chess_raft_seg_%020d-%020d_w", newEndIndex, 0);
            File newFile = new File(_LogDataDir + File.separator + newFileName);
            if (!newFile.exists()) {
                try {
                    if (newFile.createNewFile()) {
                        targetSegment = new Segment(newFile, newEndIndex, 0, true);
                        _Index2SegmentMap.put(newEndIndex, targetSegment);
                    }
                    else throw new IOException(String.format("create segment %s failed", newFileName));
                }
                catch (IOException e) {
                    _Logger.warning("create segment file failed %s", e, newFile.getAbsolutePath());
                    throw new ZException("raft local segment failed");
                }
            }
        }
        else {
            targetSegment = _Index2SegmentMap.lastEntry()
                                             .getValue();
        }
        Objects.requireNonNull(targetSegment);
        targetSegment.addRecord(entry);
        vTotalSize += entrySize;
        return newEndIndex;
    }

    @Override
    public void truncatePrefix(long newFirstIndex)
    {
        if (newFirstIndex <= getStartIndex()) { return; }
        long oldFirstIndex = getStartIndex();
        while (!_Index2SegmentMap.isEmpty()) {
            Segment segment = _Index2SegmentMap.firstEntry()
                                               .getValue();
            if (segment.isCanWrite()) {
                break;
            }
            if (newFirstIndex > segment.getEndIndex()) {
                try {
                    vTotalSize -= segment.drop();
                    _Index2SegmentMap.remove(segment.getStartIndex());
                }
                catch (Exception ex2) {
                    _Logger.warning("delete file exception:", ex2);
                }
            }
            else {
                break;
            }
        }
        long newActualFirstIndex;
        if (_Index2SegmentMap.isEmpty()) {
            newActualFirstIndex = newFirstIndex;
        }
        else {
            newActualFirstIndex = _Index2SegmentMap.firstKey();
        }
        updateLogMeta(newActualFirstIndex);
        _Logger.info("Truncating log from old first index %d to new first index %d",
                     oldFirstIndex,
                     newActualFirstIndex);
    }

    @Override
    public void truncateSuffix(long newEndIndex)
    {
        if (newEndIndex >= getEndIndex()) { return; }
        _Logger.info("Truncating log from old end index %d to new end index %d", getEndIndex(), newEndIndex);
        while (!_Index2SegmentMap.isEmpty()) {
            Segment segment = _Index2SegmentMap.lastEntry()
                                               .getValue();
            try {
                if (newEndIndex == segment.getEndIndex()) {
                    break;
                }
                else if (newEndIndex < segment.getStartIndex()) {
                    vTotalSize -= segment.drop();
                    _Index2SegmentMap.remove(segment.getStartIndex());
                }
                else if (newEndIndex < segment.getEndIndex()) {
                    vTotalSize -= segment.truncate(newEndIndex);
                }
            }
            catch (IOException ex) {
                _Logger.warning("truncateSuffix error ", ex);
            }
        }
    }

    public boolean isInstallSnapshot()
    {
        return _InstallSnapshot.get();
    }

    public boolean isTakeSnapshot()
    {
        return _TakeSnapshot.get();
    }

    public void lockSnapshot()
    {
        _SnapshotLock.lock();
    }

    @Override
    public LogMeta getLogMeta()
    {
        return mLogMeta;
    }

    @Override
    public SnapshotMeta getSnapshotMeta()
    {
        return mSnapshotMeta;
    }

    @Override
    public long getTotalSize()
    {
        return vTotalSize;
    }
}