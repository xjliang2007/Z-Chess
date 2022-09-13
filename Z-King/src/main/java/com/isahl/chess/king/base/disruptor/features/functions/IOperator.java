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
package com.isahl.chess.king.base.disruptor.features.functions;

import java.util.Objects;

/**
 * @author William.d.zk
 */
@FunctionalInterface
public interface IOperator<T, U, R>
{
    R handle(T t, U u);

    default <V> IOperator<T, U, V> andThen(IOperator<? super T, ? super R, ? extends V> after)
    {
        Objects.requireNonNull(after);
        return (t, u)->after.handle(t, handle(t, u));
    }

    default String getName()
    {
        return "operator.";
    }

    enum Type
    {
        NULL,
        CONNECTED,
        ACCEPTED,
        LOCAL_CLOSE,
        READ,
        WRITE,
        WROTE,
        DECODE,
        BIZ_LOCAL,
        CLUSTER_LOCAL,
        SINGLE,
        BATCH,
        LINK,
        CLUSTER,
        SERVICE,
        LOGIC,
        CONSISTENT_SERVICE,
        CONSISTENT,
        CONSISTENCY,
        CLUSTER_TOPOLOGY,
        CLUSTER_TIMER,
        DISPATCH,
        IGNORE
    }
}