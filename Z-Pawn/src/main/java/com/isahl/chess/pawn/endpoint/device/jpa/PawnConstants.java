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

package com.isahl.chess.pawn.endpoint.device.jpa;

import static com.isahl.chess.rook.storage.jpa.model.AuditModel.AUDIT_MODEL_SERIAL;

public interface PawnConstants
{
    int DB_SERIAL_LOCAL_MSG_ENTITY     = AUDIT_MODEL_SERIAL + 1;
    int DB_SERIAL_LOCAL_SESSION_ENTITY = DB_SERIAL_LOCAL_MSG_ENTITY + 1;
    int DB_SERIAL_REMOTE_DEVICE_ENTITY = DB_SERIAL_LOCAL_SESSION_ENTITY + 1;
    int DB_SERIAL_REMOTE_MSG_ENTITY    = DB_SERIAL_REMOTE_DEVICE_ENTITY + 1;
    int DB_SERIAL_REMOTE_SHADOW_ENTITY = DB_SERIAL_REMOTE_MSG_ENTITY + 1;

    int PAWN_END_SERIAL = DB_SERIAL_LOCAL_MSG_ENTITY + 1024;
}
