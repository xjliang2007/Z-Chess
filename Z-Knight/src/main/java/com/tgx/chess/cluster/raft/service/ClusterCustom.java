/*
 * MIT License                                                                    
 *                                                                                
 * Copyright (c) 2016~2020 Z-Chess                                                
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

package com.tgx.chess.cluster.raft.service;

import org.springframework.stereotype.Component;

import com.tgx.chess.bishop.io.zfilter.ZContext;
import com.tgx.chess.king.base.exception.ZException;
import com.tgx.chess.queen.db.inf.IRepository;
import com.tgx.chess.queen.event.inf.ICustomLogic;
import com.tgx.chess.queen.io.core.inf.IControl;
import com.tgx.chess.queen.io.core.inf.ISession;
import com.tgx.chess.queen.io.core.manager.QueenManager;

@Component
public class ClusterCustom
        implements
        ICustomLogic<ZContext>
{
    private final IRepository<RaftNode> _ClusterRepository;

    public ClusterCustom(IRepository<RaftNode> clusterRepository)
    {
        _ClusterRepository = clusterRepository;
    }

    @Override
    public IControl<ZContext>[] handle(QueenManager<ZContext> manager,
                                       ISession<ZContext> session,
                                       IControl<ZContext> content) throws ZException
    {
        return null;
    }
}
