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

package com.isahl.chess.bishop.io;

import com.isahl.chess.bishop.io.mqtt.control.X111_QttConnect;
import com.isahl.chess.bishop.io.mqtt.control.X11E_QttDisconnect;
import com.isahl.chess.bishop.io.mqtt.filter.QttCommandFactory;
import com.isahl.chess.bishop.io.sort.mqtt.MqttZSort;
import com.isahl.chess.bishop.io.sort.mqtt.ssl.MqttSslZSort;
import com.isahl.chess.bishop.io.sort.mqtt.ssl.MqttZlsZSort;
import com.isahl.chess.bishop.io.sort.websocket.WsZSort;
import com.isahl.chess.bishop.io.sort.websocket.proxy.WsProxyZSort;
import com.isahl.chess.bishop.io.sort.websocket.ssl.WsSslZSort;
import com.isahl.chess.bishop.io.sort.websocket.ssl.WsZlsZSort;
import com.isahl.chess.bishop.io.sort.websocket.ssl.proxy.WsSslProxyZSort;
import com.isahl.chess.bishop.io.zprotocol.ZClusterFactory;
import com.isahl.chess.bishop.io.zprotocol.ZServerFactory;
import com.isahl.chess.bishop.io.zprotocol.raft.X70_RaftVote;
import com.isahl.chess.bishop.io.zprotocol.raft.X76_RaftNotify;
import com.isahl.chess.king.topology.ZUID;
import com.isahl.chess.queen.event.inf.ISort;
import com.isahl.chess.queen.io.core.inf.IControl;
import com.isahl.chess.queen.io.core.inf.IPContext;

/**
 * @author william.d.zk
 * @date 2019-06-30
 */
public enum ZSortHolder
{
    WS_CONSUMER(new WsZSort(ISort.Mode.LINK, ISort.Type.CONSUMER)),
    WS_SERVER(new WsZSort(ISort.Mode.LINK, ISort.Type.SERVER)),
    WS_CONSUMER_SSL(new WsSslZSort(ISort.Mode.LINK,
                                   ISort.Type.CONSUMER,
                                   new WsZSort(ISort.Mode.LINK, ISort.Type.CONSUMER))),
    WS_SERVER_SSL(new WsSslZSort(ISort.Mode.LINK, ISort.Type.SERVER, new WsZSort(ISort.Mode.LINK, ISort.Type.SERVER))),
    WS_CLUSTER_SYMMETRY(new WsZSort(ISort.Mode.CLUSTER, ISort.Type.SYMMETRY)),
    WS_CLUSTER_CONSUMER(new WsZSort(ISort.Mode.CLUSTER, ISort.Type.CONSUMER)),
    WS_CLUSTER_SERVER(new WsZSort(ISort.Mode.CLUSTER, ISort.Type.SERVER)),
    WS_CLUSTER_SYMMETRY_ZLS(new WsZlsZSort(ISort.Mode.CLUSTER,
                                           ISort.Type.SYMMETRY,
                                           new WsZSort(ISort.Mode.CLUSTER, ISort.Type.SYMMETRY))),
    QTT_SERVER(new MqttZSort(ISort.Mode.LINK, ISort.Type.SERVER)),
    QTT_CONSUMER(new MqttZSort(ISort.Mode.LINK, ISort.Type.CONSUMER)),
    QTT_SYMMETRY(new MqttZSort(ISort.Mode.LINK, ISort.Type.SYMMETRY)),
    QTT_SERVER_SSL(new MqttSslZSort(ISort.Mode.LINK,
                                    ISort.Type.SERVER,
                                    new MqttZSort(ISort.Mode.LINK, ISort.Type.SERVER))),
    QTT_CONSUMER_SSL(new MqttSslZSort(ISort.Mode.LINK,
                                      ISort.Type.CONSUMER,
                                      new MqttZSort(ISort.Mode.LINK, ISort.Type.CONSUMER))),
    QTT_SYMMETRY_SSL(new MqttSslZSort(ISort.Mode.LINK,
                                      ISort.Type.SYMMETRY,
                                      new MqttZSort(ISort.Mode.LINK, ISort.Type.SYMMETRY))),
    QTT_SERVER_ZLS(new MqttZlsZSort(ISort.Mode.LINK,
                                    ISort.Type.SERVER,
                                    new MqttZSort(ISort.Mode.LINK, ISort.Type.SERVER))),
    WS_QTT_SERVER(new WsProxyZSort<>(ISort.Mode.LINK,
                                     ISort.Type.SERVER,
                                     new MqttZSort(ISort.Mode.LINK, ISort.Type.SERVER))),
    WS_QTT_SERVER_SSL(new WsSslProxyZSort<>(ISort.Mode.LINK,
                                            ISort.Type.SERVER,
                                            new WsProxyZSort<>(ISort.Mode.LINK,
                                                               ISort.Type.SERVER,
                                                               new MqttZSort(ISort.Mode.LINK, ISort.Type.SERVER)))),
    WS_QTT_CONSUMER(new WsProxyZSort<>(ISort.Mode.LINK,
                                       ISort.Type.CONSUMER,
                                       new MqttZSort(ISort.Mode.LINK, ISort.Type.CONSUMER))),
    WS_QTT_CONSUMER_SSL(new WsSslProxyZSort<>(ISort.Mode.LINK,
                                              ISort.Type.CONSUMER,
                                              new WsProxyZSort<>(ISort.Mode.LINK,
                                                                 ISort.Type.CONSUMER,
                                                                 new MqttZSort(ISort.Mode.LINK, ISort.Type.CONSUMER))));

    private final ISort<?> _Sort;

    <C extends IPContext> ZSortHolder(ISort<C> sort)
    {
        _Sort = sort;
    }

    public static IControl create(int serial)
    {
        if (serial >= X111_QttConnect.COMMAND && serial <= X11E_QttDisconnect.COMMAND) {
            return _QttCommandFactory.create(serial);
        }
        if (serial >= 0x20 && serial <= 0x6F) { return _ServerFactory.create(serial); }
        if (serial >= X70_RaftVote.COMMAND && serial <= X76_RaftNotify.COMMAND) {
            return _ClusterFactory.create(serial);
        }
        throw new IllegalArgumentException();
    }

    final static ZServerFactory    _ServerFactory     = new ZServerFactory();
    final static ZClusterFactory   _ClusterFactory    = new ZClusterFactory();
    final static QttCommandFactory _QttCommandFactory = new QttCommandFactory();

    @SuppressWarnings("unchecked")
    public <C extends IPContext> ISort<C> getSort()
    {
        return (ISort<C>) _Sort;
    }

    public int getSlot()
    {
        ISort.Mode mode = _Sort.getMode();
        ISort.Type type = _Sort.getType();
        return mode == ISort.Mode.LINK ? ZUID.TYPE_CONSUMER_SLOT
                                       : type == ISort.Type.SYMMETRY ? ZUID.TYPE_CLUSTER_SLOT
                                                                     : type == ISort.Type.INNER ? ZUID.TYPE_INTERNAL_SLOT
                                                                                                : ZUID.TYPE_PROVIDER_SLOT;
    }
}
