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

package com.isahl.chess.bishop.protocol.zchat.model.ctrl.zls;

import com.isahl.chess.bishop.protocol.zchat.model.ctrl.ZControl;
import com.isahl.chess.board.annotation.ISerialGenerator;
import com.isahl.chess.board.base.ISerial;
import com.isahl.chess.king.base.content.ByteBuf;

/**
 * @author William.d.zk
 */
@ISerialGenerator(parent = ISerial.PROTOCOL_BISHOP_CONTROL_SERIAL,
                  serial = 0x06)
public class X06_EncryptComp
        extends ZControl
{
    private int mCode;

    public X06_EncryptComp withCode(int code)
    {
        mCode = code;
        return this;
    }

    @Override
    public int prefix(ByteBuf input)
    {
        int remain = super.prefix(input);
        mCode = input.getInt();
        return remain - 4;
    }

    @Override
    public ByteBuf suffix(ByteBuf output)
    {
        return super.suffix(output)
                    .putInt(mCode);
    }

    @Override
    public int length()
    {
        return super.length() + 4;
    }

    public int getCode()
    {
        return mCode;
    }

    @Override
    public Level level()
    {
        return Level.AT_LEAST_ONCE;
    }
}
