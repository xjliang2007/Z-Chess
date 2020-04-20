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

package com.tgx.chess.knight.endpoint.spring.device.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@PropertySource("classpath:device.properties")
@ConfigurationProperties(prefix = "z.chess.device")
@Configuration
public class DeviceConfig
{
    private Duration passwordInvalidDays;
    private String   passwordRandomSeed;
    private String   addressWs;
    private String   addressQtt;

    public Duration getPasswordInvalidDays()
    {
        return passwordInvalidDays;
    }

    public void setPasswordInvalidDays(Duration passwordInvalidDays)
    {
        this.passwordInvalidDays = passwordInvalidDays;
    }

    public String getPasswordRandomSeed()
    {
        return passwordRandomSeed;
    }

    public void setPasswordRandomSeed(String passwordRandomSeed)
    {
        this.passwordRandomSeed = passwordRandomSeed;
    }

    public String getAddressWs()
    {
        return addressWs;
    }

    public void setAddressWs(String addressWs)
    {
        this.addressWs = addressWs;
    }

    public String getAddressQtt()
    {
        return addressQtt;
    }

    public void setAddressQtt(String addressQtt)
    {
        this.addressQtt = addressQtt;
    }
}