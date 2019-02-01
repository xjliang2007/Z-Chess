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

package com.tgx.chess.spring.biz.bill.pay.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tgx.chess.spring.biz.bill.pay.model.ItemEntity;
import com.tgx.chess.spring.biz.bill.pay.repository.ItemsRepository;

@Service
public class ItemsService
{
    private final ItemsRepository _ItemsRepository;

    @Autowired
    public ItemsService(ItemsRepository itemsRepository)
    {
        _ItemsRepository = itemsRepository;
    }

    public ItemEntity addItem(ItemEntity itemEntity)
    {
        return _ItemsRepository.save(itemEntity);
    }

    public List<ItemEntity> listItems()
    {
        return _ItemsRepository.findAll();
    }

    public Optional<ItemEntity> findById(long id)
    {
        return _ItemsRepository.findById(id);
    }
}