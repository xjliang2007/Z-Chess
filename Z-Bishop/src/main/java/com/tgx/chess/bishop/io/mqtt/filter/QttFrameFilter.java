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
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tgx.chess.bishop.io.mqtt.filter;

import java.nio.ByteBuffer;

import com.tgx.chess.bishop.io.mqtt.QttFrame;
import com.tgx.chess.bishop.io.zfilter.ZContext;
import com.tgx.chess.queen.io.core.async.AioFilterChain;
import com.tgx.chess.queen.io.core.async.AioPacket;
import com.tgx.chess.queen.io.core.inf.IPacket;
import com.tgx.chess.queen.io.core.inf.IProtocol;

/**
 * @author william.d.zk
 * @date 2019-05-07
 */
public class QttFrameFilter
        extends
        AioFilterChain<ZContext,
                       QttFrame,
                       IPacket>
{
    public final static String NAME = "mqtt_frame";

    public QttFrameFilter()
    {
        super(NAME);
    }

    @Override
    public boolean checkType(IProtocol protocol)
    {
        return checkType(protocol, IPacket.PACKET_SERIAL);
    }

    @Override
    public ResultType preEncode(ZContext context, IProtocol output)
    {
        return preFrameEncode(context, output);
    }

    @Override
    public IPacket encode(ZContext context, QttFrame output)
    {
        ByteBuffer toWrite = ByteBuffer.allocate(output.dataLength());
        toWrite.position(output.encodec(toWrite.array(), toWrite.position()))
               .flip();
        return new AioPacket(toWrite);
    }

    @Override
    public ResultType preDecode(ZContext context, IPacket input)
    {
        ResultType result = preFrameDecode(context, input);
        if (ResultType.NEXT_STEP.equals(result)) {
            ByteBuffer recvBuf = input.getBuffer();
            ByteBuffer cRvBuf = context.getRvBuffer();
            QttFrame carrier = context.getCarrier();
            int lack = context.lack();
            switch (context.position())
            {
                case -1:
                    if (lack > 0 && !recvBuf.hasRemaining()) { return ResultType.NEED_DATA; }
                    context.setCarrier(carrier = new QttFrame());
                    byte value = recvBuf.get();
                    carrier.setCtrl(value);
                    lack = context.lackLength(1, 1);
                case 0:
                    if (lack > 0 && !recvBuf.hasRemaining()) { return ResultType.NEED_DATA; }
                    carrier.setLengthCode(recvBuf.get());
                    lack = context.lackLength(1,
                                              carrier.payloadLengthLack(context.position()) + context.position() + 1);
                case 1:
                default:
                    if (lack > 0 && !recvBuf.hasRemaining()) { return ResultType.NEED_DATA; }
                    int target = context.position() + lack;
                    do {
                        if (carrier.isLengthCodeLack()) {
                            carrier.setLengthCode(recvBuf.get());
                            lack = context.lackLength(1,
                                                      target = carrier.payloadLengthLack(context.position())
                                                               + context.position()
                                                               + 1);
                        }
                        else {
                            int length = Math.min(recvBuf.remaining(), lack);
                            for (int i = 0; i < length; i++) {
                                cRvBuf.put(recvBuf.get());
                            }
                            lack = context.lackLength(length, target);
                            if (lack > 0 && !recvBuf.hasRemaining()) { return ResultType.NEED_DATA; }
                            if (carrier.getPayloadLength() > 0) {
                                byte[] payload = new byte[carrier.getPayloadLength()];
                                cRvBuf.flip();
                                cRvBuf.get(payload);
                                carrier.setPayload(payload);
                            }
                            cRvBuf.clear();
                            return ResultType.NEXT_STEP;
                        }
                    }
                    while (recvBuf.hasRemaining());
                    return ResultType.NEED_DATA;
            }
        }
        return result;
    }

    @Override
    public QttFrame decode(ZContext context, IPacket input)
    {
        QttFrame frame = context.getCarrier();
        context.finish();
        return frame;
    }
}
