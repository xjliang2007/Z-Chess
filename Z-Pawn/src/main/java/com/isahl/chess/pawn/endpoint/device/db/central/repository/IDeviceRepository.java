/*
 * MIT License
 *
 * Copyright (c) 2016~2022. Z-Chess
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

package com.isahl.chess.pawn.endpoint.device.db.central.repository;

import com.isahl.chess.pawn.endpoint.device.db.central.model.DeviceEntity;
import com.isahl.chess.rook.storage.db.repository.BaseLongRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * xxxRepository 会被jpa重载，findByXXX 函数会被处理成根据 xxx 字段去数据库查询
 *
 * @author william.d.zk
 * @date 2020-1-20
 */
@Repository
public interface IDeviceRepository
        extends BaseLongRepository<DeviceEntity>
{
    DeviceEntity findByToken(String token);

    DeviceEntity findByNotice(String number);


    Optional<DeviceEntity> findByDeviceId(long deviceId);

    List<DeviceEntity> findAllByDeviceIdIn(List<Long> deviceIds);
}