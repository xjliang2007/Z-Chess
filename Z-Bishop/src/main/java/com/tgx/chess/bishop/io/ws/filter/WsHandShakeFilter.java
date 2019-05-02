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

package com.tgx.chess.bishop.io.ws.filter;

import static com.tgx.chess.queen.io.core.inf.IContext.DECODE_FRAME;
import static com.tgx.chess.queen.io.core.inf.IContext.DECODE_HANDSHAKE;
import static com.tgx.chess.queen.io.core.inf.IContext.ENCODE_FRAME;
import static com.tgx.chess.queen.io.core.inf.IContext.ENCODE_HANDSHAKE;

import java.nio.ByteBuffer;
import java.util.Objects;

import com.tgx.chess.bishop.io.ws.bean.WsContext;
import com.tgx.chess.bishop.io.ws.bean.WsHandshake;
import com.tgx.chess.bishop.io.ws.control.X101_HandShake;
import com.tgx.chess.bishop.io.ws.control.X102_SslHandShake;
import com.tgx.chess.king.base.log.Logger;
import com.tgx.chess.queen.event.inf.ISort;
import com.tgx.chess.queen.io.core.async.AioFilterChain;
import com.tgx.chess.queen.io.core.async.AioPacket;
import com.tgx.chess.queen.io.core.inf.IPacket;
import com.tgx.chess.queen.io.core.inf.IProtocol;

/**
 * @author William.d.zk
 */
public class WsHandShakeFilter<C extends WsContext>
        extends
        AioFilterChain<C>
{
    private final static String CRLF = "\r\n";

    private final ISort  _Sort;
    private final Logger _Log = Logger.getLogger(getClass().getName());

    public WsHandShakeFilter(ISort sort)
    {
        super("web-socket-header-zfilter-");
        _Sort = sort;
    }

    @Override
    public ResultType preEncode(C context, IProtocol output)
    {
        if (context == null || output == null) { return ResultType.ERROR; }
        if (context.needHandshake()
            && context.outState() == ENCODE_HANDSHAKE
            && output instanceof WsHandshake) { return ResultType.NEXT_STEP; }
        return ResultType.IGNORE;
    }

    @Override
    public ResultType preDecode(C context, IProtocol input)
    {
        if (context == null || !(input instanceof IPacket)) { return ResultType.ERROR; }
        if (context.needHandshake() && context.inState() == DECODE_HANDSHAKE) {
            WsHandshake handshake = context.getHandshake();
            ByteBuffer recvBuf = ((IPacket) input).getBuffer();
            ByteBuffer cRvBuf = context.getRvBuffer();
            byte c;
            while (recvBuf.hasRemaining()) {
                c = recvBuf.get();
                cRvBuf.put(c);
                if (c == '\n') {
                    cRvBuf.flip();
                    String x = new String(cRvBuf.array(), cRvBuf.position(), cRvBuf.limit());
                    _Log.info(x);
                    cRvBuf.clear();
                    switch (_Sort.getType())
                    {
                        case SERVER:
                            if (Objects.isNull(handshake)) {
                                handshake = _Sort.isSSL() ? new X102_SslHandShake<C>()
                                                          : new X101_HandShake<C>();
                            }
                            context.setHandshake(handshake);
                            String[] split = x.split(" ", 2);
                            String httpKey = split[0].toUpperCase();
                            switch (httpKey)
                            {
                                case "GET":
                                    split = x.split(" ");
                                    if (!split[2].equalsIgnoreCase("HTTP/1.1\r\n")) {
                                        _Log.warning("http protocol version is low than 1.1");
                                        return ResultType.ERROR;
                                    }
                                    context.updateHandshakeState(WsContext.HS_State_GET);
                                    break;
                                case "UPGRADE:":
                                    if (!split[1].equalsIgnoreCase("websocket\r\n")) {
                                        _Log.warning("upgrade no web-socket");
                                        return ResultType.ERROR;
                                    }
                                    context.updateHandshakeState(WsContext.HS_State_UPGRADE);
                                    handshake.append(x);
                                    break;
                                case "CONNECTION:":
                                    if (!split[1].equalsIgnoreCase("Upgrade\r\n")) {
                                        _Log.warning("connection no upgrade");
                                        return ResultType.ERROR;
                                    }
                                    context.updateHandshakeState(WsContext.HS_State_CONNECTION);
                                    handshake.append(x);
                                    break;
                                case "SEC-WEBSOCKET-PROTOCOL:":
                                    if (!split[1].contains("z-push") && !split[1].contains("z-chat")) {
                                        _Log.warning("sec-websokcet-protocol failed");
                                        return ResultType.ERROR;
                                    }
                                    context.updateHandshakeState(WsContext.HS_State_SEC_PROTOCOL);
                                    handshake.append(x);
                                    break;
                                case "SEC-WEBSOCKET-VERSION:":
                                    if (!split[1].contains("13")) {
                                        _Log.warning("sec-websokcet-version to low");
                                        return ResultType.ERROR;
                                    }
                                    else if (split[1].contains("7") || split[1].contains("8")) {
                                        break;
                                    }
                                    // TODO multi version code
                                    // Sec-WebSocket-Version: 13, 7, 8 ->
                                    // Sec-WebSocket-Version:13\r\nSec-WebSocket-Version: 7, 8\r\n
                                    context.updateHandshakeState(WsContext.HS_State_SEC_VERSION);
                                    break;
                                case "HOST:":
                                    context.updateHandshakeState(WsContext.HS_State_HOST);
                                    break;
                                case "ORIGIN:":
                                    context.updateHandshakeState(WsContext.HS_State_ORIGIN);
                                    break;
                                case "SEC-WEBSOCKET-KEY:":
                                    String sec_key = split[1].replace(CRLF, "");
                                    String sec_accept_expect = context.getSecAccept(sec_key);
                                    handshake.append(String.format("Sec-WebSocket-Accept: %s\r\n", sec_accept_expect));
                                    context.updateHandshakeState(WsContext.HS_State_SEC_KEY);
                                    break;
                                case CRLF:
                                    handshake.ahead(context.checkState(WsContext.HS_State_CLIENT_OK) ? "HTTP/1.1 101 Switching Protocols\r\n"
                                                                                                     : "HTTP/1.1 400 Bad Request\r\n")
                                             .append(CRLF);
                                    return ResultType.HANDLED;
                                default:
                                    break;
                            }
                            break;
                        case CONSUMER:
                            if (handshake == null) {
                                handshake = _Sort.isSSL() ? new X102_SslHandShake()
                                                          : new X101_HandShake();
                            }
                            context.setHandshake(handshake);
                            split = x.split(" ", 2);
                            httpKey = split[0].toUpperCase();
                            switch (httpKey)
                            {
                                case "HTTP/1.1":
                                    if (!split[1].contains("101 Switching Protocols\r\n")) {
                                        _Log.warning("handshake error !:");
                                        return ResultType.ERROR;
                                    }
                                    context.updateHandshakeState(WsContext.HS_State_HTTP_101);
                                    break;
                                case "UPGRADE:":
                                    if (!"websocket\r\n".equalsIgnoreCase(split[1])) {
                                        _Log.warning("upgrade no web-socket");
                                        return ResultType.ERROR;
                                    }
                                    context.updateHandshakeState(WsContext.HS_State_UPGRADE);
                                    break;
                                case "CONNECTION:":
                                    if (!"Upgrade\r\n".equalsIgnoreCase(split[1])) {
                                        _Log.warning("connection no upgrade");
                                        return ResultType.ERROR;
                                    }
                                    context.updateHandshakeState(WsContext.HS_State_CONNECTION);
                                    break;
                                case "SEC-WEBSOCKET-ACCEPT:":
                                    if (!split[1].startsWith(context.getSecAcceptExpect())) {
                                        _Log.warning("key error: expect-> "
                                                     + context.getSecAcceptExpect()
                                                     + " | result-> "
                                                     + split[1]);
                                        return ResultType.ERROR;
                                    }
                                    context.updateHandshakeState(WsContext.HS_State_SEC_ACCEPT);
                                    break;
                                case CRLF:
                                    if (context.checkState(WsContext.HS_State_ACCEPT_OK)) { return ResultType.HANDLED; }
                                    _Log.warning("client handshake error!");
                                    return ResultType.ERROR;
                                default:
                                    break;
                            }
                        default:
                            break;

                    }

                }
                if (!recvBuf.hasRemaining()) { return ResultType.NEED_DATA; }
            }
            return ResultType.NEED_DATA;
        }
        return ResultType.IGNORE;
    }

    @Override
    public IProtocol encode(C context, IProtocol output)
    {
        AioPacket encoded = new AioPacket(ByteBuffer.wrap(output.encode()));
        context.setOutState(ENCODE_FRAME);
        return encoded;
    }

    @Override
    public IProtocol decode(C context, IProtocol input)
    {
        WsHandshake handshake = context.getHandshake();
        context.setInState(DECODE_FRAME);
        context.finish();
        return handshake;
    }

}
