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

package com.tgx.chess.rook.biz.device.client;

import static com.tgx.chess.queen.event.inf.IOperator.Type.WRITE;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import com.tgx.chess.bishop.io.QttZSort;
import com.tgx.chess.bishop.io.mqtt.bean.QttContext;
import com.tgx.chess.bishop.io.mqtt.control.X111_QttConnect;
import com.tgx.chess.bishop.io.zcrypt.EncryptHandler;
import com.tgx.chess.king.base.inf.IPair;
import com.tgx.chess.king.base.util.Pair;
import com.tgx.chess.queen.event.inf.ISort;
import com.tgx.chess.queen.event.processor.QEvent;
import com.tgx.chess.queen.io.core.inf.ICommand;
import com.tgx.chess.queen.io.core.inf.ISession;
import com.tgx.chess.queen.io.core.inf.ISessionOption;
import com.tgx.chess.rook.io.WsZSort;

/**
 * @author william.d.zk
 * @date 2019-05-11
 */
@Component
@PropertySource("classpath:qtt.client.properties")
public class QttZClient
        extends
        BaseDeviceClient<QttContext>
{

    public QttZClient(@Value("${client.target.name}") String targetName,
                      @Value("${client.target.host}") String targetHost,
                      @Value("${client.target.port}") int targetPort) throws IOException
    {
        super(targetName, targetHost, targetPort, QttZSort.SYMMETRY);
    }

    @Override
    public QttContext createContext(ISessionOption option, ISort sort)
    {
        return new QttContext(option);
    }

    @Override
    public ICommand[] createCommands(ISession<QttContext> session)
    {
        return new ICommand[] { new X111_QttConnect() };
    }

    @PostConstruct
    @SuppressWarnings("unchecked")
    private void init()
    {
        _ClientCore.build((QEvent event, long sequence, boolean endOfBatch) ->
        {
            ICommand<QttContext>[] commands = null;
            ISession<QttContext> session = null;
            switch (event.getEventType())
            {
                case LOGIC:
                    //与 Server Node 处理过程存在较大的差异，中间去掉一个decoded dispatcher 所以此处入参为 ICommand[]
                    IPair logicContent = event.getContent();
                    commands = logicContent.first();
                    session = logicContent.second();
                    if (Objects.nonNull(commands)) {
                        commands = Stream.of(commands)
                                         .map(cmd ->
                                         {
                                             _Logger.info("recv:%x ", cmd.getSerial());
                                             switch (cmd.getSerial())
                                             {
                                                 default:
                                                     break;
                                             }
                                             return null;
                                         })
                                         .filter(Objects::nonNull)
                                         .toArray(ICommand[]::new);
                    }
                    break;
                default:
                    _Logger.warning("event type no handle %s", event.getEventType());
                    break;
            }
            if (Objects.nonNull(commands) && commands.length > 0 && Objects.nonNull(session)) {
                event.produce(WRITE, new Pair<>(commands, session), WsZSort.CONSUMER.getTransfer());
            }
            else {
                event.ignore();
            }
        }, new EncryptHandler());
    }
}
