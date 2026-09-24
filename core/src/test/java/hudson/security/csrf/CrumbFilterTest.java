package hudson.security.csrf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CrumbFilterTest {

    @Test
    void legacyDefaultCrumbFieldIsUsedWhenPrimaryFieldIsMissing() {
        CrumbFilter filter = new CrumbFilter();
        CrumbIssuer issuer = mock(CrumbIssuer.class);
        CrumbIssuerDescriptor<CrumbIssuer> descriptor = mock(CrumbIssuerDescriptor.class);
        when(issuer.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getCrumbRequestField()).thenReturn("Jenkins-Crumb");
        when(descriptor.getCrumbSalt()).thenReturn("salt");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Jenkins-Crumb")).thenReturn(null);
        when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getHeader(".crumb")).thenReturn("legacy-crumb");

        // assert the fallback path is actually used
        assertEquals("legacy-crumb", filter.extractCrumbFromRequest(request, ".crumb"));
    }
}
