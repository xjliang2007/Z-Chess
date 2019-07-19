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

package com.tgx.chess.device.api;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.tgx.chess.king.base.log.Logger;
import com.tgx.chess.spring.device.model.ClientEntity;
import com.tgx.chess.spring.device.model.DeviceEntity;
import com.tgx.chess.spring.device.repository.ClientRepository;
import com.tgx.chess.spring.device.repository.DeviceRepository;
import com.tgx.chess.spring.device.service.ClientDo;
import com.tgx.chess.spring.jpa.generator.ZGenerator;

/**
 * @author william.d.zk
 * @date 2019-07-19
 */
@RestController
public class ClientController
{

    private final Logger           _Logger     = Logger.getLogger(getClass().getName());
    private final ZGenerator       _ZGenerator = new ZGenerator();
    private final ClientRepository _ClientRepository;
    private final DeviceRepository _DeviceRepository;

    @Autowired
    public ClientController(ClientRepository clientRepository,
                            DeviceRepository deviceRepository)
    {
        _ClientRepository = clientRepository;
        _DeviceRepository = deviceRepository;
    }

    @PostMapping("/client/register")
    public @ResponseBody ClientDo register(@RequestBody ClientDo client)
    {
        ClientEntity entity = new ClientEntity();
        entity.setUserName(client.getUserName());
        entity.setDevices(client.getDevices()
                                .stream()
                                .map(_DeviceRepository::findBySn)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet()));
        entity = _ClientRepository.save(entity);
        Set<String> snSet = entity.getDevices()
                                  .stream()
                                  .map(DeviceEntity::getSn)
                                  .collect(Collectors.toSet());
        client.setSuccessDevices(client.getDevices()
                                       .stream()
                                       .filter(snSet::contains)
                                       .collect(Collectors.toList()));
        client.getDevices()
              .removeAll(snSet);
        return client;
    }
}
