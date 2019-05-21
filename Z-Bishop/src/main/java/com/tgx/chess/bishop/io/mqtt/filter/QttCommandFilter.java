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

package com.tgx.chess.bishop.io.mqtt.filter;

import com.tgx.chess.bishop.io.mqtt.bean.QttContext;
import com.tgx.chess.bishop.io.mqtt.bean.QttFrame;
import com.tgx.chess.bishop.io.zprotocol.BaseCommand;
import com.tgx.chess.queen.io.core.async.AioFilterChain;

/**
 * @author william.d.zk
 * @date 2019-05-08
 */
public class QttCommandFilter
        extends
        AioFilterChain<QttContext, BaseCommand<QttContext>, QttFrame>
{
    public QttCommandFilter() {
        super("mqtt-command-filter");
    }

    @Override
    public QttFrame encode(QttContext context, BaseCommand<QttContext> output) {
        QttFrame frame = new QttFrame();
        frame.setPayload(output.encode(context));
        //        frame.setCtrl(output.getCtrl());
        return frame;
    }

    @Override
    public BaseCommand<QttContext> decode(QttContext context, QttFrame input) {

        return null;
    }

    @Override
    public ResultType preEncode(QttContext context, BaseCommand<QttContext> output) {

        return null;
    }

    @Override
    public ResultType preDecode(QttContext context, QttFrame input) {

        return null;
    }

}
