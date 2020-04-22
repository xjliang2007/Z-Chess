package com.tgx.chess.queen.event.handler.mix;

import java.util.List;

import com.tgx.chess.king.base.inf.IPair;
import com.tgx.chess.king.base.inf.ITriple;
import com.tgx.chess.queen.io.core.inf.IContext;
import com.tgx.chess.queen.io.core.inf.IControl;
import com.tgx.chess.queen.io.core.inf.ISession;
import com.tgx.chess.queen.io.core.manager.MixManager;

/**
 * @author william.d.zk
 * @date 2020/4/20
 */
public interface ILinkCustom<C extends IContext<C>>
{

    /**
     *
     * @param manager
     * @param session
     * @param content
     * @return pair
     *         first: to_send_array 1:N
     *         second: to_transfer
     *         Link->Cluster, consensus
     * @throws Exception
     *             handle error
     */
    IPair handle(MixManager<C> manager, ISession<C> session, IControl<C> content) throws Exception;

    /**
     * Cluster->Link.notify(Cluster.consensus_result)
     *
     * @param manager
     * @param request
     *            link发来 强一致的请求
     * @param session
     *            session == request.session
     * @return response
     */
    List<ITriple> notify(MixManager<C> manager, IControl<C> request, ISession<C> session);

}
