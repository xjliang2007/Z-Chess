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

package com.isahl.chess.pawn.endpoint.device.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.isahl.chess.bishop.io.mqtt.command.*;
import com.isahl.chess.bishop.io.mqtt.control.X11C_QttPingreq;
import com.isahl.chess.bishop.io.mqtt.control.X11D_QttPingresp;
import com.isahl.chess.bishop.io.mqtt.handler.IQttRouter;
import com.isahl.chess.bishop.io.ws.control.X101_HandShake;
import com.isahl.chess.bishop.io.ws.control.X103_Ping;
import com.isahl.chess.bishop.io.ws.control.X104_Pong;
import com.isahl.chess.bishop.io.ws.zchat.zprotocol.control.X105_SslHandShake;
import com.isahl.chess.bishop.io.ws.zchat.zprotocol.control.X10A_PlainText;
import com.isahl.chess.king.base.disruptor.event.inf.IHealth;
import com.isahl.chess.king.base.disruptor.processor.Health;
import com.isahl.chess.king.base.exception.ZException;
import com.isahl.chess.king.base.log.Logger;
import com.isahl.chess.king.base.util.JsonUtil;
import com.isahl.chess.knight.raft.RaftPeer;
import com.isahl.chess.pawn.endpoint.device.jpa.model.MessageBody;
import com.isahl.chess.pawn.endpoint.device.jpa.model.MessageEntity;
import com.isahl.chess.pawn.endpoint.device.spi.IMessageService;
import com.isahl.chess.queen.event.handler.mix.ILogicHandler;
import com.isahl.chess.queen.io.core.inf.*;
import org.springframework.data.jpa.domain.Specification;

import javax.net.ssl.SSLEngineResult;
import javax.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static com.isahl.chess.queen.db.inf.IStorage.Operation.OP_INSERT;
import static com.isahl.chess.queen.db.inf.IStorage.Operation.OP_MODIFY;
import static com.isahl.chess.queen.io.core.inf.IQoS.Level.ALMOST_ONCE;

/**
 * @author william.d.zk
 */
public class LogicHandler<T extends IActivity & IClusterPeer & IClusterTimer & INode & ISessionManager>
        implements ILogicHandler
{
    private final Logger _Logger = Logger.getLogger("endpoint.pawn." + getClass().getSimpleName());

    private final T               _Manager;
    private final IQttRouter      _QttRouter;
    private final RaftPeer<T>     _RaftPeer;
    private final IMessageService _MessageService;
    private final IHealth         _Health;

    public LogicHandler(T manager, IQttRouter qttRouter, RaftPeer<T> raftPeer, IMessageService messageService, int slot)
    {
        _Manager = manager;
        _QttRouter = qttRouter;
        _RaftPeer = raftPeer;
        _MessageService = messageService;
        _Health = new Health(slot);
    }

    @Override
    public IHealth getHealth()
    {
        return _Health;
    }

    @Override
    public ISessionManager getISessionManager()
    {
        return _Manager;
    }

    @Override
    public IControl[] handle(ISessionManager manager, ISession session, IControl content) throws ZException
    {
        IControl[] result = null;
        switch(content.serial()) {
            case X101_HandShake.COMMAND:
                result = new IControl[]{ content };
                break;
            case X103_Ping.COMMAND:
                X103_Ping x103 = (X103_Ping) content;
                result = new IControl[]{ new X104_Pong(x103.getPayload()) };
                break;
            case X105_SslHandShake.COMMAND:
                X105_SslHandShake x105 = (X105_SslHandShake) content;
                if(x105.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                    x105.setPayload("hello".getBytes(StandardCharsets.UTF_8));
                }
                else {
                    break;
                }
                result = new IControl[]{ content };
                break;
            case X10A_PlainText.COMMAND:
                /*
                X10A 纯作为文本消息进行回应，可以做出简单的ECHO功能
                 */
                X10A_PlainText x10A = (X10A_PlainText) content;
                String jsonStr = new String(x10A.getPayload(), StandardCharsets.UTF_8);
                _Logger.info("[0x10A]:%s", jsonStr);
                JsonNode json = JsonUtil.readTree(jsonStr);
                ((ObjectNode) json).put("response", "ok");
                x10A.setPayload(JsonUtil.writeNodeAsBytes(json));
                result = new IControl[]{ content };
                break;
            case X113_QttPublish.COMMAND:
                X113_QttPublish x113 = (X113_QttPublish) content;
                if(x113.isRetain()) {
                    _QttRouter.retain(x113.getTopic(), x113);
                }
                MessageEntity msgEntity = new MessageEntity();
                msgEntity.setOrigin(session.getIndex());
                msgEntity.setDestination(_RaftPeer.getPeerId());
                msgEntity.setTopic(x113.getTopic());
                msgEntity.setBody(new MessageBody((int) x113.getMsgId(),
                                                  x113.getTopic(),
                                                  JsonUtil.readTree(x113.getPayload())));
                msgEntity.setOperation(OP_INSERT);
                List<IControl> pushList = new LinkedList<>();
                switch(x113.getLevel()) {
                    case ALMOST_ONCE:
                        brokerTopic(manager, msgEntity, pushList);
                        break;
                    case AT_LEAST_ONCE:
                        X114_QttPuback x114 = new X114_QttPuback();
                        x114.setMsgId(x113.getMsgId());
                        pushList.add(x114);
                        brokerTopic(manager, msgEntity, pushList);
                        break;
                    case EXACTLY_ONCE:
                        X115_QttPubrec x115 = new X115_QttPubrec();
                        x115.setMsgId(x113.getMsgId());
                        _QttRouter.register(x115, session.getIndex());
                        pushList.add(x115);
                        break;
                    default:
                        break;
                }
                result = pushList.isEmpty() ? null : pushList.toArray(new IControl[0]);
                break;
            case X114_QttPuback.COMMAND:
                //x113.QoS1 → client → x114, 服务端不存储需要client持有的消息
                X114_QttPuback x114 = (X114_QttPuback) content;
                _QttRouter.ack(x114, session.getIndex());
                break;
            case X115_QttPubrec.COMMAND:
                //x113.QoS2 → client → x115, 服务端恒定返回x116,Router无需操作。
                X115_QttPubrec x115 = (X115_QttPubrec) content;
                X116_QttPubrel x116 = new X116_QttPubrel();
                x116.setMsgId(x115.getMsgId());
                result = new IControl[]{ x116 };
                break;
            case X116_QttPubrel.COMMAND:
                //x113 → server → x115 → client → x116 , 服务段收到 x116,需要注意
                x116 = (X116_QttPubrel) content;
                X117_QttPubcomp x117 = new X117_QttPubcomp();
                x117.setMsgId(x116.getMsgId());
                pushList = new LinkedList<>();
                pushList.add(x117);
                if(_QttRouter.ack(x116, session.getIndex()) && !x116.isDuplicate()) {
                    //仅在首次处理中进行数据库访问
                    Optional<MessageEntity> update = _MessageService.find1Msg((Specification<MessageEntity>) (root, criteriaQuery, criteriaBuilder)->{
                        Predicate predicate = criteriaBuilder.and(criteriaBuilder.greaterThan(root.get("createdAt"),
                                                                                              LocalDateTime.now()
                                                                                                           .minusSeconds(
                                                                                                                   3)),
                                                                  criteriaBuilder.equal(root.get("origin"),
                                                                                        session.getIndex()),
                                                                  criteriaBuilder.equal(root.get("destination"),
                                                                                        _RaftPeer.getPeerId()),
                                                                  criteriaBuilder.equal(root.get("msgId"),
                                                                                        x116.getMsgId()));
                        return criteriaQuery.where(predicate)
                                            .getRestriction();
                    });
                    update.ifPresent(msg->{
                        msg.setOperation(OP_MODIFY);
                        brokerTopic(manager, msg, pushList);
                    });
                }
                result = pushList.toArray(new IControl[0]);
                break;
            case X117_QttPubcomp.COMMAND:
                x117 = (X117_QttPubcomp) content;
                _QttRouter.ack(x117, session.getIndex());
                break;
            case X11C_QttPingreq.COMMAND:
                result = new IControl[]{ new X11D_QttPingresp() };
                break;
        }
        return result;
    }

    private void brokerTopic(ISessionManager manager, MessageEntity message, List<IControl> pushList)
    {
        Map<Long, IQoS.Level> route = _QttRouter.broker(message.getBody()
                                                               .getTopic());
        _Logger.debug("route %s", route);
        route.entrySet()
             .stream()
             .map(entry->{
                 long sessionIndex = entry.getKey();
                 ISession targetSession = manager.findSessionByIndex(sessionIndex);
                 if(targetSession != null) {
                     IQoS.Level subscribeLevel = entry.getValue();
                     X113_QttPublish x113 = new X113_QttPublish();
                     x113.setLevel(subscribeLevel);
                     x113.setTopic(message.getBody()
                                          .getTopic());
                     byte[] payload = message.getBody()
                                             .contentBinary();
                     x113.setPayload(payload);
                     x113.setSession(targetSession);
                     if(x113.getLevel() == ALMOST_ONCE) {return x113;}
                     x113.setMsgId(_MessageService.generateMsgId(sessionIndex, targetSession.getIndex()));
                     _QttRouter.register(x113, sessionIndex);
                     return x113;
                 }
                 return null;
             })
             .filter(Objects::nonNull)
             .forEach(pushList::add);
        if(_RaftPeer.isInCongress()) {
            //集群模式需要将消息进行广播，集群结构中，每个数据存储单元都设计为独立的。
            //TODO
        }
        _MessageService.handleMessage(message);
    }

    @Override
    public Logger getLogger()
    {
        return _Logger;
    }
}
