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

package com.tgx.chess.king.base.disruptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.DataProvider;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.tgx.chess.king.base.log.Logger;

/**
 * @author William.d.zk
 */
public class MultiBufferBatchEventProcessor<T>
        implements
        EventProcessor
{
    private final Logger     _Logger = Logger.getLogger("base.king." + getClass().getSimpleName());
    private static final int IDLE    = 0;
    private static final int HALTED  = IDLE + 1;
    private static final int RUNNING = HALTED + 1;

    private final AtomicInteger     _Running = new AtomicInteger(IDLE);
    private final DataProvider<T>[] _Providers;
    private final SequenceBarrier[] _Barriers;
    private final EventHandler<T>   _Handler;
    private final Sequence[]        _Sequences;
    private String                  threadName;

    public MultiBufferBatchEventProcessor(DataProvider<T>[] providers,
                                          SequenceBarrier[] barriers,
                                          EventHandler<T> handler)
    {
        if (providers.length != barriers.length) { throw new IllegalArgumentException(); }
        _Providers = providers;
        _Barriers = barriers;
        _Handler = handler;
        _Sequences = new Sequence[providers.length];
        for (int i = 0; i < _Sequences.length; i++) {
            _Sequences[i] = new Sequence(-1);
        }
    }

    public void setThreadName(String name)
    {
        threadName = name;
    }

    @Override
    public void run()
    {
        if (_Running.compareAndSet(IDLE, RUNNING)) {
            if (threadName != null) Thread.currentThread()
                                          .setName(threadName);
            for (SequenceBarrier barrier : _Barriers) {
                barrier.clearAlert();
            }

            final int barrierLength = _Barriers.length;
            int barrier_total_count;
            long delta;
            while (true) {
                barrier_total_count = 0;
                try {
                    for (int i = 0; i < barrierLength; i++) {
                        long available = _Barriers[i].waitFor(-1);
                        Sequence sequence = _Sequences[i];
                        long nextSequence = sequence.get() + 1;
                        if (nextSequence > available) {
                            _Logger.warning("%s process failed %d", threadName, i);
                        }
                        for (long l = nextSequence; l <= available; l++) {
                            _Handler.onEvent(_Providers[i].get(l), l, nextSequence == available);
                        }
                        sequence.set(available);
                        delta = available - nextSequence + 1;
                        barrier_total_count += delta;
                    }
                    //没有任何 前置生产者的存在事件的时候暂停 5ms 释放 CPU，不超过100个事件，将释放 CPU
                    if (barrier_total_count == 0) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                    }
                    else if (barrier_total_count < 100) {
                        Thread.yield();
                    }
                }
                catch (AlertException e) {
                    if (!isRunning()) {
                        _Logger.warning("%s =>shutdown", e, threadName);
                        break;
                    }
                }
                catch (Exception e) {
                    _Logger.warning(" %s → %d", threadName, e);
                    //keep working
                }
            }
        }
        else {
            if (_Running.get() == RUNNING) { throw new IllegalStateException("Thread is already running"); }
        }

    }

    @Override
    public Sequence getSequence()
    {
        throw new UnsupportedOperationException();
    }

    public Sequence[] getSequences()
    {
        return _Sequences;
    }

    @Override
    public void halt()
    {
        _Running.set(HALTED);
        _Barriers[0].alert();
    }

    @Override
    public boolean isRunning()
    {
        return _Running.get() != IDLE;
    }
}
