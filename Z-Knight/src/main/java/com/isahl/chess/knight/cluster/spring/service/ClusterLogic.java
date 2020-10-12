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

package com.isahl.chess.knight.cluster.spring.service;

import java.nio.charset.StandardCharsets;

import com.isahl.chess.bishop.io.mqtt.control.X11C_QttPingreq;
import com.isahl.chess.bishop.io.mqtt.control.X11D_QttPingresp;
import com.isahl.chess.bishop.io.ws.control.X104_Ping;
import com.isahl.chess.bishop.io.ws.control.X105_Pong;
import com.isahl.chess.bishop.io.zfilter.ZContext;
import com.isahl.chess.king.base.log.Logger;
import com.isahl.chess.queen.event.handler.mix.ILogicHandler;
import com.isahl.chess.queen.io.core.inf.IControl;
import com.isahl.chess.queen.io.core.inf.ISession;
import com.isahl.chess.queen.io.core.inf.ISessionManager;

/**
 * @author william.d.zk
 * 
 * @date 2020/5/2
 */
public class ClusterLogic
        implements
        ILogicHandler<ZContext>
{

    private final Logger                    _Logger = Logger.getLogger("cluster.knight." + getClass().getSimpleName());

    private final ISessionManager<ZContext> _Manager;

    public ClusterLogic(ISessionManager<ZContext> manager)
    {
        _Manager = manager;
    }

    @Override
    public ISessionManager<ZContext> getISessionManager()
    {
        return _Manager;
    }

    @Override
    public IControl<ZContext>[] handle(ISessionManager<ZContext> manager,
                                       ISession<ZContext> session,
                                       IControl<ZContext> content) throws Exception
    {
        _Logger.debug("cluster handle heartbeat");
        switch (content.serial())
        {
            case X104_Ping.COMMAND:
                return new X105_Pong[] { new X105_Pong(String.format("pong:%#x %s",
                                                                     session.getIndex(),
                                                                     session.getLocalAddress())
                                                             .getBytes(StandardCharsets.UTF_8))
                };
            case X11C_QttPingreq.COMMAND:
                return new X11D_QttPingresp[] { new X11D_QttPingresp()
                };
        }
        return null;
    }

    @Override
    public Logger getLogger()
    {
        return _Logger;
    }
}