/*
 * MIT License
 *
 * Copyright (c) 2016~2020. Z-Chess
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
package com.isahl.chess.bishop.io.ws.filter;

import com.isahl.chess.bishop.io.ZContext;
import com.isahl.chess.bishop.io.ws.IWsContext;
import com.isahl.chess.bishop.io.ws.WsControl;
import com.isahl.chess.bishop.io.ws.WsFrame;
import com.isahl.chess.bishop.io.ws.control.X101_HandShake;
import com.isahl.chess.bishop.io.ws.control.X102_Close;
import com.isahl.chess.bishop.io.ws.control.X103_Ping;
import com.isahl.chess.bishop.io.ws.control.X104_Pong;
import com.isahl.chess.bishop.io.zprotocol.control.X106_Identity;
import com.isahl.chess.bishop.io.zprotocol.control.X107_Redirect;
import com.isahl.chess.queen.io.core.async.AioFilterChain;
import com.isahl.chess.queen.io.core.inf.IPContext;
import com.isahl.chess.queen.io.core.inf.IProtocol;
import com.isahl.chess.queen.io.core.inf.IProxyContext;

/**
 * @author William.d.zk
 */
public class WsControlFilter<T extends ZContext & IWsContext>
        extends
        AioFilterChain<T,
                       WsControl,
                       WsFrame>
{
    public WsControlFilter()
    {
        super("ws_control");
    }

    @Override
    public ResultType seek(T context, WsControl output)
    {
        return context.isOutConvert() ? output.serial() == X101_HandShake.COMMAND ? ResultType.ERROR //已经完成握手，又发个握手，报错
                                                                                  : ResultType.NEXT_STEP //有待完成WsControl的编码，执行下一步
                                      : ResultType.IGNORE; //X101正在通过此Filter

    }

    @Override
    public WsFrame encode(T context, WsControl output)
    {
        WsFrame frame = new WsFrame();
        _Logger.debug("control %s", output);
        frame.setPayload(output.getPayload());
        frame.setCtrl(output.getCtrl());
        return frame;
    }

    @Override
    public ResultType peek(T context, WsFrame input)
    {
        return context.isInConvert() && input.isCtrl() ? ResultType.HANDLED
                                                       : ResultType.IGNORE;
    }

    @Override
    public WsControl decode(T context, WsFrame input)
    {
        return switch (input.frame_op_code & 0x0F)
        {
            case WsFrame.frame_op_code_ctrl_close -> new X102_Close(input.getPayload());
            case WsFrame.frame_op_code_ctrl_ping -> new X103_Ping(input.getPayload());
            case WsFrame.frame_op_code_ctrl_pong -> new X104_Pong(input.getPayload());
            case WsFrame.frame_op_code_ctrl_cluster -> new X106_Identity(input.getPayload());
            case WsFrame.frame_op_code_ctrl_redirect -> new X107_Redirect(input.getPayload());
            default -> throw new UnsupportedOperationException(String.format("web socket frame with control code %d.",
                                                                             input.frame_op_code & 0x0F));
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends IPContext,
            O extends IProtocol> ResultType pipeSeek(C context, O output)
    {
        if (checkType(output, IProtocol.CONTROL_SERIAL)) {
            if (context instanceof IWsContext) {
                return seek((T) context, (WsControl) output);
            }
            else if (context.isProxy()) {
                T acting = ((IProxyContext<T>) context).getActingContext();
                return seek(acting, (WsControl) output);
            }
        }
        return ResultType.IGNORE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends IPContext,
            I extends IProtocol> ResultType pipePeek(C context, I input)
    {
        if (checkType(input, IProtocol.FRAME_SERIAL)) {
            if (context instanceof IWsContext) {
                return peek((T) context, (WsFrame) input);
            }
            else if (context.isProxy()) {
                T acting = ((IProxyContext<T>) context).getActingContext();
                if (acting instanceof IWsContext) { return peek(acting, (WsFrame) input); }
            }
        }
        return ResultType.IGNORE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends IPContext,
            O extends IProtocol,
            I extends IProtocol> I pipeEncode(C context, O output)
    {
        if (context instanceof IWsContext) {
            return (I) encode((T) context, (WsControl) output);
        }
        else if (context.isProxy()) {
            return (I) encode(((IProxyContext<T>) context).getActingContext(), (WsControl) output);
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends IPContext,
            O extends IProtocol,
            I extends IProtocol> O pipeDecode(C context, I input)
    {
        if (context instanceof IWsContext) {
            return (O) decode((T) context, (WsFrame) input);
        }
        else if (context.isProxy()) {
            return (O) decode(((IProxyContext<T>) context).getActingContext(), (WsFrame) input);
        }
        return null;
    }

}
