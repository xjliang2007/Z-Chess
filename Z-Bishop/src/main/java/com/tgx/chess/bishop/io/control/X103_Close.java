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
package com.tgx.chess.bishop.io.control;

import com.tgx.chess.bishop.io.websokcet.WsControl;
import com.tgx.chess.bishop.io.websokcet.WsFrame;

/**
 * @author William.d.zk
 */
public class X103_Close
        extends
        WsControl
{
    public final static int COMMAND = 0x103;

    public X103_Close()
    {
        super(COMMAND);
        mCtrlCode = WsFrame.frame_op_code_ctrl_close;
    }

    public X103_Close(byte[] payload)
    {
        super(COMMAND, payload);
        mCtrlCode = WsFrame.frame_op_code_ctrl_close;
    }

    @Override
    public X103_Close duplicate()
    {
        return new X103_Close(getPayload());
    }

}