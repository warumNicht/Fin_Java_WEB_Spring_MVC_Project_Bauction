package bauction.config;

import bauction.constants.AppConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                    .csrf()
                    .csrfTokenRepository(this.csrfTokenRepository())
                .and()
                    .authorizeRequests()
                    .antMatchers("/", "/users/login", "/users/register").permitAll()
                    .antMatchers( "/admin/users/edit/"+AppConstants.ROOT_USER_ID).access("hasRole('NOBODY')")
                    .antMatchers( "/admin/**").access("hasAnyRole('ROOT','ADMIN')")
                    .antMatchers( "/moderator/**").access("hasAnyRole('ROOT','ADMIN','MODERATOR')")


                .antMatchers("/cssBau/**", "/js/**", "/favicon/**","/static-images/**").permitAll()
                    .anyRequest().authenticated()
                .and()
                    .formLogin()
                    .loginPage("/users/login")
                    .usernameParameter("username")
                    .passwordParameter("password")
                    .defaultSuccessUrl("/home")
                .and()
                    .logout().logoutSuccessUrl("/")
                .and()
                .exceptionHandling().accessDeniedPage("/unauthorized");



    }



    private CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository =
                new HttpSessionCsrfTokenRepository();
        repository.setSessionAttributeName("_csrf");
        return repository;
    }
}

