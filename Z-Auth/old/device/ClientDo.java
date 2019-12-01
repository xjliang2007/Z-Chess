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

package device;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @author william.d.zk
 * @date 2019-07-19
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ClientDo
{
    private String       auth;
    private String       cipher;
    private String       userName;
    private Boolean      exist;
    private List<String> devices;
    private List<String> bindDevices;

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public List<String> getDevices()
    {
        return devices;
    }

    public void setDevices(List<String> devices)
    {
        this.devices = devices;
    }

    public String getAuth()
    {
        return auth;
    }

    public void setAuth(String auth)
    {
        this.auth = auth;
    }

    public String getCipher()
    {
        return cipher;
    }

    public void setCipher(String cipher)
    {
        this.cipher = cipher;
    }

    public void setExist(boolean exist)
    {
        this.exist = exist;
    }

    public Boolean getExist()
    {
        return exist;
    }

    public List<String> getBindDevices()
    {
        return bindDevices;
    }

    public void setBindDevices(List<String> bindDevices)
    {
        this.bindDevices = bindDevices;
    }
}