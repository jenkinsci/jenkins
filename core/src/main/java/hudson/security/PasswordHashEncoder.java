package hudson.security;

import org.springframework.security.crypto.password.PasswordEncoder;

public interface PasswordHashEncoder extends PasswordEncoder {
     boolean isHashValid(String hash);
}
