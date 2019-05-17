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
package com.tgx.chess.queen.io.core.async;

import java.util.Objects;

import com.tgx.chess.queen.io.core.inf.ICommand;
import com.tgx.chess.queen.io.core.inf.IContext;
import com.tgx.chess.queen.io.core.inf.IControl;
import com.tgx.chess.queen.io.core.inf.IFilter;
import com.tgx.chess.queen.io.core.inf.IFilterChain;
import com.tgx.chess.queen.io.core.inf.IFrame;
import com.tgx.chess.queen.io.core.inf.IProtocol;

/**
 * @author William.d.zk
 */
public abstract class AioFilterChain<C extends IContext, O extends IProtocol, I extends IProtocol>
        implements
        IFilterChain<C>,
        IFilter<C, O, I>
{

    private final String    _Name;
    private IFilterChain<C> next;
    private IFilterChain<C> previous;

    private int             mIdempotent = 0x80000000;

    protected AioFilterChain(String name) {
        _Name = name;
    }

    @Override
    public int getIdempotentBit() {
        return mIdempotent;
    }

    @Override
    public void idempotentRightShift(int previous) {
        if (previous == 1) { throw new IllegalArgumentException(); }
        mIdempotent = previous == 0 && mIdempotent == 0x80000000 ? 1 : previous != 0 ? previous >>> 1 : mIdempotent;
    }

    @Override
    public IFilterChain<C> getPrevious() {
        return previous;
    }

    @Override
    public void setPrevious(IFilterChain<C> previous) {
        this.previous = previous;
    }

    @Override
    public IFilterChain<C> getNext() {
        return next;
    }

    @Override
    public void setNext(IFilterChain<C> next) {
        this.next = next;
    }

    @Override
    public IFilterChain<C> getChainHead() {
        IFilterChain<C> node = previous;
        while (node != null && node.getPrevious() != null) {
            node = node.getPrevious();
        }
        return node == null ? this : node;
    }

    @Override
    public IFilterChain<C> getChainTail() {
        IFilterChain<C> node = next;
        while (node != null && node.getNext() != null) {
            node = node.getNext();
        }
        return node == null ? this : node;
    }

    @Override
    public IFilterChain<C> linkAfter(IFilterChain<C> current) {
        if (current == null) { return this; }
        current.setNext(this);
        setPrevious(current);
        idempotentRightShift(current.getIdempotentBit());
        return this;
    }

    @Override
    public IFilterChain<C> linkFront(IFilterChain<C> current) {
        if (current == null) { return this; }
        current.setPrevious(this);
        setNext(current);
        current.idempotentRightShift(getIdempotentBit());
        return current;
    }

    @Override
    public void dispose() {
        IFilterChain<C> nextNext;
        IFilterChain<C> next = this.next;
        while (next != null) {
            nextNext = next.getNext();
            next.setNext(null);
            next = nextNext;
        }
    }

    protected ResultType preCommandEncode(C context, ICommand<C> output) {
        if (Objects.isNull(output) || Objects.isNull(context)) { return ResultType.ERROR; }
        if (context.isOutConvert()) {
            switch (output.superSerial()) {
                case IProtocol.COMMAND_SERIAL:
                    return ResultType.NEXT_STEP;
                case IProtocol.CONTROL_SERIAL:
                case IProtocol.FRAME_SERIAL:
                    return ResultType.IGNORE;
                default:
                    return ResultType.ERROR;
            }
        }
        return ResultType.IGNORE;
    }

    protected ResultType preCommandDecode(C context, IFrame input) {
        if (context == null || input == null) { return ResultType.ERROR; }
        return context.isInConvert() && input.isNoCtrl() ? ResultType.HANDLED : ResultType.IGNORE;
    }

    protected ResultType preFrameEncode(C context, ICommand<C> output) {
        if (Objects.isNull(context) || Objects.isNull(output)) { return ResultType.ERROR; }
        return context.isOutConvert() ? output.superSerial() == IProtocol.FRAME_SERIAL ? ResultType.NEXT_STEP : ResultType.ERROR
                                      : ResultType.IGNORE;
    }

    protected ResultType preFrameDecode(C context, IFrame input) {
        if (Objects.isNull(context) || Objects.isNull(input)) { return ResultType.ERROR; }
        return input.isNoCtrl() || !context.isInConvert() ? ResultType.IGNORE : ResultType.HANDLED;

    }

    protected ResultType preControlEncode(C context, IControl<C> output) {

    }

    protected ResultType preControlDecode(C context, IFrame input) {

    }

    @Override
    @SuppressWarnings("unchecked")
    public IFilter<C, O, I> getFilter() {
        return this;
    }

    @Override
    public String getName() {
        return _Name;
    }
}
