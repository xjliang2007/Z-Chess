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

package com.isahl.chess.referee.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * @author william.d.zk
 * @date 2021/3/5
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true,
                            securedEnabled = true,
                            jsr250Enabled = true)
@ConfigurationProperties(prefix = "z-chess.referee.security")
@PropertySource("classpath:security.properties")
public class WebSecurityConfig
        extends WebSecurityConfigurerAdapter
        implements ISecurityConfig
{

    private final BCryptPasswordEncoder _PasswordEncoder = new BCryptPasswordEncoder();
    private       String                mode;
    private       String[]              ignorePatterns;

    public String[] getIgnorePatterns()
    {
        return ignorePatterns;
    }

    public void setIgnorePatterns(String[] ignorePatterns)
    {
        this.ignorePatterns = ignorePatterns;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception
    {
        /*
        http.headers()
            .frameOptions()
            .disable();
        http.authorizeRequests()
            .antMatchers("/login/**", "/initUserData")//不拦截登录相关方法        
            .permitAll()
            //.antMatchers("/user").hasRole("ADMIN")  // user接口只有ADMIN角色的可以访问
            //            .anyRequest()
            //            .authenticated()// 任何尚未匹配的URL只需要验证用户即可访问
            .anyRequest()
            .access("@rbacPermission.hasPermission(request, authentication)")//根据账号权限访问            
            .and()
            .formLogin()
            .loginPage("/")
            .loginPage("/login")   //登录请求页
            .loginProcessingUrl("/login")  //登录POST请求路径
            .usernameParameter("username") //登录用户名参数
            .passwordParameter("password.md") //登录密码参数
            .defaultSuccessUrl("/main")   //默认登录成功页面
            .and()
            .exceptionHandling()
            //            .accessDeniedHandler(_AccessDeniedHandler) //无权限处理器
            .and()
            .logout()
            .logoutSuccessUrl("/login?logout");  //退出登录成功URL
        ------------------------------------------------------
        
        http.authorizeRequests()
            .antMatchers("/", "index", "/login", "/login-error", "/401", "/css/**", "/js/**")
            .permitAll()
            .anyRequest()
            .authenticated()
            .and()
            .formLogin()
            .loginPage("/login")
            .failureUrl("/login-error")
            .and()
            .exceptionHandling()
            .accessDeniedPage("/401");
        http.logout()
            .logoutSuccessUrl("/");
         */
        http.csrf()
            .disable();
        switch(mode) {
            case "oauth" -> oauth(http);
            case "jwt" -> jwt(http);
            case "basic" -> basic(http);
            default -> none(http);
        }
        http.logout()
            .logoutSuccessUrl("/");//退出登录成功URL
    }

    private void disableCors(HttpSecurity http) throws Exception
    {
        http.cors()
            .disable();
    }

    private void basic(HttpSecurity http) throws Exception
    {
        http.httpBasic()
            .and()
            .authorizeRequests()
            .anyRequest()
            .authenticated();
    }

    @Override
    public void configure(WebSecurity web) throws Exception
    {
        web.ignoring()
           .antMatchers(ignorePatterns);
    }

    private void none(HttpSecurity http) throws Exception
    {
        http.authorizeRequests()
            .anyRequest()
            .permitAll();
    }

    private void oauth(HttpSecurity http) throws Exception
    {
        http.antMatcher("/**")
            .authorizeRequests()
            .antMatchers("/")
            .permitAll()
            .anyRequest()
            .authenticated()
            .and()
            .oauth2Login()
            .and()
            .exceptionHandling()
            .accessDeniedPage("/401");
    }

    private void jwt(HttpSecurity http) throws Exception
    {
        http.sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()  // 授权配置
            .antMatchers(HttpMethod.OPTIONS, "/**")
            .permitAll()
            .antMatchers(ignorePatterns)
            .permitAll()
            .anyRequest()
            .authenticated()//message 需要验证
            .and()
            .exceptionHandling()
            .accessDeniedPage("/401");
        http.headers()
            .cacheControl();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder()
    {
        return _PasswordEncoder;
    }

    @Override
    public String getMode()
    {
        return mode;
    }

    public void setMode(String mode)
    {
        this.mode = mode;
    }
}
