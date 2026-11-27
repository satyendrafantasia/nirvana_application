package com.nirvana.application;

import com.nirvana.application.model.User;
import com.nirvana.application.model.enums.RoleType;
import com.nirvana.application.repository.RoleRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentSignInTest {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired AuthServiceImpl auth;
    @Autowired JdbcTemplate db;

    @Test void overlappingSignInsAndAnOpenProfileEditAllSucceed() throws Exception {
        long id = users.saveAndFlush(User.builder()
                .username("test-concurrent-" + UUID.randomUUID()).password("!test")
                .role(roles.findByRoleType(RoleType.CUSTOMER)).roles(Set.of("ROLE_USER")).build()).getId();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var openProfile = users.findById(id).orElseThrow();
            var snapshotsLoaded = new CyclicBarrier(2);
            Callable<String> signIn = () -> {
                var user = users.findById(id).orElseThrow();
                snapshotsLoaded.await(10, TimeUnit.SECONDS);
                return auth.finishSignIn(user).getRefreshToken();
            };
            var first = workers.submit(signIn);
            var second = workers.submit(signIn);
            var firstToken = first.get(20, TimeUnit.SECONDS);
            var secondToken = second.get(20, TimeUnit.SECONDS);
            assertNotNull(firstToken);
            assertNotNull(secondToken);
            assertNotEquals(firstToken, secondToken);

            openProfile.setName("Updated profile");
            users.saveAndFlush(openProfile);
            var saved = users.findById(id).orElseThrow();
            assertEquals("Updated profile", saved.getName());
            assertNotNull(saved.getLastLoginAt());
            assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE user_id=?",Integer.class,id));
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
            db.update("DELETE FROM refresh_token WHERE user_id=?", id);
            users.deleteById(id);
        }
    }
}
