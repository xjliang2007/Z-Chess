/*
 * MIT License
 *
 * Copyright (c) 2016~2021. Z-Chess
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

package com.isahl.chess.pawn.endpoint.device.db.remote.postgres.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.isahl.chess.board.annotation.ISerialGenerator;
import com.isahl.chess.board.base.ISerial;
import com.isahl.chess.king.base.content.ByteBuf;
import com.isahl.chess.pawn.endpoint.device.api.model.DeviceProfile;
import com.isahl.chess.rook.storage.db.model.AuditModel;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.validator.constraints.Length;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.time.LocalDateTime;

/**
 * @author william.d.zk
 */
@Entity(name = "device")
@TypeDef(name = "jsonb",
         typeClass = JsonBinaryType.class)
@Table(schema = "z_chess_pawn",
       indexes = { @Index(name = "device_idx_token_pwd_id",
                          columnList = "token,password,password_id"),
                   @Index(name = "device_idx_token_pwd",
                          columnList = "token,password"),
                   @Index(name = "device_idx_sn",
                          columnList = "sn"),
                   @Index(name = "device_idx_token",
                          columnList = "token"),
                   @Index(name = "device_idx_username",
                          columnList = "username") })
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@ISerialGenerator(parent = ISerial.STORAGE_ROOK_DB_SERIAL)
public class DeviceEntity
        extends AuditModel
{
    @Serial
    private static final long serialVersionUID = -6645586986057373344L;

    @Transient
    private String        mSn;
    @Transient
    private String        password;
    @Transient
    private String        mUsername;
    @Transient
    private int           mPasswordId;
    @Transient
    private String        mToken;
    @Transient
    private LocalDateTime mInvalidAt;
    @Transient
    private DeviceProfile mProfile;

    @Id
    @JsonIgnore
    @GeneratedValue(generator = "ZDeviceGenerator")
    @GenericGenerator(name = "ZDeviceGenerator",
                      strategy = "com.isahl.chess.pawn.endpoint.device.db.generator.ZDeviceGenerator")
    public long getId()
    {
        return primaryKey();
    }

    public void setId(long id)
    {
        pKey = id;
    }

    @Column(length = 32,
            nullable = false)
    @Length(min = 17,
            max = 32,
            message = "*Your password must have at least 17 characters less than 33 characters")
    @NotBlank(message = "*Please provide your password")
    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public void increasePasswordId()
    {
        mPasswordId++;
    }

    @Column(length = 64,
            nullable = false,
            unique = true)
    public String getToken()
    {
        return mToken;
    }

    public void setToken(String token)
    {
        mToken = token;
    }

    @JsonIgnore
    @Column(name = "password_id")
    public int getPasswordId()
    {
        return mPasswordId;
    }

    public void setPasswordId(int passwordId)
    {
        mPasswordId = passwordId;
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @Column(name = "invalid_at",
            nullable = false)
    public LocalDateTime getInvalidAt()
    {
        return mInvalidAt;
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    public void setInvalidAt(LocalDateTime invalidAt)
    {
        mInvalidAt = invalidAt;
    }

    @Column(length = 32,
            nullable = false,
            updatable = false)
    public String getSn()
    {
        return mSn;
    }

    public void setSn(String sn)
    {
        mSn = sn;
    }

    @Column(length = 32,
            nullable = false)
    @Length(min = 8,
            max = 32,
            message = "* Your Username must have at least 8 characters less than 33 characters")
    @NotBlank(message = "*Please provide your username")
    public String getUsername()
    {
        return mUsername;
    }

    public void setUsername(String username)
    {
        mUsername = username;
    }

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    public DeviceProfile getProfile()
    {
        return mProfile;
    }

    public void setProfile(DeviceProfile profile)
    {
        mProfile = profile;
    }

    @Override
    public String toString()
    {
        return String.format(
                "device{id:%s,token:%s,user:%s,pwdId:%d,pwd:%s,sn:%s,profile:%s,create:%s,update:%s,invalid:%s}",
                getId(),
                getToken(),
                getUsername(),
                getPasswordId(),
                getPassword(),
                getSn(),
                getProfile(),
                getCreatedAt(),
                getUpdatedAt(),
                getInvalidAt());
    }

    public DeviceEntity()
    {
        super();
    }

    public DeviceEntity(ByteBuf input)
    {
        super(input);
    }
}
