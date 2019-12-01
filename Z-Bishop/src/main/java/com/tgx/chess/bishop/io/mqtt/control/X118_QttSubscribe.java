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

package com.tgx.chess.bishop.io.mqtt.control;

import static com.tgx.chess.queen.io.core.inf.IQoS.Level.AT_LEAST_ONCE;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.tgx.chess.bishop.io.mqtt.bean.QttCommand;
import com.tgx.chess.king.base.inf.IPair;
import com.tgx.chess.king.base.util.IoUtil;
import com.tgx.chess.king.base.util.Pair;

/**
 * @author william.d.zk
 * @date 2019-05-30
 */
public class X118_QttSubscribe
        extends
        QttCommand
{

    public final static int COMMAND = 0x118;

    public X118_QttSubscribe()
    {
        super(COMMAND);
        setCtrl(generateCtrl(false, false, AT_LEAST_ONCE, QTT_TYPE.SUBSCRIBE));
    }

    @Override
    public boolean isMapping()
    {
        return true;
    }

    @Override
    public int getPriority()
    {
        return QOS_PRIORITY_06_META_CREATE;
    }

    @Override
    public int dataLength()
    {
        int length = super.dataLength();
        for (IPair pair : _Topics) {
            String topic = pair.first();
            //2byte UTF-8 length 1byte Qos-lv
            length += 3 + topic.getBytes(StandardCharsets.UTF_8).length;
        }
        return length;
    }

    private final List<Pair<String,
                            Level>> _Topics = new ArrayList<>(3);

    public List<Pair<String,
                     Level>> getTopics()
    {
        return _Topics;
    }

    public void setTopics(Pair<String,
                               Level>... topics)
    {
        Collections.addAll(_Topics, topics);
    }

    @Override
    public int decodec(byte[] data, int pos)
    {
        pos = super.decodec(data, pos);
        for (int size = data.length; pos < size;) {
            int utfSize = IoUtil.readUnsignedShort(data, pos);
            pos += 2;
            String topic = IoUtil.readString(data, pos, utfSize, StandardCharsets.UTF_8);
            pos += utfSize;
            Level qosLevel = Level.valueOf(data[pos++]);
            _Topics.add(new Pair<>(topic, qosLevel));
        }
        return pos;
    }

    @Override
    public int encodec(byte[] data, int pos)
    {
        pos = super.encodec(data, pos);
        for (IPair pair : _Topics) {
            String topic = pair.first();
            byte[] topicData = topic.getBytes(StandardCharsets.UTF_8);
            Level qosLevel = pair.second();
            pos += IoUtil.writeShort(topicData.length, data, pos);
            pos += IoUtil.write(topicData, data, pos);
            pos += IoUtil.writeByte(qosLevel.ordinal(), data, pos);
        }
        return pos;
    }

    @Override
    public String toString()
    {
        return String.format("subscribe local-id:%d topics:%s", getMsgId(), _Topics);
    }
}