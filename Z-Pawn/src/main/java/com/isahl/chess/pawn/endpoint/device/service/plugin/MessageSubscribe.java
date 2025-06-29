/*
 *  MIT License
 *
 * Copyright (c) 2024.  Z-Chess
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package com.isahl.chess.pawn.endpoint.device.service.plugin;

import com.isahl.chess.king.base.features.model.IoSerial;
import com.isahl.chess.king.base.log.Logger;
import com.isahl.chess.king.env.ZUID;
import com.isahl.chess.pawn.endpoint.device.db.central.model.DeviceEntity;
import com.isahl.chess.pawn.endpoint.device.db.central.model.MessageEntity;
import com.isahl.chess.pawn.endpoint.device.resource.features.IDeviceService;
import com.isahl.chess.pawn.endpoint.device.resource.features.IMessageService;
import com.isahl.chess.pawn.endpoint.device.spi.plugin.PersistentHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessageSubscribe
        implements PersistentHook.ISubscribe
{
    private final Logger          _Logger = Logger.getLogger("endpoint.pawn." + getClass().getSimpleName());
    private final IMessageService _MessageService;
    private final IDeviceService  _DeviceService;

    @Autowired
    public MessageSubscribe(IMessageService messageService, IDeviceService deviceService)
    {
        _MessageService = messageService;
        _DeviceService = deviceService;
    }

    @Override
    public void onBatch(List<IoSerial> contents)
    {
        _MessageService.submitAll(contents.stream()
                                          .filter(c->c instanceof MessageEntity)
                                          .map(c->(MessageEntity) c)
                                          .peek(c->{
                                              c.setMessageId(_MessageService.generateId());
                                              DeviceEntity device = _DeviceService.getOneDevice(c.origin() & ZUID.DEVICE_MASK);
                                              if(device != null) c.setOrigin(device.getId());
                                          })
                                          .toList());
    }

    @Override
    public void onMessage(IoSerial content)
    {
        if(content instanceof MessageEntity msg) {
            DeviceEntity device = _DeviceService.getOneDevice(msg.origin() & ZUID.DEVICE_MASK);
            if(device != null) msg.setOrigin(device.getId());
            _MessageService.submit(msg);
        }
    }
}
