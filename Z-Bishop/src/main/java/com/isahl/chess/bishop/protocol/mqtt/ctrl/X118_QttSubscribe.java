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

package com.isahl.chess.bishop.protocol.mqtt.ctrl;

import com.isahl.chess.bishop.protocol.mqtt.command.QttCommand;
import com.isahl.chess.bishop.protocol.mqtt.model.QttType;
import com.isahl.chess.board.annotation.ISerialGenerator;
import com.isahl.chess.board.base.ISerial;
import com.isahl.chess.king.base.util.IoUtil;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static com.isahl.chess.queen.io.core.features.model.session.IQoS.Level.AT_LEAST_ONCE;

/**
 * @author william.d.zk
 * @date 2019-05-30
 */
@ISerialGenerator(parent = ISerial.PROTOCOL_BISHOP_COMMAND_SERIAL,
                  serial = 0x118)
public class X118_QttSubscribe
        extends QttCommand
{

    public X118_QttSubscribe()
    {
        put(generateCtrl(false, false, AT_LEAST_ONCE, QttType.SUBSCRIBE));
    }

    private Map<String, Level> mSubscribes;

    @Override
    public boolean isMapping()
    {
        return true;
    }

    @Override
    public int priority()
    {
        return QOS_PRIORITY_06_META_CREATE;
    }

    @Override
    public int length()
    {
        int length = super.length();
        if(mSubscribes != null) {
            for(Map.Entry<String, Level> entry : mSubscribes.entrySet()) {
                String topic = entry.getKey();
                // 2byte UTF-8 length 1byte Qos-lv
                length += 3 + topic.getBytes(StandardCharsets.UTF_8).length;
            }
        }
        return length;
    }

    public Map<String, Level> getSubscribes()
    {
        return mSubscribes;
    }

    public void addSubscribe(String topic, Level level)
    {
        if(mSubscribes == null) {
            mSubscribes = new TreeMap<>();
        }
        mSubscribes.put(topic, level);
    }

    @Override
    public int decodec(byte[] data, int pos)
    {
        pos = super.decodec(data, pos);

        while(pos < data.length) {
            int utfSize = IoUtil.readUnsignedShort(data, pos);
            pos += 2;
            String topic = IoUtil.readString(data, pos, utfSize, StandardCharsets.UTF_8);
            pos += utfSize;
            Level level = Level.valueOf(data[pos++]);
            addSubscribe(topic, level);
        }
        return pos;
    }

    @Override
    public int encodec(byte[] data, int pos)
    {
        pos = super.encodec(data, pos);
        if(mSubscribes != null) {
            for(Map.Entry<String, Level> entry : mSubscribes.entrySet()) {
                byte[] topic = entry.getKey()
                                    .getBytes(StandardCharsets.UTF_8);
                Level level = entry.getValue();
                pos += IoUtil.writeShort(topic.length, data, pos);
                pos += IoUtil.write(topic, data, pos);
                pos += IoUtil.writeByte(level.getValue(), data, pos);
            }
        }
        return pos;
    }

    @Override
    public String toString()
    {
        return String.format("subscribe msg-id:%d topics:%s",
                             getMsgId(),
                             mSubscribes != null ? mSubscribes.toString() : null);
    }

}
