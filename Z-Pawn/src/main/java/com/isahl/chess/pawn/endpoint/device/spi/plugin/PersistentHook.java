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

package com.isahl.chess.pawn.endpoint.device.spi.plugin;

import com.isahl.chess.bishop.protocol.mqtt.command.X113_QttPublish;
import com.isahl.chess.king.base.cron.ScheduleHandler;
import com.isahl.chess.king.base.cron.TimeWheel;
import com.isahl.chess.king.base.cron.features.ICancelable;
import com.isahl.chess.king.base.features.model.ITriple;
import com.isahl.chess.king.base.features.model.IoSerial;
import com.isahl.chess.king.base.log.Logger;
import com.isahl.chess.king.env.ZUID;
import com.isahl.chess.knight.raft.config.IRaftConfig;
import com.isahl.chess.pawn.endpoint.device.db.central.model.MessageEntity;
import com.isahl.chess.pawn.endpoint.device.spi.IHandleHook;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author william.d.zk
 * @date 2021-08-07
 */
@Component
public class PersistentHook
        implements IHandleHook,
                   ICancelable
{
    private final static int BATCH_SIZE = 2;

    private final TimeWheel        _TimeWheel;
    private final Queue<IoSerial>  _MainQueue;
    private final List<ISubscribe> _Subscribes;

    @Autowired
    public PersistentHook(IRaftConfig raftConfig, TimeWheel timeWheel, List<ISubscribe> subscribes)
    {
        _TimeWheel = timeWheel;
        _Subscribes = subscribes;
        _MainQueue = new ConcurrentLinkedQueue<>();
    }

    @PostConstruct
    void startTimer()
    {
        _TimeWheel.acquire(this, new ScheduleHandler<>(Duration.ofSeconds(5), true, this::batchPush));
    }

    @Override
    public void afterLogic(IoSerial content, List<ITriple> results)
    {
        if(content.serial() == 0x113) {
            X113_QttPublish x113 = (X113_QttPublish) content;
            if(!x113.isDuplicate()) {
                String topic = x113.topic();
                byte[] contents = x113.payload();
                MessageEntity msgEntity = new MessageEntity();
                msgEntity.setMessage(contents);
                msgEntity.setTopic(topic);
                msgEntity.setNetAt(LocalDateTime.now());
                if(!ZUID.isClusterType(x113.session().index())) {
                    //集群扩散消息不再向DB中提交
                    msgEntity.genSummary();
                    msgEntity.setOrigin(x113.session().index());
                    _MainQueue.offer(msgEntity);
                }
            }
        }
        else {
            throw new IllegalStateException("Unexpected value: 0x" + Integer.toHexString(content.serial()));
        }
    }

    @Override
    public void afterConsume(IoSerial content)
    {
        if(content instanceof MessageEntity msg) {
            msg.genSummary();
            _MainQueue.offer(msg);
        }
    }

    private void batchPush(PersistentHook self)
    {
        if(_MainQueue.isEmpty()) return;
        List<IoSerial> cached = List.copyOf(_MainQueue);
        if(cached.size() > BATCH_SIZE) {
            for(ISubscribe subscribe : _Subscribes) {
                subscribe.onBatch(cached);
            }
            for(int i = 0; i < cached.size(); i++) {
                _MainQueue.poll();
            }
        }
        else {
            for(int i = 0; i < cached.size(); i++) {
                for(ISubscribe subscribe : _Subscribes) {
                    subscribe.onMessage(_MainQueue.poll());
                }
            }
        }

    }

    @Override
    public void cancel()
    {

    }

    public interface ISubscribe
    {
        void onBatch(List<IoSerial> contents);

        void onMessage(IoSerial content);
    }

    @Override
    public boolean isExpect(IoSerial content)
    {
        return content.serial() == 0x113 || content.serial() == 0x1D || content instanceof MessageEntity;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{for handle MessageEntity persistent}";
    }
}
