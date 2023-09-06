package hudson.security;

import org.springframework.security.crypto.password.PasswordEncoder;

interface PasswordHashEncoder extends PasswordEncoder {
     boolean isHashValid(String hash);
}
