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

package com.isahl.chess.knight.raft.model;

/**
 * @author william.d.zk
 * @date 2019/12/9
 */
public enum RaftState
{
    CLIENT(0),
    FOLLOWER(1),
    ELECTOR(1 << 1),
    CANDIDATE(1 << 2),
    LEADER(1 << 3),

    JOINT(1 << 4),
    MASK(JOINT.getCode() - 1),

    GATE(1 << 5),
    BOUNDARY(1 << 6),
    OUTSIDE(1 << 7);

    private final int _Code;

    RaftState(int code)
    {
        _Code = code;
    }

    public int getCode()
    {
        return _Code;
    }

    public static RaftState valueOf(int code)
    {
        return switch(code & MASK.getCode()) {
            case 0 -> CLIENT;
            case 1 -> FOLLOWER;
            case 2 -> ELECTOR;
            case 3 -> CANDIDATE;
            case 4 -> LEADER;
            default -> switch(code & ~MASK.getCode()) {
                case 8 -> JOINT;
                case 16 -> GATE;
                case 128 -> OUTSIDE;
                default -> throw new IllegalArgumentException();
            };
        };
    }
}
