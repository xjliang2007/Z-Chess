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

package com.tgx.chess.bishop.biz.db.dao;

import com.tgx.chess.queen.db.inf.IStorage;

public class DeviceEntry
        implements
        IStorage
{
    private final static int DEVICE_ENTRY_SERIAL = DB_SERIAL + 1;

    @Override
    public int dataLength()
    {
        return 0;
    }

    @Override
    public int getSerial()
    {
        return DEVICE_ENTRY_SERIAL;
    }

    private long    deviceUID;
    private long    passwordId;
    private String  token;
    private String  mac;
    private long    invalidTime;
    private boolean isOnline;

    @Override
    public long getPrimaryKey()
    {
        return deviceUID;
    }

    @Override
    public long getSecondaryLongKey()
    {
        return passwordId;
    }

    public void setDeviceUID(long deviceUID)
    {
        this.deviceUID = deviceUID;
    }

    public long getDeviceUID()
    {
        return deviceUID;
    }

    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }

    public String getMac()
    {
        return mac;
    }

    public void setMac(String mac)
    {
        this.mac = mac;
    }

    public long getPasswordId()
    {
        return passwordId;
    }

    public void setPasswordId(long passwordId)
    {
        this.passwordId = passwordId;
    }

    public long getInvalidTime()
    {
        return invalidTime;
    }

    public void setInvalidTime(long invalidTime)
    {
        this.invalidTime = invalidTime;
    }

    public boolean isOnline()
    {
        return isOnline;
    }

    public void setOnline(boolean online)
    {
        isOnline = online;
    }
}
