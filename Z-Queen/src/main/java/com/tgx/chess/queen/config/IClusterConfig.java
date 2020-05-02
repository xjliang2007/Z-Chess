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

package com.tgx.chess.queen.config;

/**
 * @author william.d.zk
 * @date 2020/4/23
 */
public interface IClusterConfig
{
    /**
     * pipeline decoder 分配的固定配额
     *
     * @return
     */
    int getDecoderCount();

    /**
     * pipeline encoder 分配的固定配额
     */
    int getEncoderCount();

    /**
     * pipeline 逻辑处理单元数量
     *
     * @return
     */
    int getLogicCount();

    /**
     * 用于处理集群通讯的处理器单元数
     *
     * @return
     */
    int getClusterIoCount();

    /**
     * AIO 处理队列的阶乘数
     *
     * @return
     */
    int getAioQueuePower();

    /**
     * Cluster相关处理队列的阶乘数
     *
     * @return
     */
    int getClusterPower();

    /**
     * 逻辑处理单元的处理队列阶乘数
     *
     * @return
     */
    int getLogicPower();

    /**
     * 异常处理单元的处理队列阶乘数
     *
     * @return
     */
    int getErrorPower();

    /**
     * 处理主动关闭时间的队列阶乘数
     *
     * @return
     */
    int getCloserPower();

    default int getPoolSize()
    {
        return 1 // aioDispatch
               + getDecoderCount() // read-decode
               + 1 // cluster-single
               + getLogicCount()// logic-
               + 1 // decoded-dispatch
               + 1 // write-dispatch
               + getEncoderCount()// write-encode
               + 1 // write-end
        ;
    }
}