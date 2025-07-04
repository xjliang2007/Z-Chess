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

package com.isahl.chess.pawn.endpoint.device.resource.features;

import com.isahl.chess.king.base.exception.ZException;
import com.isahl.chess.pawn.endpoint.device.db.central.model.DeviceEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface IDeviceService
{

    /**
     * 新增设备信息
     *
     * @param device
     * @return
     * @throws ZException
     */
    DeviceEntity upsertDevice(DeviceEntity device) throws ZException;

    /**
     * 更新设备信息
     *
     * @param device
     * @return
     * @throws ZException
     */
    DeviceEntity updateDevice(DeviceEntity device) throws ZException;

    /**
     * 根据设备令牌查找
     *
     * @param token
     * @return
     * @throws ZException
     */
    DeviceEntity findByToken(String token) throws ZException;

    /**
     * 根据设备编号查找
     *
     * @param number
     * @return
     * @throws ZException
     */
    DeviceEntity findByNotice(String number) throws ZException;

    List<DeviceEntity> findDevices(Specification<DeviceEntity> condition, Pageable pageable) throws ZException;

    List<DeviceEntity> findDevicesIn(List<Long> deviceIdList);

    DeviceEntity getOneDevice(long deviceId);

    /**
     * 根据设备ID删除设备
     * @param id
     */
    void deleteDevice(long id);

}