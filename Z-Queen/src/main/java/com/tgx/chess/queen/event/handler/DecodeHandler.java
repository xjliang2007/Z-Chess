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
package com.tgx.chess.queen.event.handler;

import static com.tgx.chess.queen.event.inf.IError.Type.FILTER_DECODE;
import static com.tgx.chess.queen.event.inf.IOperator.Type.DISPATCH;
import static com.tgx.chess.queen.io.core.inf.IContext.DECODE_ERROR;

import java.util.Arrays;

import com.tgx.chess.king.base.inf.IPair;
import com.tgx.chess.king.base.inf.ITriple;
import com.tgx.chess.king.base.log.Logger;
import com.tgx.chess.king.base.util.Pair;
import com.tgx.chess.queen.event.inf.IOperator;
import com.tgx.chess.queen.event.inf.IPipeEventHandler;
import com.tgx.chess.queen.event.processor.QEvent;
import com.tgx.chess.queen.io.core.inf.IContext;
import com.tgx.chess.queen.io.core.inf.IControl;
import com.tgx.chess.queen.io.core.inf.IEncryptHandler;
import com.tgx.chess.queen.io.core.inf.IPacket;
import com.tgx.chess.queen.io.core.inf.ISession;

/**
 * @author William.d.zk
 */
public class DecodeHandler<C extends IContext<C>>
        implements
        IPipeEventHandler<QEvent>
{
    protected final Logger        _Logger = Logger.getLogger(getClass().getName());
    private final IEncryptHandler _EncryptHandler;

    public DecodeHandler(IEncryptHandler encryptHandler)
    {
        _EncryptHandler = encryptHandler;
    }

    /**
     * 错误由接下去的 Handler 负责投递 Close 事件给 IoDispatcher
     *
     * @see IoDispatcher
     */
    @Override
    public void onEvent(QEvent event, long sequence, boolean batch) throws Exception
    {
        /*
        错误事件已在同级旁路中处理，此处不再关心错误处理
         */
        IPair packetContent = event.getContent();
        ISession<C> session = packetContent.second();
        IOperator<IPacket,
                  ISession<C>,
                  ITriple> packetOperator = event.getEventOp();
        C context = session.getContext();
        context.setEncryptHandler(_EncryptHandler);
        IPacket packet = packetContent.first();
        if (!context.isInErrorState()) {
            try {
                ITriple result = packetOperator.handle(packet, session);
                IControl[] commands = result.first();
                _Logger.info("decoded commands:\n%s", Arrays.toString(commands));
                transfer(event, commands, session, result.third());
            }
            catch (Exception e) {
                _Logger.warning(String.format("read decode error\n%s", session.toString()), e);
                context.setInState(DECODE_ERROR);
                //此处为Pipeline中间环节，使用event进行事件传递，不使用dispatcher
                event.error(FILTER_DECODE,
                            new Pair<>(e, session),
                            session.getContext()
                                   .getError());
            }
        }
        else {
            event.ignore();
        }
    }

    protected void transfer(QEvent event,
                            IControl[] commands,
                            ISession<C> session,
                            IOperator<IControl[],
                                      ISession<C>,
                                      ITriple> operator)
    {
        event.produce(DISPATCH, new Pair<>(commands, session), operator);
    }
}
