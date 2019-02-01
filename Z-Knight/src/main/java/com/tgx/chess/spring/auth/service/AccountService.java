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

package com.tgx.chess.spring.auth.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tgx.chess.spring.auth.model.AccountEntity;
import com.tgx.chess.spring.auth.model.ProfileEntity;
import com.tgx.chess.spring.auth.model.RoleEntity;
import com.tgx.chess.spring.auth.repository.AccountRepository;
import com.tgx.chess.spring.auth.repository.ProfileRepository;
import com.tgx.chess.spring.auth.repository.RoleRepository;

@Service
public class AccountService
{

    private final AccountRepository _AccountRepository;
    private final RoleRepository    _RoleRepository;
    private final ProfileRepository _ProfileRepository;

    @Autowired
    public AccountService(AccountRepository accountRepository,
                          RoleRepository roleRepository,
                          ProfileRepository profileRepository)
    {
        _AccountRepository = accountRepository;
        _RoleRepository = roleRepository;
        _ProfileRepository = profileRepository;
    }

    public void initializeCheck()
    {
        RoleEntity admin = _RoleRepository.findByRole("ADMIN");
        RoleEntity user = _RoleRepository.findByRole("USER");
        if (Objects.isNull(admin)) {
            admin = new RoleEntity();
            admin.setRole("ADMIN");
            _RoleRepository.save(admin);
        }
        if (Objects.isNull(user)) {
            user = new RoleEntity();
            user.setRole("USER");
            _RoleRepository.save(user);
        }
        AccountEntity test = _AccountRepository.findByName("root");
        if (Objects.isNull(test)) {
            ProfileEntity profile = new ProfileEntity();
            AccountEntity root = new AccountEntity();
            root.setActive(1);
            root.setName("root");
            root.setPassword("root12345");
            root.setRoles(new HashSet<>(Collections.singletonList(admin)));
            root.setEmail("z-chess@tgxstudio.com");
            root.setProfile(profile);
            profile.setAccount(root);
            _AccountRepository.save(root);
        }
    }

    public Optional<AccountEntity> findByEmail(String email)
    {
        return Optional.ofNullable(_AccountRepository.findByEmail(email));
    }

    public Optional<AccountEntity> findByName(String name)
    {
        return Optional.ofNullable(_AccountRepository.findByName(name));
    }

    public void saveAccount(AccountEntity account)
    {
        account.setPassword(account.getPassword());
        account.setActive(1);
        RoleEntity role = _RoleRepository.findByRole("USER");
        account.setRoles(new HashSet<>(Collections.singletonList(role)));
        _AccountRepository.save(account);
    }

}