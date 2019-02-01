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

package com.tgx.chess.king.base.schedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.tgx.chess.king.base.log.Logger;

public class TimeWheel
{
    private Logger                _Log            = Logger.getLogger(getClass().getName());
    private final int             _SlotBitLeft    = 3;
    private final TickSlot[]      _ModHashEntriesArray;
    private final int             _HashMod;
    private final Thread          _Timer;
    private final long            _Tick;
    private final ExecutorService executorService = Executors.newWorkStealingPool(3);

    private final AtomicBoolean                     _Running      = new AtomicBoolean();
    private final ConcurrentLinkedQueue<HandleTask> _RequestQueue = new ConcurrentLinkedQueue<>();
    private volatile int                            vCurrentSlot, vCurrentLoop;

    @SuppressWarnings("unchecked")
    public TimeWheel(long tick,
                     TimeUnit timeUnit)
    {
        _Tick = timeUnit.toMillis(tick);
        _ModHashEntriesArray = new TickSlot[1 << _SlotBitLeft];
        Arrays.setAll(_ModHashEntriesArray, TickSlot::new);
        _HashMod = _ModHashEntriesArray.length - 1;
        _Timer = new Thread(() -> {
            _Running.set(true);
            long millisTick = timeUnit.toMillis(tick);
            for (long align = 0, t; _Running.get();) {
                t = System.currentTimeMillis();
                long sleep = millisTick - align;
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep);
                    }
                    catch (InterruptedException e) {}
                }
                else {
                    sleep = 0;
                }
                current_millisecond = System.currentTimeMillis();
                long duration = current_millisecond - t;
                for (Iterator<HandleTask> iterator = getTimer(getCurrentSlot()); iterator.hasNext();) {
                    HandleTask handleTask = iterator.next();
                    if (handleTask.getLoop() <= getCurrentLoop()) {
                        iterator.remove();
                        if (handleTask.getHandler()
                                      .isCycle())
                        {
                            if (!_RequestQueue.offer(new HandleTask(handleTask.getConstLoop(), handleTask.getConstTick(), handleTask.getHandler()))) {
                                _Log.warning("%s,loop:%d slot%d",
                                             Thread.currentThread()
                                                   .getName(),
                                             getCurrentLoop(),
                                             getCurrentSlot());
                            }
                        }
                        executorService.submit(handleTask);
                    }
                }

                for (Iterator<HandleTask> iterator = _RequestQueue.iterator(); iterator.hasNext();) {
                    HandleTask task = iterator.next();
                    task.acquire(vCurrentLoop, vCurrentSlot);
                    acquire(task.getSlot(), task);
                    iterator.remove();
                }

                if (duration >= sleep) {
                    if (vCurrentSlot == _HashMod) {
                        vCurrentSlot = 0;
                        vCurrentLoop++;
                    }
                    else {
                        vCurrentSlot++;
                    }
                    //此处-sleep 计算当期这次过程的偏差值
                    align = System.currentTimeMillis() - t - sleep;
                }
                else {
                    //Thread.sleep interrupt
                    align = sleep + t - System.currentTimeMillis();
                }
                //sleep 代表当前轮次已被修正过的值，如果使用millisTick 将引入新的系统误差
            }
        });
        _Timer.setName(String.format("T-%d-TimerWheel", _Timer.getId()));
        _Timer.start();
    }

    private int getCurrentLoop()
    {
        return vCurrentLoop;
    }

    private int getCurrentSlot()
    {
        return vCurrentSlot;
    }

    public <A> Future<A> acquire(long time, TimeUnit timeUnit, A attachment, ITimeoutHandler<A> handler)
    {
        int slots = (int) (timeUnit.toMillis(time) / _Tick);
        int loop = slots >>> _SlotBitLeft;
        int tick = slots & _HashMod;
        if (Objects.nonNull(handler)) handler.attach(attachment);
        HandleTask task = new HandleTask<>(loop, tick, handler);
        _Log.info("time wheel offer %s,", task);
        return _RequestQueue.offer(task) ? task
                                         : null;
    }

    public Future<Void> acquire(long time, TimeUnit timeUnit, ITimeoutHandler<Void> handler)
    {
        return acquire(time, timeUnit, null, handler);
    }

    @SuppressWarnings("unchecked")
    private <A> void acquire(int slot, HandleTask<A> handleTask)
    {
        TickSlot<A> timeSlot = _ModHashEntriesArray[slot];
        int index = Collections.binarySearch(timeSlot, handleTask);
        if (index >= 0) {
            if (timeSlot.get(index) == handleTask) _Log.warning(" %s exist in slot,drop it ", handleTask);
            else timeSlot.add(index, handleTask);
        }
        else {
            timeSlot.add(-index - 1, handleTask);
        }
    }

    @SuppressWarnings("unchecked")
    private Iterator<HandleTask> getTimer(int slot)
    {
        return _ModHashEntriesArray[slot & _HashMod].iterator();
    }

    interface IWheelItem
            extends
            Comparable<IWheelItem>
    {
        /**
         * 当前 Handler 是 Wheel 转到第 Loop 圈的时候执行超时
         *
         * @return loop
         */
        int getLoop();

        @Override
        default int compareTo(IWheelItem o)
        {
            return Integer.compare(getLoop(), o.getLoop());
        }
    }

    interface ICycle
    {
        default boolean isCycle()
        {
            return false;
        }
    }

    public interface ITimeoutHandler<A>
            extends
            Callable<A>,
            Supplier<A>,
            ICycle
    {
        default void attach(A attachment)
        {
        }

        default A call() throws Exception
        {
            return null;
        }

        default A get()
        {
            return null;
        }
    }

    private class TickSlot<V>
            extends
            ArrayList<HandleTask<V>>
    {

        private final int _Slot;

        private TickSlot(int slot)
        {
            _Slot = slot;
        }

        public int getSlot()
        {
            return _Slot;
        }
    }

    private class HandleTask<V>
            extends
            FutureTask<V>
            implements
            IWheelItem
    {

        private final ITimeoutHandler<V> _Handler;
        private final int                _Loop;
        private final int                _Tick;

        private int loop;
        private int slot;

        @Override
        public String toString()
        {
            return String.format(" acquire %d[%d] | wheel %d[%d] @%x", _Loop, _Tick, loop, slot, hashCode());
        }

        HandleTask(int loop,
                   int tick,
                   ITimeoutHandler<V> handler)
        {
            super(handler);
            _Handler = handler;
            _Loop = loop;
            _Tick = tick;
        }

        ITimeoutHandler<V> getHandler()
        {
            return _Handler;
        }

        int getSlot()
        {
            return slot;
        }

        public int getLoop()
        {
            return loop;
        }

        int getConstLoop()
        {
            return _Loop;
        }

        int getConstTick()
        {
            return _Tick;
        }

        void acquire(int currentLoop, int currentSlot)
        {
            if (_Tick + currentSlot > _HashMod) {
                loop = _Loop + currentLoop + 1;
                slot = _Tick + currentSlot - _HashMod - 1;
            }
            else {
                loop = currentLoop + _Loop;
                slot = currentSlot + _Tick;
            }
        }
    }

    public void stop()
    {
        boolean running;
        do {
            running = _Running.get();
            if (!running || _Running.compareAndSet(true, false)) {
                _Timer.interrupt();
                break;
            }
        }
        while (running);
    }

    private volatile long current_millisecond;

    public long getCurrentMillisecond()
    {
        return current_millisecond;
    }

    public long getCurrentSecond()
    {
        return TimeUnit.MILLISECONDS.toSeconds(current_millisecond);
    }

}