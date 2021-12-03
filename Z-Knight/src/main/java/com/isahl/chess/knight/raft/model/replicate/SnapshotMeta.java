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

package com.isahl.chess.knight.raft.model.replicate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.isahl.chess.board.annotation.ISerialGenerator;
import com.isahl.chess.queen.io.core.features.model.content.IProtocol;

import java.nio.ByteBuffer;

@ISerialGenerator(parent = IProtocol.CLUSTER_KNIGHT_RAFT_SERIAL,
                  serial = IProtocol.CLUSTER_KNIGHT_RAFT_SERIAL + 2)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SnapshotMeta
        extends BaseMeta
{
    private long mCommit;
    private long mTerm;

    @JsonCreator
    public SnapshotMeta(
            @JsonProperty("term")
                    long term,
            @JsonProperty("commit")
                    long commit)
    {
        super(Operation.OP_INSERT, Strategy.RETAIN);
        mTerm = term;
        mCommit = commit;
    }

    public SnapshotMeta()
    {
        super();
    }

    @Override
    public int length()
    {
        return super.length() + 8 + // term
               8;  // commit
    }

    @Override
    public ByteBuffer encode()
    {
        ByteBuffer output = super.encode()
                                 .putLong(getTerm())
                                 .putLong(getCommit());
        if(payload() != null) {
            output.put(payload());
        }
        return output;
    }

    @Override
    public void decode(ByteBuffer input)
    {
        super.decode(input);
        setTerm(input.getLong());
        setCommit(input.getLong());
        if(input.hasRemaining()) {
            mPayload = new byte[input.remaining()];
            input.get(mPayload);
        }
    }

    @Override
    public void reset()
    {
        mCommit = 0;
        mTerm = 0;
        flush();
    }

    public void setCommit(long commit)
    {
        this.mCommit = commit;
    }

    public void setTerm(long term)
    {
        this.mTerm = term;
    }

    public long getCommit()
    {
        return mCommit;
    }

    public long getTerm()
    {
        return mTerm;
    }

}
