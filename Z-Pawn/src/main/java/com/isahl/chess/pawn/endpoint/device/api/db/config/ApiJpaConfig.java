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

package com.isahl.chess.pawn.endpoint.device.api.db.config;

import com.isahl.chess.rook.storage.cache.config.EhcacheConfig;
import com.isahl.chess.rook.storage.db.config.BaseJpaConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(basePackages = "com.isahl.chess.pawn.endpoint.device.api.db.repository",
                       entityManagerFactoryRef = "api-entity-manager",
                       transactionManagerRef = "api-transaction-manager")
public class ApiJpaConfig
        extends BaseJpaConfig
{
    @Autowired
    public ApiJpaConfig(EhcacheConfig cacheConfig) {super(cacheConfig);}

    @Bean("api-entity-manager")
    @Primary
    public LocalContainerEntityManagerFactoryBean createRemoteEntityManager(
            @Qualifier("primary-data-source")
            DataSource dataSource,
            @Qualifier("primary-jpa-properties")
            JpaProperties jpaProperties,
            @Qualifier("primary-jpa-hibernate-properties")
            HibernateProperties hibernateProperties,
            @Qualifier("primary-sql-init-settings")
            DatabaseInitializationSettings initializationSettings)
    {
        return getEntityManager(dataSource,
                                jpaProperties,
                                hibernateProperties,
                                initializationSettings,
                                "com.isahl.chess.pawn.endpoint.device.db.remote.postgres.model",
                                "com.isahl.chess.pawn.endpoint.device.api.db.model");
    }

    @Bean("api-transaction-manager")
    @Primary
    public PlatformTransactionManager createRemoteTransactionManager(
            @Qualifier("api-entity-manager")
            LocalContainerEntityManagerFactoryBean factory)
    {
        JpaTransactionManager tm = new JpaTransactionManager();
        tm.setEntityManagerFactory(factory.getObject());
        return tm;
    }

    @Bean("api-jdbc-template")
    @Primary
    public JdbcTemplate createJdbcTemplate(
            @Qualifier("primary-data-source")
            DataSource dataSource)
    {
        return new JdbcTemplate(dataSource);
    }

    @Bean("api-name-parameter-jdbc-template")
    @Primary
    public NamedParameterJdbcTemplate createNamedParameterJdbcTemplate(
            @Qualifier("primary-data-source")
            DataSource dataSource)
    {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
