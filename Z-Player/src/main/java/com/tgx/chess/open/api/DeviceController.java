/*
 * MIT License
 *
 * Copyright (c) 2016~2020. Z-Chess
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

package com.tgx.chess.open.api;

import static com.tgx.chess.king.base.util.IoUtil.isBlank;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.tgx.chess.king.base.inf.IPair;
import com.tgx.chess.king.base.log.Logger;
import com.tgx.chess.king.base.util.Pair;
import com.tgx.chess.king.base.util.Result;
import com.tgx.chess.king.base.util.ResultUtils;
import com.tgx.chess.open.api.model.DeviceDo;
import com.tgx.chess.open.api.service.DeviceOpenService;
import com.tgx.chess.open.api.utils.PageRequestUtil;
import com.tgx.chess.pawn.endpoint.spring.device.jpa.model.DeviceEntity;
import com.tgx.chess.pawn.endpoint.spring.device.model.DeviceStatus;

/**
 * @author william.d.zk
 */
@RestController
@RequestMapping("device")
public class DeviceController
{
    private final Logger            _Logger = Logger.getLogger(getClass().getSimpleName());
    private final DeviceOpenService _DeviceService;

    @Autowired
    public DeviceController(DeviceOpenService deviceService)
    {
        _DeviceService = deviceService;
    }

    @PostMapping("register")
    public Result<DeviceEntity> registerDevice(@RequestBody DeviceDo deviceDo)
    {
        DeviceEntity deviceEntity = new DeviceEntity();
        deviceEntity.setSn(deviceDo.getSn());
        deviceEntity.setUsername(deviceDo.getUsername());
        deviceEntity.setPassword(deviceDo.getPassword());
        return ResultUtils.success(_DeviceService.save(deviceEntity));
    }

    @GetMapping("query")
    public Result<IPair> queryDevice(@RequestParam(required = false) String token,
                                           @RequestParam(required = false) String sn,
                                           @RequestParam(required = false) Long id)
    {
        IPair result = null;
        DeviceEntity device = new DeviceEntity();
        device.setSn(sn);
        device.setToken(token);
        device.setId(id);
        if (!isBlank(token) || !isBlank(sn)) {
            DeviceEntity exist = _DeviceService.find(device);
            if (Objects.nonNull(exist)) {
                if (device.getInvalidAt()
                          .toInstant()
                          .isBefore(Instant.now()))
                {
                    result = new Pair<>(DeviceStatus.INVALID, device);
                }
                else {
                    result = new Pair<>(DeviceStatus.AVAILABLE, device);
                }
            }
        }
        else {
            DeviceEntity exist = _DeviceService.find(device);
            if (exist != null) {
                result = new Pair<>(DeviceStatus.AVAILABLE, exist);
        }
        }
        if (result == null) {
            result = new Pair<>(DeviceStatus.MISS, null);
        }
        return ResultUtils.success(result);
    }

    @PostMapping("findAllDevices")
    public Result<Page<DeviceEntity>> findAllDevices(@RequestParam("sn") String sn,
                                                     @RequestParam("page") Integer page,
                                                     @RequestParam("size") Integer size)
    {
        return ResultUtils.success(_DeviceService.findAll(sn, PageRequestUtil.getPageRequest(page, size)));
    }

    @GetMapping("online")
    public @ResponseBody Result<List<DeviceEntity>> filterOnlineWithUsername(@RequestParam(name = "username") String username)
    {
        if (isBlank(username)) return null;
        return ResultUtils.success(_DeviceService.filterOnlineDevices(username)
                                                 .collect(Collectors.toList()));
    }

}
